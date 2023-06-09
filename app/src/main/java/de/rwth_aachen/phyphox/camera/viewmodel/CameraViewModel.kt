package de.rwth_aachen.phyphox.camera.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.util.concurrent.ListenableFuture
import de.rwth_aachen.phyphox.ExpView
import de.rwth_aachen.phyphox.PhyphoxExperiment
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.helper.CameraInput
import de.rwth_aachen.phyphox.camera.model.CameraSettingState
import de.rwth_aachen.phyphox.camera.model.CameraSettingValueState
import de.rwth_aachen.phyphox.camera.model.CameraState
import de.rwth_aachen.phyphox.camera.model.CameraUiState
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisState
import de.rwth_aachen.phyphox.camera.model.SettingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraViewModel(private val application: Application): ViewModel() {

    val TAG = "CameraViewModel"
    private lateinit var cameraProviderListenableFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraProvider: ProcessCameraProvider

    private var camera: Camera? = null

    private lateinit var preview: Preview
    private lateinit var imageAnalysis: ImageAnalysis

    private val _cameraUiState: MutableStateFlow<CameraUiState> = MutableStateFlow(CameraUiState())
    private val _cameraSettingValueState : MutableStateFlow<CameraSettingValueState> = MutableStateFlow(CameraSettingValueState())
    private val _imageAnalysisUiState: MutableStateFlow<ImageAnalysisState> = MutableStateFlow(
        ImageAnalysisState.ImageAnalysisNotReady
    )

    val cameraUiState: Flow<CameraUiState> = _cameraUiState
    val cameraSettingValueState : Flow<CameraSettingValueState> = _cameraSettingValueState
    val imageAnalysisUiState: Flow<ImageAnalysisState> = _imageAnalysisUiState

    lateinit var cameraInput: CameraInput
    lateinit var phyphoxExperiment: PhyphoxExperiment

    fun initializeCamera() {

        viewModelScope.launch {
            val currentCameraUiState = _cameraUiState.value

            cameraProviderListenableFuture = ProcessCameraProvider.getInstance(application)
            val availableCameraLens =
                listOf(CameraSelector.LENS_FACING_BACK,
                       CameraSelector.LENS_FACING_FRONT
                )
                    /**
                    .filter { lensFacing ->
                    //TODO check null
                    cameraProvider.hasCamera(cameraLensToSelector(lensFacing))
                }
                    */

            val availableSettings = listOf(
                SettingMode.ISO,
                SettingMode.SHUTTER_SPEED,
                SettingMode.APERTURE
            ).filter {
                cameraInput.cameraSettings.contains(it)
            }

            val newCameraUiState = currentCameraUiState.copy(
                cameraState = CameraState.READY,
                availableSettings = availableSettings,
                availableCameraLens= availableCameraLens
            )

            _cameraUiState.emit(newCameraUiState)
        }
    }

    fun initializeCameraSettingValue() {
        viewModelScope.launch {
            val currentCurrentUiState = _cameraSettingValueState.value
            val newCameraUiState = currentCurrentUiState.copy(
                    currentApertureValue = cameraInput.apertureCurrentValue,
                    currentIsoValue = cameraInput.isoCurrentValue,
                    currentShutterValue = cameraInput.shutterSpeedCurrentValue,
            )
            _cameraSettingValueState.emit(newCameraUiState)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun startCameraPreviewView(previewView: PreviewView, lifecycleOwner: LifecycleOwner, withExposure: Boolean) {

        cameraProviderListenableFuture.addListener({
            try {
                cameraProvider = cameraProviderListenableFuture.get()
                (cameraProvider as ProcessCameraProvider?)?.let {
                    startCamera(previewView, it, lifecycleOwner, withExposure) }
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

    private fun startCamera(previewView: PreviewView, cameraProvider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner, withExposure: Boolean){
        cameraProvider.unbindAll()
        val currentCameraUiState = _cameraUiState.value

        val cameraSelector = cameraLensToSelector(currentCameraUiState.cameraLens)

        preview = setUpPreviewWithExposure(withExposure).build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis)

        setUpExposureValue()

    }

    private fun setUpDefaultPreviewBuilder(): Preview.Builder{
        return Preview.Builder().setTargetResolution(Size(640, 480))
    }

    fun stopPreview() {
        preview.setSurfaceProvider(null)
        viewModelScope.launch {
            _cameraUiState.emit(_cameraUiState.value.copy(cameraState = CameraState.PREVIEW_STOPPED))
        }
    }

    fun switchCamera() {
        val currentCameraUiState = _cameraUiState.value
        if (currentCameraUiState.cameraState == CameraState.READY) {
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
                _imageAnalysisUiState.emit(ImageAnalysisState.ImageAnalysisNotReady)
            }
        }
    }

    fun changeExposure(autoExposure: Boolean){
        val currentCameraUiState = _cameraUiState.value
        val newAutoExposure = currentCameraUiState.copy(autoExposure = autoExposure)

        viewModelScope.launch {
            _cameraUiState.emit(
                newAutoExposure.copy(
                    cameraState = CameraState.NOT_READY,
                )
            )
        }

    }

    fun restartCamera() {
        val currentCameraUiState = _cameraUiState.value
        viewModelScope.launch {
            _cameraUiState.emit(
                currentCameraUiState.copy(
                    cameraState = CameraState.NOT_READY,
                )
            )
        }
    }

    fun startAnalysis(){
        viewModelScope.launch {
            _imageAnalysisUiState.emit(ImageAnalysisState.ImageAnalysisStarted)
        }

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(application)) { image ->
            //val value = image.image?.let { photometricReader.calculateAvgRedBrightness(it) }
            //Log.d("Image value", value.toString() )

            image.close()
        }


    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setUpExposureValue(){
        val cameraInfo = camera?.cameraInfo?.let { Camera2CameraInfo.from(it) }
        val sensitivityRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureTimeRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val apertureRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)

        val currentCameraUiState = _cameraSettingValueState.value
        val newCameraUiState = currentCameraUiState.copy(
                apertureRange = apertureRange,
                shutterSpeedRange = exposureTimeRange,
                isoRange = sensitivityRange
        )
        viewModelScope.launch {
            _cameraSettingValueState.emit(newCameraUiState)
        }


    }

    @SuppressLint("UnsafeOptInUsageError")
    fun setUpPreviewWithExposure(withExposure: Boolean): Preview.Builder{
        if(!withExposure){
            return setUpDefaultPreviewBuilder()
        }

        val previewBuilder = Preview.Builder()
        val extender = Camera2Interop.Extender(previewBuilder)

        val cameraSettingValueState = _cameraSettingValueState.value
        val iso: Int = cameraSettingValueState.currentIsoValue
        val shutterSpeed: Long = cameraSettingValueState.currentShutterValue
        val aperture: Float = cameraSettingValueState.currentApertureValue


        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
        if(iso != 0) extender.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
        if(shutterSpeed != 0L) extender.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeed)
        if(aperture != 0.0f) extender.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, aperture)

        return previewBuilder
    }

    fun redrawSliders(){
        var minValue: Int = 0
        var maxValue: Int = 0
        var currentValue : Int = 0
        phyphoxExperiment.experimentViews.elementAt(0).elements.forEach {
            if(it.javaClass == ExpView.editElement::class.java){
                val editElement = it as ExpView.editElement
                if(editElement.javaClass == AppCompatSeekBar::class.java){
                    Log.d("CameraPreviewFragment", editElement.label)
                }
                if(editElement.label.equals("Shutter Speed")) {
                    maxValue = phyphoxExperiment.cameraInput.shutterSpeedRange?.upper?.toInt()!!
                    minValue = phyphoxExperiment.cameraInput.shutterSpeedRange?.lower!!.toInt()
                    currentValue = phyphoxExperiment.cameraInput.shutterSpeedCurrentValue!!.toInt()
                } else if(editElement.label.equals("ISO")) {
                    maxValue = phyphoxExperiment.cameraInput.isoRange?.upper!!
                    minValue = phyphoxExperiment.cameraInput.isoRange?.lower!!
                    currentValue = phyphoxExperiment.cameraInput.isoCurrentValue!!
                } else if(editElement.label.equals("Aperture")){
                    currentValue = phyphoxExperiment.cameraInput.apertureCurrentValue!!.toInt()
                    minValue = currentValue/ 2
                    maxValue = currentValue * 2
                }
                //editElement.removeSlider()
                //editElement.createSlider(minValue,maxValue,currentValue, false)
                viewModelScope.launch {
                    _cameraUiState.emit(
                        _cameraUiState.value.copy(
                            cameraSettingState = CameraSettingState.LOADED
                        )
                    )
                }

            }
        }
    }

    fun updateCameraSettingValue(value: String, settingMode: SettingMode){

        val currentCameraSettingState =  _cameraSettingValueState.value
        val currentCameraUiState =  _cameraUiState.value
        if(settingMode == SettingMode.ISO){
            val newCameraSettingState = currentCameraSettingState.copy(
                currentIsoValue = value.toInt()
            )


            viewModelScope.launch {
                _cameraSettingValueState.emit(newCameraSettingState)
                _cameraUiState.emit(currentCameraUiState.copy(
                    cameraState = CameraState.NOT_READY,
                    cameraSettingState = CameraSettingState.VALUE_UPDATED
                ))
            }
        } else {
            val newCameraSettingState = currentCameraSettingState.copy(
                currentShutterValue =  CameraHelper.stringToNanoseconds(value)
            )
            viewModelScope.launch {
                _cameraSettingValueState.emit(newCameraSettingState)
                _cameraUiState.emit(currentCameraUiState.copy(
                    cameraState = CameraState.NOT_READY,
                    cameraSettingState = CameraSettingState.VALUE_UPDATED
                ))
            }
        }


    }

    fun reloadCameraWithNewSetting(settingMode: SettingMode, newValue: Int){
        when (settingMode){
            SettingMode.SHUTTER_SPEED -> {
                cameraInput.shutterSpeedCurrentValue = newValue.toLong()
            }
            SettingMode.ISO -> {
                cameraInput.isoCurrentValue = newValue
            }
            SettingMode.APERTURE -> {
                cameraInput.apertureCurrentValue = newValue.toFloat()
            }
            SettingMode.NONE -> Unit
            SettingMode.AUTO_EXPOSURE -> Unit
        }

        viewModelScope.launch {
            _cameraUiState.emit(
                _cameraUiState.value.copy(
                    cameraSettingState =  CameraSettingState.LOADING
                )
            )

        }
    }


}
