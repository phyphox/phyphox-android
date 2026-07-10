package de.rwth_aachen.phyphox.camera

import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import de.rwth_aachen.phyphox.PhyphoxExperiment
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.model.CameraSettingLevel
import de.rwth_aachen.phyphox.camera.model.CameraUiAction
import de.rwth_aachen.phyphox.camera.model.ShowCameraControls
import de.rwth_aachen.phyphox.camera.ui.CameraPreviewScreen
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModelFactory
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import de.rwth_aachen.phyphox.helper.RGB
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.Serializable


class CameraPreviewFragment (
        private val experiment: PhyphoxExperiment,
        private val scrollable: Scrollable,
        private val toggleExclusive: () -> Boolean,
        private val showCameraControls: ShowCameraControls,
        private val cameraSettingsLevel: CameraSettingLevel,
        private val grayscale: Boolean,
        private val markOverexposure: RGB?,
        private val markUnderexposure: RGB?
) : Fragment() {
    val TAG = "CameraPreviewFragment"

    private var isCurrentlyLandscape: Boolean? = null
    private var isInteractive: Boolean = false

    /* view model to setup and update camera */
    private lateinit var cameraViewModel: CameraViewModel

    /* handles all the UI elements for camera preview */
    private lateinit var cameraPreviewScreen: CameraPreviewScreen

    /* tracks the current view state */
    private val cameraScreenViewState = MutableStateFlow(CameraScreenViewState())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        cameraViewModel =
            ViewModelProvider(
                this,
                CameraViewModelFactory()
            )[CameraViewModel::class.java]


        cameraViewModel.cameraInput = experiment?.cameraInput!!
        cameraViewModel.scrollable = scrollable
        cameraViewModel.setControlSettings(showCameraControls, cameraSettingsLevel)

        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    public fun setInteractive(interactive: Boolean) {
        isInteractive = interactive
        cameraPreviewScreen.setInteractive(interactive)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rootLayout = view as? ConstraintLayout ?: return
        rootLayout.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top

            if (width == 0 || height == 0)
                return@addOnLayoutChangeListener

            val isLandscape = width > height

            if (isCurrentlyLandscape != isLandscape) {
                isCurrentlyLandscape = isLandscape
                applyNewConstraints(rootLayout, isLandscape)
            }
        }


        cameraPreviewScreen = CameraPreviewScreen(view, experiment.cameraInput!!, cameraViewModel, toggleExclusive, grayscale, markOverexposure, markUnderexposure)
        cameraViewModel.start(cameraScreenViewState, cameraPreviewScreen)

        lifecycleScope.launch {
            cameraScreenViewState.collectLatest {
                cameraPreviewScreen.updateCameraScreenViewState(it)
            }
        }

        lifecycleScope.launch {
            cameraPreviewScreen.action.collectLatest { action ->
                when (action) {
                    is CameraUiAction.SwitchCameraClick -> cameraViewModel.switchCamera()

                    is CameraUiAction.ZoomClicked -> {
                        if(cameraPreviewScreen.zoomClicked){
                            cameraViewModel.showZoomController()
                        } else {
                            cameraViewModel.hideAllController()
                        }
                    }

                    is CameraUiAction.CameraSettingClick ->
                        cameraViewModel.openCameraSettingValue(action.settingMode)

                    is CameraUiAction.UpdateCameraExposureSettingValue ->
                        cameraViewModel.updateCameraSettingValue(action.value, action.settingMode)

                    is CameraUiAction.UpdateAutoExposure ->
                        cameraViewModel.changeAutoExposure(action.autoExposure)

                    is CameraUiAction.CameraSettingValueSelected ->
                        cameraViewModel.cameraSettingOpened()

                    is CameraUiAction.UpdateOverlay ->
                        cameraViewModel.updateCameraOverlay()

                    is CameraUiAction.OverlayUpdateDone ->
                        cameraViewModel.overlayUpdated()

                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cameraPreviewScreen.previewTextureView.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        cameraViewModel.stopCameraPreviewView(cameraPreviewScreen)
        cameraPreviewScreen.previewTextureView.visibility = View.GONE
    }

    fun onPageVisibleToUser(visible: Boolean) {
        cameraPreviewScreen.visibleToUser = visible
    }

    private fun applyNewConstraints(rootLayout: ConstraintLayout, isLandscape: Boolean) {
        val margin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8.0f,
            resources.getDisplayMetrics()
        ).toInt()

        val linearLayout = rootLayout.findViewById<LinearLayoutCompat>(R.id.cameraSetting)
        val params = linearLayout.layoutParams as ConstraintLayout.LayoutParams
        if (isLandscape) {
            linearLayout?.orientation = LinearLayoutCompat.VERTICAL
            linearLayout?.gravity = Gravity.CENTER_VERTICAL + Gravity.LEFT
            params.width = ConstraintLayout.LayoutParams.WRAP_CONTENT
            params.height = 0
            params.topMargin = margin
            params.leftMargin = 0

        } else {
            linearLayout?.orientation = LinearLayoutCompat.HORIZONTAL
            linearLayout?.gravity = Gravity.CENTER_HORIZONTAL + Gravity.TOP
            params.width = 0
            params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
            params.topMargin = 0
            params.leftMargin = margin
        }
        for (child in linearLayout.children) {
            (child as LinearLayoutCompat).let {
                if (isLandscape) {
                    child.orientation = LinearLayoutCompat.HORIZONTAL
                    child.gravity = Gravity.CENTER_VERTICAL + Gravity.LEFT
                } else {
                    child.orientation = LinearLayoutCompat.VERTICAL
                    child.gravity = Gravity.CENTER_HORIZONTAL + Gravity.TOP
                }
            }
        }

        val constraintSet = ConstraintSet()
        val targetLayoutId = if (isLandscape) {
            R.layout.fragment_camera_landscape
        } else {
            R.layout.fragment_camera
        }

        constraintSet.clone(requireContext(), targetLayoutId)
        constraintSet.applyTo(rootLayout)
        setInteractive(isInteractive)
        lifecycleScope.launch {
            cameraPreviewScreen.updateCameraScreenViewState(cameraScreenViewState.value, true)
        }
    }

}

interface Scrollable: Serializable {
    fun enableScrollable()

    fun disableScrollable()

}
