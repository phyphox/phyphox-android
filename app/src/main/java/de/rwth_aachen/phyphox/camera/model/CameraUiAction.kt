package de.rwth_aachen.phyphox.camera.model

import java.lang.Exception


sealed class CameraUiAction {
    object RequestPermissionClick : CameraUiAction()
    object SwitchCameraClick : CameraUiAction()
    data class CameraSettingClick(val settingMode: SettingMode) : CameraUiAction()
    data class SelectAndChangeCameraSetting(val extension: Int, val value: Int) : CameraUiAction()
    data class UpdateCameraExposureSettingValue(val settingMode: SettingMode, val value: String) : CameraUiAction()
    data class UpdateAutoExposure(val autoExposure: Boolean): CameraUiAction()
    object ExposureSettingValueSelected : CameraUiAction()
    object UpdateOverlay : CameraUiAction()
    data class UpdateCameraDimension(val height: Int, val width: Int): CameraUiAction()
    object OverlayUpdateDone : CameraUiAction()

}
