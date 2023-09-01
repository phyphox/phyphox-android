package de.rwth_aachen.phyphox.camera.model

import android.graphics.Rect
import android.os.Build
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import de.rwth_aachen.phyphox.camera.helper.CameraHelper

/**
 * Defines the current UI state of the camera
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
data class CameraUiState  constructor(
    val cameraState: CameraState = CameraState.NOT_READY,
    val availableSettings: List<CameraSettingMode> = emptyList(),
    val availableCameraLens: List<Int> = listOf(LENS_FACING_BACK),
    val editableCameraSettings: MutableMap<String, String>? = mutableMapOf(),
    val cameraLens: Int = LENS_FACING_BACK,
    val cameraHeight: Int = 0,
    val cameraWidth: Int = 0,
    // This holds the cropped image which is cropped by the user through the overlayView.
    val cameraPassepartout: Rect = Rect(),
    val overlayUpdateState: OverlayUpdateState = OverlayUpdateState.NO_UPDATE,

    val physicalSensorPixelHeight: Float = 0.0f,
    val physicalSensorPixelWidth: Float = 0.0f,
)

/**
 * Defines the current state of the camera
 */
enum class CameraState {
    NOT_READY,
    READY,
    LOADED
}

enum class OverlayUpdateState {
    NO_UPDATE,
    UPDATE,
    UPDATE_DONE
}
