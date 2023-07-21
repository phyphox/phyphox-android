package de.rwth_aachen.phyphox.camera.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler

import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.AlphaAnimation
import android.widget.ImageView

import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import androidx.camera.core.CameraSelector.LENS_FACING_FRONT
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import de.rwth_aachen.phyphox.MarkerOverlayView
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.helper.CameraInput
import de.rwth_aachen.phyphox.camera.helper.SettingChooseListener
import de.rwth_aachen.phyphox.camera.model.CameraSettingValueState
import de.rwth_aachen.phyphox.camera.model.CameraUiAction
import de.rwth_aachen.phyphox.camera.model.CameraUiState
import de.rwth_aachen.phyphox.camera.model.SettingMode
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import de.rwth_aachen.phyphox.camera.viewstate.CameraPreviewScreenViewState
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.lang.IllegalArgumentException


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraPreviewScreen(
    private val root: View,
    private val cameraInput: CameraInput,
    private val cameraViewModel: CameraViewModel
) {

    private val context: Context = root.context

    val TAG = "CameraPreviewScreen"

    val previewView: PreviewView = (root.findViewById(R.id.preview_view)) as PreviewView
    private val mainFrameLayout: ConstraintLayout = root.findViewById(R.id.mainFrameLayout)

    private val zoomSlider: Slider = root.findViewById(R.id.zoomSlider)
    private val zoomControl: LinearLayoutCompat = root.findViewById(R.id.zoomControl)

    // permissions
    private val permissionsRationaleContainer: View =
        root.findViewById(R.id.permissionsRationaleContainer)
    private val permissionsRationale: TextView = root.findViewById(R.id.permissionsRationale)
    private val permissionsRequestButton: TextView =
        root.findViewById(R.id.permissionsRequestButton)

    private val lnrSwitchLens = root.findViewById<LinearLayoutCompat>(R.id.lnrSwitchLens)
    private val lnrIso = root.findViewById<LinearLayoutCompat>(R.id.lnrImageIso)
    private val lnrShutter = root.findViewById<LinearLayoutCompat>(R.id.lnrImageShutter)
    private val lnrAperture = root.findViewById<LinearLayoutCompat>(R.id.lnrImageAperture)
    private val lnrAutoExposure = root.findViewById<LinearLayoutCompat>(R.id.lnrImageAutoExposure)
    private val lnrExposure = root.findViewById<LinearLayoutCompat>(R.id.lnrImageExposure)
    private val lnrColorCode = root.findViewById<LinearLayoutCompat>(R.id.llColorCode)

    //image buttons
    private val switchLensButton = root.findViewById<ImageView>(R.id.switchLens)
    private val imageViewIso = root.findViewById<ImageView>(R.id.imageIso)
    private val imageViewShutter = root.findViewById<ImageView>(R.id.imageShutter)
    private val imageViewAperture = root.findViewById<ImageView>(R.id.imageAperture)
    private val imageViewAutoExposure = root.findViewById<ImageView>(R.id.imageAutoExposure)
    private val imageViewExposure = root.findViewById<ImageView>(R.id.imageExposure)
    private val imageZoom = root.findViewById<ImageView>(R.id.imageZoom)

    // text views
    private val textViewCurrentIsoValue = root.findViewById<TextView>(R.id.textCurrentIso)
    private val textViewCurrentShutterValue = root.findViewById<TextView>(R.id.textCurrentShutter)
    private val textViewCurrentApertureValue = root.findViewById<TextView>(R.id.textCurrentAperture)
    private val textViewAutoExposureStatus =
        root.findViewById<TextView>(R.id.textAutoExposureStatus)
    private val textViewExposureStatus = root.findViewById<TextView>(R.id.textExposureStatus)
    private val textViewLens = root.findViewById<TextView>(R.id.textSwitchLens)
    private val tvColorCode = root.findViewById<TextView>(R.id.tvColorCode)

    private val recyclerViewExposureSetting = root.findViewById<RecyclerView>(R.id.recyclerViewCameraSetting)

    private val buttonWiderAngle: MaterialButton = root.findViewById(R.id.buttonWideAngle)
    private val buttonDefaultZoom: MaterialButton = root.findViewById(R.id.buttonDefaultZoom)
    private val buttonZoomTwoTimes: MaterialButton = root.findViewById(R.id.buttonZoomTimesTwo)
    private val buttonZoomFiveTimes: MaterialButton = root.findViewById(R.id.buttonTimesFive)

    private var autoExposure: Boolean = true

    //animation when button is clicked
    private val buttonClick = AlphaAnimation(1f, 0.4f)
    private val buttonClick1 = AlphaAnimation(1f, 0.0f)
    private var clickedButton: View? = null

    private var selectedPosition = RecyclerView.NO_POSITION

    var transformation = Matrix()

    var width: Int = 0
    var height: Int = 0

    var panningIndexX = 0
    var panningIndexY = 0

    var overlayView: MarkerOverlayView = MarkerOverlayView(context)

    // observable to observe the action performed
    private val _action: MutableSharedFlow<CameraUiAction> = MutableSharedFlow()
    val action: Flow<CameraUiAction> = _action

    private var showZoomSlider = false


    init {

        initializeAndSetupCameraDimension()

        setFrameTouchOnListener()

        switchLensButton.setOnClickListener {
            switchLens(root, it)
        }

        imageViewIso.setOnClickListener {
            setUpZoomVisibility()
            clickedButton = it
            onSettingClicked(SettingMode.ISO)
        }

        imageViewShutter.setOnClickListener {
            setUpZoomVisibility()
            clickedButton = it
            onSettingClicked(SettingMode.SHUTTER_SPEED)
        }

        imageViewAperture.setOnClickListener {
            setUpZoomVisibility()
            clickedButton = it
            onSettingClicked(SettingMode.APERTURE)
        }

        imageViewExposure.setOnClickListener {
            setUpZoomVisibility()
            clickedButton = it
            onSettingClicked(SettingMode.EXPOSURE)
        }

        imageViewAutoExposure.setOnClickListener { onSettingClicked(SettingMode.AUTO_EXPOSURE) }

        imageZoom.setOnClickListener {
            zoomControl.visibility = View.VISIBLE
            if(recyclerViewExposureSetting.isVisible){
                recyclerViewExposureSetting.visibility = View.GONE
            }
            if (showZoomSlider) {
                zoomControl.visibility = View.GONE
            }
            showZoomSlider = !showZoomSlider
        }

    }

    private fun setUpZoomVisibility(){
        if(zoomControl.isVisible){
            zoomControl.visibility = View.GONE
        }
    }
    fun setupZoomControl(cameraSettingState: CameraSettingValueState) {
        val zoomRatio = cameraSettingState.cameraZoomRatioConverted
        if(zoomRatio.isEmpty()){
            // while loading camera, zoomRatio might be null
            return
        }

        zoomSlider.valueFrom = 0.0f
        zoomSlider.valueTo = (zoomRatio.size - 1.0f)
        zoomSlider.value = zoomRatio.indexOf(1.0f).toFloat()

        changeZoomButtonColor(SelectedZoomButton.Default)

        zoomSlider.addOnChangeListener { _, value, _ ->
            if(zoomRatio.size < value) {
                // in some devices, the front camera sometime gives the value larger then ratio
                return@addOnChangeListener
            }
            if (zoomRatio.size != 0) {
                val mappedValue = zoomRatio[value.toInt()]
                cameraViewModel.camera?.cameraControl?.setZoomRatio(mappedValue)
                when (mappedValue) {
                    zoomRatio.first() -> changeZoomButtonColor(SelectedZoomButton.WiderAngle)
                    1.0f -> changeZoomButtonColor(SelectedZoomButton.Default)
                    2.0f -> changeZoomButtonColor(SelectedZoomButton.TwoTimes)
                    5.0f -> changeZoomButtonColor(SelectedZoomButton.FiveTimes)
                    else -> changeZoomButtonColor(SelectedZoomButton.None)
                }
            }
        }

        zoomSlider.setLabelFormatter { value: Float ->
            if (zoomRatio.size != 0) {
                val mappedValue = zoomRatio[value.toInt()]
                (mappedValue).toString() + "x"
            } else {
                value.toString()
            }
        }

        if (zoomRatio.first() < 1.0) {
            buttonWiderAngle.visibility = View.VISIBLE
        } else {
            buttonWiderAngle.visibility = View.GONE
        }

        buttonWiderAngle.setOnClickListener {
            changeZoomButtonColor(SelectedZoomButton.None)
            cameraViewModel.camera?.cameraControl?.setZoomRatio(cameraSettingState.cameraMinZoomRatio)
            zoomSlider.value = zoomRatio.indexOf(zoomRatio.first()).toFloat()
            changeZoomButtonColor(SelectedZoomButton.WiderAngle)
        }

        buttonDefaultZoom.setOnClickListener {
            changeZoomButtonColor(SelectedZoomButton.None)
            cameraViewModel.camera?.cameraControl?.setZoomRatio(1.0f)
            zoomSlider.value = zoomRatio.indexOf(1.0f).toFloat()
            changeZoomButtonColor(SelectedZoomButton.Default)
        }

        buttonZoomTwoTimes.setOnClickListener {
            changeZoomButtonColor(SelectedZoomButton.None)
            cameraViewModel.camera?.cameraControl?.setZoomRatio(2.0f)
            zoomSlider.value = zoomRatio.indexOf(2.0f).toFloat()
            changeZoomButtonColor(SelectedZoomButton.TwoTimes)
        }

        buttonZoomFiveTimes.setOnClickListener {
            changeZoomButtonColor(SelectedZoomButton.None)
            cameraViewModel.camera?.cameraControl?.setZoomRatio(5.0f)
            zoomSlider.value = zoomRatio.indexOf(5.0f).toFloat()
            changeZoomButtonColor(SelectedZoomButton.FiveTimes)
        }

    }

    private fun changeZoomButtonColor(selectedButton: SelectedZoomButton) {
        val activeColor = context.resources.getColor(R.color.phyphox_primary)
        val inactiveColor = context.resources.getColor(R.color.phyphox_black_100)
        when (selectedButton) {
            SelectedZoomButton.WiderAngle -> buttonWiderAngle.setBackgroundColor(activeColor)
            SelectedZoomButton.Default -> buttonDefaultZoom.setBackgroundColor(activeColor)
            SelectedZoomButton.TwoTimes -> buttonZoomTwoTimes.setBackgroundColor(activeColor)
            SelectedZoomButton.FiveTimes -> buttonZoomFiveTimes.setBackgroundColor(activeColor)
            SelectedZoomButton.None -> {
                buttonWiderAngle.setBackgroundColor(inactiveColor)
                buttonDefaultZoom.setBackgroundColor(inactiveColor)
                buttonZoomTwoTimes.setBackgroundColor(inactiveColor)
                buttonZoomFiveTimes.setBackgroundColor(inactiveColor)
            }
        }

    }

    enum class SelectedZoomButton {
        WiderAngle, Default, TwoTimes, FiveTimes, None
    }

    private fun initializeAndSetupCameraDimension() {
        // work around to get the height of preview view by delaying it so that the view is first laid
        val viewTreeObserver = previewView.viewTreeObserver
        viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // This callback will be triggered when the layout is complete
                Handler().postDelayed({
                    width = previewView.width
                    height = previewView.height
                    mainFrameLayout.removeView(overlayView)
                    mainFrameLayout.addView(overlayView, width, height)
                    root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                        _action.emit(
                            CameraUiAction.UpdateCameraDimension(
                                previewView.height,
                                previewView.width
                            )
                        )
                    }
                    root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                        _action.emit(CameraUiAction.UpdateOverlay)
                    }
                }, 200)
                // Remove the listener to avoid multiple callbacks
                previewView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    fun setCurrentValueInExposureSettingTextView(cameraSettingState: CameraSettingValueState) {
        autoExposure = cameraSettingState.autoExposure

        val isoValue = cameraSettingState.isoRange?.map { it.toInt() }
            ?.let { CameraHelper.findIsoNearestNumber(cameraSettingState.currentIsoValue, it) }
        textViewCurrentIsoValue.text = isoValue.toString()

        val fraction =
            CameraHelper.convertNanoSecondToSecond(cameraSettingState.currentShutterValue)
        textViewCurrentShutterValue.text =
            "".plus(fraction.numerator).plus("/").plus(fraction.denominator)

        textViewCurrentApertureValue.text =
            "f/".plus(cameraSettingState.currentApertureValue.toString())

        textViewExposureStatus.text = cameraSettingState.currentExposureValue.toString()

    }

    @SuppressLint("ClickableViewAccessibility")
    fun setFrameTouchOnListener() {
        mainFrameLayout.setOnTouchListener(View.OnTouchListener { v, event ->

            val touch = floatArrayOf(event.x, event.y)

            val invert = Matrix()
            transformation.invert(invert)
            invert.mapPoints(touch)
            val x: Float = touch[1] / height
            val y: Float = 1.0f - touch[0] / width

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d("previewView", "frame down")
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
                    Log.d("previewView", "frame move")
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

                    root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                        _action.emit(CameraUiAction.UpdateOverlay)
                    }

                }

                MotionEvent.ACTION_UP -> {
                    Log.d("previewView", "frame up")
                    v.performClick()
                }
            }
            true
        })
    }

    fun updateOverlay(cameraUiState: CameraUiState) {

        val inner = cameraUiState.cameraPassepartout.toRectF()
        val outer =
            RectF(0f, 0f, cameraUiState.cameraWidth.toFloat(), cameraUiState.cameraHeight.toFloat())
        transformation.mapRect(inner)
        transformation.mapRect(outer)
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

        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            _action.emit(CameraUiAction.OverlayUpdateDone)
        }
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

    private fun onClickCameraExposureSetting(settingMode: SettingMode, value: String) {
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            _action.emit(CameraUiAction.UpdateCameraExposureSettingValue(settingMode, value))
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

    private fun onSettingClicked(settingMode: SettingMode) {
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            when (settingMode) {
                SettingMode.NONE -> Unit
                SettingMode.ISO -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                SettingMode.SHUTTER_SPEED -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                SettingMode.APERTURE -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                SettingMode.EXPOSURE -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                SettingMode.AUTO_EXPOSURE -> _action.emit(CameraUiAction.UpdateAutoExposure(!autoExposure))
            }
        }
    }

    // Setup all the view state of the UI shown in the camera preview.
    fun setCameraScreenViewState(state: CameraScreenViewState) {
        setSwitchLensButtonViewState(state.cameraPreviewScreenViewState)
        setCameraExposureViewState(state.cameraPreviewScreenViewState)
        //setCameraSettingViewState(state.cameraPreviewScreenViewState)
        setCameraSettingRecyclerViewState(state.cameraPreviewScreenViewState)
        setCameraExposureControlViewState(state.cameraPreviewScreenViewState)
    }

    private fun setSwitchLensButtonViewState(state: CameraPreviewScreenViewState) {
        lnrSwitchLens.isEnabled = state.switchLensButtonViewState.isEnabled
        lnrSwitchLens.isVisible = state.switchLensButtonViewState.isVisible
    }

    private fun setCameraExposureControlViewState(state: CameraPreviewScreenViewState) {
        lnrIso.isVisible = state.isoButtonViewState.isVisible
        lnrShutter.isVisible = state.shutterButtonViewState.isVisible
        lnrAperture.isVisible = state.apertureButtonViewState.isVisible
        lnrExposure.isVisible = state.exposureViewState.isVisible
        lnrAutoExposure.isVisible = state.autoExposureViewState.isVisible
    }

    private fun setCameraSettingRecyclerViewState(state: CameraPreviewScreenViewState) {
        recyclerViewExposureSetting.isVisible = state.cameraSettingRecyclerViewState.isOpened
    }

    fun setCameraSettingButtonValue(state: CameraSettingValueState) {
        if (state.currentIsoValue != 0) textViewCurrentIsoValue.text =
            state.currentIsoValue.toString()
        if (state.currentShutterValue != 0L) {
            val fraction = CameraHelper.convertNanoSecondToSecond(state.currentShutterValue)
            textViewCurrentShutterValue.text =
                "".plus(fraction.numerator).plus("/").plus(fraction.denominator)
        }
        if (state.currentApertureValue != 0.0f) textViewCurrentApertureValue.text =
            state.currentApertureValue.toString()
        if (state.currentExposureValue != 0.0f) textViewExposureStatus.text =
            state.currentExposureValue.toString()
    }

    fun setCameraSwitchInfo(state: CameraUiState) {
        if (state.cameraLens == LENS_FACING_FRONT) {
            textViewLens.text = context.getText(R.string.cameraFront)
        } else if (state.cameraLens == LENS_FACING_BACK) {
            textViewLens.text = context.getText(R.string.cameraBack)
        }
    }

    // From the ViewState, update the state of the ImageView and TexTView of Exposure Setting UI
    private fun setCameraExposureViewState(state: CameraPreviewScreenViewState) {
        val autoExposureEnabled = state.autoExposureViewState.isEnabled

        imageViewIso.setImageDrawable(
            getDrawableAsSettingState(
                SettingMode.ISO,
                autoExposureEnabled
            )
        )
        imageViewShutter.setImageDrawable(
            getDrawableAsSettingState(
                SettingMode.SHUTTER_SPEED,
                autoExposureEnabled
            )
        )
        imageViewAperture.setImageDrawable(
            getDrawableAsSettingState(
                SettingMode.APERTURE,
                autoExposureEnabled
            )
        )
        imageViewExposure.setImageDrawable(
            getDrawableAsSettingState(
                SettingMode.EXPOSURE,
                autoExposureEnabled
            )
        )

        setTextViewAsExposureSetting(SettingMode.ISO, autoExposureEnabled)
        setTextViewAsExposureSetting(SettingMode.SHUTTER_SPEED, autoExposureEnabled)
        setTextViewAsExposureSetting(SettingMode.APERTURE, autoExposureEnabled)
        setTextViewAsExposureSetting(SettingMode.EXPOSURE, autoExposureEnabled)
        setTextViewAsExposureSetting(SettingMode.AUTO_EXPOSURE, autoExposureEnabled)
    }

    // From the exposure setting mode and auto exposure mode, set the click-ability of an image view
    // and return the drawable property of the image view
    private fun getDrawableAsSettingState(
        settingMode: SettingMode,
        autoExposureEnabled: Boolean
    ): Drawable? {

        val imageViewMap = mapOf(
            SettingMode.ISO to imageViewIso,
            SettingMode.SHUTTER_SPEED to imageViewShutter,
            SettingMode.APERTURE to imageViewAperture,
            SettingMode.EXPOSURE to imageViewExposure
        )

        val imageView = imageViewMap[settingMode]
        imageView?.isClickable = autoExposureEnabled

        val resId = when (settingMode) {
            SettingMode.ISO -> R.drawable.ic_camera_iso
            SettingMode.SHUTTER_SPEED -> R.drawable.baseline_shutter_speed_24
            SettingMode.APERTURE -> R.drawable.ic_camera_aperture
            SettingMode.EXPOSURE -> R.drawable.ic_exposure
            SettingMode.AUTO_EXPOSURE -> R.drawable.ic_auto_exposure
            SettingMode.NONE -> R.drawable.ic_empty
        }

        val res = AppCompatResources.getDrawable(context, resId)

        if (!autoExposureEnabled) res?.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)

        return res
    }

    // From the exposure setting mode and auto exposure mode,
    // set the TextView color and alter the text in the Auto Exposure as per exposure setting state
    private fun setTextViewAsExposureSetting(
        settingMode: SettingMode,
        autoExposureEnabled: Boolean
    ) {

        val textViewMap = mapOf(
            SettingMode.ISO to textViewCurrentIsoValue,
            SettingMode.SHUTTER_SPEED to textViewCurrentShutterValue,
            SettingMode.APERTURE to textViewCurrentApertureValue,
            SettingMode.EXPOSURE to textViewExposureStatus,
            SettingMode.AUTO_EXPOSURE to textViewAutoExposureStatus
        )

        val inactiveTextColor = Color.GRAY
        val activeTextColor = ContextCompat.getColor(context, R.color.phyphox_white_100)

        val textView = textViewMap[settingMode]

        if (textView == textViewAutoExposureStatus)
            textView?.text = context.getText(if (autoExposureEnabled) R.string.off else R.string.on)
        else
            textView?.setTextColor(if (autoExposureEnabled) activeTextColor else inactiveTextColor)

    }

    /**
     * From the Exposure setting list, mode and current selected value; show list of the
     * exposure value in RecyclerView and react to the click event to update the current selected value.
     * When the Exposure list is empty or with only 1 value, do not create and show RecyclerView.
    */
    fun populateAndShowExposureSettingValue(
        dataList: List<String>?,
        settingMode: SettingMode,
        currentValue: String
    ) {
        if (dataList.isNullOrEmpty() || dataList.size == 1) {
            return
        }

        val settingChangeListener = object : SettingChooseListener {
            override fun onSettingClicked(value: String) {
                Log.d(TAG, value)
                onClickCameraExposureSetting(settingMode, value)
            }
        }

        clickedButton?.startAnimation(buttonClick)
        with(recyclerViewExposureSetting) {

            val mLayoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            val dividerItemDecoration = DividerItemDecoration(context, mLayoutManager.orientation)

            AppCompatResources.getDrawable(context, R.drawable.custom_line_seperator)
                ?.let { dividerItemDecoration.setDrawable(it) }
            recyclerViewExposureSetting.addItemDecoration(dividerItemDecoration)

            layoutManager = mLayoutManager
            itemAnimator = DefaultItemAnimator()

            val adapter =
                ChooseCameraSettingValueAdapter(dataList, settingChangeListener, currentValue)

            recyclerViewExposureSetting.adapter = adapter

            selectedPosition = dataList.indexOf(currentValue)
            recyclerViewExposureSetting.postDelayed(Runnable {
                recyclerViewExposureSetting.scrollToPosition(selectedPosition)
            }, 100)

        }

        recyclerLoadingFinished()

    }

    fun recyclerLoadingFinished() {
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            _action.emit(CameraUiAction.ExposureSettingValueSelected)
        }
    }

    fun setColorCodeText(colorCode: String) {
        if (colorCode == "") {
            lnrColorCode.visibility = View.GONE
        } else {
            lnrColorCode.visibility = View.VISIBLE
            try {
                tvColorCode.setText(colorCode)
                lnrColorCode.setBackgroundColor(Color.parseColor("#$colorCode"))
            } catch (e: IllegalArgumentException) {
                tvColorCode.setText(colorCode + ": " + e)
                Log.e(TAG, "ColorCode $e")
            }
        }

    }

}
