package com.example.domain.video

import com.example.data.model.AdjustSettings
import com.example.data.model.CurveChannel
import com.example.data.model.CurvePoint
import com.example.data.model.HslColorBand
import com.example.data.model.RgbCurvesSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object RgbCurveLutBuilder {

    /**
     * Evaluates a piecewise-smooth Catmull-Rom spline for monotonic curve points on [0f..1f].
     */
    fun evaluateCurve(points: List<CurvePoint>, x: Float): Float {
        if (points.isEmpty()) return x.coerceIn(0f, 1f)
        val sorted = points.sortedBy { it.x }
        val cx = x.coerceIn(0f, 1f)
        if (cx <= sorted.first().x) return sorted.first().y.coerceIn(0f, 1f)
        if (cx >= sorted.last().x) return sorted.last().y.coerceIn(0f, 1f)

        var seg = 0
        for (i in 0 until sorted.lastIndex) {
            if (cx >= sorted[i].x && cx <= sorted[i + 1].x) {
                seg = i
                break
            }
        }
        val p0 = sorted[(seg - 1).coerceAtLeast(0)]
        val p1 = sorted[seg]
        val p2 = sorted[(seg + 1).coerceAtMost(sorted.lastIndex)]
        val p3 = sorted[(seg + 2).coerceAtMost(sorted.lastIndex)]

        val span = (p2.x - p1.x).coerceAtLeast(1e-4f)
        val t = ((cx - p1.x) / span).coerceIn(0f, 1f)
        val t2 = t * t
        val t3 = t2 * t

        // Catmull-Rom Y interpolation
        val y = 0.5f * (
            (2f * p1.y) +
                (-p0.y + p2.y) * t +
                (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
            )
        return y.coerceIn(0f, 1f)
    }

    /**
     * Builds a 17x17x17 3D LUT encapsulating:
     * - RGB Curves (White / Red / Green / Blue)
     * - Tone Adjustments (Highlights, Shadows, Whites, Blacks, Brilliance, Fade)
     * - Color Wheels (Lift / Gamma / Gain)
     * - Optional base LUT blend (for LUNA .cube/.ffx Filters combined with CapCut Adjust)
     */
    fun buildCombinedLut(
        adjust: AdjustSettings,
        baseCube: Array<Array<IntArray>>? = null,
        baseCubeIntensity: Float = 1.0f,
    ): Array<Array<IntArray>> {
        val n = 17
        val whiteLut = FloatArray(256) { i -> evaluateCurve(adjust.rgbCurves.white, i / 255f) }
        val redLut = FloatArray(256) { i -> evaluateCurve(adjust.rgbCurves.red, i / 255f) }
        val greenLut = FloatArray(256) { i -> evaluateCurve(adjust.rgbCurves.green, i / 255f) }
        val blueLut = FloatArray(256) { i -> evaluateCurve(adjust.rgbCurves.blue, i / 255f) }

        val wheel = adjust.colorWheel
        fun hueVec(deg: Float, amt: Float): Triple<Float, Float, Float> {
            if (amt <= 0.005f) return Triple(0f, 0f, 0f)
            val rad = deg * PI.toFloat() / 180f
            return Triple(
                cos(rad) * amt * 0.18f,
                cos(rad - 2.094f) * amt * 0.18f,
                cos(rad + 2.094f) * amt * 0.18f,
            )
        }
        val liftRgb = hueVec(wheel.liftHueDeg, wheel.liftAmount)
        val gammaRgb = hueVec(wheel.gammaHueDeg, wheel.gammaAmount)
        val gainRgb = hueVec(wheel.gainHueDeg, wheel.gainAmount)

        fun applyToneAndCurve(v0: Float, chLut: FloatArray, chIdx: Int): Float {
            var v = v0.coerceIn(0f, 1f)

            // Blacks & Whites clipping / shoulder
            val blacksAdj = (adjust.blacks / 50f) * 0.14f
            val whitesAdj = (adjust.whites / 50f) * 0.14f
            v = ((v + blacksAdj * (1f - v).pow(2)) + whitesAdj * v.pow(2)).coerceIn(0f, 1f)

            // Shadows & Highlights recovery/boost
            val shadowsAdj = (adjust.shadows / 50f) * 0.24f
            val highlightsAdj = (adjust.highlights / 50f) * 0.24f
            val shadowMask = (1f - v).pow(1.7f) * (1f - v * 0.4f)
            val highlightMask = v.pow(1.7f) * (0.6f + v * 0.4f)
            v = (v + shadowsAdj * shadowMask + highlightsAdj * highlightMask).coerceIn(0f, 1f)

            // Brilliance (midtone contrast + shadow illumination)
            val bril = adjust.brilliance / 50f
            if (abs(bril) > 0.01f) {
                val mid = sin(v * PI.toFloat())
                v = (v + bril * 0.16f * mid).coerceIn(0f, 1f)
            }

            // Color Wheel Lift / Gamma / Gain
            val wLift = (1f - v).pow(2f)
            val wGamma = 2f * v * (1f - v)
            val wGain = v * v
            val chShift = when (chIdx) {
                0 -> liftRgb.first * wLift + gammaRgb.first * wGamma + gainRgb.first * wGain
                1 -> liftRgb.second * wLift + gammaRgb.second * wGamma + gainRgb.second * wGain
                else -> liftRgb.third * wLift + gammaRgb.third * wGamma + gainRgb.third * wGain
            }
            val lumaShift = wheel.liftLuma * 0.25f * wLift +
                wheel.gammaLuma * 0.25f * wGamma +
                wheel.gainLuma * 0.25f * wGain
            v = (v + chShift + lumaShift).coerceIn(0f, 1f)

            // RGB Curve + Master White Curve
            val cIdx = (v * 255f).roundToInt().coerceIn(0, 255)
            v = chLut[cIdx]
            val wIdx = (v * 255f).roundToInt().coerceIn(0, 255)
            v = whiteLut[wIdx]

            // Matte Fade (lifts black floor)
            val fadeAmt = (adjust.fade / 100f).coerceIn(0f, 1f)
            if (fadeAmt > 0.005f) {
                val floor = fadeAmt * 0.20f
                v = (floor + v * (1f - floor * 0.7f)).coerceIn(0f, 1f)
            }
            return v
        }

        fun sampleBaseCube(rf: Float, gf: Float, bf: Float): Triple<Float, Float, Float> {
            if (baseCube == null || baseCube.isEmpty() || baseCubeIntensity <= 0.01f) {
                return Triple(rf, gf, bf)
            }
            val s = baseCube.size
            val ir = (rf * (s - 1)).roundToInt().coerceIn(0, s - 1)
            val ig = (gf * (s - 1)).roundToInt().coerceIn(0, s - 1)
            val ib = (bf * (s - 1)).roundToInt().coerceIn(0, s - 1)
            val packed = baseCube[ir][ig][ib]
            val lr = ((packed shr 16) and 0xFF) / 255f
            val lg = ((packed shr 8) and 0xFF) / 255f
            val lb = (packed and 0xFF) / 255f
            val alpha = baseCubeIntensity.coerceIn(0f, 1f)
            return Triple(
                rf + (lr - rf) * alpha,
                gf + (lg - gf) * alpha,
                bf + (lb - bf) * alpha,
            )
        }

        // Apply selective 8-band HSL shifts inside the 3D LUT so selective color grading is GPU-native
        val hasHsl = adjust.hsl.values.any { abs(it.hue) > 0.5f || abs(it.saturation) > 0.5f || abs(it.lightness) > 0.5f }
        fun applyHsl(r0: Float, g0: Float, b0: Float): Triple<Float, Float, Float> {
            if (!hasHsl) return Triple(r0, g0, b0)
            val (h0, s0, v0) = rgbToHsv(r0, g0, b0)
            var h = h0
            var s = s0
            var v = v0
            if (s > 0.05f) {
                var dh = 0f
                var ds = 0f
                var dl = 0f
                for (band in HslColorBand.entries) {
                    val adj = adjust.hsl[band] ?: continue
                    var diff = abs(h - band.hueCenterDeg)
                    if (diff > 180f) diff = 360f - diff
                    val w = ((1f - diff / 42f).coerceIn(0f, 1f))
                    if (w > 0f) {
                        dh += adj.hue * 0.7f * w
                        ds += (adj.saturation / 50f) * 0.5f * w
                        dl += (adj.lightness / 50f) * 0.25f * w
                    }
                }
                h = ((h + dh) % 360f + 360f) % 360f
                s = (s * (1f + ds)).coerceIn(0f, 1f)
                v = (v + dl).coerceIn(0f, 1f)
                return hsvToRgb(h, s, v)
            }
            return Triple(r0, g0, b0)
        }

        return Array(n) { r ->
            val rf = r / (n - 1f)
            Array(n) { g ->
                val gf = g / (n - 1f)
                IntArray(n) { b ->
                    val bf = b / (n - 1f)
                    val (lr, lg, lb) = sampleBaseCube(rf, gf, bf)
                    val (hr, hg, hb) = applyHsl(lr, lg, lb)
                    val or = applyToneAndCurve(hr, redLut, 0)
                    val og = applyToneAndCurve(hg, greenLut, 1)
                    val ob = applyToneAndCurve(hb, blueLut, 2)
                    val ri = (or * 255f).roundToInt().coerceIn(0, 255)
                    val gi = (og * 255f).roundToInt().coerceIn(0, 255)
                    val bi = (ob * 255f).roundToInt().coerceIn(0, 255)
                    (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
                }
            }
        }
    }

    private fun rgbToHsv(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val h = when {
            delta < 1e-5f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val s = if (max < 1e-5f) 0f else delta / max
        return Triple(h, s, max)
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Float, Float, Float> {
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (rp, gp, bp) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Triple(rp + m, gp + m, bp + m)
    }

    fun isNeutral(adjust: AdjustSettings, baseCube: Array<Array<IntArray>>?): Boolean {
        val hasHsl = adjust.hsl.values.any { abs(it.hue) > 0.5f || abs(it.saturation) > 0.5f || abs(it.lightness) > 0.5f }
        return baseCube == null && !adjust.hasToneCurveChanges() && !hasHsl
    }
}
