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

    //Experimental high-speed camera mode: the constrained high-speed API does not support the
    //exposure, white balance and zoom controls, so only the lens switch remains (and only if the
    //other lens offers a high-speed mode, too, as we never mix both camera implementations)
    fun enableHighSpeedControl(
            canSwitchLens: Boolean
    ): CameraMainControlsViewState =
            copy(
                    switchLensButton = switchLensButton.copy(isVisible = true, isEnabled = canSwitchLens),
                    shutterButton = shutterButton.copy(isVisible = false),
                    isoButton = isoButton.copy(isVisible = false),
                    apertureButton = apertureButton.copy(isVisible = false),
                    autoExposureButton = autoExposureButton.copy(isVisible = false),
                    exposureButton = exposureButton.copy(isVisible = false),
                    zoomButton = zoomButton.copy(isVisible = false),
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

