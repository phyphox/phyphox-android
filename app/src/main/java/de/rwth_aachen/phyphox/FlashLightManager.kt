package de.rwth_aachen.phyphox

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.SystemClock
import androidx.camera.core.CameraControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class FlashLightManager(private var cameraManager: CameraManager?, private var cameraControl: CameraControl? = null) {

    private var camera: Camera? = null // For API 21/22
    private val cameraId: String? = try { cameraManager?.cameraIdList?.getOrNull(0) } catch (e: Exception) { null }
    private val strobeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + strobeJob)
    private var activeStrobeJob: Job? = null
    private var nextStrobeCycle = 0L
    private var currentStrobeCycleInterval = 0L

    // Mutex ensures that Strobe and Intensity calls dont overlap.
    private val hardwareMutex = Mutex()
    private var isHardwareOn = false

    data class FlashState constructor(
        val intensity: Double = 0.0,
        val interval: Long = 0,
        val dutycycle: Double = 0.5
    )

    private val flashState = MutableStateFlow<FlashState>(FlashState(0.0, 0, 0.5))


    var isOverheated: Boolean = false
        set(value) {
            field = value
            if (value) {
                turnOfFlashLight()
            }
        }
    // Get the maximum strength level supported by the device
    private val maxIntensityLevel: Int by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && cameraId != null) {
            val chars = cameraManager?.getCameraCharacteristics(cameraId)
            chars?.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
        } else {
            1
        }
    }

    // Centralized hardware access with Mutex synchronization
    suspend fun performToggle(enabled: Boolean) = hardwareMutex.withLock {
        // Don't send command if hardware is already in that state
        if (isHardwareOn == enabled && !enabled) return@withLock

        if (enabled && isOverheated) {
            return@withLock
        }

        try {
            if (cameraControl != null) {
                cameraControl?.enableTorch(enabled)
            } else {
                val id = cameraId ?: return@withLock
                if (enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && flashState.value.intensity > 0) {
                        cameraManager?.turnOnTorchWithStrengthLevel(id,
                            (flashState.value.intensity * maxIntensityLevel).roundToInt()
                        )
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            cameraManager?.setTorchMode(id, true)
                        } else {
                            // Legacy Way (API 21/22)
                            handleLegacyFlash(true)
                        }
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cameraManager?.setTorchMode(id, false)
                    } else {
                        // Legacy Way (API 21/22)
                        handleLegacyFlash(false)
                    }
                }
            }
            isHardwareOn = enabled
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Before API 23, there was no setTorchMode method,
    // for older api, we actually have to "open" camera hardware and toggle the flash parameter manually.
    // this contains to setupPreviewTexture, so later the app can only allow to work it from Marshmallow.
    // this function is here, just for the reference.
    private fun handleLegacyFlash(isEnabled: Boolean) {
        try {
            if (isEnabled) {
                camera = Camera.open().apply {
                    val parameters = parameters
                    parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                    this.parameters = parameters
                    setPreviewTexture(SurfaceTexture(0))
                    startPreview()
                }
            } else {
                camera?.apply {
                    stopPreview()
                    release()
                }
                camera = null
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun startStrobeLoop(){
        if( activeStrobeJob?.isActive == true) return

        activeStrobeJob = scope.launch {
            try {
                flashState.collectLatest { state ->
                    if (!(state.intensity > 0.0)) {
                        performToggle(false)
                        return@collectLatest
                    } else if (state.interval == 0L) {
                        performToggle(true)
                        return@collectLatest
                    }

                    //If neither of the both above applied, we are strobing
                    var currentCycle = if (currentStrobeCycleInterval > 0)
                        (1.0 - (nextStrobeCycle - SystemClock.uptimeMillis()).toDouble()/currentStrobeCycleInterval.toDouble())
                    else 1.0
                    if (currentCycle > 1)
                        currentCycle -= 1.0
                    currentCycle.coerceIn(0.0, 1.0)
                    if (currentStrobeCycleInterval > 0)


                    if (currentStrobeCycleInterval != state.interval) {
                        nextStrobeCycle = SystemClock.uptimeMillis() - (currentCycle * state.interval).roundToLong()
                        currentStrobeCycleInterval = state.interval
                    }

                    performToggle(currentCycle < state.dutycycle) //Set intensity immediately

                    while (isActive) {
                        val cycleDelay = nextStrobeCycle - SystemClock.uptimeMillis()
                        if (cycleDelay > 0) {
                            delay(cycleDelay)
                            performToggle(true)
                        }
                        val dutyCycleDelay = (nextStrobeCycle + (state.interval * state.dutycycle) - SystemClock.uptimeMillis()).roundToLong()
                        if (dutyCycleDelay > 0) {
                            delay(dutyCycleDelay)
                            performToggle(false)
                        }
                        nextStrobeCycle += state.interval
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    performToggle(false)
                }
            }
        }
    }

    fun updateFlashState(intensity: Double, frequency: Double, dutycycle: Double) {
        val interval = if (frequency > 0) ((1.0 / frequency) * 1000).toLong().coerceAtLeast(33L) else 0
        val newState = FlashState(intensity, interval, dutycycle)
        if (newState != flashState.value)
            flashState.value = newState
    }

    fun release() {
        // Call when whole object is being destroyed
        strobeJob.cancel()
        scope.cancel()
    }

    fun stopStrobe() {
        flashState.value = FlashState(0.0, 0, 0.5)
        activeStrobeJob?.cancel()
        activeStrobeJob = null
    }


    fun turnOfFlashLight(){
        stopStrobe()
        scope.launch {
            performToggle(false)
        }

    }

}
