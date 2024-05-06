package de.rwth_aachen.phyphox.camera.model

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector.LENS_FACING_BACK

/**
 * Defines the current UI state of the camera
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
data class CameraUiState  constructor(
        val cameraPreviewState: CameraPreviewState = CameraPreviewState.INITIALIZING,
        val availableSettings: List<CameraSettingMode> = emptyList(),
        val availableCameraLens: List<Int> = listOf(LENS_FACING_BACK),
        val editableCameraSettings: MutableMap<String, String>? = mutableMapOf(),
        val cameraLens: Int = LENS_FACING_BACK,

        val overlayUpdateState: OverlayUpdateState = OverlayUpdateState.NO_UPDATE,

        val settingMode: CameraSettingMode = CameraSettingMode.NONE,

        val cameraSettingLevel: CameraSettingLevel = CameraSettingLevel.BASIC,
        val showCameraControls: ShowCameraControls = ShowCameraControls.FullViewOnly,
)

/**
 * Defines the current state of the camera
 */
enum class CameraPreviewState {
    INITIALIZING,
    WAITING_FOR_CAMERA,
    ATTACHING_TO_CAMERA,
    RUNNING,
    UPDATING
}

enum class OverlayUpdateState {
    NO_UPDATE,
    UPDATE,
    UPDATE_DONE
}

enum class CameraSettingMode {
    NONE,
    ISO,
    SHUTTER_SPEED,
    APERTURE,
    EXPOSURE,
    AUTO_EXPOSURE,
    WHITE_BALANCE,
    ZOOM,
    SWITCH_LENS
}

enum class CameraSettingLevel {
    BASIC, // auto exposure ON (Level 1)
    INTERMEDIATE, // auto exposure OFF, only adjust exposure (Level 2)
    ADVANCE // auto exposure OFF, can adjust ISO, Shutter Speed and Aperture (Level 3)
}

enum class ShowCameraControls {
    Always, Never, FullViewOnly
}
