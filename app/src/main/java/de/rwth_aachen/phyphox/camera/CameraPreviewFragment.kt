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
import de.rwth_aachen.phyphox.camera.model.SettingMode
import de.rwth_aachen.phyphox.camera.ui.CameraPreviewScreen
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModelFactory
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraPreviewFragment : Fragment(), TextureView.SurfaceTextureListener {
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
                        if (action.settingMode == SettingMode.ISO) {
                            cameraViewModel.cameraInput.isoCurrentValue = action.value.toInt()
                        }
                        if (action.settingMode == SettingMode.SHUTTER_SPEED) {
                            cameraViewModel.cameraInput.shutterSpeedCurrentValue =
                                CameraHelper.stringToNanoseconds(action.value)
                        }

                        cameraViewModel.restartCamera()
                    }

                    is CameraUiAction.ChangeAutoExposure -> {
                        cameraViewModel.changeExposure(action.autoExposure)
                        //cameraViewModel.cameraInput.autoExposure = action.autoExposure
                        //cameraViewModel.restartCamera()
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
            permissionState.combine(cameraViewModel.cameraUiState) { permissionState, cameraUiState ->
                Pair(permissionState, cameraUiState)
            }.collectLatest { (permissionState, cameraUiState) ->
                when (permissionState) {
                    PermissionState.Granted -> {
                        cameraPreviewScreen.hidePermissionsRequest()
                    }

                    is PermissionState.Denied -> {
                        if (cameraUiState.cameraState != CameraState.PREVIEW_STOPPED) {
                            cameraPreviewScreen.showPermissionsRequest(permissionState.shouldShowRational)
                            return@collectLatest
                        }
                    }
                }

                when (cameraUiState.cameraState) {
                    CameraState.NOT_READY -> {
                        Log.d(TAG, " CameraState.NOT_READY")
                        cameraScreenViewState.emit(
                            cameraScreenViewState.value
                                .updateCameraScreen {
                                    it.showCameraControls()
                                        .enableSwitchLens(false)
                                }
                                .updateCameraSetting {
                                    it.isoSliderVisibility(false)
                                    it.shutterSpeedSliderVisibility(false)
                                    it.apertureSliderVisibility(false)
                                }
                        )
                        cameraViewModel.initializeCamera()
                        cameraPreviewScreen.slidersViewSetup(experiment)
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
                                    s.showCameraControls()
                                        .enableSwitchLens(true)
                                        .enableAutoFocus(cameraUiState.autoExposure)
                                }
                                .updateCameraSetting {
                                    it.isoSliderVisibility(true)
                                    it.shutterSpeedSliderVisibility(true)
                                    it.apertureSliderVisibility(true)
                                }
                        )
                        //cameraViewModel.cameraSettingLoadingSuccess()

                    }

                    CameraState.PREVIEW_IN_BACKGROUND -> Unit
                    CameraState.PREVIEW_STOPPED -> Unit
                }

                when (cameraUiState.cameraSettingState) {
                    CameraSettingState.NOT_READY -> {
                        Log.d(TAG, " CameraSettingState.NOT_READY")
                    }

                    CameraSettingState.LOADING -> {
                        Log.d(TAG, " CameraSettingState.LOADING")
                        cameraViewModel.startCameraPreviewView(
                            cameraPreviewScreen.previewView,
                            lifecycleOwner = this@CameraPreviewFragment as LifecycleOwner,
                            cameraViewModel.cameraInput.autoExposure
                        )

                    }

                    CameraSettingState.LOADED -> {
                        Log.d(TAG, " CameraSettingState.LOADED")
                        cameraScreenViewState.emit(
                            cameraScreenViewState.value
                                .updateCameraSetting {
                                    it.isoSliderVisibility(true)
                                    it.shutterSpeedSliderVisibility(true)
                                    it.apertureSliderVisibility(true)
                                }
                        )
                    }

                    CameraSettingState.LOADING_FAILED -> Unit
                    CameraSettingState.RELOADING -> Unit
                    CameraSettingState.RELOADING_FAILED -> Unit

                }

            }
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onResume() {
        super.onResume()
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

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        TODO("Not yet implemented")
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        TODO("Not yet implemented")
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        TODO("Not yet implemented")
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        TODO("Not yet implemented")
    }


}
