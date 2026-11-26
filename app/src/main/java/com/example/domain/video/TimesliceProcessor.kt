package com.example.domain.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.util.LruCache
import android.view.Surface
import com.example.data.model.CanvasFitMode
import com.example.data.model.TimesliceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared Canvas slice compositor for LUNA's unique Timeslice effect.
 * Used in real-time by Compose Preview AND at full resolution by Export.
 */
object TimesliceCompositor {

    fun drawTimesliceFrame(
        canvas: Canvas,
        width: Float,
        height: Float,
        baseTimeMs: Long,
        clipStartMs: Long,
        clipEndMs: Long,
        settings: TimesliceSettings,
        fitMode: CanvasFitMode = CanvasFitMode.FILL,
        frameProvider: (sampleTimeMs: Long) -> Bitmap?,
    ): Boolean {
        if (width <= 1f || height <= 1f) return false
        val safeBaseMs = baseTimeMs.coerceIn(clipStartMs, clipEndMs.coerceAtLeast(clipStartMs))
        val baseBmp = frameProvider(safeBaseMs) ?: return false

        val intensity = settings.intensity.coerceIn(0f, 1f)
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        fun computeDstRect(bmp: Bitmap): RectF {
            val bw = bmp.width.coerceAtLeast(1).toFloat()
            val bh = bmp.height.coerceAtLeast(1).toFloat()
            val viewAspect = width / height
            val bmpAspect = bw / bh
            return when (fitMode) {
                CanvasFitMode.FIT -> {
                    if (bmpAspect > viewAspect) {
                        val targetH = width / bmpAspect
                        val top = (height - targetH) * 0.5f
                        RectF(0f, top, width, top + targetH)
                    } else {
                        val targetW = height * bmpAspect
                        val left = (width - targetW) * 0.5f
                        RectF(left, 0f, left + targetW, height)
                    }
                }
                CanvasFitMode.FILL, CanvasFitMode.WIDE_CINEMA -> {
                    if (bmpAspect > viewAspect) {
                        val targetW = height * bmpAspect
                        val left = (width - targetW) * 0.5f
                        RectF(left, 0f, left + targetW, height)
                    } else {
                        val targetH = width / bmpAspect
                        val top = (height - targetH) * 0.5f
                        RectF(0f, top, width, top + targetH)
                    }
                }
            }
        }

        val baseDst = computeDstRect(baseBmp)
        if (!settings.enabled || intensity <= 0.01f) {
            canvas.drawBitmap(baseBmp, null, baseDst, bitmapPaint)
            return true
        }

        // Draw base un-sliced frame first when partial intensity blending is active
        val needBlendLayer = intensity < 0.995f
        if (needBlendLayer) {
            canvas.drawBitmap(baseBmp, null, baseDst, bitmapPaint)
            canvas.saveLayerAlpha(0f, 0f, width, height, (intensity * 255f).roundToInt().coerceIn(0, 255))
        }

        val count = settings.clampedSlices()
        val isHorizontal = settings.isHorizontalSlices()
        val axisLen = if (isHorizontal) width else height
        val sliceLen = axisLen / count.toFloat()
        val featherFrac = (settings.feather / 100f).coerceIn(0f, 1f)
        val featherPx = featherFrac * (sliceLen * 0.48f)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }

        for (i in 0 until count) {
            val sampleMs = settings.sampleTimeMs(safeBaseMs, i, clipStartMs, clipEndMs)
            val sliceBmp = frameProvider(sampleMs) ?: baseBmp
            val sliceDst = if (sliceBmp === baseBmp) baseDst else computeDstRect(sliceBmp)

            val s0 = i * sliceLen
            val s1 = if (i == count - 1) axisLen else (i + 1) * sliceLen

            if (i == 0 || featherPx <= 1f) {
                canvas.save()
                if (isHorizontal) {
                    canvas.clipRect((s0 - 0.5f).coerceAtLeast(0f), 0f, (s1 + 0.5f).coerceAtMost(width), height)
                } else {
                    canvas.clipRect(0f, (s0 - 0.5f).coerceAtLeast(0f), width, (s1 + 0.5f).coerceAtMost(height))
                }
                canvas.drawBitmap(sliceBmp, null, sliceDst, bitmapPaint)
                canvas.restore()
            } else {
                // 1) Solid core of slice i (starts after feather zone)
                val coreStart = (s0 + featherPx - 0.5f).coerceAtMost(s1)
                if (coreStart < s1) {
                    canvas.save()
                    if (isHorizontal) {
                        canvas.clipRect(coreStart, 0f, (s1 + 0.5f).coerceAtMost(width), height)
                    } else {
                        canvas.clipRect(0f, coreStart, width, (s1 + 0.5f).coerceAtMost(height))
                    }
                    canvas.drawBitmap(sliceBmp, null, sliceDst, bitmapPaint)
                    canvas.restore()
                }

                // 2) Feathered alpha-gradient transition strip overlapping slice i-1 seamlessly
                val blendStart = (s0 - featherPx).coerceAtLeast(0f)
                val blendEnd = (s0 + featherPx).coerceAtMost(axisLen)
                if (blendEnd - blendStart > 1f) {
                    if (isHorizontal) {
                        canvas.saveLayer(blendStart, 0f, blendEnd, height, null)
                        canvas.clipRect(blendStart, 0f, blendEnd, height)
                        canvas.drawBitmap(sliceBmp, null, sliceDst, bitmapPaint)
                        maskPaint.shader = LinearGradient(
                            blendStart, 0f, blendEnd, 0f,
                            0x00000000, 0xFF000000.toInt(),
                            Shader.TileMode.CLAMP,
                        )
                        canvas.drawRect(blendStart, 0f, blendEnd, height, maskPaint)
                        canvas.restore()
                    } else {
                        canvas.saveLayer(0f, blendStart, width, blendEnd, null)
                        canvas.clipRect(0f, blendStart, width, blendEnd)
                        canvas.drawBitmap(sliceBmp, null, sliceDst, bitmapPaint)
                        maskPaint.shader = LinearGradient(
                            0f, blendStart, 0f, blendEnd,
                            0x00000000, 0xFF000000.toInt(),
                            Shader.TileMode.CLAMP,
                        )
                        canvas.drawRect(0f, blendStart, width, blendEnd, maskPaint)
                        canvas.restore()
                    }
                }
            }
        }

        if (needBlendLayer) {
            canvas.restore()
        }
        return true
    }
}

/**
 * Low-resolution in-memory frame pool enabling smooth real-time Preview rendering for Timeslice.
 */
class TimeslicePreviewFramePool {
    private var activeKey: String = ""
    private val cache = object : LruCache<Long, Bitmap>(180) {}
    private val buckets = sortedSetOf<Long>()

    companion object {
        const val STEP_MS = 60L
    }

    private fun quantize(ms: Long): Long = ((ms.coerceAtLeast(0L) + STEP_MS / 2L) / STEP_MS) * STEP_MS

    @Synchronized
    fun resetIfClipChanged(cacheKey: String) {
        if (activeKey != cacheKey) {
            activeKey = cacheKey
            cache.evictAll()
            buckets.clear()
        }
    }

    @Synchronized
    fun putFrame(timeMs: Long, bitmap: Bitmap) {
        val q = quantize(timeMs)
        cache.put(q, bitmap)
        buckets.add(q)
    }

    @Synchronized
    fun hasExactBucket(timeMs: Long): Boolean = cache.get(quantize(timeMs)) != null

    @Synchronized
    fun getNearestBitmap(timeMs: Long): Bitmap? {
        val q = quantize(timeMs)
        cache.get(q)?.let { return it }
        if (buckets.isEmpty()) return null
        val floor = buckets.floor(q)
        val ceil = buckets.ceiling(q)
        val bestKey = when {
            floor != null && ceil != null -> if (abs(q - floor) <= abs(ceil - q)) floor else ceil
            floor != null -> floor
            else -> ceil
        } ?: return null
        return cache.get(bestKey)
    }

    suspend fun ensureWindowFrames(
        context: Context,
        uri: Uri,
        cacheKey: String,
        clipStartMs: Long,
        clipEndMs: Long,
        centerTimeMs: Long,
        settings: TimesliceSettings,
        onBatchDecoded: () -> Unit,
    ) = withContext(Dispatchers.IO) {
        resetIfClipChanged(cacheKey)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            // Priority 1: decode exact slice timestamps around current centerTimeMs so paused UI updates instantaneously
            val sliceTargets = (0 until settings.clampedSlices()).map { idx ->
                settings.sampleTimeMs(centerTimeMs, idx, clipStartMs, clipEndMs)
            }.distinct()

            var decodedCount = 0
            for (ms in sliceTargets) {
                coroutineContext.ensureActive()
                if (!hasExactBucket(ms)) {
                    decodeScaledFrame(retriever, ms)?.let { bmp ->
                        putFrame(ms, bmp)
                        decodedCount++
                        if (decodedCount % 2 == 0) onBatchDecoded()
                    }
                }
            }
            if (decodedCount > 0) onBatchDecoded()

            // Priority 2: warm quantized 60ms timeline neighborhood (-1.2s .. +4.5s) then remaining clip for smooth playback
            val safeEnd = clipEndMs.coerceAtLeast(clipStartMs + 200L)
            val nearStart = (centerTimeMs - 1_200L).coerceAtLeast(clipStartMs)
            val nearEnd = (centerTimeMs + 4_500L).coerceAtMost(safeEnd)
            val orderedBuckets = mutableListOf<Long>()
            var cur = nearStart
            while (cur <= nearEnd) {
                orderedBuckets.add(cur)
                cur += STEP_MS
            }
            var tail = clipStartMs
            while (tail <= safeEnd) {
                if (tail < nearStart || tail > nearEnd) orderedBuckets.add(tail)
                tail += STEP_MS
            }

            var bgBatch = 0
            for (ms in orderedBuckets) {
                coroutineContext.ensureActive()
                if (!hasExactBucket(ms)) {
                    decodeScaledFrame(retriever, ms)?.let { bmp ->
                        putFrame(ms, bmp)
                        bgBatch++
                        if (bgBatch % 4 == 0) onBatchDecoded()
                    }
                }
            }
            if (bgBatch > 0) onBatchDecoded()
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeScaledFrame(retriever: MediaMetadataRetriever, timeMs: Long): Bitmap? {
        val timeUs = timeMs.coerceAtLeast(0L) * 1_000L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, 420, 420)
                ?: retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 420, 420)
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { raw ->
                    val maxSide = maxOf(raw.width, raw.height).coerceAtLeast(1)
                    val s = minOf(1f, 420f / maxSide.toFloat())
                    val w = (raw.width * s).roundToInt().coerceAtLeast(64)
                    val h = (raw.height * s).roundToInt().coerceAtLeast(64)
                    val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                    if (scaled !== raw) raw.recycle()
                    scaled
                }
        }
    }
}

/**
 * Full-resolution Timeslice MP4 pre-renderer for MP4 Export.
 * Decodes source frames at target export resolution, composites N clipped/feathered slices
 * onto an offscreen Bitmap using TimesliceCompositor, encodes to H.264 MP4, and muxes trimmed audio.
 */
object TimesliceProcessor {

    data class TimesliceResult(
        val outputUri: Uri,
        val outputFile: File,
        val durationMs: Long,
    )

    fun renderTimesliceClipMp4Blocking(
        context: Context,
        sourceUri: Uri,
        clipId: String,
        trimStartMs: Long,
        trimEndMs: Long,
        targetWidth: Int,
        targetHeight: Int,
        targetFps: Int,
        settings: TimesliceSettings,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit,
    ): Result<TimesliceResult> {
        val dir = File(context.cacheDir, "timeslice_exports").apply { mkdirs() }
        val sig = "${clipId.take(10)}_${trimStartMs}_${trimEndMs}_${settings.slices}_${settings.direction.ordinal}_${(settings.timeSpreadSec * 100).roundToInt()}_${settings.feather.roundToInt()}_${(settings.intensity * 100).roundToInt()}_${targetHeight}_${targetFps}"
        val outFile = File(dir, "ts_$sig.mp4")
        val workDir = File(context.cacheDir, "ts_tmp_${UUID.randomUUID()}").apply { mkdirs() }

        if (outFile.exists() && outFile.length() > 4096L) {
            onProgress(100)
            return Result.success(
                TimesliceResult(
                    outputUri = Uri.fromFile(outFile),
                    outputFile = outFile,
                    durationMs = (trimEndMs - trimStartMs).coerceAtLeast(200L),
                )
            )
        }

        try {
            val retriever = MediaMetadataRetriever()
            var metaW = targetWidth.takeIf { it > 0 } ?: 1280
            var metaH = targetHeight.takeIf { it > 0 } ?: 720
            var metaDur = 0L
            try {
                retriever.setDataSource(context, sourceUri)
                metaW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: metaW
                metaH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: metaH
                metaDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                runCatching { retriever.release() }
            }

            val srcDur = metaDur.coerceAtLeast(250L)
            val safeStartMs = trimStartMs.coerceIn(0L, (srcDur - 200L).coerceAtLeast(0L))
            val safeEndMs = trimEndMs.coerceIn(safeStartMs + 200L, srcDur)
            val spanMs = (safeEndMs - safeStartMs).coerceAtLeast(200L)

            val scaleDown = minOf(1f, targetHeight.coerceIn(480, 2160).toFloat() / maxOf(metaW, metaH).toFloat())
            val encW = (((metaW * scaleDown).roundToInt().coerceAtLeast(256)) / 2) * 2
            val encH = (((metaH * scaleDown).roundToInt().coerceAtLeast(256)) / 2) * 2
            val safeFps = targetFps.coerceIn(24, 60)

            onProgress(5)
            val decodedFrames = extractExportFrames(
                context = context,
                uri = sourceUri,
                startMs = safeStartMs,
                endMs = safeEndMs,
                targetW = encW,
                targetH = encH,
                targetFps = safeFps,
                tempDir = workDir,
                isCancelled = isCancelled,
                onDecodeProgress = { p -> onProgress((5 + p * 45f).roundToInt()) },
            )
            if (isCancelled()) return Result.failure(IllegalStateException("Export cancelled"))
            if (decodedFrames.isEmpty()) {
                return Result.failure(IllegalStateException("Unable to decode frames for Timeslice export."))
            }

            val videoOnlyFile = File(workDir, "ts_video.mp4")
            encodeTimesliceFrames(
                decodedFrames = decodedFrames,
                width = encW,
                height = encH,
                startMs = safeStartMs,
                endMs = safeEndMs,
                fps = safeFps,
                settings = settings,
                outputFile = videoOnlyFile,
                isCancelled = isCancelled,
                onEncodeProgress = { p -> onProgress((50 + p * 40f).roundToInt()) },
            )
            if (isCancelled()) return Result.failure(IllegalStateException("Export cancelled"))

            val audioOnlyFile = File(workDir, "ts_audio.mp4")
            val hasAudio = runCatching {
                extractAudioTrimmed(
                    context = context,
                    uri = sourceUri,
                    startMs = safeStartMs,
                    endMs = safeEndMs,
                    outputFile = audioOnlyFile,
                )
            }.getOrDefault(false)

            if (hasAudio && audioOnlyFile.exists() && audioOnlyFile.length() > 0L) {
                muxVideoAndAudio(videoOnlyFile, audioOnlyFile, outFile)
            } else {
                videoOnlyFile.copyTo(outFile, overwrite = true)
            }
            onProgress(100)
            return Result.success(
                TimesliceResult(
                    outputUri = Uri.fromFile(outFile),
                    outputFile = outFile,
                    durationMs = spanMs,
                )
            )
        } catch (e: Exception) {
            runCatching { outFile.delete() }
            return Result.failure(e)
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    private data class TimedFrameFile(
        val timeMs: Long,
        val file: File,
    )

    private fun extractExportFrames(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        targetW: Int,
        targetH: Int,
        targetFps: Int,
        tempDir: File,
        isCancelled: () -> Boolean,
        onDecodeProgress: (Float) -> Unit,
    ): List<TimedFrameFile> {
        val codecList = runCatching {
            extractFramesViaCodec(context, uri, startMs, endMs, targetW, targetH, targetFps, tempDir, isCancelled, onDecodeProgress)
        }.getOrDefault(emptyList())
        if (codecList.size >= 2) return codecList
        return extractFramesViaRetrieverFallback(context, uri, startMs, endMs, targetW, targetH, targetFps, tempDir, isCancelled, onDecodeProgress)
    }

    private fun extractFramesViaCodec(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        targetW: Int,
        targetH: Int,
        targetFps: Int,
        tempDir: File,
        isCancelled: () -> Boolean,
        onDecodeProgress: (Float) -> Unit,
    ): List<TimedFrameFile> {
        val extractor = MediaExtractor()
        val results = mutableListOf<TimedFrameFile>()
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return emptyList()
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            val startUs = startMs * 1_000L
            val endUs = endMs * 1_000L
            val spanUs = (endUs - startUs).coerceAtLeast(1_000L)
            val gapUs = (1_000_000L / targetFps).coerceAtLeast(14_000L)
            val maxFrames = ((spanUs / gapUs).toInt() + 12).coerceIn(8, 1_200)

            val outputSurface = DecoderOutputSurface(targetW, targetH)
            val decoder = MediaCodec.createDecoderByType(mime)
            try {
                decoder.configure(format, outputSurface.surface, null, 0)
                decoder.start()
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val info = MediaCodec.BufferInfo()
                var inEos = false
                var outEos = false
                var lastSavedUs = Long.MIN_VALUE

                while (!outEos && !isCancelled() && results.size < maxFrames) {
                    if (!inEos) {
                        val inIdx = decoder.dequeueInputBuffer(10_000L)
                        if (inIdx >= 0) {
                            val buf = decoder.getInputBuffer(inIdx)!!
                            val sz = extractor.readSampleData(buf, 0)
                            val st = extractor.sampleTime
                            if (sz < 0 || st > endUs + 500_000L) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inEos = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, sz, st, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = decoder.dequeueOutputBuffer(info, 10_000L)
                    if (outIdx >= 0) {
                        val ptsUs = info.presentationTimeUs
                        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val inWin = info.size > 0 && ptsUs >= startUs - 16_000L && ptsUs <= endUs + 16_000L
                        val spaced = lastSavedUs == Long.MIN_VALUE || (ptsUs - lastSavedUs) >= gapUs
                        val render = inWin && spaced
                        decoder.releaseOutputBuffer(outIdx, render)
                        if (render) {
                            outputSurface.awaitNewImage()
                            outputSurface.drawImage()
                            val bmp = outputSurface.readBitmap()
                            val file = File(tempDir, String.format("tf_%05d.jpg", results.size))
                            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                            bmp.recycle()
                            results.add(TimedFrameFile((ptsUs / 1_000L).coerceIn(startMs, endMs), file))
                            lastSavedUs = ptsUs
                            onDecodeProgress(((ptsUs - startUs).toFloat() / spanUs.toFloat()).coerceIn(0f, 1f))
                        }
                        if (eos || ptsUs > endUs + 250_000L) outEos = true
                    }
                }
            } finally {
                runCatching { decoder.stop() }
                runCatching { decoder.release() }
                runCatching { outputSurface.release() }
            }
        } finally {
            runCatching { extractor.release() }
        }
        return results
    }

    private fun extractFramesViaRetrieverFallback(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        targetW: Int,
        targetH: Int,
        targetFps: Int,
        tempDir: File,
        isCancelled: () -> Boolean,
        onDecodeProgress: (Float) -> Unit,
    ): List<TimedFrameFile> {
        val retriever = MediaMetadataRetriever()
        val results = mutableListOf<TimedFrameFile>()
        try {
            retriever.setDataSource(context, uri)
            val spanMs = (endMs - startMs).coerceAtLeast(200L)
            val count = ((spanMs * targetFps) / 1000L).toInt().coerceIn(8, 420)
            for (i in 0 until count) {
                if (isCancelled()) break
                val frac = if (count <= 1) 0f else i.toFloat() / (count - 1).toFloat()
                val tMs = startMs + (spanMs * frac).toLong()
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(tMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST, targetW, targetH)
                        ?: retriever.getScaledFrameAtTime(tMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
                } else {
                    retriever.getFrameAtTime(tMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { Bitmap.createScaledBitmap(it, targetW, targetH, true) }
                }
                if (bmp != null) {
                    val file = File(tempDir, String.format("tr_%05d.jpg", results.size))
                    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    bmp.recycle()
                    results.add(TimedFrameFile(tMs.coerceIn(startMs, endMs), file))
                }
                onDecodeProgress(frac)
            }
        } finally {
            runCatching { retriever.release() }
        }
        return results
    }

    private fun encodeTimesliceFrames(
        decodedFrames: List<TimedFrameFile>,
        width: Int,
        height: Int,
        startMs: Long,
        endMs: Long,
        fps: Int,
        settings: TimesliceSettings,
        outputFile: File,
        isCancelled: () -> Boolean,
        onEncodeProgress: (Float) -> Unit,
    ) {
        val spanMs = (endMs - startMs).coerceAtLeast(200L)
        val totalOutFrames = ((spanMs * fps) / 1000L).toInt().coerceIn(4, 1_500)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (width * height * 6).coerceIn(2_500_000, 16_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val inputSurface: EncoderInputSurface
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrack = -1
        var muxerStarted = false

        val bmpCache = object : LruCache<Int, Bitmap>(36) {
            override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
                if (evicted && !oldValue.isRecycled) oldValue.recycle()
            }
        }
        val composeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val composeCanvas = Canvas(composeBitmap)

        fun lookupNearestDecoded(sampleMs: Long): Bitmap? {
            if (decodedFrames.isEmpty()) return null
            // Binary search nearest timestamp
            var lo = 0
            var hi = decodedFrames.lastIndex
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (decodedFrames[mid].timeMs < sampleMs) lo = mid + 1 else hi = mid
            }
            val idx = if (lo > 0 && abs(decodedFrames[lo - 1].timeMs - sampleMs) <= abs(decodedFrames[lo].timeMs - sampleMs)) {
                lo - 1
            } else lo
            bmpCache.get(idx)?.let { if (!it.isRecycled) return it }
            val loaded = BitmapFactory.decodeFile(decodedFrames[idx].file.absolutePath) ?: return null
            bmpCache.put(idx, loaded)
            return loaded
        }

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = EncoderInputSurface(encoder.createInputSurface(), width, height)
            encoder.start()
            inputSurface.makeCurrent()

            val info = MediaCodec.BufferInfo()
            val stepNs = (1_000_000_000L / fps).coerceAtLeast(8_333_333L)

            fun drain(eos: Boolean) {
                if (eos) encoder.signalEndOfInputStream()
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(info, 10_000L)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!eos) break
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        videoTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (outIdx >= 0) {
                        val encBuf = encoder.getOutputBuffer(outIdx)
                        if (encBuf != null && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && muxerStarted) {
                            encBuf.position(info.offset)
                            encBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(videoTrack, encBuf, info)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }

            for (f in 0 until totalOutFrames) {
                if (isCancelled()) break
                val frac = if (totalOutFrames <= 1) 0f else f.toFloat() / totalOutFrames.toFloat()
                val tMs = (startMs + (spanMs * frac).toLong()).coerceIn(startMs, endMs)
                composeCanvas.drawColor(android.graphics.Color.BLACK)
                TimesliceCompositor.drawTimesliceFrame(
                    canvas = composeCanvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    baseTimeMs = tMs,
                    clipStartMs = startMs,
                    clipEndMs = endMs,
                    settings = settings,
                    fitMode = CanvasFitMode.FILL,
                    frameProvider = { reqMs -> lookupNearestDecoded(reqMs) },
                )
                inputSurface.drawBitmap(composeBitmap)
                inputSurface.setPresentationTime(f * stepNs)
                inputSurface.swapBuffers()
                drain(false)
                onEncodeProgress(frac)
            }
            drain(true)
            inputSurface.release()
        } finally {
            bmpCache.evictAll()
            if (!composeBitmap.isRecycled) composeBitmap.recycle()
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun extractAudioTrimmed(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        outputFile: File,
    ): Boolean {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            extractor.setDataSource(context, uri, null)
            var audioIdx = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioIdx = i
                    break
                }
            }
            if (audioIdx < 0) return false
            extractor.selectTrack(audioIdx)
            val fmt = extractor.getTrackFormat(audioIdx)
            val outTrack = muxer.addTrack(fmt)
            muxer.start()
            muxerStarted = true

            val startUs = startMs * 1_000L
            val endUs = endMs * 1_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val buf = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val sz = extractor.readSampleData(buf, 0)
                val st = extractor.sampleTime
                if (sz < 0 || st > endUs) break
                if (st >= startUs - 20_000L) {
                    info.set(0, sz, (st - startUs).coerceAtLeast(0L), extractor.sampleFlags)
                    muxer.writeSampleData(outTrack, buf, info)
                }
                extractor.advance()
            }
            return true
        } finally {
            runCatching { extractor.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
        val vExt = MediaExtractor()
        val aExt = MediaExtractor()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            vExt.setDataSource(videoFile.absolutePath)
            aExt.setDataSource(audioFile.absolutePath)
            vExt.selectTrack(0)
            aExt.selectTrack(0)
            val vTrack = muxer.addTrack(vExt.getTrackFormat(0))
            val aTrack = muxer.addTrack(aExt.getTrackFormat(0))
            muxer.start()
            muxerStarted = true
            val buf = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val sz = vExt.readSampleData(buf, 0)
                if (sz < 0) break
                info.set(0, sz, vExt.sampleTime, vExt.sampleFlags)
                muxer.writeSampleData(vTrack, buf, info)
                vExt.advance()
            }
            while (true) {
                val sz = aExt.readSampleData(buf, 0)
                if (sz < 0) break
                info.set(0, sz, aExt.sampleTime, aExt.sampleFlags)
                muxer.writeSampleData(aTrack, buf, info)
                aExt.advance()
            }
        } finally {
            runCatching { vExt.release() }
            runCatching { aExt.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private class DecoderOutputSurface(
        private val width: Int,
        private val height: Int,
    ) : SurfaceTexture.OnFrameAvailableListener {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private val frameSync = Object()
        private var frameAvailable = false

        private val stMatrix = FloatArray(16)
        private val mvpMatrix = FloatArray(16)
        private var texId = 0
        private var program = 0
        private var posHandle = 0
        private var texHandle = 0
        private var mvpHandle = 0
        private var stHandle = 0
        val surface: Surface
        private val st: SurfaceTexture
        private val quadBuf: FloatBuffer

        init {
            val quadData = floatArrayOf(
                -1f, -1f, 0f, 0f, 0f,
                1f, -1f, 0f, 1f, 0f,
                -1f, 1f, 0f, 0f, 1f,
                1f, 1f, 0f, 1f, 1f,
            )
            quadBuf = ByteBuffer.allocateDirect(quadData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(quadData)
            quadBuf.position(0)
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val vers = IntArray(2)
            EGL14.eglInitialize(eglDisplay, vers, 0, vers, 1)
            val cfgAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE,
            )
            val cfgs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, cfgAttribs, 0, cfgs, 0, 1, num, 0)
            eglContext = EGL14.eglCreateContext(eglDisplay, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texId = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            program = buildGlProgram(OES_VERTEX, OES_FRAGMENT)
            posHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            stHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
            Matrix.setIdentityM(mvpMatrix, 0)
            Matrix.scaleM(mvpMatrix, 0, 1f, -1f, 1f)
            st = SurfaceTexture(texId)
            st.setOnFrameAvailableListener(this)
            surface = Surface(st)
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
            synchronized(frameSync) {
                frameAvailable = true
                frameSync.notifyAll()
            }
        }

        fun awaitNewImage() {
            synchronized(frameSync) {
                if (!frameAvailable) frameSync.wait(1_500L)
                frameAvailable = false
            }
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)
        }

        fun drawImage() {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            quadBuf.position(0)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 20, quadBuf)
            GLES20.glEnableVertexAttribArray(posHandle)
            quadBuf.position(3)
            GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 20, quadBuf)
            GLES20.glEnableVertexAttribArray(texHandle)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(stHandle, 1, false, stMatrix, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glFinish()
        }

        fun readBitmap(): Bitmap {
            val buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            buf.rewind()
            bmp.copyPixelsFromBuffer(buf)
            return bmp
        }

        fun release() {
            runCatching { surface.release() }
            runCatching { st.release() }
            if (program != 0) runCatching { GLES20.glDeleteProgram(program) }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        }
    }

    private class EncoderInputSurface(
        private val surface: Surface,
        private val width: Int,
        private val height: Int,
    ) {
        private val eglDisplay: EGLDisplay
        private val eglContext: EGLContext
        private val eglSurface: EGLSurface
        private val program: Int
        private val texId: Int
        private val posHandle: Int
        private val texHandle: Int
        private val quadBuf: FloatBuffer

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val vers = IntArray(2)
            EGL14.eglInitialize(eglDisplay, vers, 0, vers, 1)
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, 0x3142, 1, EGL14.EGL_NONE,
            )
            val cfgs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribs, 0, cfgs, 0, 1, num, 0)
            eglContext = EGL14.eglCreateContext(eglDisplay, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, cfgs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            val quadData = floatArrayOf(
                -1f, -1f, 0f, 0f, 1f,
                1f, -1f, 0f, 1f, 1f,
                -1f, 1f, 0f, 0f, 0f,
                1f, 1f, 0f, 1f, 0f,
            )
            quadBuf = ByteBuffer.allocateDirect(quadData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(quadData)
            quadBuf.position(0)
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texId = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            program = buildGlProgram(TEX2D_VERTEX, TEX2D_FRAGMENT)
            posHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun drawBitmap(bmp: Bitmap) {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            quadBuf.position(0)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 20, quadBuf)
            GLES20.glEnableVertexAttribArray(posHandle)
            quadBuf.position(3)
            GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 20, quadBuf)
            GLES20.glEnableVertexAttribArray(texHandle)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        fun swapBuffers() {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (program != 0) runCatching { GLES20.glDeleteProgram(program) }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            runCatching { surface.release() }
        }
    }

    private fun buildGlProgram(vSrc: String, fSrc: String): Int {
        fun compile(t: Int, s: String): Int = GLES20.glCreateShader(t).also {
            GLES20.glShaderSource(it, s)
            GLES20.glCompileShader(it)
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fSrc)
        return GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, vs)
            GLES20.glAttachShader(p, fs)
            GLES20.glLinkProgram(p)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
        }
    }

    private const val OES_VERTEX = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uSTMatrix;
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTextureCoord = (uSTMatrix * aTextureCoord).xy;
        }
    """

    private const val OES_FRAGMENT = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        void main() {
            gl_FragColor = texture2D(sTexture, vTextureCoord);
        }
    """

    private const val TEX2D_VERTEX = """
        attribute vec4 aPosition;
        attribute vec2 aTextureCoord;
        varying vec2 vTextureCoord;
        void main() {
            gl_Position = aPosition;
            vTextureCoord = aTextureCoord;
        }
    """

    private const val TEX2D_FRAGMENT = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        void main() {
            gl_FragColor = texture2D(sTexture, vTextureCoord);
        }
    """
}
