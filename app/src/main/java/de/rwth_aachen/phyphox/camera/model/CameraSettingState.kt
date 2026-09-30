package de.rwth_aachen.phyphox.camera.model

import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import de.rwth_aachen.phyphox.camera.analyzer.SpectroscopyAnalyzer
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.helper.WhiteBalance

/**
 * Defines the current Camera Settings state of camera
 */
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

    //White balance by white point (file format 1.21): the request, the value in effect after clamping (or as the
    //camera reports it on the CCT path), and how this camera reaches it
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val whiteBalanceTemperature: Int = WhiteBalance.DEFAULT_TEMPERATURE, //Kelvin
    val whiteBalanceTint: Float = 0.0f, //Duv
    val whiteBalanceTemperatureInEffect: Int = WhiteBalance.DEFAULT_TEMPERATURE,
    val whiteBalanceTintInEffect: Float = 0.0f,
    val whiteBalanceTemperatureRange: IntRange = WhiteBalance.MIN_TEMPERATURE..WhiteBalance.MAX_TEMPERATURE,
    val whiteBalanceMethod: WhiteBalanceMethod = WhiteBalanceMethod.AWB_LOCK,
    val whiteBalanceLockedByFile: Boolean = false, //set by the experiment file: the camera-gui control is disabled
    val whiteBalanceFrozen: Boolean = false, //the AWB lock is engaged

    val spectrumAnalysisOrientation: SpectroscopyAnalyzer.SpectrumOrientation = SpectroscopyAnalyzer.SpectrumOrientation.LANDSCAPE
) {
    //Whether the shaders apply the adaptation from the daylight anchor to the requested white point
    fun whiteBalanceCorrectionActive(): Boolean =
        whiteBalanceMode == WhiteBalanceMode.TEMPERATURE && whiteBalanceMethod != WhiteBalanceMethod.CCT

    //The request clamped to what this camera can reach
    fun withWhiteBalanceInEffect(): CameraSettingState {
        val maxTint = if (whiteBalanceMethod == WhiteBalanceMethod.CCT) WhiteBalance.MAX_TINT_CCT else WhiteBalance.MAX_TINT
        return copy(
            whiteBalanceTemperatureInEffect = whiteBalanceTemperature.coerceIn(whiteBalanceTemperatureRange),
            whiteBalanceTintInEffect = whiteBalanceTint.coerceIn(-maxTint, maxTint)
        )
    }
}

enum class WhiteBalanceMode {
    AUTO, LOCKED, TEMPERATURE
}

//How a colour temperature is reached on this camera (phyphox-docs spec/input.yml, camera/locked): the calibrated
//CCT colour-correction mode of Android 16, the daylight AWB mode as a D65 anchor plus the adaptation in the
//shaders, or the AWB lock at the first start with the adaptation relative to that unknown anchor
enum class WhiteBalanceMethod {
    CCT, DAYLIGHT_ANCHOR, AWB_LOCK
}

enum class CameraState{
    NONE,
    INITIALIZING,
    RUNNING,
    RESTART,
    SHUTDOWN
}


