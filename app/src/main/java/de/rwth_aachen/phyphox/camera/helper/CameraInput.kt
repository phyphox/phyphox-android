package de.rwth_aachen.phyphox.camera.helper

import android.os.Build
import androidx.annotation.RequiresApi
import de.rwth_aachen.phyphox.DataBuffer
import de.rwth_aachen.phyphox.DataOutput
import de.rwth_aachen.phyphox.ExperimentTimeReference
import de.rwth_aachen.phyphox.camera.model.ExposureSettingMode
import java.util.Vector
import java.util.concurrent.locks.Lock
import kotlin.properties.Delegates

class CameraInput() {

    // Represents the ratio of the overlay in camera preview to the actual size of the preview
    var x1: Float = 0f
    var x2: Float = 0f
    var y1: Float = 0f
    var y2: Float = 0f

    // Holds and release the image analysis value (z) and time value (t) from data buffer
    lateinit var dataZ: DataBuffer
    lateinit var dataT: DataBuffer
    lateinit var shutterSpeedDataBuffer: DataBuffer
    lateinit var isoDataBuffer: DataBuffer
    lateinit var apertureDataBuffer: DataBuffer
    lateinit var exposureDataBuffer: DataBuffer

    // List of buffers (variables) that is provided in the xml
    lateinit var buffers: Vector<DataOutput>

    // List of camera setting that is available to control the camera sensor
    var cameraSettings = ArrayList<ExposureSettingMode>()

    var autoExposure: Boolean = true

    var isoCurrentValue: Int = 100
    var shutterSpeedCurrentValue by Delegates.notNull<Long>()
    var apertureCurrentValue: Float = 1.0f
    var currentExposureValue: Float = 0.0f
    var exposureAdjustmentLevel: Int = 1
    lateinit var cameraFeature : PhyphoxCameraFeature
    var lockedSettings: MutableMap<String,String>? = mutableMapOf()

    // Status of the play and pause for image analysis
    var measuring = false

    lateinit var experimentTimeReference: ExperimentTimeReference

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(x1: Float,
                x2: Float,
                y1: Float,
                y2: Float,
                buffers: Vector<DataOutput>,
                lock: Lock,
                experimentTimeReference: ExperimentTimeReference,
                cameraFeature: PhyphoxCameraFeature,
                autoExposure: Boolean,
                exposureAdjustmentLevel: Int,
                lockedSettings: String?) : this() {

        this.x1 = x1
        this.x2 = x2
        this.y1 = y1
        this.y2 = y2
        this.buffers = buffers
        this.experimentTimeReference = experimentTimeReference
        this.cameraFeature = cameraFeature
        this.autoExposure = autoExposure
        this.exposureAdjustmentLevel = exposureAdjustmentLevel

        // convert string -> "shutter=1/60,exposure=0.0" to map
        val lockedSettingsChar = lockedSettings?.split(",")
        lockedSettingsChar?.let { chars ->
            for(pair in chars){
                val (key, value) = pair.split("=")
                this.lockedSettings?.set(key.trim(), value.trim())
            }
        }


        /*
        * Lock the camera settings:
        * case 1: can be empty or the element is not there at all
        * case 2: can be one or 4 settings (for now fixed)
        * case 3: values can be empty so need to handle it as well
        * case 4: should handle - shutter_speed, aperture(?), iso, exposure,
        *
        * */


        shutterSpeedCurrentValue = CameraHelper.stringToNanoseconds("1/60")

        if (buffers.size > 0 && buffers[0] != null) dataZ = buffers[0].buffer
        if (buffers.size > 1 && buffers[1] != null) dataT = buffers[1].buffer

        if (buffers.size > 2 && buffers[2] != null) shutterSpeedDataBuffer = buffers[2].buffer
        if (buffers.size > 3 && buffers[3] != null) isoDataBuffer = buffers[3].buffer
        if (buffers.size > 4 && buffers[4] != null) apertureDataBuffer = buffers[4].buffer
        if (buffers.size > 5 && buffers[5] != null) exposureDataBuffer = buffers[5].buffer

    }

    enum class PhyphoxCameraFeature {
        Photometric, ColorDetector, Spectroscopy, MotionAnalysis, OCR
    }

    fun start() {
        measuring = true
    }

    fun stop() {
        measuring = false
    }

}
