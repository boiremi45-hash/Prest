package com.example.domain.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
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
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Generates a real, frame-by-frame reversed MP4 file for any VideoClipSegment.
 * Decodes `[trimStartMs..trimEndMs]` GOP-by-GOP or frame-by-frame onto disk-backed frame files
 * (preventing OOM on high-resolution footage), then re-encodes frames N-1..0 using MediaCodec + EGL
 * with optional 16-bit PCM audio reversal (`reverseAudio = true`) into a muxed `.mp4` inside `filesDir/luna_media/reversed/`.
 */
object VideoReverseProcessor {

    data class ReverseResult(
        val reversedUri: Uri,
        val outputFile: File,
        val durationMs: Long,
        val frameCount: Int,
    )

    suspend fun generateReversedClipMp4(
        context: Context,
        sourceUri: Uri,
        clipId: String,
        trimStartMs: Long,
        trimEndMs: Long,
        reverseAudio: Boolean,
        targetWidth: Int,
        targetHeight: Int,
        minFrameGapUs: Long = 30_000L,
        onProgress: (percent: Int, label: String) -> Unit,
    ): Result<ReverseResult> = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "rev_tmp_${UUID.randomUUID()}").apply { mkdirs() }
        val outDir = File(context.filesDir, "luna_media/reversed").apply { mkdirs() }
        val qualityTag = if (minFrameGapUs < 25_000L) "_hq" else ""
        val outFile = File(outDir, "rev_${clipId.take(12)}_${trimStartMs}_${trimEndMs}_${if (reverseAudio) "ra" else "fa"}$qualityTag.mp4")
        if (outFile.exists()) outFile.delete()

        try {
            onProgress(3, "Inspecting source clip...")
            val meta = VideoExportManager.readVideoMetadata(context, sourceUri)
            val srcDurMs = meta.durationMs.coerceAtLeast(250L)
            val safeStartMs = trimStartMs.coerceIn(0L, (srcDurMs - 200L).coerceAtLeast(0L))
            val safeEndMs = trimEndMs.coerceIn(safeStartMs + 200L, srcDurMs)
            val spanDurMs = (safeEndMs - safeStartMs).coerceAtLeast(200L)

            // Cap encoding resolution cleanly at <= 1080p even numbers for fast, reliable MediaCodec surface encoding
            val rawW = targetWidth.takeIf { it > 0 } ?: meta.width.takeIf { it > 0 } ?: 1280
            val rawH = targetHeight.takeIf { it > 0 } ?: meta.height.takeIf { it > 0 } ?: 720
            val scaleDown = minOf(1f, 1280f / maxOf(rawW, rawH).toFloat())
            val encW = (((rawW * scaleDown).roundToInt().coerceAtLeast(256)) / 2) * 2
            val encH = (((rawH * scaleDown).roundToInt().coerceAtLeast(256)) / 2) * 2

            onProgress(8, "Decoding frames...")
            val frameFiles = extractFramesInRange(
                context = context,
                uri = sourceUri,
                startMs = safeStartMs,
                endMs = safeEndMs,
                targetW = encW,
                targetH = encH,
                tempDir = workDir,
                minFrameGapUs = minFrameGapUs,
                onDecodeProgress = { p -> onProgress(8 + (p * 0.42f).roundToInt(), "Decoding video frames...") },
            )

            if (frameFiles.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Unable to decode any video frames for reversal."))
            }

            onProgress(52, "Re-encoding reversed frames...")
            val videoOnlyFile = File(workDir, "video_rev.mp4")
            encodeBitmapsReverse(
                frameFiles = frameFiles,
                width = encW,
                height = encH,
                totalDurationMs = spanDurMs,
                outputFile = videoOnlyFile,
                onEncodeProgress = { p -> onProgress(52 + (p * 0.30f).roundToInt(), "Encoding reversed video...") },
            )

            // Decode and reverse (or keep) audio if present in source
            val audioFile = File(workDir, "audio_rev.mp4")
            val hasAudio = runCatching {
                extractAndEncodeAudio(
                    context = context,
                    uri = sourceUri,
                    startMs = safeStartMs,
                    endMs = safeEndMs,
                    reversePcm = reverseAudio,
                    outputFile = audioFile,
                )
            }.getOrDefault(false)

            onProgress(88, "Finalizing reversed clip...")
            if (hasAudio && audioFile.exists() && audioFile.length() > 0L) {
                muxVideoAndAudio(videoOnlyFile, audioFile, outFile)
            } else {
                videoOnlyFile.copyTo(outFile, overwrite = true)
            }

            onProgress(100, "Reverse complete")
            Result.success(
                ReverseResult(
                    reversedUri = Uri.fromFile(outFile),
                    outputFile = outFile,
                    durationMs = spanDurMs,
                    frameCount = frameFiles.size,
                )
            )
        } catch (e: Exception) {
            runCatching { outFile.delete() }
            Result.failure(e)
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    private fun extractFramesInRange(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        targetW: Int,
        targetH: Int,
        tempDir: File,
        minFrameGapUs: Long,
        onDecodeProgress: (Float) -> Unit,
    ): List<File> {
        val codecFrames = runCatching {
            extractFramesViaMediaCodec(context, uri, startMs, endMs, targetW, targetH, tempDir, minFrameGapUs, onDecodeProgress)
        }.getOrDefault(emptyList())
        if (codecFrames.size >= 2) return codecFrames

        // Robust fallback via MediaMetadataRetriever if hardware decoder SurfaceTexture yielded 0 frames
        return extractFramesViaRetriever(context, uri, startMs, endMs, targetW, targetH, tempDir, minFrameGapUs, onDecodeProgress)
    }

    private fun extractFramesViaMediaCodec(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        targetW: Int,
        targetH: Int,
        tempDir: File,
        minFrameGapUs: Long,
        onDecodeProgress: (Float) -> Unit,
    ): List<File> {
        val extractor = MediaExtractor()
        val results = mutableListOf<File>()
        try {
            extractor.setDataSource(context, uri, null)
            var videoTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    format = f
                    break
                }
            }
            if (videoTrackIndex < 0 || format == null) return emptyList()
            extractor.selectTrack(videoTrackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            val startUs = startMs * 1_000L
            val endUs = endMs * 1_000L
            val spanUs = (endUs - startUs).coerceAtLeast(1_000L)

            val outputSurface = DecoderOutputSurface(targetW, targetH)
            val decoder = MediaCodec.createDecoderByType(mime)
            try {
                decoder.configure(format, outputSurface.surface, null, 0)
                decoder.start()

                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false
                var lastSavedUs = Long.MIN_VALUE
                val safeGapUs = minFrameGapUs.coerceAtLeast(8_000L)
                val maxFrames = ((spanUs / safeGapUs).toInt() + 12).coerceIn(12, 900)

                while (!sawOutputEOS && results.size < maxFrames) {
                    if (!sawInputEOS) {
                        val inIdx = decoder.dequeueInputBuffer(10_000L)
                        if (inIdx >= 0) {
                            val buf = decoder.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(buf, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || sampleTimeUs > endUs + 500_000L) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIdx >= 0) {
                        val ptsUs = bufferInfo.presentationTimeUs
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val inWindow = bufferInfo.size > 0 && ptsUs >= startUs - 15_000L && ptsUs <= endUs + 15_000L
                        val spacedOk = lastSavedUs == Long.MIN_VALUE || (ptsUs - lastSavedUs) >= safeGapUs

                        val render = inWindow && spacedOk
                        decoder.releaseOutputBuffer(outIdx, render)
                        if (render) {
                            outputSurface.awaitNewImage()
                            outputSurface.drawImage()
                            val bmp = outputSurface.readBitmap()
                            val frameFile = File(tempDir, String.format("f_%05d.jpg", results.size))
                            FileOutputStream(frameFile).use { fos ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                            }
                            bmp.recycle()
                            results.add(frameFile)
                            lastSavedUs = ptsUs
                            onDecodeProgress(((ptsUs - startUs).toFloat() / spanUs.toFloat()).coerceIn(0f, 1f))
                        }
                        if (eos || ptsUs > endUs + 250_000L) {
                            sawOutputEOS = true
                        }
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

    private fun extractFramesViaRetriever(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        targetW: Int,
        targetH: Int,
        tempDir: File,
        minFrameGapUs: Long,
        onDecodeProgress: (Float) -> Unit,
    ): List<File> {
        val retriever = MediaMetadataRetriever()
        val files = mutableListOf<File>()
        try {
            retriever.setDataSource(context, uri)
            val spanMs = (endMs - startMs).coerceAtLeast(200L)
            val fps = if (minFrameGapUs <= 18_000L) 60 else 24
            val totalFrames = ((spanMs * fps) / 1000L).toInt().coerceIn(8, 360)
            for (i in 0 until totalFrames) {
                val frac = if (totalFrames <= 1) 0f else i.toFloat() / (totalFrames - 1).toFloat()
                val timeUs = (startMs + (spanMs * frac).toLong()) * 1_000L
                val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, targetW, targetH)
                        ?: retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { Bitmap.createScaledBitmap(it, targetW, targetH, true) }
                }
                if (bmp != null) {
                    val file = File(tempDir, String.format("r_%05d.jpg", files.size))
                    FileOutputStream(file).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    bmp.recycle()
                    files.add(file)
                }
                onDecodeProgress(frac)
            }
        } finally {
            runCatching { retriever.release() }
        }
        return files
    }

    private fun encodeBitmapsReverse(
        frameFiles: List<File>,
        width: Int,
        height: Int,
        totalDurationMs: Long,
        outputFile: File,
        onEncodeProgress: (Float) -> Unit,
    ) {
        val encFps = if (totalDurationMs <= 650L && frameFiles.size > 16) 60 else 30
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (width * height * 6).coerceIn(2_000_000, 14_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, encFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val inputSurface: EncoderInputSurface
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrackIndex = -1
        var muxerStarted = false

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = EncoderInputSurface(encoder.createInputSurface(), width, height)
            encoder.start()
            inputSurface.makeCurrent()

            val totalNs = totalDurationMs.coerceAtLeast(200L) * 1_000_000L
            val reversed = frameFiles.asReversed()
            val count = reversed.size
            val stepNs = (totalNs / count.coerceAtLeast(1)).coerceAtLeast(8_333_333L)
            val bufferInfo = MediaCodec.BufferInfo()

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) break
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (outIdx >= 0) {
                        val encoded = encoder.getOutputBuffer(outIdx)
                        if (encoded != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encoded, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }

            reversed.forEachIndexed { idx, file ->
                val rawBmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                val sizedBmp = if (rawBmp.width != width || rawBmp.height != height) {
                    val canvasBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val c = Canvas(canvasBmp)
                    c.drawBitmap(rawBmp, null, Rect(0, 0, width, height), null)
                    rawBmp.recycle()
                    canvasBmp
                } else {
                    rawBmp
                }
                inputSurface.drawBitmap(sizedBmp)
                sizedBmp.recycle()
                inputSurface.setPresentationTime(idx * stepNs)
                inputSurface.swapBuffers()
                drainEncoder(false)
                onEncodeProgress((idx + 1).toFloat() / count.toFloat())
            }
            // Write final anchor frame slightly before end so last timestamp covers full clip duration cleanly
            drainEncoder(true)
            inputSurface.release()
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }

    private fun extractAndEncodeAudio(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        reversePcm: Boolean,
        outputFile: File,
    ): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var inFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inFormat = f
                    break
                }
            }
            if (audioTrackIndex < 0 || inFormat == null) return false
            extractor.selectTrack(audioTrackIndex)

            val mime = inFormat.getString(MediaFormat.KEY_MIME) ?: return false
            var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inFormat, null, null, 0)
            decoder.start()

            val startUs = startMs * 1_000L
            val endUs = endMs * 1_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val pcmOut = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inEos = false
            var outEos = false

            while (!outEos) {
                if (!inEos) {
                    val inIdx = decoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val sz = extractor.readSampleData(buf, 0)
                        val pts = extractor.sampleTime
                        if (sz < 0 || pts > endUs + 50_000L) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sz, pts, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, 10_000L)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val df = decoder.outputFormat
                    if (df.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = df.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (df.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channelCount = df.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
                } else if (outIdx >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0 && info.presentationTimeUs in startUs..endUs) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(chunk)
                        pcmOut.write(chunk)
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || info.presentationTimeUs > endUs) {
                        outEos = true
                    }
                }
            }
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            decoder = null

            val pcmBytes = pcmOut.toByteArray()
            if (pcmBytes.isEmpty()) return false

            val bytesPerSampleFrame = channelCount * 2
            val alignedLen = (pcmBytes.size / bytesPerSampleFrame) * bytesPerSampleFrame
            if (alignedLen <= 0) return false

            if (reversePcm) {
                // Reverse 16-bit audio sample frames cleanly (preserves stereo L/R channel order per sample frame)
                var lo = 0
                var hi = alignedLen - bytesPerSampleFrame
                while (lo < hi) {
                    for (b in 0 until bytesPerSampleFrame) {
                        val tmp = pcmBytes[lo + b]
                        pcmBytes[lo + b] = pcmBytes[hi + b]
                        pcmBytes[hi + b] = tmp
                    }
                    lo += bytesPerSampleFrame
                    hi -= bytesPerSampleFrame
                }
            }

            // Encode PCM -> AAC .mp4
            val encFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var outTrack = -1
            var offset = 0
            var totalSamples = 0L
            var encDone = false
            var fedEos = false
            val encInfo = MediaCodec.BufferInfo()

            while (!encDone) {
                if (!fedEos) {
                    val inIdx = encoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val buf = encoder.getInputBuffer(inIdx)!!
                        buf.clear()
                        if (offset >= alignedLen) {
                            encoder.queueInputBuffer(inIdx, 0, 0, (totalSamples * 1_000_000L) / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            fedEos = true
                        } else {
                            val chunkSize = minOf(buf.capacity(), alignedLen - offset)
                            val usable = (chunkSize / bytesPerSampleFrame) * bytesPerSampleFrame
                            buf.put(pcmBytes, offset, usable)
                            val ptsUs = (totalSamples * 1_000_000L) / sampleRate
                            encoder.queueInputBuffer(inIdx, 0, usable, ptsUs, 0)
                            offset += usable
                            totalSamples += (usable / bytesPerSampleFrame)
                        }
                    }
                }
                val outIdx = encoder.dequeueOutputBuffer(encInfo, 10_000L)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIdx >= 0) {
                    val encoded = encoder.getOutputBuffer(outIdx)
                    if (encoded != null && (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && encInfo.size > 0 && muxerStarted) {
                        encoded.position(encInfo.offset)
                        encoded.limit(encInfo.offset + encInfo.size)
                        muxer.writeSampleData(outTrack, encoded, encInfo)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) encDone = true
                }
            }
            return true
        } finally {
            runCatching { extractor.release() }
            decoder?.let { runCatching { it.stop(); it.release() } }
            encoder?.let { runCatching { it.stop(); it.release() } }
            muxer?.let { m ->
                if (muxerStarted) runCatching { m.stop() }
                runCatching { m.release() }
            }
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
        private val frameSyncObj = Object()
        private var frameAvailable = false

        private val stMatrix = FloatArray(16)
        private val mvpMatrix = FloatArray(16)
        private var textureId = 0
        private var program = 0
        private var positionHandle = 0
        private var texCoordHandle = 0
        private var stMatrixHandle = 0
        private var mvpMatrixHandle = 0

        val surface: Surface
        private val surfaceTexture: SurfaceTexture
        private val quadBuffer: FloatBuffer

        init {
            val quadData = floatArrayOf(
                -1f, -1f, 0f, 0f, 0f,
                1f, -1f, 0f, 1f, 0f,
                -1f, 1f, 0f, 0f, 1f,
                1f, 1f, 0f, 1f, 1f,
            )
            quadBuffer = ByteBuffer.allocateDirect(quadData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(quadData)
            quadBuffer.position(0)

            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val vers = IntArray(2)
            EGL14.eglInitialize(eglDisplay, vers, 0, vers, 1)
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0)
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], pbufferAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            textureId = texIds[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            program = buildGlProgram(OES_VERTEX, OES_FRAGMENT)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            stMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
            Matrix.setIdentityM(mvpMatrix, 0)
            // Flip vertically for glReadPixels bottom-to-top -> Bitmap top-to-bottom conversion
            Matrix.scaleM(mvpMatrix, 0, 1f, -1f, 1f)

            surfaceTexture = SurfaceTexture(textureId)
            surfaceTexture.setOnFrameAvailableListener(this)
            surface = Surface(surfaceTexture)
        }

        override fun onFrameAvailable(st: SurfaceTexture?) {
            synchronized(frameSyncObj) {
                frameAvailable = true
                frameSyncObj.notifyAll()
            }
        }

        fun awaitNewImage() {
            synchronized(frameSyncObj) {
                if (!frameAvailable) {
                    frameSyncObj.wait(1_500L)
                }
                frameAvailable = false
            }
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(stMatrix)
        }

        fun drawImage() {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            quadBuffer.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 20, quadBuffer)
            GLES20.glEnableVertexAttribArray(positionHandle)
            quadBuffer.position(3)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 20, quadBuffer)
            GLES20.glEnableVertexAttribArray(texCoordHandle)

            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, stMatrix, 0)
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
            runCatching { surfaceTexture.release() }
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
        private val quadBuffer: FloatBuffer

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val vers = IntArray(2)
            EGL14.eglInitialize(eglDisplay, vers, 0, vers, 1)
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID (constant safe across minSdk 24+)
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0)
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            val quadData = floatArrayOf(
                -1f, -1f, 0f, 0f, 1f,
                1f, -1f, 0f, 1f, 1f,
                -1f, 1f, 0f, 0f, 0f,
                1f, 1f, 0f, 1f, 0f,
            )
            quadBuffer = ByteBuffer.allocateDirect(quadData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(quadData)
            quadBuffer.position(0)

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            texId = texIds[0]
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

            quadBuffer.position(0)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 20, quadBuffer)
            GLES20.glEnableVertexAttribArray(posHandle)
            quadBuffer.position(3)
            GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 20, quadBuffer)
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

    private fun buildGlProgram(vertexSrc: String, fragmentSrc: String): Int {
        fun compileShader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return p
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
