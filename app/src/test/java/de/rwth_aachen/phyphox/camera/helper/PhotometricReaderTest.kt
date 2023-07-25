package de.rwth_aachen.phyphox.camera.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import org.junit.Test
import java.io.ByteArrayOutputStream

class PhotometricReaderTest {

    @Test
    fun cropByteArray() {
        val bitmapImage = BitmapFactory.decodeFile("/Users/gauravtripathee/StudioProjects/phyphox-android/app/src/main/res/raw/test_image.jpg")
        val outputStream = ByteArrayOutputStream()
        bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val photoMetricReader = PhotometricReader()
        val cropRect = Rect(406, 480 ,610, 720)
        photoMetricReader.cropByteArray(outputStream.toByteArray(),bitmapImage.height, bitmapImage.width, cropRect)
    }
}
