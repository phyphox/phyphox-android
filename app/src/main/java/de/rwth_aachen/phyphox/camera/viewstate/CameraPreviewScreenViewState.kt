package de.rwth_aachen.phyphox.camera.viewstate

data class CameraPreviewScreenViewState(
    val cameraSettingLinearLayoutViewState: CameraSettingLinearLayoutViewState = CameraSettingLinearLayoutViewState(),
    val cameraSettingControllersViewState: CameraSettingControllersViewState = CameraSettingControllersViewState(),
    val shutterButtonViewState: ShutterButtonViewState = ShutterButtonViewState(),
    val isoButtonViewState: IsoButtonViewState = IsoButtonViewState(),
    val apertureButtonViewState: ApertureButtonViewState = ApertureButtonViewState(),
    val exposureViewState: ExposureViewState = ExposureViewState(),
    val autoExposureViewState: AutoExposureViewState = AutoExposureViewState(),
    val switchLensButtonViewState: SwitchLensButtonViewState = SwitchLensButtonViewState(),
    val cameraSettingRecyclerViewState : CameraSettingRecyclerView = CameraSettingRecyclerView(),

    val widerAngleButtonViewState: WiderAngleZoomButtonViewState = WiderAngleZoomButtonViewState(),
    val defaultButtonViewState: DefaultZoomButtonViewState = DefaultZoomButtonViewState(),
    val twoTimesButtonViewState: TwoTimesZoomButtonViewState = TwoTimesZoomButtonViewState(),
    val fiveTimesButtonViewState: FiveTimesZoomButtonViewState = FiveTimesZoomButtonViewState(),
    val tenTimesButtonViewState: TenTimesZoomButtonViewState = TenTimesZoomButtonViewState(),
) {

    fun setCameraSettingsVisibility(isVisible: Boolean): CameraPreviewScreenViewState =
        copy(cameraSettingLinearLayoutViewState = cameraSettingLinearLayoutViewState.copy(isVisible = isVisible))

    fun setCameraZoomControllerVisibility(isVisible: Boolean): CameraPreviewScreenViewState =
        copy(cameraSettingControllersViewState = cameraSettingControllersViewState.copy(isZoomControlVisible = isVisible))

    fun setCameraRecyclerViewExposureControllerVisibility(isVisible: Boolean): CameraPreviewScreenViewState =
        copy(cameraSettingControllersViewState = cameraSettingControllersViewState.copy(isRecyclerViewExposureControlVisible = isVisible))

    fun setCameraWhiteBalanceControllerVisibility(isVisible: Boolean): CameraPreviewScreenViewState =
        copy(cameraSettingControllersViewState = cameraSettingControllersViewState.copy(isWhiteBalanceControlVisible = isVisible))

    fun showSwitchLensControl(): CameraPreviewScreenViewState =
        copy(switchLensButtonViewState = switchLensButtonViewState.copy(isVisible = true))

    fun hideIso(isVisible: Boolean): CameraPreviewScreenViewState =
        copy(
            isoButtonViewState = isoButtonViewState.copy(isVisible = isVisible),
        )

    fun hideShutterSpeed(isVisible: Boolean): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = isVisible),
        )

    fun hideAperture(isVisible: Boolean): CameraPreviewScreenViewState =
        copy(
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = isVisible),
        )


    fun showSwitchLens(isVisible: Boolean): CameraPreviewScreenViewState =
        copy(switchLensButtonViewState = switchLensButtonViewState.copy(isVisible = isVisible, isEnabled = isVisible))

    fun enableAutoFocus(isEnabled: Boolean) : CameraPreviewScreenViewState =
        copy(autoExposureViewState = autoExposureViewState.copy(isEnabled = isEnabled ))

    fun setCameraSettingsClickability(isEnabled: Boolean) : CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isEnabled = isEnabled),
            isoButtonViewState = isoButtonViewState.copy(isEnabled = isEnabled),
            apertureButtonViewState = apertureButtonViewState.copy(isEnabled = isEnabled),
            exposureViewState = exposureViewState.copy(isEnabled = isEnabled),
        )

    fun enableExposure(isEnabled: Boolean) : CameraPreviewScreenViewState =
        copy(exposureViewState = exposureViewState.copy(isVisible = isEnabled ))


    fun enableBasicExposureControl() : CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = false),
            isoButtonViewState = isoButtonViewState.copy(isVisible = false),
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = false),
            autoExposureViewState = autoExposureViewState.copy(isVisible = false),
            exposureViewState = exposureViewState.copy(isVisible = false)
        )

    fun  enableIntermediateExposureControl(
        isExposureLocked: Boolean
    ): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = false),
            isoButtonViewState = isoButtonViewState.copy(isVisible = false),
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = false),
            autoExposureViewState = autoExposureViewState.copy(isVisible = true),
            exposureViewState = exposureViewState.copy(isVisible = true, isEnabled = !isExposureLocked)
        )

    fun enableAdvanceExposureControl(
        isIsoLocked: Boolean,
        isShutterSpeedLocked: Boolean,
        ): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = true, isEnabled = !isShutterSpeedLocked),
            isoButtonViewState = isoButtonViewState.copy(isVisible = true, isEnabled = !isIsoLocked),
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = true, isEnabled = false),
            autoExposureViewState = autoExposureViewState.copy(isVisible = true),
            exposureViewState = exposureViewState.copy(isVisible = false)
        )

    fun showRecyclerView() : CameraPreviewScreenViewState =
        copy(cameraSettingRecyclerViewState = cameraSettingRecyclerViewState.copy(
            isOpened = true))

    fun hideRecyclerView() : CameraPreviewScreenViewState =
        copy(cameraSettingRecyclerViewState = cameraSettingRecyclerViewState.copy(
            isOpened = false
        ))

    fun setupOpticalZoomButtonVisibility(
        showWiderAngle: Boolean,
        showDefault: Boolean,
        showTwoTimes: Boolean,
        showFiveTimes: Boolean,
        showTenTimes: Boolean
    ): CameraPreviewScreenViewState =
        copy(
            widerAngleButtonViewState = widerAngleButtonViewState.copy(isVisible = showWiderAngle),
            defaultButtonViewState = defaultButtonViewState.copy(isVisible = showDefault),
            twoTimesButtonViewState = twoTimesButtonViewState.copy(isVisible = showTwoTimes),
            fiveTimesButtonViewState = fiveTimesButtonViewState.copy(isVisible = showFiveTimes),
            tenTimesButtonViewState = tenTimesButtonViewState.copy(isVisible = showTenTimes),

            )

}

data class CameraSettingLinearLayoutViewState(
    val isVisible: Boolean = true,
    val isEnabled: Boolean = true
)

data class CameraSettingControllersViewState(
    val isZoomControlVisible: Boolean = false,
    val isRecyclerViewExposureControlVisible: Boolean = false,
    val isWhiteBalanceControlVisible: Boolean = false
)

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

data class ExposureViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false
)

data class AutoExposureViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false
)

data class CameraSettingRecyclerView(
    val isOpened: Boolean = false,
)

data class WiderAngleZoomButtonViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false
)

data class DefaultZoomButtonViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false
)

data class TwoTimesZoomButtonViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false
)

data class FiveTimesZoomButtonViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false
)

data class TenTimesZoomButtonViewState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false
)
