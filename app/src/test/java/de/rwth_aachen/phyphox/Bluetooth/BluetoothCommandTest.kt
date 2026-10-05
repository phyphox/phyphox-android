package de.rwth_aachen.phyphox.Bluetooth

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Vector

// phyphox-test: ble-command-dispatch
//The command characteristic contract (phyphox-docs, bluetooth-low-energy.md, "Phyphox command
// characteristic (0005)"): byte 0 selects the command, further bytes are ignored, unknown codes are
// dropped, and a known one reaches the activity as the corresponding command.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BluetoothCommandTest {

    @Test
    fun everyDocumentedCodeDecodesToItsCommand() {
        assertThat(BluetoothCommand.decode(byteArrayOf(0x00))).isEqualTo(BluetoothCommand.PAUSE)
        assertThat(BluetoothCommand.decode(byteArrayOf(0x01))).isEqualTo(BluetoothCommand.START)
        assertThat(BluetoothCommand.decode(byteArrayOf(0x02))).isEqualTo(BluetoothCommand.TOGGLE)
        assertThat(BluetoothCommand.decode(byteArrayOf(0x10))).isEqualTo(BluetoothCommand.CLEAR)
        assertThat(BluetoothCommand.decode(byteArrayOf(0x11))).isEqualTo(BluetoothCommand.CLEAR_ALL)
        assertThat(BluetoothCommand.decode(byteArrayOf(0xF0.toByte()))).isEqualTo(BluetoothCommand.STATUS)
    }

    @Test
    fun reservedBytesAfterTheCommandAreIgnored() {
        assertThat(BluetoothCommand.decode(byteArrayOf(0x01, 0x7f, 0xff.toByte(), 0x00))).isEqualTo(BluetoothCommand.START)
    }

    @Test
    fun unknownAndEmptyNotificationsDecodeToNothing() {
        //unused codes inside the groups, an unknown group, the event characteristic's SYNC code
        for (code in listOf(0x03, 0x0f, 0x12, 0x20, 0xf1, 0xff))
            assertThat(BluetoothCommand.decode(byteArrayOf(code.toByte()))).isNull()
        assertThat(BluetoothCommand.decode(byteArrayOf())).isNull()
    }

    class CapturingActivity : Activity(), BluetoothCommandDelegate {
        val received = mutableListOf<BluetoothCommand>()
        override fun onBluetoothCommand(command: BluetoothCommand, device: Bluetooth) {
            received.add(command)
        }
    }

    @Test
    fun aNotificationReachesTheActivityAsACommandAndUnknownOnesDoNot() {
        val activity = Robolectric.buildActivity(CapturingActivity::class.java).create().get()
        val device = Bluetooth(null, "sim", null, null, false, activity, activity, Vector())
        device.handleCommand(byteArrayOf(0x01))
        device.handleCommand(byteArrayOf(0x03))
        device.handleCommand(byteArrayOf())
        device.handleCommand(byteArrayOf(0x11, 0x55))
        device.handleCommand(byteArrayOf(0xF0.toByte()))
        assertThat(activity.received).containsExactly(BluetoothCommand.START, BluetoothCommand.CLEAR_ALL, BluetoothCommand.STATUS).inOrder()
    }
}
