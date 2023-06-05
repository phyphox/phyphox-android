package de.rwth_aachen.phyphox.camera.model

sealed class CameraUiAction {
    object RequestPermissionClick : CameraUiAction()
    object SwitchCameraClick : CameraUiAction()
    object LoadCameraSettings : CameraUiAction()
    data class ReloadCameraSettings(val settingMode: SettingMode, val currentValue: Int) : CameraUiAction()
    data class SelectAndChangeCameraSetting(val extension: Int, val value: Int) : CameraUiAction()

    data class ChangeCameraSettingValue(val settingMode: SettingMode, val value: String) : CameraUiAction()

    data class ChangeAutoExposure(val autoExposure: Boolean): CameraUiAction()

}
