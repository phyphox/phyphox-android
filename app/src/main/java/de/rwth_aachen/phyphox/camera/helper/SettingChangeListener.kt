package de.rwth_aachen.phyphox.camera.helper

import de.rwth_aachen.phyphox.camera.model.ExposureSettingMode

interface SettingChangeListener {

    fun onProgressChange(settingMode: ExposureSettingMode, value: Int)

}

interface SettingChooseListener {
    fun onSettingClicked(value: String)
}
