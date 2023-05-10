package de.rwth_aachen.phyphox.Camera

import android.media.Image
import android.os.Build
import androidx.annotation.RequiresApi
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class PhotometricReader(private val imageEvaluation: ImageEvaluation) {

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

    fun calculateAvgRedBrightness(img: Image) : Double{
        // Convert BGR to HSV color space
        val hsv = Mat()
        val matImg: Mat = CameraHelper.imageToMat(img);
        //Imgproc.cvtColor(matImg, hsv, Imgproc.COLOR_BGR2HSV)

        // Split HSV channels
        val channels = ArrayList<Mat>()
        Core.split(matImg, channels)

        // Extract the red channel
        val redChannel = channels[0]
        val greenChannel = channels[1]
        val blueChannel = channels[2]

        // Calculate the average brightness of the red channel
        val redBrightness = Core.mean(redChannel)
        val greenBrightness = Core.mean(greenChannel)
        val blueBrightness = Core.mean(blueChannel)

        // Print the average red brightness
        println("Average red brightness: ${redBrightness}")
        println("Average green brightness: ${greenBrightness}")
        println("Average blue brightness: ${blueBrightness}")

        return redBrightness.`val`[0]
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


}
