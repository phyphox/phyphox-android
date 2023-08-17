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

    private var TAG = "ImageAnalyser"
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
                //analyseLuminosity(image.image!!)
                //analyseImage(image)
                analyseBrightness(image.image!!)
            }
            cameraViewModel.imageAnalysisFinished()
        }

        image.close()
    }

    private fun analyseBrightness(mediaImage: Image) {
        // Image is in YUV format, so first it is converted into RBGA bytearray
        // Since the image.timestamp is only provided in YUV, so right not the format we get is in YUV
        //TODO need to find a better way
        val rgbaData = convertIntArrayToByteArray(convertYUV420ToRGBA(mediaImage))

        val croppedArray = photometricReader.cropByteArray(
            rgbaData,
            cameraViewModel.getCameraRect().width(),
            cameraViewModel.getCameraRect().height(),
            cameraViewModel.getCameraRect()
        )

        //photometricReader.calculateAvgBrightnessFromRGB(croppedArray, cameraViewModel.getCameraRect())
        val brightness =
            photometricReader.calculateAvgBrightness(croppedArray, cameraViewModel.getCameraRect())

        // 63 is the value when there is full dark, so substracting with 60
        val brightnessPercentage = (((brightness - 60.0)/ 255.0) * 100.0)

        Log.d(TAG, "brightnessPercentage: "+brightnessPercentage)

        val t: Double =
            cameraViewModel.cameraInput.experimentTimeReference.getExperimentTimeFromEvent(
                mediaImage.timestamp)

        cameraViewModel.updateImageAnalysisLuminance(brightnessPercentage, t)
        cameraViewModel.cameraInput.dataZ.append(brightnessPercentage)
        cameraViewModel.cameraInput.dataT.append(t)

    }

    private fun convertIntArrayToByteArray(intArray: IntArray): ByteArray {
        val byteArray = ByteArray(intArray.size * 4) // Each element is 4 bytes (RGBA)
        var byteIndex = 0

        for (colorValue in intArray) {
            byteArray[byteIndex++] = (colorValue shr 24 and 0xFF).toByte() // Alpha channel
            byteArray[byteIndex++] = (colorValue shr 16 and 0xFF).toByte() // Red channel
            byteArray[byteIndex++] = (colorValue shr 8 and 0xFF).toByte()  // Green channel
            byteArray[byteIndex++] = (colorValue and 0xFF).toByte()       // Blue channel
        }

        return byteArray
    }

    fun convertYUV420ToRGBA(image: Image): IntArray {
        val width = image.width
        val height = image.height

        val planes = image.planes
        val yuvBytesY = planes[0].buffer // Y plane
        val yuvBytesU = planes[1].buffer // U plane
        val yuvBytesV = planes[2].buffer // V plane

        val strideY = planes[0].rowStride
        val strideU = planes[1].rowStride
        val strideV = planes[2].rowStride

        val pixelStrideU = planes[1].pixelStride
        val pixelStrideV = planes[2].pixelStride

        val rgbData = IntArray(width * height)
        var rgbIndex = 0

        for (y in 0 until height) {
            val offsetY = y * strideY
            val offsetU = (y / 2) * strideU
            val offsetV = (y / 2) * strideV

            for (x in 0 until width) {
                val uvOffset = x / 2 * pixelStrideU + offsetU

                val yValue = (yuvBytesY.get(offsetY + x).toInt() and 0xFF)
                val uValue = (yuvBytesU.get(uvOffset).toInt() and 0xFF)
                val vValue = (yuvBytesV.get(uvOffset).toInt() and 0xFF)

                // YUV to RGB conversion
                val c = yValue - 16
                val d = uValue - 128
                val e = vValue - 128

                val r = (298 * c + 409 * e + 128) shr 8
                val g = (298 * c - 100 * d - 208 * e + 128) shr 8
                val b = (298 * c + 516 * d + 128) shr 8

                val clampedR = r.coerceIn(0, 255)
                val clampedG = g.coerceIn(0, 255)
                val clampedB = b.coerceIn(0, 255)

                rgbData[rgbIndex++] = 0xFF shl 24 or (clampedR shl 16) or (clampedG shl 8) or clampedB
            }
        }

        return rgbData
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyseLuminosity(mediaImage: Image) {

        val rgbaData = convertIntArrayToByteArray(convertYUV420ToRGBA(mediaImage))

        val croppedArray = photometricReader.cropByteArray(
            rgbaData,
            cameraViewModel.getCameraRect().width(),
            cameraViewModel.getCameraRect().height(),
            cameraViewModel.getCameraRect()
        )

        val pixels = croppedArray.map { it.toInt() and 0xFF }
        val luma = pixels.average()

        val t: Double =
            cameraViewModel.cameraInput.experimentTimeReference.getExperimentTimeFromEvent(
                mediaImage.timestamp
            )

        cameraViewModel.updateImageAnalysisLuminance(luma, t)
        cameraViewModel.cameraInput.dataZ.append(luma)
        cameraViewModel.cameraInput.dataT.append(t)

    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun detectColor(image: ImageProxy) {
        val mediaImage = image.image ?: return
        val pixelStride: Int = image.planes.get(0).getPixelStride()

        val buffer1 = mediaImage.planes?.get(0)?.buffer

        val croppedArray = photometricReader.cropByteArray(
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

        // TODO need to fix the retreivel of pixel to show the correct color
        val normalizeRed = redByte.copyOfRange(1, 10).random().toInt() + 128
        val normalizeGreen = greenByte.copyOfRange(1, 10).random().toInt() + 128
        val normalizeBlue = blueByte.copyOfRange(1, 10).random().toInt() + 128

        val colorCode = Integer.toHexString(normalizeRed.and(0xFF)) +
                Integer.toHexString(normalizeGreen.and(0xFF)) +
                Integer.toHexString(normalizeBlue.and(0xFF))

        Log.d("PhotometricReader", "hexcode: $colorCode")

        cameraViewModel.updateImageAnalysisColor(colorCode)

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
