package de.rwth_aachen.phyphox.camera.helper

import de.rwth_aachen.phyphox.DataBuffer
import de.rwth_aachen.phyphox.DataOutput
import de.rwth_aachen.phyphox.ExperimentTimeReference
import de.rwth_aachen.phyphox.camera.model.SettingMode
import java.util.Vector
import java.util.concurrent.locks.Lock

class CameraInput() {

    // Represents the ratio of the overlay in camera preview to the actual size of the preview
    var x1: Float = 0f
    var x2: Float = 0f
    var y1: Float = 0f
    var y2: Float = 0f

    // Holds and release the image analysis value (z) and time value (t) from data buffer
    lateinit var dataZ: DataBuffer
    lateinit var dataT: DataBuffer

    // List of buffers (variables) that is provided in the xml
    lateinit var buffers: Vector<DataOutput>

    // List of camera setting that is available to control the camera sensor
    var cameraSettings = ArrayList<SettingMode>()

    // Holds initial value provided in xml
    var isoCurrentValue: Int = 0
    var shutterSpeedCurrentValue: Long = 0
    var apertureCurrentValue: Float = 0.0f
    var autoExposure: Boolean = true
    var currentExposureValue: Float = 0F
    var exposureAdjustmentLevel: String = "1"

    // Status of the play and pause for image analysis
    var measuring = false

    lateinit var experimentTimeReference: ExperimentTimeReference

    constructor(cameraExtractionMode: CameraExtractionMode,
                x1: Float,
                x2: Float,
                y1: Float,
                y2: Float,
                buffers: Vector<DataOutput>,
                lock: Lock,
                experimentTimeReference: ExperimentTimeReference,
                cameraSettings: ArrayList<SettingMode>) : this() {

        this.x1 = x1
        this.x2 = x2
        this.y1 = y1
        this.y2 = y2
        this.cameraSettings = cameraSettings
        this.buffers = buffers
        this.experimentTimeReference = experimentTimeReference

        if (buffers.size > 0 && buffers[0] != null) dataZ = buffers[0].buffer
        if (buffers.size > 1 && buffers[1] != null) dataT = buffers[1].buffer

    }

    enum class CameraExtractionMode {
        Average, Closest, Weighted
    }

    fun start() {
        measuring = true
    }

    fun stop() {
        measuring = false
    }

}
