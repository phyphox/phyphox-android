package de.rwth_aachen.phyphox.camera.helper

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageProxy
import de.rwth_aachen.phyphox.camera.model.ExposureSettingMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.util.Arrays
import kotlin.math.pow
import kotlin.math.round


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
object CameraHelper {
    private var cameraList: MutableMap<String, CameraCharacteristics>? = null
    @JvmField
    val EXPERIMENT_ARG = "experiment"

    @JvmStatic
    fun updateCameraList(cm: CameraManager) {
        cameraList = HashMap()
        try {
            for (cameraId in cm.cameraIdList) {
                try {
                    (cameraList as HashMap<String, CameraCharacteristics>)[cameraId] = cm.getCameraCharacteristics(cameraId)
                } catch (e: CameraAccessException) {
                    //If a single camera is unavailable, skip it.
                }
            }
        } catch (e: CameraAccessException) {
            //That's it. If no camera is available, the list shall remain empty
        }
    }

    @JvmStatic
    fun getCameraList(): Map<String, CameraCharacteristics>? {
        return cameraList
    }

    fun facingConstToString(c: Int): String {
        when (c) {
            CameraCharacteristics.LENS_FACING_FRONT -> return "LENS_FACING_FRONT"
            CameraCharacteristics.LENS_FACING_BACK -> return "LENS_FACING_BACK"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> return "LENS_FACING_EXTERNAL"
        }
        return c.toString()
    }

    fun hardwareLevelConstToString(c: Int): String {
        when (c) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> return "HARDWARE_LEVEL_LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> return "HARDWARE_LEVEL_FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> return "HARDWARE_LEVEL_LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> return "HARDWARE_LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> return "HARDWARE_LEVEL_EXTERNAL"
        }
        return c.toString()
    }

    fun capabilityConstToString(c: Int): String {
        when (c) {
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> return "CAPABILITIES_BACKWARD_COMPATIBLE"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> return "CAPABILITIES_MANUAL_SENSOR"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> return "CAPABILITIES_MANUAL_POST_PROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> return "CAPABILITIES_RAW"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> return "CAPABILITIES_PRIVATE_REPROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> return "CAPABILITIES_READ_SENSOR_SETTINGS"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> return "CAPABILITIES_BURST_CAPTURE"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> return "CAPABILITIES_YUV_REPROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> return "CAPABILITIES_DEPTH_OUTPUT"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> return "CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> return "CAPABILITIES_MOTION_TRACKING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> return "CAPABILITIES_LOGICAL_MULTI_CAMERA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> return "CAPABILITIES_MONOCHROME"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> return "CAPABILITIES_SECURE_IMAGE_DATA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> return "CAPABILITIES_SYSTEM_CAMERA"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING -> return "CAPABILITIES_OFFLINE_PROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR -> return "CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING -> return "CAPABILITIES_REMOSAIC_REPROCESSING"
        }
        return c.toString()
    }

    @JvmStatic
    fun getCamera2FormattedCaps(full: Boolean): String {
        val json = JSONArray()
        for ((key1, value) in cameraList!!) {
            val jsonCam = JSONObject()
            try {
                jsonCam.put("id", key1)
                jsonCam.put(
                    "facing",
                    facingConstToString(value.get(CameraCharacteristics.LENS_FACING)!!)
                )
                jsonCam.put(
                    "hardwareLevel",
                    hardwareLevelConstToString(value.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!)
                )
                val caps = value.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                val jsonCaps = JSONArray()
                if (caps != null) {
                    for (cap in caps) jsonCaps.put(capabilityConstToString(cap))
                }
                jsonCam.put("capabilities", jsonCaps)
                if (full) {
                    val jsonCapRequestKeys = JSONArray()
                    val captureRequestKeys = value.availableCaptureRequestKeys
                    if (captureRequestKeys != null) {
                        for (key in captureRequestKeys) {
                            jsonCapRequestKeys.put(key.name)
                        }
                    }
                    jsonCam.put("captureRequestKeys", jsonCapRequestKeys)
                    val jsonCapResultKeys = JSONArray()
                    val captureResultKeys = value.availableCaptureResultKeys
                    if (captureResultKeys != null) {
                        for (key in captureResultKeys) {
                            jsonCapResultKeys.put(key.name)
                        }
                    }
                    jsonCam.put("captureResultKeys", jsonCapRequestKeys)
                    val jsonFpsRanges = JSONArray()
                    val fpsRanges =
                        value.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!
                    for (fpsRange in fpsRanges) {
                        val jsonFpsRange = JSONObject()
                        jsonFpsRange.put("min", fpsRange.lower)
                        jsonFpsRange.put("max", fpsRange.upper)
                        jsonFpsRanges.put(jsonFpsRange)
                    }
                    jsonCam.put("fpsRanges", jsonFpsRanges)
                    val jsonPhysicalCamIds = JSONArray()
                    var physicalCamIds: Set<String?>
                    physicalCamIds =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) value.physicalCameraIds else HashSet()
                    for (physicalCamId in physicalCamIds) {
                        jsonPhysicalCamIds.put(physicalCamId)
                    }
                    jsonCam.put("physicalCamIds", jsonPhysicalCamIds)
                    val jsonStreamConfigs = JSONArray()
                    val configMap = value.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val formats = configMap!!.outputFormats
                    for (format in formats) {
                        val jsonFormat = JSONObject()
                        jsonFormat.put("format", format)
                        val jsonSizes = JSONArray()
                        val sizes = configMap.getOutputSizes(format)
                        for (size in sizes) {
                            val jsonSize = JSONObject()
                            jsonSize.put("w", size.width)
                            jsonSize.put("h", size.height)
                            jsonSizes.put(jsonSize)
                        }
                        jsonFormat.put("outputSizes", jsonSizes)
                        val jsonHighspeed = JSONArray()
                        val highSpeedVideoSizes = configMap.highSpeedVideoSizes
                        for (size in highSpeedVideoSizes) {
                            val jsonSize = JSONObject()
                            jsonSize.put("w", size.width)
                            jsonSize.put("h", size.height)
                            val jsonHighSpeedVideoFpsRanges = JSONArray()
                            val highSpeedVideoFpsRange =
                                configMap.getHighSpeedVideoFpsRangesFor(size)
                            for (fpsRange in highSpeedVideoFpsRange) {
                                val jsonFpsRange = JSONObject()
                                jsonFpsRange.put("min", fpsRange.lower)
                                jsonFpsRange.put("max", fpsRange.upper)
                                jsonHighSpeedVideoFpsRanges.put(jsonFpsRange)
                            }
                            jsonSize.put("fpsRanges", jsonHighSpeedVideoFpsRanges)
                            jsonHighspeed.put(jsonSize)
                        }
                        jsonFormat.put("highspeed", jsonHighspeed)
                        jsonStreamConfigs.put(jsonFormat)
                    }
                    jsonCam.put("streamConfigurations", jsonStreamConfigs)
                }
                json.put(jsonCam)
            } catch (e: JSONException) {
                try {
                    jsonCam.put("error", e.message)
                } catch (e2: JSONException) {
                    Log.e(
                        "CameraHelper",
                        "Severe JSON error when creating camera2api caps string: " + e2.message
                    )
                }
            }
        }
        return json.toString()
    }

    fun byteArrayToMat(byteArray: ByteArray, cameraRect: Rect) : Mat{

        val width = cameraRect.width()
        val height = cameraRect.height()

        val mat = Mat(height , width, CvType.CV_8UC1)
        mat.put(0, 0, byteArray)
        //Imgproc.cvtColor(mat, mat, Imgproc.COLOR_YUV2RGBA_YV12)

        return mat
    }

    fun imageToMat(image: Image): Mat {
        // Get the image dimensions and format
        val width = image.width
        val height = image.height
        val format = image.format

        // Convert the Image to a byte array
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Create a Mat object from the byte array
        val mat: Mat
        when (format) {
            ImageFormat.YUV_420_888 -> {
                mat = Mat(height + height / 2, width, CvType.CV_8UC1)
                mat.put(0, 0, bytes)
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_YUV2RGBA_NV21)
            }
            ImageFormat.YUV_422_888 -> {
                mat = Mat(height, width, CvType.CV_8UC2)
                mat.put(0, 0, bytes)
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_YUV2RGBA_YUY2)
            }
            ImageFormat.YUV_444_888 -> {
                mat = Mat(height, width, CvType.CV_8UC3)
                mat.put(0, 0, bytes)
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_YUV2RGB_YUY2)
            }
            else -> throw IllegalArgumentException("Unsupported image format: $format")
        }

        return mat
    }

    fun convertMatToImageProxy(mat: Mat?, image: ImageProxy): ImageProxy {
        val rgba = Mat()
        Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_GRAY2RGBA)
        val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bitmap)
        val planes = image.image!!.planes
        val buffer = planes[0].buffer
        buffer.rewind()
        bitmap.copyPixelsToBuffer(buffer)
        return image
    }

    /**
     * Convert the array-like string to arrayList
     * For eg: from "[a,b,c]" to [SettingMode.Iso,SettingMode.aperture,SettingMode.shutterSpeed]
     */
    @JvmStatic
    fun convertInputSettingToSettingMode(inputString: String): ArrayList<ExposureSettingMode> {
        // Remove the square brackets from the input string
        val content = inputString.substring(1, inputString.length - 1)

        // Split the string by comma and trim the elements
        val elements =
            content.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        for (i in elements.indices) {
            elements[i] = elements[i].trim { it <= ' ' }
        }

        val availableCameraSettings = ArrayList(Arrays.asList(*elements))
        val availableSettingModes: ArrayList<ExposureSettingMode> = ArrayList()
        for (cameraSetting in availableCameraSettings) {
            when (cameraSetting) {
                "iso" -> availableSettingModes.add(ExposureSettingMode.ISO)
                "shutter speed" -> availableSettingModes.add(ExposureSettingMode.SHUTTER_SPEED)
                "aperture" -> availableSettingModes.add(ExposureSettingMode.APERTURE)
            }
        }

        return availableSettingModes;
    }

    data class Fraction(val numerator: Long, val denominator: Long)


    fun BigDecimal.toFraction(): Fraction {
        var num = this
        var den = BigDecimal.ONE
        var maxDenominator = BigDecimal(10).pow(12) // Set the maximum denominator

        while (num.remainder(BigDecimal.ONE).abs() > BigDecimal.ZERO) {
            num *= BigDecimal(10)
            den *= BigDecimal(10)
            if (den > maxDenominator)
                break
        }

        val gcd = num.toBigInteger().gcd(den.toBigInteger())
        num /= BigDecimal(gcd)
        den /= BigDecimal(gcd)

        return Fraction(num.toLong(), den.toLong())
    }

    fun convertNanoSecondToSecond(value: Long) : Fraction {
        val seconds = BigDecimal(value).divide(BigDecimal.valueOf(1_000_000_000), 10, RoundingMode.HALF_UP)
        val fraction = seconds.toFraction()
        var denominator = fraction.denominator / fraction.numerator
        if (denominator >= 1_000L) {
            denominator = (denominator.toDouble() / 1000).toLong() * 1000
        }
        return Fraction(1, denominator)
    }

    private fun fractionToNanoseconds(fraction: Fraction): Long{
        val numerator = fraction.numerator * 1_000_000_000L
        val denominator = fraction.denominator
        return numerator / denominator
    }

    // TODO create the range mathematically and in loop
    fun shutterSpeedRange(min: Long, max: Long) : List<Fraction>{
        val shutterSpeedRange = listOf<Fraction>(
            Fraction(1, 1),
            Fraction(1, 2),
            Fraction(1, 4),
            Fraction(1, 8),
            Fraction(1, 15),
            Fraction(1, 30),
            Fraction(1, 60),
            Fraction(1, 125),
            Fraction(1, 250),
            Fraction(1, 500),
            Fraction(1, 1000),
            Fraction(1, 2000),
            Fraction(1, 4000),
            Fraction(1, 8000),
            )

        val filteredShutterSpeedRange = shutterSpeedRange.filter {
            fractionToNanoseconds(it) in min..max
        }

        return filteredShutterSpeedRange

    }

    fun isoRange(min: Int, max: Int): List<Int>{
        val isoRange = listOf<Int>(
            25,50,100,200,400,800,1600,3200,6400,12800, 25600, 51200
        )

        val filteredIsoRange = isoRange.filter {
            it in min..max
        }

        return filteredIsoRange
    }

    fun findIsoNearestNumber(input: Int, numbers: List<Int>): Int{
        var nearestNumber = numbers[0]
        var difference = Math.abs(input - nearestNumber)

        for (number in numbers) {
            val currentDifference = Math.abs(input - number)
            if (currentDifference < difference) {
                difference = currentDifference
                nearestNumber = number
            }
        }

        return nearestNumber
    }

    fun stringToNanoseconds(value: String): Long{
        val parts = value.split("/").map {
            it.toLong()
        }
        val fraction = Fraction(parts[0], parts[1])
        return fractionToNanoseconds(fraction)
    }

    // TODO think about how to manage the steps
    // returns the list of exposure values which are divisible by 5 only, so 4.0, 3.5, 3.0 and so on
    fun getExposureValuesFromRange(min: Int, max: Int, step: Float): List<Float>{

        val exposureValues = mutableListOf<Float>()
        for (value in min..max){
            val exposureCompensation = value*step
            val decimalPLaces = 1
            val powerOf10 = 10.0f.pow(decimalPLaces)
            val roundedNumber = round(exposureCompensation * powerOf10) / powerOf10
            exposureValues.add(roundedNumber)
        }

        return exposureValues.filter { (it * 10).toInt() % 5 == 0 }
    }

    fun getActualValueFromExposureCompensation(exposureCompensation: Float, step: Float): Int {
        return (exposureCompensation/ step).toInt()

    }


    /**
     * From the range given, compute the list that will be used for zoom ratio
     * Input: Min and Max zoom ratio, eg: min: 0.5, max: 30.0
     * Output: [0.5,0,6,...., 9.9, 10, 11, 12,...., 29, 30]
     */
    fun computeZoomRatios(min_zoom:Float, max_zoom: Float): MutableList<Float>{
        val zoomRatio : MutableList<Float> = mutableListOf()

        for (ratio in 0 until max_zoom.toInt()){

            // start and end range to calculate the 1 digit arrays between them [0, 0.1, 0.2, ...,0,9,1]
            val start = ratio * 10
            val end = (ratio + 1) * 10

            if(end == (max_zoom * 10).toInt()){
                // add the last value as the last value will not be included in loop
                zoomRatio.add(end/10f)
                break
            }

            if(start >= 100){
                //TODO
                // Discuss whether the distance between, for eg., 9.1 - 9.2 and 10 - 11 should be same?
                /**
                for(value in 0 until 5){
                    zoomRatio.add(start/10f)
                }
                */
                zoomRatio.add(start/10f)
                // if the value is greater than 100 (10.0), continue adding only the int (11, 12,..)
                continue
            }

            for (innerRatio in start..end){
                if(innerRatio/10f < (min_zoom - 0.1) || innerRatio == end ){
                    // the start of the the loop is from 0,
                    // so do not add value that is  smaller then the minimum zoom value
                    // Or, do not add end value as it will be added in next iteration's start value
                    continue
                }

                zoomRatio.add(innerRatio/10f)

            }
        }
        // Log.d doesnot work with test, so System.out.print is used
        System.out.print("zoom ratio: " +zoomRatio.filter { it >= 0.0 }.toString())

        return zoomRatio

    }


    fun convertTemperatureToRggb(temperature_kelvin: Int): FloatArray {
        val temperature = temperature_kelvin / 100.0f
        var red: Float
        var green: Float
        var blue: Float
        if (temperature <= 66) {
            red = 255f
        } else {
            red = temperature - 60
            red = (329.698727446 * Math.pow(red.toDouble(), -0.1332047592)).toFloat()
            if (red < 0) red = 0f
            if (red > 255) red = 255f
        }
        if (temperature <= 66) {
            green = temperature
            green = (99.4708025861 * Math.log(green.toDouble()) - 161.1195681661).toFloat()
            if (green < 0) green = 0f
            if (green > 255) green = 255f
        } else {
            green = temperature - 60
            green = (288.1221695283 * Math.pow(green.toDouble(), -0.0755148492)).toFloat()
            if (green < 0) green = 0f
            if (green > 255) green = 255f
        }
        if (temperature >= 66) blue = 255f else if (temperature <= 19) blue = 0f else {
            blue = temperature - 10
            blue = (138.5177312231 * Math.log(blue.toDouble()) - 305.0447927307).toFloat()
            if (blue < 0) blue = 0f
            if (blue > 255) blue = 255f
        }

        red = red / 255.0f
        green = green / 255.0f
        blue = blue / 255.0f
        red = RGBtoGain(red)
        green = RGBtoGain(green)
        blue = RGBtoGain(blue)

        return floatArrayOf(red, green / 2, green / 2, blue)
    }

    private fun RGBtoGain(value: Float): Float {
        var value = value
        val max_gain_c = 10.0f
        if (value < 1.0e-5f) {
            return max_gain_c
        }
        value = 1.0f / value
        value = Math.min(max_gain_c, value)
        return value
    }

    fun getWhiteBalanceTemperatureList() : MutableList<Int> {
        return mutableListOf(3000, 4000, 5000, 6000, 6600, 7000, 8000, 9000, 10000 , 12000, 15000)
    }

    fun getWhiteBalanceModes(): MutableList<Int> {
        return mutableListOf(
            CaptureRequest.CONTROL_AWB_MODE_OFF,
            CaptureRequest.CONTROL_AWB_MODE_AUTO,
            CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
            CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
            CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT,
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
            CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
            CaptureRequest.CONTROL_AWB_MODE_TWILIGHT,
            CaptureRequest.CONTROL_AWB_MODE_SHADE
        )
    }

    fun getWhiteBalanceNames() : MutableList<String> {
        return mutableListOf(
            "Manual",
            "Auto",
            "Incandescent",
            "Fluorescent",
            "Warm Fluorescent",
            "Daylight",
            "Cloudy Daylight",
            "Twilight",
            "Shade"
        )
    }

}
