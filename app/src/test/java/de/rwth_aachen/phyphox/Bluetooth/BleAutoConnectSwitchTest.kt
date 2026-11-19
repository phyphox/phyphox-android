package de.rwth_aachen.phyphox.Bluetooth

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import de.rwth_aachen.phyphox.helper.DebugSwitches
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Vector

// phyphox-test: ble-shipped-sensors
//The lab's debug.phyphox.bleAutoConnect switch makes every bluetooth block (input and output) take the first
// matching device without the pick dialog, as if it had autoConnect="true".
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BleAutoConnectSwitchTest {

    private val originalReader = DebugSwitches.propertyReader

    @After
    fun restoreReader() {
        DebugSwitches.propertyReader = originalReader
    }

    private fun setProperty(value: String) {
        DebugSwitches.propertyReader = DebugSwitches.PropertyReader { if (it == "debug.phyphox.bleAutoConnect") value else "" }
    }

    private fun output(autoConnect: Boolean): Bluetooth {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return BluetoothOutput(null, BluetoothNameFilter("phyphox", null), null, null, autoConnect,
                activity, activity, Vector(), Vector())
    }

    @Test
    fun withoutThePropertyTheFileDecides() {
        setProperty("")
        assertThat(output(false).effectiveAutoConnect).isFalse()
        assertThat(output(true).effectiveAutoConnect).isTrue()
    }

    @Test
    fun thePropertyForcesAutoConnect() {
        for (value in listOf("1", "true", "TRUE")) {
            setProperty(value)
            assertThat(output(false).effectiveAutoConnect).isTrue()
        }
    }

    @Test
    fun otherValuesLeaveItOff() {
        for (value in listOf("0", "false", "yes")) {
            setProperty(value)
            assertThat(output(false).effectiveAutoConnect).isFalse()
        }
    }

    @Test
    fun theSwitchIsReadOnEveryCall() {
        val bluetooth = output(false)
        setProperty("1")
        assertThat(bluetooth.effectiveAutoConnect).isTrue()
        setProperty("")
        assertThat(bluetooth.effectiveAutoConnect).isFalse()
    }
}
