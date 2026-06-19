package de.rwth_aachen.phyphox.camera.helper

import de.rwth_aachen.phyphox.camera.DeviceOrientation
import de.rwth_aachen.phyphox.camera.analyzer.SpectrumOrientation
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

class SpectrumDispersionManager {

    var currentDeviceOrientation: DeviceOrientation = DeviceOrientation.PORTRAIT
        private set

    var currentDispersionOrientation: SpectrumOrientation = SpectrumOrientation.HORIZONTAL_RED_RIGHT
        private set

    private val userSelectedDispersionMap = ConcurrentHashMap<DeviceOrientation, SpectrumOrientation>()

    init {
        userSelectedDispersionMap[DeviceOrientation.PORTRAIT] = SpectrumOrientation.HORIZONTAL_RED_RIGHT
    }

    fun onUserDispersionSelected(chosenDispersion: SpectrumOrientation) {
        userSelectedDispersionMap[currentDeviceOrientation] = chosenDispersion
        currentDispersionOrientation = chosenDispersion
    }

    fun onDeviceRotated(newDeviceOrientation: DeviceOrientation) {
        if (newDeviceOrientation == currentDeviceOrientation) return

        val previousDeviceOrientation = currentDeviceOrientation
        currentDeviceOrientation = newDeviceOrientation

        val baseDispersion = currentDispersionOrientation

        currentDispersionOrientation = when {
            // PORTRAIT -> LANDSCAPE
            previousDeviceOrientation == DeviceOrientation.PORTRAIT && newDeviceOrientation == DeviceOrientation.LANDSCAPE ->
                baseDispersion.rotateClockwise()

            // LANDSCAPE -> PORTRAIT
            previousDeviceOrientation == DeviceOrientation.LANDSCAPE && newDeviceOrientation == DeviceOrientation.PORTRAIT ->
                baseDispersion.rotateCounterClockwise()

            // PORTRAIT -> REVERSE_LANDSCAPE
            previousDeviceOrientation == DeviceOrientation.PORTRAIT && newDeviceOrientation == DeviceOrientation.LANDSCAPE_REVERSE ->
                baseDispersion.rotateCounterClockwise()

            // REVERSE_LANDSCAPE -> PORTRAIT
            previousDeviceOrientation == DeviceOrientation.LANDSCAPE_REVERSE && newDeviceOrientation == DeviceOrientation.PORTRAIT ->
                baseDispersion.rotateClockwise()

            // LANDSCAPE -> REVERSE_LANDSCAPE (a 180-degree turn)
            previousDeviceOrientation == DeviceOrientation.LANDSCAPE && newDeviceOrientation == DeviceOrientation.LANDSCAPE_REVERSE ->
                baseDispersion.rotateClockwise().rotateClockwise()

            // REVERSE_LANDSCAPE -> LANDSCAPE (a 180-degree turn)
            previousDeviceOrientation == DeviceOrientation.LANDSCAPE_REVERSE && newDeviceOrientation == DeviceOrientation.LANDSCAPE ->
                baseDispersion.rotateClockwise().rotateClockwise()

            // Fallback for any other unhandled case
            else -> getHardcodedDefaultFor(newDeviceOrientation)
        }

        // IMPORTANT: Because we just calculated a *new* default, we save it as the preference for this orientation.
        // This makes the behavior consistent if the user rotates away and back again.
        userSelectedDispersionMap[newDeviceOrientation] = currentDispersionOrientation
    }


    private fun getHardcodedDefaultFor(orientation: DeviceOrientation): SpectrumOrientation {
        return when (orientation) {
            DeviceOrientation.PORTRAIT -> SpectrumOrientation.HORIZONTAL_RED_RIGHT
            DeviceOrientation.LANDSCAPE -> SpectrumOrientation.VERTICAL_BLUE_UP
            DeviceOrientation.LANDSCAPE_REVERSE -> SpectrumOrientation.VERTICAL_RED_UP
            DeviceOrientation.PORTRAIT_REVERSE -> SpectrumOrientation.HORIZONTAL_BLUE_RIGHT
        }
    }

}
