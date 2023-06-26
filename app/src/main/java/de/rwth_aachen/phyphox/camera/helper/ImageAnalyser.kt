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
class ImageAnalyser(val cameraViewModel: CameraViewModel) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L

    // observable to observe the action performed
    private val _action: MutableSharedFlow<ImageAnalysisUIAction> = MutableSharedFlow()
    val action: Flow<ImageAnalysisUIAction> = _action

    /**
     * Helper extension function used to extract a byte array from an
     * image plane buffer
     */
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {

        if(cameraViewModel.cameraInput.measuring){

            cameraViewModel.imageAnalysisStarted()
            val mediaImage = image.image
            image.imageInfo.rotationDegrees
            image.setCropRect(
                Rect(
                    cameraViewModel.getCameraRect().left,
                    cameraViewModel.getCameraRect().top,
                    cameraViewModel.getCameraRect().right,
                    cameraViewModel.getCameraRect().bottom
                )
            )

            val currentTimestamp = System.currentTimeMillis()
            val t: Double = cameraViewModel.cameraInput.experimentTimeReference.getExperimentTimeFromEvent(currentTimestamp)

            // Calculate the average luma no more often than every second
            if (currentTimestamp - lastAnalyzedTimestamp >=
                TimeUnit.SECONDS.toMillis(1)
            ) {
                if (mediaImage != null && mediaImage.format == ImageFormat.YUV_420_888) {
                    // Since format in ImageAnalysis is YUV, image.planes[0]
                    // contains the Y (luminance) plane
                    val buffer = mediaImage.planes[0].buffer

                    val data = croppedNV21(mediaImage, cameraViewModel.getCameraRect())

                    val pixels = data.map { it.toInt() and 0xFF }
                    Log.d("CameraXApp", "Pixel Size: ${pixels.size}")

                    // Compute average luminance for the image
                    val luma = pixels.average()

                    cameraViewModel.updateImageAnalysisLuminance(luma, t)

                    if (cameraViewModel.cameraInput.dataZ != null) cameraViewModel.cameraInput.dataZ.append(luma) //Given in millimeters, but phyphox uses meter

                    if (cameraViewModel.cameraInput.dataT != null) {
                        cameraViewModel.cameraInput.dataT.append(t)
                    }

                    // Update timestamp of last analyzed frame
                    lastAnalyzedTimestamp = currentTimestamp
                }
            }
            cameraViewModel.imageAnalysisFinished()
        }
        image.close()
    }

    // TODO check the avaibility and access of different frame rates

    // TODO look for OpenGL

    // from here https://stackoverflow.com/questions/63390243/is-there-a-way-to-crop-image-imageproxy-before-passing-to-mlkits-analyzer
    private fun croppedNV21(mediaImage: Image, cropRect: Rect): ByteArray {
        val yBuffer = mediaImage.planes[0].buffer // Y
        val vuBuffer = mediaImage.planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        return cropByteArray(nv21, mediaImage.width, cropRect)

    }

    private fun cropByteArray(array: ByteArray, imageWidth: Int, cropRect: Rect): ByteArray {
        val croppedArray = ByteArray(cropRect.width() * cropRect.height())
        var i = 0
        array.forEachIndexed { index, byte ->
            val x = index % imageWidth
            val y = index / imageWidth

            if (cropRect.left <= x && x < cropRect.right && cropRect.top <= y && y < cropRect.bottom) {
                croppedArray[i] = byte
                i++
            }
        }

        return croppedArray
    }

}
