package de.rwth_aachen.phyphox.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
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
import de.rwth_aachen.phyphox.camera.model.CameraSettingLevel
import de.rwth_aachen.phyphox.camera.model.CameraSettingRecyclerState
import de.rwth_aachen.phyphox.camera.model.CameraSettingState
import de.rwth_aachen.phyphox.camera.model.CameraState
import de.rwth_aachen.phyphox.camera.model.CameraUiAction
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisState
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisUIAction
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisValueState
import de.rwth_aachen.phyphox.camera.model.OverlayUpdateState
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
                experiment =
                    args.getSerializable(CameraHelper.EXPERIMENT_ARG, PhyphoxExperiment::class.java)
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraPreviewScreen = CameraPreviewScreen(view, experiment?.cameraInput!!, cameraViewModel)

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
                    is CameraUiAction.CameraSettingClick -> {
                        cameraViewModel.openCameraSettingValue(action.settingMode)
                    }

                    is CameraUiAction.UpdateCameraExposureSettingValue -> {
                        cameraViewModel.updateCameraSettingValue(action.value, action.settingMode)
                    }

                    is CameraUiAction.UpdateAutoExposure -> {
                        cameraViewModel.changeExposure(action.autoExposure)
                    }

                    is CameraUiAction.ExposureSettingValueSelected -> {
                        cameraViewModel.cameraSettingOpened()
                    }

                    is CameraUiAction.UpdateOverlay -> {
                        cameraViewModel.updateCameraOverlayValue()
                    }

                    is CameraUiAction.UpdateCameraDimension -> {
                        cameraViewModel.setUpCameraDimension(action.height, action.width)
                    }

                    is CameraUiAction.OverlayUpdateDone -> {
                        cameraViewModel.overlayUpdated()
                    }
                }
            }
        }

        lifecycleScope.launch {
            cameraViewModel.imageAnalyser.action.collectLatest {action: ImageAnalysisUIAction ->

                when(action){
                    is ImageAnalysisUIAction.UpdateLuminaceValue -> {
                        Log.d(TAG, "ImageAnalysisUIAction.UpdateLuminaceValue" )
                    }

                }

            }
        }


        lifecycleScope.launch {
            cameraViewModel.imageAnalysisUiState.collectLatest { value: ImageAnalysisValueState ->

                when(value.imageAnalysisState){
                    ImageAnalysisState.IMAGE_ANALYSIS_NOT_READY -> {
                        Log.d(TAG, "ImageAnalysisState.IMAGE_ANALYSIS_NOT_READY" )
                    }
                    ImageAnalysisState.IMAGE_ANALYSIS_READY -> {
                        Log.d(TAG, "ImageAnalysisState.IMAGE_ANALYSIS_READY" )
                    }
                    ImageAnalysisState.IMAGE_ANALYSIS_STARTED -> {
                        Log.d(TAG, "ImageAnalysisState.IMAGE_ANALYSIS_STARTED" )
                        Log.d(TAG, cameraViewModel.getLumnicanceValue().toString() )
                        Log.d(TAG, "ColorCode: " + cameraViewModel.getColorCode())
                        cameraPreviewScreen.setColorCodeText(cameraViewModel.getColorCode())

                    }
                    ImageAnalysisState.IMAGE_ANALYSIS_FINISHED -> {
                        Log.d(TAG, "ImageAnalysisState.IMAGE_ANALYSIS_FINISHED" )
                    }
                    ImageAnalysisState.IMAGE_ANALYSIS_FAILED -> {
                        Log.d(TAG, "ImageAnalysisState.IMAGE_ANALYSIS_FAILED" )
                    }
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
                                    it.hideCameraControls()
                                }
                        )
                        cameraViewModel.initializeCamera()
                    }

                    CameraState.READY -> {
                        Log.d(TAG, " CameraState.READY")
                        cameraPreviewScreen.previewView.doOnLayout {
                            cameraViewModel.startCameraPreviewView(
                                cameraPreviewScreen.previewView,
                                lifecycleOwner = this@CameraPreviewFragment as LifecycleOwner,
                                cameraSettingState.autoExposure
                            )
                        }
                        cameraScreenViewState.emit(
                            cameraScreenViewState.value
                                .updateCameraScreen { s ->
                                        s.showSwitchLens(true)
                                        .enableAutoFocus(cameraSettingState.autoExposure)
                                }
                        )
                        cameraPreviewScreen.setCameraSwitchInfo(cameraUiState)
                        cameraViewModel.imageAnalysisPrepared()

                    }

                    CameraState.LOADED -> {
                        cameraPreviewScreen.setupZoomControl(
                            cameraSettingState)
                    }
                    CameraState.PREVIEW_IN_BACKGROUND -> Unit
                    CameraState.PREVIEW_STOPPED -> Unit
                }

                when (cameraSettingState.cameraSettingState) {
                    CameraSettingState.NOT_READY -> {
                        cameraViewModel.setUpExposureValue()
                    }

                    CameraSettingState.LOADING -> Unit
                    CameraSettingState.LOADED -> {
                        cameraPreviewScreen.setCameraSettingText(cameraSettingState)
                        cameraScreenViewState.emit(
                            cameraScreenViewState.value.updateCameraScreen {
                                it.showCameraControls()

                                when (cameraSettingState.cameraSettingLevel) {
                                    CameraSettingLevel.BASIC -> it.enableBasicExposureControl()
                                    CameraSettingLevel.INTERMEDIATE -> it.enableIntermediateExposureControl()
                                    else -> it.enableAdvanceExposureControl()
                                }
                            }
                        )
                    }

                    CameraSettingState.LOADING_FAILED -> Unit
                    CameraSettingState.RELOADING -> Unit
                    CameraSettingState.RELOADING_FAILED -> Unit
                    CameraSettingState.LOADING_VALUE -> {

                        // When clicking an Exposure settings, hide or show the RecyclerView, which is
                        // showing the list of Exposure values to select from.
                        if (cameraSettingState.cameraSettingRecyclerState == CameraSettingRecyclerState.HIDDEN) {
                            cameraViewModel.updateViewStateOfRecyclerView(recyclerViewShown = true)
                        } else {
                            cameraViewModel.updateViewStateOfRecyclerView(recyclerViewShown = false)
                            cameraPreviewScreen.recyclerLoadingFinished()
                            return@collectLatest
                        }

                        // Get the current organized Exposure value as per the Exposure Mode,
                        // which is mapped from the raw value provided by camera API
                        val currentValue = when (cameraSettingState.settingMode) {
                            SettingMode.ISO -> cameraSettingState.isoRange?.map { it.toInt() }
                                ?.let { isoRange ->
                                    CameraHelper.findIsoNearestNumber(
                                        cameraSettingState.currentIsoValue,
                                        isoRange
                                    )
                                }

                            SettingMode.SHUTTER_SPEED -> {
                                val fraction =
                                    CameraHelper.convertNanoSecondToSecond(cameraSettingState.currentShutterValue)
                                "${fraction.numerator}/${fraction.denominator}"
                            }

                            SettingMode.APERTURE -> cameraSettingState.currentApertureValue
                            SettingMode.EXPOSURE ->
                                CameraHelper.getActualValueFromExposureCompensation(
                                    cameraSettingState.currentExposureValue, cameraSettingState.exposureStep)
                            else -> ""
                        }.toString()


                        val exposureSettingRange = when (cameraSettingState.settingMode) {
                            SettingMode.ISO -> cameraSettingState.isoRange
                            SettingMode.SHUTTER_SPEED -> cameraSettingState.shutterSpeedRange
                            SettingMode.APERTURE -> cameraSettingState.apertureRange
                            SettingMode.EXPOSURE -> cameraSettingState.exposureRange
                            else -> emptyList()
                        }

                        cameraPreviewScreen.showRecyclerViewForExposureSetting(
                            exposureSettingRange,
                            cameraSettingState.settingMode,
                            currentValue
                        )
                    }
                    CameraSettingState.LOAD_VALUE -> Unit
                    CameraSettingState.VALUE_UPDATED -> {
                        cameraScreenViewState.emit(cameraScreenViewState.value.updateCameraScreen {
                            it.hideRecyclerView()
                        })
                        cameraViewModel.updateViewStateOfRecyclerView(recyclerViewShown = false)
                        cameraPreviewScreen.setCameraSettingButtonValue(cameraSettingState)
                    }

                    CameraSettingState.LOAD_FINISHED -> {
                        if (cameraSettingState.cameraSettingRecyclerState == CameraSettingRecyclerState.SHOWN) {
                            cameraScreenViewState.emit(cameraScreenViewState.value.updateCameraScreen {
                                it.showRecyclerView()
                            })
                        } else {
                            cameraScreenViewState.emit(cameraScreenViewState.value.updateCameraScreen {
                                it.hideRecyclerView()
                            })
                        }
                    }

                }

                when(cameraUiState.overlayUpdateState){
                    OverlayUpdateState.NO_UPDATE -> Unit
                    OverlayUpdateState.UPDATE -> {
                        Log.d(TAG, "OverlayUpdateState.UPDATE")
                        cameraPreviewScreen.updateOverlay(cameraUiState)
                    }
                    OverlayUpdateState.UPDATE_DONE -> {
                        Log.d(TAG, "OverlayUpdateState.UPDATE_DONE")
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

    override fun onDestroy() {
        super.onDestroy()
        cameraViewModel.cameraExecutor.shutdown()
    }

}
