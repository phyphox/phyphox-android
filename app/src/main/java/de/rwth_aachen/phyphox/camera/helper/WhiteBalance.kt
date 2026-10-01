package de.rwth_aachen.phyphox.camera.helper

import kotlin.math.sqrt

//White balance by white point (file format 1.21, phyphox-docs docs/file-format/input.md "White balance"):
//a correlated colour temperature and a Duv tint become a chromaticity, and the chromaticity a Bradford
//adaptation in linear sRGB that makes a neutral surface under that illuminant neutral in the image.
//Used on the software path, where the camera is held at a daylight (D65) white point.
object WhiteBalance {
    //Validity of the Planckian locus approximation below, and the range of the software path
    const val MIN_TEMPERATURE = 1667
    const val MAX_TEMPERATURE = 25000
    const val DEFAULT_TEMPERATURE = 5500
    //Duv; Android's CCT mode takes -0.01..0.01, the software path allows a little more
    const val MAX_TINT = 0.02f
    const val MAX_TINT_CCT = 0.01f

    val D65 = doubleArrayOf(0.3127, 0.3290)

    //Planckian locus in CIE xy (Kim et al. 2002), 1667..25000 K
    fun planckianXY(temperature: Double): DoubleArray {
        val t = temperature.coerceIn(MIN_TEMPERATURE.toDouble(), MAX_TEMPERATURE.toDouble())
        val t2 = t * t
        val t3 = t2 * t
        val x = if (t <= 4000.0)
            -0.2661239e9 / t3 - 0.2343589e6 / t2 + 0.8776956e3 / t + 0.179910
        else
            -3.0258469e9 / t3 + 2.1070379e6 / t2 + 0.2226347e3 / t + 0.240390
        val x2 = x * x
        val x3 = x2 * x
        val y = when {
            t <= 2222.0 -> -1.1063814 * x3 - 1.34811020 * x2 + 2.18555832 * x - 0.20219683
            t <= 4000.0 -> -0.9549476 * x3 - 1.37418593 * x2 + 2.09137015 * x - 0.16748867
            else -> 3.0817580 * x3 - 5.87338670 * x2 + 3.75112997 * x - 0.37001483
        }
        return doubleArrayOf(x, y)
    }

    //CIE 1960 uv, the diagram in which Duv is measured
    fun xyToUv(xy: DoubleArray): DoubleArray {
        val d = -2.0 * xy[0] + 12.0 * xy[1] + 3.0
        return doubleArrayOf(4.0 * xy[0] / d, 6.0 * xy[1] / d)
    }

    fun uvToXy(uv: DoubleArray): DoubleArray {
        val d = 2.0 * uv[0] - 8.0 * uv[1] + 4.0
        return doubleArrayOf(3.0 * uv[0] / d, 2.0 * uv[1] / d)
    }

    //White point of a temperature and a Duv offset: the locus point moved along the locus normal in uv,
    //positive above the locus (towards green, larger v), negative towards magenta
    fun chromaticity(temperature: Double, duv: Double): DoubleArray {
        val uv = xyToUv(planckianXY(temperature))
        if (duv == 0.0)
            return uvToXy(uv)
        val step = temperature * 0.01
        val ahead = xyToUv(planckianXY(temperature + step))
        val behind = xyToUv(planckianXY(temperature - step))
        val du = ahead[0] - behind[0]
        val dv = ahead[1] - behind[1]
        val length = sqrt(du * du + dv * dv)
        var nu = dv / length
        var nv = -du / length
        if (nv < 0.0) {
            nu = -nu
            nv = -nv
        }
        return uvToXy(doubleArrayOf(uv[0] + duv * nu, uv[1] + duv * nv))
    }

    fun xyToXYZ(xy: DoubleArray): DoubleArray =
        doubleArrayOf(xy[0] / xy[1], 1.0, (1.0 - xy[0] - xy[1]) / xy[1])

    //Row-major 3x3 matrices
    private val BRADFORD = doubleArrayOf(
        0.8951, 0.2664, -0.1614,
        -0.7502, 1.7135, 0.0367,
        0.0389, -0.0685, 1.0296)
    private val BRADFORD_INVERSE = doubleArrayOf(
        0.9869929, -0.1470543, 0.1599627,
        0.4323053, 0.5183603, 0.0492912,
        -0.0085287, 0.0400428, 0.9684867)
    val SRGB_TO_XYZ = doubleArrayOf(
        0.4124564, 0.3575761, 0.1804375,
        0.2126729, 0.7151522, 0.0721750,
        0.0193339, 0.1191920, 0.9503041)
    val XYZ_TO_SRGB = doubleArrayOf(
        3.2404542, -1.5371385, -0.4985314,
        -0.9692660, 1.8760108, 0.0415560,
        0.0556434, -0.2040259, 1.0572252)

    fun multiply(a: DoubleArray, b: DoubleArray): DoubleArray {
        val r = DoubleArray(9)
        for (i in 0..2)
            for (j in 0..2)
                r[3 * i + j] = a[3 * i] * b[j] + a[3 * i + 1] * b[3 + j] + a[3 * i + 2] * b[6 + j]
        return r
    }

    fun apply(m: DoubleArray, v: DoubleArray): DoubleArray = doubleArrayOf(
        m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
        m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
        m[6] * v[0] + m[7] * v[1] + m[8] * v[2])

    //Bradford chromatic adaptation from a source white to a destination white, expressed in linear sRGB
    fun adaptation(sourceXY: DoubleArray, destinationXY: DoubleArray): DoubleArray =
        adaptationXYZ(xyToXYZ(sourceXY), xyToXYZ(destinationXY))

    fun adaptationXYZ(sourceXYZ: DoubleArray, destinationXYZ: DoubleArray): DoubleArray {
        val source = apply(BRADFORD, sourceXYZ)
        val destination = apply(BRADFORD, destinationXYZ)
        val scale = doubleArrayOf(
            destination[0] / source[0], 0.0, 0.0,
            0.0, destination[1] / source[1], 0.0,
            0.0, 0.0, destination[2] / source[2])
        return multiply(XYZ_TO_SRGB, multiply(BRADFORD_INVERSE, multiply(scale, multiply(BRADFORD, SRGB_TO_XYZ))))
    }

    //The correction that balances a D65-anchored image for the given illuminant: its white becomes neutral,
    //so a daylight-neutral image turns blue for a tungsten target and warm for a cloudy-sky target. The
    //destination is the white of the sRGB matrices themselves, so that neutral means exactly equal channels.
    fun correction(temperature: Int, duv: Float): DoubleArray =
        adaptationXYZ(xyToXYZ(chromaticity(temperature.toDouble(), duv.toDouble())), apply(SRGB_TO_XYZ, doubleArrayOf(1.0, 1.0, 1.0)))

    //Linear sRGB of the illuminant's white, scaled to a maximum component of one
    fun illuminantRGB(temperature: Int, duv: Float): DoubleArray {
        val rgb = apply(XYZ_TO_SRGB, xyToXYZ(chromaticity(temperature.toDouble(), duv.toDouble())))
        val max = maxOf(rgb[0], rgb[1], rgb[2])
        return doubleArrayOf(rgb[0] / max, rgb[1] / max, rgb[2] / max)
    }

    //Column-major, as glUniformMatrix3fv takes it
    fun shaderMatrix(temperature: Int, duv: Float): FloatArray {
        val m = correction(temperature, duv)
        return floatArrayOf(
            m[0].toFloat(), m[3].toFloat(), m[6].toFloat(),
            m[1].toFloat(), m[4].toFloat(), m[7].toFloat(),
            m[2].toFloat(), m[5].toFloat(), m[8].toFloat())
    }
}
