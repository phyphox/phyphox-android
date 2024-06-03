package de.rwth_aachen.phyphox.camera

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraX
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.common.util.concurrent.ListenableFuture
import de.rwth_aachen.phyphox.DataBuffer
import de.rwth_aachen.phyphox.DataOutput
import de.rwth_aachen.phyphox.ExperimentTimeReference
import de.rwth_aachen.phyphox.camera.analyzer.AnalyzingOpenGLRenderer
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.model.CameraSettingLevel
import de.rwth_aachen.phyphox.camera.model.CameraSettingMode
import de.rwth_aachen.phyphox.camera.model.CameraState
import de.rwth_aachen.phyphox.camera.model.CameraSettingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.Serializable
import java.util.Vector
import java.util.concurrent.ExecutionException
import java.util.concurrent.locks.Lock

/*
* Takes the essential input from the PhyphoxExperiment which are provided from XML.
* */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraInput : Serializable, AnalyzingOpenGLRenderer.ExposureStatisticsListener {
    @Transient var camera: Camera? = null
    @Transient private lateinit var cameraProviderListenableFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null

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

    @SuppressLint("UnsafeOptInUsageError")
    fun setUpPreviewUseCase(): Preview.Builder {
        val previewBuilder = Preview.Builder()
        val extender = Camera2Interop.Extender(previewBuilder)

        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)

        return previewBuilder

    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun setupWhiteBalance(cameraSettingState: CameraSettingState, extender:  Camera2Interop.Extender<Preview> ){
        val currentMode = cameraSettingState.cameraCurrentWhiteBalanceMode
        val currentValue = cameraSettingState.cameraCurrentWhiteBalanceManualValue

        when(currentMode) {
            0 -> {
                //extender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                extender.setCaptureRequestOption(
                        CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
                )
                if(currentValue.size == 4){
                    extender.setCaptureRequestOption(
                            CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector(currentValue[0], currentValue[1],  currentValue[2], currentValue[3])
                    )
                }
            }
            else -> extender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, currentMode)

        }
    }

    fun startCameraFromProvider(lifecycleOwner: LifecycleOwner, application: Application) {
        this.lifecycleOwner = lifecycleOwner
        cameraProviderListenableFuture = ProcessCameraProvider.getInstance(application)

        cameraProviderListenableFuture.addListener({
            try {
                cameraProvider = cameraProviderListenableFuture.get()
                (cameraProvider as ProcessCameraProvider?)?.let {
                    it.unbindAll()
                    startCamera()
                }
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(application))

        lifecycleOwner.lifecycleScope.launch {
            cameraSettingState.collectLatest { cameraSettingState ->
                when (cameraSettingState.cameraState) {
                    CameraState.NONE -> Unit
                    CameraState.INITIALIZING -> {
                        setupZoomControl()
                        loadAndSetupExposureSettingRanges()
                        updateCaptureRequestOptions(cameraSettingState)
                        _cameraSettingState.emit(
                                _cameraSettingState.value.copy(
                                        cameraState = CameraState.RUNNING
                                )
                        )
                    }
                    CameraState.RUNNING -> Unit
                    CameraState.RESTART -> {
                        Log.d("TEST", "RESTART")
                        cameraProvider?.unbindAll()
                        analyzingOpenGLRenderer?.releaseCameraSurface({
                            lifecycleOwner.lifecycleScope.launch {
                                startCamera()
                            }
                        }
                        )
                    }
                }
            }
        }
    }

    fun startCamera() {
        Log.d("TEST", "startCamera")
        if (analyzingOpenGLRenderer == null)
            analyzingOpenGLRenderer = AnalyzingOpenGLRenderer(this, dataLock, cameraSettingState, this)

        val cameraSelector = CameraHelper.cameraLensToSelector(cameraSettingState.value.currentLens)

        val preview = setUpPreviewUseCase().build().also {
            it.setSurfaceProvider(analyzingOpenGLRenderer)
        }

        lifecycleOwner?.let {
            camera = cameraProvider?.bindToLifecycle(
                    it,
                    cameraSelector,
                    preview
            )

            it.lifecycleScope.launch {
                _cameraSettingState.emit(
                        cameraSettingState.value.copy(
                                cameraState = CameraState.INITIALIZING
                        )
                )
            }
        }
    }

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

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun loadAndSetupExposureSettingRanges() {
        val cameraInfo = camera?.cameraInfo?.let { Camera2CameraInfo.from(it) }

        // from the cameraCharacteristic, isoRange is acquired which is in the form of of Range<Int>,
        // which is then mapped into List<String>
        val isoRange =
                cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                        .let { isoRange_ ->
                            isoRange_?.lower?.let { lower ->
                                isoRange_.upper?.let { upper ->
                                    CameraHelper.isoRange(lower, upper).map { it.toString() }
                                }
                            }
                        }

        // from the cameraCharacteristic, shutter speed range is acquired which is in the form of of Range<Long>,
        // which is then mapped into List<String>
        val shutterSpeedRange =
                cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                        .let { shutterSpeedRange_ ->
                            shutterSpeedRange_?.lower?.let { lower ->
                                shutterSpeedRange_.upper?.let { upper ->
                                    CameraHelper.shutterSpeedRange(lower, upper)
                                            .map { "" + it.numerator + "/" + it.denominator }
                                }
                            }

                        }

        // from the cameraCharacteristic, aperture range is acquired which is in the form of of FloatArray,
        // which is then mapped into List<String>
        val apertureRange =
                cameraInfo?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                        .let { apertureRange_ ->
                            apertureRange_?.map { it.toString() }
                        }
        val fixedAperture = if (apertureRange?.contains(cameraSettingState.value.currentApertureValue.toString()) ?: true) cameraSettingState.value.currentApertureValue else apertureRange!!.first().toFloat()

        val exposureRange = CameraHelper.getExposureValuesDefaultList()

        val awbAvailableModes = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
        val maxRegionsAWB = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0

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

    // List of buffers (variables) that is provided in the xml
    lateinit var buffers: Vector<DataOutput>

    lateinit var cameraFeature: PhyphoxCameraFeature
    var lockedSettings: MutableMap<String, String>? = mutableMapOf()

    // Status of the play and pause for image analysis
    var measuring = false

    lateinit var experimentTimeReference: ExperimentTimeReference

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
            x1: Float,
            x2: Float,
            y1: Float,
            y2: Float,
            buffers: Vector<DataOutput>,
            lock: Lock,
            experimentTimeReference: ExperimentTimeReference,
            cameraFeature: PhyphoxCameraFeature,
            autoExposure: Boolean,
            lockedSettings: String?,
            aeStrategy: AEStrategy,
            thresholdAnalyzerThreshold: Double
    ) {

        this._cameraSettingState = MutableStateFlow<CameraSettingState>(
                setDefaultCameraSettingValueIfAvailable(lockedSettings).copy(
                        cameraPassepartout = RectF(x1, y1, x2, y2),
                        autoExposure = autoExposure
                )
        )

        cameraSettingState = _cameraSettingState

        this.buffers = buffers
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
    }

    enum class PhyphoxCameraFeature {
        Photometric, ColorDetector, Spectroscopy, MotionAnalysis, OCR
    }

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
                        if (minRGB > 0.25) {
                            adjust = 1.0 - 0.1 * (meanLuma - targetExposure)
                        } else if (minRGB > 0.1) {
                            adjust = 1.0 - 0.1 * (minRGB - 0.25)
                        } else {
                            adjust = 1.0 - 0.2 * (minRGB - 0.25)
                        }
                    }
                    AEStrategy.avoidOverexposure -> {
                        if (maxRGB < 0.75) {
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

                updateCaptureRequestOptions(newCameraSettingState)

                lifecycleOwner?.lifecycleScope?.launch {
                    _cameraSettingState.emit(
                            newCameraSettingState
                    )
                }
            }
        }
    }

}
