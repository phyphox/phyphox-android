package de.rwth_aachen.phyphox.camera.helper

import android.os.Build
import androidx.annotation.RequiresApi
import de.rwth_aachen.phyphox.DataBuffer
import de.rwth_aachen.phyphox.DataOutput
import de.rwth_aachen.phyphox.ExperimentTimeReference
import java.util.Vector
import java.util.concurrent.locks.Lock
import kotlin.properties.Delegates

/*
* Takes the essential input from the PhyphoxExperiment which are provided from XML.
* */
class CameraInput() {

    // Represents the ratio of the overlay in camera preview to the actual size of the preview
    var x1: Float = 0f
    var x2: Float = 0f
    var y1: Float = 0f
    var y2: Float = 0f

    // Holds and release the image analysis value (z) and time value (t) from data buffer
    var dataLuma: DataBuffer? = null
    var dataLuminance: DataBuffer? = null
    var dataT: DataBuffer? = null
    var shutterSpeedDataBuffer: DataBuffer? = null
    var isoDataBuffer: DataBuffer? = null
    var apertureDataBuffer: DataBuffer? = null
    var exposureDataBuffer: DataBuffer? = null
    var sensorPixelHeight: DataBuffer? = null
    var sensorPixelWidth: DataBuffer? = null

    var analysisRequiresColor = false
    var analysisRequiresRGB = false
    var analysisRequiresLinearRGB = false

    // List of buffers (variables) that is provided in the xml
    lateinit var buffers: Vector<DataOutput>

    var showControls: PhyphoxShowCameraControls = PhyphoxShowCameraControls.FullViewOnly
    var autoExposure: Boolean = true

    var isoCurrentValue: Int = 100
    var shutterSpeedCurrentValue by Delegates.notNull<Long>()
    var apertureCurrentValue: Float = 1.0f
    var currentExposureValue: Float = 0.0f
    var exposureAdjustmentLevel: Int = 1
    lateinit var cameraFeature: PhyphoxCameraFeature
    var lockedSettings: MutableMap<String, String>? = mutableMapOf()

    // Status of the play and pause for image analysis
    var measuring = false

    lateinit var experimentTimeReference: ExperimentTimeReference

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
        x1: Float,
        x2: Float,
        y1: Float,
        y2: Float,
        buffers: Vector<DataOutput>,
        lock: Lock,
        experimentTimeReference: ExperimentTimeReference,
        showCameraControls: PhyphoxShowCameraControls,
        cameraFeature: PhyphoxCameraFeature,
        autoExposure: Boolean,
        exposureAdjustmentLevel: Int,
        lockedSettings: String?
    ) : this() {

        this.x1 = x1
        this.x2 = x2
        this.y1 = y1
        this.y2 = y2
        this.buffers = buffers
        this.experimentTimeReference = experimentTimeReference
        this.showControls = showCameraControls
        this.cameraFeature = cameraFeature
        this.autoExposure = autoExposure
        this.exposureAdjustmentLevel = exposureAdjustmentLevel

        if (buffers.size > 0 && buffers[0] != null) dataLuma = buffers[0].buffer
        if (buffers.size > 1 && buffers[1] != null) dataLuminance = buffers[1].buffer
        if (buffers.size > 2 && buffers[2] != null) dataT = buffers[2].buffer

        if (buffers.size > 3 && buffers[3] != null) shutterSpeedDataBuffer = buffers[3].buffer
        if (buffers.size > 4 && buffers[4] != null) isoDataBuffer = buffers[4].buffer
        if (buffers.size > 5 && buffers[5] != null) apertureDataBuffer = buffers[5].buffer
        if (buffers.size > 6 && buffers[6] != null) exposureDataBuffer = buffers[6].buffer

        if (buffers.size > 7 && buffers[7] != null) sensorPixelHeight = buffers[7].buffer
        if (buffers.size > 8 && buffers[8] != null) sensorPixelWidth = buffers[8].buffer

        if (dataLuminance != null) {
            analysisRequiresColor = true
            analysisRequiresRGB = true
            analysisRequiresLinearRGB = true
        }

        setDefaultCameraSettingValueIfAvailable(lockedSettings)

    }

    enum class PhyphoxCameraFeature {
        Photometric, ColorDetector, Spectroscopy, MotionAnalysis, OCR
    }

    enum class  PhyphoxShowCameraControls {
        Always, Never, FullViewOnly
    }

    fun start() {
        measuring = true
    }

    fun stop() {
        measuring = false
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun setDefaultCameraSettingValueIfAvailable(setting: String?) {
        parseMapFromString(setting)

        shutterSpeedCurrentValue = CameraHelper.stringToNanoseconds("1/60")

        lockedSettings?.get("iso")?.takeIf(String::isNotEmpty)?.toIntOrNull()?.let {
            isoCurrentValue = it
        }

        lockedSettings?.get("shutter_speed")?.takeIf(String::isNotEmpty)?.let {
            shutterSpeedCurrentValue = CameraHelper.stringToNanoseconds(it)
        }

        lockedSettings?.get("exposure")?.takeIf(String::isNotEmpty)?.toFloatOrNull()?.let {
            currentExposureValue = it
        }

    }

    /*
   * Input as String = "shutter_speed=1/60, iso=100"
   * Output as Map<String,String> = {shutter_speed = 1/60, iso = 100}
   * */
    private fun parseMapFromString(setting: String?) {
        val lockedSettingsChar = setting?.split(",")
        lockedSettingsChar?.let { chars ->
            for (pair in chars) {
                // User might not provide the default value
                if (!pair.contains("=")) {
                    this.lockedSettings?.set(key = pair.trim(), value = "")
                } else {
                    val (key, value) = pair.split("=")
                    this.lockedSettings?.set(key.trim(), value.trim())
                }
            }
        }
    }

}
