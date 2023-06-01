package de.rwth_aachen.phyphox.camera.model

sealed class CameraUiAction {
    object RequestPermissionClick : CameraUiAction()
    object SwitchCameraClick : CameraUiAction()
    object LoadCameraSettings : CameraUiAction()
    object ReloadCameraSettings : CameraUiAction()
    data class SelectAndChangeCameraSetting(val extension: Int, val value: Int) : CameraUiAction()

}
