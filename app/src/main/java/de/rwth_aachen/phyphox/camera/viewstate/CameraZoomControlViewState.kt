package de.rwth_aachen.phyphox.camera.viewstate

data class CameraZoomControlViewState (
        val isEnabled: Boolean = false,
        val isVisible: Boolean = false,

        val widerAngleButtonViewState: CameraControlElementViewState = CameraControlElementViewState(),
        val defaultButtonViewState: CameraControlElementViewState = CameraControlElementViewState(),
        val twoTimesButtonViewState: CameraControlElementViewState = CameraControlElementViewState(),
        val fiveTimesButtonViewState: CameraControlElementViewState = CameraControlElementViewState(),
        val tenTimesButtonViewState: CameraControlElementViewState = CameraControlElementViewState(),
) {


    fun setupOpticalZoomButtonVisibility(
            showWiderAngle: Boolean,
            showDefault: Boolean,
            showTwoTimes: Boolean,
            showFiveTimes: Boolean,
            showTenTimes: Boolean
    ): CameraZoomControlViewState =
            copy(
                    widerAngleButtonViewState = widerAngleButtonViewState.copy(isVisible = showWiderAngle),
                    defaultButtonViewState = defaultButtonViewState.copy(isVisible = showDefault),
                    twoTimesButtonViewState = twoTimesButtonViewState.copy(isVisible = showTwoTimes),
                    fiveTimesButtonViewState = fiveTimesButtonViewState.copy(isVisible = showFiveTimes),
                    tenTimesButtonViewState = tenTimesButtonViewState.copy(isVisible = showTenTimes),

                    )

}
