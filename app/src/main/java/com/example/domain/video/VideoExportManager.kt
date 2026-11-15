package com.example.domain.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@UnstableApi
class VideoExportManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null

    fun startExport(
        sourceUri: Uri,
        preset: FfxPreset,
        config: ExportConfig,
        onProgress: (Int) -> Unit,
        onSuccess: (ExportRecord, savedLocationLabel: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        cancelExport()

        val safeBase = config.fileName
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .trim('_')
            .ifBlank { "PresetFX_Render" }
        val timestampTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val finalFileName = "${safeBase}_$timestampTag.mp4"

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempFile = File(exportDir, finalFileName)
        if (tempFile.exists()) tempFile.delete()

        val effects = VideoEffectsEngine.buildExportEffects(
            preset = preset,
            targetHeight = config.resolution.targetHeight,
        )

        val itemBuilder = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
            .setRemoveAudio(config.removeAudio)
            .setEffects(effects)

        val speed = preset.speedMultiplier.toFloat().coerceIn(0.25f, 4.0f)
        if (kotlin.math.abs(speed - 1.0f) > 0.02f) {
            itemBuilder.setSpeed(object : SpeedProvider {
                override fun getSpeed(presentationTimeUs: Long): Float = speed
                override fun getNextSpeedChangeTimeUs(inputTimeUs: Long): Long = Long.MAX_VALUE
            })
        }

        val editedMediaItem = itemBuilder.build()

        val videoEncoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(config.bitrateMbps.coerceIn(2, 120) * 1_000_000)
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
                        preset = preset,
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
        transformer.start(editedMediaItem, tempFile.absolutePath)

        val progressHolder = ProgressHolder()
        val poller = object : Runnable {
            override fun run() {
                val current = activeTransformer ?: return
                val state = current.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress.coerceIn(0, 99))
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 160L)
                }
            }
        }
        progressRunnable = poller
        mainHandler.postDelayed(poller, 160L)
    }

    fun cancelExport() {
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
                            "${Environment.DIRECTORY_DOWNLOADS}/PresetFX"
                        )
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val itemUri = resolver.insert(collection, values)
                        ?: throw IllegalStateException("Unable to create file in Downloads/PresetFX.")

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
                    itemUri to "Downloads/PresetFX/$finalFileName"
                } else {
                    val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                        ?: context.filesDir
                    val destFile = File(moviesDir, finalFileName)
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                    Uri.fromFile(destFile) to destFile.absolutePath
                }

                val record = ExportRecord(
                    presetId = preset.id.ifBlank { "studio_working" },
                    presetName = preset.name,
                    outputFileName = finalFileName,
                    resolutionLabel = config.resolution.label,
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
                VideoMetadata(
                    uri = uri,
                    displayName = displayName,
                    width = width,
                    height = height,
                    durationMs = durationMs,
                )
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
