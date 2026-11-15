package com.example.domain.ffx

import com.example.data.model.FfxPreset
import com.example.data.model.PresetCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.Locale

@Serializable
private data class PortableFfxPayload(
    val format: String = "PresetFX-AE-FFX-v1",
    val name: String,
    val category: String,
    val description: String = "",
    val ffxVersion: String = "AE 2024 (RIFX)",
    val effectModules: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val exposure: Double = 0.0,
    val contrast: Double = 0.0,
    val saturation: Double = 0.0,
    val hueShift: Double = 0.0,
    val temperature: Double = 0.0,
    val tint: Double = 0.0,
    val vignetteIntensity: Double = 0.0,
    val glowIntensity: Double = 0.0,
    val grainAmount: Double = 0.0,
    val zoomPulse: Double = 0.0,
    val shakeIntensity: Double = 0.0,
    val rotationDegrees: Double = 0.0,
    val speedMultiplier: Double = 1.0,
    val letterboxRatio: Double = 0.0,
)

object FfxParser {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val knownModulePatterns = listOf(
        "ADBE Lumetri" to "ADBE Lumetri",
        "ADBE Exposure2" to "ADBE Exposure2",
        "ADBE Brightness & Contrast" to "ADBE Brightness & Contrast 2",
        "ADBE Hue/Saturation" to "ADBE Hue/Saturation",
        "ADBE Color Balance" to "ADBE Color Balance (HLS)",
        "ADBE CurvesCustom" to "ADBE CurvesCustom",
        "ADBE Glow2" to "ADBE Glow2",
        "ADBE Glo2" to "ADBE Glow2",
        "ADBE Vignette" to "ADBE Vignette",
        "CC Vignette" to "CC Vignette",
        "ADBE Add Grain" to "ADBE Add Grain",
        "ADBE Match Grain" to "ADBE Match Grain",
        "ADBE Noise" to "ADBE Noise",
        "ADBE Gaussian Blur" to "ADBE Gaussian Blur 2",
        "ADBE Transform" to "ADBE Transform",
        "ADBE Motion Tile" to "ADBE Motion Tile",
        "ADBE Timewarp" to "ADBE Timewarp",
        "ADBE Posterize Time" to "ADBE Posterize Time",
        "ADBE Tint" to "ADBE Tint",
        "ADBE Tritone" to "ADBE Tritone",
        "ADBE Optics Compensation" to "ADBE Optics Compensation",
        "S_Shake" to "S_Shake (Sapphire)",
        "S_Glow" to "S_Glow (Sapphire)",
        "Deep Glow" to "Deep Glow",
        "Twixtor" to "Twixtor Pro",
        "BCC" to "Boris FX BCC",
    )

    fun parseFromBytes(fileName: String, bytes: ByteArray): FfxPreset {
        val cleanFileName = fileName.substringAfterLast('/').ifBlank { "imported_preset.ffx" }
        val baseName = cleanFileName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Imported FFX Preset" }

        val utf8Text = bytes.toString(StandardCharsets.UTF_8)

        // Check for portable PresetFX JSON payload first (for lossless round-trip .ffx files)
        val jsonStart = utf8Text.indexOf("{\"format\":\"PresetFX-AE-FFX-v1\"")
        val prettyJsonStart = if (jsonStart < 0) utf8Text.indexOf("\"PresetFX-AE-FFX-v1\"") else jsonStart
        if (jsonStart >= 0 || prettyJsonStart >= 0) {
            val startBrace = utf8Text.lastIndexOf('{', startIndex = maxOf(0, prettyJsonStart))
            val endBrace = utf8Text.lastIndexOf('}')
            if (startBrace >= 0 && endBrace > startBrace) {
                runCatching {
                    val decoded = json.decodeFromString<PortableFfxPayload>(
                        utf8Text.substring(startBrace, endBrace + 1)
                    )
                    return FfxPreset(
                        name = decoded.name,
                        fileName = cleanFileName,
                        category = decoded.category,
                        description = decoded.description,
                        sourceType = "imported_ffx",
                        ffxVersion = decoded.ffxVersion,
                        effectModules = decoded.effectModules,
                        tags = decoded.tags,
                        exposure = decoded.exposure,
                        contrast = decoded.contrast,
                        saturation = decoded.saturation,
                        hueShift = decoded.hueShift,
                        temperature = decoded.temperature,
                        tint = decoded.tint,
                        vignetteIntensity = decoded.vignetteIntensity,
                        glowIntensity = decoded.glowIntensity,
                        grainAmount = decoded.grainAmount,
                        zoomPulse = decoded.zoomPulse,
                        shakeIntensity = decoded.shakeIntensity,
                        rotationDegrees = decoded.rotationDegrees,
                        speedMultiplier = decoded.speedMultiplier,
                        letterboxRatio = decoded.letterboxRatio,
                        rawHeaderSnippet = "PresetFX Portable FFX (${decoded.effectModules.joinToString(", ")})".take(900),
                    ).clamped()
                }
            }
        }

        val isRifx = bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'X'.code.toByte()

        val asciiRuns = extractPrintableAsciiRuns(bytes)
        val combinedText = asciiRuns.joinToString(" ")
        val lowerCombined = (combinedText + " " + baseName).lowercase(Locale.US)

        val detectedModules = linkedSetOf<String>()
        for ((pattern, displayName) in knownModulePatterns) {
            if (combinedText.contains(pattern, ignoreCase = true)) {
                detectedModules.add(displayName)
            }
        }
        // Also collect any raw ADBE identifiers discovered in binary
        Regex("ADBE [A-Za-z0-9 &/()_\\-]{2,36}").findAll(combinedText).forEach { match ->
            if (detectedModules.size < 10) {
                detectedModules.add(match.value.trim())
            }
        }

        if (detectedModules.isEmpty()) {
            detectedModules.add("ADBE Transform")
            detectedModules.add("ADBE Lumetri")
        }

        // Heuristic mapping from detected After Effects modules & expressions to GPU parameters
        var exposure = 0.0
        var contrast = 0.14
        var saturation = 0.0
        var hueShift = 0.0
        var temperature = 0.0
        var tint = 0.0
        var vignette = 0.0
        var glow = 0.0
        var grain = 0.0
        var zoomPulse = 0.0
        var shake = 0.0
        var rotation = 0.0
        var speed = 1.0
        var letterbox = 0.0

        if (lowerCombined.contains("glow") || lowerCombined.contains("bloom") || lowerCombined.contains("halation") || lowerCombined.contains("flare")) {
            glow = 0.55
            exposure += 0.08
        }
        if (lowerCombined.contains("vignette")) {
            vignette = 0.40
        }
        if (lowerCombined.contains("grain") || lowerCombined.contains("noise") || lowerCombined.contains("film") || lowerCombined.contains("vhs") || lowerCombined.contains("retro")) {
            grain = 0.45
            contrast += 0.12
            vignette = maxOf(vignette, 0.28)
        }
        if (lowerCombined.contains("wiggle") || lowerCombined.contains("shake")) {
            shake = 0.48
        }
        if (lowerCombined.contains("zoom") || lowerCombined.contains("scale") || lowerCombined.contains("punch") || lowerCombined.contains("transform")) {
            zoomPulse = 0.42
        }
        if (lowerCombined.contains("twixtor") || lowerCombined.contains("timewarp") || lowerCombined.contains("slow")) {
            speed = 0.65
        } else if (lowerCombined.contains("posterize") || lowerCombined.contains("fast")) {
            speed = 1.25
        }
        if (lowerCombined.contains("lumetri") || lowerCombined.contains("curves") || lowerCombined.contains("contrast")) {
            contrast += 0.22
            saturation += 12.0
        }
        if (lowerCombined.contains("teal") || lowerCombined.contains("orange") || lowerCombined.contains("cinema")) {
            temperature = 0.18
            tint = -0.12
            letterbox = 0.12
            vignette = maxOf(vignette, 0.30)
        }
        if (lowerCombined.contains("neon") || lowerCombined.contains("cyber")) {
            saturation += 26.0
            temperature = -0.22
            tint = 0.28
            glow = maxOf(glow, 0.45)
        }
        if (lowerCombined.contains("warm") || lowerCombined.contains("golden") || lowerCombined.contains("sunset")) {
            temperature = 0.28
            exposure += 0.06
        }
        if (lowerCombined.contains("cool") || lowerCombined.contains("cold") || lowerCombined.contains("ice")) {
            temperature = -0.28
        }
        if (lowerCombined.contains("bw") || lowerCombined.contains("monochrome") || lowerCombined.contains("noir")) {
            saturation = -100.0
            contrast += 0.32
        }
        if (lowerCombined.contains("tilt") || lowerCombined.contains("dutch")) {
            rotation = 6.0
        }
        if (lowerCombined.contains("letterbox") || lowerCombined.contains("anamorphic") || lowerCombined.contains("2.39")) {
            letterbox = 0.14
        }

        val inferredCategory = when {
            shake > 0.2 || zoomPulse > 0.2 -> PresetCategory.MOTION_SHAKE.label
            glow > 0.3 -> PresetCategory.GLOW_OPTICS.label
            grain > 0.3 -> PresetCategory.FILM_RETRO.label
            speed != 1.0 || rotation != 0.0 -> PresetCategory.TRANSFORM.label
            else -> PresetCategory.COLOR_GRADE.label
        }

        val headerPrefix = if (isRifx) "RIFX/FFX Binary" else "FFX Descriptor"
        val rawSnippet = "$headerPrefix (${bytes.size} B) | Modules: ${detectedModules.joinToString(", ")}"

        return FfxPreset(
            name = baseName,
            fileName = cleanFileName,
            category = inferredCategory,
            description = "Imported from $cleanFileName (${detectedModules.size} After Effects effect modules detected).",
            sourceType = "imported_ffx",
            ffxVersion = if (isRifx) "AE Binary (RIFX)" else "AE Preset (.ffx)",
            effectModules = detectedModules.toList().take(12),
            tags = listOfNotNull(
                if (isRifx) "rifx" else "ffx",
                inferredCategory.lowercase(Locale.US).substringBefore(' '),
                if (glow > 0.2) "glow" else null,
                if (shake > 0.2) "shake" else null,
            ).distinct().take(10),
            exposure = exposure,
            contrast = contrast,
            saturation = saturation,
            hueShift = hueShift,
            temperature = temperature,
            tint = tint,
            vignetteIntensity = vignette,
            glowIntensity = glow,
            grainAmount = grain,
            zoomPulse = zoomPulse,
            shakeIntensity = shake,
            rotationDegrees = rotation,
            speedMultiplier = speed,
            letterboxRatio = letterbox,
            rawHeaderSnippet = rawSnippet.take(950),
        ).clamped()
    }

    fun serializeToFfxDescriptor(preset: FfxPreset): String {
        val payload = PortableFfxPayload(
            name = preset.name,
            category = preset.category,
            description = preset.description,
            ffxVersion = preset.ffxVersion,
            effectModules = preset.effectModules,
            tags = preset.tags,
            exposure = preset.exposure,
            contrast = preset.contrast,
            saturation = preset.saturation,
            hueShift = preset.hueShift,
            temperature = preset.temperature,
            tint = preset.tint,
            vignetteIntensity = preset.vignetteIntensity,
            glowIntensity = preset.glowIntensity,
            grainAmount = preset.grainAmount,
            zoomPulse = preset.zoomPulse,
            shakeIntensity = preset.shakeIntensity,
            rotationDegrees = preset.rotationDegrees,
            speedMultiplier = preset.speedMultiplier,
            letterboxRatio = preset.letterboxRatio,
        )
        return buildString {
            appendLine("; PresetFX After Effects Preset Descriptor")
            appendLine("; Modules: ${preset.effectModules.joinToString(", ")}")
            appendLine(json.encodeToString(payload))
        }
    }

    private fun extractPrintableAsciiRuns(bytes: ByteArray, minLen: Int = 4): List<String> {
        val maxScan = minOf(bytes.size, 512 * 1024)
        val runs = ArrayList<String>(128)
        val sb = StringBuilder()
        for (i in 0 until maxScan) {
            val c = bytes[i].toInt() and 0xFF
            if (c in 32..126) {
                sb.append(c.toChar())
            } else {
                if (sb.length >= minLen) {
                    runs.add(sb.toString())
                    if (runs.size >= 1200) break
                }
                sb.setLength(0)
            }
        }
        if (sb.length >= minLen && runs.size < 1200) {
            runs.add(sb.toString())
        }
        return runs
    }
}
