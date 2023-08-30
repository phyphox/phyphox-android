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
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.view.View
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
import de.rwth_aachen.phyphox.camera.model.ExposureSettingMode
import de.rwth_aachen.phyphox.camera.model.ImageButtonViewState
import de.rwth_aachen.phyphox.camera.model.TextViewCameraSettingViewState
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import de.rwth_aachen.phyphox.camera.viewstate.CameraPreviewScreenViewState
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.text.DecimalFormat


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
    private val whiteBalanceSlider: Slider = root.findViewById(R.id.whiteBalanceSlider)
    private val zoomControl: LinearLayoutCompat = root.findViewById(R.id.zoomControl)

    private val lnrSwitchLens = root.findViewById<LinearLayoutCompat>(R.id.lnrSwitchLens)
    private val lnrIso = root.findViewById<LinearLayoutCompat>(R.id.lnrImageIso)
    private val lnrShutter = root.findViewById<LinearLayoutCompat>(R.id.lnrImageShutter)
    private val lnrAperture = root.findViewById<LinearLayoutCompat>(R.id.lnrImageAperture)
    private val lnrAutoExposure = root.findViewById<LinearLayoutCompat>(R.id.lnrImageAutoExposure)
    private val lnrExposure = root.findViewById<LinearLayoutCompat>(R.id.lnrImageExposure)
    private val lnrColorCode = root.findViewById<LinearLayoutCompat>(R.id.llColorCode)
    private val lnrWhiteBalance = root.findViewById<LinearLayoutCompat>(R.id.lnrWhiteBalance)

    //image buttons
    private val switchLensButton = root.findViewById<ImageView>(R.id.switchLens)
    private val imageViewIso = root.findViewById<ImageView>(R.id.imageIso)
    private val imageViewShutter = root.findViewById<ImageView>(R.id.imageShutter)
    private val imageViewAperture = root.findViewById<ImageView>(R.id.imageAperture)
    private val imageViewAutoExposure = root.findViewById<ImageView>(R.id.imageAutoExposure)
    private val imageViewExposure = root.findViewById<ImageView>(R.id.imageExposure)
    private val imageZoom = root.findViewById<ImageView>(R.id.imageZoom)
    private val imageWhiteBalance = root.findViewById<ImageView>(R.id.imageWhiteBalance)

    // text views
    private val textViewCurrentIsoValue = root.findViewById<TextView>(R.id.textCurrentIso)
    private val textViewCurrentShutterValue = root.findViewById<TextView>(R.id.textCurrentShutter)
    private val textViewCurrentApertureValue = root.findViewById<TextView>(R.id.textCurrentAperture)
    private val textViewAutoExposureStatus = root.findViewById<TextView>(R.id.textAutoExposureStatus)
    private val textViewExposureStatus = root.findViewById<TextView>(R.id.textExposureStatus)
    private val textViewLens = root.findViewById<TextView>(R.id.textSwitchLens)
    private val tvColorCode = root.findViewById<TextView>(R.id.tvColorCode)
    private val textWhiteBalance = root.findViewById<TextView>(R.id.textWhiteBalance)

    private val recyclerViewExposureSetting = root.findViewById<RecyclerView>(R.id.recyclerViewCameraSetting)

    private val buttonWiderAngle: MaterialButton = root.findViewById(R.id.buttonWideAngle)
    private val buttonDefaultZoom: MaterialButton = root.findViewById(R.id.buttonDefaultZoom)
    private val buttonZoomTwoTimes: MaterialButton = root.findViewById(R.id.buttonZoomTimesTwo)
    private val buttonZoomFiveTimes: MaterialButton = root.findViewById(R.id.buttonTimesFive)
    private val buttonZoomTenTimes: MaterialButton = root.findViewById(R.id.buttonTimesTen)

    private var autoExposure: Boolean = true

    //animation when button is clicked
    private val buttonClick = AlphaAnimation(1f, 0.4f)
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
            setCameraSettingsVisibility(CameraSettingsView.ExposureSettingListView)
            clickedButton = it
            onSettingClicked(ExposureSettingMode.ISO)
        }

        imageViewShutter.setOnClickListener {
            setCameraSettingsVisibility(CameraSettingsView.ExposureSettingListView)
            clickedButton = it
            onSettingClicked(ExposureSettingMode.SHUTTER_SPEED)
        }

        /**
        imageViewAperture.setOnClickListener {
            setCameraSettingsVisibility(CameraSettingsView.ExposureSettingListView)
            clickedButton = it
            onSettingClicked(ExposureSettingMode.APERTURE)
        }
        */

        imageViewExposure.setOnClickListener {
            setCameraSettingsVisibility(CameraSettingsView.ExposureSettingListView)
            clickedButton = it
            onSettingClicked(ExposureSettingMode.EXPOSURE)
        }

        imageViewAutoExposure.setOnClickListener { onSettingClicked(ExposureSettingMode.AUTO_EXPOSURE) }

        imageZoom.setOnClickListener {
            setCameraSettingsVisibility(CameraSettingsView.ZoomSliderView)
        }

        imageWhiteBalance.setOnClickListener {
            setCameraSettingsVisibility(CameraSettingsView.ExposureSettingListView)
            clickedButton = it
            onSettingClicked(ExposureSettingMode.WHITE_BALANCE)
        }

    }
    private fun setCameraSettingsVisibility(cameraSettingsView: CameraSettingsView){
        when (cameraSettingsView) {
            CameraSettingsView.ExposureSettingListView -> {
                if(zoomControl.isVisible){
                    zoomControl.visibility = View.GONE
                }
                if(whiteBalanceSlider.isVisible){
                    whiteBalanceSlider.visibility = View.GONE
                }
                clickedButton?.startAnimation(buttonClick)
            }
            CameraSettingsView.ZoomSliderView -> {
                if(recyclerViewExposureSetting.isVisible){
                    recyclerViewExposureSetting.visibility = View.GONE
                }
                if(whiteBalanceSlider.isVisible){
                    whiteBalanceSlider.visibility = View.GONE
                }

                zoomControl.visibility = View.VISIBLE
                if (showZoomSlider) {
                    zoomControl.visibility = View.GONE
                }
                showZoomSlider = !showZoomSlider

            }
        }

    }

    private enum class CameraSettingsView {
        ExposureSettingListView, ZoomSliderView
    }

    fun setUpWhiteBalanceControl(cameraSettingState: CameraSettingValueState){
        val wbRange = cameraSettingState.cameraWhiteBalanceManualRange
        if(wbRange.isEmpty())
            return

        whiteBalanceSlider.valueFrom = 0.0f
        whiteBalanceSlider.valueTo = 10.0f
        whiteBalanceSlider.value = 5.0f


        //TODO while using descrete slider, there is lots of callback inside the changeListener, and thus update is sending error
        // and while using continues slider, the issue is gone but need to implement the change continuosly.
        /**
        whiteBalanceSlider.addOnChangeListener { _, value, fromUser ->

                val selectedValue =  when (value) {
                    0.0f ->  CameraHelper.convertTemperatureToRggb(3000)
                    1.0f -> CameraHelper.convertTemperatureToRggb(4000)
                    2.0f -> CameraHelper.convertTemperatureToRggb(5000)
                    3.0f -> CameraHelper.convertTemperatureToRggb(6000)
                    4.0f -> CameraHelper.convertTemperatureToRggb(7000)
                    5.0f -> CameraHelper.convertTemperatureToRggb(8000)
                    6.0f -> CameraHelper.convertTemperatureToRggb(9000)
                    7.0f -> CameraHelper.convertTemperatureToRggb(10000)
                    8.0f -> CameraHelper.convertTemperatureToRggb(12000)
                    9.0f -> CameraHelper.convertTemperatureToRggb(14000)
                    10.0f -> CameraHelper.convertTemperatureToRggb(15000)
                    else -> floatArrayOf()
                }
            cameraViewModel.updateCurrentWhiteBalanceValue(selectedValue)

        }
        */

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
                    10.0f -> changeZoomButtonColor(SelectedZoomButton.TenTimes)
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

        buttonWiderAngle.text = DecimalFormat("#.#")
                .apply { roundingMode = RoundingMode.FLOOR }
                .format(cameraSettingState.cameraMinZoomRatio)
                .plus("x")

        setZoomButtonClickListener(cameraSettingState.cameraMinZoomRatio, SelectedZoomButton.WiderAngle, zoomRatio)
        setZoomButtonClickListener(1.0f, SelectedZoomButton.Default,  zoomRatio)
        setZoomButtonClickListener(2.0f, SelectedZoomButton.TwoTimes,  zoomRatio)
        setZoomButtonClickListener(5.0f, SelectedZoomButton.FiveTimes,  zoomRatio)
        setZoomButtonClickListener(10.0f, SelectedZoomButton.TenTimes,  zoomRatio)

    }

    private fun setZoomButtonClickListener(
        zoomRatioValue: Float,
        selectedButton: SelectedZoomButton,
        zoomRatio: MutableList<Float>
    ) {

        val buttons = mapOf(
            SelectedZoomButton.WiderAngle to buttonWiderAngle,
            SelectedZoomButton.Default to buttonDefaultZoom,
            SelectedZoomButton.TwoTimes to buttonZoomTwoTimes,
            SelectedZoomButton.FiveTimes to buttonZoomFiveTimes,
            SelectedZoomButton.TenTimes to buttonZoomTenTimes
        )

        buttons[selectedButton]?.setOnClickListener {
            changeZoomButtonColor(SelectedZoomButton.None)
            cameraViewModel.camera?.cameraControl?.setZoomRatio(zoomRatioValue)
            zoomSlider.value = zoomRatio.indexOf(zoomRatioValue).toFloat()
            changeZoomButtonColor(selectedButton)
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
            SelectedZoomButton.TenTimes -> buttonZoomFiveTimes.setBackgroundColor(activeColor)
            SelectedZoomButton.None -> {
                buttonWiderAngle.setBackgroundColor(inactiveColor)
                buttonDefaultZoom.setBackgroundColor(inactiveColor)
                buttonZoomTwoTimes.setBackgroundColor(inactiveColor)
                buttonZoomFiveTimes.setBackgroundColor(inactiveColor)
                buttonZoomTenTimes.setBackgroundColor(inactiveColor)
            }
        }

    }

    enum class SelectedZoomButton {
        WiderAngle, Default, TwoTimes, FiveTimes, TenTimes, None
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

    fun setCurrentValueInCameraSettingTextView(cameraSettingState: CameraSettingValueState) {
        autoExposure = cameraSettingState.disabledAutoExposure

        with(cameraSettingState) {

            textViewCurrentIsoValue.text = isoRange?.map { it.toInt() }?.let {
                CameraHelper.findIsoNearestNumber(cameraSettingState.currentIsoValue, it)
            }.toString()

            textViewCurrentShutterValue.text =
                CameraHelper.convertNanoSecondToSecond(currentShutterValue)
                    .let { fraction ->
                        "".plus(fraction.numerator).plus("/").plus(fraction.denominator)
                    }

            textViewCurrentApertureValue.text = "f/".plus(apertureRange?.get(0))

            textViewExposureStatus.text = currentExposureValue.toString()

            textWhiteBalance.text =
                CameraHelper.getWhiteBalanceNames()[cameraCurrentWhiteBalanceMode]
        }

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

    private fun onClickCameraExposureSetting(settingMode: ExposureSettingMode, value: String) {
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

    private fun onSettingClicked(settingMode: ExposureSettingMode) {
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            when (settingMode) {
                ExposureSettingMode.NONE -> Unit
                ExposureSettingMode.ISO -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                ExposureSettingMode.SHUTTER_SPEED -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                ExposureSettingMode.APERTURE -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                ExposureSettingMode.EXPOSURE -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                ExposureSettingMode.AUTO_EXPOSURE -> _action.emit(CameraUiAction.UpdateAutoExposure(!autoExposure))
                ExposureSettingMode.WHITE_BALANCE -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
            }
        }
    }

    /* Setup all the view state of the UI shown in the camera preview. */
    fun updateCameraScreenViewState(state: CameraScreenViewState) {
        updateSwitchLensButtonViewState(state.cameraPreviewScreenViewState)
        updateCameraExposureViewState(state.cameraPreviewScreenViewState)
        setZoomButtonVisibility(state.cameraPreviewScreenViewState)
        setCameraSettingRecyclerViewState(state.cameraPreviewScreenViewState)
        setCameraExposureControlViewState(state.cameraPreviewScreenViewState)
    }

    private fun updateSwitchLensButtonViewState(state: CameraPreviewScreenViewState) {
        lnrSwitchLens.isEnabled = state.switchLensButtonViewState.isEnabled
        lnrSwitchLens.isVisible = state.switchLensButtonViewState.isVisible
    }

    private fun setCameraExposureControlViewState(state: CameraPreviewScreenViewState) {
        lnrIso.isVisible = state.isoButtonViewState.isVisible
        imageViewIso.isEnabled = state.isoButtonViewState.isEnabled

        lnrShutter.isVisible = state.shutterButtonViewState.isVisible
        imageViewShutter.isEnabled = state.shutterButtonViewState.isEnabled

        lnrAperture.isVisible = state.apertureButtonViewState.isVisible
        imageViewAperture.isEnabled = state.apertureButtonViewState.isEnabled

        lnrExposure.isVisible = state.exposureViewState.isVisible
        imageViewExposure.isEnabled = state.exposureViewState.isEnabled

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
        if (state.apertureRange?.size!! > 0) textViewCurrentApertureValue.text =
            state.apertureRange!![0]
        if (state.currentExposureValue != 0.0f) textViewExposureStatus.text =
            state.currentExposureValue.toString()
    }

    private fun setZoomButtonVisibility(state: CameraPreviewScreenViewState) {
        buttonWiderAngle.isVisible = state.widerAngleButtonViewState.isVisible
        buttonDefaultZoom.isVisible = state.defaultButtonViewState.isVisible
        buttonZoomTwoTimes.isVisible = state.twoTimesButtonViewState.isVisible
        buttonZoomFiveTimes.isVisible = state.fiveTimesButtonViewState.isVisible
        buttonZoomTenTimes.isVisible = state.tenTimesButtonViewState.isVisible
    }

    fun setWhiteBalanceSliderVisibility(state: CameraSettingValueState){
        if(state.cameraCurrentWhiteBalanceMode == 0){
            whiteBalanceSlider.visibility = View.VISIBLE
        } else {
            whiteBalanceSlider.visibility = View.GONE
        }

    }

    fun setCameraSwitchInfo(state: CameraUiState) {
        if (state.cameraLens == LENS_FACING_FRONT) {
            textViewLens.text = context.getText(R.string.cameraFront)
        } else if (state.cameraLens == LENS_FACING_BACK) {
            textViewLens.text = context.getText(R.string.cameraBack)
        }
    }

    /* From the ViewState, update the state of the ImageView and TextView of Camera Setting UI */
    private fun updateCameraExposureViewState(state: CameraPreviewScreenViewState) {

        listOf(
            ImageButtonViewState(imageViewExposure, R.drawable.ic_exposure, state.exposureViewState.isEnabled),
            ImageButtonViewState(imageViewIso, R.drawable.ic_camera_iso, state.isoButtonViewState.isEnabled),
            ImageButtonViewState(imageViewShutter, R.drawable.baseline_shutter_speed_24, state.shutterButtonViewState.isEnabled),
            ImageButtonViewState(imageViewAperture, R.drawable.ic_camera_aperture, false)
        ).forEach {
                viewState -> setImageViewDrawable(viewState)
        }

        textViewAutoExposureStatus.text =
            context.getText(if (state.autoExposureViewState.isEnabled) R.string.off else R.string.on)

        listOf(
            TextViewCameraSettingViewState(textViewCurrentIsoValue, state.isoButtonViewState.isEnabled),
            TextViewCameraSettingViewState(textViewCurrentShutterValue, state.shutterButtonViewState.isEnabled),
            TextViewCameraSettingViewState(textViewCurrentApertureValue, state.apertureButtonViewState.isEnabled),
            TextViewCameraSettingViewState(textViewExposureStatus, state.exposureViewState.isEnabled),
        ).forEach { viewState ->
            setTextViewColor(viewState, state.autoExposureViewState.isEnabled)
        }

    }


    private fun setImageViewDrawable(viewState: ImageButtonViewState) {
        val drawable = AppCompatResources.getDrawable(context, viewState.drawableResId)
        if (!viewState.isEnabled) {
            drawable?.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
        }
        viewState.imageView.setImageDrawable(drawable)
    }

    private fun setTextViewColor(viewState: TextViewCameraSettingViewState, autoExposureEnabled: Boolean){
        val inactiveTextColor = ContextCompat.getColor(context, R.color.phyphox_white_50_black_50)
        val activeTextColor = ContextCompat.getColor(context, R.color.phyphox_white_100)

        if(!autoExposureEnabled){
            viewState.textView.setTextColor(inactiveTextColor)
            return
        }

        viewState.textView.setTextColor(if (viewState.isEnabled) activeTextColor else inactiveTextColor)
    }

    /**
     * From the Exposure setting list, mode and current selected value; show list of the
     * exposure value in RecyclerView and react to the click event to update the current selected value.
     * When the Exposure list is empty or with only 1 value, do not create and show RecyclerView.
     * Also, show the white balance list when the white balance button is triggered
    */
    fun populateAndShowCameraSettingValue(
        dataList: List<String>?,
        settingMode: ExposureSettingMode,
        currentValue: String
    ) {
        if (dataList.isNullOrEmpty() || dataList.size == 1) {
            return
        }

        if(settingMode == ExposureSettingMode.WHITE_BALANCE && currentValue == "Manual"){
            whiteBalanceSlider.visibility = View.VISIBLE
        }

        val settingChangeListener = object : SettingChooseListener {
            override fun onSettingClicked(value: String) {
                Log.d(TAG, value)
                onClickCameraExposureSetting(settingMode, value)
            }
        }

        with(recyclerViewExposureSetting) {

            val mLayoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
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
