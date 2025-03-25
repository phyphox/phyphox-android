package de.rwth_aachen.phyphox.camera.viewmodel

import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.core.view.doOnLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.rwth_aachen.phyphox.camera.Scrollable
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.CameraInput
import de.rwth_aachen.phyphox.camera.model.CameraState
import de.rwth_aachen.phyphox.camera.model.CameraPreviewState
import de.rwth_aachen.phyphox.camera.model.CameraSettingLevel
import de.rwth_aachen.phyphox.camera.model.CameraUiState
import de.rwth_aachen.phyphox.camera.model.OverlayUpdateState
import de.rwth_aachen.phyphox.camera.model.CameraSettingMode
import de.rwth_aachen.phyphox.camera.model.ShowCameraControls
import de.rwth_aachen.phyphox.camera.ui.CameraPreviewScreen
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import de.rwth_aachen.phyphox.camera.viewstate.CameraZoomControlViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraViewModel() : ViewModel() {

    val TAG = "CameraViewModel"

    lateinit var preview: Preview

    private val _cameraUiState: MutableStateFlow<CameraUiState> = MutableStateFlow(CameraUiState())
    val cameraUiState: StateFlow<CameraUiState> = _cameraUiState
    var cameraScreenViewState: MutableStateFlow<CameraScreenViewState>? = null

    lateinit var cameraInput: CameraInput

    lateinit var scrollable : Scrollable

    fun setControlSettings(showCameraControls: ShowCameraControls, cameraSettingLevel: CameraSettingLevel) {
        _cameraUiState.value = cameraUiState.value.copy(
                showCameraControls = showCameraControls,
                cameraSettingLevel = cameraSettingLevel
        )
    }
    fun start(cameraScreenViewState: MutableStateFlow<CameraScreenViewState>, cameraPreviewScreen: CameraPreviewScreen) {
        this.cameraScreenViewState = cameraScreenViewState
        viewModelScope.launch {
            cameraUiState.collectLatest { cameraUiState ->
                when (cameraUiState.cameraPreviewState) {
                    CameraPreviewState.INITIALIZING -> {
                        initializeCameraUI()

                        cameraScreenViewState.emit(
                                cameraScreenViewState.value
                                        .copy(isVisible = cameraPreviewScreen.getCameraSettingsVisibility())
                                        .updateCameraMainControls {
                                            when (cameraUiState.cameraSettingLevel) {
                                                CameraSettingLevel.BASIC -> it.enableBasicExposureControl()
                                                CameraSettingLevel.INTERMEDIATE -> {
                                                    val exposureLocked =
                                                            cameraUiState.editableCameraSettings?.contains("exposure") ?: false
                                                    it.enableIntermediateExposureControl(exposureLocked)
                                                }
                                                CameraSettingLevel.ADVANCED -> {
                                                    val isoLocked =
                                                            cameraUiState.editableCameraSettings?.contains("iso") ?: false
                                                    val shutterLocked =
                                                            cameraUiState.editableCameraSettings?.contains("shutter_speed") ?: false
                                                    val apertureLocked =
                                                            cameraUiState.editableCameraSettings?.contains("aperture") ?: false
                                                    it.enableAdvanceExposureControl(isoLocked, shutterLocked, apertureLocked)
                                                }
                                            }
                                        }
                        )
                    }

                    CameraPreviewState.WAITING_FOR_CAMERA -> {
                        if (cameraInput.cameraSettingState.value.cameraState == CameraState.RUNNING) {
                            _cameraUiState.emit(
                                cameraUiState.copy(cameraPreviewState = CameraPreviewState.ATTACHING_TO_CAMERA)
                            )
                        }
                    }

                    CameraPreviewState.ATTACHING_TO_CAMERA -> {
                        cameraPreviewScreen.previewTextureView.doOnLayout {
                            startCameraPreviewView(cameraPreviewScreen)
                        }

                        cameraPreviewScreen.setupZoomControl(cameraInput.cameraSettingState.value)
                        cameraPreviewScreen.setUpWhiteBalanceControl(cameraInput.cameraSettingState.value)

                        _cameraUiState.emit(
                                cameraUiState.copy(cameraPreviewState = CameraPreviewState.UPDATING)
                        )
                    }


                    CameraPreviewState.UPDATING -> {
                        _cameraUiState.emit(
                                cameraUiState.copy(cameraPreviewState = CameraPreviewState.RUNNING)
                        )
                        cameraScreenViewState?.value?.let {
                            cameraScreenViewState?.emit(
                                    it.copy(
                                            isVisible = cameraPreviewScreen.getCameraSettingsVisibility(),
                                            subControls = it.subControls
                                                    .hideAll()
                                                    .copy(
                                                        recyclerViewVisible = true,
                                                        exposureSlider = it.subControls.exposureSlider.copy(isVisible = cameraUiState.settingMode == CameraSettingMode.EXPOSURE, isEnabled = cameraUiState.settingMode == CameraSettingMode.EXPOSURE),
                                                        isoSlider = it.subControls.isoSlider.copy(isVisible = cameraUiState.settingMode == CameraSettingMode.ISO, isEnabled = cameraUiState.settingMode == CameraSettingMode.ISO),
                                                        apertureSlider = it.subControls.apertureSlider.copy(isVisible = cameraUiState.settingMode == CameraSettingMode.APERTURE, isEnabled = cameraUiState.settingMode == CameraSettingMode.APERTURE),
                                                        shutterSpeedSlider = it.subControls.shutterSpeedSlider.copy(isVisible = cameraUiState.settingMode == CameraSettingMode.SHUTTER_SPEED, isEnabled = cameraUiState.settingMode == CameraSettingMode.SHUTTER_SPEED),
                                                        whiteBalanceControl = it.subControls.whiteBalanceControl.copy(isVisible = cameraUiState.settingMode == CameraSettingMode.WHITE_BALANCE, isEnabled = cameraUiState.settingMode == CameraSettingMode.WHITE_BALANCE),
                                            )
                                    )
                            )
                        }
                    }

                    CameraPreviewState.RUNNING -> Unit
                }

                when (cameraUiState.overlayUpdateState) {
                    OverlayUpdateState.NO_UPDATE -> Unit
                    OverlayUpdateState.UPDATE -> {
                        cameraPreviewScreen.updateOverlay()
                    }
                    OverlayUpdateState.UPDATE_DONE -> Unit
                }
            }
        }

        viewModelScope.launch {
            cameraInput.cameraSettingState.collectLatest { cameraSettingState ->
                if (cameraSettingState.cameraState == CameraState.RUNNING) {
                    if (cameraUiState.value.cameraPreviewState == CameraPreviewState.WAITING_FOR_CAMERA) {
                        _cameraUiState.emit(
                                cameraUiState.value.copy(cameraPreviewState = CameraPreviewState.ATTACHING_TO_CAMERA)
                        )
                    }
                    cameraPreviewScreen.setCurrentValueInCameraSettingTextView(cameraSettingState)
                    val oldState = cameraScreenViewState.value
                    val newState = cameraScreenViewState.value.copy(
                            mainControls = oldState.mainControls.copy(
                                    isoButton = oldState.mainControls.isoButton.copy(isEnabled = !cameraSettingState.autoExposure && !(cameraInput.lockedSettings?.contains("iso") ?: false)),
                                    shutterButton = oldState.mainControls.shutterButton.copy(isEnabled = !cameraSettingState.autoExposure && !(cameraInput.lockedSettings?.contains("shutter_speed") ?: false)),
                                    apertureButton = oldState.mainControls.apertureButton.copy(isEnabled = !cameraSettingState.autoExposure && (cameraSettingState.apertureRange?.count() ?: 0) > 1 && !(cameraInput.lockedSettings?.contains("aperture") ?: false))
                            )
                    )

                    cameraScreenViewState.emit(newState)
                } else if (cameraSettingState.cameraState == CameraState.RESTART) {
                    _cameraUiState.emit(
                        cameraUiState.value.copy(cameraPreviewState = CameraPreviewState.WAITING_FOR_CAMERA)
                    )
                }
            }
        }
    }

    private fun initializeCameraUI() {
        viewModelScope.launch {
            val currentCameraUiState = _cameraUiState.value

            val availableCameraLens =
                    listOf(
                            CameraSelector.LENS_FACING_BACK,
                            CameraSelector.LENS_FACING_FRONT
                    )

            val newCameraUiState = currentCameraUiState.copy(
                    cameraPreviewState = CameraPreviewState.WAITING_FOR_CAMERA,
                    availableCameraLens = availableCameraLens,
                    editableCameraSettings = cameraInput.lockedSettings
            )

            _cameraUiState.emit(newCameraUiState)
        }
    }

    fun requestUpdate() {
        viewModelScope.launch {
            if (cameraUiState.value.cameraPreviewState == CameraPreviewState.RUNNING) {
                _cameraUiState.emit(
                        cameraUiState.value.copy(
                                cameraPreviewState = CameraPreviewState.UPDATING
                        )
                )
            }
        }
    }

    fun startCameraPreviewView(
            cameraPreviewScreen: CameraPreviewScreen
    ) {
        cameraInput.analyzingOpenGLRenderer?.attachTexturePreviewView(cameraPreviewScreen)
    }

    public fun stopCameraPreviewView(
            cameraPreviewScreen: CameraPreviewScreen
    ) {
        cameraInput.analyzingOpenGLRenderer?.detachTexturePreviewView(cameraPreviewScreen)
    }

    fun switchCamera() {
        if (cameraUiState.value.cameraPreviewState == CameraPreviewState.RUNNING) {
            // To switch the camera lens, there has to be at least 2 camera lenses
            if (cameraUiState.value.availableCameraLens.size == 1) return

            // cameraInput.switchCamera() TODO
        }
    }

    fun showZoomController() {
        viewModelScope.launch {
            cameraScreenViewState?.value?.let {
                val opticalZooms =
                        CameraHelper.getAvailableOpticalZoomList(cameraInput.cameraSettingState.value.cameraMaxOpticalZoom)

                cameraScreenViewState?.emit(
                        it.copy(
                                subControls = it.subControls.hideAll().copy(
                                    zoomControls = CameraZoomControlViewState(true, true).setupOpticalZoomButtonVisibility(
                                            cameraInput.cameraSettingState.value.cameraMinZoomRatio < 1.0f,
                                            opticalZooms.isNotEmpty(),
                                            opticalZooms.contains(2),
                                            opticalZooms.contains(5),
                                            opticalZooms.contains(10)
                                    )
                                )
                        )
                )
            }
        }
    }

    fun hideAllController(){
        viewModelScope.launch {
            cameraScreenViewState?.value?.let {
                cameraScreenViewState?.emit(
                        it.copy(subControls = it.subControls.hideAll())
                )
            }
        }
    }

    fun changeAutoExposure(autoExposure: Boolean) {
        // cameraInput.setAutoExposure(autoExposure) TODO
    }



    fun openCameraSettingValue(settingMode: CameraSettingMode) {
        val currentCameraUiState = _cameraUiState.value
        viewModelScope.launch {
            _cameraUiState.emit(
                currentCameraUiState.copy(
                    settingMode = settingMode,
                    cameraPreviewState = CameraPreviewState.UPDATING
                )
            )
        }
    }

    fun cameraSettingOpened() {
    }

    fun changeWhiteBalance(value: FloatArray) {
        // cameraInput.setWhiteBalance(value) TODO
    }

    fun updateCameraSettingValue(value: String, settingMode: CameraSettingMode) {
        // cameraInput.updateCameraSettingValue(value, settingMode) TODO
    }

    fun updateCameraOverlay() {
        viewModelScope.launch {
            _cameraUiState.emit(
                _cameraUiState.value.copy(
                    overlayUpdateState = OverlayUpdateState.UPDATE
            ))
        }
    }

    fun overlayUpdated(){
        viewModelScope.launch {
            _cameraUiState.emit(
                _cameraUiState.value.copy(
                    overlayUpdateState = OverlayUpdateState.UPDATE_DONE
            ))
        }
    }

    fun setPassepartout(passepartout: RectF) {
        // cameraInput.setPassepartout(passepartout) TODO
        updateCameraOverlay()
    }

}
