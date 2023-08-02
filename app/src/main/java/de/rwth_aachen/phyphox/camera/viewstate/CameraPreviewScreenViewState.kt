package de.rwth_aachen.phyphox.camera.viewstate

data class CameraPreviewScreenViewState(
    val shutterButtonViewState: ShutterButtonViewState = ShutterButtonViewState(),
    val isoButtonViewState: IsoButtonViewState = IsoButtonViewState(),
    val apertureButtonViewState: ApertureButtonViewState = ApertureButtonViewState(),
    val exposureViewState: ExposureViewState = ExposureViewState(),
    val autoExposureViewState: AutoExposureViewState = AutoExposureViewState(),
    val switchLensButtonViewState: SwitchLensButtonViewState = SwitchLensButtonViewState(),
    val cameraSettingRecyclerViewState : CameraSettingRecyclerView = CameraSettingRecyclerView()
) {

    fun showSwitchLensControl(): CameraPreviewScreenViewState =
        copy(switchLensButtonViewState = switchLensButtonViewState.copy(isVisible = true))

    fun hideCameraControls(): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = false),
            isoButtonViewState = isoButtonViewState.copy(isVisible = false),
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = false),
            exposureViewState = exposureViewState.copy(isVisible = false),
            autoExposureViewState = autoExposureViewState.copy(isVisible = false)
        )

    fun showSwitchLens(isVisible: Boolean): CameraPreviewScreenViewState =
        copy(switchLensButtonViewState = switchLensButtonViewState.copy(isVisible = isVisible))

    fun enableAutoFocus(isEnabled: Boolean) : CameraPreviewScreenViewState =
        copy(autoExposureViewState = autoExposureViewState.copy(isEnabled = isEnabled ))

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

    fun enableIntermediateExposureControl(): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = false),
            isoButtonViewState = isoButtonViewState.copy(isVisible = false),
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = false),
            autoExposureViewState = autoExposureViewState.copy(isVisible = true),
            exposureViewState = exposureViewState.copy(isVisible = true)
        )

    fun enableAdvanceExposureControl(): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = true),
            isoButtonViewState = isoButtonViewState.copy(isVisible = true),
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = false),
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
