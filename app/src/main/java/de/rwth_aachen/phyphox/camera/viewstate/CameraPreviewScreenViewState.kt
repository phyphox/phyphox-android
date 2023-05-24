package de.rwth_aachen.phyphox.camera.viewstate

data class CameraPreviewScreenViewState(
    val shutterButtonViewState: ShutterButtonViewState = ShutterButtonViewState(),
    val switchLensButtonViewState: SwitchLensButtonViewState = SwitchLensButtonViewState()
) {
    fun hideCameraControls(): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = false),
            switchLensButtonViewState = switchLensButtonViewState.copy(isVisible = false),
        )

    fun showCameraControls(): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = true),
            switchLensButtonViewState = switchLensButtonViewState.copy(isVisible = true),
        )

    fun enableCameraShutter(isEnabled: Boolean): CameraPreviewScreenViewState =
        copy(shutterButtonViewState = shutterButtonViewState.copy(isEnabled = isEnabled))

    fun enableSwitchLens(isEnabled: Boolean): CameraPreviewScreenViewState =
        copy(switchLensButtonViewState = switchLensButtonViewState.copy(isEnabled = isEnabled))

}

data class ShutterButtonViewState(
    val isVisible: Boolean = false,
    val isEnabled: Boolean = false
)

data class SwitchLensButtonViewState(
    val isVisible: Boolean = false,
    val isEnabled: Boolean = false
)
