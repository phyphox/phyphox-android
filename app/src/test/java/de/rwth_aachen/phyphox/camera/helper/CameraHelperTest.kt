package de.rwth_aachen.phyphox.camera.helper


import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test


class CameraHelperTest {

    private val TAG = "CameraHelperTest"

    @Test
    fun convertNanoSecondToSecond() {
    }

    @Test
    fun shutterSpeedRange() {
    }

    @Test
    fun isoRange() {
    }

    @Test
    fun findIsoNearestNumber() {
    }

    @Test
    fun stringToNanoseconds() {
    }

    @Test
    fun getExposureValuesFromRange() {
    }

    @Test
    fun getActualValueFromExposureCompensation() {
    }

    @Test
    fun `compute full zoom ratio`(){
        // Not actual test, but just to print out the values in list
        val zoomRatio = CameraHelper.computeZoomRatios(0.55f, 20.0f)
        assertThat(zoomRatio).isNotEmpty()
    }
}
