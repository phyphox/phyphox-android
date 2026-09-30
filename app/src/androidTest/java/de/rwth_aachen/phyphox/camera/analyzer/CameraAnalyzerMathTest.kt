package de.rwth_aachen.phyphox.camera.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.rwth_aachen.phyphox.DataBuffer
import de.rwth_aachen.phyphox.camera.model.CameraSettingState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// phyphox-test: camera-analyzer-math
//The camera analyzers on generated frames instead of camera frames: a bitmap is drawn into a
//SurfaceTexture, which is exactly what the camera delivers (an external OES texture with a transform
//matrix), and every analyzer runs its real shaders and reduction chain on it in an offscreen EGL
//context. Expected values come from a per-pixel reference on the CPU. Frame sizes are deliberately
//no multiple of four, so each downsampling step ends in a partial tile.
@RunWith(AndroidJUnit4::class)
class CameraAnalyzerMathTest {

    companion object {
        const val W = 331
        const val H = 197
        val FULL = RectF(0f, 0f, 1f, 1f)
        //Exposure factor 2^aperture/2 * 100/ISO * (1/60)/shutter: 1 for the first, 2 for the second
        val UNIT_EXPOSURE = CameraSettingState(currentIsoValue = 100, currentShutterValue = 1_000_000_000L / 60, currentApertureValue = 1f)
        val DOUBLED_EXPOSURE = CameraSettingState(currentIsoValue = 200, currentShutterValue = 1_000_000_000L / 120, currentApertureValue = 2f)

        const val WR = 0.2126
        const val WG = 0.7152
        const val WB = 0.0722

        fun linearize(v: Double) = if (v < 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        //The analyzers carry value and coverage as 16-bit means, so the reference and the GPU differ by the
        //16-bit rounding once per reduction step and by the sub-LSB precision of the texture filter
        const val UNIFORM_TOLERANCE = 1e-4
        const val TEXTURED_TOLERANCE = 3e-4

        fun r(c: Int) = Color.red(c) / 255.0
        fun g(c: Int) = Color.green(c) / 255.0
        fun b(c: Int) = Color.blue(c) / 255.0
        fun luma(c: Int) = WR * r(c) + WG * g(c) + WB * b(c)
        fun luminance(c: Int) = WR * linearize(r(c)) + WG * linearize(g(c)) + WB * linearize(b(c))
        fun saturation(c: Int): Double {
            val mx = max(r(c), max(g(c), b(c)))
            val mn = min(r(c), min(g(c), b(c)))
            return if (mx == 0.0) 0.0 else (mx - mn) / mx
        }
        fun value(c: Int) = max(r(c), max(g(c), b(c)))
        //Hue in radians, the shader's formula
        fun hue(c: Int): Double {
            val mx = max(r(c), max(g(c), b(c)))
            val mn = min(r(c), min(g(c), b(c)))
            val d = mx - mn
            return when {
                mx == mn -> 0.0
                mx == r(c) -> 2.0 * Math.PI * (g(c) - b(c) + d * (if (g(c) < b(c)) 6.0 else 0.0)) / (6.0 * d)
                mx == g(c) -> 2.0 * Math.PI * (b(c) - r(c) + d * 2.0) / (6.0 * d)
                else -> 2.0 * Math.PI * (r(c) - g(c) + d * 4.0) / (6.0 * d)
            }
        }
        fun hueDistanceDegrees(a: Double, b: Double): Double {
            val d = abs(a - b) % 360.0
            return min(d, 360.0 - d)
        }
    }

    class Buffer(name: String) : DataBuffer(name, null, 0, null)

    private lateinit var display: EGLDisplay
    private lateinit var context: EGLContext
    private lateinit var config: EGLConfig
    private var textureId = 0
    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var surface: Surface
    private var frameArrived = CountDownLatch(1)
    private val camMatrix = FloatArray(16)

    @Before
    fun setUp() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        assertTrue("eglInitialize failed", EGL14.eglInitialize(display, version, 0, version, 1))
        //The same configuration as AnalyzingOpenGLRenderer.createContext
        val configAttr = intArrayOf(
            EGL14.EGL_COLOR_BUFFER_TYPE, EGL14.EGL_RGB_BUFFER,
            EGL14.EGL_LEVEL, 0,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_LUMINANCE_SIZE, 0,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_BUFFER_SIZE, 32,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfig = IntArray(1)
        EGL14.eglChooseConfig(display, configAttr, 0, configs, 0, 1, numConfig, 0)
        assertTrue("no EGL configuration", numConfig[0] > 0)
        config = configs[0]!!
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        assertTrue("eglMakeCurrent failed", EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context))

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setDefaultBufferSize(W, H)
        surfaceTexture.setOnFrameAvailableListener({ frameArrived.countDown() }, Handler(Looper.getMainLooper()))
        surface = Surface(surfaceTexture)

        OpenGLHelper.prepareFullScreenVertices()
    }

    @After
    fun tearDown() {
        AnalyzingModule.release()
        surface.release()
        surfaceTexture.release()
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }

    //Hands a frame to the analyzers the way the camera does: through the surface texture, with its
    //transform matrix and the transformed size, then rebuilds the shared analysis buffers for that size
    private fun present(bitmap: Bitmap) {
        frameArrived = CountDownLatch(1)
        val canvas = surface.lockCanvas(null)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        surface.unlockCanvasAndPost(canvas)
        assertTrue("the frame never reached the surface texture", frameArrived.await(5, TimeUnit.SECONDS))
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(camMatrix)
        val fbW = abs((camMatrix[0] * W + camMatrix[1] * H).roundToInt())
        val fbH = abs((camMatrix[4] * W + camMatrix[5] * H).roundToInt())
        AnalyzingModule.release()
        AnalyzingModule.init(fbW, fbH, context, display, config, textureId)
        AnalyzingModule.photometrySetupGL()
    }

    private fun bitmap(color: (x: Int, y: Int) -> Int): Bitmap {
        val pixels = IntArray(W * H)
        for (y in 0 until H)
            for (x in 0 until W)
                pixels[y * W + x] = color(x, y)
        return Bitmap.createBitmap(pixels, W, H, Bitmap.Config.ARGB_8888)
    }

    //Mean of a per-pixel quantity over the region of interest, in the analyzers' coordinate system:
    //passepartout x runs down the image rows, passepartout y from the right edge to the left
    //(PhyphoxFile maps the user's x1..y2 onto this with x -> 1-y, y -> 1-x)
    private fun mean(bitmap: Bitmap, roi: RectF, f: (Int) -> Double): Double {
        var sum = 0.0
        var n = 0
        for (y in 0 until H) {
            val px = (y + 0.5) / H
            if (px < roi.left || px > roi.right) continue
            for (x in 0 until W) {
                val py = 1.0 - (x + 0.5) / W
                if (py < roi.top || py > roi.bottom) continue
                sum += f(bitmap.getPixel(x, y))
                n++
            }
        }
        return sum / n
    }

    private fun meanHueDegrees(bitmap: Bitmap): Double {
        var x = 0.0
        var y = 0.0
        for (py in 0 until H)
            for (px in 0 until W) {
                val h = hue(bitmap.getPixel(px, py))
                x += cos(h)
                y += sin(h)
            }
        val degrees = Math.toDegrees(atan2(y, x))
        return if (degrees < 0) degrees + 360.0 else degrees
    }

    private fun scalar(module: AnalyzingModule, out: Buffer, roi: RectF, state: CameraSettingState): Double {
        module.prepare()
        module.analyze(camMatrix, roi)
        module.writeToBuffers(state)
        return out.value
    }

    private fun channel(linear: Boolean, channel: LuminanceAnalyzer.Channel, roi: RectF = FULL, state: CameraSettingState = UNIT_EXPOSURE): Double {
        val out = Buffer("out")
        return scalar(LuminanceAnalyzer(out, linear, channel), out, roi, state)
    }

    private fun hsv(mode: HSVAnalyzer.Mode, roi: RectF = FULL): Double {
        val out = Buffer("out")
        return scalar(HSVAnalyzer(out, mode), out, roi, UNIT_EXPOSURE)
    }

    private fun spectra(orientation: SpectroscopyAnalyzer.SpectrumOrientation, state: CameraSettingState = UNIT_EXPOSURE): Map<String, DoubleArray> {
        val names = listOf("luminance", "pixelPosition", "linearRed", "linearGreen", "linearBlue")
        val buffers = names.associateWith { Buffer(it) }
        val module = SpectroscopyAnalyzer(buffers["luminance"], buffers["pixelPosition"], buffers["linearRed"], buffers["linearGreen"], buffers["linearBlue"], orientation)
        module.prepare()
        module.analyze(camMatrix, FULL)
        module.writeToBuffers(state)
        return buffers.mapValues { (_, buffer) -> buffer.array.map { it.toDouble() }.toDoubleArray() }
    }

    private fun uniform(r: Int, g: Int, b: Int) = bitmap { _, _ -> Color.rgb(r, g, b) }

    private fun hsvToRgb(h: Double, s: Double, v: Double): Int = Color.HSVToColor(floatArrayOf(h.toFloat(), s.toFloat(), v.toFloat()))

    //Ramps and a pseudo-random channel, so that every value and every carry in the 16-bit packing occurs
    private fun textured() = bitmap { x, y -> Color.rgb(x * 255 / (W - 1), y * 255 / (H - 1), (x * 7 + y * 13) % 256) }

    @Test
    fun everyScalarChannelOnAUniformFrame() {
        val c = Color.rgb(200, 100, 50)
        present(uniform(200, 100, 50))

        assertEquals("red", r(c), channel(false, LuminanceAnalyzer.Channel.red), UNIFORM_TOLERANCE)
        assertEquals("green", g(c), channel(false, LuminanceAnalyzer.Channel.green), UNIFORM_TOLERANCE)
        assertEquals("blue", b(c), channel(false, LuminanceAnalyzer.Channel.blue), UNIFORM_TOLERANCE)
        assertEquals("luma", luma(c), channel(false, LuminanceAnalyzer.Channel.luma), UNIFORM_TOLERANCE)
        assertEquals("linearRed", linearize(r(c)), channel(true, LuminanceAnalyzer.Channel.red), UNIFORM_TOLERANCE)
        assertEquals("linearGreen", linearize(g(c)), channel(true, LuminanceAnalyzer.Channel.green), UNIFORM_TOLERANCE)
        assertEquals("linearBlue", linearize(b(c)), channel(true, LuminanceAnalyzer.Channel.blue), UNIFORM_TOLERANCE)
        assertEquals("luminance", luminance(c), channel(true, LuminanceAnalyzer.Channel.luma), UNIFORM_TOLERANCE)
        assertEquals("hue", 20.0, hsv(HSVAnalyzer.Mode.hue), 0.1)
        assertEquals("saturation", saturation(c), hsv(HSVAnalyzer.Mode.saturation), UNIFORM_TOLERANCE)
        assertEquals("value", value(c), hsv(HSVAnalyzer.Mode.value), UNIFORM_TOLERANCE)

        //The auto-exposure statistics share the pipeline but stay 8-bit
        val exposure = ExposureAnalyzer()
        exposure.prepare()
        exposure.analyze(camMatrix, FULL)
        assertEquals("exposure minRGB", b(c), exposure.minRGB, UNIFORM_TOLERANCE)
        assertEquals("exposure maxRGB", r(c), exposure.maxRGB, UNIFORM_TOLERANCE)
        assertEquals("exposure meanLuma", (luma(c) * 255).roundToInt() / 255.0, exposure.meanLuma, 0.5 / 255)
    }

    @Test
    fun hueBranchesAndGrey() {
        present(uniform(0, 255, 0))
        assertEquals("green", 120.0, hsv(HSVAnalyzer.Mode.hue), 0.1)
        present(uniform(0, 0, 255))
        assertEquals("blue", 240.0, hsv(HSVAnalyzer.Mode.hue), 0.1)
        present(uniform(255, 0, 128))
        assertEquals("magenta-ish, the wrap-around branch of red", Math.toDegrees(hue(Color.rgb(255, 0, 128))), hsv(HSVAnalyzer.Mode.hue), 0.1)
        present(uniform(100, 100, 100))
        assertEquals("grey has hue 0", 0.0, hueDistanceDegrees(0.0, hsv(HSVAnalyzer.Mode.hue)), 0.1)
        assertEquals("grey has no saturation", 0.0, hsv(HSVAnalyzer.Mode.saturation), UNIFORM_TOLERANCE)
        assertEquals("value of grey", 100 / 255.0, hsv(HSVAnalyzer.Mode.value), UNIFORM_TOLERANCE)
    }

    @Test
    fun hueIsAveragedOnTheColourWheel() {
        //Half the frame just below 360 degrees, half just above 0: the mean lies at 0, an arithmetic
        //mean of the angles would report 180 (cyan)
        present(bitmap { x, _ -> if (x < W / 2) Color.rgb(255, 0, 43) else Color.rgb(255, 43, 0) })
        val hue = hsv(HSVAnalyzer.Mode.hue)
        assertTrue("hue $hue is not at the wrap-around", hueDistanceDegrees(0.0, hue) < 0.1)
        assertTrue("hue $hue is the arithmetic mean of the angles", hueDistanceDegrees(180.0, hue) > 170.0)
    }

    @Test
    fun exposureFactorScalesOnlyTheLinearOutputs() {
        val c = Color.rgb(200, 100, 50)
        present(uniform(200, 100, 50))
        assertEquals("luminance", 2 * luminance(c), channel(true, LuminanceAnalyzer.Channel.luma, state = DOUBLED_EXPOSURE), 2 * UNIFORM_TOLERANCE)
        assertEquals("linearRed", 2 * linearize(r(c)), channel(true, LuminanceAnalyzer.Channel.red, state = DOUBLED_EXPOSURE), 2 * UNIFORM_TOLERANCE)
        assertEquals("luma", luma(c), channel(false, LuminanceAnalyzer.Channel.luma, state = DOUBLED_EXPOSURE), UNIFORM_TOLERANCE)
        assertEquals("red", r(c), channel(false, LuminanceAnalyzer.Channel.red, state = DOUBLED_EXPOSURE), UNIFORM_TOLERANCE)
    }

    @Test
    fun regionOfInterestSelectsTheRightPixels() {
        val quadrants = bitmap { x, y ->
            if (y < H / 2) (if (x < W / 2) Color.rgb(255, 0, 0) else Color.rgb(0, 255, 0))
            else (if (x < W / 2) Color.rgb(0, 0, 255) else Color.rgb(255, 255, 255))
        }
        present(quadrants)
        //Region -> (r, g, b) of the quadrant it must select, strictly inside the quadrant
        val cases = mapOf(
            RectF(0.1f, 0.6f, 0.4f, 0.9f) to Triple(1.0, 0.0, 0.0), //top left
            RectF(0.1f, 0.1f, 0.4f, 0.4f) to Triple(0.0, 1.0, 0.0), //top right
            RectF(0.6f, 0.6f, 0.9f, 0.9f) to Triple(0.0, 0.0, 1.0), //bottom left
            RectF(0.6f, 0.1f, 0.9f, 0.4f) to Triple(1.0, 1.0, 1.0)  //bottom right
        )
        for ((roi, expected) in cases) {
            assertEquals("red in $roi", expected.first, channel(false, LuminanceAnalyzer.Channel.red, roi), UNIFORM_TOLERANCE)
            assertEquals("green in $roi", expected.second, channel(false, LuminanceAnalyzer.Channel.green, roi), UNIFORM_TOLERANCE)
            assertEquals("blue in $roi", expected.third, channel(false, LuminanceAnalyzer.Channel.blue, roi), UNIFORM_TOLERANCE)
            //The CPU reference uses the same mapping, so a mismatch here means the mapping comment is wrong
            assertEquals("reference in $roi", expected.first, mean(quadrants, roi) { r(it) }, 1e-9)
        }
        //A region across a boundary averages both sides: half red, half green in the top half
        val topHalf = RectF(0.1f, 0.1f, 0.4f, 0.9f)
        //The region's edges fall between pixel columns, so the reference may be off by one column of 99
        assertEquals(mean(quadrants, topHalf) { r(it) }, channel(false, LuminanceAnalyzer.Channel.red, topHalf), 0.02)
        assertEquals(mean(quadrants, topHalf) { g(it) }, channel(false, LuminanceAnalyzer.Channel.green, topHalf), 0.02)
        assertEquals("nothing but red and green up there", 0.0, channel(false, LuminanceAnalyzer.Channel.blue, topHalf), UNIFORM_TOLERANCE)

        val exposure = ExposureAnalyzer()
        exposure.prepare()
        exposure.analyze(camMatrix, FULL)
        assertEquals("exposure minRGB", 0.0, exposure.minRGB, UNIFORM_TOLERANCE)
        assertEquals("exposure maxRGB", 1.0, exposure.maxRGB, UNIFORM_TOLERANCE)
    }

    @Test
    fun reductionChainOnATexturedFrame() {
        val frame = textured()
        present(frame)
        val tolerance = TEXTURED_TOLERANCE
        assertEquals("red", mean(frame, FULL) { r(it) }, channel(false, LuminanceAnalyzer.Channel.red), tolerance)
        assertEquals("green", mean(frame, FULL) { g(it) }, channel(false, LuminanceAnalyzer.Channel.green), tolerance)
        assertEquals("blue", mean(frame, FULL) { b(it) }, channel(false, LuminanceAnalyzer.Channel.blue), tolerance)
        assertEquals("luma", mean(frame, FULL) { luma(it) }, channel(false, LuminanceAnalyzer.Channel.luma), tolerance)
        assertEquals("luminance", mean(frame, FULL) { luminance(it) }, channel(true, LuminanceAnalyzer.Channel.luma), tolerance)
        assertEquals("linearRed", mean(frame, FULL) { linearize(r(it)) }, channel(true, LuminanceAnalyzer.Channel.red), tolerance)
        assertEquals("linearGreen", mean(frame, FULL) { linearize(g(it)) }, channel(true, LuminanceAnalyzer.Channel.green), tolerance)
        assertEquals("linearBlue", mean(frame, FULL) { linearize(b(it)) }, channel(true, LuminanceAnalyzer.Channel.blue), tolerance)
        assertEquals("saturation", mean(frame, FULL) { saturation(it) }, hsv(HSVAnalyzer.Mode.saturation), tolerance)
        assertEquals("value", mean(frame, FULL) { value(it) }, hsv(HSVAnalyzer.Mode.value), tolerance)
    }

    @Test
    fun hueReductionOnSweepsAndRamps() {
        //The mean hue is the direction of the mean unit vector, so a frame only makes a meaningful test when
        //that vector is not short: the textured frame above covers the colour wheel almost uniformly (mean
        //vector length 0.003) and its angle would be ill-conditioned. These frames keep the partial tiles
        //and the ramps but leave a mean vector of length 0.6 or more.
        val cases = mapOf(
            "half circle sweep" to bitmap { x, _ -> hsvToRgb(180.0 * x / (W - 1), 1.0, 1.0) },
            "half circle sweep at low saturation and value" to bitmap { x, _ -> hsvToRgb(180.0 * x / (W - 1), 0.1, 0.5) },
            "red and green ramps" to bitmap { x, y -> Color.rgb(x * 255 / (W - 1), y * 255 / (H - 1), 0) }
        )
        for ((name, frame) in cases) {
            present(frame)
            val hue = hsv(HSVAnalyzer.Mode.hue)
            val reference = meanHueDegrees(frame)
            assertTrue("$name: hue $hue vs reference $reference", hueDistanceDegrees(reference, hue) < 0.1)
        }
    }

    @Test
    fun spectraFollowTheSpectrumAxis() {
        //A red ramp along the image width over constant green: one orientation resolves it into a
        //spectrum of one value per column, the other averages every row to the same flat spectrum
        val frame = bitmap { x, _ -> Color.rgb(x * 255 / (W - 1), 100, 0) }
        present(frame)
        val state = DOUBLED_EXPOSURE
        val factor = 2.0

        val ramp = spectra(SpectroscopyAnalyzer.SpectrumOrientation.PORTRAIT, state)
        assertEquals("one value per column", W, ramp["pixelPosition"]!!.size)
        for (name in listOf("luminance", "linearRed", "linearGreen", "linearBlue"))
            assertEquals("$name as long as pixelPosition", W, ramp[name]!!.size)
        for (i in 0 until W) {
            assertEquals("pixel position", i.toDouble(), ramp["pixelPosition"]!![i], 1e-9)
            val c = frame.getPixel(i, 0)
            val tolerance = factor * UNIFORM_TOLERANCE
            assertEquals("linearRed at column $i", factor * linearize(r(c)), ramp["linearRed"]!![i], tolerance)
            assertEquals("linearGreen at column $i", factor * linearize(g(c)), ramp["linearGreen"]!![i], tolerance)
            assertEquals("linearBlue at column $i", 0.0, ramp["linearBlue"]!![i], tolerance)
            assertEquals("luminance at column $i", factor * luminance(c), ramp["luminance"]!![i], tolerance)
        }
        assertTrue("the red spectrum rises along the ramp", ramp["linearRed"]!![W - 1] > ramp["linearRed"]!![0] + 1.0)

        val flat = spectra(SpectroscopyAnalyzer.SpectrumOrientation.LANDSCAPE, state)
        assertEquals("one value per row", H, flat["pixelPosition"]!!.size)
        val rowMean = factor * mean(frame, FULL) { linearize(r(it)) }
        for (j in 0 until H)
            assertEquals("linearRed at row $j", rowMean, flat["linearRed"]!![j], factor * TEXTURED_TOLERANCE)
    }
}
