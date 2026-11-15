package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.model.PresetCategory
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PresetRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(db.app),
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePresets(userId: String): Flow<List<FfxPreset>> = flow {
        val path = "users/$userId/presets"
        emitAll(
            db.collection("users").document(userId).collection("presets")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FfxPreset::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeExports(userId: String): Flow<List<ExportRecord>> = flow {
        val path = "users/$userId/exports"
        emitAll(
            db.collection("users").document(userId).collection("exports")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ExportRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getPreset(userId: String, presetId: String): Result<FfxPreset?> {
        val docRef = db.collection("users").document(userId).collection("presets").document(presetId)
        return try {
            val snap = docRef.get().await()
            val preset = snap.toObject(
                FfxPreset::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(preset)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createPreset(preset: FfxPreset): Result<String> {
        val uid = requireUserId()
        val clean = preset.clamped()
        val docRef = db.collection("users").document(uid).collection("presets").document()
        val payload = buildCreatePayload(uid, clean)
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePreset(preset: FfxPreset): Result<Unit> {
        val uid = requireUserId()
        require(preset.id.isNotBlank()) { "Preset ID must not be blank for update" }
        val clean = preset.clamped()
        val docRef = db.collection("users").document(uid).collection("presets").document(preset.id)
        val payload = mapOf(
            "name" to clean.name,
            "fileName" to clean.fileName,
            "category" to clean.category,
            "description" to clean.description,
            "ffxVersion" to clean.ffxVersion,
            "effectModules" to clean.effectModules,
            "tags" to clean.tags,
            "isFavorite" to clean.isFavorite,
            "exposure" to clean.exposure,
            "contrast" to clean.contrast,
            "saturation" to clean.saturation,
            "hueShift" to clean.hueShift,
            "temperature" to clean.temperature,
            "tint" to clean.tint,
            "vignetteIntensity" to clean.vignetteIntensity,
            "glowIntensity" to clean.glowIntensity,
            "grainAmount" to clean.grainAmount,
            "zoomPulse" to clean.zoomPulse,
            "shakeIntensity" to clean.shakeIntensity,
            "rotationDegrees" to clean.rotationDegrees,
            "speedMultiplier" to clean.speedMultiplier,
            "letterboxRatio" to clean.letterboxRatio,
            "rawHeaderSnippet" to clean.rawHeaderSnippet,
            "usageCount" to clean.usageCount,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(presetId: String, isFavorite: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("presets").document(presetId)
        return try {
            docRef.update(
                mapOf(
                    "isFavorite" to isFavorite,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun incrementUsage(presetId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("presets").document(presetId)
        return try {
            docRef.update(
                mapOf(
                    "usageCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePreset(presetId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("presets").document(presetId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun seedStarterPackIfEmpty(): Result<Int> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("presets")
        return try {
            val existing = colRef.limit(1).get().await()
            if (!existing.isEmpty) {
                return Result.success(0)
            }
            val batch = db.batch()
            val starters = starterFfxPresets()
            starters.forEach { preset ->
                val docRef = colRef.document()
                batch.set(docRef, buildCreatePayload(uid, preset.clamped()))
            }
            batch.commit().await()
            Result.success(starters.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordExport(record: ExportRecord): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("exports").document()
        val payload = mapOf(
            "userId" to uid,
            "presetId" to record.presetId.take(128).ifBlank { "custom" },
            "presetName" to record.presetName.take(80).ifBlank { "PresetFX Render" },
            "outputFileName" to record.outputFileName.take(150).ifBlank { "PresetFX_Export.mp4" },
            "resolutionLabel" to record.resolutionLabel.take(40),
            "codecLabel" to record.codecLabel.take(40),
            "bitrateMbps" to record.bitrateMbps.coerceIn(1L, 200L),
            "fileSizeBytes" to record.fileSizeBytes.coerceAtLeast(0L),
            "durationMs" to record.durationMs.coerceAtLeast(0L),
            "localUriString" to record.localUriString.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteExportRecord(exportId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("exports").document(exportId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    private fun buildCreatePayload(uid: String, clean: FfxPreset): Map<String, Any> {
        return mapOf(
            "userId" to uid,
            "name" to clean.name,
            "fileName" to clean.fileName,
            "category" to clean.category,
            "description" to clean.description,
            "sourceType" to clean.sourceType,
            "ffxVersion" to clean.ffxVersion,
            "effectModules" to clean.effectModules,
            "tags" to clean.tags,
            "isFavorite" to clean.isFavorite,
            "exposure" to clean.exposure,
            "contrast" to clean.contrast,
            "saturation" to clean.saturation,
            "hueShift" to clean.hueShift,
            "temperature" to clean.temperature,
            "tint" to clean.tint,
            "vignetteIntensity" to clean.vignetteIntensity,
            "glowIntensity" to clean.glowIntensity,
            "grainAmount" to clean.grainAmount,
            "zoomPulse" to clean.zoomPulse,
            "shakeIntensity" to clean.shakeIntensity,
            "rotationDegrees" to clean.rotationDegrees,
            "speedMultiplier" to clean.speedMultiplier,
            "letterboxRatio" to clean.letterboxRatio,
            "rawHeaderSnippet" to clean.rawHeaderSnippet,
            "usageCount" to clean.usageCount,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
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
