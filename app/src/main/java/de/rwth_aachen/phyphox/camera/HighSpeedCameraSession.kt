package de.rwth_aachen.phyphox.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import de.rwth_aachen.phyphox.camera.analyzer.AnalyzingOpenGLRenderer
import java.io.Serializable

/**
 * Experimental alternative to the CameraX-based camera session in CameraInput: Drives the camera
 * through the camera2 constrained high-speed API (CameraConstrainedHighSpeedCaptureSession) to
 * reach frame rates beyond the 60fps limit of a regular preview stream. The AnalyzingOpenGLRenderer
 * acts as the single target surface, so the entire analyzing pipeline is fed exactly like in the
 * regular implementation.
 *
 * Note that the high-speed API does not support most camera controls (manual exposure, ISO,
 * shutter speed, white balance, zoom). Exposure is left to the device's auto exposure and the
 * corresponding phyphox controls have to be disabled while this implementation is used.
 */

//Serializable description of a usable high-speed configuration, determined while loading the
//experiment (CameraInput is serializable, so this must not hold camera2 objects)
data class HighSpeedCameraConfig(
        val cameraId: String,
        val width: Int,
        val height: Int,
        val fpsMax: Int
) : Serializable

@RequiresApi(Build.VERSION_CODES.M)
class HighSpeedCameraSession(
        context: Context,
        private val config: HighSpeedCameraConfig,
        private val listener: Listener
) {
    interface Listener {
        //The session has been configured and frames are being delivered at the given frame duration
        fun onHighSpeedSessionRunning(frameDurationNs: Long)
        //Exposure values reported by the device's auto exposure (any of them may be null if the device does not report them)
        fun onHighSpeedCaptureResult(shutterSpeedNs: Long?, iso: Int?, aperture: Float?)
        //The session could not be started or died unexpectedly
        fun onHighSpeedSessionError(message: String)
    }

    companion object {
        private const val TAG = "HighSpeedCameraSession"

        //Match the resolution aim of the regular implementation: prefer the smallest size that
        //still covers 1280x720, otherwise the largest available one. The preview-class surface of
        //the OpenGL renderer must not exceed 1080p in a constrained high-speed session.
        private const val MAX_WIDTH = 1920
        private const val MAX_HEIGHT = 1080
        private const val TARGET_WIDTH = 1280
        private const val TARGET_HEIGHT = 720

        //Find the best constrained high-speed configuration for the given lens facing direction
        //(constants shared by CameraCharacteristics and CameraX' CameraSelector).
        //Returns null if the device offers no constrained high-speed mode for this lens.
        @JvmStatic
        fun findConfig(cameraManager: CameraManager, lensFacing: Int): HighSpeedCameraConfig? {
            try {
                for (cameraId in cameraManager.cameraIdList) {
                    val characteristics = try {
                        cameraManager.getCameraCharacteristics(cameraId)
                    } catch (e: Exception) {
                        continue
                    }
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) != lensFacing)
                        continue
                    val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: continue
                    if (!capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO))
                        continue
                    val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

                    var best: HighSpeedCameraConfig? = null
                    for (size in streamConfigurationMap.highSpeedVideoSizes) {
                        if (size.width > MAX_WIDTH || size.height > MAX_HEIGHT)
                            continue
                        val fpsMax = streamConfigurationMap.getHighSpeedVideoFpsRangesFor(size).maxOfOrNull { it.upper } ?: continue
                        if (fpsMax <= 60)
                            continue //No use in the added complexity if it is not faster than the regular implementation
                        val candidate = HighSpeedCameraConfig(cameraId, size.width, size.height, fpsMax)
                        if (best == null || isBetter(candidate, best))
                            best = candidate
                    }
                    if (best != null)
                        return best
                }
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Could not enumerate cameras for high-speed support.", e)
            }
            return null
        }

        private fun isBetter(candidate: HighSpeedCameraConfig, current: HighSpeedCameraConfig): Boolean {
            if (candidate.fpsMax != current.fpsMax)
                return candidate.fpsMax > current.fpsMax
            val candidateCoversTarget = candidate.width >= TARGET_WIDTH && candidate.height >= TARGET_HEIGHT
            val currentCoversTarget = current.width >= TARGET_WIDTH && current.height >= TARGET_HEIGHT
            if (candidateCoversTarget != currentCoversTarget)
                return candidateCoversTarget
            val candidateArea = candidate.width * candidate.height
            val currentArea = current.width * current.height
            //Among sizes covering the target resolution the smaller one is preferred (faster analysis), otherwise the larger one
            return if (candidateCoversTarget) candidateArea < currentArea else candidateArea > currentArea
        }
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraConstrainedHighSpeedCaptureSession? = null
    private var surface: Surface? = null

    @Volatile
    private var closed = false

    //Throttle for state updates derived from capture results
    private var lastReportedShutterSpeedNs: Long? = null
    private var lastReportedIso: Int? = null
    private var lastReportedAperture: Float? = null
    private var lastReportTime: Long = 0

    fun start(renderer: AnalyzingOpenGLRenderer) {
        val thread = HandlerThread("HighSpeedCameraSession")
        thread.start()
        handlerThread = thread
        handler = Handler(thread.looper)

        renderer.createCameraSurface(config.width, config.height) { surface ->
            handler?.post {
                openCamera(surface)
            }
        }
    }

    //The camera permission has already been checked when the experiment was loaded (see the camera
    //block in PhyphoxFile), the experiment would not have reached this point without it.
    @SuppressLint("MissingPermission")
    private fun openCamera(surface: Surface) {
        if (closed)
            return
        this.surface = surface
        try {
            cameraManager.openCamera(config.cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    if (closed) {
                        device.close()
                        return
                    }
                    createSession(device, surface)
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    if (!closed)
                        listener.onHighSpeedSessionError("High-speed camera disconnected.")
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    if (!closed)
                        listener.onHighSpeedSessionError("High-speed camera error: $error")
                }
            }, handler)
        } catch (e: Exception) {
            listener.onHighSpeedSessionError("Could not open high-speed camera: ${e.message}")
        }
    }

    private fun createSession(device: CameraDevice, surface: Surface) {
        try {
            @Suppress("DEPRECATION") //The non-deprecated variant with SessionConfiguration requires API 28
            device.createConstrainedHighSpeedCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (closed) {
                        session.close()
                        return
                    }
                    startRepeatingBurst(session as CameraConstrainedHighSpeedCaptureSession, surface)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    if (!closed)
                        listener.onHighSpeedSessionError("Could not configure high-speed camera session.")
                }
            }, handler)
        } catch (e: Exception) {
            listener.onHighSpeedSessionError("Could not create high-speed camera session: ${e.message}")
        }
    }

    private fun startRepeatingBurst(session: CameraConstrainedHighSpeedCaptureSession, surface: Surface) {
        captureSession = session

        val supportedRanges = try {
            cameraManager.getCameraCharacteristics(config.cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getHighSpeedVideoFpsRangesFor(Size(config.width, config.height))
                    ?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        //With our renderer as the only target every request of the generated burst targets its
        //surface, so a fixed fps range delivers every single frame to the analysis. Some devices
        //might reject a fixed range without a video encoder surface, so the variable ranges (which
        //only deliver 30fps to a preview-class surface) remain as a fallback. Any fixed range,
        //even at lower fps, beats that fallback, hence all fixed ranges come first.
        val rangesToTry = supportedRanges.filter { it.lower == it.upper }.sortedByDescending { it.upper } +
                supportedRanges.filter { it.lower != it.upper }.sortedByDescending { it.upper }

        for (fpsRange in rangesToTry) {
            try {
                val builder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                builder.addTarget(surface)
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
                val burst = session.createHighSpeedRequestList(builder.build())
                session.setRepeatingBurst(burst, captureCallback, handler)

                val deliveredFps = if (fpsRange.lower == fpsRange.upper) fpsRange.upper else 30
                Log.i(TAG, "High-speed session running: camera ${config.cameraId}, ${config.width}x${config.height}, fps range $fpsRange, delivered fps $deliveredFps")
                listener.onHighSpeedSessionRunning(1_000_000_000L / deliveredFps)
                return
            } catch (e: Exception) {
                Log.w(TAG, "High-speed burst with fps range $fpsRange failed: ${e.message}")
            }
        }
        listener.onHighSpeedSessionError("Could not start high-speed capture for any supported fps range.")
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            //Report the device-controlled exposure values so they can be written to the experiment
            //buffers, but throttle the updates as this callback fires for every completed burst
            val now = System.currentTimeMillis()
            if (now - lastReportTime < 200)
                return
            val shutterSpeedNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            val aperture = result.get(CaptureResult.LENS_APERTURE)
            if (shutterSpeedNs == lastReportedShutterSpeedNs && iso == lastReportedIso && aperture == lastReportedAperture)
                return
            lastReportTime = now
            lastReportedShutterSpeedNs = shutterSpeedNs
            lastReportedIso = iso
            lastReportedAperture = aperture
            listener.onHighSpeedCaptureResult(shutterSpeedNs, iso, aperture)
        }
    }

    //Close the session and camera. The callback is invoked on the session's handler thread once
    //the camera has been closed, so the caller can safely release the target surface afterwards.
    fun close(callback: Runnable) {
        closed = true
        val currentHandler = handler
        if (currentHandler == null) {
            callback.run()
            return
        }
        currentHandler.post {
            try {
                captureSession?.close()
            } catch (e: Exception) {
                //The session may already be in an unusable state, closing the device below is what matters
            }
            captureSession = null
            try {
                cameraDevice?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Exception while closing high-speed camera: ${e.message}")
            }
            cameraDevice = null
            surface = null
            handlerThread?.quitSafely()
            handlerThread = null
            handler = null
            callback.run()
        }
    }
}
