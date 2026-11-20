package com.example.domain.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.GlEffect
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SingleColorLut
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.effect.TimestampWrapper
import androidx.media3.transformer.Effects
import com.example.data.model.FfxPreset
import com.example.data.model.LayerKind
import com.example.data.model.LayerShapeType
import com.example.data.model.TimelineLayer
import com.example.domain.lut.CubeLutParser
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@UnstableApi
object VideoEffectsEngine {

    fun buildPreviewEffectsForTimeline(
        layers: List<TimelineLayer>,
        clipDurationMs: Long,
        bypass: Boolean,
    ): List<Effect> {
        if (bypass) return emptyList()
        return buildEffectsListForTimeline(
            layers = layers,
            clipDurationMs = clipDurationMs,
            targetHeight = null,
        )
    }

    fun buildExportEffectsForTimeline(
        layers: List<TimelineLayer>,
        clipDurationMs: Long,
        targetHeight: Int?,
    ): Effects {
        val videoEffects = buildEffectsListForTimeline(
            layers = layers,
            clipDurationMs = clipDurationMs,
            targetHeight = targetHeight,
        )
        return Effects(emptyList(), videoEffects)
    }

    private fun buildEffectsListForTimeline(
        layers: List<TimelineLayer>,
        clipDurationMs: Long,
        targetHeight: Int?,
    ): List<Effect> {
        val effects = mutableListOf<Effect>()
        val visibleLayers = layers.filter { it.isVisible }
        val safeClipDuration = clipDurationMs.coerceAtLeast(1_000L)

        // 1. Video layer transforms (rotation / horizontal flip) + direct preset on video layer
        visibleLayers.filter { it.kind == LayerKind.VIDEO }.forEach { videoLayer ->
            if (videoLayer.rotationStepDegrees != 0 || videoLayer.flipHorizontal) {
                effects.add(
                    ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees(videoLayer.rotationStepDegrees.toFloat())
                        .setScale(if (videoLayer.flipHorizontal) -1f else 1f, 1f)
                        .build()
                )
            }
            videoLayer.preset?.let { preset ->
                appendPresetGlEffects(
                    target = effects,
                    preset = preset,
                    startMs = videoLayer.startMs,
                    endMs = videoLayer.endMs,
                    clipDurationMs = safeClipDuration,
                    opacityMultiplier = videoLayer.opacity,
                )
            }
        }

        // 2. Preset / FX Adjustment Layers on timeline (supports Trim & Split ranges via TimestampWrapper!)
        visibleLayers.filter { it.kind == LayerKind.PRESET_FX }.forEach { fxLayer ->
            val preset = fxLayer.preset ?: return@forEach
            appendPresetGlEffects(
                target = effects,
                preset = preset,
                startMs = fxLayer.startMs,
                endMs = fxLayer.endMs,
                clipDurationMs = safeClipDuration,
                opacityMultiplier = fxLayer.opacity,
            )
        }

        // 3. Shape & Text Overlay Layers rendered into GPU OverlayEffect
        val overlays = mutableListOf<TextureOverlay>()
        visibleLayers.forEach { layer ->
            when (layer.kind) {
                LayerKind.SHAPE -> {
                    val shapeType = layer.shapeType ?: return@forEach
                    val bmp = createShapeLayerBitmap(
                        shapeType = shapeType,
                        colorHex = layer.accentColorHex,
                        hasBorderShadow = layer.hasBorderShadow,
                        opacity = (layer.opacity * layer.blendStyle.alphaDefault).coerceIn(0.05f, 1f),
                    )
                    overlays.add(
                        BitmapOverlay.createStaticBitmapOverlay(
                            bmp,
                            StaticOverlaySettings.Builder()
                                .setAlphaScale((layer.opacity * layer.blendStyle.alphaDefault).coerceIn(0.05f, 1f))
                                .setRotationDegrees(layer.rotationStepDegrees.toFloat())
                                .build()
                        )
                    )
                }
                LayerKind.TEXT -> {
                    val label = layer.textContent.trim()
                    if (label.isNotEmpty()) {
                        val spannable = SpannableString(label).apply {
                            setSpan(
                                ForegroundColorSpan(layer.accentColorHex.toInt()),
                                0,
                                length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                            setSpan(
                                StyleSpan(Typeface.BOLD),
                                0,
                                length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }
                        val settings = StaticOverlaySettings.Builder()
                            .setAlphaScale(layer.opacity.coerceIn(0.1f, 1f))
                            .setBackgroundFrameAnchor(0f, -0.72f)
                            .setOverlayFrameAnchor(0f, 0f)
                            .setRotationDegrees(layer.rotationStepDegrees.toFloat())
                            .build()
                        overlays.add(TextOverlay.createStaticTextOverlay(spannable, settings))
                    }
                }
                else -> Unit
            }
        }
        if (overlays.isNotEmpty()) {
            effects.add(OverlayEffect(overlays))
        }

        // 4. Export resolution scaling
        if (targetHeight != null && targetHeight > 0) {
            effects.add(Presentation.createForHeight(targetHeight))
        }

        return effects
    }

    private fun appendPresetGlEffects(
        target: MutableList<Effect>,
        preset: FfxPreset,
        startMs: Long,
        endMs: Long,
        clipDurationMs: Long,
        opacityMultiplier: Float,
    ) {
        val rawEffects = mutableListOf<GlEffect>()

        // A. Exact 3D LUT (.cube file OR Curves/Levels 3D LUT) via hardware SingleColorLut
        if (preset.lutSize >= 2 && preset.lutData.isNotBlank()) {
            CubeLutParser.decodeToCubeArray(preset.lutSize, preset.lutData)?.let { cube ->
                rawEffects.add(SingleColorLut.createFromCube(cube))
            }
        }

        // B. Exposure / Brightness
        val exposureVal = ((preset.exposure + preset.glowIntensity * 0.06) * opacityMultiplier)
            .toFloat()
            .coerceIn(-0.95f, 0.95f)
        if (abs(exposureVal) > 0.01f) {
            rawEffects.add(Brightness(exposureVal))
        }

        // C. Contrast
        val contrastVal = (preset.contrast * opacityMultiplier).toFloat().coerceIn(-0.95f, 0.95f)
        if (abs(contrastVal) > 0.01f) {
            rawEffects.add(Contrast(contrastVal))
        }

        // D. Grayscale / Monochrome vs HSL Hue + Saturation
        if (preset.saturation <= -98.0) {
            rawEffects.add(RgbFilter.createGrayscaleFilter())
        } else {
            val hueDeg = (preset.hueShift * opacityMultiplier).toFloat().coerceIn(-180f, 180f)
            val satAdj = (preset.saturation * opacityMultiplier).toFloat().coerceIn(-100f, 100f)
            if (abs(hueDeg) > 0.5f || abs(satAdj) > 0.5f) {
                rawEffects.add(
                    HslAdjustment.Builder()
                        .adjustHue(hueDeg)
                        .adjustSaturation(satAdj)
                        .build()
                )
            }
        }

        // E. Temperature & Tint via RGB Adjustment
        val temp = (preset.temperature * opacityMultiplier).toFloat().coerceIn(-1f, 1f)
        val tint = (preset.tint * opacityMultiplier).toFloat().coerceIn(-1f, 1f)
        if (abs(temp) > 0.01f || abs(tint) > 0.01f) {
            val redScale = (1.0f + temp * 0.28f + tint * 0.12f).coerceIn(0.4f, 1.8f)
            val greenScale = (1.0f - tint * 0.22f).coerceIn(0.4f, 1.8f)
            val blueScale = (1.0f - temp * 0.28f + tint * 0.12f).coerceIn(0.4f, 1.8f)
            rawEffects.add(
                RgbAdjustment.Builder()
                    .setRedScale(redScale)
                    .setGreenScale(greenScale)
                    .setBlueScale(blueScale)
                    .build()
            )
        }

        // F. Gaussian Blur (from AE ADBE Gaussian Blur / Fast Box Blur)
        val blur = (preset.blurRadius * opacityMultiplier).toFloat().coerceIn(0f, 20f)
        if (blur > 0.25f) {
            rawEffects.add(GaussianBlur(blur))
        }

        // G. Static Rotation
        val rot = preset.rotationDegrees.toFloat().coerceIn(-180f, 180f)
        if (abs(rot) > 0.2f) {
            rawEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(rot)
                    .build()
            )
        }

        // H. Keyframed AE Motion (Zoom Pulse + Camera Shake Wiggle)
        val zoomAmt = (preset.zoomPulse * opacityMultiplier).toFloat().coerceIn(0f, 1f)
        val shakeAmt = (preset.shakeIntensity * opacityMultiplier).toFloat().coerceIn(0f, 1f)
        if (zoomAmt > 0.02f || shakeAmt > 0.02f) {
            rawEffects.add(
                MatrixTransformation { presentationTimeUs ->
                    val tSec = presentationTimeUs / 1_000_000.0
                    val matrix = Matrix()
                    val baseScale = 1.0f + shakeAmt * 0.12f
                    val pulse = if (zoomAmt > 0.02f) {
                        (zoomAmt * 0.16f) * (0.5f + 0.5f * sin(tSec * 2.0 * PI * 1.25).toFloat())
                    } else {
                        0f
                    }
                    val totalScale = baseScale + pulse
                    matrix.postScale(totalScale, totalScale)

                    if (shakeAmt > 0.02f) {
                        val amp = shakeAmt * 0.055f
                        val dx = (sin(tSec * 23.0) * 0.6 + sin(tSec * 41.0) * 0.4).toFloat() * amp
                        val dy = (sin(tSec * 29.0 + 1.1) * 0.6 + sin(tSec * 47.0) * 0.4).toFloat() * amp
                        matrix.postTranslate(dx, dy)
                    }
                    matrix
                }
            )
        }

        // I. Optical Overlay (Halation Glow + Vignette + Film Grain + Letterbox)
        val hasOverlay = preset.vignetteIntensity > 0.02 ||
            preset.glowIntensity > 0.02 ||
            preset.grainAmount > 0.02 ||
            preset.letterboxRatio > 0.01
        if (hasOverlay) {
            val overlayBitmap = createOpticalOverlayBitmap(
                vignette = (preset.vignetteIntensity * opacityMultiplier).toFloat().coerceIn(0f, 1f),
                glow = (preset.glowIntensity * opacityMultiplier).toFloat().coerceIn(0f, 1f),
                grain = (preset.grainAmount * opacityMultiplier).toFloat().coerceIn(0f, 1f),
                letterbox = preset.letterboxRatio.toFloat().coerceIn(0f, 0.35f),
            )
            val overlay = BitmapOverlay.createStaticBitmapOverlay(
                overlayBitmap,
                StaticOverlaySettings.Builder().setAlphaScale(1.0f).build()
            )
            rawEffects.add(OverlayEffect(listOf(overlay)))
        }

        val isTrimmedRange = startMs > 250L || (endMs in 1L until (clipDurationMs - 250L))
        if (isTrimmedRange && endMs > startMs) {
            val startUs = startMs * 1_000L
            val endUs = endMs * 1_000L
            rawEffects.forEach { glEffect ->
                target.add(TimestampWrapper(glEffect, startUs, endUs))
            }
        } else {
            target.addAll(rawEffects)
        }
    }

    private fun createShapeLayerBitmap(
        shapeType: LayerShapeType,
        colorHex: Long,
        hasBorderShadow: Boolean,
        opacity: Float,
    ): Bitmap {
        val w = 640
        val h = 360
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseColor = colorHex.toInt()
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)

        when (shapeType) {
            LayerShapeType.LETTERBOX -> {
                val barPaint = Paint().apply { color = Color.BLACK }
                val barH = h * 0.13f
                canvas.drawRect(0f, 0f, w.toFloat(), barH, barPaint)
                canvas.drawRect(0f, h - barH, w.toFloat(), h.toFloat(), barPaint)
            }
            LayerShapeType.VIGNETTE_FRAME -> {
                val edgeAlpha = (opacity * 220f).toInt().coerceIn(40, 235)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        w * 0.5f,
                        h * 0.5f,
                        w * 0.62f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(edgeAlpha, r / 4, g / 4, b / 4)),
                        floatArrayOf(0.45f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            LayerShapeType.BORDER_FRAME, LayerShapeType.ROUNDED_RECT -> {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = if (hasBorderShadow) 10f else 5f
                    color = Color.argb(215, r, g, b)
                }
                val inset = 18f
                canvas.drawRoundRect(
                    RectF(inset, inset, w - inset, h - inset),
                    24f,
                    24f,
                    strokePaint,
                )
            }
            LayerShapeType.CIRCLE -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = if (hasBorderShadow) 12f else 6f
                    color = Color.argb(200, r, g, b)
                }
                canvas.drawCircle(w * 0.5f, h * 0.5f, h * 0.42f, paint)
            }
            LayerShapeType.STAR_BADGE -> {
                val cx = w * 0.86f
                val cy = h * 0.18f
                val outerR = 26f
                val innerR = 11f
                val path = Path()
                for (i in 0 until 10) {
                    val angle = -PI / 2.0 + i * PI / 5.0
                    val rad = if (i % 2 == 0) outerR else innerR
                    val x = cx + (rad * cos(angle)).toFloat()
                    val y = cy + (rad * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(225, r, g, b)
                }
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }

    private fun createOpticalOverlayBitmap(
        vignette: Float,
        glow: Float,
        grain: Float,
        letterbox: Float,
    ): Bitmap {
        val w = 640
        val h = 360
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (glow > 0.02f) {
            val glowAlpha = (glow * 72f).toInt().coerceIn(0, 95)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.5f,
                    h * 0.45f,
                    w * 0.65f,
                    intArrayOf(
                        Color.argb(glowAlpha, 255, 224, 245),
                        Color.argb((glowAlpha * 0.45f).toInt(), 168, 120, 255),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glowPaint)
        }

        if (vignette > 0.02f) {
            val edgeAlpha = (vignette * 220f).toInt().coerceIn(0, 235)
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.5f,
                    h * 0.5f,
                    w * 0.62f,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.argb((edgeAlpha * 0.25f).toInt(), 0, 0, 0),
                        Color.argb(edgeAlpha, 5, 5, 10),
                    ),
                    floatArrayOf(0.42f, 0.74f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignettePaint)
        }

        if (grain > 0.02f) {
            val rng = Random(42)
            val dotCount = (grain * 5200).toInt().coerceIn(100, 5200)
            val lightPaint = Paint().apply {
                color = Color.argb((grain * 58f).toInt().coerceIn(8, 70), 255, 255, 255)
                strokeWidth = 1.4f
            }
            val darkPaint = Paint().apply {
                color = Color.argb((grain * 62f).toInt().coerceIn(8, 75), 12, 12, 18)
                strokeWidth = 1.4f
            }
            for (i in 0 until dotCount) {
                val x = rng.nextFloat() * w
                val y = rng.nextFloat() * h
                canvas.drawPoint(x, y, if (i % 2 == 0) lightPaint else darkPaint)
            }
        }

        if (letterbox > 0.01f) {
            val barHeight = h * (letterbox * 0.5f).coerceIn(0f, 0.22f)
            val barPaint = Paint().apply { color = Color.BLACK }
            canvas.drawRect(0f, 0f, w.toFloat(), barHeight, barPaint)
            canvas.drawRect(0f, h - barHeight, w.toFloat(), h.toFloat(), barPaint)
        }

        return bitmap
    }
}
