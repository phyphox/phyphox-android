package de.rwth_aachen.phyphox.camera.viewstate

data class CameraPreviewScreenViewState(
    val shutterButtonViewState: ShutterButtonViewState = ShutterButtonViewState(),
    val isoButtonViewState: IsoButtonViewState = IsoButtonViewState(),
    val apertureSliderViewState: ApertureSliderViewState = ApertureSliderViewState(),
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
            apertureSliderViewState = apertureSliderViewState.copy(isVisible = true),
            switchLensButtonViewState = switchLensButtonViewState.copy(isVisible = true),
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
    val value: String = ""
)

data class IsoButtonViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false,
    val value: String = ""
)

data class ApertureButtonViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false,
    val value: String = ""
)

data class AutoExposureViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false
)
