package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.model.PresetCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PresetRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: PresetRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PresetRepository(database.presetDao(), database.exportDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun seedStarterPackIfEmpty_seedsOnlyOnce() = runBlocking {
        val firstSeed = repository.seedStarterPackIfEmpty().getOrThrow()
        assertEquals(6, firstSeed)

        val secondSeed = repository.seedStarterPackIfEmpty().getOrThrow()
        assertEquals(0, secondSeed)

        val observed = repository.observePresets().first()
        assertEquals(6, observed.size)
    }

    @Test
    fun createUpdateFavoriteAndDelete_presetLifecycleWorks() = runBlocking {
        val preset = FfxPreset(
            name = "Sunset Halation",
            fileName = "sunset_halation.ffx",
            category = PresetCategory.GLOW_OPTICS.label,
            glowIntensity = 0.6,
            contrast = 0.25,
        )

        val id = repository.createPreset(preset).getOrThrow()
        assertTrue(id.isNotBlank())

        val created = repository.getPreset(id).getOrThrow()
        assertNotNull(created)
        assertEquals("Sunset Halation", created?.name)
        val originalCreatedAt = created!!.createdAtMillis
        assertTrue(originalCreatedAt > 0L)

        repository.toggleFavorite(id, true).getOrThrow()
        repository.updatePreset(created.copy(name = "Sunset Halation v2", isFavorite = true)).getOrThrow()

        val updated = repository.getPreset(id).getOrThrow()!!
        assertEquals("Sunset Halation v2", updated.name)
        assertTrue(updated.isFavorite)
        assertEquals(originalCreatedAt, updated.createdAtMillis)

        repository.deletePreset(id).getOrThrow()
        assertNull(repository.getPreset(id).getOrThrow())
    }

    @Test
    fun recordAndDeleteExport_updatesObservedFlow() = runBlocking {
        val record = ExportRecord(
            presetId = "starter_1",
            presetName = "Teal & Orange Blockbuster",
            outputFileName = "PresetFX_Clip.mp4",
            resolutionLabel = "1080p Full HD",
            codecLabel = "H.264 / AVC",
            bitrateMbps = 24L,
            fileSizeBytes = 12_500_000L,
            durationMs = 8_500L,
            localUriString = "content://media/external/downloads/101",
        )

        val exportId = repository.recordExport(record).getOrThrow()
        val list = repository.observeExports().first { it.isNotEmpty() }
        assertEquals(1, list.size)
        assertEquals(exportId, list.first().id)
        assertEquals(24L, list.first().bitrateMbps)

        repository.deleteExportRecord(exportId).getOrThrow()
        val afterDelete = repository.observeExports().first()
        assertTrue(afterDelete.isEmpty())
    }
}
