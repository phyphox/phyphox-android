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
import de.rwth_aachen.phyphox.camera.helper.CameraInput
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
    private lateinit var cameraProviderListenableFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraProvider: ProcessCameraProvider

    private var camera: Camera? = null

    private lateinit var preview: Preview
    private lateinit var imageAnalysis: ImageAnalysis

    private val _cameraUiState: MutableStateFlow<CameraUiState> = MutableStateFlow(CameraUiState())
    private val _imageAnalysisUiState: MutableStateFlow<ImageAnalysisState> = MutableStateFlow(
        ImageAnalysisState.ImageAnalysisNotReady
    )

    val cameraUiState: Flow<CameraUiState> = _cameraUiState
    val imageAnalysisUiState: Flow<ImageAnalysisState> = _imageAnalysisUiState

    lateinit var cameraInput: CameraInput

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

    @SuppressLint("UnsafeOptInUsageError")
    fun startCameraPreviewView(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {

        cameraProviderListenableFuture.addListener({
            try {
                cameraProvider = cameraProviderListenableFuture.get()
                (cameraProvider as ProcessCameraProvider?)?.let {
                    startCamera(previewView, it, lifecycleOwner) }
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

    private fun startCamera(previewView: PreviewView, cameraProvider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner){
        cameraProvider.unbindAll()
        val currentCameraUiState = _cameraUiState.value

        val cameraSelector = cameraLensToSelector(currentCameraUiState.cameraLens)

        preview = setUpPreviewWithExposure(CameraMetadata.CONTROL_AE_MODE_ON)
            .setTargetResolution(Size(640, 480)) // Set the desired resolution
            .build().also {
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
    fun setUpPreviewWithExposure(exposureState: Int): Preview.Builder{
        val previewBuilder = Preview.Builder()
        val extender = Camera2Interop.Extender(previewBuilder)

        val cameraInfo = camera?.cameraInfo?.let { Camera2CameraInfo.from(it) }
        val sensitivityRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureTimeRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val apertureRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)

        cameraInput.apertureRange = apertureRange
        cameraInput.shutterSpeedRange = exposureTimeRange
        cameraInput.isoRange = sensitivityRange

        var iso: Int? = cameraInput.isoCurrentValue
        var shutterSpeed: Long? = cameraInput.shutterSpeedCurrentValue
        var aperture: Float? = cameraInput.apertureCurrentValue


        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, exposureState)
        if(iso != null) extender.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
        if(shutterSpeed != null) extender.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeed)
        if(aperture !=null) extender.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, aperture)

        return previewBuilder
    }


}
