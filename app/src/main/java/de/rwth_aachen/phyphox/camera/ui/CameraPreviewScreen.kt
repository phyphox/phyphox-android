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
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import androidx.camera.core.CameraSelector.LENS_FACING_FRONT
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.common.util.concurrent.ListenableFuture
import de.rwth_aachen.phyphox.Helper.RGB
import de.rwth_aachen.phyphox.MarkerOverlayView
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.CameraInput
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.helper.SettingChooseListener
import de.rwth_aachen.phyphox.camera.model.CameraSettingMode
import de.rwth_aachen.phyphox.camera.model.CameraSettingState
import de.rwth_aachen.phyphox.camera.model.CameraUiAction
import de.rwth_aachen.phyphox.camera.model.ImageButtonViewState
import de.rwth_aachen.phyphox.camera.model.ShowCameraControls
import de.rwth_aachen.phyphox.camera.model.TextViewCameraSettingViewState
import de.rwth_aachen.phyphox.camera.model.ZoomButtonInfo
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import de.rwth_aachen.phyphox.camera.viewstate.CameraControlElementViewState
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import de.rwth_aachen.phyphox.camera.viewstate.CameraZoomControlViewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.ln
import kotlin.math.pow


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraPreviewScreen(
        private val root: View,
        private val cameraInput: CameraInput,
        private val cameraViewModel: CameraViewModel,
        private val toggleExclusive: () -> Boolean,
        val grayscale: Boolean,
        val markOverexposure: RGB?,
        val markUnderexposure: RGB?
) {

    private val context: Context = root.context

    var visibleToUser: Boolean = true

    val TAG = "CameraPreviewScreen"

    val previewTextureView: TextureView = root.findViewById(R.id.preview_view)
    private val frameLayoutPreviewView: FrameLayout = root.findViewById(R.id.fl_preview_view)
    private val overlayView: MarkerOverlayView = root.findViewById(R.id.cam_overlay_view)

    private val buttonMaximize: ImageView = root.findViewById(R.id.imageMaximize)
    private val buttonMinimize: ImageView = root.findViewById(R.id.imageMinimize)

    private val zoomSlider: Slider = root.findViewById(R.id.zoomSlider)

    private val lnrCameraSetting: LinearLayoutCompat = root.findViewById(R.id.cameraSetting)
    private val lnrZoomControl: LinearLayoutCompat = root.findViewById(R.id.zoomControl)
    private val lnrZoom: LinearLayoutCompat = root.findViewById(R.id.lnrZoom)
    private val lnrSwitchLens = root.findViewById<LinearLayoutCompat>(R.id.lnrSwitchLens)
    private val lnrIso = root.findViewById<LinearLayoutCompat>(R.id.lnrImageIso)
    private val lnrShutter = root.findViewById<LinearLayoutCompat>(R.id.lnrImageShutter)
    private val lnrAperture = root.findViewById<LinearLayoutCompat>(R.id.lnrImageAperture)
    private val lnrAutoExposure = root.findViewById<LinearLayoutCompat>(R.id.lnrImageAutoExposure)
    private val lnrExposure = root.findViewById<LinearLayoutCompat>(R.id.lnrImageExposure)
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
    private val textZoom = root.findViewById<TextView>(R.id.textCurrentZoom)
    private val textWhiteBalance = root.findViewById<TextView>(R.id.textWhiteBalance)

    private val recyclerViewCameraSetting = root.findViewById<RecyclerView>(R.id.recyclerViewCameraSetting)

    private val buttonWiderAngle: MaterialButton = root.findViewById(R.id.buttonWideAngle)
    private val buttonDefaultZoom: MaterialButton = root.findViewById(R.id.buttonDefaultZoom)
    private val buttonZoomTwoTimes: MaterialButton = root.findViewById(R.id.buttonZoomTimesTwo)
    private val buttonZoomFiveTimes: MaterialButton = root.findViewById(R.id.buttonTimesFive)
    private val buttonZoomTenTimes: MaterialButton = root.findViewById(R.id.buttonTimesTen)

    var recyclerViewClicked = false
    var zoomClicked = false

    //animation when button is clicked
    private val buttonClick = AlphaAnimation(1f, 0.4f)

    private var selectedPosition = RecyclerView.NO_POSITION

    private var transformation = Matrix()

    var panningIndexX = 0
    var panningIndexY = 0

    // observable to observe the action performed
    private val _action: MutableSharedFlow<CameraUiAction> = MutableSharedFlow()
    val action: Flow<CameraUiAction> = _action

    private var currentState: CameraScreenViewState? = null

    var resizableState = ResizableViewModuleState.Normal

    init {

        initializeAndSetupCameraDimension()
        setFrameTouchOnListener()

        buttonMaximize.setOnClickListener {
            toggleExclusive()
        }

        buttonMinimize.setOnClickListener {
            toggleExclusive()
        }

        lnrSwitchLens.setOnClickListener {
            animateButton(switchLensButton)
            recyclerViewClicked = false
            zoomClicked = false
            onSettingClicked(CameraSettingMode.SWITCH_LENS)
        }

        lnrIso.setOnClickListener {
            it.startAnimation(buttonClick)
            recyclerViewClicked = !recyclerViewClicked
            zoomClicked = false
            onSettingClicked(CameraSettingMode.ISO)
        }

        lnrShutter.setOnClickListener {
            it.startAnimation(buttonClick)
            recyclerViewClicked = !recyclerViewClicked
            zoomClicked = false
            onSettingClicked(CameraSettingMode.SHUTTER_SPEED)
        }

        lnrAperture.setOnClickListener {
            it.startAnimation(buttonClick)
            recyclerViewClicked = !recyclerViewClicked
            zoomClicked = false
            onSettingClicked(CameraSettingMode.APERTURE)
        }

        lnrExposure.setOnClickListener {
            it.startAnimation(buttonClick)
            recyclerViewClicked = !recyclerViewClicked
            zoomClicked = false
            onSettingClicked(CameraSettingMode.EXPOSURE)
        }

        lnrAutoExposure.setOnClickListener {
            it.startAnimation(buttonClick)
            recyclerViewClicked = false
            zoomClicked = false
            onSettingClicked(CameraSettingMode.AUTO_EXPOSURE)
        }

        lnrZoom.setOnClickListener {
            it.startAnimation(buttonClick)
            recyclerViewClicked = false
            zoomClicked = !zoomClicked
            onSettingClicked(CameraSettingMode.ZOOM)
        }

        lnrWhiteBalance.setOnClickListener {
            it.startAnimation(buttonClick)
            recyclerViewClicked = !recyclerViewClicked
            zoomClicked = false
            onSettingClicked(CameraSettingMode.WHITE_BALANCE)
        }
    }

    fun setInteractive(interactive: Boolean) {
        if (interactive) {
            buttonMinimize.visibility = View.VISIBLE
            buttonMaximize.visibility = View.GONE
            resizableState = ResizableViewModuleState.Exclusive
            initializeAndSetupCameraDimension()
        } else {
            buttonMinimize.visibility = View.GONE
            buttonMaximize.visibility = View.VISIBLE
            resizableState = ResizableViewModuleState.Normal
            initializeAndSetupCameraDimension()
        }
        cameraViewModel.requestUpdate()
    }

    enum class ResizableViewModuleState {
        Normal, Exclusive, Hidden
    }

    fun getCameraSettingsVisibility(): Boolean {
        return when (cameraViewModel.cameraUiState.value.showCameraControls) {
            ShowCameraControls.FullViewOnly -> {
                when (resizableState) {
                    ResizableViewModuleState.Normal -> return false
                    ResizableViewModuleState.Exclusive -> return true
                    else -> false
                }
            }
            ShowCameraControls.Always -> true
            ShowCameraControls.Never -> false
        }
    }

    private fun positionToZoomValue(pos: Float): Float {
        val min = zoomSlider.valueFrom.toDouble()
        val max = zoomSlider.valueTo.toDouble()
        val zoom = Math.round(100.0 * min * (max / min).pow(((pos-min)/(max-min)))) / 100.0
        return Math.min(Math.max(zoom, min), max).toFloat()
    }

    private fun zoomValueToPosition(v: Float): Float {
        val min = zoomSlider.valueFrom.toDouble()
        val max = zoomSlider.valueTo.toDouble()
        val position = min + (max-min)*ln(v / min) / ln(max / min)
        return Math.min(Math.max(position, min), max).toFloat()
    }

    fun setupZoomControl(cameraSettingState: CameraSettingState) {

        val zoomButtons = listOf(
            ZoomButtonInfo(zoomSlider.valueFrom, SelectedZoomButton.WiderAngle),
            ZoomButtonInfo(1.0f, SelectedZoomButton.Default),
            ZoomButtonInfo(2.0f, SelectedZoomButton.TwoTimes),
            ZoomButtonInfo(5.0f, SelectedZoomButton.FiveTimes),
            ZoomButtonInfo(10.0f, SelectedZoomButton.TenTimes)
        )

        zoomSlider.valueFrom = cameraSettingState.cameraMinZoomRatio
        zoomSlider.valueTo = cameraSettingState.cameraMaxZoomRatio
        zoomSlider.stepSize = 0.0f
        zoomSlider.value = zoomValueToPosition(1.0f)

        changeZoomButtonColor(SelectedZoomButton.Default)

        zoomSlider.addOnChangeListener { _, value, _ ->

            val mappedValue = positionToZoomValue(value)
            val listenableZoomRatio: ListenableFuture<Void>? = cameraInput.camera?.cameraControl?.setZoomRatio(mappedValue)
            val selectedButton = zoomButtons.firstOrNull { it.zoomValue == mappedValue }?.button ?: SelectedZoomButton.None
            changeZoomButtonColor(selectedButton)
            listenableZoomRatio?.cancel(true) // fix for zoom lagging
        }

        zoomSlider.setLabelFormatter { value: Float ->
            val mappedValue = positionToZoomValue(value)
            "${mappedValue}x"
        }

        buttonWiderAngle.visibility = if (zoomSlider.valueFrom < 1.0) View.VISIBLE else View.GONE

        buttonWiderAngle.text = DecimalFormat("#.#")
                .apply { roundingMode = RoundingMode.FLOOR }
                .format(cameraSettingState.cameraMinZoomRatio)
                .plus("x")

        zoomButtons.forEach { (zoomValue, button) ->
            setZoomButtonClickListener(zoomValue, button)
        }
    }

    private fun setZoomButtonClickListener(
        zoomRatioValue: Float,
        selectedButton: SelectedZoomButton
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
            cameraInput.camera?.cameraControl?.setZoomRatio(zoomRatioValue)
            zoomSlider.value = zoomValueToPosition(zoomRatioValue)
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
        previewTextureView.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    _action.emit(CameraUiAction.UpdateOverlay)
                }
                previewTextureView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    fun setCurrentValueInCameraSettingTextView(cameraSettingState: CameraSettingState) {
        with(cameraSettingState) {

            if (cameraSettingState.currentLens == LENS_FACING_FRONT) {
                textViewLens.text = context.getText(R.string.cameraFront)
            } else if (cameraSettingState.currentLens == LENS_FACING_BACK) {
                textViewLens.text = context.getText(R.string.cameraBack)
            }

            textViewAutoExposureStatus.text =  if (cameraSettingState.autoExposure) "On" else "Off"

            textViewCurrentIsoValue.text = isoRange?.map { it.toInt() }?.let {
                CameraHelper.findIsoNearestNumber(cameraSettingState.currentIsoValue, it)
            }.toString()

            textViewCurrentShutterValue.text =
                CameraHelper.convertNanoSecondToSecond(currentShutterValue)
                    .let { fraction ->
                        "".plus(fraction.numerator).plus("/").plus(fraction.denominator)
                    }

            textViewCurrentApertureValue.text = "f/".plus(currentApertureValue)

            textViewExposureStatus.text = currentExposureValue.toString().plus("EV")

            textWhiteBalance.text =
                context.getString(CameraHelper.getWhiteBalanceModes()[cameraCurrentWhiteBalanceMode] ?: R.string.wb_auto)
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    fun setFrameTouchOnListener() {
        overlayView.setOnTouchListener { v, event ->

            if (resizableState == ResizableViewModuleState.Normal) return@setOnTouchListener false

            val touch = floatArrayOf(event.x, event.y)
            val invert = Matrix()
            transformation.invert(invert)
            invert.mapPoints(touch)
            val x: Float = touch[1] / frameLayoutPreviewView.getHeight()
            val y: Float = 1.0f - touch[0] / frameLayoutPreviewView.getWidth()

            var passepartout = RectF(cameraInput.cameraSettingState.value.cameraPassepartout)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    /*Scrollable is disabled while updating overlay because the drag is interrupted by scroll event*/
                    cameraViewModel.scrollable.disableScrollable()

                    val d11: Float =
                        (x - passepartout.left) * (x - passepartout.left) + (y - passepartout.top) * (y - passepartout.top)
                    val d12: Float =
                        (x - passepartout.left) * (x - passepartout.left) + (y - passepartout.bottom) * (y - passepartout.bottom)
                    val d21: Float =
                        (x - passepartout.right) * (x - passepartout.right) + (y - passepartout.top) * (y - passepartout.top)
                    val d22: Float =
                        (x - passepartout.right) * (x - passepartout.right) + (y - passepartout.bottom) * (y - passepartout.bottom)

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

                    if (panningIndexX == 1) passepartout.left = x
                    else if (panningIndexX == 2) passepartout.right = x

                    if (panningIndexY == 1) passepartout.top = y
                    else if (panningIndexY == 2) passepartout.bottom = y

                    cameraViewModel.setPassepartout(passepartout)
                }

                MotionEvent.ACTION_UP -> {
                    cameraViewModel.scrollable.enableScrollable()
                }
            }
            true
        }
    }

    public fun updateTransformation(outWidth: Int, outHeight: Int) {
        cameraInput.analyzingOpenGLRenderer?.let {
            val w: Int = it.previewWidth
            val h: Int = it.previewHeight
            if (w == 0 || h == 0) return
            val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
            transformation = Matrix()
            val landscape = Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation
            val targetAspect = if (landscape) w.toFloat() / h.toFloat() else h.toFloat() / w.toFloat() //Careful: We calculate relative to a portrait orientation, so h and w of the camera start flipped
            val sx: Float
            val sy: Float
            if (outWidth.toFloat() / outHeight.toFloat() > targetAspect) {
                sx = outHeight * targetAspect / outWidth.toFloat()
                sy = 1f
            } else {
                sx = 1f
                sy = outWidth / targetAspect / outHeight.toFloat()
            }
            if (Surface.ROTATION_90 == rotation) {
                transformation.postScale(sx / targetAspect, sy * targetAspect)
                transformation.postRotate(-90f)
                transformation.postTranslate(0.5f * (1f - sx) * outWidth, 0.5f * (1f + sy) * outHeight)
            } else if (Surface.ROTATION_180 == rotation) {
                transformation.postRotate(180f)
                transformation.postScale(sx, sy)
                transformation.postTranslate(0.5f * (1f + sx) * outWidth, 0.5f * (1f + sy) * outHeight)
            } else if (Surface.ROTATION_270 == rotation) {
                transformation.postScale(sx / targetAspect, sy * targetAspect)
                transformation.postRotate(90f)
                transformation.postTranslate(0.5f * (1f + sx) * outWidth, 0.5f * (1f - sy) * outHeight)
            } else {
                transformation.postScale(sx, sy)
                transformation.postTranslate(0.5f * (1f - sx) * outWidth, 0.5f * (1f - sy) * outHeight)
            }
            previewTextureView.setTransform(transformation)
            updateOverlay()
        }
    }

    public fun updateOverlay() {
        val passepartout = cameraInput.cameraSettingState.value.cameraPassepartout

        val xmin: Float = Math.min(passepartout.left, passepartout.right)
        val xmax: Float = Math.max(passepartout.left, passepartout.right)
        val ymin: Float = Math.min(passepartout.top, passepartout.bottom)
        val ymax: Float = Math.max(passepartout.top, passepartout.bottom)
        val inner = RectF((1.0f - ymax) * frameLayoutPreviewView.getWidth(), xmin * frameLayoutPreviewView.getHeight(), (1.0f - ymin) * frameLayoutPreviewView.getWidth(), xmax * frameLayoutPreviewView.getHeight())
        transformation.mapRect(inner)
        val points: Array<Point?>?
        if (resizableState == ResizableViewModuleState.Exclusive) {
            points = arrayOfNulls(4)
            points[0] = Point(Math.round(inner.left), Math.round(inner.top))
            points[1] = Point(Math.round(inner.right), Math.round(inner.top))
            points[2] = Point(Math.round(inner.left), Math.round(inner.bottom))
            points[3] = Point(Math.round(inner.right), Math.round(inner.bottom))
        } else points = null
        overlayView.setClipRect(null)
        overlayView.update(null, points)
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            _action.emit(CameraUiAction.OverlayUpdateDone)
        }
    }

    private fun onClickCameraExposureSetting(settingMode: CameraSettingMode, value: ChooseCameraSettingValue) {
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            _action.emit(CameraUiAction.UpdateCameraExposureSettingValue(settingMode, value))
        }
    }

    private fun animateButton(switchLensButton: View) {

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

    private fun onSettingClicked(settingMode: CameraSettingMode) {
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            when (settingMode) {
                CameraSettingMode.NONE -> Unit
                CameraSettingMode.ISO -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                CameraSettingMode.SHUTTER_SPEED -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                CameraSettingMode.APERTURE -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                CameraSettingMode.EXPOSURE -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                CameraSettingMode.AUTO_EXPOSURE -> _action.emit(CameraUiAction.UpdateAutoExposure(!cameraInput.cameraSettingState.value.autoExposure))
                CameraSettingMode.WHITE_BALANCE -> _action.emit(CameraUiAction.CameraSettingClick(settingMode))
                CameraSettingMode.ZOOM -> _action.emit(CameraUiAction.ZoomClicked)
                CameraSettingMode.SWITCH_LENS -> _action.emit(CameraUiAction.SwitchCameraClick)
            }
        }
    }

    /* Setup all the view state of the UI shown in the camera preview. */
    fun updateCameraScreenViewState(newState: CameraScreenViewState) {
        var forceAll = (currentState == null)
        if (forceAll) {
            currentState = CameraScreenViewState()
        }
        val oldState = currentState!!

        if (forceAll || (newState.isVisible != oldState.isVisible)) {
            lnrCameraSetting.isVisible = newState.isVisible
            if (newState.isVisible) {
                forceAll = true
            } else {
                recyclerViewCameraSetting.isVisible = false
                lnrZoomControl.isVisible = false
            }
            currentState = currentState!!.copy(isVisible = newState.isVisible)
        }

        if (!forceAll && !newState.isVisible) {
            return //No need to update anything as long as it isn't visible anyways
        }

        if (forceAll || newState.mainControls.switchLensButton != oldState.mainControls.switchLensButton) {
            updateMainButton(lnrSwitchLens, newState.mainControls.switchLensButton, switchLensButton, textViewLens, R.drawable.ic_flip_camera_android)
            currentState = currentState!!.copy(mainControls = currentState!!.mainControls.copy(switchLensButton = newState.mainControls.switchLensButton.copy()))
        }

        if (forceAll || newState.mainControls.autoExposureButton != oldState.mainControls.autoExposureButton) {
            updateMainButton(lnrAutoExposure, newState.mainControls.autoExposureButton, imageViewAutoExposure, textViewAutoExposureStatus, R.drawable.ic_auto_exposure)
            currentState = currentState!!.copy(mainControls = currentState!!.mainControls.copy(autoExposureButton = newState.mainControls.autoExposureButton.copy()))
        }

        if (forceAll || newState.mainControls.exposureButton != oldState.mainControls.exposureButton) {
            updateMainButton(lnrExposure, newState.mainControls.exposureButton, imageViewExposure, textViewExposureStatus, R.drawable.ic_exposure)
            currentState = currentState!!.copy(mainControls = currentState!!.mainControls.copy(exposureButton = newState.mainControls.exposureButton.copy()))
        }

        if (forceAll || newState.mainControls.isoButton != oldState.mainControls.isoButton) {
            updateMainButton(lnrIso, newState.mainControls.isoButton, imageViewIso, textViewCurrentIsoValue, R.drawable.ic_camera_iso)
            currentState = currentState!!.copy(mainControls = currentState!!.mainControls.copy(isoButton = newState.mainControls.isoButton.copy()))
        }

        if (forceAll || newState.mainControls.shutterButton != oldState.mainControls.shutterButton) {
            updateMainButton(lnrShutter, newState.mainControls.shutterButton, imageViewShutter, textViewCurrentShutterValue, R.drawable.baseline_shutter_speed_24)
            currentState = currentState!!.copy(mainControls = currentState!!.mainControls.copy(shutterButton = newState.mainControls.shutterButton.copy()))
        }

        if (forceAll || newState.mainControls.apertureButton != oldState.mainControls.apertureButton) {
            updateMainButton(lnrAperture, newState.mainControls.apertureButton, imageViewAperture, textViewCurrentApertureValue, R.drawable.ic_camera_aperture)
            currentState = currentState!!.copy(mainControls = currentState!!.mainControls.copy(apertureButton = newState.mainControls.apertureButton.copy()))
        }

        if (forceAll || newState.mainControls.zoomButton != oldState.mainControls.zoomButton) {
            updateMainButton(lnrZoom, newState.mainControls.zoomButton, imageZoom, textZoom, R.drawable.ic_zoom)
            currentState = currentState!!.copy(mainControls = currentState!!.mainControls.copy(zoomButton = newState.mainControls.zoomButton.copy()))
        }

        if (forceAll || newState.mainControls.whiteBalanceButton != oldState.mainControls.whiteBalanceButton) {
            updateMainButton(lnrWhiteBalance, newState.mainControls.whiteBalanceButton, imageWhiteBalance, textWhiteBalance, R.drawable.ic_white_balance)
            currentState = currentState!!.copy(mainControls = currentState!!.mainControls.copy(whiteBalanceButton = newState.mainControls.whiteBalanceButton.copy()))
        }

        if (forceAll || (newState.subControls.recyclerViewVisible != oldState.subControls.recyclerViewVisible)) {
            recyclerViewCameraSetting.isVisible = newState.subControls.recyclerViewVisible
            currentState = currentState!!.copy(subControls = currentState!!.subControls.copy(recyclerViewVisible = newState.subControls.recyclerViewVisible))
        }

        if (forceAll || newState.subControls.zoomControls != oldState.subControls.zoomControls) {
            lnrZoomControl.isVisible = newState.subControls.zoomControls.isVisible
            setZoomButtonVisibility(newState.subControls.zoomControls)
            currentState = currentState!!.copy(subControls = currentState!!.subControls.copy(zoomControls = newState.subControls.zoomControls.copy()))
        }

        if (forceAll || newState.subControls.apertureSlider != oldState.subControls.apertureSlider) {
            if (newState.subControls.apertureSlider.isVisible) {
                loadRecyclerViewContent(CameraSettingMode.APERTURE)
            }
            currentState = currentState!!.copy(subControls = currentState!!.subControls.copy(apertureSlider = newState.subControls.apertureSlider.copy()))
        }

        if (forceAll || newState.subControls.exposureSlider != oldState.subControls.exposureSlider) {
            if (newState.subControls.exposureSlider.isVisible) {
                loadRecyclerViewContent(CameraSettingMode.EXPOSURE)
            }
            currentState = currentState!!.copy(subControls = currentState!!.subControls.copy(exposureSlider = newState.subControls.exposureSlider.copy()))
        }

        if (forceAll || newState.subControls.isoSlider != oldState.subControls.isoSlider) {
            if (newState.subControls.isoSlider.isVisible) {
                loadRecyclerViewContent(CameraSettingMode.ISO)
            }
            currentState = currentState!!.copy(subControls = currentState!!.subControls.copy(isoSlider = newState.subControls.isoSlider.copy()))
        }

        if (forceAll || newState.subControls.shutterSpeedSlider != oldState.subControls.shutterSpeedSlider) {
            if (newState.subControls.shutterSpeedSlider.isVisible) {
                loadRecyclerViewContent(CameraSettingMode.SHUTTER_SPEED)
            }
            currentState = currentState!!.copy(subControls = currentState!!.subControls.copy(shutterSpeedSlider = newState.subControls.shutterSpeedSlider.copy()))
        }

        if (forceAll || newState.subControls.whiteBalanceControl != oldState.subControls.whiteBalanceControl) {
            if (newState.subControls.whiteBalanceControl.isVisible) {
                loadRecyclerViewContent(CameraSettingMode.WHITE_BALANCE)
            }
            currentState = currentState!!.copy(subControls = currentState!!.subControls.copy(whiteBalanceControl = newState.subControls.whiteBalanceControl.copy()))
        }

    }
    fun updateMainButton(lnr: LinearLayoutCompat, state: CameraControlElementViewState, imageView: ImageView, textView: TextView, drawable: Int) {
        lnr.isVisible = state.isVisible
        lnr.isEnabled = state.isEnabled
        imageView.isEnabled = state.isEnabled
        setImageViewDrawable(ImageButtonViewState(imageView, drawable, state.isEnabled))
        setTextViewColor(TextViewCameraSettingViewState(textView, state.isEnabled))
    }

    fun loadRecyclerViewContent(mode: CameraSettingMode) {
        val currentValue = when (mode) {
            CameraSettingMode.ISO -> cameraInput.cameraSettingState.value.isoRange?.map { it.toInt() }
                ?.let { isoRange ->
                    CameraHelper.findIsoNearestNumber(
                        cameraInput.cameraSettingState.value.currentIsoValue,
                        isoRange
                    )
                }?.toDouble()

            CameraSettingMode.SHUTTER_SPEED -> {
                cameraInput.cameraSettingState.value.currentShutterValue.toDouble()
            }

            CameraSettingMode.APERTURE -> cameraInput.cameraSettingState.value.currentApertureValue.toDouble()

            CameraSettingMode.EXPOSURE -> cameraInput.cameraSettingState.value.currentExposureValue.toDouble()

            CameraSettingMode.WHITE_BALANCE ->
                cameraInput.cameraSettingState.value.cameraCurrentWhiteBalanceMode.toDouble()

            else -> 0.0
        }

        val exposureSettingRange = when (mode) {
            CameraSettingMode.ISO -> cameraInput.cameraSettingState.value.isoRange?.map {
                ChooseCameraSettingValue(
                    label = it.toString(),
                    value = it.toDouble()
                )
            }
            CameraSettingMode.SHUTTER_SPEED -> cameraInput.cameraSettingState.value.shutterSpeedRange?.map {
                ChooseCameraSettingValue(
                    label = "${it.numerator}/${it.denominator}",
                    value = (1_000_000_000 * it.numerator / it.denominator).toDouble()
                )
            }
            CameraSettingMode.APERTURE -> cameraInput.cameraSettingState.value.apertureRange?.map {
                ChooseCameraSettingValue(
                    label = "f/$it",
                    value = it.toDouble()
                )
            }
            CameraSettingMode.EXPOSURE -> cameraInput.cameraSettingState.value.exposureRange?.map {
                ChooseCameraSettingValue(
                    label = "${if (it > 0) "+" else ""}${it}EV",
                    value = it.toDouble()
                )
            }
            CameraSettingMode.WHITE_BALANCE ->
                CameraHelper.getWhiteBalanceModes().map {
                    ChooseCameraSettingValue(
                        label = context.getString(it.value),
                        value = it.key.toDouble()
                    )
                }
            else -> emptyList()
        }

        populateAndShowCameraSettingValueIntoRecyclerView(
                exposureSettingRange,
                mode,
                currentValue
        )
    }

    private fun setZoomButtonVisibility(state: CameraZoomControlViewState) {
        buttonWiderAngle.isVisible = state.widerAngleButtonViewState.isVisible
        buttonDefaultZoom.isVisible = state.defaultButtonViewState.isVisible
        buttonZoomTwoTimes.isVisible = state.twoTimesButtonViewState.isVisible
        buttonZoomFiveTimes.isVisible = state.fiveTimesButtonViewState.isVisible
        buttonZoomTenTimes.isVisible = state.tenTimesButtonViewState.isVisible
    }

    /* From the ViewState, update the state of the ImageView and TextView of Camera Setting UI */

    private fun setImageViewDrawable(viewState: ImageButtonViewState) {
        val drawable = AppCompatResources.getDrawable(context, viewState.drawableResId)
        if (!viewState.isEnabled) {
            drawable?.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
        }
        viewState.imageView.setImageDrawable(drawable)
    }

    private fun setTextViewColor(viewState: TextViewCameraSettingViewState){
        val inactiveTextColor = ContextCompat.getColor(context, R.color.phyphox_white_50_black_50)
        val activeTextColor = ContextCompat.getColor(context, R.color.phyphox_white_100)

        viewState.textView.setTextColor(if (viewState.isEnabled) activeTextColor else inactiveTextColor)
    }

    /**
     * Camera Setting includes: ISO, ShutterSpeed, Exposure and White Balance
    */
    fun populateAndShowCameraSettingValueIntoRecyclerView(
        dataList: List<ChooseCameraSettingValue>?,
        settingMode: CameraSettingMode,
        currentValue: Double?
    ) {
        if (dataList.isNullOrEmpty() || dataList.size == 1) {
            return
        }

        val settingChangeListener = object : SettingChooseListener {
            override fun onSettingClicked(value: ChooseCameraSettingValue?) {
                value?.let {
                    onClickCameraExposureSetting(settingMode, it)
                }
            }
        }

        with(recyclerViewCameraSetting) {

            val mLayoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            layoutManager = mLayoutManager
            itemAnimator = DefaultItemAnimator()

            recyclerViewCameraSetting.adapter =
                ChooseCameraSettingValueAdapter(dataList, settingChangeListener, currentValue)

            selectedPosition = dataList.indexOfFirst { it.value == currentValue }
            recyclerViewCameraSetting.postDelayed({
                recyclerViewCameraSetting.scrollToPosition(selectedPosition) }, 100)
        }

        recyclerLoadingFinished()

    }

    fun recyclerLoadingFinished() {
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            _action.emit(CameraUiAction.CameraSettingValueSelected)
        }
    }

}
