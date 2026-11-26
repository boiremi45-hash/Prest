package com.example.data.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
enum class EasingGraph(val label: String) {
    NONE("None"),
    LINEAR("Linear"),
    EASE_IN("Ease In"),
    EASE_OUT("Ease Out"),
    QUAD_IN("Quad In"),
    QUAD_OUT("Quad Out"),
    CUBIC_IN("Cubic In"),
    CUBIC_OUT("Cubic Out"),
    EASE("Ease"),
    QUAD_EASE("Quad Ease"),
    CUBIC_EASE("Cubic Ease"),
    CIRCULAR("Circular"),
    REBOUND("Rebound"),
    FLOW_1("Flow 1"),
    FLOW_2("Flow 2"),
    FLOW_3("Flow 3"),
    FLOW_4("Flow 4"),
    FLOW_5("Flow 5"),
    FLOW_6("Flow 6"),
    FLOW_7("Flow 7"),
    BOUNCE("Bounce");

    fun evaluate(rawT: Float): Float {
        val t = rawT.coerceIn(0f, 1f)
        return when (this) {
            NONE -> if (t >= 1f) 1f else 0f
            LINEAR -> t
            EASE_IN -> 1f - cos((t * PI) / 2.0).toFloat()
            EASE_OUT -> sin((t * PI) / 2.0).toFloat()
            QUAD_IN -> t * t
            QUAD_OUT -> 1f - (1f - t) * (1f - t)
            CUBIC_IN -> t * t * t
            CUBIC_OUT -> 1f - (1f - t).pow(3)
            EASE -> (-(cos(PI * t) - 1.0) / 2.0).toFloat()
            QUAD_EASE -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f
            CUBIC_EASE -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
            CIRCULAR -> if (t < 0.5f) {
                ((1.0 - sqrt((1f - (2f * t).pow(2)).coerceAtLeast(0f).toDouble())) / 2.0).toFloat()
            } else {
                ((sqrt((1f - (-2f * t + 2f).pow(2)).coerceAtLeast(0f).toDouble()) + 1.0) / 2.0).toFloat()
            }
            REBOUND -> {
                val c4 = (2.0 * PI) / 3.0
                when {
                    t == 0f -> 0f
                    t == 1f -> 1f
                    else -> (2.0.pow(-10.0 * t) * sin((t * 10.0 - 0.75) * c4) + 1.0).toFloat()
                }
            }
            FLOW_1 -> cubicBezierY(t, 0.25f, 0.1f, 0.25f, 1.0f)
            FLOW_2 -> cubicBezierY(t, 0.42f, 0.0f, 0.20f, 1.0f)
            FLOW_3 -> cubicBezierY(t, 0.16f, 1.0f, 0.30f, 1.0f)
            FLOW_4 -> cubicBezierY(t, 0.70f, 0.0f, 0.30f, 1.0f)
            FLOW_5 -> cubicBezierY(t, 0.85f, 0.0f, 0.15f, 1.0f)
            FLOW_6 -> cubicBezierY(t, 0.34f, 1.45f, 0.64f, 1.0f)
            FLOW_7 -> cubicBezierY(t, 0.68f, -0.45f, 0.32f, 1.45f)
            BOUNCE -> bounceOut(t)
        }
    }

    private fun bounceOut(x: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> {
                val u = x - 1.5f / d1
                n1 * u * u + 0.75f
            }
            x < 2.5f / d1 -> {
                val u = x - 2.25f / d1
                n1 * u * u + 0.9375f
            }
            else -> {
                val u = x - 2.625f / d1
                n1 * u * u + 0.984375f
            }
        }
    }

    private fun cubicBezierY(t: Float, p1x: Float, p1y: Float, p2x: Float, p2y: Float): Float {
        // Solve X(u) = t with 6 Newton/bisection steps, then evaluate Y(u)
        var u = t
        for (i in 0 until 6) {
            val inv = 1f - u
            val x = 3f * inv * inv * u * p1x + 3f * inv * u * u * p2x + u * u * u
            val dx = 3f * inv * inv * p1x + 6f * inv * u * (p2x - p1x) + 3f * u * u * (1f - p2x)
            if (abs(dx) < 1e-4f) break
            u = (u - (x - t) / dx).coerceIn(0f, 1f)
        }
        val inv = 1f - u
        return 3f * inv * inv * u * p1y + 3f * inv * u * u * p2y + u * u * u
    }
}

@Serializable
data class KeyframeNode(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long,
    val easingToNext: EasingGraph = EasingGraph.LINEAR,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val zoomPercent: Float = 100f,
    val rotateDeg: Float = 0f,
    val opacity: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val filterIntensity: Float = 1f,
)

data class EvaluatedTransformState(
    val posX: Float,
    val posY: Float,
    val zoomPercent: Float,
    val rotateDeg: Float,
    val opacity: Float,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val filterIntensity: Float,
)

object KeyframeEvaluator {
    fun evaluate(
        keyframes: List<KeyframeNode>,
        localTimeMs: Long,
        baseTransform: TransformSettings,
        baseOpacity: Float,
        baseAdjust: AdjustSettings,
        baseFilterIntensity: Float,
    ): EvaluatedTransformState {
        if (keyframes.isEmpty()) {
            return EvaluatedTransformState(
                posX = baseTransform.posX,
                posY = baseTransform.posY,
                zoomPercent = baseTransform.zoomPercent,
                rotateDeg = baseTransform.rotateDeg,
                opacity = baseOpacity,
                brightness = baseAdjust.brightness,
                contrast = baseAdjust.contrast,
                saturation = baseAdjust.saturation,
                filterIntensity = baseFilterIntensity,
            )
        }
        val sorted = keyframes.sortedBy { it.timeMs }
        if (localTimeMs <= sorted.first().timeMs) {
            val k = sorted.first()
            return EvaluatedTransformState(
                k.posX, k.posY, k.zoomPercent, k.rotateDeg, k.opacity,
                k.brightness, k.contrast, k.saturation, k.filterIntensity,
            )
        }
        if (localTimeMs >= sorted.last().timeMs) {
            val k = sorted.last()
            return EvaluatedTransformState(
                k.posX, k.posY, k.zoomPercent, k.rotateDeg, k.opacity,
                k.brightness, k.contrast, k.saturation, k.filterIntensity,
            )
        }
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (localTimeMs in a.timeMs..b.timeMs) {
                val span = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
                val rawT = (localTimeMs - a.timeMs) / span
                val e = a.easingToNext.evaluate(rawT)
                fun lerpIfChanged(v0: Float, v1: Float): Float =
                    if (abs(v1 - v0) < 0.0001f) v0 else v0 + (v1 - v0) * e
                return EvaluatedTransformState(
                    posX = lerpIfChanged(a.posX, b.posX),
                    posY = lerpIfChanged(a.posY, b.posY),
                    zoomPercent = lerpIfChanged(a.zoomPercent, b.zoomPercent),
                    rotateDeg = lerpIfChanged(a.rotateDeg, b.rotateDeg),
                    opacity = lerpIfChanged(a.opacity, b.opacity).coerceIn(0f, 1f),
                    brightness = lerpIfChanged(a.brightness, b.brightness),
                    contrast = lerpIfChanged(a.contrast, b.contrast),
                    saturation = lerpIfChanged(a.saturation, b.saturation),
                    filterIntensity = lerpIfChanged(a.filterIntensity, b.filterIntensity).coerceIn(0f, 1f),
                )
            }
        }
        return EvaluatedTransformState(
            baseTransform.posX, baseTransform.posY, baseTransform.zoomPercent,
            baseTransform.rotateDeg, baseOpacity, baseAdjust.brightness,
            baseAdjust.contrast, baseAdjust.saturation, baseFilterIntensity,
        )
    }

    fun surroundingSegment(keyframes: List<KeyframeNode>, localTimeMs: Long): Pair<KeyframeNode, KeyframeNode>? {
        val sorted = keyframes.sortedBy { it.timeMs }
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (localTimeMs in a.timeMs..b.timeMs) return a to b
        }
        return null
    }
}

@Serializable
data class TextKeyframeNode(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long,
    val easingToNext: EasingGraph = EasingGraph.LINEAR,
    val posX: Float = 0f,
    val posY: Float = -0.68f,
    val scale: Float = 1.0f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1.0f,
)

data class EvaluatedTextState(
    val posX: Float,
    val posY: Float,
    val scale: Float,
    val rotationDeg: Float,
    val opacity: Float,
)

object TextKeyframeEvaluator {
    fun evaluate(
        keyframes: List<TextKeyframeNode>,
        localTimeMs: Long,
        baseItem: TextTrackItem,
    ): EvaluatedTextState {
        if (keyframes.isEmpty()) {
            return EvaluatedTextState(
                posX = baseItem.posX,
                posY = baseItem.posY,
                scale = baseItem.scale,
                rotationDeg = baseItem.rotationDeg,
                opacity = baseItem.opacity.coerceIn(0f, 1f),
            )
        }
        val sorted = keyframes.sortedBy { it.timeMs }
        if (localTimeMs <= sorted.first().timeMs) {
            val k = sorted.first()
            return EvaluatedTextState(k.posX, k.posY, k.scale, k.rotationDeg, k.opacity.coerceIn(0f, 1f))
        }
        if (localTimeMs >= sorted.last().timeMs) {
            val k = sorted.last()
            return EvaluatedTextState(k.posX, k.posY, k.scale, k.rotationDeg, k.opacity.coerceIn(0f, 1f))
        }
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (localTimeMs in a.timeMs..b.timeMs) {
                val span = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
                val rawT = (localTimeMs - a.timeMs) / span
                val e = a.easingToNext.evaluate(rawT)
                fun lerpIfChanged(v0: Float, v1: Float): Float =
                    if (abs(v1 - v0) < 0.0001f) v0 else v0 + (v1 - v0) * e
                return EvaluatedTextState(
                    posX = lerpIfChanged(a.posX, b.posX),
                    posY = lerpIfChanged(a.posY, b.posY),
                    scale = lerpIfChanged(a.scale, b.scale).coerceIn(0.35f, 3.5f),
                    rotationDeg = lerpIfChanged(a.rotationDeg, b.rotationDeg),
                    opacity = lerpIfChanged(a.opacity, b.opacity).coerceIn(0f, 1f),
                )
            }
        }
        return EvaluatedTextState(baseItem.posX, baseItem.posY, baseItem.scale, baseItem.rotationDeg, baseItem.opacity)
    }

    fun surroundingSegment(keyframes: List<TextKeyframeNode>, localTimeMs: Long): Pair<TextKeyframeNode, TextKeyframeNode>? {
        val sorted = keyframes.sortedBy { it.timeMs }
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (localTimeMs in a.timeMs..b.timeMs) return a to b
        }
        return null
    }
}

@Serializable
data class VolumeKeyframeNode(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long,
    val volumePercent: Int = 100, // 0..1000
    val easingToNext: EasingGraph = EasingGraph.LINEAR,
)

object VolumeKeyframeEvaluator {
    fun evaluate(
        keyframes: List<VolumeKeyframeNode>,
        localTimeMs: Long,
        baseVolumePercent: Int,
    ): Int {
        if (keyframes.isEmpty()) return baseVolumePercent.coerceIn(0, 1000)
        val sorted = keyframes.sortedBy { it.timeMs }
        if (localTimeMs <= sorted.first().timeMs) return sorted.first().volumePercent.coerceIn(0, 1000)
        if (localTimeMs >= sorted.last().timeMs) return sorted.last().volumePercent.coerceIn(0, 1000)
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (localTimeMs in a.timeMs..b.timeMs) {
                val span = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
                val rawT = ((localTimeMs - a.timeMs) / span).coerceIn(0f, 1f)
                val e = a.easingToNext.evaluate(rawT)
                return Math.round(a.volumePercent + (b.volumePercent - a.volumePercent) * e).coerceIn(0, 1000)
            }
        }
        return baseVolumePercent.coerceIn(0, 1000)
    }
}

@Serializable
enum class SpeedMode(val label: String) {
    NORMAL("Normal"),
    CURVE("Curve"),
    VELOCITY("Velocity effects"),
}

@Serializable
enum class CurveSpeedPreset(val label: String, val points: List<Float>) {
    CUSTOM("Custom", listOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f)),
    MONTAGE("Montage", listOf(1.8f, 0.5f, 1.6f, 0.4f, 1.5f)),
    HERO("Hero", listOf(1.2f, 2.5f, 0.4f, 2.2f, 1.0f)),
    BULLET("Bullet", listOf(2.8f, 0.35f, 0.35f, 2.5f, 1.2f)),
    JUMP_CUT("Jump Cut", listOf(3.2f, 0.6f, 3.0f, 0.6f, 2.8f)),
    FLASH_IN("Flash In", listOf(3.5f, 2.0f, 1.0f, 1.0f, 1.0f)),
    FLASH_OUT("Flash Out", listOf(1.0f, 1.0f, 1.2f, 2.4f, 4.0f)),
}

@Serializable
enum class VelocityPreset(val label: String, val points: List<Float>) {
    NONE("None", listOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f)),
    AUTO_VELOCITY("Auto Velocity", listOf(2.4f, 0.45f, 2.2f, 0.45f, 2.0f)),
    PULSE_BEAT("Pulse Beat", listOf(1.5f, 0.65f, 1.8f, 0.65f, 1.5f)),
    FLASH_RAMP("Flash Ramp", listOf(4.0f, 0.5f, 0.5f, 3.5f, 1.0f)),
    HERO_DROP("Hero Drop", listOf(1.8f, 0.3f, 0.3f, 0.5f, 2.8f)),
}

@Serializable
data class SpeedCurvePoint(
    val position01: Float, // 0f .. 1f
    val speed: Float,      // 0.1f .. 10.0f
)

@Serializable
data class SpeedSettings(
    val mode: SpeedMode = SpeedMode.NORMAL,
    val normalSpeed: Float = 1.0f, // 0.1f .. 100.0f
    val smoothSlowMo: Boolean = false,
    val smoothSlowMoBetterQuality: Boolean = false,
    val changeAudioPitch: Boolean = false,
    val curvePreset: CurveSpeedPreset = CurveSpeedPreset.CUSTOM,
    val curvePoints: List<Float> = listOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f),
    val curveControlPoints: List<SpeedCurvePoint> = emptyList(),
    val curveBezierSmooth: Boolean = false,
    val velocityPreset: VelocityPreset = VelocityPreset.NONE,
) {
    fun resolvedCurveControlPoints(): List<SpeedCurvePoint> {
        if (curveControlPoints.isNotEmpty()) {
            return curveControlPoints
                .map { SpeedCurvePoint(it.position01.coerceIn(0f, 1f), it.speed.coerceIn(0.1f, 10f)) }
                .sortedBy { it.position01 }
        }
        val pts = curvePoints.ifEmpty { listOf(1.0f, 1.0f) }
        if (pts.size == 1) {
            return listOf(
                SpeedCurvePoint(0f, pts.first().coerceIn(0.1f, 10f)),
                SpeedCurvePoint(1f, pts.first().coerceIn(0.1f, 10f)),
            )
        }
        return pts.mapIndexed { idx, v ->
            SpeedCurvePoint(idx / (pts.size - 1f), v.coerceIn(0.1f, 10f))
        }
    }

    fun effectiveSpeedMultiplier(): Float = when (mode) {
        SpeedMode.NORMAL -> normalSpeed.coerceIn(0.1f, 100f)
        SpeedMode.CURVE -> {
            if (curveControlPoints.isEmpty() && !curveBezierSmooth) {
                curvePoints.average().toFloat().coerceIn(0.1f, 10f)
            } else {
                val samples = 32
                var invSum = 0f
                for (i in 0..samples) {
                    invSum += 1f / speedAtProgress(i / samples.toFloat()).coerceIn(0.1f, 10f)
                }
                ((samples + 1).toFloat() / invSum.coerceAtLeast(0.01f)).coerceIn(0.1f, 10f)
            }
        }
        SpeedMode.VELOCITY -> velocityPreset.points.average().toFloat().coerceIn(0.1f, 10f)
    }

    fun speedAtProgress(progress01: Float): Float {
        val t = progress01.coerceIn(0f, 1f)
        return when (mode) {
            SpeedMode.NORMAL -> normalSpeed.coerceIn(0.1f, 100f)
            SpeedMode.CURVE -> {
                if (curveControlPoints.isEmpty() && !curveBezierSmooth) {
                    val pts = curvePoints
                    if (pts.isEmpty()) return 1f
                    if (pts.size == 1) return pts.first().coerceIn(0.1f, 100f)
                    val scaled = t * (pts.size - 1)
                    val i = scaled.toInt().coerceIn(0, pts.size - 2)
                    val f = scaled - i
                    (pts[i] + (pts[i + 1] - pts[i]) * f).coerceIn(0.1f, 100f)
                } else {
                    val ctrl = resolvedCurveControlPoints()
                    if (ctrl.isEmpty()) return 1f
                    if (ctrl.size == 1) return ctrl.first().speed.coerceIn(0.1f, 10f)
                    if (t <= ctrl.first().position01) return ctrl.first().speed.coerceIn(0.1f, 10f)
                    if (t >= ctrl.last().position01) return ctrl.last().speed.coerceIn(0.1f, 10f)
                    var segIdx = 0
                    for (i in 0 until ctrl.lastIndex) {
                        if (t >= ctrl[i].position01 && t <= ctrl[i + 1].position01) {
                            segIdx = i
                            break
                        }
                    }
                    val a = ctrl[segIdx]
                    val b = ctrl[segIdx + 1]
                    val span = (b.position01 - a.position01).coerceAtLeast(0.0001f)
                    val u = ((t - a.position01) / span).coerceIn(0f, 1f)
                    val interp = if (curveBezierSmooth) u * u * (3f - 2f * u) else u
                    (a.speed + (b.speed - a.speed) * interp).coerceIn(0.1f, 10f)
                }
            }
            SpeedMode.VELOCITY -> {
                val pts = if (velocityPreset == VelocityPreset.NONE) curvePoints else velocityPreset.points
                if (pts.isEmpty()) return 1f
                if (pts.size == 1) return pts.first().coerceIn(0.1f, 100f)
                val scaled = t * (pts.size - 1)
                val i = scaled.toInt().coerceIn(0, pts.size - 2)
                val f = scaled - i
                (pts[i] + (pts[i + 1] - pts[i]) * f).coerceIn(0.1f, 100f)
            }
        }
    }

    fun mirrored(): SpeedSettings {
        val flippedControl = resolvedCurveControlPoints()
            .map { SpeedCurvePoint((1f - it.position01).coerceIn(0f, 1f), it.speed) }
            .sortedBy { it.position01 }
        return copy(
            mode = SpeedMode.CURVE,
            curvePreset = CurveSpeedPreset.CUSTOM,
            curvePoints = flippedControl.map { it.speed },
            curveControlPoints = flippedControl,
            curveBezierSmooth = true,
        )
    }

    fun sourceProgressAtTimelineProgress(timelineProgress01: Float): Float {
        if (mode == SpeedMode.NORMAL) return timelineProgress01.coerceIn(0f, 1f)
        val tp = timelineProgress01.coerceIn(0f, 1f)
        if (tp <= 0f) return 0f
        if (tp >= 1f) return 1f
        val steps = 32
        val cumTime = FloatArray(steps + 1)
        for (i in 1..steps) {
            val midSource = (i - 0.5f) / steps
            val spd = speedAtProgress(midSource).coerceIn(0.1f, 10f)
            cumTime[i] = cumTime[i - 1] + (1f / spd)
        }
        val total = cumTime[steps].coerceAtLeast(0.0001f)
        val target = tp * total
        for (i in 1..steps) {
            if (target <= cumTime[i]) {
                val segSpan = (cumTime[i] - cumTime[i - 1]).coerceAtLeast(0.0001f)
                val frac = (target - cumTime[i - 1]) / segSpan
                return (((i - 1) + frac) / steps).coerceIn(0f, 1f)
            }
        }
        return 1f
    }

    fun timelineProgressAtSourceProgress(sourceProgress01: Float): Float {
        if (mode == SpeedMode.NORMAL) return sourceProgress01.coerceIn(0f, 1f)
        val sp = sourceProgress01.coerceIn(0f, 1f)
        if (sp <= 0f) return 0f
        if (sp >= 1f) return 1f
        val steps = 32
        val cumTime = FloatArray(steps + 1)
        for (i in 1..steps) {
            val midSource = (i - 0.5f) / steps
            val spd = speedAtProgress(midSource).coerceIn(0.1f, 10f)
            cumTime[i] = cumTime[i - 1] + (1f / spd)
        }
        val total = cumTime[steps].coerceAtLeast(0.0001f)
        val scaled = sp * steps
        val idx = scaled.toInt().coerceIn(0, steps - 1)
        val frac = scaled - idx
        val tVal = cumTime[idx] + (cumTime[idx + 1] - cumTime[idx]) * frac
        return (tVal / total).coerceIn(0f, 1f)
    }
}

@Serializable
data class TransformSettings(
    val posX: Float = 0f,         // -1.0f .. 1.0f
    val posY: Float = 0f,         // -1.0f .. 1.0f
    val zoomPercent: Float = 100f, // 100f .. 360f (300f slider + Fill Canvas support)
    val rotateDeg: Float = 0f,     // 0f .. 360f
    val mirrorHorizontal: Boolean = false,
)

fun fillCanvasZoomPercent(
    videoWidth: Int,
    videoHeight: Int,
    canvasRatio: Float,
    rotationDeg: Float = 0f,
): Float {
    val safeW = videoWidth.coerceAtLeast(1).toFloat()
    val safeH = videoHeight.coerceAtLeast(1).toFloat()
    val safeCanvasRatio = canvasRatio.coerceIn(0.25f, 4f)
    val vr = safeW / safeH

    val hw = 1f
    val hh = 1f / safeCanvasRatio
    val fw: Float
    val fh: Float
    if (vr >= safeCanvasRatio) {
        fw = hw
        fh = hw / vr
    } else {
        fh = hh
        fw = hh * vr
    }
    val rad = Math.toRadians(rotationDeg.toDouble())
    val c = abs(cos(rad)).toFloat()
    val s = abs(sin(rad)).toFloat()
    val reqHalfW = hw * c + hh * s
    val reqHalfH = hw * s + hh * c
    val scale = maxOf(reqHalfW / fw.coerceAtLeast(0.01f), reqHalfH / fh.coerceAtLeast(0.01f))
    return (scale * 100f).coerceIn(100f, 360f)
}

@Serializable
enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("Free", null),
    R_9_16("9:16", 9f / 16f),
    R_16_9("16:9", 16f / 9f),
    R_1_1("1:1", 1f),
    R_4_3("4:3", 4f / 3f),
    R_3_4("3:4", 3f / 4f),
}

@Serializable
data class CropSettings(
    val aspect: CropAspect = CropAspect.FREE,
    val rotateDeg: Float = 0f,
    val scaleCrop: Float = 1.0f,
)

@Serializable
enum class MaskShape(val label: String, val code: Int) {
    NONE("None", 0),
    SPLIT("Split", 1),
    FILMSTRIP("Filmstrip", 2),
    CIRCLE("Circle", 3),
    RECTANGLE("Rectangle", 4),
    HEART("Heart", 5),
    STAR("Star", 6),
}

@Serializable
data class MaskSettings(
    val shape: MaskShape = MaskShape.NONE,
    val feather: Float = 0.08f,
    val invert: Boolean = false,
    val sizeScale: Float = 0.72f,
)

@Serializable
enum class MotionBlurDirection(val label: String) {
    BOTH("Both"),
    FORWARD("Forward"),
    BACKWARD("Backward"),
}

@Serializable
data class MotionBlurSettings(
    val enabled: Boolean = false,
    val blurAmount: Float = 0.5f,
    val blendAmount: Float = 0.5f,
    val direction: MotionBlurDirection = MotionBlurDirection.BOTH,
    val times: Int = 2,
)

@Serializable
enum class StabilizeMode(val label: String) {
    OFF("None"),
    MINIMAL("Minimal cropping"),
    RECOMMENDED("Recommended"),
    MOST_STABLE("Most stable"),
}

@Serializable
enum class AnimationCategory(val label: String) {
    IN("In"),
    OUT("Out"),
    COMBO("Combo"),
}

@Serializable
enum class ClipAnimationType(val label: String, val code: Int) {
    NONE("None", 0),
    TRICKLING("Trickling", 1),
    STRETCHED_IN("Stretched In", 2),
    LASER_SCAN("Laser Scan", 3),
    FADE_ASIDE("Fade Aside", 4),
    GHOST("Ghost", 5),
    FLUID_BURN("Fluid Burn", 6),
    GLITCH("Glitch", 7),
}

@Serializable
data class AnimationSettings(
    val inType: ClipAnimationType = ClipAnimationType.NONE,
    val inDurationSec: Float = 0.5f,
    val outType: ClipAnimationType = ClipAnimationType.NONE,
    val outDurationSec: Float = 0.5f,
    val comboType: ClipAnimationType = ClipAnimationType.NONE,
    val comboDurationSec: Float = 0.8f,
)

@Serializable
enum class TransitionType(val label: String, val code: Int) {
    NONE("None", 0),
    DREAMY_FLASH("Dreamy Flash", 1),
    GLITCH_SPLIT("Glitch Split", 2),
    PUMPKIN_DROP("Pumpkin Drop", 3),
    SPARKLE_POPS("Sparkle Pops", 4),
    LIGHT_TWIST("Light Twist", 5),
    SCANLINE_GLITCH("Scanline Glitch", 6),
    PULSE_FADE("Pulse Fade", 7),
}

@Serializable
data class TransitionSpec(
    val id: String = UUID.randomUUID().toString(),
    val afterClipId: String,
    val type: TransitionType = TransitionType.NONE,
    val durationSec: Float = 0.4f,
)

@Serializable
enum class EffectMainTab(val label: String) {
    VIDEO("Video effects"),
    BODY("Body effects"),
    STYLE("Style"),
    AI("AI effects"),
}

@Serializable
enum class EffectSubCategory(val label: String) {
    TRENDING("Trending"),
    CLASSIC("Classic"),
    NEW("NEW"),
}

@Serializable
enum class CapCutEffectType(
    val label: String,
    val code: Int,
    val mainTab: EffectMainTab,
    val category: EffectSubCategory,
) {
    NONE("None", 0, EffectMainTab.VIDEO, EffectSubCategory.TRENDING),
    MATRIX_PANELS("Matrix Panels", 1, EffectMainTab.VIDEO, EffectSubCategory.TRENDING),
    ANALOG_SNOW("Analog Snow", 2, EffectMainTab.VIDEO, EffectSubCategory.CLASSIC),
    BLUR("Blur", 3, EffectMainTab.VIDEO, EffectSubCategory.CLASSIC),
    FLASH_COPIES("Flash Copies", 4, EffectMainTab.VIDEO, EffectSubCategory.TRENDING),
    DREAMLAND("Dreamland", 5, EffectMainTab.STYLE, EffectSubCategory.TRENDING),
    PHOTO_STRIP("Photo Strip", 6, EffectMainTab.VIDEO, EffectSubCategory.CLASSIC),
    LASER_GRID("Laser Grid", 7, EffectMainTab.BODY, EffectSubCategory.NEW),
    NEON_RAY("Neon Ray", 8, EffectMainTab.BODY, EffectSubCategory.TRENDING),
    RETRO_RAYS("Retro Rays", 9, EffectMainTab.STYLE, EffectSubCategory.CLASSIC),
    LED_MATRIX("LED Matrix", 10, EffectMainTab.AI, EffectSubCategory.NEW),
    GOLDEN_RAY("Golden Ray", 11, EffectMainTab.STYLE, EffectSubCategory.NEW),
    TRACKING_GLITCH("Tracking Glitch", 12, EffectMainTab.AI, EffectSubCategory.TRENDING),
}

@Serializable
data class VideoEffectLayer(
    val id: String = UUID.randomUUID().toString(),
    val type: CapCutEffectType,
    val intensity: Float = 0.75f,
    val startMs: Long = 0L,
    val endMs: Long = 8_000L,
    val applyToAllVideos: Boolean = false,
)

@Serializable
enum class HslColorBand(val label: String, val colorArgb: Long, val hueCenterDeg: Float) {
    RED("Red", 0xFFEF4444L, 0f),
    ORANGE("Orange", 0xFFF97316L, 28f),
    YELLOW("Yellow", 0xFFEAB308L, 58f),
    GREEN("Green", 0xFF22C55EL, 120f),
    CYAN("Cyan", 0xFF06B6D4L, 180f),
    BLUE("Blue", 0xFF3B82F6L, 225f),
    PURPLE("Purple", 0xFF8B5CF6L, 272f),
    MAGENTA("Magenta", 0xFFEC4899L, 320f),
}

@Serializable
data class HslBandValue(
    val hue: Float = 0f,        // -50f..50f
    val saturation: Float = 0f, // -50f..50f
    val lightness: Float = 0f,  // -50f..50f
)

@Serializable
data class ColorWheelSpec(
    val liftHueDeg: Float = 0f,
    val liftAmount: Float = 0f, // 0f..1f
    val liftLuma: Float = 0f,   // -0.5f..0.5f
    val gammaHueDeg: Float = 0f,
    val gammaAmount: Float = 0f,
    val gammaLuma: Float = 0f,
    val gainHueDeg: Float = 0f,
    val gainAmount: Float = 0f,
    val gainLuma: Float = 0f,
)

@Serializable
enum class CurveChannel(val label: String, val accentArgb: Long) {
    WHITE("White", 0xFFF4F5FAL),
    RED("Red", 0xFFEF4444L),
    GREEN("Green", 0xFF22C55EL),
    BLUE("Blue", 0xFF3B82F6L),
}

@Serializable
data class CurvePoint(
    val x: Float, // 0f..1f
    val y: Float, // 0f..1f
)

@Serializable
data class RgbCurvesSpec(
    val white: List<CurvePoint> = defaultCurvePoints(),
    val red: List<CurvePoint> = defaultCurvePoints(),
    val green: List<CurvePoint> = defaultCurvePoints(),
    val blue: List<CurvePoint> = defaultCurvePoints(),
) {
    fun pointsFor(channel: CurveChannel): List<CurvePoint> = when (channel) {
        CurveChannel.WHITE -> white
        CurveChannel.RED -> red
        CurveChannel.GREEN -> green
        CurveChannel.BLUE -> blue
    }

    fun isIdentity(): Boolean =
        isLinear(white) && isLinear(red) && isLinear(green) && isLinear(blue)

    private fun isLinear(pts: List<CurvePoint>): Boolean =
        pts.all { abs(it.x - it.y) < 0.01f }

    companion object {
        fun defaultCurvePoints(): List<CurvePoint> = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.25f, 0.25f),
            CurvePoint(0.5f, 0.5f),
            CurvePoint(0.75f, 0.75f),
            CurvePoint(1f, 1f),
        )
    }
}

@Serializable
data class AdjustSettings(
    val brightness: Float = 0f,   // -50..50
    val contrast: Float = 0f,     // -50..50
    val saturation: Float = 0f,   // -50..50
    val brilliance: Float = 0f,   // -50..50
    val sharpen: Float = 0f,      // 0..100
    val clarity: Float = 0f,      // -50..50
    val highlights: Float = 0f,   // -50..50
    val shadows: Float = 0f,      // -50..50
    val whites: Float = 0f,       // -50..50
    val blacks: Float = 0f,       // -50..50
    val temp: Float = 0f,         // -50..50
    val hue: Float = 0f,          // -50..50
    val fade: Float = 0f,         // 0..100
    val vignette: Float = 0f,     // -50..50
    val grain: Float = 0f,        // 0..100
    val hsl: Map<HslColorBand, HslBandValue> = emptyMap(),
    val colorWheel: ColorWheelSpec = ColorWheelSpec(),
    val rgbCurves: RgbCurvesSpec = RgbCurvesSpec(),
) {
    fun hasToneCurveChanges(): Boolean =
        !rgbCurves.isIdentity() ||
            abs(brilliance) > 0.5f ||
            abs(highlights) > 0.5f ||
            abs(shadows) > 0.5f ||
            abs(whites) > 0.5f ||
            abs(blacks) > 0.5f ||
            fade > 0.5f ||
            colorWheel.liftAmount > 0.01f ||
            colorWheel.gammaAmount > 0.01f ||
            colorWheel.gainAmount > 0.01f ||
            abs(colorWheel.liftLuma) > 0.01f ||
            abs(colorWheel.gammaLuma) > 0.01f ||
            abs(colorWheel.gainLuma) > 0.01f
}

@Serializable
enum class OverlayBlendMode(val label: String) {
    NORMAL("Normal"),
    FILTER("Filter"),
    OVERLAY("Overlay"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
}

@Serializable
data class OverlayTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val isVideo: Boolean,
    val displayName: String,
    val startMs: Long = 0L,
    val endMs: Long = 5_000L,
    val posX: Float = 0f,     // normalized -1f..1f
    val posY: Float = 0f,     // normalized -1f..1f
    val scale: Float = 0.48f, // 0.15f..1.8f
    val rotationDeg: Float = 0f,
    val opacity: Float = 1.0f,
    val blendMode: OverlayBlendMode = OverlayBlendMode.NORMAL,
    val autoRemoveBg: Boolean = false,
    val chromaEnabled: Boolean = false,
    val chromaColorArgb: Long = 0xFF1FE066L,
    val chromaIntensity: Float = 0.38f,
    val chromaShadow: Float = 0.15f,
)

@Serializable
enum class TimelineTrackKind {
    TEXT,
    VIDEO,
    AUDIO,
}

@Serializable
data class TimelineTrackConfig(
    val id: String,
    val kind: TimelineTrackKind,
    val trackIndex: Int,
    val label: String,
    val subtitle: String = "",
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false,
    val heightDp: Float = 54f,
)

fun defaultTimelineTracks(videoCount: Int = 3): List<TimelineTrackConfig> {
    val safeVideos = videoCount.coerceIn(1, 8)
    val list = mutableListOf<TimelineTrackConfig>()
    list += TimelineTrackConfig(
        id = "track_text_0",
        kind = TimelineTrackKind.TEXT,
        trackIndex = 0,
        label = "TEXT",
        subtitle = "Text / Sticker",
        heightDp = 38f,
    )
    for (idx in 0 until safeVideos) {
        val subtitle = when (idx) {
            0 -> "Main video"
            1 -> "Overlay / PIP"
            2 -> "Extra overlay"
            else -> "Video layer"
        }
        list += TimelineTrackConfig(
            id = "track_video_$idx",
            kind = TimelineTrackKind.VIDEO,
            trackIndex = idx,
            label = "V${idx + 1}",
            subtitle = subtitle,
            heightDp = if (idx == 0) 58f else 50f,
        )
    }
    list += TimelineTrackConfig(
        id = "track_audio_0",
        kind = TimelineTrackKind.AUDIO,
        trackIndex = 0,
        label = "A1",
        subtitle = "Music",
        heightDp = 38f,
    )
    list += TimelineTrackConfig(
        id = "track_audio_1",
        kind = TimelineTrackKind.AUDIO,
        trackIndex = 1,
        label = "A2",
        subtitle = "Sound / Voice",
        heightDp = 38f,
    )
    return list
}

@Serializable
data class AudioTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String? = null,
    val title: String,
    val trackIndex: Int = 0, // 0 = A1 (Music), 1 = A2 (Sound / Voiceover)
    val startMs: Long = 0L,
    val endMs: Long = 8_000L,
    val sourceStartMs: Long = 0L,
    val volumePercent: Int = 100,
    val reduceNoise: Boolean = false,
    val volumeKeyframes: List<VolumeKeyframeNode> = emptyList(),
)

@Serializable
data class TextTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isCaption: Boolean = false,
    val startMs: Long = 0L,
    val endMs: Long = 4_000L,
    val posX: Float = 0f,
    val posY: Float = -0.68f,
    val scale: Float = 1.0f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1.0f,
    val colorArgb: Long = 0xFFFFFFFFL,
    val bold: Boolean = true,
    val keyframes: List<TextKeyframeNode> = emptyList(),
)

@Serializable
enum class SmoothReverseRole {
    NONE,
    ORIGINAL,
    REVERSED,
}

@Serializable
data class SmoothReverseConfig(
    val zoomPercent: Float = 140f,
    val startSpeed: Float = 10.0f,
    val endSpeed: Float = 0.1f,
    val segmentDurationMs: Long = 400L,
    val originalTrimStartMs: Long = 0L,
    val originalTrimEndMs: Long = 8_000L,
)

@Serializable
enum class TimesliceDirection(val label: String, val shortLabel: String) {
    LEFT_TO_RIGHT("Left to Right", "L → R"),
    RIGHT_TO_LEFT("Right to Left", "R → L"),
    TOP_TO_BOTTOM("Top to Bottom", "T → B"),
    BOTTOM_TO_TOP("Bottom to Top", "B → T"),
}

@Serializable
data class TimesliceSettings(
    val enabled: Boolean = false,
    val slices: Int = 10,                // 3 .. 30
    val direction: TimesliceDirection = TimesliceDirection.LEFT_TO_RIGHT,
    val timeSpreadSec: Float = 0.3f,     // 0.1f .. 1.0f between slices
    val feather: Float = 25f,            // 0f .. 100f soft-edge blend
    val intensity: Float = 1.0f,         // 0f .. 1.0f effect blend
    val previewEnabled: Boolean = true,  // Preview On / Off
) {
    fun clampedSlices(): Int = slices.coerceIn(3, 30)

    fun timeSpreadMs(): Long = (timeSpreadSec.coerceIn(0.1f, 1.0f) * 1000f).toLong()

    fun isHorizontalSlices(): Boolean =
        direction == TimesliceDirection.LEFT_TO_RIGHT || direction == TimesliceDirection.RIGHT_TO_LEFT

    /**
     * Maps visual slice index `i` (0..N-1 left-to-right or top-to-bottom) to sampled source timestamp,
     * applying T + (step * timeSpread) along the active direction and clamping cleanly within [clipStartMs, clipEndMs].
     */
    fun sampleTimeMs(
        baseTimeMs: Long,
        visualSliceIndex: Int,
        clipStartMs: Long,
        clipEndMs: Long,
    ): Long {
        val count = clampedSlices()
        val idx = visualSliceIndex.coerceIn(0, count - 1)
        val directionalStep = when (direction) {
            TimesliceDirection.LEFT_TO_RIGHT,
            TimesliceDirection.TOP_TO_BOTTOM -> idx
            TimesliceDirection.RIGHT_TO_LEFT,
            TimesliceDirection.BOTTOM_TO_TOP -> (count - 1 - idx)
        }
        val raw = baseTimeMs + directionalStep.toLong() * timeSpreadMs()
        val safeStart = clipStartMs.coerceAtLeast(0L)
        val safeEnd = clipEndMs.coerceAtLeast(safeStart)
        return raw.coerceIn(safeStart, safeEnd)
    }
}

@Serializable
data class VideoClipSegment(
    val id: String = UUID.randomUUID().toString(),
    val sourceUriString: String? = null,
    val displayName: String = "Main Clip",
    val trackIndex: Int = 0, // 0 = V1 (Main), 1 = V2 (Overlay/PIP), 2 = V3 (Extra Overlay), ...
    val width: Int = 1920,
    val height: Int = 1080,
    val timelineStartMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 8_000L,
    val sourceDurationMs: Long = 8_000L,
    val isReversed: Boolean = false,
    val reversedUriString: String? = null,
    val reverseAudio: Boolean = true,
    val cutFlashPunchOnStart: Boolean = false,
    val smoothReverseRole: SmoothReverseRole = SmoothReverseRole.NONE,
    val smoothReverseGroupId: String? = null,
    val smoothReverseConfig: SmoothReverseConfig? = null,
    val timeslice: TimesliceSettings = TimesliceSettings(),
    val isFrozen: Boolean = false,
    val freezeAtMs: Long = 0L,
    val freezeDurationMs: Long = 3_000L,
    val volumePercent: Int = 100, // 0..1000
    val isMuted: Boolean = false,
    val reduceNoise: Boolean = false,
    val volumeKeyframes: List<VolumeKeyframeNode> = emptyList(),
    val beatMarkersMs: List<Long> = emptyList(),
    val opacity: Float = 1.0f,
    val speed: SpeedSettings = SpeedSettings(),
    val transform: TransformSettings = TransformSettings(),
    val crop: CropSettings = CropSettings(),
    val adjust: AdjustSettings = AdjustSettings(),
    val mask: MaskSettings = MaskSettings(),
    val motionBlur: MotionBlurSettings = MotionBlurSettings(),
    val stabilize: StabilizeMode = StabilizeMode.OFF,
    val animation: AnimationSettings = AnimationSettings(),
    val keyframes: List<KeyframeNode> = emptyList(),
) {
    fun baseDurationMs(): Long = if (isFrozen) {
        freezeDurationMs.coerceAtLeast(500L)
    } else {
        (trimEndMs - trimStartMs).coerceAtLeast(200L)
    }

    fun timelineDurationMs(): Long {
        val spd = speed.effectiveSpeedMultiplier().coerceAtLeast(0.1f)
        return (baseDurationMs() / spd).toLong().coerceAtLeast(200L)
    }

    fun hasReversedMediaReady(): Boolean = !reversedUriString.isNullOrBlank()

    fun effectiveUriString(): String? =
        if (isReversed && !reversedUriString.isNullOrBlank()) reversedUriString else sourceUriString

    fun effectiveTrimStartMs(): Long = when {
        isFrozen -> freezeAtMs.coerceAtLeast(0L)
        isReversed && !reversedUriString.isNullOrBlank() -> 0L
        else -> trimStartMs.coerceAtLeast(0L)
    }

    fun effectiveTrimEndMs(): Long = when {
        isFrozen -> (freezeAtMs + 250L).coerceAtMost(sourceDurationMs.coerceAtLeast(300L))
        isReversed && !reversedUriString.isNullOrBlank() -> baseDurationMs()
        else -> trimEndMs.coerceAtLeast(trimStartMs + 200L)
    }

    fun effectiveSourceDurationMs(): Long =
        if (isReversed && !reversedUriString.isNullOrBlank()) baseDurationMs() else sourceDurationMs
}

@Serializable
data class TimelineSnapshot(
    val clips: List<VideoClipSegment> = emptyList(),
    val transitions: List<TransitionSpec> = emptyList(),
    val effectLayers: List<VideoEffectLayer> = emptyList(),
    val overlays: List<OverlayTrackItem> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    val textTracks: List<TextTrackItem> = emptyList(),
    val adjust: AdjustSettings = AdjustSettings(),
    val activePreset: FfxPreset = FfxPreset(),
    val filterIntensity: Float = 1.0f,
    val hasEndingCard: Boolean = false,
    val canvasBackgroundArgb: Long = 0xFF05060AL,
    val videoTrackCount: Int = 3,
    val trackConfigs: List<TimelineTrackConfig> = defaultTimelineTracks(3),
) {
    fun effectiveVideoTrackCount(): Int = maxOf(
        videoTrackCount.coerceIn(1, 8),
        (clips.maxOfOrNull { it.trackIndex } ?: 0) + 1,
        3,
    ).coerceIn(1, 8)

    fun normalizedTrackConfigs(): List<TimelineTrackConfig> {
        val count = effectiveVideoTrackCount()
        val defaults = defaultTimelineTracks(count)
        val byId = trackConfigs.associateBy { it.id }
        return defaults.map { def -> byId[def.id] ?: def }
    }

    fun trackConfigFor(kind: TimelineTrackKind, trackIndex: Int): TimelineTrackConfig? =
        normalizedTrackConfigs().firstOrNull { it.kind == kind && it.trackIndex == trackIndex }

    fun isVideoTrackHidden(trackIndex: Int): Boolean =
        trackConfigFor(TimelineTrackKind.VIDEO, trackIndex)?.isHidden == true

    fun isVideoTrackLocked(trackIndex: Int): Boolean =
        trackConfigFor(TimelineTrackKind.VIDEO, trackIndex)?.isLocked == true

    fun isVideoTrackMuted(trackIndex: Int): Boolean =
        trackConfigFor(TimelineTrackKind.VIDEO, trackIndex)?.isMuted == true

    fun isTextTrackHidden(): Boolean =
        trackConfigFor(TimelineTrackKind.TEXT, 0)?.isHidden == true

    fun isTextTrackLocked(): Boolean =
        trackConfigFor(TimelineTrackKind.TEXT, 0)?.isLocked == true

    fun isAudioTrackHidden(trackIndex: Int): Boolean =
        trackConfigFor(TimelineTrackKind.AUDIO, trackIndex)?.isHidden == true

    fun isAudioTrackLocked(trackIndex: Int): Boolean =
        trackConfigFor(TimelineTrackKind.AUDIO, trackIndex)?.isLocked == true

    fun isAudioTrackMuted(trackIndex: Int): Boolean =
        trackConfigFor(TimelineTrackKind.AUDIO, trackIndex)?.isMuted == true
}

@Serializable
data class PersistedProjectState(
    val projectId: String,
    val title: String,
    val aspectPreset: AspectPreset = AspectPreset.RATIO_9_16,
    val resolutionLabel: String = "1080p",
    val fps: Int = 30,
    val canvasFitMode: CanvasFitMode = CanvasFitMode.FIT,
    val playheadMs: Long = 0L,
    val timelineZoom: Float = 1.0f,
    val thumbnailPath: String? = null,
    val snapshot: TimelineSnapshot,
)

data class ClipTimelineSpan(
    val clip: VideoClipSegment,
    val startMs: Long,
    val endMs: Long,
)

fun computeTrackSpans(clips: List<VideoClipSegment>, trackIndex: Int): List<ClipTimelineSpan> {
    return clips
        .filter { it.trackIndex == trackIndex }
        .sortedBy { it.timelineStartMs }
        .map { clip ->
            val start = clip.timelineStartMs.coerceAtLeast(0L)
            ClipTimelineSpan(clip = clip, startMs = start, endMs = start + clip.timelineDurationMs())
        }
}

fun computeTotalVideoDurationMs(clips: List<VideoClipSegment>): Long {
    if (clips.isEmpty()) return 1_000L
    val clipEnd = clips.sortedBy { it.timelineStartMs }.maxOfOrNull { clip ->
        clip.timelineStartMs.coerceAtLeast(0L) + clip.timelineDurationMs()
    } ?: 0L
    return clipEnd.coerceAtLeast(1_000L)
}

fun computeTimelineWidthPx(totalDurationMs: Long, pxPerSec: Float): Float =
    ((totalDurationMs.coerceAtLeast(1_000L) / 1000f) * pxPerSec).coerceAtLeast(1f)

fun isTimelineItemActiveAt(
    startMs: Long,
    endMs: Long,
    playheadMs: Long,
    @Suppress("UNUSED_PARAMETER") hasNextContiguous: Boolean = false,
): Boolean {
    if (endMs <= startMs) return false
    return playheadMs >= startMs && playheadMs < endMs
}

fun findActiveSpanIndex(spans: List<ClipTimelineSpan>, playheadMs: Long): Int {
    for (i in spans.indices) {
        val span = spans[i]
        val hasNextContiguous = i < spans.lastIndex &&
            abs(spans[i + 1].startMs - span.endMs) <= 20L
        val match = if (hasNextContiguous) {
            playheadMs >= span.startMs && playheadMs < span.endMs
        } else {
            playheadMs in span.startMs..span.endMs
        }
        if (match) return i
    }
    return -1
}

fun findPreviewSpanIndex(spans: List<ClipTimelineSpan>, playheadMs: Long): Int {
    if (spans.isEmpty()) return -1
    val exact = findActiveSpanIndex(spans, playheadMs)
    if (exact >= 0) return exact
    val lastEnd = spans.maxOfOrNull { it.endMs } ?: 0L
    if (playheadMs >= lastEnd) return spans.lastIndex
    return -1
}
