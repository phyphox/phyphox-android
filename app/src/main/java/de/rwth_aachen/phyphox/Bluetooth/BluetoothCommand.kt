package de.rwth_aachen.phyphox.Bluetooth

/**
 * A command a device sends on the phyphox command characteristic (cddf0005): byte 0 of a
 * notification, further bytes are reserved and ignored. The upper nibble groups the commands
 * (0x0 measurement state, 0x1 clearing, 0xF queries) so each group can grow; a code the app does
 * not know is ignored. Specified in phyphox-docs, bluetooth-low-energy.md, "Phyphox command
 * characteristic (0005)".
 */
enum class BluetoothCommand(val code: Int) {
    PAUSE(0x00),
    START(0x01),
    TOGGLE(0x02),
    CLEAR(0x10),
    CLEAR_ALL(0x11),
    STATUS(0xF0);

    companion object {
        /** The command in a notification, or null for an empty notification or an unknown code */
        @JvmStatic
        fun decode(data: ByteArray): BluetoothCommand? {
            if (data.isEmpty())
                return null
            val code = data[0].toInt() and 0xff
            return values().firstOrNull { it.code == code }
        }
    }
}

/**
 * Implemented by the experiment activity: carries out a command from a device as if the user had
 * used the corresponding control of the app. Called on a BLE thread.
 */
interface BluetoothCommandDelegate {
    fun onBluetoothCommand(command: BluetoothCommand, device: Bluetooth)
}
