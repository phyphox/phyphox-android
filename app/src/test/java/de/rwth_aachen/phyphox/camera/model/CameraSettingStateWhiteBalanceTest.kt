package de.rwth_aachen.phyphox.camera.model

import de.rwth_aachen.phyphox.camera.helper.WhiteBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

//The white balance request against what the camera can reach: a temperature outside the range and a tint beyond
//the method's limit are clamped in the value in effect, the request itself is kept; only a temperature on a
//camera without the CCT mode turns on the adaptation in the shaders.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraSettingStateWhiteBalanceTest {

    @Test
    fun temperatureAndTintAreClampedToTheRangeInEffect() {
        val state = CameraSettingState(whiteBalanceMode = WhiteBalanceMode.TEMPERATURE, whiteBalanceTemperature = 100000, whiteBalanceTint = 0.05f)
        val inEffect = state.withWhiteBalanceInEffect()
        assertEquals(WhiteBalance.MAX_TEMPERATURE, inEffect.whiteBalanceTemperatureInEffect)
        assertEquals(WhiteBalance.MAX_TINT, inEffect.whiteBalanceTintInEffect, 1e-9f)
        assertEquals("the request is kept", 100000, inEffect.whiteBalanceTemperature)

        val cct = state.copy(whiteBalanceMethod = WhiteBalanceMethod.CCT, whiteBalanceTemperatureRange = 2000..8000).withWhiteBalanceInEffect()
        assertEquals("the camera's own range on the CCT path", 8000, cct.whiteBalanceTemperatureInEffect)
        assertEquals("the CCT tint limit", WhiteBalance.MAX_TINT_CCT, cct.whiteBalanceTintInEffect, 1e-9f)
        assertEquals(-WhiteBalance.MAX_TINT_CCT, cct.copy(whiteBalanceTint = -1f).withWhiteBalanceInEffect().whiteBalanceTintInEffect, 1e-9f)
    }

    @Test
    fun theAdaptationRunsOnlyForATemperatureWithoutCct() {
        val temperature = CameraSettingState(whiteBalanceMode = WhiteBalanceMode.TEMPERATURE)
        assertTrue(temperature.copy(whiteBalanceMethod = WhiteBalanceMethod.DAYLIGHT_ANCHOR).whiteBalanceCorrectionActive())
        assertTrue(temperature.copy(whiteBalanceMethod = WhiteBalanceMethod.AWB_LOCK).whiteBalanceCorrectionActive())
        assertFalse(temperature.copy(whiteBalanceMethod = WhiteBalanceMethod.CCT).whiteBalanceCorrectionActive())
        assertFalse(CameraSettingState(whiteBalanceMode = WhiteBalanceMode.LOCKED).whiteBalanceCorrectionActive())
        assertFalse(CameraSettingState().whiteBalanceCorrectionActive())
    }
}
