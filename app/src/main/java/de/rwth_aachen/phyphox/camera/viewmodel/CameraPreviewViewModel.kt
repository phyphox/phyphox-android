package de.rwth_aachen.phyphox.camera.viewmodel

/**
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.Preview.SurfaceProvider
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rwth_aachen.phyphox.camera.CameraInput
import de.rwth_aachen.phyphox.camera.model.CameraState
import de.rwth_aachen.phyphox.camera.model.CameraUiState
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisState
import de.rwth_aachen.phyphox.camera.model.SettingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraPreviewViewModel : ViewModel() {

    private val _previewUiState: MutableStateFlow<CameraUiState> =
        MutableStateFlow(CameraUiState())

    val previewUiState : StateFlow<CameraUiState> = _previewUiState

    val cameraInput: CameraInput = CameraInput()

    private lateinit var imageAnalysis: ImageAnalysis

    private var camera: Camera? = null

    init {
        initializeCamera()
    }

    private fun initializeCamera() {
        viewModelScope.launch {
            val currentCameraUiState = _previewUiState.value

            val availableSettings = listOf(
                SettingMode.ISO,
                SettingMode.SHUTTER_SPEED,
                SettingMode.APERTURE
            ).filter {
                cameraInput.cameraSettings.contains(it)
            }

            _previewUiState.emit(
                previewUiState.value.copy(
                    cameraState =  CameraState.READY,
                    availableSettings = availableSettings
                )
            )

        }
    }

    fun startCameraPreviewView(
        lifecycleOwner: LifecycleOwner,
        context : Context
    ): PreviewView {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val previewView = PreviewView(context)
        val preview =
            setUpPreviewWithExposure(
                CameraMetadata.CONTROL_AE_MODE_ON,
                600,
                100000000,
                0.5f ).build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val camSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        try {
            camera = cameraProviderFuture.get().bindToLifecycle(
                lifecycleOwner,
                camSelector,
                preview,
                imageAnalysis
            )

            viewModelScope.launch {
                _previewUiState.emit(_previewUiState.value.copy(cameraState =  CameraState.READY))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return  previewView

    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setUpPreviewWithExposure(exposureState: Int, iso: Int, shutterSpeed: Long, aperture: Float ): Preview.Builder{
        val previewBuilder = Preview.Builder()
        val extender = Camera2Interop.Extender(previewBuilder)

        val cameraInfo = camera?.cameraInfo?.let { Camera2CameraInfo.from(it) }
        val sensitivityRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureTimeRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val apertureRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)

        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, exposureState)
        extender.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
        extender.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeed)
        extender.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, aperture)

        return previewBuilder
    }

}
*/
