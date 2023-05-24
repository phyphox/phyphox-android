package de.rwth_aachen.phyphox.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.common.util.concurrent.ListenableFuture
import de.rwth_aachen.phyphox.camera.model.PermissionState
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import de.rwth_aachen.phyphox.MarkerOverlayView
import de.rwth_aachen.phyphox.R
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
import java.util.concurrent.ExecutionException


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraPreviewFragment : Fragment() , TextureView.SurfaceTextureListener {

    private lateinit var previewView: PreviewView
    private var camera: Camera? = null
    var imageAnalysis: ImageAnalysis? = null
    var cameraInput: CameraInput = CameraInput()

    lateinit var overlayView: MarkerOverlayView
    var transformation = Matrix()

    var panningIndexX = 0
    var panningIndexY = 0

    var width = 1000
    var height = 1000

    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraManager: CameraManager

    private var photometricReader: PhotometricReader = PhotometricReader(PhotometricReader.ImageEvaluation.RedBrightness)

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

        /**

        cameraProviderListenableFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderListenableFuture.addListener({
            try {
               cameraProvider = cameraProviderListenableFuture.get()
                (cameraProvider as ProcessCameraProvider?)?.let {
                    mainFrameLayout.removeView(overlayView)
                    height = mainFrameLayout.height
                    width = mainFrameLayout.width
                    mainFrameLayout.addView(overlayView, width, height)
                    updateOverlay()
                    startCamera(it) }
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
        */

    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onResume() {
        super.onResume()
        /**
        mainFrameLayout.setOnTouchListener(OnTouchListener { v, event ->

            val touch = floatArrayOf(event.x, event.y)

            val invert = Matrix()
            transformation.invert(invert)
            invert.mapPoints(touch)
            val x: Float = touch[1] / 1000f // TODO getHeight instead
            val y: Float = 1.0f - touch[0] / 1000f

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d("CameraPreviewFramgment", "ACTION_DOWN")
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
                    Log.d("CameraPreviewFramgment", "ACTION_MOVE")
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

                    updateOverlay()

                }
                MotionEvent.ACTION_UP ->  {
                    Log.d("CameraPreviewFramgment", "ACTION_UP")
                    v.performClick()
                }
            }
            true
        })
        */
    }

    private fun updateOverlay() {
        //TODO dynamic assigning of the width and height
        val xmin: Float = Math.min(cameraInput.x1, cameraInput.x2)
        val xmax: Float = Math.max(cameraInput.x1, cameraInput.x2)
        val ymin: Float = Math.min(cameraInput.y1, cameraInput.y2)
        val ymax: Float = Math.max(cameraInput.y1, cameraInput.y2)
        val inner = RectF(
            (1.0f - ymax) * width,
            xmin * height,
            (1.0f - ymin) * width,
            xmax * height
        )
        val outer = RectF(0f, 0f, width.toFloat(), height.toFloat())
        transformation.mapRect(inner)
        transformation.mapRect(outer)
        val off = IntArray(2)
        val off2 = IntArray(2)
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
    }


    private fun startCamera(cameraProvider: ProcessCameraProvider){
        cameraProvider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK) //TODO support it for both lens
            .build()

        val preview = setUpPreviewWithExposure(CameraMetadata.CONTROL_AE_MODE_ON, 600, 100000000,0.5f )
            .setTargetResolution(Size(640, 480)) // Set the desired resolution
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { image ->
                    val value = image.image?.let { photometricReader.calculateAvgRedBrightness(it) }
                    Log.d("Image value", value.toString() )
                    image.close()
                }
            }

        camera = cameraProvider.bindToLifecycle(
            (requireActivity() as LifecycleOwner),
            cameraSelector,
            preview,
            imageAnalysis)


    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setUpPreviewWithExposure(exposureState: Int, iso: Int, shutterSpeed: Long, aperture: Float ): Preview.Builder{
        val previewBuilder = Preview.Builder()
        val extender = Camera2Interop.Extender(previewBuilder)

        val cameraInfo = camera?.cameraInfo?.let { Camera2CameraInfo.from(it) }
        val sensitivityRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureTimeRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val apertureRange = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)

        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, exposureState)
        extender.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
        extender.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeed)
        extender.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, aperture)

        return previewBuilder
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
