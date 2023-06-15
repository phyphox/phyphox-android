package de.rwth_aachen.phyphox.camera.model

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

data class CameraSettingValueState(
    val currentIsoValue: Int = 1,
    val currentShutterValue: Long = 1L,
    val currentApertureValue: Float = 1.0f,
    val currentExposureValue: Int = 0,
    val autoExposure: Boolean = true,
    val isoRange: List<String>? =  emptyList(),
    var shutterSpeedRange:  List<String>? = emptyList(),
    var apertureRange:  List<String>? = emptyList(),
    var exposureRange: List<String>? = emptyList(),
    val cameraSettingState : CameraSettingState = CameraSettingState.NOT_READY,
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


/**
 * Defines the various states during the image analysis process
 */
sealed class ImageAnalysisState {
    object ImageAnalysisNotReady : ImageAnalysisState()
    object ImageAnalysisReady : ImageAnalysisState()
    object ImageAnalysisStarted : ImageAnalysisState()
    object ImageAnalysisFinished : ImageAnalysisState()
    data class ImageAnalysisFailed(val exception: Exception) : ImageAnalysisState()

}
