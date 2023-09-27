package de.rwth_aachen.phyphox.camera.model


sealed class CameraUiAction {
    object SwitchCameraClick : CameraUiAction()
    data class CameraSettingClick(val settingMode: CameraSettingMode) : CameraUiAction()
    object ZoomClicked: CameraUiAction()
    data class UpdateCameraExposureSettingValue(val settingMode: CameraSettingMode, val value: String) : CameraUiAction()
    data class UpdateAutoExposure(val autoExposure: Boolean): CameraUiAction()
    object CameraSettingValueSelected : CameraUiAction()
    object UpdateOverlay : CameraUiAction()
    data class UpdateCameraDimension(val height: Int, val width: Int): CameraUiAction()
    object OverlayUpdateDone : CameraUiAction()

}
