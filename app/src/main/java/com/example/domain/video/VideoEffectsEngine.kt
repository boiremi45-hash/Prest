package com.example.domain.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Effects
import com.example.data.model.FfxPreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

@UnstableApi
object VideoEffectsEngine {

    fun buildPreviewVideoEffects(preset: FfxPreset, bypass: Boolean): List<Effect> {
        if (bypass) return emptyList()
        return buildVideoEffectsList(preset = preset, targetHeight = null)
    }

    fun buildExportEffects(
        preset: FfxPreset,
        targetHeight: Int?,
    ): Effects {
        val videoEffects = buildVideoEffectsList(
            preset = preset,
            targetHeight = targetHeight,
        )
        return Effects(emptyList(), videoEffects)
    }

    private fun buildVideoEffectsList(
        preset: FfxPreset,
        targetHeight: Int?,
    ): List<Effect> {
        val effects = mutableListOf<Effect>()

        // 1. Exposure + soft halation lift
        val exposureVal = (preset.exposure + preset.glowIntensity * 0.08).toFloat().coerceIn(-0.95f, 0.95f)
        if (abs(exposureVal) > 0.01f) {
            effects.add(Brightness(exposureVal))
        }

        // 2. Contrast
        val contrastVal = preset.contrast.toFloat().coerceIn(-0.95f, 0.95f)
        if (abs(contrastVal) > 0.01f) {
            effects.add(Contrast(contrastVal))
        }

        // 3. Hue & Saturation (HSL)
        val hueDeg = preset.hueShift.toFloat().coerceIn(-180f, 180f)
        val satAdj = preset.saturation.toFloat().coerceIn(-100f, 100f)
        if (abs(hueDeg) > 0.5f || abs(satAdj) > 0.5f) {
            effects.add(
                HslAdjustment.Builder()
                    .adjustHue(hueDeg)
                    .adjustSaturation(satAdj)
                    .build()
            )
        }

        // 4. Temperature & Tint via RGB Matrix balance
        val temp = preset.temperature.toFloat().coerceIn(-1f, 1f)
        val tint = preset.tint.toFloat().coerceIn(-1f, 1f)
        if (abs(temp) > 0.01f || abs(tint) > 0.01f) {
            val redScale = (1.0f + temp * 0.28f + tint * 0.12f).coerceIn(0.4f, 1.8f)
            val greenScale = (1.0f - tint * 0.22f).coerceIn(0.4f, 1.8f)
            val blueScale = (1.0f - temp * 0.28f + tint * 0.12f).coerceIn(0.4f, 1.8f)
            effects.add(
                RgbAdjustment.Builder()
                    .setRedScale(redScale)
                    .setGreenScale(greenScale)
                    .setBlueScale(blueScale)
                    .build()
            )
        }

        // 5. Static Rotation
        val rot = preset.rotationDegrees.toFloat().coerceIn(-180f, 180f)
        if (abs(rot) > 0.2f) {
            effects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(rot)
                    .build()
            )
        }

        // 6. Keyframed AE Transform: Zoom Pulse + Camera Wiggle Shake
        val zoomAmt = preset.zoomPulse.toFloat().coerceIn(0f, 1f)
        val shakeAmt = preset.shakeIntensity.toFloat().coerceIn(0f, 1f)
        if (zoomAmt > 0.02f || shakeAmt > 0.02f) {
            effects.add(
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

        // 7. Optical Overlay: Vignette + Glow Bloom + Film Grain + Anamorphic Letterbox
        val hasOverlay = preset.vignetteIntensity > 0.02 ||
            preset.glowIntensity > 0.02 ||
            preset.grainAmount > 0.02 ||
            preset.letterboxRatio > 0.01
        if (hasOverlay) {
            val overlayBitmap = createOpticalOverlayBitmap(
                vignette = preset.vignetteIntensity.toFloat().coerceIn(0f, 1f),
                glow = preset.glowIntensity.toFloat().coerceIn(0f, 1f),
                grain = preset.grainAmount.toFloat().coerceIn(0f, 1f),
                letterbox = preset.letterboxRatio.toFloat().coerceIn(0f, 0.35f),
            )
            val overlay = BitmapOverlay.createStaticBitmapOverlay(
                overlayBitmap,
                StaticOverlaySettings.Builder().setAlphaScale(1.0f).build()
            )
            effects.add(OverlayEffect(listOf(overlay)))
        }

        // 8. Target export resolution scaling
        if (targetHeight != null && targetHeight > 0) {
            effects.add(Presentation.createForHeight(targetHeight))
        }

        return effects
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

        // Halation / atmospheric optical glow veil
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

        // Optical vignette
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

        // Film grain speckle texture
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

        // Anamorphic cinema letterbox bars
        if (letterbox > 0.01f) {
            val barHeight = h * (letterbox * 0.5f).coerceIn(0f, 0.22f)
            val barPaint = Paint().apply { color = Color.BLACK }
            canvas.drawRect(0f, 0f, w.toFloat(), barHeight, barPaint)
            canvas.drawRect(0f, h - barHeight, w.toFloat(), h.toFloat(), barPaint)
        }

        return bitmap
    }
}
