package de.rwth_aachen.phyphox.camera.helper

import android.graphics.Rect
import android.media.Image
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class PhotometricReader() {

    val TAG = "PhotometricReader"
    enum class ImageEvaluation {
        AverageLuminosity, RedBrightness, BlueBrightness, GreenBrightness,
        AverageRGB, HueAverage, AverageSaturation, ImageContrast
    }


    fun calculateAverageLuminosity(pixelArray: ByteArray): Double {
        var luminosity = 0.0
        for (i in pixelArray.indices) {
            val pixel =
                (pixelArray[i].toInt() and 0xFF) // Convert the unsigned byte to an integer
            luminosity += pixel.toDouble()
        }
        luminosity /= pixelArray.size.toDouble() // Divide the sum by the number of pixels
        return luminosity
    }

    fun calculateAvgBrightness(byteArray: ByteArray, rect: Rect) : Double{

        val hsv = Mat()
        val rgbImage = Mat()
        val matImg: Mat = CameraHelper.byteArrayToMat(byteArray, rect )

        Imgproc.cvtColor(matImg, rgbImage, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(rgbImage, hsv, Imgproc.COLOR_RGB2HSV)

        val channels = ArrayList<Mat>()
        Core.split(hsv, channels)

        val hueChannel = channels[0]
        val saturationChannel = channels[1]
        val valueChannel = channels[2]
        val meanValue = Core.mean(valueChannel)

        //Log.d(TAG, "heu: " +Core.mean(hueChannel).`val`.get(0))
        //Log.d(TAG, "saturation: " +Core.mean(saturationChannel).`val`.get(0))
        Log.d(TAG, "value: " +Core.mean(valueChannel).`val`.get(0))

        return meanValue.`val`.get(0)

    }

    fun calculateAvgBrightnessFromRGB(byteArray: ByteArray, rect: Rect): Double {
        val hsv = Mat()
        val rgbImage = Mat()
        val matImg: Mat = CameraHelper.byteArrayToMat(byteArray, rect )

        //Imgproc.cvtColor(matImg, rgbImage, Imgproc.COLOR_RGBA2RGB)

        val channels = ArrayList<Mat>()
        Core.split(matImg, channels)

        for (channel in channels){
            Log.d(TAG, "rgb: " +Core.mean(channel).`val`.get(0))
        }
        Log.d(TAG, "gray: ....")
        return 0.0
    }

    fun calculateBrightnessFromGraScale(byteArray: ByteArray, rect: Rect) : Double {

        val hsv = Mat()
        val grayImage = Mat()
        val matImg: Mat = CameraHelper.byteArrayToMat(byteArray, rect )

        Imgproc.cvtColor(matImg, grayImage, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(grayImage, hsv, Imgproc.COLOR_RGB2GRAY)

        val channels = ArrayList<Mat>()
        Core.split(hsv, channels)

        for (channel in channels){
            Log.d(TAG, "gray: " +Core.mean(channel).`val`.get(0))
        }
        Log.d(TAG, "gray: ....")
        return 0.0

    }

    fun calculateAverageRedBrightness(img: Image) : Double{
        val matImg: Mat = CameraHelper.imageToMat(img);

        // Convert the image from BGR to HSV color space
        val hsv = Mat()
        Imgproc.cvtColor(matImg, hsv, Imgproc.COLOR_BGR2HSV)

        // Define range of red color in HSV
        val lowerRed = Scalar(0.0, 50.0, 50.0)
        val upperRed = Scalar(10.0, 255.0, 255.0)
        val mask1 = Mat()
        Core.inRange(hsv, lowerRed, upperRed, mask1)

        val lowerRed2 = Scalar(170.0, 50.0, 50.0)
        val upperRed2 = Scalar(180.0, 255.0, 255.0)
        val mask2 = Mat()
        Core.inRange(hsv, lowerRed2, upperRed2, mask2)

        // Combine the masks
        val mask = Mat()
        Core.bitwise_or(mask1, mask2, mask)

        // Calculate the mean of the red color
        val mean = Core.mean(matImg, mask)
        return mean.`val`[0]
    }

    fun cropByteArray(rgbaArray: ByteArray, width: Int, height: Int, cropRect: Rect): ByteArray {
        val croppedWidth = cropRect.width()
        val croppedHeight = cropRect.height()
        val croppedArray = ByteArray(croppedWidth * croppedHeight * 4) // 4 bytes per pixel (RGBA)
        System.out.println("croppedArray: "+croppedArray.size)
        for (y in 0 until croppedHeight) {
            for (x in 0 until croppedWidth) {
                val croppedIndex =
                    (y * croppedWidth + x) * 4 // Calculate the index in the cropped array
                val originalIndex =
                    ((y + cropRect.top) * width + (x + cropRect.left)) * 4 // Calculate the index in the original array

                // Copy RGBA values from original array to cropped array
                if(rgbaArray.size > originalIndex){
                    croppedArray[croppedIndex] = rgbaArray[originalIndex] // Red
                    croppedArray[croppedIndex + 1] = rgbaArray[originalIndex + 1] // Green
                    croppedArray[croppedIndex + 2] = rgbaArray[originalIndex + 2] // Blue
                    croppedArray[croppedIndex + 3] = rgbaArray[originalIndex + 3] // Alpha
                }

            }
        }

        return croppedArray
    }


}
