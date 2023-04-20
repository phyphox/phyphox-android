package de.rwth_aachen.phyphox.Camera

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.Preview.SurfaceProvider
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import de.rwth_aachen.phyphox.MarkerOverlayView
import de.rwth_aachen.phyphox.R
import java.util.concurrent.ExecutionException
import kotlin.experimental.and

class CameraPreviewActivity : AppCompatActivity() {
    var previewView: PreviewView? = null
    private var cameraProviderListenableFuture: ListenableFuture<ProcessCameraProvider>? = null
    var imageAnalysis: ImageAnalysis? = null
    private lateinit var overlayView: MarkerOverlayView;

    //TODO version code
    @RequiresApi(api = Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)
        previewView = findViewById(R.id.view_finder)
        overlayView = MarkerOverlayView(this)


        cameraProviderListenableFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderListenableFuture!!.addListener({
            try {
                val cameraProvider = cameraProviderListenableFuture!!.get()
                startCamera(cameraProvider)
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    fun startCamera(cameraProvider: ProcessCameraProvider) {
        cameraProvider.unbindAll()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView!!.surfaceProvider)

        previewView!!.overlay.add(overlayView);

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis!!.setAnalyzer(
            ContextCompat.getMainExecutor(this)
        ) { image ->
            val buffer = image.planes[0].buffer // Get the buffer of the Y plane
            val pixelArray =
                ByteArray(buffer.remaining()) // Create a byte array and copy the buffer contents to it
            buffer[pixelArray] // Copy the buffer contents to the byte array
            val luminosity = calculateAverageLuminosity(pixelArray) // Calculate the luminosity
            Log.d("Luminosity", "value is: $luminosity")

            image.close()
        }

       val camera =  cameraProvider.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector,
            preview,
            imageAnalysis,
        )
        camera.cameraControl.setExposureCompensationIndex(0)

    }

    private fun calculateAverageLuminosity(pixelArray: ByteArray): Double {
        var luminosity = 0.0
        for (i in pixelArray.indices) {
            val pixel =
                (pixelArray[i].toInt() and 0xFF) // Convert the unsigned byte to an integer
            luminosity += pixel.toDouble()
        }
        luminosity /= pixelArray.size.toDouble() // Divide the sum by the number of pixels
        return luminosity
    }
}

// average different version
    // average of all colors RGB
    // Brightness combine red blue,
    // red green and blue seperately
    // output the lunimance, black or white
    // brghtness value which counts exposure from the camera (ev) unit
    // change the iso settings
    // average U of entire screen
    // saturrations of the image average
    // contrast, common is look at the numerical laplace
