package de.rwth_aachen.phyphox.camera.model

import android.graphics.Rect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import de.rwth_aachen.phyphox.camera.helper.CameraHelper

/**
 * Defines the current UI state of the camera
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
data class CameraUiState  constructor(
    val cameraState: CameraState = CameraState.NOT_READY,
    val availableSettings: List<ExposureSettingMode> = emptyList(),
    val availableCameraLens: List<Int> = listOf(LENS_FACING_BACK),
    val editableCameraSettings: MutableMap<String, String>? = mutableMapOf(),
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

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
data class CameraSettingValueState  constructor(
    val currentIsoValue: Int = 1,
    val isoRange: List<String>? =  emptyList(),

    val currentShutterValue: Long = 1L,
    var shutterSpeedRange:  List<String>? = emptyList(),

    val currentApertureValue: Float = 1.0f,
    var apertureRange:  List<String>? = emptyList(),

    val currentExposureValue: Float = 0.0f,
    var exposureRange: List<String>? = emptyList(),

    val autoExposure: Boolean = true,
    var exposureStep: Float = 0F,
    val exposureSettingState: ExposureSettingState = ExposureSettingState.NOT_READY,
    val settingMode: ExposureSettingMode = ExposureSettingMode.NONE,

    val cameraSettingLevel: CameraSettingLevel = CameraSettingLevel.BASIC,
    val cameraSettingRecyclerState: CameraSettingRecyclerState = CameraSettingRecyclerState.HIDDEN,

    val cameraMaxZoomRatio: Float = 0.0f,
    val cameraMinZoomRatio: Float = 0.0f,
    val cameraZoomRatio: Float = 0.0f,
    val cameraLinearRatio: Float = 0.0f,
    val cameraZoomRatioConverted: MutableList<Float> = mutableListOf(),
    val  cameraMaxOpticalZoom: Float? = 1.0f,

    val cameraMaxRegionAWB: Int = 0,
    val cameraWhiteBalanceManualRange: List<Int> = CameraHelper.getWhiteBalanceTemperatureList(),
    val cameraWhiteBalanceModes: List<Int> = CameraHelper.getWhiteBalanceModes(),
    val cameraCurrentWhiteBalanceValue: FloatArray = CameraHelper.convertTemperatureToRggb(15000),
    val cameraCurrentWhiteBalanceMode : Int = 1
    )

enum class ExposureSettingMode {
    NONE,
    ISO,
    SHUTTER_SPEED,
    APERTURE,
    EXPOSURE,
    AUTO_EXPOSURE,
    WHITE_BALANCE
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

enum class ExposureSettingState{
    NOT_READY,
    LOADED,
    LOADING_FAILED,
    LOAD_LIST,
    VALUE_UPDATED,
    LOAD_FINISHED
}


data class ImageAnalysisValueState(
    val currentTimeStamp: Double = 0.0,
    val luminance : Double = 0.0,
    val colorCode: String = "",
    val imageAnalysisState: ImageAnalysisState = ImageAnalysisState.IMAGE_ANALYSIS_NOT_READY
)

enum class ImageAnalysisState{
    IMAGE_ANALYSIS_NOT_READY,
    IMAGE_ANALYSIS_READY,
    IMAGE_ANALYSIS_STARTED,
    IMAGE_ANALYSIS_FINISHED,
    IMAGE_ANALYSIS_FAILED
}
