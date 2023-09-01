package de.rwth_aachen.phyphox.camera.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.util.concurrent.ListenableFuture
import de.rwth_aachen.phyphox.PhyphoxExperiment
import de.rwth_aachen.phyphox.camera.Scrollable
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.helper.CameraInput
import de.rwth_aachen.phyphox.camera.helper.ImageAnalyser
import de.rwth_aachen.phyphox.camera.model.CameraSettingLevel
import de.rwth_aachen.phyphox.camera.model.CameraSettingRecyclerState
import de.rwth_aachen.phyphox.camera.model.ExposureSettingState
import de.rwth_aachen.phyphox.camera.model.CameraSettingValueState
import de.rwth_aachen.phyphox.camera.model.CameraState
import de.rwth_aachen.phyphox.camera.model.CameraUiState
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisState
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisValueState
import de.rwth_aachen.phyphox.camera.model.OverlayUpdateState
import de.rwth_aachen.phyphox.camera.model.ExposureSettingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraViewModel(private val application: Application) : ViewModel() {

    val TAG = "CameraViewModel"
    private lateinit var cameraProviderListenableFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraProvider: ProcessCameraProvider
    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var camera: Camera? = null

    lateinit var preview: Preview
    private lateinit var imageAnalysis: ImageAnalysis

    private val _cameraUiState: MutableStateFlow<CameraUiState> = MutableStateFlow(CameraUiState())
    private val _cameraSettingValueState: MutableStateFlow<CameraSettingValueState> =
        MutableStateFlow(CameraSettingValueState())
    private val _imageAnalysisValueState: MutableStateFlow<ImageAnalysisValueState> = MutableStateFlow(
        ImageAnalysisValueState()
    )

    val cameraUiState: Flow<CameraUiState> = _cameraUiState
    val cameraSettingValueState: Flow<CameraSettingValueState> = _cameraSettingValueState
    val imageAnalysisUiState: Flow<ImageAnalysisValueState> = _imageAnalysisValueState

    lateinit var cameraInput: CameraInput
    lateinit var phyphoxExperiment: PhyphoxExperiment

    val imageAnalyser: ImageAnalyser = ImageAnalyser(this)

    lateinit var scrollable : Scrollable

    fun initializeCamera() {

        viewModelScope.launch {
            val currentCameraUiState = _cameraUiState.value

            cameraProviderListenableFuture = ProcessCameraProvider.getInstance(application)
            val availableCameraLens =
                listOf(
                    CameraSelector.LENS_FACING_BACK,
                    CameraSelector.LENS_FACING_FRONT
                )

            val newCameraUiState = currentCameraUiState.copy(
                cameraState = CameraState.READY,
                availableCameraLens = availableCameraLens,
                editableCameraSettings = cameraInput.lockedSettings
            )

            _cameraUiState.emit(newCameraUiState)
        }
    }

    private fun cameraInitialized() {
        viewModelScope.launch {
            val currentCameraUiState = _cameraUiState.value
            _cameraUiState.emit(
                currentCameraUiState.copy(
                    cameraState = CameraState.LOADED
                )
            )
        }
    }

    fun initializeCameraSettingValue() {
        viewModelScope.launch {
            val currentCurrentUiState = _cameraSettingValueState.value
            val newCameraUiState = currentCurrentUiState.copy(
                currentApertureValue = cameraInput.apertureCurrentValue,
                currentIsoValue = cameraInput.isoCurrentValue,
                currentShutterValue = cameraInput.shutterSpeedCurrentValue,
                currentExposureValue = cameraInput.currentExposureValue,
                disabledAutoExposure = cameraInput.autoExposure,
                cameraSettingLevel = when (cameraInput.exposureAdjustmentLevel) {
                    1 -> CameraSettingLevel.BASIC
                    2 -> CameraSettingLevel.INTERMEDIATE
                    else -> CameraSettingLevel.ADVANCE
                }
            )
            _cameraSettingValueState.emit(newCameraUiState)
        }
    }

    fun setUpCameraDimension(height: Int, width: Int){
        viewModelScope.launch {
            _cameraUiState.emit(
                _cameraUiState.value.copy(
                    cameraHeight = height,
                    cameraWidth = width
                )
            )
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun startCameraPreviewView(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        withExposure: Boolean
    ) {

        cameraProviderListenableFuture.addListener({
            try {
                cameraProvider = cameraProviderListenableFuture.get()
                (cameraProvider as ProcessCameraProvider?)?.let {
                    startCamera(previewView, it, lifecycleOwner, withExposure)
                }
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(application))
    }

    private fun cameraLensToSelector(@CameraSelector.LensFacing lensFacing: Int): CameraSelector =
        when (lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraSelector.LENS_FACING_BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> throw IllegalArgumentException("Invalid lens facing type: $lensFacing")
        }

    private fun startCamera(
        previewView: PreviewView,
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        withExposure: Boolean
    ) {
        cameraProvider.unbindAll()
        val currentCameraUiState = _cameraUiState.value

        val cameraSelector = cameraLensToSelector(currentCameraUiState.cameraLens)

        preview = setUpPreviewWithExposure(withExposure).build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor, imageAnalyser)

        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )

        setupZoomControl()
        loadAndSetupExposureSettingRanges()
        cameraInitialized()

    }

    fun getCameraRect(): Rect{
       return _cameraUiState.value.cameraPassepartout

    }

    fun switchCamera() {
        val currentCameraUiState = _cameraUiState.value
        if (currentCameraUiState.cameraState == CameraState.LOADED) {
            // To switch the camera lens, there has to be at least 2 camera lenses
            if (currentCameraUiState.availableCameraLens.size == 1) return

            val camLensFacing = currentCameraUiState.cameraLens
            // Toggle the lens facing
            val newCameraUiState = if (camLensFacing == CameraSelector.LENS_FACING_BACK) {
                currentCameraUiState.copy(cameraLens = CameraSelector.LENS_FACING_FRONT)
            } else {
                currentCameraUiState.copy(cameraLens = CameraSelector.LENS_FACING_BACK)
            }

            viewModelScope.launch {
                _cameraUiState.emit(
                    newCameraUiState.copy(
                        cameraState = CameraState.NOT_READY,
                    )
                )
                _imageAnalysisValueState.emit(_imageAnalysisValueState.value.copy(
                    imageAnalysisState = ImageAnalysisState.IMAGE_ANALYSIS_NOT_READY
                ))
            }
        }
    }

    fun changeExposure(autoExposure: Boolean) {
        viewModelScope.launch {
            _cameraUiState.emit(
                _cameraUiState.value.copy(
                    cameraState = CameraState.NOT_READY,
                )
            )
            _cameraSettingValueState.emit(
                _cameraSettingValueState.value.copy(
                    disabledAutoExposure = autoExposure
                )
            )
        }

    }

    fun cameraReady() {
        viewModelScope.launch {
            _imageAnalysisValueState.emit(_imageAnalysisValueState.value.copy(
                imageAnalysisState = ImageAnalysisState.IMAGE_ANALYSIS_READY
            ))
        }

    }

    fun imageAnalysisStarted(){
        viewModelScope.launch {
            _imageAnalysisValueState.emit(_imageAnalysisValueState.value.copy(
                imageAnalysisState = ImageAnalysisState.IMAGE_ANALYSIS_STARTED
            ))
        }
    }

    fun imageAnalysisFinished(){
        viewModelScope.launch {
            _imageAnalysisValueState.emit(_imageAnalysisValueState.value.copy(
                imageAnalysisState = ImageAnalysisState.IMAGE_ANALYSIS_FINISHED
            ))
        }
    }

    fun updateImageAnalysisLuminance(luminance: Double, currentTime: Double){
        viewModelScope.launch {
            _imageAnalysisValueState.emit(_imageAnalysisValueState.value.copy(
                luminance = luminance,
                currentTimeStamp = currentTime
            ))
        }
    }

    fun updateImageAnalysisColor(colorCode: String){
        viewModelScope.launch {
            _imageAnalysisValueState.emit(_imageAnalysisValueState.value.copy(
                colorCode = colorCode
            ))
        }
    }

    fun getColorCode(): String {
        return _imageAnalysisValueState.value.colorCode
    }

    @RequiresApi(Build.VERSION_CODES.M)
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

        var exposureStep = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat()
        if(exposureStep == null)
            exposureStep = 1F

        val exposureLower =
            cameraInfo?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.lower
        val exposureUpper =
            cameraInfo?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.upper

        val exposureRange = if (exposureLower != null && exposureUpper != null)
            CameraHelper.getExposureValuesFromRange(exposureLower, exposureUpper, exposureStep).map { it.toString() }
         else {
            emptyList()
        }

        val awbAvailableModes = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
        val maxRegionsAWB = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0
        var awbLockAvailable = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)

        val sensorPhysicalSize = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        cameraInput.sensorPixelHeight.clear(true)
        cameraInput.sensorPixelHeight.append(sensorPhysicalSize?.height?.toDouble() ?: 0.0)
        cameraInput.sensorPixelWidth.clear(true)
        cameraInput.sensorPixelWidth.append(sensorPhysicalSize?.width?.toDouble() ?: 0.0)


        val currentCameraUiState = _cameraSettingValueState.value
        val newCameraUiState = currentCameraUiState.copy(
            apertureRange = apertureRange,
            shutterSpeedRange = shutterSpeedRange,
            isoRange = isoRange,
            exposureRange = exposureRange,
            exposureStep = exposureStep,
            exposureSettingState = ExposureSettingState.LOADED,
            cameraMaxRegionAWB =  maxRegionsAWB,
            cameraWhiteBalanceModes = CameraHelper.getWhiteBalanceModes().filter { awbAvailableModes.contains(it.key) }.keys.toList()

        )


        viewModelScope.launch {
            _cameraSettingValueState.emit(newCameraUiState)
        }

        viewModelScope.launch {
            _cameraUiState.emit(
                _cameraUiState.value.copy(
                    physicalSensorPixelHeight = sensorPhysicalSize?.height ?: 0.0f,
                    physicalSensorPixelWidth =  sensorPhysicalSize?.width ?: 0.0f
                )
            )
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun setUpPreviewWithExposure(withExposure: Boolean): Preview.Builder {
        if (!withExposure) return Preview.Builder()

        val previewBuilder = Preview.Builder()
        val extender = Camera2Interop.Extender(previewBuilder)

        val cameraSettingValueState = _cameraSettingValueState.value

        when (cameraSettingValueState.cameraSettingLevel) {
            CameraSettingLevel.BASIC -> return Preview.Builder()
            CameraSettingLevel.INTERMEDIATE -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON
                )

                val exposure: Int = CameraHelper.getActualValueFromExposureCompensation(
                    cameraSettingValueState.currentExposureValue,
                    cameraSettingValueState.exposureStep
                )

                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_LOCK,
                    true
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                    exposure
                )

                cameraInput.exposureDataBuffer.clear(true)
                cameraInput.exposureDataBuffer.append(exposure.toDouble())

                return previewBuilder
            }
            CameraSettingLevel.ADVANCE -> {
                extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)

                val iso: Int = cameraSettingValueState.currentIsoValue
                val shutterSpeed: Long = cameraSettingValueState.currentShutterValue
                val aperture: Float = cameraSettingValueState.currentApertureValue

                if (iso != 0) extender.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                if (shutterSpeed != 0L) extender.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeed)
                if (aperture != 0.0f) extender.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, aperture)

                cameraInput.shutterSpeedDataBuffer.clear(true)
                cameraInput.exposureDataBuffer.append(shutterSpeed.toDouble())

                cameraInput.isoDataBuffer.clear(true)
                cameraInput.isoDataBuffer.append(iso.toDouble())

                cameraInput.apertureDataBuffer.clear(true)
                cameraInput.apertureDataBuffer.append(aperture.toDouble())

                setupWhiteBalance(cameraSettingValueState, extender)

                return previewBuilder
            }
        }

    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun setupWhiteBalance(cameraSettingValueState: CameraSettingValueState, extender:  Camera2Interop.Extender<Preview> ){
        val currentMode = cameraSettingValueState.cameraCurrentWhiteBalanceMode
        val currentValue = cameraSettingValueState.cameraCurrentWhiteBalanceManualValue

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

    fun openCameraSettingValue(settingMode: ExposureSettingMode) {
        val currentCameraSettingState = _cameraSettingValueState.value
        viewModelScope.launch {
            _cameraSettingValueState.emit(
                currentCameraSettingState.copy(
                    exposureSettingState = ExposureSettingState.LOAD_LIST,
                    settingMode = settingMode,
                )
            )
        }
    }

    fun updateViewStateOfRecyclerView(showRecyclerview: Boolean) {
        val currentCameraSettingState = _cameraSettingValueState.value
        viewModelScope.launch {
            _cameraSettingValueState.emit(
                currentCameraSettingState.copy(
                    exposureSettingState = ExposureSettingState.LOAD_FINISHED,
                    cameraSettingRecyclerState = if (showRecyclerview) CameraSettingRecyclerState.TO_SHOW else CameraSettingRecyclerState.TO_HIDE,
                )
            )
        }
    }

    fun cameraSettingOpened() {
        val currentCameraSettingState = _cameraSettingValueState.value
        viewModelScope.launch {
            _cameraSettingValueState.emit(
                currentCameraSettingState.copy(
                    exposureSettingState = ExposureSettingState.LOAD_FINISHED,
                )
            )
        }
    }

    fun updateCurrentWhiteBalanceValue(value: FloatArray){
        val currentCameraSettingState = _cameraSettingValueState.value
        viewModelScope.launch {
            _cameraSettingValueState.emit(
                currentCameraSettingState.copy(
                    cameraCurrentWhiteBalanceManualValue = value,
                    exposureSettingState = ExposureSettingState.VALUE_UPDATED
                )
            )
        }
    }

    fun updateCameraSettingValue(value: String, settingMode: ExposureSettingMode) {

        val currentCameraSettingState = _cameraSettingValueState.value
        val currentCameraUiState = _cameraUiState.value

        val newCameraSettingState: CameraSettingValueState = when (settingMode) {
            ExposureSettingMode.ISO -> {
                currentCameraSettingState.copy(
                    currentIsoValue = value.toInt(),
                    exposureSettingState = ExposureSettingState.VALUE_UPDATED
                )
            }
            ExposureSettingMode.SHUTTER_SPEED -> {
                currentCameraSettingState.copy(
                    currentShutterValue = CameraHelper.stringToNanoseconds(value),
                    exposureSettingState = ExposureSettingState.VALUE_UPDATED
                )
            }
            ExposureSettingMode.WHITE_BALANCE -> {
                currentCameraSettingState.copy(
                    cameraCurrentWhiteBalanceMode = CameraHelper.getWhiteBalanceModes().filter { value == it.value }.keys.first(),
                    exposureSettingState = ExposureSettingState.VALUE_UPDATED
                )
            }
            else -> {
                currentCameraSettingState.copy(
                    currentExposureValue = value.toFloat(),
                    exposureSettingState = ExposureSettingState.VALUE_UPDATED
                )
            }
        }
        viewModelScope.launch {
            _cameraSettingValueState.emit(newCameraSettingState)
            _cameraUiState.emit(
                currentCameraUiState.copy(
                    cameraState = CameraState.NOT_READY,
                )
            )
        }

    }

    fun updateCameraOverlayValue() {
        val height = _cameraUiState.value.cameraHeight
        val width = _cameraUiState.value.cameraWidth

        val xmin: Float = Math.min(cameraInput.x1, cameraInput.x2)
        val xmax: Float = Math.max(cameraInput.x1, cameraInput.x2)
        val ymin: Float = Math.min(cameraInput.y1, cameraInput.y2)
        val ymax: Float = Math.max(cameraInput.y1, cameraInput.y2)

        val inner = RectF(
            (1.0f - ymax) * width,
            xmin * height,
            (1.0f - ymin) * width,
            xmax * height
        )

        viewModelScope.launch {
            _cameraUiState.emit(
                _cameraUiState.value.copy(
                    cameraPassepartout = inner.toRect(),
                    overlayUpdateState = OverlayUpdateState.UPDATE
            ))
        }

    }

    fun overlayUpdated(){
        viewModelScope.launch { _cameraUiState.emit(_cameraUiState.value.copy(overlayUpdateState = OverlayUpdateState.UPDATE_DONE)) }
    }
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun  setupZoomControl(){
        val cameraInfo = camera?.cameraInfo?.let { Camera2CameraInfo.from(it) }
        val zoomStateValue = camera?.cameraInfo?.zoomState?.value

        val maxZoomRatio =  zoomStateValue?.maxZoomRatio ?: 1f
        val minZoomRatio =  zoomStateValue?.minZoomRatio ?: 0f
        val zoomRatio =  zoomStateValue?.zoomRatio ?: 1f
        val linearZoom =  zoomStateValue?.linearZoom ?: 1f

        val maxOpticalZoom: FloatArray? = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)

        val ratios: MutableList<Float> = CameraHelper.computeZoomRatios(minZoomRatio, maxZoomRatio)

        viewModelScope.launch {
            _cameraSettingValueState.emit(_cameraSettingValueState.value.copy(
                cameraMinZoomRatio = minZoomRatio,
                cameraMaxZoomRatio = maxZoomRatio,
                cameraZoomRatio = zoomRatio,
                cameraLinearRatio = linearZoom,
                cameraZoomRatioConverted = ratios,
                cameraMaxOpticalZoom = maxOpticalZoom?.last()
            ))
        }

    }

}
