package de.rwth_aachen.phyphox.camera.helper

import android.util.Log
import de.rwth_aachen.phyphox.DataBuffer
import de.rwth_aachen.phyphox.DataOutput
import de.rwth_aachen.phyphox.ExperimentTimeReference
import de.rwth_aachen.phyphox.camera.model.SettingMode
import java.util.Vector
import java.util.concurrent.locks.Lock

class CameraInput() {

    var x1: Float = 0.4f
    var x2: Float = 0.6f
    var y1: Float = 0.4f
    var y2: Float = 0.6f

    var w = 0
    var h = 0

    lateinit var dataZ: DataBuffer
    lateinit var dataT: DataBuffer

    lateinit var buffers: Vector<DataOutput>

    var cameraSettings = ArrayList<SettingMode>()

    var isoCurrentValue: Int = 0

    var shutterSpeedCurrentValue: Long = 0

    var apertureCurrentValue: Float = 0.0f

    var autoExposure: Boolean = true

    var currentExposureValue: Float = 0F

    var exposureAdjustmentLevel: String = "1"

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

        this.experimentTimeReference = experimentTimeReference

        //Store the buffer references if any

        //Store the buffer references if any
        if (buffers == null) return

        if (buffers.size > 0 && buffers[0] != null) dataZ = buffers[0].buffer
        if (buffers.size > 1 && buffers[1] != null) dataT = buffers[1].buffer


    }

    enum class CameraExtractionMode {
        average, closest, weighted
    }


    fun start() {

        measuring = true
    }

    fun stop() {
        measuring = false
    }



}
