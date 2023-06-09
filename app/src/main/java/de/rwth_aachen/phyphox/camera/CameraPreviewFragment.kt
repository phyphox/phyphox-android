package de.rwth_aachen.phyphox.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.rwth_aachen.phyphox.PhyphoxExperiment
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.model.CameraSettingState
import de.rwth_aachen.phyphox.camera.model.CameraState
import de.rwth_aachen.phyphox.camera.model.CameraUiAction
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisState
import de.rwth_aachen.phyphox.camera.model.PermissionState
import de.rwth_aachen.phyphox.camera.ui.CameraPreviewScreen
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModelFactory
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraPreviewFragment : Fragment() {
    val TAG = "CameraPreviewFragment"

    // view model for operating on the camera and analysis of an image
    private lateinit var cameraViewModel: CameraViewModel

    // monitors changes in camera permission state
    private lateinit var permissionState: MutableStateFlow<PermissionState>

    // handles all the UI elements for camera preview
    private lateinit var cameraPreviewScreen: CameraPreviewScreen

    //tracks the current view state
    private val cameraScreenViewState = MutableStateFlow(CameraScreenViewState())

    //holds experiment info/object
    private var experiment: PhyphoxExperiment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        cameraViewModel =
            ViewModelProvider(
                this,
                CameraViewModelFactory(application = requireActivity().application)
            )[CameraViewModel::class.java]

        // Retrieve the object from the arguments
        val args = arguments
        if (args != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                experiment = args.getSerializable(CameraHelper.EXPERIMENT_ARG, PhyphoxExperiment::class.java)
            } else {
                experiment = args.getSerializable(CameraHelper.EXPERIMENT_ARG) as PhyphoxExperiment?
            }
            cameraViewModel.cameraInput = experiment?.cameraInput!!
            cameraViewModel.phyphoxExperiment = experiment!!
        }

        cameraViewModel.initializeCameraSettingValue()

        // initialize the permission state flow with the current camera permission status
        permissionState = MutableStateFlow(getCurrentPermissionState())

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // check the current permission state every time upon the fragment is resumed
                permissionState.emit(getCurrentPermissionState())
            }
        }
        return inflater.inflate(R.layout.fragment_camera, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraPreviewScreen = CameraPreviewScreen(view, experiment?.cameraInput!!)

        lifecycleScope.launch {
            cameraScreenViewState.collectLatest {
                cameraPreviewScreen.setCameraScreenViewState(state = it)
            }
        }

        val requestPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    lifecycleScope.launch { permissionState.emit(PermissionState.Granted) }
                } else {
                    lifecycleScope.launch { permissionState.emit(PermissionState.Denied(true)) }
                }
            }

        lifecycleScope.launch {
            cameraPreviewScreen.action.collectLatest { action ->
                when (action) {
                    is CameraUiAction.SwitchCameraClick -> cameraViewModel.switchCamera()

                    is CameraUiAction.RequestPermissionClick -> {
                        requestPermissionsLauncher.launch(Manifest.permission.CAMERA)

                    }
                    is CameraUiAction.SelectAndChangeCameraSetting -> Unit
                    is CameraUiAction.LoadCameraSettings -> Unit
                    is CameraUiAction.ReloadCameraSettings -> {
                        cameraViewModel.reloadCameraWithNewSetting(
                            action.settingMode,
                            action.currentValue
                        )
                    }
                    is CameraUiAction.ChangeCameraSettingValue -> {
                        Log.d(TAG, "ChangeCameraSettingValue")
                        cameraViewModel.updateCameraSettingValue(action.value, action.settingMode)
                    }
                    is CameraUiAction.ChangeAutoExposure -> {
                        cameraViewModel.changeExposure(action.autoExposure)
                    }
                }
            }
        }

        lifecycleScope.launch {
            cameraViewModel.imageAnalysisUiState.collectLatest { state ->
                when (state) {
                    ImageAnalysisState.ImageAnalysisNotReady -> {}
                    ImageAnalysisState.ImageAnalysisReady -> {}
                    ImageAnalysisState.ImageAnalysisStarted -> {}
                    ImageAnalysisState.ImageAnalysisFinished -> {}
                    ImageAnalysisState.ImageAnalysisFailed(Exception("")) -> {
                    }

                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            permissionState.collectLatest { permissionState ->
                when (permissionState) {
                    PermissionState.Granted -> {
                        cameraPreviewScreen.hidePermissionsRequest()
                    }

                    // TODO manage the permission
                    is PermissionState.Denied -> {
                        //if (cameraViewModel.cameraUiState != CameraState.PREVIEW_STOPPED) {
                         //   cameraPreviewScreen.showPermissionsRequest(permissionState.shouldShowRational)
                         //   return@collectLatest
                        //}
                    }
                }
            }
        }


        lifecycleScope.launch {
            cameraViewModel.cameraUiState.combine(cameraViewModel.cameraSettingValueState) { cameraUiState, cameraSettingState ->
                Pair(cameraUiState, cameraSettingState)
            }.collectLatest { (cameraUiState, cameraSettingState) ->

                when (cameraUiState.cameraState) {
                    CameraState.NOT_READY -> {
                        Log.d(TAG, " CameraState.NOT_READY")
                        cameraScreenViewState.emit(
                            cameraScreenViewState.value
                                .updateCameraScreen {
                                    it.showCameraControls()
                                        .enableSwitchLens(false)
                                        .showCameraControls()
                                }
                        )
                        cameraViewModel.initializeCamera()
                        //cameraPreviewScreen.slidersViewSetup(experiment)
                    }

                    CameraState.READY -> {
                        Log.d(TAG, " CameraState.READY")
                        cameraPreviewScreen.previewView.doOnLayout {
                            cameraPreviewScreen.setUpOverlay()
                            cameraViewModel.startCameraPreviewView(
                                cameraPreviewScreen.previewView,
                                lifecycleOwner = this@CameraPreviewFragment as LifecycleOwner,
                                cameraUiState.autoExposure
                            )
                        }
                        cameraScreenViewState.emit(
                            cameraScreenViewState.value
                                .updateCameraScreen { s ->
                                    Log.d(TAG, " UpdateCameraScreen")
                                    s.showCameraControls()
                                        .enableSwitchLens(true)
                                        .enableAutoFocus(cameraUiState.autoExposure)
                                        .enableCameraControls()
                                }
                        )

                    }

                    CameraState.PREVIEW_IN_BACKGROUND -> Unit
                    CameraState.PREVIEW_STOPPED -> Unit
                }

                when (cameraUiState.cameraSettingState) {
                    CameraSettingState.NOT_READY -> Unit
                    CameraSettingState.LOADING -> Unit
                    CameraSettingState.LOADED -> Unit
                    CameraSettingState.LOADING_FAILED -> Unit
                    CameraSettingState.RELOADING -> Unit
                    CameraSettingState.RELOADING_FAILED -> Unit
                    CameraSettingState.VALUE_UPDATED -> {
                        Log.d(TAG, "VALUE_UPDATED")
                        cameraPreviewScreen.setCameraSettingButtonValue(cameraSettingState)
                    }

                }

            }
        }

    }

    private fun getCurrentPermissionState(): PermissionState {
        val status =
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
        return if (status == PackageManager.PERMISSION_GRANTED) {
            PermissionState.Granted
        } else {
            PermissionState.Denied(
                ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(),
                    Manifest.permission.CAMERA
                )
            )
        }
    }

}
