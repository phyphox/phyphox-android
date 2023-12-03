package de.rwth_aachen.phyphox.camera.helper

import de.rwth_aachen.phyphox.camera.model.CameraSettingMode

interface SettingChangeListener {
    fun onProgressChange(settingMode: CameraSettingMode, value: Int)

}

interface SettingChooseListener {
    fun onSettingClicked(value: String)
}
