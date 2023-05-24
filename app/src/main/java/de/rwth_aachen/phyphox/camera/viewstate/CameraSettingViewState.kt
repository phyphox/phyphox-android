package de.rwth_aachen.phyphox.camera.viewstate

data class CameraSettingViewState(
    val isoSliderViewState: IsoSliderViewState = IsoSliderViewState(),
    val shutterSpeedSliderViewState: ShutterSpeedSliderViewState = ShutterSpeedSliderViewState(),
    val apertureSliderViewState: ApertureSliderViewState = ApertureSliderViewState()
) {

    fun isoSliderVisibility(isVisible: Boolean): CameraSettingViewState =
        copy(isoSliderViewState = isoSliderViewState.copy(isVisible  = isVisible))

    fun shutterSpeedSliderVisibility(isVisible : Boolean,): CameraSettingViewState =
        copy(shutterSpeedSliderViewState = shutterSpeedSliderViewState.copy(isVisible  = isVisible))

    fun apertureSliderVisibility(isVisible: Boolean): CameraSettingViewState =
        copy(apertureSliderViewState = apertureSliderViewState.copy(isVisible  = isVisible))

    fun enableIsoSlider(isEnabled: Boolean): CameraSettingViewState =
        copy(isoSliderViewState = isoSliderViewState.copy(isEnabled = isEnabled))

    fun enableShutterSpeedSlider(isEnabled: Boolean): CameraSettingViewState =
        copy(shutterSpeedSliderViewState = shutterSpeedSliderViewState.copy(isEnabled = isEnabled))

    fun enableApertureSlider(isEnabled: Boolean): CameraSettingViewState =
        copy(apertureSliderViewState = apertureSliderViewState.copy(isEnabled = isEnabled))

}

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
