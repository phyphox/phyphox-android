package de.rwth_aachen.phyphox.camera.helper

import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisUIAction
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ImageAnalyser(private val cameraViewModel: CameraViewModel) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L
    private var photometricReader: PhotometricReader = PhotometricReader()

    // Observable to observe the action performed
    private val _action: MutableSharedFlow<ImageAnalysisUIAction> = MutableSharedFlow()
    val action: Flow<ImageAnalysisUIAction> = _action

    /**
     * Helper extension function used to extract a byte array from an image plane buffer
     */
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val mediaImage =  image.image ?: return

        val cameraFeature = cameraViewModel.cameraInput.cameraFeature

        if (cameraViewModel.cameraInput.measuring) {
            cameraViewModel.imageAnalysisStarted()

            //We need to avoid copying the data (unless we want to allow for more analysis time then
            // the frame period) and we need to avoid iterating over all the data multiple times.
            // Even function call overload should be avoided "per pixel".
            // Therefore we have one loop over the native data taking into account the cropped area
            // and use inline functions to extract the values for each pixel.

            var luma = 0.0

            val crop = cameraViewModel.getCameraRect()
            val w = image.width
            val h = image.height
            val xmin = (crop.top * w).toInt().coerceIn(0, w-1)
            val xmax = (crop.bottom * w).toInt().coerceIn(0, w-1)
            val ymin = (crop.left * h).toInt().coerceIn(0, h-1)
            val ymax = (crop.right * h).toInt().coerceIn(0, h-1)

            val lData = image.planes[0].buffer
            val cbData = image.planes[1].buffer
            val crData = image.planes[2].buffer

            val xlStride = image.planes[0].pixelStride
            val xcbStride = image.planes[1].pixelStride
            val xcrStride = image.planes[2].pixelStride

            val ylStride = image.planes[0].rowStride
            val ycbStride = image.planes[1].rowStride
            val ycrStride = image.planes[2].rowStride

            for (y in ymin until ymax) {
                val ylOffset = y*ylStride
                for (x in xmin until xmax) {
                    luma += lData[ylOffset + x*xlStride].toInt() and 0xFF
                }
            }

            luma /= (xmax-xmin)*(ymax-ymin)
            cameraViewModel.updateImageAnalysisLuma(luma)
            cameraViewModel.cameraInput.dataLuma.append(luma)

            val t: Double =
                    cameraViewModel.cameraInput.experimentTimeReference.getExperimentTimeFromEvent(
                            mediaImage.timestamp)
            cameraViewModel.cameraInput.dataT.append(t)

            cameraViewModel.imageAnalysisFinished(t)
        }
        image.close()
    }

}
