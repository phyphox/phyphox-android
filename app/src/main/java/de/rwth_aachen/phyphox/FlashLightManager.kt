package de.rwth_aachen.phyphox

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.camera.core.CameraControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.locks.LockSupport
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class FlashLightManager(private var cameraManager: CameraManager?, private var cameraControl: CameraControl? = null) {

    private var camera: Camera? = null // For API 21/22
    private val cameraId: String? = try { cameraManager?.cameraIdList?.getOrNull(0) } catch (e: Exception) { null }

    private val strobeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + strobeJob)
    private var activeStrobeJob: Job? = null
    private var nextStrobeCycle = 0L //Absolute schedule in the System.nanoTime() timebase
    private var currentStrobeCycleInterval = 0L //ns

    private var isHardwareOn = false

    private val useTorchWithStrength = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    private val useSetTorchMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    data class FlashState constructor(
        val intensity: Double = 0.0,
        val interval: Long = 0, //Strobe period in ns, zero meaning constantly on
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

    private fun performToggle(enabled: Boolean) {
        if (isHardwareOn == enabled && !enabled) return

        if (enabled && isOverheated) {
            return
        }

        try {
            val control = cameraControl
            if (control != null) {
                control.enableTorch(enabled)
            } else {
                val id = cameraId ?: return
                if (enabled) {
                    val state = flashState.value
                    if (useTorchWithStrength && state.intensity > 0) {
                        cameraManager?.turnOnTorchWithStrengthLevel(id,
                            (state.intensity * maxIntensityLevel).roundToInt()
                        )
                    } else if (useSetTorchMode) {
                        cameraManager?.setTorchMode(id, true)
                    } else {
                        // Legacy Way (API 21/22)
                        handleLegacyFlash(true)
                    }
                } else {
                    if (useSetTorchMode) {
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

    //Waits until the given System.nanoTime() deadline: delay() for the millisecond part, then
    //park the sub-millisecond remainder on the strobe thread.
    private suspend fun delayUntil(targetNanos: Long) {
        while (true) {
            val remaining = targetNanos - System.nanoTime()
            if (remaining <= 0)
                return
            val ms = remaining / 1_000_000
            if (ms > 0)
                delay(ms)
            else
                LockSupport.parkNanos(remaining)
        }
    }

    fun startStrobeLoop(){
        if( activeStrobeJob?.isActive == true) return

        //The strobe gets its own high-priority thread for as long as it runs: at default
        //priority the scheduler may defer wakeups long enough to distort the strobe timing, and
        //delay() only has millisecond granularity, so sub-millisecond remainders are parked on
        //this thread (see delayUntil). The dispatcher is closed when the loop ends, so the
        //thread only exists while the experiment is running.
        val strobeDispatcher = Executors.newSingleThreadExecutor { r ->
            Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
                r.run()
            }, "phyphox flashlight")
        }.asCoroutineDispatcher()

        activeStrobeJob = scope.launch(strobeDispatcher) {
            try {
                flashState.collectLatest { state ->
                    //Off and constantly on are steady states, including the dutycycle extremes,
                    //so the hardware is not toggled for them
                    if (!(state.intensity > 0.0) || !(state.dutycycle > 0.0)) {
                        performToggle(false)
                        return@collectLatest
                    } else if (state.interval == 0L || state.dutycycle >= 1.0) {
                        performToggle(true)
                        return@collectLatest
                    }

                    //If neither of the both above applied, we are strobing. Align the schedule
                    //with the phase of the previous one if the interval changed.
                    var currentCycle = if (currentStrobeCycleInterval > 0)
                        (1.0 - (nextStrobeCycle - System.nanoTime()).toDouble()/currentStrobeCycleInterval.toDouble())
                    else 1.0
                    if (currentCycle > 1)
                        currentCycle -= 1.0
                    currentCycle = currentCycle.coerceIn(0.0, 1.0)

                    if (currentStrobeCycleInterval != state.interval) {
                        nextStrobeCycle = System.nanoTime() - (currentCycle * state.interval).roundToLong()
                        currentStrobeCycleInterval = state.interval
                    }

                    performToggle(currentCycle < state.dutycycle) //Set intensity immediately

                    while (isActive) {
                        var now = System.nanoTime()

                        //If the loop fell behind by one or more whole cycles (scheduling under
                        //load or a slow torch call), skip the missed cycles but keep the phase
                        if (now - nextStrobeCycle >= state.interval) {
                            val missedCycles = (now - nextStrobeCycle) / state.interval
                            nextStrobeCycle += missedCycles * state.interval
                        }

                        if (now < nextStrobeCycle)
                            delayUntil(nextStrobeCycle)

                        //Turn on as long as any part of the on phase remains: waking late
                        //shortens the flash instead of dropping it entirely
                        val dutyEnd = nextStrobeCycle + (state.interval * state.dutycycle).roundToLong()
                        now = System.nanoTime()
                        if (now < dutyEnd) {
                            performToggle(true)
                            if (System.nanoTime() < dutyEnd)
                                delayUntil(dutyEnd)
                        }
                        performToggle(false)

                        nextStrobeCycle += state.interval
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    performToggle(false)
                }
            }
        }.also { job ->
            job.invokeOnCompletion { strobeDispatcher.close() }
        }
    }

    fun updateFlashState(intensity: Double, frequency: Double, dutycycle: Double) {
        val interval = if (frequency > 0 && frequency.isFinite()) (1.0e9 / frequency).roundToLong().coerceAtLeast(1L) else 0
        val newState = FlashState(if (intensity.isFinite()) intensity else 0.0,
                                  interval,
                                  if (dutycycle.isFinite()) dutycycle else 0.0)
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
