package de.rwth_aachen.phyphox.camera

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.rwth_aachen.phyphox.Helper.RGB
import de.rwth_aachen.phyphox.PhyphoxExperiment
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.model.CameraSettingLevel
import de.rwth_aachen.phyphox.camera.model.CameraUiAction
import de.rwth_aachen.phyphox.camera.model.ShowCameraControls
import de.rwth_aachen.phyphox.camera.ui.CameraPreviewScreen
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModelFactory
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.Serializable


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
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
        cameraPreviewScreen.setInteractive(interactive)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraPreviewScreen = CameraPreviewScreen(view, experiment?.cameraInput!!, cameraViewModel, toggleExclusive, grayscale, markOverexposure, markUnderexposure)
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

                    else -> {}
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        cameraViewModel.stopCameraPreviewView(cameraPreviewScreen)
    }

}

interface Scrollable: Serializable {
    fun enableScrollable()

    fun disableScrollable()

}
