package de.rwth_aachen.phyphox.camera.model

import android.graphics.Rect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import java.lang.Exception

/**
 * Defines the current UI state of the camera
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
data class CameraUiState  constructor(
    val cameraState: CameraState = CameraState.NOT_READY,
    val availableSettings: List<SettingMode> = emptyList(),
    val availableCameraLens: List<Int> = listOf(LENS_FACING_BACK),
    val cameraLens: Int = LENS_FACING_BACK,
    val cameraHeight: Int = 0,
    val cameraWidth: Int = 0,
    // This holds the cropped image which is cropped by the user through the overlayView.
    val cameraPassepartout: Rect = Rect(),
    val overlayUpdateState: OverlayUpdateState = OverlayUpdateState.NO_UPDATE
)

/**
 * Defines the current state of the camera
 */
enum class CameraState {
    NOT_READY,
    READY,
    LOADED,
    PREVIEW_IN_BACKGROUND,
    PREVIEW_STOPPED
}

enum class OverlayUpdateState {
    NO_UPDATE,
    UPDATE,
    UPDATE_DONE
}


data class CameraSettingValueState(
    val currentIsoValue: Int = 1,
    val currentShutterValue: Long = 1L,
    val currentApertureValue: Float = 1.0f,
    val currentExposureValue: Float = 0.0f,
    val autoExposure: Boolean = true,
    val isoRange: List<String>? =  emptyList(),
    var shutterSpeedRange:  List<String>? = emptyList(),
    var apertureRange:  List<String>? = emptyList(),
    var exposureRange: List<String>? = emptyList(),
    var exposureStep: Float = 0F,
    val cameraSettingState: CameraSettingState = CameraSettingState.NOT_READY,
    val settingMode: SettingMode = SettingMode.NONE,
    val cameraSettingLevel: CameraSettingLevel = CameraSettingLevel.BASIC,
    val cameraSettingRecyclerState: CameraSettingRecyclerState = CameraSettingRecyclerState.HIDDEN

    )

enum class SettingMode {
    NONE,
    ISO,
    SHUTTER_SPEED,
    APERTURE,
    EXPOSURE,
    AUTO_EXPOSURE
}

enum class CameraSettingLevel {
    BASIC, // auto exposure ON (Level 1)
    INTERMEDIATE, // auto exposure OFF, only adjust exposure (Level 2)
    ADVANCE // auto exposure OFF, can adjust ISO, Shutter Speed and Aperture (Level 3)
}

enum class CameraSettingRecyclerState {
    SHOWN,
    HIDDEN
}

enum class CameraSettingState{
    NOT_READY,
    LOADING,
    LOADED,
    RELOADING,
    LOADING_FAILED,
    RELOADING_FAILED,
    LOADING_VALUE,
    LOAD_VALUE,
    VALUE_UPDATED,
    LOAD_FINISHED
}


data class ImageAnalysisValueState(
    val currentTimeStamp: Long = 0L,
    val luminance : Double = 0.0,
    val imageAnalysisState: ImageAnalysisState = ImageAnalysisState.IMAGE_ANALYSIS_NOT_READY
)

enum class ImageAnalysisState{
    IMAGE_ANALYSIS_NOT_READY,
    IMAGE_ANALYSIS_READY,
    IMAGE_ANALYSIS_STARTED,
    IMAGE_ANALYSIS_FINISHED,
    IMAGE_ANALYSIS_FAILED
}
