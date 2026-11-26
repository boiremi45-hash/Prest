package com.example.domain.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
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
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.util.LruCache
import android.view.Surface
import com.example.data.model.AspectPreset
import com.example.data.model.CanvasFitMode
import com.example.data.model.ClipTimelineSpan
import com.example.data.model.KeyframeEvaluator
import com.example.data.model.SpeedMode
import com.example.data.model.TimelineSnapshot
import com.example.data.model.VideoClipSegment
import com.example.data.model.computeTotalVideoDurationMs
import com.example.data.model.computeTrackSpans
import com.example.data.model.findActiveSpanIndex
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Composites visible multi-track video layers (V1 bottom -> V2 -> V3 -> V4+ top)
 * with exact per-clip transforms, keyframes, opacity, crop, mirror, speed curves,
 * and canvas aspect ratio into a single synchronized H.264 MP4 base video track.
 */
object MultiTrackExportRenderer {

    data class CompositeResult(
        val outputUri: Uri,
        val outputFile: File,
        val durationMs: Long,
        val width: Int,
        val height: Int,
    )

    fun compositeVideoTracksBlocking(
        context: Context,
        sourceVideoUri: Uri,
        snapshot: TimelineSnapshot,
        aspectPreset: AspectPreset,
        canvasFitMode: CanvasFitMode,
        targetHeight: Int,
        targetFps: Int,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit,
    ): Result<CompositeResult> {
        val visibleClips = snapshot.clips.filter { clip ->
            !clip.effectiveUriString().isNullOrBlank() && !snapshot.isVideoTrackHidden(clip.trackIndex)
        }
        if (visibleClips.isEmpty()) {
            return Result.failure(IllegalStateException("No visible video tracks to export."))
        }

        val trackCount = snapshot.effectiveVideoTrackCount()
        val spansByTrack = (0 until trackCount)
            .filter { !snapshot.isVideoTrackHidden(it) }
            .associateWith { idx -> computeTrackSpans(visibleClips, idx) }
            .filterValues { it.isNotEmpty() }

        val totalDurationMs = computeTotalVideoDurationMs(visibleClips).coerceAtLeast(500L)
        val safeH = ((targetHeight.coerceIn(480, 2160)) / 2) * 2
        val safeW = (((safeH * aspectPreset.ratio).roundToInt().coerceIn(270, 3840)) / 2) * 2
        val safeFps = targetFps.coerceIn(24, 60)

        val dir = File(context.cacheDir, "multitrack_exports").apply { mkdirs() }
        val outFile = File(dir, "mt_${UUID.randomUUID().toString().take(8)}.mp4")

        val retrievers = mutableMapOf<String, MediaMetadataRetriever>()
        val frameCaches = mutableMapOf<String, LruCache<Long, Bitmap>>()

        fun getFrameBitmap(uri: Uri, timeMs: Long): Bitmap? {
            val key = uri.toString()
            val retriever = retrievers.getOrPut(key) {
                MediaMetadataRetriever().apply { setDataSource(context, uri) }
            }
            val cache = frameCaches.getOrPut(key) {
                object : LruCache<Long, Bitmap>(28) {
                    override fun entryRemoved(evicted: Boolean, k: Long, oldValue: Bitmap, newValue: Bitmap?) {
                        if (evicted && !oldValue.isRecycled) oldValue.recycle()
                    }
                }
            }
            val stepMs = (1_000L / safeFps).coerceAtLeast(16L)
            val quantizedMs = (timeMs.coerceAtLeast(0L) / stepMs) * stepMs
            cache.get(quantizedMs)?.let { if (!it.isRecycled) return it }

            val timeUs = quantizedMs * 1_000L
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, safeW, safeH)
                    ?: retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, safeW, safeH)
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { raw ->
                        val maxSide = maxOf(raw.width, raw.height).coerceAtLeast(1)
                        val maxTarget = maxOf(safeW, safeH).toFloat()
                        val s = minOf(1f, maxTarget / maxSide.toFloat())
                        val scaled = Bitmap.createScaledBitmap(
                            raw,
                            (raw.width * s).roundToInt().coerceAtLeast(64),
                            (raw.height * s).roundToInt().coerceAtLeast(64),
                            true,
                        )
                        if (scaled !== raw) raw.recycle()
                        scaled
                    }
            }
            if (bmp != null) cache.put(quantizedMs, bmp)
            return bmp
        }

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, safeW, safeH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (safeW * safeH * 6).coerceIn(3_000_000, 18_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, safeFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrack = -1
        var muxerStarted = false
        val composeBitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val composeCanvas = Canvas(composeBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val matrix = Matrix()
        var inputSurface: SurfaceInputEncoder? = null

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = SurfaceInputEncoder(encoder.createInputSurface(), safeW, safeH)
            encoder.start()
            inputSurface.makeCurrent()

            val totalFrames = ((totalDurationMs * safeFps) / 1000L).toInt().coerceIn(4, 3_600)
            val stepNs = (1_000_000_000L / safeFps).coerceAtLeast(8_333_333L)
            val info = MediaCodec.BufferInfo()

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

            val orderedTracks = spansByTrack.keys.sorted()
            val halfW = safeW * 0.5f
            val halfH = safeH * 0.5f
            val canvasRatio = safeW.toFloat() / safeH.toFloat()

            for (f in 0 until totalFrames) {
                if (isCancelled()) return Result.failure(IllegalStateException("Export cancelled"))
                val frac = if (totalFrames <= 1) 0f else f.toFloat() / totalFrames.toFloat()
                val tMs = (totalDurationMs * frac).toLong().coerceIn(0L, totalDurationMs)

                composeCanvas.drawColor(snapshot.canvasBackgroundArgb.toInt())

                orderedTracks.forEach { trackIdx ->
                    val trackSpans = spansByTrack[trackIdx] ?: return@forEach
                    val activeIdx = findActiveSpanIndex(trackSpans, tMs)
                    if (activeIdx < 0) return@forEach
                    val span = trackSpans[activeIdx]
                    val clip = span.clip
                    val localMs = (tMs - span.startMs).coerceIn(0L, (span.endMs - span.startMs).coerceAtLeast(1L))
                    val spanDur = (span.endMs - span.startMs).coerceAtLeast(1L)

                    val clipUri = clip.effectiveUriString()?.let { runCatching { Uri.parse(it) }.getOrNull() }
                        ?: sourceVideoUri
                    val sourceMs = clip.effectiveTrimStartMs() + timelineToSourceMs(clip, localMs, spanDur)
                    val frameBmp = runCatching { getFrameBitmap(clipUri, sourceMs) }.getOrNull() ?: return@forEach

                    val eval = KeyframeEvaluator.evaluate(
                        keyframes = clip.keyframes,
                        localTimeMs = localMs,
                        baseTransform = clip.transform,
                        baseOpacity = clip.opacity,
                        baseAdjust = clip.adjust,
                        baseFilterIntensity = snapshot.filterIntensity,
                    )
                    val alphaInt = (eval.opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
                    if (alphaInt <= 0) return@forEach

                    val bw = frameBmp.width.coerceAtLeast(1).toFloat()
                    val bh = frameBmp.height.coerceAtLeast(1).toFloat()
                    val bmpRatio = bw / bh
                    val baseFitScale = when (canvasFitMode) {
                        CanvasFitMode.FIT -> if (bmpRatio > canvasRatio) safeW / bw else safeH / bh
                        CanvasFitMode.FILL, CanvasFitMode.WIDE_CINEMA -> if (bmpRatio > canvasRatio) safeH / bh else safeW / bw
                    }

                    val userScale = (eval.zoomPercent / 100f).coerceIn(0.25f, 5f) * clip.crop.scaleCrop
                    val mirrorSign = if (clip.transform.mirrorHorizontal) -1f else 1f
                    val totalRotDeg = eval.rotateDeg + clip.crop.rotateDeg

                    matrix.reset()
                    // Center bitmap at origin
                    matrix.postTranslate(-bw * 0.5f, -bh * 0.5f)
                    // Fit/Fill canvas scale + user zoom + horizontal mirror
                    matrix.postScale(baseFitScale * userScale * mirrorSign, baseFitScale * userScale)
                    // Rotation
                    if (kotlin.math.abs(totalRotDeg) > 0.001f) {
                        matrix.postRotate(totalRotDeg)
                    }
                    // Translate to canvas center + Preview-matching posX/posY offset
                    matrix.postTranslate(
                        halfW + eval.posX * halfW,
                        halfH - eval.posY * halfH,
                    )

                    paint.alpha = alphaInt
                    composeCanvas.drawBitmap(frameBmp, matrix, paint)
                }

                inputSurface.drawBitmap(composeBitmap)
                inputSurface.setPresentationTime(f * stepNs)
                inputSurface.swapBuffers()
                drain(false)

                if (f % 3 == 0 || f == totalFrames - 1) {
                    onProgress((frac * 100f).roundToInt().coerceIn(0, 100))
                }
            }
            drain(true)
            return Result.success(
                CompositeResult(
                    outputUri = Uri.fromFile(outFile),
                    outputFile = outFile,
                    durationMs = totalDurationMs,
                    width = safeW,
                    height = safeH,
                )
            )
        } catch (e: Exception) {
            runCatching { outFile.delete() }
            return Result.failure(e)
        } finally {
            frameCaches.values.forEach { it.evictAll() }
            retrievers.values.forEach { runCatching { it.release() } }
            if (!composeBitmap.isRecycled) composeBitmap.recycle()
            runCatching { inputSurface?.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun timelineToSourceMs(
        clip: VideoClipSegment,
        localTimelineMs: Long,
        spanDurationMs: Long,
    ): Long {
        val baseDur = clip.baseDurationMs().coerceAtLeast(1L)
        if (clip.speed.mode == SpeedMode.NORMAL) {
            val spd = clip.speed.effectiveSpeedMultiplier().coerceIn(0.1f, 8f)
            return (localTimelineMs * spd).toLong().coerceIn(0L, (baseDur - 15L).coerceAtLeast(0L))
        }
        val tp = (localTimelineMs.toFloat() / spanDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val sp = clip.speed.sourceProgressAtTimelineProgress(tp)
        return (sp * baseDur).toLong().coerceIn(0L, (baseDur - 15L).coerceAtLeast(0L))
    }

    private class SurfaceInputEncoder(
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
            eglContext = EGL14.eglCreateContext(
                eglDisplay, cfgs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
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
            program = buildProgram()
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

        private fun buildProgram(): Int {
            val vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also {
                GLES20.glShaderSource(it, VERT_SRC)
                GLES20.glCompileShader(it)
            }
            val fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also {
                GLES20.glShaderSource(it, FRAG_SRC)
                GLES20.glCompileShader(it)
            }
            return GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vs)
                GLES20.glAttachShader(it, fs)
                GLES20.glLinkProgram(it)
            }
        }

        companion object {
            private const val VERT_SRC = """
                attribute vec4 aPosition;
                attribute vec2 aTextureCoord;
                varying vec2 vTextureCoord;
                void main() {
                    gl_Position = aPosition;
                    vTextureCoord = aTextureCoord;
                }
            """
            private const val FRAG_SRC = """
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform sampler2D sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
            """
        }
    }
}
