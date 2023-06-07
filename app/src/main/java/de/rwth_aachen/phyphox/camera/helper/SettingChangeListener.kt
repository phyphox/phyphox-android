package de.rwth_aachen.phyphox.camera.helper

import de.rwth_aachen.phyphox.camera.model.SettingMode

interface SettingChangeListener {

    fun onProgressChange(settingMode: SettingMode, value: Int)

}

interface SettingChooseListener {
    fun onSettingClicked(value: String, position: Int)
}
