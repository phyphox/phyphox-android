package de.rwth_aachen.phyphox.camera

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
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
import de.rwth_aachen.phyphox.camera.model.CameraSettingMode
import de.rwth_aachen.phyphox.camera.ui.CameraPreviewScreen
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModelFactory
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.Serializable


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraPreviewFragment : Fragment() {
    val TAG = "CameraPreviewFragment"

    /* view model to setup and update camera */
    private lateinit var cameraViewModel: CameraViewModel

    /* handles all the UI elements for camera preview */
    private lateinit var cameraPreviewScreen: CameraPreviewScreen

    /* tracks the current view state */
    private val cameraScreenViewState = MutableStateFlow(CameraScreenViewState())

    /* holds all the experiment information */
    private var experiment: PhyphoxExperiment? = null

    lateinit var scrollable : Scrollable

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
                scrollable =
                    args.getSerializable(CameraHelper.EXPERIMENT_SCROLL_ARG, Scrollable::class.java)!!
            } else {
                experiment = args.getSerializable(CameraHelper.EXPERIMENT_ARG) as PhyphoxExperiment?
                scrollable =
                    args.getSerializable(CameraHelper.EXPERIMENT_SCROLL_ARG) as Scrollable
            }
            cameraViewModel.cameraInput = experiment?.cameraInput!!
            cameraViewModel.phyphoxExperiment = experiment!!
            cameraViewModel.scrollable = scrollable
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
                cameraPreviewScreen.updateCameraScreenViewState(state = it)
            }
        }

        lifecycleScope.launch {
            cameraPreviewScreen.action.collectLatest { action ->
                when (action) {
                    is CameraUiAction.SwitchCameraClick -> cameraViewModel.switchCamera()

                    is CameraUiAction.CameraSettingClick ->
                        cameraViewModel.openCameraSettingValue(action.settingMode)

                    is CameraUiAction.UpdateCameraExposureSettingValue ->
                        cameraViewModel.updateCameraSettingValue(action.value, action.settingMode)

                    is CameraUiAction.UpdateAutoExposure ->
                        cameraViewModel.changeExposure(action.autoExposure)

                    is CameraUiAction.CameraSettingValueSelected ->
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
            cameraViewModel
                .cameraUiState
                .combine(cameraViewModel.cameraSettingValueState)
                { cameraUiState, cameraSettingState -> Pair(cameraUiState, cameraSettingState) }
                .collectLatest { (cameraUiState, cameraSettingState) ->

                    when (cameraUiState.cameraState) {
                        CameraState.NOT_READY -> cameraViewModel.initializeCamera()

                        CameraState.READY -> {
                            cameraPreviewScreen.previewView.doOnLayout {
                                cameraViewModel.startCameraPreviewView(
                                    cameraPreviewScreen.previewView,
                                    lifecycleOwner = this@CameraPreviewFragment as LifecycleOwner,
                                    cameraSettingState.disabledAutoExposure
                                )
                            }
                            cameraScreenViewState.emit(
                                cameraScreenViewState.value.updateCameraScreen { cameraScreen ->
                                        cameraScreen
                                            .showSwitchLens(true)
                                            .enableAutoFocus(cameraSettingState.disabledAutoExposure)

                                    }
                            )
                            cameraPreviewScreen.setCameraSwitchInfo(cameraUiState)
                            cameraViewModel.cameraReady()

                        }

                        CameraState.LOADED -> {
                            cameraScreenViewState.emit(
                                cameraScreenViewState.value.updateCameraScreen { cameraScreen ->
                                    val opticalZooms =
                                        CameraHelper.getAvailableOpticalZoomList(cameraSettingState.cameraMaxOpticalZoom)

                                    cameraScreen.setupOpticalZoomButtonVisibility(
                                        cameraSettingState.cameraMinZoomRatio < 1.0f,
                                        opticalZooms.isNotEmpty(),
                                        opticalZooms.contains(2),
                                        opticalZooms.contains(5),
                                        opticalZooms.contains(10)
                                    )

                                }
                            )
                            cameraPreviewScreen.setupZoomControl(cameraSettingState)
                            cameraPreviewScreen.setUpWhiteBalanceControl(cameraSettingState)
                        }

                    }

                    when (cameraSettingState.cameraSettingState) {
                        CameraSettingState.NOT_READY -> cameraViewModel.loadAndSetupExposureSettingRanges()

                        CameraSettingState.LOADED -> {
                            cameraPreviewScreen.setCurrentValueInCameraSettingTextView(cameraSettingState)
                            cameraScreenViewState.emit(
                                cameraScreenViewState.value.updateCameraScreen { it ->
                                    it.showSwitchLensControl()

                                    when (cameraSettingState.cameraSettingLevel) {
                                        CameraSettingLevel.BASIC -> it.enableBasicExposureControl()

                                        CameraSettingLevel.INTERMEDIATE -> {
                                            val exposureLocked =
                                                cameraUiState.editableCameraSettings?.contains("exposure") ?: false

                                            // if exposure is locked, then disable the exposure click
                                            if (cameraSettingState.disabledAutoExposure)
                                                it.enableIntermediateExposureControl(exposureLocked)
                                            else
                                                it.enableIntermediateExposureControl(exposureLocked)
                                                    .setCameraSettingsClickability(false)
                                        }

                                        CameraSettingLevel.ADVANCE -> {

                                            val isoLocked =
                                                cameraUiState.editableCameraSettings?.contains("iso") ?: false

                                            val shutterLocked =
                                                cameraUiState.editableCameraSettings?.contains("shutter_speed") ?: false

                                            if (cameraSettingState.disabledAutoExposure)
                                                it.enableAdvanceExposureControl(isoLocked, shutterLocked)
                                            else
                                                it.enableAdvanceExposureControl(isoLocked, shutterLocked)
                                                    .setCameraSettingsClickability(false)
                                        }
                                    }
                                }
                            )
                        }

                        CameraSettingState.LOADING_FAILED -> Unit
                        CameraSettingState.LOAD_LIST -> {

                            if (cameraSettingState.cameraSettingRecyclerState == CameraSettingRecyclerState.TO_HIDE) {
                                cameraViewModel.updateViewStateOfRecyclerView(showRecyclerview = true)
                            } else {
                                cameraViewModel.updateViewStateOfRecyclerView(showRecyclerview = false)
                                cameraPreviewScreen.recyclerLoadingFinished()
                                return@collectLatest
                            }

                            // Get the current organized Exposure value as per the Exposure Mode,
                            // which is mapped from the raw value provided by camera API
                            val currentValue = when (cameraSettingState.settingMode) {
                                CameraSettingMode.ISO -> cameraSettingState.isoRange?.map { it.toInt() }
                                    ?.let { isoRange ->
                                        CameraHelper.findIsoNearestNumber(
                                            cameraSettingState.currentIsoValue,
                                            isoRange
                                        )
                                    }

                                CameraSettingMode.SHUTTER_SPEED -> {
                                    val fraction =
                                        CameraHelper.convertNanoSecondToSecond(cameraSettingState.currentShutterValue)
                                    "${fraction.numerator}/${fraction.denominator}"
                                }

                                CameraSettingMode.APERTURE -> cameraSettingState.currentApertureValue

                                CameraSettingMode.EXPOSURE ->
                                    CameraHelper.getActualValueFromExposureCompensation(
                                        cameraSettingState.currentExposureValue,
                                        cameraSettingState.exposureStep
                                    )

                                CameraSettingMode.WHITE_BALANCE ->
                                    CameraHelper.getWhiteBalanceModes().getValue(cameraSettingState.cameraCurrentWhiteBalanceMode)

                                else -> ""
                            }.toString()


                            val exposureSettingRange = when (cameraSettingState.settingMode) {
                                CameraSettingMode.ISO -> cameraSettingState.isoRange
                                CameraSettingMode.SHUTTER_SPEED -> cameraSettingState.shutterSpeedRange
                                CameraSettingMode.APERTURE -> cameraSettingState.apertureRange
                                CameraSettingMode.EXPOSURE -> cameraSettingState.exposureRange
                                CameraSettingMode.WHITE_BALANCE ->
                                    CameraHelper.getWhiteBalanceModes().filter {
                                        cameraSettingState.cameraWhiteBalanceModes.contains(it.key)
                                    }.values.toList().filter {
                                        it != "Manual"
                                    }
                                else -> emptyList()
                            }

                            cameraPreviewScreen.populateAndShowCameraSettingValueIntoRecyclerView(
                                exposureSettingRange,
                                cameraSettingState.settingMode,
                                currentValue
                            )
                        }

                        CameraSettingState.VALUE_UPDATED -> {
                            cameraPreviewScreen.setCameraSettingButtonValue(cameraSettingState)
                            cameraPreviewScreen.setWhiteBalanceSliderVisibility(cameraSettingState)
                        }

                        CameraSettingState.LOAD_FINISHED -> {
                            if (cameraSettingState.cameraSettingRecyclerState == CameraSettingRecyclerState.TO_SHOW
                                &&
                                cameraSettingState.disabledAutoExposure
                            ) {
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

    override fun onDestroy() {
        super.onDestroy()
        cameraViewModel.cameraExecutor.shutdown()
    }

}

interface Scrollable: Serializable {
    fun enableScrollable()

    fun disableScrollable()

}
