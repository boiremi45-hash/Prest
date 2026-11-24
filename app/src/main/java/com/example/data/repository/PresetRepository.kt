package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.local.ExportDao
import com.example.data.local.PresetDao
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.local.toEntity
import com.example.data.model.AspectPreset
import com.example.data.model.CanvasFitMode
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.model.LunaProjectSummary
import com.example.data.model.PersistedProjectState
import com.example.data.model.PresetCategory
import com.example.data.model.TimelineSnapshot
import com.example.data.model.computeTotalVideoDurationMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PresetRepository(
    private val presetDao: PresetDao,
    private val exportDao: ExportDao,
    private val projectDao: ProjectDao? = null,
    private val appContext: Context? = null,
) {
    constructor(presetDao: PresetDao, exportDao: ExportDao) : this(
        presetDao = presetDao,
        exportDao = exportDao,
        projectDao = null,
        appContext = null,
    )

    constructor(context: Context) : this(
        presetDao = AppDatabase.getInstance(context).presetDao(),
        exportDao = AppDatabase.getInstance(context).exportDao(),
        projectDao = AppDatabase.getInstance(context).projectDao(),
        appContext = context.applicationContext,
    )

    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun observePresets(): Flow<List<FfxPreset>> =
        presetDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeExports(): Flow<List<ExportRecord>> =
        exportDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeProjects(): Flow<List<LunaProjectSummary>> =
        projectDao?.observeAll()?.map { list -> list.map { it.toSummary() } } ?: flowOf(emptyList())

    suspend fun getMostRecentProjectState(): PersistedProjectState? {
        val dao = projectDao ?: return null
        val entity = runCatching { dao.getMostRecent() }.getOrNull() ?: return null
        return decodeProjectEntity(entity)
    }

    suspend fun loadProjectState(projectId: String): Result<PersistedProjectState?> {
        val dao = projectDao ?: return Result.success(null)
        return try {
            val entity = dao.getById(projectId) ?: return Result.success(null)
            Result.success(decodeProjectEntity(entity))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveProjectState(state: PersistedProjectState): Result<Unit> {
        val dao = projectDao ?: return Result.success(Unit)
        return try {
            val existing = dao.getById(state.projectId)
            val now = System.currentTimeMillis()
            val createdAt = existing?.createdAtMillis?.takeIf { it > 0L } ?: now
            val totalDur = computeTotalVideoDurationMs(state.snapshot.clips)
            val layerCount = (
                state.snapshot.clips.size +
                    state.snapshot.effectLayers.size +
                    state.snapshot.overlays.size +
                    state.snapshot.textTracks.size +
                    state.snapshot.audioTracks.size
                ).coerceAtLeast(1)
            val firstVideoName = state.snapshot.clips.firstOrNull { !it.sourceUriString.isNullOrBlank() }?.displayName
            val snapshotJson = jsonCodec.encodeToString(state.snapshot)
            val entity = ProjectEntity(
                id = state.projectId,
                title = state.title.trim().ifBlank { "Untitled Project" },
                aspectPresetName = state.aspectPreset.name,
                resolutionLabel = state.resolutionLabel,
                fps = state.fps,
                durationMs = totalDur,
                activePresetName = state.snapshot.activePreset.name,
                layerCount = layerCount,
                videoDisplayName = firstVideoName,
                thumbnailPath = state.thumbnailPath ?: existing?.thumbnailPath,
                playheadMs = state.playheadMs.coerceIn(0L, totalDur),
                canvasFitModeName = state.canvasFitMode.name,
                timelineZoom = state.timelineZoom.coerceIn(0.35f, 4.0f),
                snapshotJson = snapshotJson,
                createdAtMillis = createdAt,
                updatedAtMillis = now,
            )
            dao.upsert(entity)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        val dao = projectDao ?: return Result.success(Unit)
        return try {
            val existing = dao.getById(projectId)
            dao.deleteById(projectId)
            existing?.thumbnailPath?.let { path ->
                runCatching { File(path).takeIf { it.exists() }?.delete() }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeProjectEntity(entity: ProjectEntity): PersistedProjectState? {
        val snapshot = runCatching {
            jsonCodec.decodeFromString<TimelineSnapshot>(entity.snapshotJson)
        }.getOrNull() ?: return null
        val aspect = runCatching { AspectPreset.valueOf(entity.aspectPresetName) }.getOrDefault(AspectPreset.RATIO_9_16)
        val fitMode = runCatching { CanvasFitMode.valueOf(entity.canvasFitModeName) }.getOrDefault(CanvasFitMode.FIT)
        return PersistedProjectState(
            projectId = entity.id,
            title = entity.title,
            aspectPreset = aspect,
            resolutionLabel = entity.resolutionLabel,
            fps = entity.fps,
            canvasFitMode = fitMode,
            playheadMs = entity.playheadMs,
            timelineZoom = entity.timelineZoom,
            thumbnailPath = entity.thumbnailPath,
            snapshot = snapshot,
        )
    }

    suspend fun persistMediaLocally(
        context: Context,
        uri: Uri,
        subDir: String = "imported",
    ): Uri = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            val f = uri.path?.let { File(it) }
            if (f != null && f.exists()) return@withContext uri
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val mediaDir = File(context.filesDir, "luna_media/$subDir").apply { mkdirs() }
        val rawName = com.example.domain.video.VideoExportManager.queryDisplayName(context, uri)
            ?.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            ?: "media_${System.currentTimeMillis()}.mp4"
        val destFile = File(mediaDir, "${UUID.randomUUID().toString().take(8)}_$rawName")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.exists() && destFile.length() > 0L
        }.getOrDefault(false)
        if (copied) Uri.fromFile(destFile) else uri
    }

    suspend fun saveProjectThumbnail(
        context: Context,
        projectId: String,
        videoUri: Uri,
        positionMs: Long = 0L,
    ): String? = withContext(Dispatchers.IO) {
        val thumbDir = File(context.filesDir, "luna_thumbs").apply { mkdirs() }
        val thumbFile = File(thumbDir, "$projectId.jpg")
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val timeUs = (positionMs.coerceAtLeast(0L)) * 1_000L
            val frame: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    320,
                    240,
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { Bitmap.createScaledBitmap(it, 320, 240, true) }
            }
            if (frame != null) {
                FileOutputStream(thumbFile).use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                thumbFile.absolutePath
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun getPreset(presetId: String): Result<FfxPreset?> {
        return try {
            Result.success(presetDao.getById(presetId)?.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPreset(preset: FfxPreset): Result<String> {
        val clean = preset.clamped()
        val id = clean.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val createdAt = if (clean.createdAtMillis > 0L) clean.createdAtMillis else now
        return try {
            presetDao.upsert(clean.toEntity(resolvedId = id, createdAt = createdAt, updatedAt = now))
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePreset(preset: FfxPreset): Result<Unit> {
        require(preset.id.isNotBlank()) { "Preset ID must not be blank for update" }
        val clean = preset.clamped()
        val now = System.currentTimeMillis()
        return try {
            val existing = presetDao.getById(clean.id)
            val createdAt = existing?.createdAtMillis?.takeIf { it > 0L }
                ?: clean.createdAtMillis.takeIf { it > 0L }
                ?: now
            presetDao.upsert(clean.toEntity(resolvedId = clean.id, createdAt = createdAt, updatedAt = now))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(presetId: String, isFavorite: Boolean): Result<Unit> {
        return try {
            presetDao.updateFavorite(presetId, isFavorite, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun incrementUsage(presetId: String): Result<Unit> {
        return try {
            presetDao.incrementUsage(presetId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePreset(presetId: String): Result<Unit> {
        return try {
            presetDao.deleteById(presetId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun seedStarterPackIfEmpty(): Result<Int> {
        return try {
            if (presetDao.count() > 0) {
                return Result.success(0)
            }
            val now = System.currentTimeMillis()
            val starters = starterFfxPresets().mapIndexed { idx, preset ->
                val id = preset.id.ifBlank { UUID.randomUUID().toString() }
                preset.clamped().toEntity(
                    resolvedId = id,
                    createdAt = now - (idx * 1000L),
                    updatedAt = now - (idx * 1000L),
                )
            }
            presetDao.insertAll(starters)
            Result.success(starters.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordExport(record: ExportRecord): Result<String> {
        val id = record.id.ifBlank { UUID.randomUUID().toString() }
        val now = if (record.createdAtMillis > 0L) record.createdAtMillis else System.currentTimeMillis()
        val sanitized = record.copy(
            id = id,
            presetId = record.presetId.take(128).ifBlank { "luna_timeline" },
            presetName = record.presetName.take(80).ifBlank { "LUNA Render" },
            outputFileName = record.outputFileName.take(150).ifBlank { "LUNA_Export.mp4" },
            bitrateMbps = record.bitrateMbps.coerceIn(1L, 200L),
            fileSizeBytes = record.fileSizeBytes.coerceAtLeast(0L),
            durationMs = record.durationMs.coerceAtLeast(0L),
            localUriString = record.localUriString.take(500),
            createdAtMillis = now,
        )
        return try {
            exportDao.insert(sanitized.toEntity(resolvedId = id, createdAt = now))
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteExportRecord(exportId: String): Result<Unit> {
        return try {
            exportDao.deleteById(exportId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun starterFfxPresets(): List<FfxPreset> = listOf(
            FfxPreset(
                name = "Teal & Orange Blockbuster",
                fileName = "Teal_Orange_Blockbuster.ffx",
                category = PresetCategory.COLOR_GRADE.label,
                description = "High-contrast Hollywood split-tone with warm highlights, cyan shadows, subtle vignette, and 2.39:1 anamorphic letterbox.",
                sourceType = "studio",
                ffxVersion = "AE 2024 (RIFX)",
                effectModules = listOf("ADBE Lumetri", "ADBE CurvesCustom", "ADBE Vignette"),
                tags = listOf("cinema", "teal-orange", "letterbox"),
                isFavorite = true,
                exposure = 0.06,
                contrast = 0.34,
                saturation = 14.0,
                temperature = 0.22,
                tint = -0.14,
                vignetteIntensity = 0.38,
                glowIntensity = 0.12,
                letterboxRatio = 0.12,
                rawHeaderSnippet = "RIFX..FFX..ADBE Lumetri Color..ADBE CurvesCustom..ADBE Vignette",
            ),
            FfxPreset(
                name = "Anamorphic Halation Glow",
                fileName = "Anamorphic_Halation_Glow.ffx",
                category = PresetCategory.GLOW_OPTICS.label,
                description = "Dreamy optical highlight bloom and warm specular roll-off inspired by 35mm cinema glass.",
                sourceType = "studio",
                ffxVersion = "AE 2024 (RIFX)",
                effectModules = listOf("ADBE Glow2", "ADBE Exposure2", "ADBE Color Balance (HLS)"),
                tags = listOf("bloom", "halation", "dreamy"),
                exposure = 0.12,
                contrast = 0.18,
                saturation = 8.0,
                temperature = 0.14,
                glowIntensity = 0.68,
                vignetteIntensity = 0.24,
                grainAmount = 0.14,
                rawHeaderSnippet = "RIFX..FFX..ADBE Glow2..ADBE Exposure2..ADBE Color Balance (HLS)",
            ),
            FfxPreset(
                name = "Kinetic Punch Zoom",
                fileName = "Kinetic_Punch_Zoom.ffx",
                category = PresetCategory.MOTION_SHAKE.label,
                description = "Rhythmic keyframed scale push-in with subtle handheld camera wiggle and punchy contrast for music edits & reels.",
                sourceType = "studio",
                ffxVersion = "AE 2024 (RIFX)",
                effectModules = listOf("ADBE Transform", "ADBE Motion Tile", "ADBE Brightness & Contrast 2"),
                tags = listOf("amv", "reels", "zoom", "wiggle"),
                contrast = 0.26,
                saturation = 18.0,
                zoomPulse = 0.55,
                shakeIntensity = 0.35,
                speedMultiplier = 1.05,
                rawHeaderSnippet = "RIFX..FFX..ADBE Transform..ADBE Motion Tile..Wiggle(4,12)",
            ),
            FfxPreset(
                name = "Kodak 2383 Film Grain",
                fileName = "Kodak_2383_Print.ffx",
                category = PresetCategory.FILM_RETRO.label,
                description = "Organic 16mm grain texture with muted greens, rich shadow density, and warm vintage skin response.",
                sourceType = "studio",
                ffxVersion = "AE 2023 (RIFX)",
                effectModules = listOf("ADBE Add Grain", "ADBE Hue/Saturation", "ADBE Vignette"),
                tags = listOf("film", "16mm", "vintage"),
                exposure = -0.04,
                contrast = 0.28,
                saturation = -14.0,
                hueShift = -6.0,
                temperature = 0.18,
                tint = 0.08,
                vignetteIntensity = 0.45,
                grainAmount = 0.58,
                rawHeaderSnippet = "RIFX..FFX..ADBE Add Grain..ADBE Hue/Saturation..ADBE Vignette",
            ),
            FfxPreset(
                name = "Cyber Neon Split-Tone",
                fileName = "Cyber_Neon_Split.ffx",
                category = PresetCategory.GLOW_OPTICS.label,
                description = "Electric magenta-cyan night grade with strong specular bloom and crisp edge contrast for night street footage.",
                sourceType = "studio",
                ffxVersion = "AE 2024 (RIFX)",
                effectModules = listOf("ADBE Hue/Saturation", "ADBE Glow2", "ADBE Tint"),
                tags = listOf("cyberpunk", "neon", "night"),
                contrast = 0.42,
                saturation = 32.0,
                hueShift = -18.0,
                temperature = -0.28,
                tint = 0.34,
                glowIntensity = 0.52,
                vignetteIntensity = 0.30,
                rawHeaderSnippet = "RIFX..FFX..ADBE Hue/Saturation..ADBE Glow2..ADBE Tint",
            ),
            FfxPreset(
                name = "120fps Optical Slow-Mo",
                fileName = "Optical_SlowMo_Grade.ffx",
                category = PresetCategory.TRANSFORM.label,
                description = "Smooth 0.65x time-stretch preset paired with clean daylight exposure lift and subtle vignette.",
                sourceType = "studio",
                ffxVersion = "AE 2024 (RIFX)",
                effectModules = listOf("ADBE Timewarp", "ADBE Exposure2"),
                tags = listOf("slowmo", "speed", "clean"),
                exposure = 0.08,
                contrast = 0.15,
                saturation = 6.0,
                vignetteIntensity = 0.15,
                speedMultiplier = 0.65,
                rawHeaderSnippet = "RIFX..FFX..ADBE Timewarp..ADBE Exposure2",
            ),
        )
    }
}
