package de.rwth_aachen.phyphox

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
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

class FlashLightManager(private var cameraManager: CameraManager?, private var cameraControl: CameraControl? = null) {

    private var camera: Camera? = null // For API 21/22
    private val cameraId: String? = try { cameraManager?.cameraIdList?.getOrNull(0) } catch (e: Exception) { null }
    private var currentIntensity: Int = 1
    private val strobeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + strobeJob)
    private var activeStrobeJob: Job? = null
    private val strobeInterval = MutableStateFlow<Long?>(null)

    // Mutex ensures that Strobe and Intensity calls dont overlap.
    private val hardwareMutex = Mutex()
    private var isHardwareOn = false


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

        try {
            if (cameraControl != null) {
                cameraControl?.enableTorch(enabled)
            } else {
                val id = cameraId ?: return@withLock
                if (enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && currentIntensity > 1) {
                        cameraManager?.turnOnTorchWithStrengthLevel(id, currentIntensity)
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
                strobeInterval.collectLatest { interval ->
                    if (interval == null) {
                        performToggle(false)
                        return@collectLatest
                    }

                    while (isActive) {
                        performToggle(true)
                        delay(interval / 2)
                        performToggle(false)
                        delay(interval / 2)
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    performToggle(false)
                }
            }
        }
    }

    fun updateRate(rate: Double) {
        strobeInterval.value = if (rate <= 0) null else ((1.0 / rate) * 1000).toLong().coerceAtLeast(33L)
    }

    fun release() {
        // Call when whole object is being destroyed
        strobeJob.cancel()
        scope.cancel()
    }

    fun stopStrobe() {
        strobeInterval.value = null
        activeStrobeJob?.cancel()
        activeStrobeJob = null
    }

    fun setIntensity(level: Int) {

        this.currentIntensity = level.coerceIn(1, maxIntensityLevel)
        // If strobe is running, the loop will pick up the new intensity on the next toggle.
        // Otherwise, apply it now.
        if (activeStrobeJob == null || activeStrobeJob?.isActive == false) {
            scope.launch { performToggle(true) }
        }
    }

    fun turnOfFlashLight(){
        stopStrobe()
        scope.launch {
            performToggle(false)
        }

    }

}
