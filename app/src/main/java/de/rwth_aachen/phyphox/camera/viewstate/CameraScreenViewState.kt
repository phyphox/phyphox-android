package de.rwth_aachen.phyphox.camera.viewstate

import java.util.Collections.copy

data class CameraScreenViewState(
    val cameraPreviewScreenViewState: CameraPreviewScreenViewState = CameraPreviewScreenViewState(),
    val cameraSettingViewState: CameraSettingViewState = CameraSettingViewState()
) {
    fun updateCameraScreen(block: (cameraPreviewScreenViewState: CameraPreviewScreenViewState) -> CameraPreviewScreenViewState): CameraScreenViewState =
        copy(cameraPreviewScreenViewState = block(cameraPreviewScreenViewState))

}
