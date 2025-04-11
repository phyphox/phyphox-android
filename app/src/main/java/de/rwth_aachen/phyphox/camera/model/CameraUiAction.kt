package de.rwth_aachen.phyphox.camera.model

import de.rwth_aachen.phyphox.camera.ui.ChooseCameraSettingValue


sealed class CameraUiAction {
    object SwitchCameraClick : CameraUiAction()
    data class CameraSettingClick(val settingMode: CameraSettingMode) : CameraUiAction()
    object ZoomClicked: CameraUiAction()
    data class UpdateCameraExposureSettingValue(val settingMode: CameraSettingMode, val value: ChooseCameraSettingValue) : CameraUiAction()
    data class UpdateAutoExposure(val autoExposure: Boolean): CameraUiAction()
    object CameraSettingValueSelected : CameraUiAction()
    object UpdateOverlay : CameraUiAction()
    object OverlayUpdateDone : CameraUiAction()

}
