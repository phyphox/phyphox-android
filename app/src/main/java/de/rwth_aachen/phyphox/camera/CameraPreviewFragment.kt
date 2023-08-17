package de.rwth_aachen.phyphox.camera

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.rwth_aachen.phyphox.PhyphoxExperiment
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.helper.CameraHelper
import de.rwth_aachen.phyphox.camera.model.CameraSettingLevel
import de.rwth_aachen.phyphox.camera.model.CameraSettingRecyclerState
import de.rwth_aachen.phyphox.camera.model.CameraState
import de.rwth_aachen.phyphox.camera.model.CameraUiAction
import de.rwth_aachen.phyphox.camera.model.ExposureSettingMode
import de.rwth_aachen.phyphox.camera.model.ExposureSettingState
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisState
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisUIAction
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisValueState
import de.rwth_aachen.phyphox.camera.model.OverlayUpdateState
import de.rwth_aachen.phyphox.camera.service.CameraCallback
import de.rwth_aachen.phyphox.camera.service.CameraServiceLiveData
import de.rwth_aachen.phyphox.camera.service.ImageAnalyseService
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

    // view model to setup and update camera
    private lateinit var cameraViewModel: CameraViewModel

    // handles all the UI elements for camera preview
    private lateinit var cameraPreviewScreen: CameraPreviewScreen

    //tracks the current view state
    private val cameraScreenViewState = MutableStateFlow(CameraScreenViewState())

    //holds experiment info/object
    private var experiment: PhyphoxExperiment? = null

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        if(isMyServiceRunning(ImageAnalyseService::class.java)){
            val intent = Intent(context, ImageAnalyseService::class.java)
            context?.stopService(intent)
        }

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



        lifecycleScope.launch {
            cameraPreviewScreen.action.collectLatest { action ->
                when (action) {
                    is CameraUiAction.SwitchCameraClick -> cameraViewModel.switchCamera()

                    is CameraUiAction.CameraSettingClick -> {
                        cameraViewModel.openCameraSettingValue(action.settingMode)
                    }

                    is CameraUiAction.UpdateCameraExposureSettingValue ->
                        cameraViewModel.updateCameraSettingValue(action.value, action.settingMode)

                    is CameraUiAction.UpdateAutoExposure ->
                        cameraViewModel.changeExposure(action.autoExposure)

                    is CameraUiAction.ExposureSettingValueSelected ->
                        cameraViewModel.cameraSettingOpened()

                    is CameraUiAction.UpdateOverlay ->
                        cameraViewModel.updateCameraOverlayValue()

                    is CameraUiAction.UpdateCameraDimension ->
                        cameraViewModel.setUpCameraDimension(action.height, action.width)

                    is CameraUiAction.OverlayUpdateDone ->
                        cameraViewModel.overlayUpdated()
                }
            }
        }

        lifecycleScope.launch {
            cameraViewModel.imageAnalyser.action.collectLatest { action: ImageAnalysisUIAction ->
                when (action) {
                    is ImageAnalysisUIAction.UpdateLuminaceValue -> Unit
                }
            }
        }

        lifecycleScope.launch {
            cameraViewModel.imageAnalysisUiState.collectLatest { value: ImageAnalysisValueState ->
                when (value.imageAnalysisState) {
                    ImageAnalysisState.IMAGE_ANALYSIS_NOT_READY -> Unit

                    ImageAnalysisState.IMAGE_ANALYSIS_READY -> Unit

                    ImageAnalysisState.IMAGE_ANALYSIS_STARTED -> {
                        // set hex code text when color is detected
                        cameraPreviewScreen.setColorCodeText(cameraViewModel.getColorCode())
                    }

                    ImageAnalysisState.IMAGE_ANALYSIS_FINISHED -> Unit

                    ImageAnalysisState.IMAGE_ANALYSIS_FAILED -> Unit
                }
            }
        }


        lifecycleScope.launch {
            cameraViewModel.cameraUiState.combine(cameraViewModel.cameraSettingValueState) { cameraUiState, cameraSettingState ->
                Pair(cameraUiState, cameraSettingState)
            }.collectLatest { (cameraUiState, cameraSettingState) ->

                when (cameraUiState.cameraState) {
                    CameraState.NOT_READY -> {
                        cameraScreenViewState.emit(
                            cameraScreenViewState.value
                                .updateCameraScreen {
                                    it.hideCameraControls()
                                }
                        )
                        Log.d(TAG, "NOT_READY" )
                        cameraViewModel.initializeCamera()
                    }

                    CameraState.READY -> {
                        cameraPreviewScreen.previewView.doOnLayout {
                            startCameraService(cameraPreviewScreen.previewView)
                        }
                        cameraScreenViewState.emit(
                            cameraScreenViewState.value
                                .updateCameraScreen { cameraScreen ->
                                    cameraScreen
                                        .showSwitchLens(true)
                                        .enableAutoFocus(cameraSettingState.autoExposure)

                                }
                        )
                        cameraPreviewScreen.setCameraSwitchInfo(cameraUiState)
                        cameraViewModel.imageAnalysisPrepared()


                    }

                    CameraState.LOADED -> {
                        cameraScreenViewState.emit(
                            cameraScreenViewState.value.updateCameraScreen { cameraScreen ->
                                val opticalZooms =
                                    CameraHelper.getAvailableOpticalZoomList(cameraSettingState.cameraMaxOpticalZoom)

                                val widerAngleAvailable = cameraSettingState.cameraMinZoomRatio < 1.0f
                                val defaultZoom = opticalZooms.isNotEmpty()
                                val twoTimesZoomAvailable = opticalZooms.contains(2)
                                val fiveTimesZoomAvailable = opticalZooms.contains(5)
                                val tenTimesZoomAvailable = opticalZooms.contains(10)

                                cameraScreen.setupOpticalZoomButtonVisibility(
                                    widerAngleAvailable,
                                    defaultZoom,
                                    twoTimesZoomAvailable,
                                    fiveTimesZoomAvailable,
                                    tenTimesZoomAvailable
                                )

                            }
                        )
                        cameraPreviewScreen.setupZoomControl(cameraSettingState)
                        cameraPreviewScreen.setUpWhiteBalanceControl(cameraSettingState)
                    }

                    else -> {}
                }

                when (cameraSettingState.exposureSettingState) {
                    ExposureSettingState.NOT_READY -> cameraViewModel.loadAndSetupExposureSettingRanges()
                    ExposureSettingState.LOADED -> {
                        cameraPreviewScreen.setCurrentValueInExposureSettingTextView(
                            cameraSettingState
                        )
                        cameraScreenViewState.emit(
                            cameraScreenViewState.value.updateCameraScreen {
                                it.showSwitchLensControl()
                                when (cameraSettingState.cameraSettingLevel) {
                                    CameraSettingLevel.BASIC -> it.enableBasicExposureControl()
                                    CameraSettingLevel.INTERMEDIATE -> it.enableIntermediateExposureControl()
                                    CameraSettingLevel.ADVANCE -> it.enableAdvanceExposureControl()
                                }
                            }
                        )
                    }

                    ExposureSettingState.LOADING_FAILED -> Unit
                    ExposureSettingState.LOAD_LIST -> {

                        // When clicking an Exposure settings, hide or show the RecyclerView, which is
                        // showing the list of Exposure values to select from.
                        if (cameraSettingState.cameraSettingRecyclerState == CameraSettingRecyclerState.HIDDEN) {
                            cameraViewModel.updateViewStateOfRecyclerView(showRecyclerview = true)
                        } else {
                            cameraViewModel.updateViewStateOfRecyclerView(showRecyclerview = false)
                            cameraPreviewScreen.recyclerLoadingFinished()
                            return@collectLatest
                        }

                        // Get the current organized Exposure value as per the Exposure Mode,
                        // which is mapped from the raw value provided by camera API
                        val currentValue = when (cameraSettingState.settingMode) {
                            ExposureSettingMode.ISO -> cameraSettingState.isoRange?.map { it.toInt() }
                                ?.let { isoRange ->
                                    CameraHelper.findIsoNearestNumber(
                                        cameraSettingState.currentIsoValue,
                                        isoRange
                                    )
                                }

                            ExposureSettingMode.SHUTTER_SPEED -> {
                                val fraction =
                                    CameraHelper.convertNanoSecondToSecond(cameraSettingState.currentShutterValue)
                                "${fraction.numerator}/${fraction.denominator}"
                            }

                            ExposureSettingMode.APERTURE -> cameraSettingState.currentApertureValue
                            ExposureSettingMode.EXPOSURE ->
                                CameraHelper.getActualValueFromExposureCompensation(
                                    cameraSettingState.currentExposureValue,
                                    cameraSettingState.exposureStep
                                )

                            ExposureSettingMode.WHITE_BALANCE -> CameraHelper.getWhiteBalanceNames()
                                .get(cameraSettingState.cameraCurrentWhiteBalanceMode)

                            else -> ""
                        }.toString()


                        val exposureSettingRange = when (cameraSettingState.settingMode) {
                            ExposureSettingMode.ISO -> cameraSettingState.isoRange
                            ExposureSettingMode.SHUTTER_SPEED -> cameraSettingState.shutterSpeedRange
                            ExposureSettingMode.APERTURE -> cameraSettingState.apertureRange
                            ExposureSettingMode.EXPOSURE -> cameraSettingState.exposureRange
                            ExposureSettingMode.WHITE_BALANCE ->
                                if (cameraSettingState.cameraMaxRegionAWB == 0)
                                    CameraHelper.getWhiteBalanceNames().filter { it != "Manual" }
                                else
                                    CameraHelper.getWhiteBalanceNames()

                            else -> emptyList()
                        }

                        cameraPreviewScreen.populateAndShowCameraSettingValue(
                            exposureSettingRange,
                            cameraSettingState.settingMode,
                            currentValue
                        )
                    }

                    ExposureSettingState.VALUE_UPDATED -> {
                        //cameraScreenViewState.emit(cameraScreenViewState.value.updateCameraScreen { it.hideRecyclerView() })
                        //cameraViewModel.updateViewStateOfRecyclerView(showRecyclerview = false)
                        cameraPreviewScreen.setCameraSettingButtonValue(cameraSettingState)
                        cameraPreviewScreen.setWhiteBalanceSliderVisibility(cameraSettingState)
                    }

                    ExposureSettingState.LOAD_FINISHED -> {
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

                when (cameraUiState.overlayUpdateState) {
                    OverlayUpdateState.NO_UPDATE -> Unit
                    OverlayUpdateState.UPDATE -> cameraPreviewScreen.updateOverlay(cameraUiState)
                    OverlayUpdateState.UPDATE_DONE -> Unit
                }
            }
        }

    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "OnStop")
        val intent = Intent(context, ImageAnalyseService::class.java)
        context?.startService(intent)
        //cameraViewModel.restartCameraPreview()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OnDestroy")
        cameraViewModel.cameraExecutor.shutdown()
        if(isMyServiceRunning(ImageAnalyseService::class.java)){
            val intent = Intent(context, ImageAnalyseService::class.java)
            context?.stopService(intent)
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = context?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startCameraService(previewView: PreviewView) {
        // Start the service
        val serviceIntent = Intent(requireContext(), ImageAnalyseService::class.java)
        context?.startService(serviceIntent)
        Log.d("CameraServiceLiveData", "startCameraService")
        CameraServiceLiveData.getInstance().postCustomObject(previewView);
    }
}
