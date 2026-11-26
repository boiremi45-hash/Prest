package com.example.domain.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.data.model.AdjustSettings
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.model.OverlayTrackItem
import com.example.data.model.SpeedMode
import com.example.data.model.TextTrackItem
import com.example.data.model.TransitionSpec
import com.example.data.model.VideoClipSegment
import com.example.data.model.VideoEffectLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@UnstableApi
class VideoExportManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null
    @Volatile
    private var exportCancelled = false

    fun startCapCutExport(
        sourceVideoUri: Uri,
        snapshot: com.example.data.model.TimelineSnapshot,
        aspectPreset: com.example.data.model.AspectPreset = com.example.data.model.AspectPreset.RATIO_9_16,
        canvasFitMode: com.example.data.model.CanvasFitMode = com.example.data.model.CanvasFitMode.FIT,
        config: ExportConfig,
        onProgress: (Int) -> Unit,
        onSuccess: (ExportRecord, savedLocationLabel: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        cancelExport()
        exportCancelled = false

        val visibleClips = snapshot.clips.filter { !snapshot.isVideoTrackHidden(it.trackIndex) }
        val activeVisibleClips = visibleClips.ifEmpty { snapshot.clips }
        val timesliceTargets = activeVisibleClips.filter { it.timeslice.enabled && it.timeslice.intensity > 0.01f }
        val distinctTracks = activeVisibleClips
            .filter { !it.effectiveUriString().isNullOrBlank() || it.id.isNotBlank() }
            .map { it.trackIndex }
            .distinct()
            .sorted()
        val needsMultiTrackComposite = distinctTracks.size > 1

        if (timesliceTargets.isEmpty() && !needsMultiTrackComposite) {
            launchTransformerComposition(
                sourceVideoUri = sourceVideoUri,
                snapshot = snapshot,
                compositedVideoClip = null,
                audioSourceClips = activeVisibleClips,
                config = config,
                progressMin = 0,
                onProgress = onProgress,
                onSuccess = onSuccess,
                onError = onError,
            )
            return
        }

        Thread {
            try {
                var currentProgressMax = 0
                val totalTs = timesliceTargets.size
                var tsDone = 0
                val updatedClips = snapshot.clips.map { clip ->
                    if (exportCancelled) throw IllegalStateException("Export cancelled")
                    if (snapshot.isVideoTrackHidden(clip.trackIndex) || !clip.timeslice.enabled || clip.timeslice.intensity <= 0.01f) {
                        clip
                    } else {
                        val baseUri = clip.effectiveUriString()?.let { runCatching { Uri.parse(it) }.getOrNull() }
                            ?: sourceVideoUri
                        val effStart = clip.effectiveTrimStartMs()
                        val effEnd = clip.effectiveTrimEndMs()
                        val tsRes = TimesliceProcessor.renderTimesliceClipMp4Blocking(
                            context = context,
                            sourceUri = baseUri,
                            clipId = clip.id,
                            trimStartMs = effStart,
                            trimEndMs = effEnd,
                            targetWidth = clip.width,
                            targetHeight = config.resolution.targetHeight ?: clip.height.takeIf { it > 0 } ?: 1080,
                            targetFps = config.frameRate,
                            settings = clip.timeslice,
                            isCancelled = { exportCancelled },
                            onProgress = { clipPct ->
                                val tsWeight = if (needsMultiTrackComposite) 0.25f else 0.35f
                                val overall = ((tsDone * 100f + clipPct) / totalTs.coerceAtLeast(1).toFloat())
                                val mapped = (overall * tsWeight).toInt().coerceIn(0, (tsWeight * 100).toInt())
                                mainHandler.post { if (!exportCancelled) onProgress(mapped) }
                            },
                        ).getOrThrow()
                        tsDone++
                        clip.copy(
                            sourceUriString = tsRes.outputUri.toString(),
                            reversedUriString = null,
                            isReversed = false,
                            trimStartMs = 0L,
                            trimEndMs = tsRes.durationMs,
                            sourceDurationMs = tsRes.durationMs,
                            timeslice = clip.timeslice.copy(enabled = false),
                        )
                    }
                }
                if (exportCancelled) return@Thread

                val updatedSnapshot = snapshot.copy(clips = updatedClips)
                val updatedVisible = updatedClips.filter { !updatedSnapshot.isVideoTrackHidden(it.trackIndex) }
                val updatedDistinctTracks = updatedVisible.map { it.trackIndex }.distinct().sorted()

                var compositedClip: VideoClipSegment? = null
                if (updatedDistinctTracks.size > 1) {
                    val basePct = if (totalTs > 0) 25 else 0
                    val mtHeight = config.resolution.targetHeight ?: updatedVisible.firstOrNull()?.height?.takeIf { it > 0 } ?: 1080
                    val compRes = MultiTrackExportRenderer.compositeVideoTracksBlocking(
                        context = context,
                        sourceVideoUri = sourceVideoUri,
                        snapshot = updatedSnapshot,
                        aspectPreset = aspectPreset,
                        canvasFitMode = canvasFitMode,
                        targetHeight = mtHeight,
                        targetFps = config.frameRate,
                        isCancelled = { exportCancelled },
                        onProgress = { mtPct ->
                            val mapped = basePct + ((mtPct / 100f) * (65 - basePct)).toInt()
                            mainHandler.post { if (!exportCancelled) onProgress(mapped.coerceIn(basePct, 65)) }
                        },
                    ).getOrThrow()
                    currentProgressMax = 65
                    compositedClip = VideoClipSegment(
                        id = "mt_composite",
                        sourceUriString = compRes.outputUri.toString(),
                        displayName = "Multi-Track Composite",
                        trackIndex = 0,
                        width = compRes.width,
                        height = compRes.height,
                        timelineStartMs = 0L,
                        trimStartMs = 0L,
                        trimEndMs = compRes.durationMs,
                        sourceDurationMs = compRes.durationMs,
                        isMuted = true, // Video track audio is mixed separately per original track
                    )
                } else {
                    currentProgressMax = if (totalTs > 0) 35 else 0
                }

                if (exportCancelled) return@Thread
                mainHandler.post {
                    if (exportCancelled) return@post
                    launchTransformerComposition(
                        sourceVideoUri = sourceVideoUri,
                        snapshot = updatedSnapshot,
                        compositedVideoClip = compositedClip,
                        audioSourceClips = updatedVisible,
                        config = config,
                        progressMin = currentProgressMax,
                        onProgress = onProgress,
                        onSuccess = onSuccess,
                        onError = onError,
                    )
                }
            } catch (e: Exception) {
                if (!exportCancelled) {
                    mainHandler.post {
                        onError(e.localizedMessage ?: "Multi-track export rendering failed.")
                    }
                }
            }
        }.start()
    }

    private fun launchTransformerComposition(
        sourceVideoUri: Uri,
        snapshot: com.example.data.model.TimelineSnapshot,
        compositedVideoClip: VideoClipSegment?,
        audioSourceClips: List<VideoClipSegment>,
        config: ExportConfig,
        progressMin: Int,
        onProgress: (Int) -> Unit,
        onSuccess: (ExportRecord, savedLocationLabel: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val safeBase = config.fileName
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .trim('_')
            .ifBlank { "LUNA_Render" }
        val timestampTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val finalFileName = "${safeBase}_$timestampTag.mp4"

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempFile = File(exportDir, finalFileName)
        if (tempFile.exists()) tempFile.delete()

        val activePreset = snapshot.activePreset
        val filterIntensity = snapshot.filterIntensity
        val visibleTexts = if (snapshot.isTextTrackHidden()) emptyList() else snapshot.textTracks
        val effectLayers = snapshot.effectLayers
        val overlays = snapshot.overlays
        val transitions = snapshot.transitions

        val videoClipsForSeq: List<VideoClipSegment> = if (compositedVideoClip != null) {
            listOf(compositedVideoClip)
        } else {
            val singleTrackIdx = audioSourceClips.firstOrNull()?.trackIndex ?: 0
            audioSourceClips.filter { it.trackIndex == singleTrackIdx }.sortedBy { it.timelineStartMs }
        }
        val spans = VideoEffectsEngine.computeClipSpans(videoClipsForSeq)

        // Check if primary video sequence itself carries audio or if all audio comes from separate audio sequences
        val includePrimaryVideoAudio = compositedVideoClip == null && !config.removeAudio
        val primaryTrackTypes = if (includePrimaryVideoAudio) {
            setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)
        } else {
            setOf(C.TRACK_TYPE_VIDEO)
        }
        val seqBuilder = EditedMediaItemSequence.Builder(primaryTrackTypes)
        var cursorMs = 0L
        spans.forEach { span ->
            val clip = span.clip
            if (span.startMs > cursorMs + 40L) {
                seqBuilder.addGap((span.startMs - cursorMs) * 1_000L)
                cursorMs = span.startMs
            }
            val clipUri = clip.effectiveUriString()?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?: sourceVideoUri
            val mediaBuilder = MediaItem.Builder().setUri(clipUri)

            val trimStart = clip.effectiveTrimStartMs()
            val trimEnd = clip.effectiveTrimEndMs()
            val effSrcDur = clip.effectiveSourceDurationMs()
            if (trimStart > 30L || trimEnd < effSrcDur - 30L) {
                mediaBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(trimStart)
                        .setEndPositionMs(trimEnd)
                        .build()
                )
            }

            val effects = VideoEffectsEngine.buildExportEffectsForClip(
                context = context,
                clip = clip,
                clipStartInTimelineMs = span.startMs,
                allClips = videoClipsForSeq,
                transitions = transitions,
                effectLayers = effectLayers,
                overlays = overlays,
                texts = visibleTexts,
                adjust = clip.adjust,
                filterPreset = activePreset,
                filterIntensity = filterIntensity,
                config = config,
            )

            val trackMuted = snapshot.isVideoTrackMuted(clip.trackIndex) || snapshot.isVideoTrackHidden(clip.trackIndex)
            val muteAudio = !includePrimaryVideoAudio || trackMuted || clip.isMuted ||
                (clip.volumePercent <= 0 && clip.volumeKeyframes.isEmpty())
            val itemBuilder = EditedMediaItem.Builder(mediaBuilder.build())
                .setRemoveAudio(muteAudio)
                .setEffects(effects)

            if (clip.isFrozen) {
                itemBuilder.setDurationUs(clip.freezeDurationMs * 1_000L)
            }

            val baseDurUs = clip.baseDurationMs() * 1_000L
            val normalSpd = clip.speed.normalSpeed
            if (clip.speed.mode == SpeedMode.NORMAL && abs(normalSpd - 1.0f) > 0.02f) {
                val clamped = normalSpd.coerceIn(0.1f, 20f)
                itemBuilder.setSpeed(object : SpeedProvider {
                    override fun getSpeed(presentationTimeUs: Long): Float = clamped
                    override fun getNextSpeedChangeTimeUs(inputTimeUs: Long): Long = C.TIME_UNSET
                })
            } else if (clip.speed.mode != SpeedMode.NORMAL) {
                val segmentCount = 24
                val segUs = (baseDurUs / segmentCount).coerceAtLeast(16_667L)
                itemBuilder.setSpeed(object : SpeedProvider {
                    override fun getSpeed(presentationTimeUs: Long): Float {
                        val prog = (presentationTimeUs.toFloat() / baseDurUs.toFloat()).coerceIn(0f, 1f)
                        return clip.speed.speedAtProgress(prog).coerceIn(0.1f, 16f)
                    }

                    override fun getNextSpeedChangeTimeUs(inputTimeUs: Long): Long {
                        val nextIdx = (inputTimeUs / segUs) + 1L
                        val nextBoundary = nextIdx * segUs
                        return if (nextBoundary < baseDurUs) nextBoundary else C.TIME_UNSET
                    }
                })
            }
            seqBuilder.addItem(itemBuilder.build())
            cursorMs = maxOf(cursorMs, span.endMs)
        }

        val sequences = mutableListOf(seqBuilder.build())

        // Mix all unmuted video-track audio (when composited multi-track video is used) + A1/A2 AudioTrackItems
        if (!config.removeAudio) {
            if (compositedVideoClip != null) {
                val clipAudioTracks = audioSourceClips
                    .filter { clip ->
                        !snapshot.isVideoTrackHidden(clip.trackIndex) &&
                            !snapshot.isVideoTrackMuted(clip.trackIndex) &&
                            !clip.isMuted &&
                            !clip.isFrozen &&
                            (clip.volumePercent > 0 || clip.volumeKeyframes.isNotEmpty())
                    }
                    .groupBy { it.trackIndex }

                clipAudioTracks.values.forEach { clipsOnTrack ->
                    val audioSeqBuilder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                    var audioCursor = 0L
                    var hasAdded = false
                    clipsOnTrack.sortedBy { it.timelineStartMs }.forEach { clip ->
                        val start = clip.timelineStartMs.coerceAtLeast(audioCursor)
                        if (start > audioCursor + 30L) {
                            audioSeqBuilder.addGap((start - audioCursor) * 1_000L)
                        }
                        val u = clip.effectiveUriString()?.let { runCatching { Uri.parse(it) }.getOrNull() }
                            ?: sourceVideoUri
                        val mb = MediaItem.Builder().setUri(u)
                        val tStart = clip.effectiveTrimStartMs()
                        val tEnd = clip.effectiveTrimEndMs()
                        if (tStart > 30L || tEnd < clip.effectiveSourceDurationMs() - 30L) {
                            mb.setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(tStart)
                                    .setEndPositionMs(tEnd)
                                    .build()
                            )
                        }
                        val procs = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
                        if (clip.volumeKeyframes.isNotEmpty() || clip.volumePercent != 100) {
                            procs.add(KeyframeVolumeAudioProcessor(clip.volumeKeyframes, clip.volumePercent))
                        }
                        val item = EditedMediaItem.Builder(mb.build())
                            .setRemoveVideo(true)
                            .setEffects(androidx.media3.transformer.Effects(procs, emptyList()))
                            .build()
                        audioSeqBuilder.addItem(item)
                        audioCursor = start + clip.timelineDurationMs()
                        hasAdded = true
                    }
                    if (hasAdded) sequences.add(audioSeqBuilder.build())
                }
            }

            // A1 (Music) & A2 (Sound / Voiceover)
            snapshot.audioTracks
                .filter { au ->
                    !au.uriString.isNullOrBlank() &&
                        !snapshot.isAudioTrackHidden(au.trackIndex) &&
                        !snapshot.isAudioTrackMuted(au.trackIndex) &&
                        (au.volumePercent > 0 || au.volumeKeyframes.isNotEmpty()) &&
                        au.endMs > au.startMs + 100L
                }
                .forEach { au ->
                    val uri = runCatching { Uri.parse(au.uriString) }.getOrNull() ?: return@forEach
                    val auSeqBuilder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                    if (au.startMs > 30L) {
                        auSeqBuilder.addGap(au.startMs * 1_000L)
                    }
                    val clipStart = au.sourceStartMs.coerceAtLeast(0L)
                    val clipEnd = clipStart + (au.endMs - au.startMs).coerceAtLeast(200L)
                    val mb = MediaItem.Builder().setUri(uri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(clipStart)
                                .setEndPositionMs(clipEnd)
                                .build()
                        )
                    val procs = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
                    if (au.volumeKeyframes.isNotEmpty() || au.volumePercent != 100) {
                        procs.add(KeyframeVolumeAudioProcessor(au.volumeKeyframes, au.volumePercent))
                    }
                    val editedAu = EditedMediaItem.Builder(mb.build())
                        .setRemoveVideo(true)
                        .setEffects(androidx.media3.transformer.Effects(procs, emptyList()))
                        .build()
                    auSeqBuilder.addItem(editedAu)
                    sequences.add(auSeqBuilder.build())
                }
        }

        val composition = Composition.Builder(sequences).build()

        val videoEncoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(config.bitrateMbps.coerceIn(4, 120) * 1_000_000)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(videoEncoderSettings)
            .setEnableFallback(true)
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(config.codec.mimeType)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgressPolling()
                    activeTransformer = null
                    persistToLocalDrive(
                        tempFile = tempFile,
                        finalFileName = finalFileName,
                        preset = activePreset,
                        config = config,
                        exportResult = exportResult,
                        onSuccess = onSuccess,
                        onError = onError,
                    )
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    stopProgressPolling()
                    activeTransformer = null
                    tempFile.delete()
                    onError(exportException.localizedMessage ?: "Hardware encoder export failed.")
                }
            })
            .build()

        activeTransformer = transformer
        transformer.start(composition, tempFile.absolutePath)

        val progressHolder = ProgressHolder()
        val poller = object : Runnable {
            override fun run() {
                val current = activeTransformer ?: return
                val state = current.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val mapped = progressMin + ((progressHolder.progress / 99f) * (99 - progressMin)).toInt()
                    onProgress(mapped.coerceIn(progressMin, 99))
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 140L)
                }
            }
        }
        progressRunnable = poller
        mainHandler.postDelayed(poller, 140L)
    }

    fun cancelExport() {
        exportCancelled = true
        stopProgressPolling()
        activeTransformer?.cancel()
        activeTransformer = null
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun persistToLocalDrive(
        tempFile: File,
        finalFileName: String,
        preset: FfxPreset,
        config: ExportConfig,
        exportResult: ExportResult,
        onSuccess: (ExportRecord, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        Thread {
            try {
                val sizeBytes = tempFile.length()
                val durationMs = exportResult.approximateDurationMs.coerceAtLeast(0L)
                val (localUri, locationLabel) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                        put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            "${Environment.DIRECTORY_DOWNLOADS}/LUNA"
                        )
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val itemUri = resolver.insert(collection, values)
                        ?: throw IllegalStateException("Unable to create file in Downloads/LUNA.")

                    resolver.openOutputStream(itemUri)?.use { out ->
                        FileInputStream(tempFile).use { input ->
                            input.copyTo(out)
                        }
                    } ?: throw IllegalStateException("Unable to write MP4 bytes to Downloads.")

                    val completeValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, completeValues, null, null)
                    tempFile.delete()
                    itemUri to "Downloads/LUNA/$finalFileName"
                } else {
                    val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                        ?: context.filesDir
                    val destFile = File(moviesDir, finalFileName)
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                    Uri.fromFile(destFile) to destFile.absolutePath
                }

                val record = ExportRecord(
                    presetId = preset.id.ifBlank { "luna_capcut" },
                    presetName = "${preset.name} • ${config.frameRate}fps",
                    outputFileName = finalFileName,
                    resolutionLabel = "${config.resolution.shortLabel} ${config.frameRate}fps",
                    codecLabel = config.codec.label,
                    bitrateMbps = config.bitrateMbps.toLong(),
                    fileSizeBytes = sizeBytes,
                    durationMs = durationMs,
                    localUriString = localUri.toString(),
                )
                mainHandler.post { onSuccess(record, locationLabel) }
            } catch (e: Exception) {
                mainHandler.post {
                    onError("Rendered video could not be saved locally: ${e.localizedMessage}")
                }
            }
        }.start()
    }

    companion object {
        suspend fun readVideoMetadata(context: Context, uri: Uri): VideoMetadata =
            withContext(Dispatchers.IO) {
                val displayName = queryDisplayName(context, uri) ?: "Selected_Clip.mp4"
                val retriever = MediaMetadataRetriever()
                var width = 1920
                var height = 1080
                var durationMs = 0L
                try {
                    retriever.setDataSource(context, uri)
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 1920
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 1080
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                } finally {
                    runCatching { retriever.release() }
                }
                if (durationMs <= 0L) {
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(context, uri, null)
                        for (i in 0 until extractor.trackCount) {
                            val fmt = extractor.getTrackFormat(i)
                            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                            if (mime.startsWith("video/") && fmt.containsKey(MediaFormat.KEY_DURATION)) {
                                val us = fmt.getLong(MediaFormat.KEY_DURATION)
                                if (us > 0L) durationMs = maxOf(durationMs, us / 1_000L)
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        runCatching { extractor.release() }
                    }
                }
                VideoMetadata(
                    uri = uri,
                    displayName = displayName,
                    width = width,
                    height = height,
                    durationMs = durationMs,
                )
            }

        suspend fun extractFilmstripThumbnails(
            context: Context,
            uri: Uri,
            durationMs: Long,
            trimStartMs: Long = 0L,
            trimEndMs: Long = durationMs,
            isReversed: Boolean = false,
            count: Int = ((trimEndMs - trimStartMs).coerceAtLeast(durationMs.coerceAtLeast(1_000L)) / 1_000L).toInt().coerceIn(3, 28),
        ): List<ImageBitmap> = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val results = mutableListOf<ImageBitmap>()
            try {
                retriever.setDataSource(context, uri)
                val safeStartMs = trimStartMs.coerceAtLeast(0L)
                val safeEndMs = trimEndMs.coerceAtLeast(safeStartMs + 250L)
                val spanUs = (safeEndMs - safeStartMs).coerceAtLeast(250L) * 1_000L
                val safeCount = count.coerceIn(1, 32)
                for (i in 0 until safeCount) {
                    val frac = if (safeCount <= 1) 0.5f else i.toFloat() / safeCount.toFloat()
                    val effectiveFrac = if (isReversed) (1f - frac) else frac
                    val timeUs = safeStartMs * 1_000L + (spanUs * effectiveFrac).toLong()
                    val frame: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            120,
                            90,
                        )
                    } else {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?.let { Bitmap.createScaledBitmap(it, 120, 90, true) }
                    }
                    if (frame != null) {
                        results.add(frame.asImageBitmap())
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }
            results
        }

        fun queryDisplayName(context: Context, uri: Uri): String? {
            return runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) cursor.getString(idx) else null
                        } else {
                            null
                        }
                    }
            }.getOrNull() ?: uri.lastPathSegment
        }

        suspend fun copyUriToDestination(
            context: Context,
            sourceUri: Uri,
            destUri: Uri,
        ): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    } ?: error("Could not open destination output stream")
                } ?: error("Could not open source video stream")
                Unit
            }
        }
    }
}
