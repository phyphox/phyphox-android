package de.rwth_aachen.phyphox.camera.viewstate

data class CameraSettingViewState(
    val isoSliderViewState: IsoSliderViewState = IsoSliderViewState(),
    val shutterSpeedSliderViewState: ShutterSpeedSliderViewState = ShutterSpeedSliderViewState(),
    val apertureSliderViewState: ApertureSliderViewState = ApertureSliderViewState()
)

data class IsoSliderViewState(
    val isVisible: Boolean = false,
    val isEnabled: Boolean = false
)

data class ShutterSpeedSliderViewState(
    val isVisible: Boolean = false,
    val isEnabled: Boolean = false
)

data class ApertureSliderViewState(
    val isVisible: Boolean = false,
    val isEnabled: Boolean = false
)
