package de.rwth_aachen.phyphox.camera.viewstate

import de.rwth_aachen.phyphox.camera.model.SettingMode

data class CameraPreviewScreenViewState(
    val shutterButtonViewState: ShutterButtonViewState = ShutterButtonViewState(),
    val isoButtonViewState: IsoButtonViewState = IsoButtonViewState(),
    val apertureButtonViewState: ApertureButtonViewState = ApertureButtonViewState(),
    val exposureViewState: ExposureViewState = ExposureViewState(),
    val autoExposureViewState: AutoExposureViewState = AutoExposureViewState(),
    val switchLensButtonViewState: SwitchLensButtonViewState = SwitchLensButtonViewState(),
    val adjustSettingSeekbarViewState : AdjustSettingSeekbar = AdjustSettingSeekbar(),
    val cameraSettingRecyclerViewState : CameraSettingRecyclerView = CameraSettingRecyclerView()
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

    fun changeSettingSeekbar(isVisible: Boolean, maxValue: Int, minValue: Int, currentValue: Int ) : CameraPreviewScreenViewState=
        copy(adjustSettingSeekbarViewState = adjustSettingSeekbarViewState.copy(
            isVisible = isVisible,
            maxValue = maxValue,
            minValue = minValue,
            currentValue = currentValue
        ))

    fun enableBasicExposureControl() : CameraPreviewScreenViewState =
        copy(shutterButtonViewState = shutterButtonViewState.copy(isVisible = false),
            isoButtonViewState = isoButtonViewState.copy(isVisible = false),
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = false),
            autoExposureViewState = autoExposureViewState.copy(isVisible = false),
            exposureViewState = exposureViewState.copy(isVisible = false)
        )

    fun enableIntermediateExposureControl(): CameraPreviewScreenViewState =
        copy(shutterButtonViewState = shutterButtonViewState.copy(isVisible = false),
            isoButtonViewState = isoButtonViewState.copy(isVisible = false),
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = false),
            autoExposureViewState = autoExposureViewState.copy(isVisible = true),
            exposureViewState = exposureViewState.copy(isVisible = true)
        )

    fun enableAdvanceExposureControl(): CameraPreviewScreenViewState =
        copy(shutterButtonViewState = shutterButtonViewState.copy(isVisible = true),
            isoButtonViewState = isoButtonViewState.copy(isVisible = true),
            apertureButtonViewState = apertureButtonViewState.copy(isVisible = true),
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

data class AdjustSettingSeekbar(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false,
    val maxValue: Int = 0,
    val minValue: Int = 0,
    val currentValue: Int = 0,
)

data class CameraSettingRecyclerView(
    val isOpened: Boolean = false,
)
