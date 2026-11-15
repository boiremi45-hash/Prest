package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.model.PresetCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PresetRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createPreset_validPayload_createsDocumentAndReturnsId() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PresetRepository(firestore, auth)

        val preset = FfxPreset(
            name = "Test Cinema LUT",
            fileName = "test_cinema_${UUID.randomUUID().toString().take(6)}.ffx",
            category = PresetCategory.COLOR_GRADE.label,
            description = "Unit test preset",
            sourceType = "custom",
            effectModules = listOf("ADBE Lumetri"),
            tags = listOf("test"),
            contrast = 0.25,
            saturation = 10.0,
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createPreset(preset) }
        assertTrue("Create should succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val id = createResult.getOrThrow()
        assertTrue(id.isNotEmpty())

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getPreset(uid, id) }.getOrThrow()
        assertNotNull(fetched)
        assertEquals("Test Cinema LUT", fetched?.name)
    }

    @Test
    fun observePresets_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PresetRepository(firestore, auth)

        val preset = FfxPreset(
            name = "Observed Glow",
            fileName = "observed_glow.ffx",
            category = PresetCategory.GLOW_OPTICS.label,
            glowIntensity = 0.65,
        )
        val id = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createPreset(preset).getOrThrow() }

        val presets = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePresets(uid).first { list -> list.any { it.id == id } }
        }
        assertTrue(presets.any { it.id == id && it.name == "Observed Glow" })
    }

    @Test
    fun updatePreset_authenticatedOwner_updatesFieldsAndPreservesCreatedAt() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PresetRepository(firestore, auth)

        val preset = FfxPreset(
            name = "Mutable Preset",
            fileName = "mutable.ffx",
            category = PresetCategory.MOTION_SHAKE.label,
            zoomPulse = 0.3,
        )
        val id = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createPreset(preset).getOrThrow() }
        val loaded = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getPreset(uid, id).getOrThrow() }!!

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updatePreset(loaded.copy(name = "Updated Motion FX", isFavorite = true, zoomPulse = 0.7))
        }
        assertTrue("Update should succeed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)

        val reloaded = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getPreset(uid, id).getOrThrow() }!!
        assertEquals("Updated Motion FX", reloaded.name)
        assertTrue(reloaded.isFavorite)
        assertEquals(0.7, reloaded.zoomPulse, 0.001)
    }

    @Test
    fun getPreset_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = PresetRepository(firestore, auth)
        val presetId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPreset(
                FfxPreset(name = "Alice Private FFX", fileName = "alice_private.ffx")
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = PresetRepository(firestore, auth)
        val bobRead = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getPreset(aliceUid, presetId) }
        assertTrue(bobRead.isFailure)
        val firestoreException = bobRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun observePresets_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = PresetRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) { repository.observePresets("alice_uid_placeholder").first() }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull("Expected PERMISSION_DENIED exception", caught)
        val firestoreException = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun recordExport_validPayload_createsDocumentAndObserveEmits() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PresetRepository(firestore, auth)

        val exportRecord = ExportRecord(
            presetId = "preset_${UUID.randomUUID().toString().take(6)}",
            presetName = "Teal & Orange Blockbuster",
            outputFileName = "PresetFX_${UUID.randomUUID().toString().take(6)}.mp4",
            resolutionLabel = "1080p Full HD",
            codecLabel = "H.264 / AVC",
            bitrateMbps = 24L,
            fileSizeBytes = 15_400_000L,
            durationMs = 10_200L,
            localUriString = "content://media/external/downloads/42",
        )
        val expResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.recordExport(exportRecord) }
        assertTrue("Export record should succeed: ${expResult.exceptionOrNull()}", expResult.isSuccess)
        val expId = expResult.getOrThrow()

        val exports = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeExports(uid).first { list -> list.any { it.id == expId } }
        }
        assertTrue(exports.any { it.id == expId && it.bitrateMbps == 24L })
    }

    private companion object {
        const val ALICE_EMAIL = "alice_vfx@test.com"
        const val BOB_EMAIL = "bob_vfx@test.com"
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val FLOW_TIMEOUT_MS = 10_000L
    }
}
