package de.rwth_aachen.phyphox.camera.helper


import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test


class CameraHelperTest {

    private val TAG = "CameraHelperTest"

    @Test
    fun `compute full zoom ratio`(){
        // Not actual test, but just to print out the values in list
        val zoomRatio = CameraHelper.computeZoomRatios(0.55f, 20.0f)
        assertThat(zoomRatio).isNotEmpty()
    }

    @Test
    fun `compute white balance`(){
        val rggb = CameraHelper.convertTemperatureToRggb(12000)
        System.out.println("red: " +rggb[0])
        System.out.println("green even: " +rggb[1])
        System.out.println("green odd: " +rggb[2])
        System.out.println("blue: " +rggb[3])

    }

}
