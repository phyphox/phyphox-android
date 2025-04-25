package de.rwth_aachen.phyphox.camera.model

import android.graphics.RectF
import android.hardware.camera2.CaptureRequest
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

    val sensorFrameDuration: Long = 1_000_000_000/60,

    val currentIsoValue: Int = 1,
    val isoRange: List<Int>? =  emptyList(),

    val currentShutterValue: Long = 1L,
    var shutterSpeedRange:  List<CameraHelper.Fraction>? = emptyList(),

    val currentApertureValue: Float = 1.0f,
    var apertureRange:  List<Float>? = emptyList(),

    val currentExposureValue: Float = 0.0f,
    var exposureRange: List<Float>? = emptyList(),

    val autoExposure: Boolean = true,
    val cameraState: CameraState = CameraState.NONE,

    val cameraMaxZoomRatio: Float = 0.0f,
    val cameraMinZoomRatio: Float = 0.0f,
    val cameraZoomRatio: Float = 0.0f,
    val cameraLinearRatio: Float = 0.0f,
    val cameraMaxOpticalZoom: Float? = 1.0f,

    val cameraMaxRegionAWB: Int = 0,
    val cameraWhiteBalanceManualRange: List<Int> = CameraHelper.getWhiteBalanceTemperatureList(),
    val cameraCurrentWhiteBalanceManualValue: FloatArray = CameraHelper.convertTemperatureToRggb(5600),
    val cameraWhiteBalanceModes: List<Int> = CameraHelper.getWhiteBalanceModes().keys.toList(),
    val cameraCurrentWhiteBalanceMode : Int = CaptureRequest.CONTROL_AWB_MODE_AUTO
)

enum class CameraState{
    NONE,
    INITIALIZING,
    RUNNING,
    RESTART,
    SHUTDOWN
}


