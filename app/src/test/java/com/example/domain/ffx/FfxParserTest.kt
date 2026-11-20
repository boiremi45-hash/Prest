package com.example.domain.ffx

import com.example.data.model.FfxPreset
import com.example.data.model.PresetCategory
import com.example.domain.lut.CubeLutParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FfxParserTest {

    @Test
    fun parseFromBytes_rifxBinary_extractsAdbeModulesWithoutFakeColorShifts() {
        val fakeRifxPayload = (
            "RIFX\u0000\u0000\u0001\u0020FFX " +
                "LIST\u0000\u0000\u0000\u0040ADBE Transform\u0000\u0000" +
                "wiggle(6, 28)\u0000\u0000"
            ).toByteArray(StandardCharsets.ISO_8859_1)

        val parsed = FfxParser.parseFromBytes("Camera_Impact_Shake.ffx", fakeRifxPayload)

        assertEquals("Camera Impact Shake", parsed.name)
        assertEquals("FFX", parsed.presetFormat)
        assertTrue(parsed.effectModules.contains("ADBE Transform"))
        assertTrue("Expected shake intensity from wiggle(6, 28)", parsed.shakeIntensity > 0.5)
        // Non-CC shake preset must NOT invent fake contrast or saturation shifts
        assertEquals(0.0, parsed.contrast, 0.0001)
        assertEquals(0.0, parsed.saturation, 0.0001)
    }

    @Test
    fun parseFromBytes_rifxCdatChunks_extractsRealNumericValuesAndDesktopPlugins() {
        val body = ByteArrayOutputStream()
        fun writeChunk(tag: String, payload: ByteArray) {
            body.write(tag.toByteArray(StandardCharsets.ISO_8859_1))
            val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array()
            body.write(lenBuf)
            body.write(payload)
            if (payload.size % 2 != 0) body.write(0)
        }

        fun doubleBytes(v: Double): ByteArray =
            ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(v).array()

        writeChunk("Utf8", "ADBE CurvesCustom".toByteArray(StandardCharsets.UTF_8))
        writeChunk("Utf8", "ADBE Brightness & Contrast".toByteArray(StandardCharsets.UTF_8))
        writeChunk("Utf8", "Contrast".toByteArray(StandardCharsets.UTF_8))
        writeChunk("cdat", doubleBytes(48.0))
        writeChunk("Utf8", "ADBE Hue/Saturation".toByteArray(StandardCharsets.UTF_8))
        writeChunk("Utf8", "Master Saturation".toByteArray(StandardCharsets.UTF_8))
        writeChunk("cdat", doubleBytes(-35.0))
        writeChunk("Utf8", "S_Shake Looks3".toByteArray(StandardCharsets.UTF_8))

        val bodyBytes = body.toByteArray()
        val rifx = ByteArrayOutputStream()
        rifx.write("RIFX".toByteArray(StandardCharsets.ISO_8859_1))
        rifx.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(4 + bodyBytes.size).array())
        rifx.write("FFX ".toByteArray(StandardCharsets.ISO_8859_1))
        rifx.write(bodyBytes)

        val parsed = FfxParser.parseFromBytes("Dark_Grade.ffx", rifx.toByteArray())

        assertTrue(parsed.contrast > 0.3)
        assertEquals(-35.0, parsed.saturation, 0.01)
        assertTrue(parsed.lutSize >= 2)
        assertTrue(parsed.unsupportedModules.any { it.contains("Sapphire", ignoreCase = true) })
        assertTrue(parsed.unsupportedModules.any { it.contains("Magic Bullet Looks", ignoreCase = true) })
    }

    @Test
    fun parseFromBytes_cubeLutFile_parses3dLutArray() {
        val cubeText = buildString {
            appendLine("TITLE \"Teal Cinema\"")
            appendLine("LUT_3D_SIZE 2")
            // 2^3 = 8 triplets (R varies fastest or B fastest normalized 0..1)
            for (i in 0 until 8) {
                val r = if ((i and 1) != 0) "0.95" else "0.05"
                val g = if ((i and 2) != 0) "0.90" else "0.10"
                val b = if ((i and 4) != 0) "0.85" else "0.15"
                appendLine("$r $g $b")
            }
        }

        val parsed = FfxParser.parseFromBytes("Teal_Cinema.cube", cubeText.toByteArray(StandardCharsets.UTF_8))

        assertEquals("CUBE_LUT", parsed.presetFormat)
        assertEquals(2, parsed.lutSize)
        assertTrue(parsed.lutData.isNotBlank())
        val cube = CubeLutParser.decodeToCubeArray(parsed.lutSize, parsed.lutData)
        assertNotNull(cube)
        assertEquals(2, cube!!.size)
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
            blurRadius = 2.5,
            speedMultiplier = 0.75,
            letterboxRatio = 0.14,
        )

        val descriptor = FfxParser.serializeToFfxDescriptor(original)
        val reparsed = FfxParser.parseFromBytes("blockbuster_2383.ffx", descriptor.toByteArray(StandardCharsets.UTF_8))

        assertEquals(original.name, reparsed.name)
        assertEquals(original.category, reparsed.category)
        assertEquals(original.exposure, reparsed.exposure, 0.001)
        assertEquals(original.contrast, reparsed.contrast, 0.001)
        assertEquals(original.saturation, reparsed.saturation, 0.001)
        assertEquals(original.glowIntensity, reparsed.glowIntensity, 0.001)
        assertEquals(original.grainAmount, reparsed.grainAmount, 0.001)
        assertEquals(original.blurRadius, reparsed.blurRadius, 0.001)
        assertEquals(original.speedMultiplier, reparsed.speedMultiplier, 0.001)
        assertEquals(original.letterboxRatio, reparsed.letterboxRatio, 0.001)
    }
}
