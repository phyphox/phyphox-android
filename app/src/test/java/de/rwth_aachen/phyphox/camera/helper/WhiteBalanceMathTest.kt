package de.rwth_aachen.phyphox.camera.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

//The colour math behind the camera's white balance by white point (file format 1.21, phyphox-docs
//docs/file-format/input.md "White balance"): temperature and Duv to a chromaticity, and the Bradford
//adaptation that makes the illuminant's white neutral. The sign conventions are the ones that matter:
//a tungsten target turns a daylight-neutral image blue, a positive tint turns it magenta.
class WhiteBalanceMathTest {

    private fun assertClose(message: String, expected: DoubleArray, actual: DoubleArray, tolerance: Double) {
        for (i in expected.indices)
            assertEquals("$message [$i]", expected[i], actual[i], tolerance)
    }

    @Test
    fun planckianLocusMatchesTabulatedPoints() {
        assertClose("6500 K", doubleArrayOf(0.3135, 0.3237), WhiteBalance.planckianXY(6500.0), 0.001)
        assertClose("3200 K", doubleArrayOf(0.4234, 0.3990), WhiteBalance.planckianXY(3200.0), 0.002)
        assertClose("illuminant A", doubleArrayOf(0.4476, 0.4074), WhiteBalance.planckianXY(2856.0), 0.003)
        assertClose("2000 K", doubleArrayOf(0.5267, 0.4133), WhiteBalance.planckianXY(2000.0), 0.003)
    }

    @Test
    fun uvConversionRoundTrips() {
        val xy = doubleArrayOf(0.3127, 0.3290)
        assertClose("xy -> uv -> xy", xy, WhiteBalance.uvToXy(WhiteBalance.xyToUv(xy)), 1e-12)
        assertClose("D65 in uv", doubleArrayOf(0.1978, 0.3122), WhiteBalance.xyToUv(xy), 0.0005)
    }

    @Test
    fun tintIsDuvTowardsGreen() {
        val onLocus = WhiteBalance.xyToUv(WhiteBalance.chromaticity(5500.0, 0.0))
        val green = WhiteBalance.xyToUv(WhiteBalance.chromaticity(5500.0, 0.01))
        val magenta = WhiteBalance.xyToUv(WhiteBalance.chromaticity(5500.0, -0.01))
        val d = sqrt((green[0] - onLocus[0]) * (green[0] - onLocus[0]) + (green[1] - onLocus[1]) * (green[1] - onLocus[1]))
        assertEquals("the offset is the Duv distance", 0.01, d, 1e-6)
        assertTrue("positive Duv lies above the locus", green[1] > onLocus[1])
        assertTrue("negative Duv lies below the locus", magenta[1] < onLocus[1])
        //D65 sits about 0.0032 above the Planckian locus at 6504 K
        assertClose("D65 from 6504 K and Duv 0.0032", WhiteBalance.D65, WhiteBalance.chromaticity(6504.0, 0.0032), 0.0006)
    }

    private fun neutral(temperature: Int, duv: Float): DoubleArray {
        val white = WhiteBalance.illuminantRGB(temperature, duv)
        return WhiteBalance.apply(WhiteBalance.correction(temperature, duv), white)
    }

    @Test
    fun illuminantWhiteBecomesNeutral() {
        for (temperature in intArrayOf(2000, 2850, 3200, 4200, 5500, 6500, 8000, 15000, 25000))
            for (duv in floatArrayOf(0f, 0.01f, -0.01f)) {
                val rgb = neutral(temperature, duv)
                assertTrue("$temperature K, Duv $duv: white stays bright ($rgb)", rgb[0] > 0.3)
                //The tabulated matrices carry seven digits
                assertEquals("$temperature K, Duv $duv: red = green", rgb[0], rgb[1], 1e-5)
                assertEquals("$temperature K, Duv $duv: green = blue", rgb[1], rgb[2], 1e-5)
            }
    }

    @Test
    fun daylightNeutralTurnsBlueForTungstenAndWarmForShade() {
        val white = doubleArrayOf(1.0, 1.0, 1.0)
        val tungsten = WhiteBalance.apply(WhiteBalance.correction(3200, 0f), white)
        assertTrue("3200 K makes daylight white blue: $tungsten", tungsten[2] > tungsten[1] && tungsten[1] > tungsten[0])
        val shade = WhiteBalance.apply(WhiteBalance.correction(8000, 0f), white)
        assertTrue("8000 K makes daylight white warm: $shade", shade[0] > shade[1] && shade[1] > shade[2])
    }

    @Test
    fun positiveTintTurnsNeutralMagenta() {
        val white = doubleArrayOf(1.0, 1.0, 1.0)
        val magenta = WhiteBalance.apply(WhiteBalance.correction(6504, 0.01f), white)
        assertTrue("green is the smallest channel: $magenta", magenta[1] < magenta[0] && magenta[1] < magenta[2])
        val green = WhiteBalance.apply(WhiteBalance.correction(6504, -0.01f), white)
        assertTrue("green is the largest channel: $green", green[1] > green[0] && green[1] > green[2])
    }

    @Test
    fun d65IsCloseToTheIdentity() {
        val m = WhiteBalance.correction(6504, 0.0032f)
        for (i in 0..8)
            assertEquals("entry $i", if (i % 4 == 0) 1.0 else 0.0, m[i], 0.005)
    }

    @Test
    fun adaptationRoundTripIsTheIdentity() {
        val a = WhiteBalance.chromaticity(3200.0, -0.004)
        val b = WhiteBalance.chromaticity(7500.0, 0.006)
        val m = WhiteBalance.multiply(WhiteBalance.adaptation(a, b), WhiteBalance.adaptation(b, a))
        for (i in 0..8)
            assertEquals("entry $i", if (i % 4 == 0) 1.0 else 0.0, m[i], 1e-5)
    }

    @Test
    fun shaderMatrixIsColumnMajor() {
        val m = WhiteBalance.correction(4200, 0.002f)
        val gl = WhiteBalance.shaderMatrix(4200, 0.002f)
        for (row in 0..2)
            for (col in 0..2)
                assertEquals("row $row, column $col", m[3 * row + col], gl[3 * col + row].toDouble(), 1e-6)
        assertTrue("not the identity", abs(gl[8] - 1.0f) > 1e-4)
    }

    @Test
    fun temperaturesOutsideTheLocusApproximationAreClamped() {
        assertClose("below", WhiteBalance.planckianXY(1667.0), WhiteBalance.planckianXY(1000.0), 1e-12)
        assertClose("above", WhiteBalance.planckianXY(25000.0), WhiteBalance.planckianXY(40000.0), 1e-12)
    }
}
