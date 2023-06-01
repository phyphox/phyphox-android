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
    val cameraSettingState : CameraSettingState = CameraSettingState.NOT_READY
)

/**
 * Defines the current state of the camera
 */
enum class CameraState {
    NOT_READY,
    READY,
    PREVIEW_IN_BACKGROUND,
    PREVIEW_STOPPED
}

enum class SettingMode {
    NONE,
    ISO,
    SHUTTER_SPEED,
    APERTURE
}

enum class CameraSettingState{
    NOT_READY,
    LOADING,
    LOADED,
    RELOADING,
    LOADING_FAILED,
    RELOADING_FAILED,
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
