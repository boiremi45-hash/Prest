package com.example.domain.ffx

import com.example.data.model.FfxPreset
import com.example.data.model.PresetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FfxParserTest {

    @Test
    fun parseFromBytes_rifxBinary_extractsAdbeModulesAndInfersCategory() {
        val fakeRifxPayload = (
            "RIFX\u0000\u0000\u0001\u0020FFX " +
                "LIST\u0000\u0000\u0000\u0040ADBE Lumetri\u0000\u0000" +
                "ADBE Glow2\u0000\u0000ADBE Vignette\u0000\u0000"
            ).toByteArray(Charsets.ISO_8859_1)

        val parsed = FfxParser.parseFromBytes("Neon_Halation_Dream.ffx", fakeRifxPayload)

        assertEquals("Neon Halation Dream", parsed.name)
        assertEquals("Neon_Halation_Dream.ffx", parsed.fileName)
        assertEquals("imported_ffx", parsed.sourceType)
        assertTrue(parsed.effectModules.contains("ADBE Lumetri"))
        assertTrue(parsed.effectModules.contains("ADBE Glow2"))
        assertTrue(parsed.effectModules.contains("ADBE Vignette"))
        assertTrue(parsed.glowIntensity > 0.3)
        assertTrue(parsed.vignetteIntensity > 0.2)
    }

    @Test
    fun serializeAndReparse_roundTrip_preservesAllParameters() {
        val original = FfxPreset(
            name = "Blockbuster 2383",
            fileName = "blockbuster_2383.ffx",
            category = PresetCategory.FILM_RETRO.label,
            description = "Warm print grade",
            effectModules = listOf("ADBE Lumetri", "ADBE Add Grain"),
            tags = listOf("film", "2383"),
            exposure = 0.15,
            contrast = 0.32,
            saturation = -12.0,
            glowIntensity = 0.44,
            grainAmount = 0.52,
            speedMultiplier = 0.75,
            letterboxRatio = 0.14,
        )

        val descriptor = FfxParser.serializeToFfxDescriptor(original)
        val reparsed = FfxParser.parseFromBytes("blockbuster_2383.ffx", descriptor.toByteArray(Charsets.UTF_8))

        assertEquals(original.name, reparsed.name)
        assertEquals(original.category, reparsed.category)
        assertEquals(original.exposure, reparsed.exposure, 0.001)
        assertEquals(original.contrast, reparsed.contrast, 0.001)
        assertEquals(original.saturation, reparsed.saturation, 0.001)
        assertEquals(original.glowIntensity, reparsed.glowIntensity, 0.001)
        assertEquals(original.grainAmount, reparsed.grainAmount, 0.001)
        assertEquals(original.speedMultiplier, reparsed.speedMultiplier, 0.001)
        assertEquals(original.letterboxRatio, reparsed.letterboxRatio, 0.001)
    }
}
