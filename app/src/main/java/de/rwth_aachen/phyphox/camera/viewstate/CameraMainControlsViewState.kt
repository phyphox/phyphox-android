package de.rwth_aachen.phyphox.camera.viewstate

data class CameraMainControlsViewState(
        val shutterButton: CameraControlElementViewState = CameraControlElementViewState(),
        val isoButton: CameraControlElementViewState = CameraControlElementViewState(),
        val apertureButton: CameraControlElementViewState = CameraControlElementViewState(),
        val exposureButton: CameraControlElementViewState = CameraControlElementViewState(),
        val autoExposureButton: CameraControlElementViewState = CameraControlElementViewState(),
        val switchLensButton: CameraControlElementViewState = CameraControlElementViewState(),
        val zoomButton: CameraControlElementViewState = CameraControlElementViewState(),
        val whiteBalanceButton: CameraControlElementViewState = CameraControlElementViewState(),
        ) {

    fun enableBasicExposureControl(): CameraMainControlsViewState =
            copy(
                    switchLensButton = switchLensButton.copy(isVisible = true, isEnabled = true),
                    shutterButton = shutterButton.copy(isVisible = false),
                    isoButton = isoButton.copy(isVisible = false),
                    apertureButton = apertureButton.copy(isVisible = false),
                    autoExposureButton = autoExposureButton.copy(isVisible = false),
                    exposureButton = exposureButton.copy(isVisible = false),
                    zoomButton = zoomButton.copy(isVisible = true, isEnabled = true),
                    whiteBalanceButton = whiteBalanceButton.copy(isVisible = false)

            )

    fun enableIntermediateExposureControl(
            isExposureLocked: Boolean
    ): CameraMainControlsViewState =
            copy(
                    switchLensButton = switchLensButton.copy(isVisible = true, isEnabled = true),
                    shutterButton = shutterButton.copy(isVisible = false),
                    isoButton = isoButton.copy(isVisible = false),
                    apertureButton = apertureButton.copy(isVisible = false),
                    autoExposureButton = autoExposureButton.copy(isVisible = true, isEnabled = true),
                    exposureButton = exposureButton.copy(isVisible = true, isEnabled = !isExposureLocked),
                    zoomButton = zoomButton.copy(isVisible = true, isEnabled = true),
                    whiteBalanceButton = whiteBalanceButton.copy(isVisible = false)
            )

    fun enableAdvanceExposureControl(
            isIsoLocked: Boolean,
            isShutterSpeedLocked: Boolean,
            isApertureLocked: Boolean,
    ): CameraMainControlsViewState =
            copy(
                    switchLensButton = switchLensButton.copy(isVisible = true, isEnabled = true),
                    shutterButton = shutterButton.copy(isVisible = true, isEnabled = !isShutterSpeedLocked),
                    isoButton = isoButton.copy(isVisible = true, isEnabled = !isIsoLocked),
                    apertureButton = apertureButton.copy(isVisible = true, isEnabled = !isApertureLocked),
                    autoExposureButton = autoExposureButton.copy(isVisible = true, isEnabled = true),
                    exposureButton = exposureButton.copy(isVisible = false),
                    zoomButton = zoomButton.copy(isVisible = true, isEnabled = true),
                    whiteBalanceButton = whiteBalanceButton.copy(isVisible = true, isEnabled = true)
            )
}

