package de.rwth_aachen.phyphox.Bluetooth

import java.io.Serializable
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * The name criteria of a bluetooth element: `name` is a substring test, `nameRegex` has to match the whole
 * device name (rule ble-name-regex). Every criterion given has to hold. Equality is by the two pattern
 * strings, so a filter can key a map.
 */
data class BluetoothNameFilter @Throws(PatternSyntaxException::class) constructor(val name: String?, val regex: String?) : Serializable {

    private val pattern: Pattern? = if (regex.isNullOrEmpty()) null else Pattern.compile(regex)

    val isEmpty: Boolean get() = name.isNullOrEmpty() && pattern == null

    fun matches(deviceName: String): Boolean {
        if (!name.isNullOrEmpty() && !deviceName.contains(name))
            return false
        if (pattern != null && !pattern.matcher(deviceName).matches())
            return false
        return true
    }

    /** The criterion as shown to the user (scan dialog, error messages). */
    val description: String get() = if (!regex.isNullOrEmpty()) regex else name ?: ""

    companion object {
        @JvmField
        val NONE = BluetoothNameFilter(null, null)
    }
}
