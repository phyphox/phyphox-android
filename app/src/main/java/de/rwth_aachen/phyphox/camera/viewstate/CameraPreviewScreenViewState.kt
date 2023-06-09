package de.rwth_aachen.phyphox.camera.viewstate

data class CameraPreviewScreenViewState(
    val shutterButtonViewState: ShutterButtonViewState = ShutterButtonViewState(),
    val isoButtonViewState: IsoButtonViewState = IsoButtonViewState(),
    val apertureButtonViewState: ApertureButtonViewState = ApertureButtonViewState(),
    val autoExposureViewState: AutoExposureViewState = AutoExposureViewState(),
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
            isoButtonViewState = isoButtonViewState.copy(isVisible = true),
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = true),
            switchLensButtonViewState = switchLensButtonViewState.copy(isVisible = true),
        )

    fun enableCameraControls(): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isEnabled = true),
            isoButtonViewState = isoButtonViewState.copy(isEnabled = true),
            apertureButtonViewState = apertureButtonViewState.copy(isEnabled = true),
            switchLensButtonViewState = switchLensButtonViewState.copy(isEnabled = true),
        )

    fun disableCameraControls(): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isEnabled = false),
            isoButtonViewState = isoButtonViewState.copy(isEnabled = false),
            apertureButtonViewState = apertureButtonViewState.copy(isEnabled = false),
            switchLensButtonViewState = switchLensButtonViewState.copy(isEnabled = false),
        )
    fun enableCameraShutter(isEnabled: Boolean): CameraPreviewScreenViewState =
        copy(shutterButtonViewState = shutterButtonViewState.copy(isEnabled = isEnabled))

    fun enableSwitchLens(isEnabled: Boolean): CameraPreviewScreenViewState =
        copy(switchLensButtonViewState = switchLensButtonViewState.copy(isEnabled = isEnabled))

    fun enableAutoFocus(isEnabled: Boolean) : CameraPreviewScreenViewState =
        copy(autoExposureViewState = autoExposureViewState.copy(isEnabled = isEnabled ))

}

data class SwitchLensButtonViewState(
    val isVisible: Boolean = false,
    val isEnabled: Boolean = false
)

data class ShutterButtonViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false,
)

data class IsoButtonViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false,
)

data class ApertureButtonViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false,
)

data class AutoExposureViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false
)
