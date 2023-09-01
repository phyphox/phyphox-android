package de.rwth_aachen.phyphox.camera.model

import android.os.Build
import androidx.annotation.RequiresApi
import de.rwth_aachen.phyphox.camera.helper.CameraHelper

/**
 * Defines the current Camera Settings state of camera
 */
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

    val disabledAutoExposure: Boolean = true,
    var exposureStep: Float = 0F,
    val cameraSettingState: CameraSettingState = CameraSettingState.NOT_READY,
    val settingMode: CameraSettingMode = CameraSettingMode.NONE,

    val cameraSettingLevel: CameraSettingLevel = CameraSettingLevel.BASIC,
    val cameraSettingRecyclerState: CameraSettingRecyclerState = CameraSettingRecyclerState.TO_HIDE,

    val cameraMaxZoomRatio: Float = 0.0f,
    val cameraMinZoomRatio: Float = 0.0f,
    val cameraZoomRatio: Float = 0.0f,
    val cameraLinearRatio: Float = 0.0f,
    val cameraZoomRatioConverted: MutableList<Float> = mutableListOf(),
    val  cameraMaxOpticalZoom: Float? = 1.0f,

    val cameraMaxRegionAWB: Int = 0,
    val cameraWhiteBalanceManualRange: List<Int> = CameraHelper.getWhiteBalanceTemperatureList(),
    val cameraCurrentWhiteBalanceManualValue: FloatArray = CameraHelper.convertTemperatureToRggb(15000),
    val cameraWhiteBalanceModes: List<Int> = CameraHelper.getWhiteBalanceModes().keys.toList(),
    val cameraCurrentWhiteBalanceMode : Int = 1
)

enum class CameraSettingMode {
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
    TO_SHOW,
    TO_HIDE
}

enum class CameraSettingState{
    NOT_READY,
    LOADED,
    LOADING_FAILED,
    LOAD_LIST,
    VALUE_UPDATED,
    LOAD_FINISHED
}
