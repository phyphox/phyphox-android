package de.rwth_aachen.phyphox.camera.helper

import de.rwth_aachen.phyphox.camera.CameraInput
import org.junit.Test

class CameraInputTest{

    @Test
    fun `parse the camera setting locked string to map`(){
        val valueSingleSetting = CameraInput().setDefaultCameraSettingValueIfAvailable("shutter_speed=1/60")
        val valueIsoSettingsOneWithOutDefault = CameraInput().setDefaultCameraSettingValueIfAvailable("shutter_speed=1/60, iso")
        val valueTwoSetting = CameraInput().setDefaultCameraSettingValueIfAvailable("shutter_speed=1/60, iso= 100")
        val valueUnknownString = CameraInput().setDefaultCameraSettingValueIfAvailable("s")
        val valueEmpty = CameraInput().setDefaultCameraSettingValueIfAvailable("")


    }

}
