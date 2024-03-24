package de.rwth_aachen.phyphox.camera.helper

import android.graphics.Rect
import android.media.Image
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

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
