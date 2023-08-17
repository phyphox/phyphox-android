package de.rwth_aachen.phyphox.camera.service

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ImageAnalyseService: LifecycleService(), CameraCallback {

    private lateinit var cameraProviderListenableFuture:  ListenableFuture<ProcessCameraProvider>
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var imageAnalysis: ImageAnalysis
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val imageAnalyser: ImageAnalyserTemp = ImageAnalyserTemp()

    var camera: Camera? = null
    lateinit var preview: Preview

    var previewView: PreviewView? = null


    override fun onCreate() {
        super.onCreate()
        listenToLiveData()
        initializeCamera()

    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        //listenForCameraInstantiation()
        return START_STICKY
    }

    private fun listenToLiveData(){
        Log.d("CameraServiceLiveData", "listenToLiveData")
        CameraServiceLiveData.getInstance().observe(this) { t ->
            if (t != null) {
                previewView = t
                listenForCameraInstantiation()
            }
        }
    }

    private fun initializeCamera(){
        cameraProviderListenableFuture = ProcessCameraProvider.getInstance(application)
    }

    private fun listenForCameraInstantiation(){

            cameraProviderListenableFuture = ProcessCameraProvider.getInstance(this@ImageAnalyseService)

            cameraProviderListenableFuture.addListener({
                try {
                    processCameraProvider = cameraProviderListenableFuture.get()
                    (processCameraProvider as ProcessCameraProvider?)?.let {
                        startCamera(it)
                    }
                } catch (e: ExecutionException) {
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(this@ImageAnalyseService))

    }


    private fun startCamera(cameraProvider: ProcessCameraProvider){
        cameraProvider.unbindAll()
        val cameraSelector =  CameraSelector.DEFAULT_BACK_CAMERA

        preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView?.surfaceProvider)
        }

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor, imageAnalyser)

        if(lifecycle.currentState != Lifecycle.State.DESTROYED)
            cameraProvider.bindToLifecycle(this@ImageAnalyseService, cameraSelector, preview, imageAnalysis)

    }


    override fun onDestroy() {
        super.onDestroy()

    }

    override fun onSetupCameraPreview(previewView: PreviewView) {
       this.previewView = previewView
    }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
class ImageAnalyserTemp: ImageAnalysis.Analyzer {
    override fun  analyze(image: ImageProxy) {
        val mediaImage = image.image!!
        Log.d("ImageAnalyserTemp", "image: pixelStride "+mediaImage.planes[0].pixelStride)
        image.close()

    }

}
