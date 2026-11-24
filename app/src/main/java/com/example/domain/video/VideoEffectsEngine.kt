package com.example.domain.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.SingleColorLut
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TimestampWrapper
import androidx.media3.transformer.Effects
import com.example.data.model.AdjustSettings
import com.example.data.model.ClipTimelineSpan
import com.example.data.model.FfxPreset
import com.example.data.model.KeyframeEvaluator
import com.example.data.model.OverlayTrackItem
import com.example.data.model.StabilizeMode
import com.example.data.model.TextKeyframeEvaluator
import com.example.data.model.TextTrackItem
import com.example.data.model.TransitionSpec
import com.example.data.model.VideoClipSegment
import com.example.data.model.VideoEffectLayer
import com.example.data.model.VolumeKeyframeEvaluator
import com.example.data.model.VolumeKeyframeNode
import com.example.domain.lut.CubeLutParser
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@UnstableApi
class KeyframeVolumeAudioProcessor(
    private val volumeKeyframes: List<VolumeKeyframeNode>,
    private val baseVolumePercent: Int,
) : BaseAudioProcessor() {
    private var processedFrames: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onQueueEndOfStream() {
        super.onQueueEndOfStream()
    }

    override fun onFlush() {
        processedFrames = 0L
    }

    override fun onReset() {
        processedFrames = 0L
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        val out = replaceOutputBuffer(remaining)
        val bytesPerFrame = channels * 2
        val totalFrames = remaining / bytesPerFrame
        for (f in 0 until totalFrames) {
            val timeMs = (processedFrames * 1_000L) / sampleRate
            val gain = (VolumeKeyframeEvaluator.evaluate(volumeKeyframes, timeMs, baseVolumePercent) / 100f)
                .coerceIn(0f, 4f)
            for (c in 0 until channels) {
                val sample = inputBuffer.short.toInt()
                val scaled = (sample * gain).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                out.putShort(scaled)
            }
            processedFrames++
        }
        // Drain any trailing bytes safely
        while (inputBuffer.hasRemaining()) {
            out.put(inputBuffer.get())
        }
        out.flip()
    }
}

@UnstableApi
object VideoEffectsEngine {

    fun computeClipSpans(clips: List<VideoClipSegment>): List<ClipTimelineSpan> {
        return clips.sortedBy { it.timelineStartMs }.map { clip ->
            val start = clip.timelineStartMs.coerceAtLeast(0L)
            val dur = clip.timelineDurationMs()
            ClipTimelineSpan(clip = clip, startMs = start, endMs = start + dur)
        }
    }

    fun buildPreviewEffects(
        context: Context,
        activeClip: VideoClipSegment,
        allClips: List<VideoClipSegment>,
        transitions: List<TransitionSpec>,
        effectLayers: List<VideoEffectLayer>,
        overlays: List<OverlayTrackItem>,
        texts: List<TextTrackItem>,
        adjust: AdjustSettings,
        filterPreset: FfxPreset,
        filterIntensity: Float,
        shaderParamsProvider: () -> LunaShaderParams,
        bypass: Boolean,
    ): List<Effect> {
        if (bypass) return emptyList()
        return buildPipelineEffects(
            context = context,
            activeClip = activeClip,
            effectLayers = effectLayers,
            overlays = overlays,
            texts = texts,
            adjust = adjust,
            filterPreset = filterPreset,
            filterIntensity = filterIntensity,
            shaderParamsProvider = shaderParamsProvider,
            includeClipTransformInGl = false,
            includeOverlaysAndTexts = false,
            targetHeight = null,
            targetFps = null,
        )
    }

    fun buildExportEffectsForClip(
        context: Context,
        clip: VideoClipSegment,
        clipStartInTimelineMs: Long,
        allClips: List<VideoClipSegment>,
        transitions: List<TransitionSpec>,
        effectLayers: List<VideoEffectLayer>,
        overlays: List<OverlayTrackItem>,
        texts: List<TextTrackItem>,
        adjust: AdjustSettings,
        filterPreset: FfxPreset,
        filterIntensity: Float,
        config: ExportConfig,
    ): Effects {
        val spans = computeClipSpans(allClips)
        val transitionWindows = buildTransitionWindows(spans, transitions)
        val clipDuration = clip.timelineDurationMs()

        // Shift timeline-global effect/transition windows into this clip's local presentation time
        val localEffects = effectLayers.mapNotNull { fx ->
            val s = (fx.startMs - clipStartInTimelineMs).coerceAtLeast(0L)
            val e = (fx.endMs - clipStartInTimelineMs).coerceAtMost(clipDuration)
            if (e > s) fx.copy(startMs = s, endMs = e) else null
        }
        val localTransitions = transitionWindows.mapNotNull { tr ->
            val localBoundary = tr.boundaryMs - clipStartInTimelineMs
            if (localBoundary in -tr.durationMs..(clipDuration + tr.durationMs)) {
                tr.copy(boundaryMs = localBoundary)
            } else {
                null
            }
        }
        val localOverlays = overlays.mapNotNull { ov ->
            val s = (ov.startMs - clipStartInTimelineMs).coerceAtLeast(0L)
            val e = (ov.endMs - clipStartInTimelineMs).coerceAtMost(clipDuration)
            if (e > s) ov.copy(startMs = s, endMs = e) else null
        }
        val localTexts = texts.mapNotNull { tx ->
            val s = (tx.startMs - clipStartInTimelineMs).coerceAtLeast(0L)
            val e = (tx.endMs - clipStartInTimelineMs).coerceAtMost(clipDuration)
            if (e > s) tx.copy(startMs = s, endMs = e) else null
        }

        val clipAdjust = clip.adjust
        val frozenParams = makeShaderParams(
            clip = clip,
            adjust = clipAdjust,
            filterPreset = filterPreset,
            filterIntensity = filterIntensity,
            effectLayers = localEffects,
            transitionWindows = localTransitions,
            bypass = false,
        )

        val videoEffects = buildPipelineEffects(
            context = context,
            activeClip = clip,
            effectLayers = localEffects,
            overlays = localOverlays,
            texts = localTexts,
            adjust = clipAdjust,
            filterPreset = filterPreset,
            filterIntensity = filterIntensity,
            shaderParamsProvider = { frozenParams },
            includeClipTransformInGl = true,
            includeOverlaysAndTexts = true,
            targetHeight = config.resolution.targetHeight,
            targetFps = config.frameRate,
        )

        val audioProcessors = mutableListOf<AudioProcessor>()
        val speedFactor = clip.speed.effectiveSpeedMultiplier()
        // When ChangeAudioPitch is ON, SonicAudioProcessor applies tempo speed AND matching pitch shift.
        if (clip.speed.changeAudioPitch && kotlin.math.abs(speedFactor - 1f) > 0.03f) {
            val pitchRatio = speedFactor.coerceIn(0.45f, 2.2f)
            audioProcessors.add(
                SonicAudioProcessor().apply {
                    setPitch(pitchRatio)
                }
            )
        }
        if (clip.volumeKeyframes.isNotEmpty() || clip.volumePercent != 100) {
            audioProcessors.add(
                KeyframeVolumeAudioProcessor(
                    volumeKeyframes = clip.volumeKeyframes,
                    baseVolumePercent = clip.volumePercent,
                )
            )
        }

        return Effects(audioProcessors, videoEffects)
    }

    fun makeShaderParams(
        clip: VideoClipSegment,
        adjust: AdjustSettings,
        filterPreset: FfxPreset,
        filterIntensity: Float,
        effectLayers: List<VideoEffectLayer>,
        transitionWindows: List<TimedTransitionWindow>,
        bypass: Boolean,
        timelineOffsetMs: Long = 0L,
    ): LunaShaderParams {
        val fi = filterIntensity.coerceIn(0f, 1f)
        return LunaShaderParams(
            bypass = bypass,
            adjust = adjust,
            presetExposure = (filterPreset.exposure * fi).toFloat(),
            presetContrast = (filterPreset.contrast * fi).toFloat(),
            presetSaturation = (filterPreset.saturation * fi).toFloat(),
            presetHueDeg = (filterPreset.hueShift * fi).toFloat(),
            presetTemperature = (filterPreset.temperature * fi).toFloat(),
            presetTint = (filterPreset.tint * fi).toFloat(),
            presetVignette = (filterPreset.vignetteIntensity * fi).toFloat(),
            presetGlow = (filterPreset.glowIntensity * fi).toFloat(),
            presetGrain = (filterPreset.grainAmount * fi).toFloat(),
            effectLayers = effectLayers,
            keyframes = clip.keyframes,
            baseTransform = clip.transform,
            baseOpacity = clip.opacity,
            maskShape = clip.mask.shape,
            maskFeather = clip.mask.feather,
            maskInvert = clip.mask.invert,
            maskScale = clip.mask.sizeScale,
            motionBlur = clip.motionBlur,
            clipDurationMs = clip.timelineDurationMs(),
            animation = clip.animation,
            transitions = transitionWindows,
            timelineOffsetMs = timelineOffsetMs,
            cutFlashPunchOnStart = clip.cutFlashPunchOnStart,
        )
    }

    fun buildTransitionWindows(
        spans: List<ClipTimelineSpan>,
        transitions: List<TransitionSpec>,
    ): List<TimedTransitionWindow> {
        return transitions.mapNotNull { tr ->
            val span = spans.firstOrNull { it.clip.id == tr.afterClipId } ?: return@mapNotNull null
            TimedTransitionWindow(
                boundaryMs = span.endMs,
                type = tr.type,
                durationMs = (tr.durationSec * 1000f).toLong().coerceIn(100L, 800L),
            )
        }
    }

    private fun buildPipelineEffects(
        context: Context,
        activeClip: VideoClipSegment,
        effectLayers: List<VideoEffectLayer>,
        overlays: List<OverlayTrackItem>,
        texts: List<TextTrackItem>,
        adjust: AdjustSettings,
        filterPreset: FfxPreset,
        filterIntensity: Float,
        shaderParamsProvider: () -> LunaShaderParams,
        includeClipTransformInGl: Boolean,
        includeOverlaysAndTexts: Boolean,
        targetHeight: Int?,
        targetFps: Int?,
    ): List<Effect> {
        val pipeline = mutableListOf<Effect>()

        // 1. Keyframed + Stabilized + Crop + Filter MatrixTransformation (evaluated smoothly with EasingGraph!)
        pipeline.add(
            MatrixTransformation { presentationTimeUs ->
                val p = shaderParamsProvider()
                val timeMs = presentationTimeUs / 1_000L
                val eval = KeyframeEvaluator.evaluate(
                    keyframes = p.keyframes,
                    localTimeMs = timeMs,
                    baseTransform = p.baseTransform,
                    baseOpacity = p.baseOpacity,
                    baseAdjust = p.adjust,
                    baseFilterIntensity = filterIntensity,
                )
                val matrix = Matrix()
                val stabilizeZoom = when (activeClip.stabilize) {
                    StabilizeMode.OFF -> 1.0f
                    StabilizeMode.MINIMAL -> 1.06f
                    StabilizeMode.RECOMMENDED -> 1.12f
                    StabilizeMode.MOST_STABLE -> 1.20f
                }

                val tSec = presentationTimeUs / 1_000_000.0
                val fxZoom = if (filterPreset.zoomPulse > 0.02) {
                    (filterPreset.zoomPulse * filterIntensity * 0.16).toFloat() *
                        (0.5f + 0.5f * sin(tSec * 2.0 * PI * 1.25).toFloat())
                } else 0f
                val shake = (filterPreset.shakeIntensity * filterIntensity).toFloat()

                val cutPunch = if (!p.bypass && p.cutFlashPunchOnStart && timeMs in 0L..55L) {
                    0.16f * (1f - timeMs / 55f).coerceIn(0f, 1f)
                } else 0f

                if (includeClipTransformInGl) {
                    val zoomScale = ((eval.zoomPercent / 100f).coerceIn(0.25f, 3.8f) + cutPunch) *
                        activeClip.crop.scaleCrop * stabilizeZoom
                    val mirrorX = if (activeClip.transform.mirrorHorizontal) -1f else 1f
                    val totalRot = eval.rotateDeg + activeClip.crop.rotateDeg + filterPreset.rotationDegrees.toFloat()
                    val finalScaleX = (zoomScale + fxZoom + shake * 0.10f) * mirrorX
                    val finalScaleY = (zoomScale + fxZoom + shake * 0.10f)

                    matrix.postScale(finalScaleX, finalScaleY)
                    matrix.postRotate(-totalRot)

                    var tx = eval.posX.coerceIn(-1.5f, 1.5f)
                    var ty = eval.posY.coerceIn(-1.5f, 1.5f)
                    if (shake > 0.02f) {
                        val amp = shake * 0.055f
                        tx += (sin(tSec * 23.0) * 0.6 + sin(tSec * 41.0) * 0.4).toFloat() * amp
                        ty += (sin(tSec * 29.0 + 1.1) * 0.6 + sin(tSec * 47.0) * 0.4).toFloat() * amp
                    }
                    matrix.postTranslate(tx, ty)
                } else {
                    // Preview renders posX/posY/zoom/rotate/mirror live via Compose graphicsLayer on TextureView!
                    val previewGlScale = stabilizeZoom + fxZoom + cutPunch + shake * 0.10f
                    matrix.postScale(previewGlScale, previewGlScale)
                    if (kotlin.math.abs(filterPreset.rotationDegrees) > 0.01) {
                        matrix.postRotate(-filterPreset.rotationDegrees.toFloat())
                    }
                    if (shake > 0.02f) {
                        val amp = shake * 0.055f
                        val sx = (sin(tSec * 23.0) * 0.6 + sin(tSec * 41.0) * 0.4).toFloat() * amp
                        val sy = (sin(tSec * 29.0 + 1.1) * 0.6 + sin(tSec * 47.0) * 0.4).toFloat() * amp
                        matrix.postTranslate(sx, sy)
                    }
                }
                matrix
            }
        )

        // 2. Hardware 3D LUT for .cube Preset AND/OR RGB Curves + Highlights/Shadows/HSL/ColorWheel
        val presetCube = if (filterPreset.lutSize >= 2 && filterPreset.lutData.isNotBlank()) {
            CubeLutParser.decodeToCubeArray(filterPreset.lutSize, filterPreset.lutData)
        } else {
            null
        }
        if (!RgbCurveLutBuilder.isNeutral(adjust, presetCube)) {
            val combinedCube = RgbCurveLutBuilder.buildCombinedLut(
                adjust = adjust,
                baseCube = presetCube,
                baseCubeIntensity = filterIntensity.coerceIn(0f, 1f),
            )
            pipeline.add(SingleColorLut.createFromCube(combinedCube))
        }

        // 3. Real-time Custom GLSL Multi-Shader (Adjust + 12 CapCut Effects + Mask + Motion Blur + Animations + Transitions)
        pipeline.add(LunaGlEffect(shaderParamsProvider))

        // 4 & 5. Image/Video Overlays and Text Overlays (used in hardware MP4 Export; Preview renders them interactively in Compose above PlayerView)
        if (includeOverlaysAndTexts) {
            overlays.forEach { ov ->
                val bmp = decodeOverlayBitmap(context, ov)
                if (bmp != null && ov.endMs > ov.startMs) {
                    val settings = StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(ov.posX.coerceIn(-1f, 1f), ov.posY.coerceIn(-1f, 1f))
                        .setOverlayFrameAnchor(0f, 0f)
                        .setScale(ov.scale.coerceIn(0.12f, 2.5f), ov.scale.coerceIn(0.12f, 2.5f))
                        .setRotationDegrees(-ov.rotationDeg)
                        .setAlphaScale(ov.opacity.coerceIn(0.05f, 1f))
                        .build()
                    val ovEffect = OverlayEffect(
                        listOf(BitmapOverlay.createStaticBitmapOverlay(bmp, settings))
                    )
                    pipeline.add(
                        TimestampWrapper(
                            ovEffect,
                            ov.startMs.coerceAtLeast(0L) * 1_000L,
                            ov.endMs.coerceAtLeast(ov.startMs + 100L) * 1_000L,
                        )
                    )
                }
            }

            texts.forEach { t ->
                if (t.text.isNotBlank() && t.endMs > t.startMs) {
                    val spannable = SpannableString(" ${t.text.trim()} ").apply {
                        setSpan(ForegroundColorSpan(t.colorArgb.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (t.isCaption) {
                            setSpan(BackgroundColorSpan(0xBB0A0C14.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        if (t.bold) {
                            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                    if (t.keyframes.isEmpty()) {
                        val scaleVal = t.scale.coerceIn(0.35f, 3.5f)
                        val settings = StaticOverlaySettings.Builder()
                            .setBackgroundFrameAnchor(t.posX.coerceIn(-0.95f, 0.95f), t.posY.coerceIn(-0.95f, 0.95f))
                            .setOverlayFrameAnchor(0f, 0f)
                            .setScale(scaleVal, scaleVal)
                            .setRotationDegrees(-t.rotationDeg)
                            .setAlphaScale(t.opacity.coerceIn(0f, 1f))
                            .build()
                        val textFx = OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(spannable, settings)))
                        pipeline.add(
                            TimestampWrapper(
                                textFx,
                                t.startMs.coerceAtLeast(0L) * 1_000L,
                                t.endMs.coerceAtLeast(t.startMs + 100L) * 1_000L,
                            )
                        )
                    } else {
                        val spanMs = (t.endMs - t.startMs).coerceAtLeast(100L)
                        val stepMs = (spanMs / 24L).coerceAtLeast(100L)
                        var cursor = t.startMs
                        while (cursor < t.endMs) {
                            val sliceEnd = (cursor + stepMs).coerceAtMost(t.endMs)
                            val localMid = ((cursor - t.startMs) + (sliceEnd - t.startMs)) / 2L
                            val ev = TextKeyframeEvaluator.evaluate(t.keyframes, localMid, t)
                            val scaleVal = ev.scale.coerceIn(0.35f, 3.5f)
                            val settings = StaticOverlaySettings.Builder()
                                .setBackgroundFrameAnchor(ev.posX.coerceIn(-0.95f, 0.95f), ev.posY.coerceIn(-0.95f, 0.95f))
                                .setOverlayFrameAnchor(0f, 0f)
                                .setScale(scaleVal, scaleVal)
                                .setRotationDegrees(-ev.rotationDeg)
                                .setAlphaScale(ev.opacity.coerceIn(0f, 1f))
                                .build()
                            val textFx = OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(spannable, settings)))
                            pipeline.add(
                                TimestampWrapper(
                                    textFx,
                                    cursor.coerceAtLeast(0L) * 1_000L,
                                    sliceEnd.coerceAtLeast(cursor + 30L) * 1_000L,
                                )
                            )
                            cursor = sliceEnd
                        }
                    }
                }
            }
        }

        // 6. Export Frame Rate & Output Resolution
        if (targetFps != null && targetFps > 0) {
            pipeline.add(FrameDropEffect.createDefaultFrameDropEffect(targetFps.toFloat()))
        }
        if (targetHeight != null && targetHeight > 0) {
            pipeline.add(Presentation.createForHeight(targetHeight))
        }

        return pipeline
    }

    fun decodeOverlayBitmap(context: Context, item: OverlayTrackItem, timeMs: Long = 0L): Bitmap? {
        val uri = runCatching { Uri.parse(item.uriString) }.getOrNull() ?: return null
        val raw = runCatching {
            if (item.isVideo) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    r.getFrameAtTime((timeMs.coerceAtLeast(0L)) * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { r.release() }
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        }.getOrNull() ?: return null

        return OverlayMatteProcessor.processBitmapMatte(
            src = raw,
            autoRemoveBg = item.autoRemoveBg,
            chromaEnabled = item.chromaEnabled,
            chromaColorArgb = item.chromaColorArgb,
            chromaIntensity = item.chromaIntensity,
            chromaShadow = item.chromaShadow,
            opacity = item.opacity,
        )
    }
}
