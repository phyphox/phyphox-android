package de.rwth_aachen.phyphox.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.rwth_aachen.phyphox.camera.helper.PhotometricReader
import de.rwth_aachen.phyphox.camera.model.PermissionState
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import de.rwth_aachen.phyphox.MarkerOverlayView
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.camera.helper.CameraInput
import de.rwth_aachen.phyphox.camera.model.CameraState
import de.rwth_aachen.phyphox.camera.model.CameraUiAction
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisState
import de.rwth_aachen.phyphox.camera.ui.CameraPreviewScreen
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModelFactory
import de.rwth_aachen.phyphox.camera.viewstate.CameraScreenViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.lang.Exception


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraPreviewFragment : Fragment() , TextureView.SurfaceTextureListener {

    var cameraInput: CameraInput = CameraInput()

    var width = 1000
    var height = 1000

    val TAG = "CameraPreviewFragment"
    // view model for operating on the camera and capturing a photo
    private lateinit var cameraViewModel: CameraViewModel

    // monitors changes in camera permission state
    private lateinit var permissionState: MutableStateFlow<PermissionState>

    private lateinit var cameraPreviewScreen: CameraPreviewScreen

    // tracks the current view state
    private val cameraScreenViewState = MutableStateFlow(CameraScreenViewState())


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        cameraViewModel = ViewModelProvider(
            this,
            CameraViewModelFactory(
                application = requireActivity().application
            )
        )[CameraViewModel::class.java]

        // initialize the permission state flow with the current camera permission status
        permissionState = MutableStateFlow(getCurrentPermissionState())

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // check the current permission state every time upon the activity is resumed
                permissionState.emit(getCurrentPermissionState())
            }
        }

        cameraInput.x1 = 0.6f;
        cameraInput.x2 = 0.4f;
        cameraInput.y1 = 0.6f;
        cameraInput.y2 = 0.4f;
        return inflater.inflate(R.layout.fragment_camera, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraPreviewScreen = CameraPreviewScreen(view)

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
            cameraPreviewScreen.action.collectLatest {action ->
                    when(action) {
                        is CameraUiAction.SwitchCameraClick -> {
                            cameraViewModel.switchCamera()
                        }
                        is CameraUiAction.RequestPermissionClick -> {
                            requestPermissionsLauncher.launch(Manifest.permission.CAMERA)

                        }
                        is CameraUiAction.SelectAndChangeCameraSetting -> Unit
                    }
            }
        }

        lifecycleScope.launch {
            cameraViewModel.imageAnalysisUiState.collectLatest {state ->
                when(state) {
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


        lifecycleScope.launch{
           permissionState.combine(cameraViewModel.cameraUiState) { permissionState, cameraUiState ->
               Pair(permissionState, cameraUiState)
           }.collectLatest {(permissionState, cameraUiState) ->
                when(permissionState) {
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

               when (cameraUiState.cameraState){
                   CameraState.NOT_READY -> {
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
                   }
                   CameraState.READY -> {
                       cameraPreviewScreen.previewView.doOnLayout {
                           cameraPreviewScreen.setUpOverlay()
                           cameraViewModel.startCameraPreviewView(
                               cameraPreviewScreen.previewView,
                               lifecycleOwner = this@CameraPreviewFragment as LifecycleOwner
                           )

                       }
                       cameraScreenViewState.emit(
                           cameraScreenViewState.value
                               .updateCameraScreen {s ->
                                   s.showCameraControls()
                                       .enableSwitchLens(true)
                               }
                               .updateCameraSetting {
                                   it.isoSliderVisibility(true)
                                   it.shutterSpeedSliderVisibility(true)
                                   it.apertureSliderVisibility(true)
                               }
                       )
                   }
                   CameraState.PREVIEW_IN_BACKGROUND -> Unit
                   CameraState.PREVIEW_STOPPED -> Unit
               }

           }
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onResume() {
        super.onResume()
    }


    private fun getCurrentPermissionState(): PermissionState {
        val status = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
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
