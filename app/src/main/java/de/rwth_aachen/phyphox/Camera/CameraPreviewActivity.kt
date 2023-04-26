package de.rwth_aachen.phyphox.Camera

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import de.rwth_aachen.phyphox.MarkerOverlayView
import de.rwth_aachen.phyphox.R
import java.util.concurrent.ExecutionException


class CameraPreviewActivity : AppCompatActivity() {
    var previewView: PreviewView? = null
    private var cameraProviderListenableFuture: ListenableFuture<ProcessCameraProvider>? = null
    var imageAnalysis: ImageAnalysis? = null
    private lateinit var overlayView: MarkerOverlayView;
    var photometricReader: PhotometricReader? = null
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    var imageEvaluation = PhotometricReader.ImageEvaluation.RedBrightness
    var textView: TextView? = null

    //TODO version code
    @RequiresApi(api = Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)
        previewView = findViewById(R.id.view_finder)
        overlayView = MarkerOverlayView(this)
        textView = findViewById(R.id.textPreviewValue);
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

        photometricReader = PhotometricReader(PhotometricReader.ImageEvaluation.RedBrightness)

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis!!.setAnalyzer(
            ContextCompat.getMainExecutor(this)
        ) { image ->
            // Get the buffer of the Y plane
            //val buffer = image.planes[0].buffer

            // Create a byte array and copy the buffer contents to it
            //val pixelArray = ByteArray(buffer.remaining())

            // Copy the buffer contents to the byte array
            //buffer[pixelArray]

            if( imageEvaluation== PhotometricReader.ImageEvaluation.AverageLuminosity){
                //val luminosity = photometricReader!!.calculateAverageLuminosity(pixelArray) // Calculate the luminosity
                //Log.d("Luminosity", "value is: $luminosity")
            }
            else if(imageEvaluation == PhotometricReader.ImageEvaluation.RedBrightness){
                val value = image.image?.let { photometricReader!!.calculateAvgRedBrightness(it) }
                textView?.text = value.toString()
            }

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

    /**
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun calculateRedBrightness(imageProxy: ImageProxy){
        val mat: Mat = CameraHelper.convertImageProxyToMat(imageProxy);
        val outputMat: Mat = Mat();
        Imgproc.cvtColor(mat, outputMat, Imgproc.COLOR_BGR2GRAY)


        val mRgba = Mat(imageProxy.height, imageProxy.width, CvType.CV_8UC4)
        val mIntermediateMat = Mat(imageProxy.height, imageProxy.width, CvType.CV_8UC4)
        val mGray = Mat(imageProxy.height, imageProxy.width, CvType.CV_8UC1)

        // Split the image into its RGB channels
        // Split the image into its RGB channels
        Core.extractChannel(mRgba, mIntermediateMat, 0) // Blue

        val blueMat = Mat()
        Imgproc.threshold(mIntermediateMat, blueMat, 0.0, 255.0, Imgproc.THRESH_BINARY_INV)

        Core.extractChannel(mRgba, mIntermediateMat, 1) // Green

        val greenMat = Mat()
        Imgproc.threshold(mIntermediateMat, greenMat, 0.0, 255.0, Imgproc.THRESH_BINARY_INV)

        Core.extractChannel(mRgba, mIntermediateMat, 2) // Red

        val redMat = Mat()
        Imgproc.threshold(mIntermediateMat, redMat, 0.0, 255.0, Imgproc.THRESH_BINARY_INV)

        val outputRed: ImageProxy = convertMatToImageProxy(redMat, imageProxy)
        val outputBlue: ImageProxy = convertMatToImageProxy(blueMat, imageProxy)
        val outputGreen: ImageProxy = convertMatToImageProxy(greenMat, imageProxy)

        Log.d("Luminosity", "value of Red: " + calculateColorIntensity(outputRed))
        Log.d("Luminosity", "value of Blue: " + calculateColorIntensity(outputBlue))
        Log.d("Luminosity", "value of Green: " + calculateColorIntensity(outputGreen))
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun calculateColorIntensity(imageProxy: ImageProxy): Double{
        val buffer = imageProxy.planes[0].buffer // Get the buffer of the Y plane
        val pixelArray =
            ByteArray(buffer.remaining()) // Create a byte array and copy the buffer contents to it
        buffer[pixelArray]

        return calculateAverageLuminosity(pixelArray)
    }
     */
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
