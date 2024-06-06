package de.rwth_aachen.phyphox.camera.model

import android.widget.ImageView
import android.widget.TextView
import de.rwth_aachen.phyphox.camera.ui.CameraPreviewScreen

data class ImageButtonViewState(
    val imageView: ImageView,
    val drawableResId: Int,
    val isEnabled: Boolean
)

data class TextViewCameraSettingViewState(
    val textView: TextView,
    val isEnabled: Boolean
)

data class ZoomButtonInfo(
    val zoomValue: Float,
    val button: CameraPreviewScreen.SelectedZoomButton
)
