package de.rwth_aachen.phyphox.camera.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.rwth_aachen.phyphox.ExpView
import de.rwth_aachen.phyphox.MarkerOverlayView
import de.rwth_aachen.phyphox.PhyphoxExperiment
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.helper.CameraInput
import de.rwth_aachen.phyphox.camera.helper.SettingChangeListener
import de.rwth_aachen.phyphox.camera.helper.SettingChooseListener
import de.rwth_aachen.phyphox.camera.model.CameraSettingValueState
import de.rwth_aachen.phyphox.camera.model.CameraUiAction
import de.rwth_aachen.phyphox.camera.model.CameraUiState
import de.rwth_aachen.phyphox.camera.model.SettingMode
import de.rwth_aachen.phyphox.camera.viewstate.CameraPreviewScreenViewState
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import de.rwth_aachen.phyphox.camera.viewstate.CameraSettingViewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraPreviewScreen(private val root: View, private val cameraInput: CameraInput) {

    private val context: Context = root.context

    val TAG = "CameraPreviewScreen"

    val previewView: PreviewView = (root.findViewById(R.id.preview_view)) as PreviewView
    private val mainFrameLayout: FrameLayout = root.findViewById(R.id.mainFrameLayout)

    // permissions
    private val permissionsRationaleContainer: View =
        root.findViewById(R.id.permissionsRationaleContainer)
    private val permissionsRationale: TextView = root.findViewById(R.id.permissionsRationale)
    private val permissionsRequestButton: TextView =
        root.findViewById(R.id.permissionsRequestButton)

    var cameraSettingSliders: MutableMap<String, AppCompatSeekBar> = HashMap()

    //image buttons
    private val switchLensButton = root.findViewById<ImageView>(R.id.switchLens)
    private val imageViewIso = root.findViewById<ImageView>(R.id.imageIso)
    private val imageViewShutter = root.findViewById<ImageView>(R.id.imageShutter)
    private val imageViewAperture = root.findViewById<ImageView>(R.id.imageAperture)
    private val imageViewAutoExposure = root.findViewById<ImageView>(R.id.imageAutoExposure)

    // text views
    private val textViewCurrentIsoValue = root.findViewById<TextView>(R.id.textCurrentIso)
    private val textViewCurrentShutterValue = root.findViewById<TextView>(R.id.textCurrentShutter)
    private val textViewCurrentApertureValue = root.findViewById<TextView>(R.id.textCurrentAperture)
    private val textViewAutoExposureStatus = root.findViewById<TextView>(R.id.textAutoExposureStatus)

    //animation when button is clicked
    private val buttonClick = AlphaAnimation(1f, 0.4f)

    private var selectedPosition = RecyclerView.NO_POSITION

    private var autoExposure: Boolean = true

    var width = 800
    var height = 800

    var transformation = Matrix()

    var panningIndexX = 0
    var panningIndexY = 0

    var overlayView: MarkerOverlayView = MarkerOverlayView(context)

    // observable to observe the action performed
    private val _action: MutableSharedFlow<CameraUiAction> = MutableSharedFlow()
    val action: Flow<CameraUiAction> = _action

    init {
        mainFrameLayout.addView(overlayView, width, height)
        setFrameTouchOnListener()

        switchLensButton.setOnClickListener {
            switchLens(root, it)
        }

        //val nanoseconds = 279_590_625L
        val nanoseconds = 26_503L
        val fraction = CameraHelper.convertNanoSecondToSecond(nanoseconds)
        val numerator = fraction.numerator
        val denominator = fraction.denominator
        Log.d(TAG, "$nanoseconds nanoseconds is equal to $numerator / $denominator seconds.")

        autoExposure = cameraInput.autoExposure

        cameraInput.shutterSpeedCurrentValue?.let {
            val fraction = CameraHelper.convertNanoSecondToSecond(it)

            textViewCurrentShutterValue.text = "" + fraction.numerator + "/" + fraction.denominator
        }
        textViewCurrentApertureValue.text = "f/" + cameraInput.apertureCurrentValue.toString()

        imageViewIso.setOnClickListener { onSettingClicked(SettingMode.ISO, it) }
        imageViewShutter.setOnClickListener { onSettingClicked(SettingMode.SHUTTER_SPEED, it) }
        imageViewAperture.setOnClickListener { onSettingClicked(SettingMode.APERTURE, it) }
        imageViewAutoExposure.setOnClickListener { onSettingClicked(SettingMode.AUTO_EXPOSURE, it) }

    }

    @SuppressLint("ClickableViewAccessibility")
    fun setFrameTouchOnListener() {
        mainFrameLayout.setOnTouchListener(View.OnTouchListener { v, event ->

            val touch = floatArrayOf(event.x, event.y)

            val invert = Matrix()
            transformation.invert(invert)
            invert.mapPoints(touch)
            val x: Float = touch[1] / 1000f // TODO getHeight instead
            val y: Float = 1.0f - touch[0] / 1000f

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d("CameraPreviewFramgment", "ACTION_DOWN")
                    val d11: Float =
                        (x - cameraInput.x1) * (x - cameraInput.x1) + (y - cameraInput.y1) * (y - cameraInput.y1)
                    val d12: Float =
                        (x - cameraInput.x1) * (x - cameraInput.x1) + (y - cameraInput.y2) * (y - cameraInput.y2)
                    val d21: Float =
                        (x - cameraInput.x2) * (x - cameraInput.x2) + (y - cameraInput.y1) * (y - cameraInput.y1)
                    val d22: Float =
                        (x - cameraInput.x2) * (x - cameraInput.x2) + (y - cameraInput.y2) * (y - cameraInput.y2)

                    val dist = 0.1f

                    if (d11 < dist && d11 < d12 && d11 < d21 && d11 < d22) {
                        panningIndexX = 1
                        panningIndexY = 1
                    } else if (d12 < dist && d12 < d21 && d12 < d22) {
                        panningIndexX = 1
                        panningIndexY = 2
                    } else if (d21 < dist && d21 < d22) {
                        panningIndexX = 2
                        panningIndexY = 1
                    } else if (d22 < dist) {
                        panningIndexX = 2
                        panningIndexY = 2
                    } else {
                        panningIndexX = 0
                        panningIndexY = 0

                    }

                }

                MotionEvent.ACTION_MOVE -> {
                    Log.d("CameraPreviewFramgment", "ACTION_MOVE")
                    if (panningIndexX == 1) {
                        cameraInput.x1 = x
                    } else if (panningIndexX == 2) {
                        cameraInput.x2 = x
                    }

                    if (panningIndexY == 1) {
                        cameraInput.y1 = y
                    } else if (panningIndexY == 2) {
                        cameraInput.y2 = y
                    }

                    updateOverlay()

                }

                MotionEvent.ACTION_UP -> {
                    Log.d("CameraPreviewFramgment", "ACTION_UP")
                    v.performClick()
                }
            }
            true
        })
    }

    private fun updateOverlay() {
        //TODO dynamic assigning of the width and height
        val xmin: Float = Math.min(cameraInput.x1, cameraInput.x2)
        val xmax: Float = Math.max(cameraInput.x1, cameraInput.x2)
        val ymin: Float = Math.min(cameraInput.y1, cameraInput.y2)
        val ymax: Float = Math.max(cameraInput.y1, cameraInput.y2)
        val inner = RectF(
            (1.0f - ymax) * width,
            xmin * height,
            (1.0f - ymin) * width,
            xmax * height
        )
        val outer = RectF(0f, 0f, width.toFloat(), height.toFloat())
        transformation.mapRect(inner)
        transformation.mapRect(outer)
        val off = IntArray(2)
        val off2 = IntArray(2)
        val points: Array<Point?>?
        if (true) {
            points = arrayOfNulls(4)
            points[0] = Point(Math.round(inner.left), Math.round(inner.top))
            points[1] = Point(Math.round(inner.right), Math.round(inner.top))
            points[2] = Point(Math.round(inner.left), Math.round(inner.bottom))
            points[3] = Point(Math.round(inner.right), Math.round(inner.bottom))
        } else points = null
        overlayView.setClipRect(outer)
        overlayView.setPassepartout(inner)
        overlayView.update(null, points)
    }

    fun hidePermissionsRequest() {
        permissionsRationaleContainer.isVisible = false
    }

    fun showPermissionsRequest(shouldShowRationale: Boolean) {
        permissionsRationaleContainer.isVisible = true
        if (shouldShowRationale) {
            permissionsRationale.text =
                "You can\\'t use camera extensions unless CameraX Extensions has access to your camera."
        } else {
            permissionsRationale.text =
                "Allow CameraX Extensions access to your camera to try camera extensions."
        }
    }

    fun setUpOverlay() {
        mainFrameLayout.removeView(overlayView)
        height = mainFrameLayout.height
        width = mainFrameLayout.width
        mainFrameLayout.addView(overlayView, width, height)
        updateOverlay()

    }

    private fun onClickCameraSetting(settingMode: SettingMode, value: String) {
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            _action.emit(CameraUiAction.ChangeCameraSettingValue(settingMode, value))
        }
    }

    private fun switchLens(root: View, switchLensButton: View) {
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            _action.emit(CameraUiAction.SwitchCameraClick)
        }
        switchLensButton.animate().apply {
            rotation(180f)
            duration = 300L
            setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    switchLensButton.rotation = 0f
                }
            })
            start()
        }
    }

    private fun onSettingClicked(settingMode: SettingMode, view: View){
        Log.d(TAG, settingMode.name)
        val buttonLocation = IntArray(2)
        view.startAnimation(buttonClick)
        view.getLocationInWindow(buttonLocation)
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            when (settingMode){
                SettingMode.NONE -> TODO()
                SettingMode.ISO -> {
                    val isoRange = CameraHelper.isoRange(47, 11906).map { it.toString() }
                    showCustomDialog(view, buttonLocation, isoRange, SettingMode.ISO)
                }
                SettingMode.SHUTTER_SPEED -> {
                    val shutterSpeedRange = CameraHelper.shutterSpeedRange(26_503L, 279_590_625L)
                        .map { "" + it.numerator + "/" + it.denominator }
                    showCustomDialog(view, buttonLocation, shutterSpeedRange, settingMode)
                }
                SettingMode.APERTURE -> {
                    showCustomDialog(view, buttonLocation, arrayListOf("1.4"), settingMode)
                }
                SettingMode.AUTO_EXPOSURE -> {
                    autoExposure = !autoExposure
                    _action.emit(CameraUiAction.ChangeAutoExposure(autoExposure))
                }
            }
        }
        }

    fun setCameraScreenViewState(state: CameraScreenViewState) {
        setCameraPreviewScreenViewState(state.cameraPreviewScreenViewState)
        setCameraSettingViewState(state.cameraPreviewScreenViewState)

    }

    private fun setCameraPreviewScreenViewState(state: CameraPreviewScreenViewState) {
        switchLensButton.isEnabled = state.switchLensButtonViewState.isEnabled
        switchLensButton.isVisible = state.switchLensButtonViewState.isVisible
        setCameraExposureViewState(state)
    }

    private fun setCameraSettingViewState(state: CameraPreviewScreenViewState) {
        imageViewIso.isVisible = state.isoButtonViewState.isVisible
        imageViewIso.isEnabled = state.isoButtonViewState.isEnabled

        imageViewShutter.isVisible = state.shutterButtonViewState.isVisible
        imageViewShutter.isEnabled = state.shutterButtonViewState.isEnabled

        imageViewAperture.isVisible = state.apertureButtonViewState.isVisible
        imageViewAperture.isEnabled = state.apertureButtonViewState.isEnabled

    }

    fun setCameraSettingButtonValue(state: CameraSettingValueState){
        if(state.currentIsoValue != 0) textViewCurrentIsoValue.text = state.currentIsoValue.toString()
        if(state.currentShutterValue != 0L) {
            val fraction = CameraHelper.convertNanoSecondToSecond(state.currentShutterValue)
            textViewCurrentShutterValue.text = "".plus(fraction.numerator).plus("/").plus(fraction.denominator)
        }
        if(state.currentApertureValue != 0.0f) textViewCurrentApertureValue.text = state.currentApertureValue.toString()


    }


    private fun setCameraExposureViewState(state: CameraPreviewScreenViewState){
        if(state.autoExposureViewState.isEnabled){
            Log.d(TAG, "setCameraSxposure: True")
            imageViewAutoExposure.setBackgroundColor(Color.TRANSPARENT)
            imageViewIso.setBackgroundColor(Color.TRANSPARENT)
            imageViewIso.isClickable = true
            imageViewShutter.isClickable = true
            imageViewAperture.isClickable = true
            imageViewShutter.setBackgroundColor(Color.TRANSPARENT)
            imageViewAperture.setBackgroundColor(Color.TRANSPARENT)
            textViewAutoExposureStatus.text = "Off"
        } else {
            Log.d(TAG, "setCameraSxposure")
            imageViewAutoExposure.setBackgroundColor(context.resources.getColor(R.color.phyphox_white_50_black_50))
            textViewAutoExposureStatus.text = "On"
            imageViewIso.setBackgroundColor(context.resources.getColor(R.color.phyphox_white_50_black_50))
            imageViewShutter.setBackgroundColor(context.resources.getColor(R.color.phyphox_white_50_black_50))
            imageViewAperture.setBackgroundColor(context.resources.getColor(R.color.phyphox_white_50_black_50))
            imageViewIso.isClickable = false
            imageViewShutter.isClickable = false
            imageViewAperture.isClickable = false
        }
    }

    private val settingChangeListener = object : SettingChangeListener {
        override fun onProgressChange(settingMode: SettingMode, value: Int) {
            root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                _action.emit(CameraUiAction.ReloadCameraSettings(settingMode, value))
            }
        }
    }

    fun slidersViewSetup(phyphoxExperiment: PhyphoxExperiment?) {
        cameraSettingSliders.clear()
        phyphoxExperiment?.experimentViews?.elementAt(0)?.elements?.forEach {
            if (it.javaClass == ExpView.editElement::class.java) {
                val editElement = it as ExpView.editElement
                cameraSettingSliders[editElement.label] = editElement.seekBar
                if (editElement.settingChangeListener == null) {
                    editElement.settingChangeListener = settingChangeListener
                }
            }
        }
    }

    private fun showCustomDialog(
        view: View,
        buttonLocation: IntArray,
        dataList: List<String>,
        settingMode: SettingMode
    ) {
        Log.d(TAG, "showCustomDialog")

        if (dataList.isEmpty() || dataList.size == 1) {
            return
        }

        val dialogView =
            LayoutInflater.from(view.context).inflate(R.layout.custom_camera_setting_dialog, null)

        val dialog = Dialog(dialogView.context)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)

        val window = dialog.window
        val dialogLayoutParams = WindowManager.LayoutParams().apply {
            copyFrom(window?.attributes)
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = buttonLocation[0] - 450
            y = buttonLocation[1] - 750
        }
        window?.attributes = dialogLayoutParams

        val settingChangeListener = object : SettingChooseListener {
            override fun onSettingClicked(value: String, position: Int) {
                onClickCameraSetting(settingMode, value)
                selectedPosition = position
                dialog.dismiss()
            }
        }

        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        recyclerView.adapter =
            ChooseCameraSettingValueAdapter(dataList, settingChangeListener, selectedPosition)

        dialog.show()

    }

}
