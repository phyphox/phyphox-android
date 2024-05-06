package de.rwth_aachen.phyphox.camera.viewstate

data class CameraScreenViewState(
        val isVisible: Boolean = false,
        val mainControls: CameraMainControlsViewState = CameraMainControlsViewState(),
        val subControls: CameraSubControlViewState = CameraSubControlViewState()
) {
    fun updateCameraMainControls(block: (cameraMainControlsViewState: CameraMainControlsViewState) -> CameraMainControlsViewState): CameraScreenViewState =
        copy(mainControls = block(mainControls))

}

data class CameraSubControlViewState(
        val recyclerViewVisible : Boolean = false,
        val exposureSlider: CameraControlElementViewState = CameraControlElementViewState(),
        val isoSlider: CameraControlElementViewState = CameraControlElementViewState(),
        val shutterSpeedSlider: CameraControlElementViewState = CameraControlElementViewState(),
        val apertureSlider: CameraControlElementViewState = CameraControlElementViewState(),
        val zoomControls: CameraZoomControlViewState = CameraZoomControlViewState(),
        val whiteBalanceControl: CameraControlElementViewState = CameraControlElementViewState(),
) {

    fun hideAll(): CameraSubControlViewState =
        copy(
            recyclerViewVisible = false,
            exposureSlider = isoSlider.copy(isVisible = false),
            isoSlider = isoSlider.copy(isVisible = false),
            shutterSpeedSlider = shutterSpeedSlider.copy(isVisible = false),
            apertureSlider = apertureSlider.copy(isVisible = false),
            zoomControls = zoomControls.copy(isVisible = false),
            whiteBalanceControl = whiteBalanceControl.copy(isVisible = false)
        )
}

data class CameraControlElementViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false
)

