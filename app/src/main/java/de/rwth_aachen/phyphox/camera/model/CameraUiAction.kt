package de.rwth_aachen.phyphox.camera.model


sealed class CameraUiAction {
    object RequestPermissionClick : CameraUiAction()
    object SwitchCameraClick : CameraUiAction()
    data class CameraSettingClick(val settingMode: ExposureSettingMode) : CameraUiAction()
    data class SelectAndChangeCameraSetting(val extension: Int, val value: Int) : CameraUiAction()
    data class UpdateCameraExposureSettingValue(val settingMode: ExposureSettingMode, val value: String) : CameraUiAction()
    data class UpdateAutoExposure(val autoExposure: Boolean): CameraUiAction()
    object ExposureSettingValueSelected : CameraUiAction()
    object UpdateOverlay : CameraUiAction()
    data class UpdateCameraDimension(val height: Int, val width: Int): CameraUiAction()
    object OverlayUpdateDone : CameraUiAction()

}
