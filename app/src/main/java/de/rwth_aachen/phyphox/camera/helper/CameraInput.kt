package de.rwth_aachen.phyphox.camera.helper

import android.util.Range
import de.rwth_aachen.phyphox.camera.model.SettingMode
import de.rwth_aachen.phyphox.DataBuffer
import de.rwth_aachen.phyphox.DataOutput
import de.rwth_aachen.phyphox.ExperimentTimeReference
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

    var currentExposureValue: Int = 0

    var exposureAdjustmentLevel: String = "1"


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
        this.buffers = buffers
        this.cameraSettings = cameraSettings

    }

    enum class CameraExtractionMode {
        average, closest, weighted
    }




}
