package de.rwth_aachen.phyphox.camera.helper

import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.media.Image.Plane
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import de.rwth_aachen.phyphox.camera.model.ImageAnalysisUIAction
import de.rwth_aachen.phyphox.camera.viewmodel.CameraViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.TermCriteria
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.experimental.and
import kotlin.math.sqrt


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ImageAnalyser(private val cameraViewModel: CameraViewModel) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L
    private var photometricReader: PhotometricReader = PhotometricReader()

    // Observable to observe the action performed
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

    var count = 0
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {

        if (cameraViewModel.cameraInput.measuring) {

            cameraViewModel.imageAnalysisStarted()
            if (cameraViewModel.cameraInput.cameraFeature == CameraInput.PhyphoxCameraFeature.Photometric) {
                analyseImage(image)
            } else if (cameraViewModel.cameraInput.cameraFeature == CameraInput.PhyphoxCameraFeature.ColorDetector) {
                val currentTimestamp = System.currentTimeMillis()
                count += 1
                // Calculate the average luma no more often than every second
                if (currentTimestamp - lastAnalyzedTimestamp >=
                    TimeUnit.SECONDS.toMillis(1)
                ) {

                    detectColor(image)

                    Log.d("PhotometricReader", count.toString())
                    count = 0

                    lastAnalyzedTimestamp = currentTimestamp
                }
            }

            cameraViewModel.imageAnalysisFinished()
        }
        image.close()
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun detectColor(image: ImageProxy) {
        val mediaImage = image.image ?: return
        val pixelStride: Int = image.planes.get(0).getPixelStride()

        val buffer1 = mediaImage.planes?.get(0)?.buffer

        val croppedArray = cropByteArray(
            buffer1?.toByteArray()!!,
            cameraViewModel.getCameraRect().width(),
            cameraViewModel.getCameraRect().height(),
            cameraViewModel.getCameraRect()
        )
        val croppedArraySize = croppedArray.size

        val redByte = ByteArray(croppedArraySize.div(4))
        val greenByte = ByteArray(croppedArraySize.div(4))
        val blueByte = ByteArray(croppedArraySize.div(4))
        val aByte = ByteArray(croppedArraySize.div(4))

        var j = 0
        for (i in 0..croppedArraySize) {

            val pixelOffset: Int = i * pixelStride

            if (pixelOffset >= croppedArraySize) {
                break
            }

            redByte[j] = croppedArray[pixelOffset]
            greenByte[j] = croppedArray[pixelOffset + 1]
            blueByte[j] = croppedArray[pixelOffset + 2]
            aByte[j] = croppedArray[pixelOffset + 3]
            j++

        }

        Log.d("PhotometricReader", "redByte: distinct " + redByte.distinct())
        Log.d("PhotometricReader", "redByte: max " + redByte.max())
        Log.d("PhotometricReader", "redByte: min " + redByte.min())
        Log.d("PhotometricReader", "red average: " + redByte.average())
        Log.d("PhotometricReader", "..................")

        Log.d("PhotometricReader", "greenByte: distinct " + greenByte.distinct())
        Log.d("PhotometricReader", "greenByte: max " + greenByte.max())
        Log.d("PhotometricReader", "greenByte: min " + greenByte.min())
        Log.d("PhotometricReader", "green average: " + greenByte.average())
        Log.d("PhotometricReader", "..................")

        Log.d("PhotometricReader", "blueByte: distinct " + blueByte.distinct())
        Log.d("PhotometricReader", "blueByte: max " + blueByte.max())
        Log.d("PhotometricReader", "blueByte: min " + blueByte.min())
        Log.d("PhotometricReader", "blue average: " + blueByte.average())
        Log.d("PhotometricReader", "..................")

        // TODO need to fix the retreivel of pixel to show the correct color
        val normalizeRed = redByte.copyOfRange(1,10).random().toInt() + 128
        val normalizeGreen = greenByte.copyOfRange(1,10).random().toInt() + 128
        val normalizeBlue = blueByte.copyOfRange(1,10).random().toInt() + 128

        val colorCode = Integer.toHexString(normalizeRed.and(0xFF)) +
                Integer.toHexString(normalizeGreen.and(0xFF)) +
                Integer.toHexString(normalizeBlue.and(0xFF))

        Log.d("PhotometricReader", "hexcode: $colorCode")

        cameraViewModel.updateImageAnalysisColor(colorCode)

    }
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyseImage(image: ImageProxy) {
        val mediaImage = image.image

        val currentTimestamp = System.currentTimeMillis()
        val t: Double =
            cameraViewModel.cameraInput.experimentTimeReference.getExperimentTimeFromEvent(
                mediaImage?.timestamp!!
            )

        // Calculate the average luma no more often than every second
        if (currentTimestamp - lastAnalyzedTimestamp >=
            TimeUnit.SECONDS.toMillis(1)
        ) {
            if (mediaImage.format == ImageFormat.YUV_420_888) {
                // Since format in ImageAnalysis is YUV, image.planes[0]
                // contains the Y (luminance) plane
                val buffer = mediaImage.planes[0].buffer

                val data = croppedNV21(mediaImage, cameraViewModel.getCameraRect())

                val pixels = data.map { it.toInt() and 0xFF }

                Log.d(
                    "CameraXApp",
                    PhotometricReader().calculateAvgRedBrightness(mediaImage).toString()
                )

                // Compute average luminance for the image
                val luma = pixels.average()

                cameraViewModel.updateImageAnalysisLuminance(luma, t)

                cameraViewModel.cameraInput.dataZ.append(luma)
                cameraViewModel.cameraInput.dataT.append(t)

                // Update timestamp of last analyzed frame
                lastAnalyzedTimestamp = currentTimestamp
            }
        }
    }

    private fun cropByteArray(rgbaArray: ByteArray, width: Int, height: Int, cropRect: Rect): ByteArray {
        val croppedWidth = cropRect.width()
        val croppedHeight = cropRect.height()
        val croppedArray = ByteArray(croppedWidth * croppedHeight * 4) // 4 bytes per pixel (RGBA)

        for (y in 0 until croppedHeight) {
            for (x in 0 until croppedWidth) {
                val croppedIndex =
                    (y * croppedWidth + x) * 4 // Calculate the index in the cropped array
                val originalIndex =
                    ((y + cropRect.top) * width + (x + cropRect.left)) * 4 // Calculate the index in the original array

                // Copy RGBA values from original array to cropped array
                croppedArray[croppedIndex] = rgbaArray[originalIndex] // Red
                croppedArray[croppedIndex + 1] = rgbaArray[originalIndex + 1] // Green
                croppedArray[croppedIndex + 2] = rgbaArray[originalIndex + 2] // Blue
                croppedArray[croppedIndex + 3] = rgbaArray[originalIndex + 3] // Alpha
            }
        }

        return croppedArray
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
