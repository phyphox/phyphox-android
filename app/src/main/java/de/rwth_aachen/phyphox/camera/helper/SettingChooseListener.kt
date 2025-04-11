package de.rwth_aachen.phyphox.camera.helper

import de.rwth_aachen.phyphox.camera.ui.ChooseCameraSettingValue


interface SettingChooseListener {
    fun onSettingClicked(value: ChooseCameraSettingValue?)
}
