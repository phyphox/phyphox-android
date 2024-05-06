package de.rwth_aachen.phyphox.camera.model

import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import de.rwth_aachen.phyphox.camera.helper.CameraHelper

/**
 * Defines the current Camera Settings state of camera
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
data class CameraSettingState  constructor(
        val currentLens : Int = CameraSelector.LENS_FACING_BACK,
        val cameraPassepartout: RectF = RectF(),

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
        val cameraState: CameraState = CameraState.NONE,

        val cameraMaxZoomRatio: Float = 0.0f,
        val cameraMinZoomRatio: Float = 0.0f,
        val cameraZoomRatio: Float = 0.0f,
        val cameraLinearRatio: Float = 0.0f,
        val cameraZoomRatioConverted: MutableList<Float> = mutableListOf(),
        val cameraMaxOpticalZoom: Float? = 1.0f,

        val cameraMaxRegionAWB: Int = 0,
        val cameraWhiteBalanceManualRange: List<Int> = CameraHelper.getWhiteBalanceTemperatureList(),
        val cameraCurrentWhiteBalanceManualValue: FloatArray = CameraHelper.convertTemperatureToRggb(15000),
        val cameraWhiteBalanceModes: List<Int> = CameraHelper.getWhiteBalanceModes().keys.toList(),
        val cameraCurrentWhiteBalanceMode : Int = 1
)

enum class CameraState{
    NONE,
    INITIALIZING,
    RUNNING,
    RESTART
}


