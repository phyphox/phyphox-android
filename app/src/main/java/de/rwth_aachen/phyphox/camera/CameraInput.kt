package de.rwth_aachen.phyphox.camera

import android.graphics.ImageFormat
import android.graphics.RectF
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.util.Range
import android.util.SizeF
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.rwth_aachen.phyphox.DataBuffer
import de.rwth_aachen.phyphox.DataOutput
import de.rwth_aachen.phyphox.ExperimentTimeReference
import de.rwth_aachen.phyphox.camera.analyzer.AnalyzingOpenGLRenderer
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.helper.CameraHelper.getCameraList
import de.rwth_aachen.phyphox.camera.model.CameraSettingState
import de.rwth_aachen.phyphox.camera.model.CameraState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.Serializable
import java.util.Vector
import java.util.concurrent.locks.Lock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.round

/*
* Takes the essential input from the PhyphoxExperiment which are provided from XML.
* */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraInput : Serializable, AnalyzingOpenGLRenderer.ExposureStatisticsListener, AnalyzingOpenGLRenderer.CreateSurfaceCallback {

    private val cameraManager: CameraManager
    private var cameraId: String? = null
    var w: Int = 0
    var h: Int = 0
    private var camera: CameraDevice? = null

    data class CameraFastParameters(
        val highSpeedMode: Boolean,
        val w: Int,
        val h: Int,
        val fps: Float
    )

    // Holds and release the image analysis value (z) and time value (t) from data buffer
    var dataLuma: DataBuffer? = null
    var dataLuminance: DataBuffer? = null
    var dataHue: DataBuffer? = null
    var dataSaturation: DataBuffer? = null
    var dataValue: DataBuffer? = null
    var dataThreshold: DataBuffer? = null

    var dataT: DataBuffer? = null
    var shutterSpeedDataBuffer: DataBuffer? = null
    var isoDataBuffer: DataBuffer? = null
    var apertureDataBuffer: DataBuffer? = null

    enum class AEStrategy {
        mean, avoidUnderxposure, avoidOverexposure
    }
    var aeStrategy: AEStrategy = AEStrategy.mean

    var thresholdAnalyzerThreshold: Double = 0.5

    val dataLock: Lock

    val _cameraSettingState: MutableStateFlow<CameraSettingState>
    public val cameraSettingState: StateFlow<CameraSettingState>

    var lifecycleOwner: LifecycleOwner? = null

    @Transient var analyzingOpenGLRenderer: AnalyzingOpenGLRenderer? = null

    var lockedSettings: MutableMap<String, String>? = mutableMapOf()

    // Status of the play and pause for image analysis
    var measuring = false

    lateinit var experimentTimeReference: ExperimentTimeReference

    class CameraInputException(message: String) : Exception(message)

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
        x1: Float,
        x2: Float,
        y1: Float,
        y2: Float,
        buffers: Vector<DataOutput>,
        lock: Lock,
        experimentTimeReference: ExperimentTimeReference,
        autoExposure: Boolean,
        lockedSettings: String?,
        aeStrategy: AEStrategy,
        thresholdAnalyzerThreshold: Double,
        cameraManager: CameraManager
    ) {

        this._cameraSettingState = MutableStateFlow<CameraSettingState>(
            setDefaultCameraSettingValueIfAvailable(lockedSettings).copy(
                cameraPassepartout = RectF(x1, y1, x2, y2),
                autoExposure = autoExposure
            )
        )

        cameraSettingState = _cameraSettingState

        this.experimentTimeReference = experimentTimeReference

        if (buffers.size > 0 && buffers[0] != null) dataT = buffers[0].buffer
        if (buffers.size > 1 && buffers[1] != null) dataLuma = buffers[1].buffer
        if (buffers.size > 2 && buffers[2] != null) dataLuminance = buffers[2].buffer
        if (buffers.size > 3 && buffers[3] != null) dataHue = buffers[3].buffer
        if (buffers.size > 4 && buffers[4] != null) dataSaturation = buffers[4].buffer
        if (buffers.size > 5 && buffers[5] != null) dataValue = buffers[5].buffer
        if (buffers.size > 6 && buffers[6] != null) dataThreshold = buffers[6].buffer

        if (buffers.size > 7 && buffers[7] != null) shutterSpeedDataBuffer = buffers[7].buffer
        if (buffers.size > 8 && buffers[8] != null) isoDataBuffer = buffers[8].buffer
        if (buffers.size > 9 && buffers[9] != null) apertureDataBuffer = buffers[9].buffer

        this.dataLock = lock
        this.aeStrategy = aeStrategy
        this.thresholdAnalyzerThreshold = thresholdAnalyzerThreshold
        this.cameraManager = cameraManager

        findBestCamera(-1)?.let {
            setCamera(it)
        }
    }

    fun getFastParameters(cam: CameraCharacteristics): CameraFastParameters? {
        var highSpeedMode = false
        var maxFps = 0.0f
        var w = 0
        var h = 0

        val targetResolution = 720*1280

        val caps = cam.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return null

        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO in caps) {
            highSpeedMode = true
            cam.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.let { map ->
                    map.highSpeedVideoSizes?.let { sizes ->
                        for (size in sizes) {
                            val resolution = size.width * size.height
                            map.getHighSpeedVideoFpsRangesFor(size)?.let { fpsRanges ->
                                for (fpsRange in fpsRanges) {
                                    if (fpsRange.upper > maxFps || (fpsRange.upper.toFloat() == maxFps && abs(targetResolution - resolution) < abs(targetResolution - h*w))) {
                                        maxFps = fpsRange.upper.toFloat()
                                        w = size.width
                                        h = size.height
                                    }
                                }
                            }
                        }
                    }
                }
        } else {
            cam.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.let { map ->
                    map.getOutputSizes(ImageFormat.YUV_420_888)?.let { sizes ->
                        for (size in sizes) {
                            val resolution = size.width * size.height
                            val fps = 1_000_000_000.0f/map.getOutputMinFrameDuration(ImageFormat.YUV_420_888, size)
                            if (fps > maxFps || (fps == maxFps && abs(targetResolution - resolution) < abs(targetResolution - h*w))) {
                                maxFps = fps
                                w = size.width
                                h = size.height
                            }
                        }
                    }
                }
        }

        return CameraFastParameters(highSpeedMode = highSpeedMode, w = w, h = h, fps = maxFps)
    }

    fun findBestCamera(lensFacing: Int): String? {
        Log.d("CameraInput", "findDefaultCamera")
        var cameraId: String? = null

        var bestMatchValue = -1

        getCameraList()?.let {cams ->
            for ((key, cam) in cams) {
                var matchValue = 0
                val foundFacing = cam.get(CameraCharacteristics.LENS_FACING)

                //Strongly prefer requested direction. Only use the other direction if there absolutely is no other camera
                if (lensFacing >= 0 && lensFacing == foundFacing)
                    matchValue += 1000000000
                //Slightly prefer back facing over front facing
                if (foundFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    matchValue += 100000000
                }
                val caps = cam.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: continue

                if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR !in caps) {
                    continue
                }

                val fastParameters = getFastParameters(cam) ?: continue

                if (fastParameters.highSpeedMode)
                    matchValue += 1000000

                matchValue += (fastParameters.fps * 100).toInt() + fastParameters.w*fastParameters.h / 10000   // maxFps is everything, but in a tie the higher resolution wins

                val sensorSize = cam.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: SizeF(6.0f, 4.0f)

                var bestFieldOfView = 0.0
                cam.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.let { focalLengths ->
                        for (focalLength in focalLengths) {
                            val fieldOfView = 2.0 * atan(sensorSize.width / (focalLength * 2.0)) * 180.0 / Math.PI
                            if (abs(fieldOfView - 73.7) < abs(bestFieldOfView - 73.7)) {
                                bestFieldOfView = fieldOfView
                            }
                        }
                        matchValue += 50 - abs(bestFieldOfView - 73.7).toInt() //Slightly prefer default zoom
                    }

                Log.d("CameraInput", "Found camera $key with ${fastParameters.fps} fps at ${fastParameters.w}x${fastParameters.h} pixels. (${bestFieldOfView}°) => Score $matchValue")

                if (matchValue > bestMatchValue) {
                    bestMatchValue = matchValue
                    cameraId = key
                }

            }
        }

        Log.d("CameraInput", "Selected camera $cameraId as best match.")

        return cameraId
    }

    fun setCamera(cameraId: String) {
        Log.d("CameraInput", "setCamera")
        val chars = getCameraList()?.get(cameraId)
        if (chars == null) {
            Log.e("depthInput", "setCamera: Camera not found in CameraList.")
            return
        }

        val parameters = getFastParameters(chars) ?: return
        w = parameters.w
        h = parameters.h

        Log.d("CameraInput", "New resolution ${w}x${h}")
        this.cameraId = cameraId
    }

    fun startCameraWithLifecycle(lifecycleOwner: LifecycleOwner) {
        Log.d("CameraInput", "startCameraWithLifecycle")
        this.lifecycleOwner = lifecycleOwner

        lifecycleOwner.lifecycleScope.launch {
            cameraSettingState.collectLatest { cameraSettingState ->
                when (cameraSettingState.cameraState) {
                    CameraState.NONE -> Unit
                    CameraState.INITIALIZING -> {
                        // setupZoomControl() TODO
                        loadAndSetupExposureSettingRanges()
                        // updateCaptureRequestOptions(cameraSettingState) TODO
                        _cameraSettingState.emit(
                                _cameraSettingState.value.copy(
                                        cameraState = CameraState.RUNNING
                                )
                        )
                    }
                    CameraState.RUNNING -> Unit
                    CameraState.RESTART -> {
                        analyzingOpenGLRenderer?.releaseCameraSurface({
                            lifecycleOwner.lifecycleScope.launch {
                                try {
                                    startCamera()
                                } catch (e: CameraInputException) {
                                    //TODO properly handle...
                                    Log.e("CameraInput", "Error while starting the camer (${e.message})")
                                }
                            }
                        })
                    }
                }
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            startCamera()
        }
    }

    suspend fun startCamera() {
        Log.d("CameraInput", "startCamera")

        val cameraId = cameraId ?: throw CameraInputException("No camera available")

        if (analyzingOpenGLRenderer == null)
            analyzingOpenGLRenderer =
                AnalyzingOpenGLRenderer(this, dataLock, cameraSettingState, this)

        // TODO: It might be a good idea to implement an ImageReader as it receives the exact timestamp...?

        try {
            this.camera = suspendCoroutine { cont ->
                val callback = object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cont.resume(camera)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cont.resume(null)
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e("CameraInput", "Open camera error ($error).")
                        camera.close()
                        cont.resume(null)
                    }
                }
                cameraManager.openCamera(cameraId, callback, null)
            }
        } catch (e: SecurityException) {
            throw CameraInputException("Missing permissions ($e).")
        } catch (e: Exception) {
            throw CameraInputException("Unknown exception ($e).")
        }

        analyzingOpenGLRenderer?.createSurface(w, h, this)
    }

    override fun onSurfaceReady(surface: Surface) {
        lifecycleOwner?.lifecycleScope?.launch {
            val camera = camera ?: throw CameraInputException("Failed to open the camera.")
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)

            captureRequestBuilder.addTarget(surface)

            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(240, 240))
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000/500)
            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, 0)

            val captureSession = suspendCoroutine { cont ->
                val callback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cont.resume(session)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("CameraInput", "Camera capture session onConfigureFailed.")
                        cont.resume(null)
                    }
                }
                camera.createConstrainedHighSpeedCaptureSession(listOf(surface), callback, null)
            } as CameraConstrainedHighSpeedCaptureSession

            captureSession?.setRepeatingBurst(
                captureSession.createHighSpeedRequestList(captureRequestBuilder.build()),
                null,
                null
            ) // TODO Can the capture callback be used for timestamps?

            _cameraSettingState.emit(
                cameraSettingState.value.copy(
                    cameraState = CameraState.INITIALIZING
                )
            )
        }
    }
/* TODO
    fun switchCamera() {
        val camLensFacing = cameraSettingState.value.currentLens
        // Toggle the lens facing
        val newState = if (camLensFacing == CameraSelector.LENS_FACING_BACK) {
            cameraSettingState.value.copy(
                    currentLens = CameraSelector.LENS_FACING_FRONT,
                    cameraState = CameraState.RESTART
            )
        } else {
            cameraSettingState.value.copy(
                    currentLens = CameraSelector.LENS_FACING_BACK,
                    cameraState = CameraState.RESTART
            )
        }

        lifecycleOwner?.lifecycleScope?.launch {
            _cameraSettingState.emit(newState)
        }
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun setupZoomControl(){
        val cameraInfo = camera?.cameraInfo?.let { Camera2CameraInfo.from(it) }
        val zoomStateValue = camera?.cameraInfo?.zoomState?.value

        val maxZoomRatio =  zoomStateValue?.maxZoomRatio ?: 1f
        val minZoomRatio =  zoomStateValue?.minZoomRatio ?: 0f
        val zoomRatio =  zoomStateValue?.zoomRatio ?: 1f
        val linearZoom =  zoomStateValue?.linearZoom ?: 1f

        val maxOpticalZoom: FloatArray? = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)

        val ratios: MutableList<Float> = CameraHelper.computeZoomRatios(minZoomRatio, maxZoomRatio)

        _cameraSettingState.value = cameraSettingState.value.copy(
                cameraMinZoomRatio = minZoomRatio,
                cameraMaxZoomRatio = maxZoomRatio,
                cameraZoomRatio = zoomRatio,
                cameraLinearRatio = linearZoom,
                cameraZoomRatioConverted = ratios,
                cameraMaxOpticalZoom = maxOpticalZoom?.last()
        )
    }
    */

    fun loadAndSetupExposureSettingRanges() {
        // from the cameraCharacteristic, isoRange is acquired which is in the form of of Range<Int>,
        // which is then mapped into List<String>
        val isoRange = getCameraList()?.get(cameraId)?.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let {
            isoRange ->
                isoRange.lower?.let { lower ->
                    isoRange.upper?.let { upper ->
                        CameraHelper.isoRange(lower, upper).map { it.toString() }
                    }
                }
        }

        // from the cameraCharacteristic, shutter speed range is acquired which is in the form of of Range<Long>,
        // which is then mapped into List<String>
        val shutterSpeedRange =
            getCameraList()?.get(cameraId)?.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                        .let { shutterSpeedRange ->
                            shutterSpeedRange?.lower?.let { lower ->
                                shutterSpeedRange.upper?.let { upper ->
                                    CameraHelper.shutterSpeedRange(lower, upper)
                                            .map { "" + it.numerator + "/" + it.denominator }
                                }
                            }

                        }

        // from the cameraCharacteristic, aperture range is acquired which is in the form of of FloatArray,
        // which is then mapped into List<String>
        val apertureRange =
            getCameraList()?.get(cameraId)?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                        .let { apertureRange ->
                            apertureRange?.map { it.toString() }
                        }
        val fixedAperture = if (apertureRange?.contains(cameraSettingState.value.currentApertureValue.toString()) != false) cameraSettingState.value.currentApertureValue else apertureRange.first().toFloat()

        val exposureRange = CameraHelper.getExposureValuesDefaultList()

        val awbAvailableModes = getCameraList()?.get(cameraId)?.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
        val maxRegionsAWB = getCameraList()?.get(cameraId)?.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0

        val currentCameraSettingValueState = _cameraSettingState.value
        val newCameraSettingValueState = currentCameraSettingValueState.copy(
                apertureRange = apertureRange,
                currentApertureValue = fixedAperture,
                shutterSpeedRange = shutterSpeedRange,
                isoRange = isoRange,
                exposureRange = exposureRange,
                cameraState = CameraState.RUNNING,
                cameraMaxRegionAWB =  maxRegionsAWB,
                cameraWhiteBalanceModes = CameraHelper.getWhiteBalanceModes().filter { awbAvailableModes.contains(it.key) }.keys.toList(),
        )

         _cameraSettingState.value = newCameraSettingValueState

    }

/* TODO
    fun setAutoExposure(autoExposure: Boolean) {
        lifecycleOwner?.lifecycleScope?.launch {
            _cameraSettingState.emit(
                    _cameraSettingState.value.copy(
                            autoExposure = autoExposure,
                    )
            )
        }
    }

    fun setWhiteBalance(value: FloatArray) {
        lifecycleOwner?.lifecycleScope?.launch {
            _cameraSettingState.emit(
                    _cameraSettingState.value.copy(
                            cameraCurrentWhiteBalanceManualValue = value,
                    )
            )
        }
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun updateCaptureRequestOptions(newState: CameraSettingState) {
        val crBuilder = CaptureRequestOptions.Builder()
        crBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, newState.currentIsoValue)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, newState.currentShutterValue)
                .setCaptureRequestOption(CaptureRequest.LENS_APERTURE, newState.currentApertureValue)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, newState.cameraCurrentWhiteBalanceMode)

        camera?.let {
            val control = Camera2CameraControl.from(it.getCameraControl())
            control.setCaptureRequestOptions(crBuilder.build())
        }
    }

    fun updateCameraSettingValue(value: String, settingMode: CameraSettingMode) {

        val currentCameraSettingState = cameraSettingState.value
        var newCameraSettingState: CameraSettingState


        when (settingMode) {
            CameraSettingMode.ISO -> {
                val iso: Int = value.toInt()
                newCameraSettingState = currentCameraSettingState.copy(
                        currentIsoValue = iso,
                )
            }
            CameraSettingMode.SHUTTER_SPEED -> {
                val shutterSpeed: Long = CameraHelper.stringToNanoseconds(value)
                newCameraSettingState = currentCameraSettingState.copy(
                        currentShutterValue = shutterSpeed,
                )
            }
            CameraSettingMode.APERTURE -> {
                val aperture: Float = value.toFloat()
                newCameraSettingState = currentCameraSettingState.copy(
                        currentApertureValue = aperture,
                )
            }
            CameraSettingMode.EXPOSURE -> {
                val exposure: Float = CameraHelper.exposureValueStringToFloat(value)
                if (!currentCameraSettingState.autoExposure) {
                    val (shutter, iso) = CameraHelper.adjustExposure(Math.pow(2.0, (exposure - currentCameraSettingState.currentExposureValue).toDouble()), currentCameraSettingState)
                    newCameraSettingState = currentCameraSettingState.copy(
                            currentExposureValue = exposure,
                            currentShutterValue = shutter,
                            currentIsoValue = iso
                    )
                } else {
                    newCameraSettingState = currentCameraSettingState.copy(
                            currentExposureValue = exposure,
                    )
                }
            }
            CameraSettingMode.WHITE_BALANCE -> {
                val wb: Int = CameraHelper.getWhiteBalanceModes().filter { value == it.value }.keys.first()
                newCameraSettingState = currentCameraSettingState.copy(
                        cameraCurrentWhiteBalanceMode = wb,
                )
            }
            else -> {
                Log.e("CameraInput", "updateCameraSettingValue called with unexpected settingMode " + settingMode)
                return
            }
        }

        if (!currentCameraSettingState.autoExposure)
            updateCaptureRequestOptions(newCameraSettingState)

        lifecycleOwner?.lifecycleScope?.launch {
            _cameraSettingState.emit(
                    newCameraSettingState
            )
        }
    }

    fun setPassepartout(passepartout: RectF) {
        lifecycleOwner?.lifecycleScope?.launch {
            _cameraSettingState.emit(cameraSettingState.value.copy(
                    cameraPassepartout = passepartout
            ))
        }
    }
*/
    fun start() {
        measuring = true
        analyzingOpenGLRenderer?.measuring = true
    }

    fun stop() {
        measuring = false
        analyzingOpenGLRenderer?.measuring = false
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun setDefaultCameraSettingValueIfAvailable(setting: String?): CameraSettingState {
        parseMapFromString(setting)

        var isoCurrentValue = 100
        var shutterSpeedCurrentValue: Long = CameraHelper.stringToNanoseconds("1/60")
        var apertureCurrentValue: Float = 1.0f
        var currentExposureValue: Float = 0.0f

        lockedSettings?.get("iso")?.takeIf(String::isNotEmpty)?.toIntOrNull()?.let {
            isoCurrentValue = it
        }

        lockedSettings?.get("shutter_speed")?.takeIf(String::isNotEmpty)?.let {
            shutterSpeedCurrentValue = CameraHelper.stringToNanoseconds(it)
        }

        lockedSettings?.get("exposure")?.takeIf(String::isNotEmpty)?.toFloatOrNull()?.let {
            currentExposureValue = it
        }

        lockedSettings?.get("aperture")?.takeIf(String::isNotEmpty)?.toFloatOrNull()?.let {
            apertureCurrentValue = it
        }

        return CameraSettingState(
                currentIsoValue = isoCurrentValue,
                currentShutterValue = shutterSpeedCurrentValue,
                currentApertureValue = apertureCurrentValue,
                currentExposureValue = currentExposureValue
        )
    }

    /*
   * Input as String = "shutter_speed=1/60, iso=100"
   * Output as Map<String,String> = {shutter_speed = 1/60, iso = 100}
   * */
    private fun parseMapFromString(setting: String?) {
        val lockedSettingsChar = setting?.split(",")
        lockedSettingsChar?.let { chars ->
            for (pair in chars) {
                // User might not provide the default value
                if (!pair.contains("=")) {
                    this.lockedSettings?.set(key = pair.trim(), value = "")
                } else {
                    val (key, value) = pair.split("=")
                    this.lockedSettings?.set(key.trim(), value.trim())
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    override fun newExposureStatistics(minRGB: Double, maxRGB: Double, meanLuma: Double) {
        if (cameraSettingState.value.autoExposure) {
            lifecycleOwner?.lifecycleScope?.launch {
                val state = cameraSettingState.value
                var adjust = 1.0
                var targetExposure = 0.5* Math.pow(2.0, state.currentExposureValue.toDouble())
                if (targetExposure > 0.95)
                    targetExposure = 0.95
                when (aeStrategy) {
                    AEStrategy.mean -> {
                        adjust = 1.0 - 0.1 * (meanLuma - targetExposure)
                    }
                    AEStrategy.avoidUnderxposure -> {
                        if (minRGB > 0.2) {
                            adjust = 1.0 - 0.1 * (meanLuma - targetExposure)
                        } else if (minRGB > 0.1) {
                            adjust = 1.0 - 0.1 * (minRGB - 0.25)
                        } else {
                            adjust = 1.0 - 0.2 * (minRGB - 0.25)
                        }
                    }
                    AEStrategy.avoidOverexposure -> {
                        if (maxRGB < 0.8) {
                            adjust = 1.0 - 0.1 * (meanLuma - targetExposure)
                        } else if (maxRGB < 0.9) {
                            adjust = 1.0 - 0.1 * (maxRGB - 0.75)
                        } else {
                            adjust = 1.0 - 0.2 * (maxRGB - 0.75)
                        }
                    }
                }

                val (shutter, iso) = CameraHelper.adjustExposure(adjust, state)

                var newCameraSettingState = state.copy(
                        currentIsoValue = iso,
                        currentShutterValue = shutter
                )

                //updateCaptureRequestOptions(newCameraSettingState) TODO

                lifecycleOwner?.lifecycleScope?.launch {
                    _cameraSettingState.emit(
                            newCameraSettingState
                    )
                }
            }
        }
    }

}
