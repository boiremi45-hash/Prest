package com.example.domain.ffx

import com.example.data.model.FfxPreset
import com.example.data.model.PresetCategory
import com.example.domain.lut.CubeLutParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.abs

@Serializable
private data class PortableFfxPayload(
    val format: String = "LUNA-AE-FFX-v2",
    val name: String,
    val category: String,
    val description: String = "",
    val presetFormat: String = "FFX",
    val ffxVersion: String = "AE 2024 (RIFX)",
    val effectModules: List<String> = emptyList(),
    val unsupportedModules: List<String> = emptyList(),
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
    val blurRadius: Double = 0.0,
    val zoomPulse: Double = 0.0,
    val shakeIntensity: Double = 0.0,
    val rotationDegrees: Double = 0.0,
    val speedMultiplier: Double = 1.0,
    val letterboxRatio: Double = 0.0,
    val lutSize: Int = 0,
    val lutData: String = "",
)

object FfxParser {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val supportedModulePatterns = listOf(
        "ADBE Lumetri" to "ADBE Lumetri Color",
        "ADBE Exposure2" to "ADBE Exposure",
        "ADBE Brightness & Contrast" to "ADBE Brightness & Contrast",
        "ADBE Hue/Saturation" to "ADBE Hue/Saturation",
        "ADBE Color Balance" to "ADBE Color Balance (HLS)",
        "ADBE CurvesCustom" to "ADBE Curves",
        "ADBE Pro Levels2" to "ADBE Levels",
        "ADBE Easy Levels2" to "ADBE Levels",
        "ADBE Glow2" to "ADBE Glow",
        "ADBE Glo2" to "ADBE Glow",
        "ADBE Vignette" to "ADBE Vignette",
        "CC Vignette" to "CC Vignette",
        "ADBE Add Grain" to "ADBE Add Grain",
        "ADBE Match Grain" to "ADBE Match Grain",
        "ADBE Noise" to "ADBE Noise",
        "ADBE Gaussian Blur" to "ADBE Gaussian Blur",
        "ADBE Fast Blur" to "ADBE Fast Box Blur",
        "ADBE Transform" to "ADBE Transform",
        "ADBE Motion Tile" to "ADBE Motion Tile",
        "ADBE Timewarp" to "ADBE Timewarp",
        "ADBE Posterize Time" to "ADBE Posterize Time",
        "ADBE Tint" to "ADBE Tint",
        "ADBE Tritone" to "ADBE Tritone",
        "ADBE Sharpen" to "ADBE Sharpen",
    )

    private val desktopOnlyPluginPatterns = listOf(
        "Looks3" to "Magic Bullet Looks (Desktop AE Plugin)",
        "MB Looks" to "Magic Bullet Looks (Desktop AE Plugin)",
        "S_Shake" to "Boris FX Sapphire S_Shake",
        "S_Glow" to "Boris FX Sapphire S_Glow",
        "S_BlurMoCurves" to "Boris FX Sapphire BlurMoCurves",
        "S_Flicker" to "Boris FX Sapphire S_Flicker",
        "S_Warp" to "Boris FX Sapphire Warp",
        "Deep Glow" to "Deep Glow (3rd-Party AE Plugin)",
        "Twixtor" to "RE:Vision Twixtor (3rd-Party AE Plugin)",
        "RSMB" to "RE:Vision RSMB Motion Blur",
        "BCC" to "Boris FX Continuum (BCC)",
        "ADBE Turbulent Displace" to "AE Turbulent Displace",
        "ADBE Optics Compensation" to "AE Optics Compensation",
        "Optical Flares" to "Video Copilot Optical Flares",
        "VC Saber" to "Video Copilot Saber",
        "Twitch" to "Video Copilot Twitch",
    )

    private data class PropertyNumericObservation(
        val key: String,
        val values: List<Double>,
    )

    fun parseFromBytes(fileName: String, bytes: ByteArray): FfxPreset {
        val cleanFileName = fileName.substringAfterLast('/').ifBlank { "imported_preset.ffx" }
        val utf8Text = bytes.toString(StandardCharsets.UTF_8)

        // 1. Direct .cube 3D LUT file import
        if (CubeLutParser.looksLikeCubeFile(cleanFileName, utf8Text)) {
            return CubeLutParser.parseToPreset(cleanFileName, utf8Text)
        }

        // 2. Lossless LUNA / PresetFX portable descriptor round-trip
        val markerIdx = listOf(
            utf8Text.indexOf("\"LUNA-AE-FFX-v2\""),
            utf8Text.indexOf("\"PresetFX-AE-FFX-v1\""),
        ).firstOrNull { it >= 0 } ?: -1
        if (markerIdx >= 0) {
            val startBrace = utf8Text.lastIndexOf('{', startIndex = markerIdx)
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
                        presetFormat = decoded.presetFormat,
                        ffxVersion = decoded.ffxVersion,
                        effectModules = decoded.effectModules,
                        unsupportedModules = decoded.unsupportedModules,
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
                        blurRadius = decoded.blurRadius,
                        zoomPulse = decoded.zoomPulse,
                        shakeIntensity = decoded.shakeIntensity,
                        rotationDegrees = decoded.rotationDegrees,
                        speedMultiplier = decoded.speedMultiplier,
                        letterboxRatio = decoded.letterboxRatio,
                        lutSize = decoded.lutSize,
                        lutData = decoded.lutData,
                        rawHeaderSnippet = "LUNA Portable Preset (${decoded.effectModules.joinToString(", ")})".take(900),
                    ).clamped()
                }
            }
        }

        // 3. Binary After Effects RIFX / RIFF parser (extracts chunks, UTF-16/UTF-8 strings, IEEE-754 floats/doubles & expressions)
        val baseName = cleanFileName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Imported FFX Preset" }

        val isBigEndianRifx = bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'X'.code.toByte()

        val isLittleEndianRiff = bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()

        val extractedStrings = linkedSetOf<String>()
        val propertyNumbers = mutableListOf<PropertyNumericObservation>()
        val expressionTexts = mutableListOf<String>()

        if (isBigEndianRifx || isLittleEndianRiff) {
            val order = if (isBigEndianRifx) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
            walkRifxChunks(
                bytes = bytes,
                start = 12,
                end = bytes.size,
                order = order,
                depth = 0,
                lastPropertyLabel = arrayOf(""),
                extractedStrings = extractedStrings,
                propertyNumbers = propertyNumbers,
                expressionTexts = expressionTexts,
            )
        }

        // Supplement with ASCII + UTF-16BE + UTF-16LE scan across file bytes
        extractedStrings.addAll(extractPrintableAsciiRuns(bytes))
        extractedStrings.addAll(extractUtf16Strings(bytes, bigEndian = true))
        extractedStrings.addAll(extractUtf16Strings(bytes, bigEndian = false))

        val combinedText = extractedStrings.joinToString(" ")
        val lowerCombined = (combinedText + " " + baseName).lowercase(Locale.US)

        val detectedSupported = linkedSetOf<String>()
        for ((pattern, displayName) in supportedModulePatterns) {
            if (combinedText.contains(pattern, ignoreCase = true)) {
                detectedSupported.add(displayName)
            }
        }
        Regex("ADBE [A-Za-z0-9 &/()_\\-]{2,36}").findAll(combinedText).forEach { match ->
            val raw = match.value.trim()
            if (!raw.startsWith("ADBE Effect") && detectedSupported.size < 14) {
                detectedSupported.add(raw)
            }
        }

        val detectedUnsupported = linkedSetOf<String>()
        for ((pattern, displayName) in desktopOnlyPluginPatterns) {
            if (combinedText.contains(pattern, ignoreCase = true)) {
                detectedUnsupported.add(displayName)
            }
        }

        // Start cleanly at NEUTRAL 0.0 (no fake contrast/saturation shifts!)
        var exposure = 0.0
        var contrast = 0.0
        var saturation = 0.0
        var hueShift = 0.0
        var temperature = 0.0
        var tint = 0.0
        var vignette = 0.0
        var glow = 0.0
        var grain = 0.0
        var blurRadius = 0.0
        var zoomPulse = 0.0
        var shake = 0.0
        var rotation = 0.0
        var speed = 1.0
        var letterbox = 0.0

        // Read real numeric values extracted from AE RIFX `cdat` / `ldat` chunks!
        for (obs in propertyNumbers) {
            val k = obs.key.lowercase(Locale.US)
            val v = obs.values.firstOrNull() ?: continue
            when {
                k.contains("exposure") -> {
                    exposure = v.coerceIn(-1.0, 1.0)
                }
                k == "contrast" || k.contains("contrast") -> {
                    // AE Brightness & Contrast & Lumetri use -100..100 or -150..150
                    contrast = (if (abs(v) > 1.5) v / 120.0 else v).coerceIn(-0.85, 0.85)
                }
                k == "brightness" || k.contains("brightness") -> {
                    exposure += (if (abs(v) > 1.5) v / 180.0 else v).coerceIn(-0.6, 0.6)
                }
                k.contains("master saturation") || k == "saturation" -> {
                    // AE Hue/Sat uses -100..100; Lumetri Saturation uses 0..200 (100 = neutral)
                    saturation = if (k.contains("lumetri") || (v in 0.0..200.0 && lowerCombined.contains("lumetri"))) {
                        (v - 100.0).coerceIn(-100.0, 100.0)
                    } else {
                        v.coerceIn(-100.0, 100.0)
                    }
                }
                k.contains("master hue") || k == "hue" -> {
                    hueShift = v.coerceIn(-180.0, 180.0)
                }
                k.contains("master lightness") || k == "lightness" -> {
                    exposure += (v / 120.0).coerceIn(-0.6, 0.6)
                }
                k.contains("temperature") || k.contains("warmth") -> {
                    temperature = (if (abs(v) > 2.0) v / 100.0 else v).coerceIn(-1.0, 1.0)
                }
                k == "tint" || k.contains("tint") -> {
                    tint = (if (abs(v) > 2.0) v / 100.0 else v).coerceIn(-1.0, 1.0)
                }
                k.contains("blurriness") || k.contains("blur") -> {
                    blurRadius = (v / 8.0).coerceIn(0.0, 20.0)
                }
                k.contains("glow intensity") || k.contains("glow radius") -> {
                    glow = maxOf(glow, (v / 10.0).coerceIn(0.15, 0.95))
                }
                k.contains("vignette") -> {
                    vignette = maxOf(vignette, (abs(v) / 5.0).coerceIn(0.15, 0.9))
                }
                k.contains("grain") || k.contains("noise") -> {
                    grain = maxOf(grain, (if (v > 1.5) v / 100.0 else v).coerceIn(0.1, 0.9))
                }
                k.contains("rotation") || k == "angle" -> {
                    if (abs(v) <= 180.0) rotation = v
                }
                k == "speed" || k.contains("speed") -> {
                    if (v in 10.0..400.0) speed = (v / 100.0).coerceIn(0.25, 4.0)
                    else if (v in 0.25..4.0) speed = v
                }
            }
        }

        // Parse real AE expressions like wiggle(freq, amp)
        val exprJoined = (expressionTexts.joinToString(" ") + " " + combinedText)
        val wiggleMatch = Regex("wiggle\\s*\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*,\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
            .find(exprJoined)
        if (wiggleMatch != null) {
            val amp = wiggleMatch.groupValues.getOrNull(2)?.toDoubleOrNull() ?: 12.0
            shake = (amp / 45.0).coerceIn(0.18, 0.95)
        } else if (lowerCombined.contains("wiggle") || lowerCombined.contains("shake")) {
            shake = 0.45
        }

        // Module/keyword fallbacks ONLY for effects genuinely named in detected modules/expressions/filename
        if (glow == 0.0 && (detectedSupported.contains("ADBE Glow") || lowerCombined.contains("glow") || lowerCombined.contains("halation"))) {
            glow = 0.50
        }
        if (vignette == 0.0 && (detectedSupported.contains("ADBE Vignette") || detectedSupported.contains("CC Vignette") || lowerCombined.contains("vignette"))) {
            vignette = 0.42
        }
        if (grain == 0.0 && (detectedSupported.contains("ADBE Add Grain") || detectedSupported.contains("ADBE Noise") || lowerCombined.contains("grain") || lowerCombined.contains("16mm"))) {
            grain = 0.42
        }
        if (blurRadius == 0.0 && (detectedSupported.contains("ADBE Gaussian Blur") || detectedSupported.contains("ADBE Fast Box Blur"))) {
            blurRadius = 3.5
        }
        if (lowerCombined.contains("zoom") || lowerCombined.contains("punch")) {
            zoomPulse = 0.42
        }
        if (detectedSupported.contains("ADBE Timewarp") && abs(speed - 1.0) < 0.02) {
            speed = 0.65
        } else if (detectedSupported.contains("ADBE Posterize Time") && abs(speed - 1.0) < 0.02) {
            speed = 1.25
        }
        if (lowerCombined.contains("letterbox") || lowerCombined.contains("2.39")) {
            letterbox = 0.14
        }
        if (lowerCombined.contains("bw") || lowerCombined.contains("monochrome") || lowerCombined.contains("noir")) {
            saturation = -100.0
        }
        if (lowerCombined.contains("teal") && lowerCombined.contains("orange")) {
            temperature = if (temperature == 0.0) 0.22 else temperature
            tint = if (tint == 0.0) -0.14 else tint
        }

        // Build a synthetic 3D Curves LUT when Curves / Levels / Dark CC is detected
        var synthesizedLutSize = 0
        var synthesizedLutData = ""
        val hasCurvesOrLevels = detectedSupported.any {
            it.contains("Curves", ignoreCase = true) ||
                it.contains("Levels", ignoreCase = true) ||
                it.contains("Lumetri", ignoreCase = true) ||
                it.contains("Color Balance", ignoreCase = true) ||
                it.contains("Brightness & Contrast", ignoreCase = true)
        }
        if (hasCurvesOrLevels && (abs(contrast) > 0.05 || lowerCombined.contains("dark") || lowerCombined.contains("cc"))) {
            val darkPreset = lowerCombined.contains("dark") || lowerCombined.contains("moody") || lowerCombined.contains("shadow")
            val cube = CubeLutParser.buildToneCurveCube(
                shadowCrush = if (darkPreset) 0.55f else 0.25f,
                highlightLift = if (darkPreset) -0.15f else 0.2f,
                gamma = if (darkPreset) 0.88f else 1.05f,
                warmSplit = temperature.toFloat().coerceIn(-0.6f, 0.6f),
            )
            // Encode synthesized 17^3 curve LUT cleanly
            val n = cube.size
            val lutBytes = ByteArray(n * n * n * 3)
            var p = 0
            for (r in 0 until n) {
                for (g in 0 until n) {
                    for (b in 0 until n) {
                        val argb = cube[r][g][b]
                        lutBytes[p++] = ((argb shr 16) and 0xFF).toByte()
                        lutBytes[p++] = ((argb shr 8) and 0xFF).toByte()
                        lutBytes[p++] = (argb and 0xFF).toByte()
                    }
                }
            }
            synthesizedLutSize = n
            synthesizedLutData = android.util.Base64.encodeToString(lutBytes, android.util.Base64.NO_WRAP)
            if (!detectedSupported.contains("Tone Curve 3D LUT")) {
                detectedSupported.add("Tone Curve 3D LUT")
            }
        }

        val inferredCategory = when {
            shake > 0.15 || zoomPulse > 0.15 -> PresetCategory.MOTION_SHAKE.label
            glow > 0.25 || blurRadius > 0.5 -> PresetCategory.GLOW_OPTICS.label
            grain > 0.25 -> PresetCategory.FILM_RETRO.label
            abs(speed - 1.0) > 0.05 || abs(rotation) > 1.0 -> PresetCategory.TRANSFORM.label
            else -> PresetCategory.COLOR_GRADE.label
        }

        val modulesSummary = buildList {
            if (detectedSupported.isNotEmpty()) add("Applied: ${detectedSupported.joinToString(", ")}")
            if (detectedUnsupported.isNotEmpty()) add("Desktop-only plugins: ${detectedUnsupported.joinToString(", ")}")
        }.joinToString(" • ").ifBlank { "No standard AE modules decoded from binary" }

        val headerPrefix = when {
            isBigEndianRifx -> "RIFX/FFX Binary"
            isLittleEndianRiff -> "RIFF/FFX Binary"
            else -> "FFX Descriptor"
        }

        return FfxPreset(
            name = baseName,
            fileName = cleanFileName,
            category = inferredCategory,
            description = "$modulesSummary.",
            sourceType = "imported_ffx",
            presetFormat = "FFX",
            ffxVersion = if (isBigEndianRifx || isLittleEndianRiff) "AE Binary (RIFX)" else "AE Preset (.ffx)",
            effectModules = detectedSupported.toList().take(16),
            unsupportedModules = detectedUnsupported.toList().take(16),
            tags = listOfNotNull(
                if (isBigEndianRifx || isLittleEndianRiff) "rifx" else "ffx",
                inferredCategory.lowercase(Locale.US).substringBefore(' '),
                if (synthesizedLutSize > 0) "curves-lut" else null,
                if (shake > 0.15) "shake" else null,
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
            blurRadius = blurRadius,
            zoomPulse = zoomPulse,
            shakeIntensity = shake,
            rotationDegrees = rotation,
            speedMultiplier = speed,
            letterboxRatio = letterbox,
            lutSize = synthesizedLutSize,
            lutData = synthesizedLutData,
            rawHeaderSnippet = "$headerPrefix (${bytes.size} B) | Props: ${propertyNumbers.size} | $modulesSummary".take(950),
        ).clamped()
    }

    fun serializeToFfxDescriptor(preset: FfxPreset): String {
        val payload = PortableFfxPayload(
            name = preset.name,
            category = preset.category,
            description = preset.description,
            presetFormat = preset.presetFormat,
            ffxVersion = preset.ffxVersion,
            effectModules = preset.effectModules,
            unsupportedModules = preset.unsupportedModules,
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
            blurRadius = preset.blurRadius,
            zoomPulse = preset.zoomPulse,
            shakeIntensity = preset.shakeIntensity,
            rotationDegrees = preset.rotationDegrees,
            speedMultiplier = preset.speedMultiplier,
            letterboxRatio = preset.letterboxRatio,
            lutSize = preset.lutSize,
            lutData = preset.lutData,
        )
        return buildString {
            appendLine("; LUNA After Effects Preset Descriptor")
            appendLine("; Modules: ${preset.effectModules.joinToString(", ")}")
            appendLine(json.encodeToString(payload))
        }
    }

    private fun walkRifxChunks(
        bytes: ByteArray,
        start: Int,
        end: Int,
        order: ByteOrder,
        depth: Int,
        lastPropertyLabel: Array<String>,
        extractedStrings: LinkedHashSet<String>,
        propertyNumbers: MutableList<PropertyNumericObservation>,
        expressionTexts: MutableList<String>,
    ) {
        if (depth > 8 || start + 8 > end) return
        var offset = start
        val buf = ByteBuffer.wrap(bytes).order(order)

        while (offset + 8 <= end) {
            val chunkId = String(bytes, offset, 4, StandardCharsets.ISO_8859_1)
            val chunkSizeLong = buf.getInt(offset + 4).toLong() and 0xFFFFFFFFL
            val dataStart = offset + 8
            val dataEnd = (dataStart + chunkSizeLong).coerceAtMost(end.toLong()).toInt()
            if (chunkSizeLong < 0 || dataStart > end) break

            when (chunkId) {
                "LIST" -> {
                    if (dataStart + 4 <= dataEnd) {
                        val listType = String(bytes, dataStart, 4, StandardCharsets.ISO_8859_1)
                        extractedStrings.add(listType)
                        walkRifxChunks(
                            bytes = bytes,
                            start = dataStart + 4,
                            end = dataEnd,
                            order = order,
                            depth = depth + 1,
                            lastPropertyLabel = lastPropertyLabel,
                            extractedStrings = extractedStrings,
                            propertyNumbers = propertyNumbers,
                            expressionTexts = expressionTexts,
                        )
                    }
                }
                "Utf8", "fnam", "tdmn", "tdb4" -> {
                    val str = bytes.copyOfRange(dataStart, dataEnd)
                        .toString(StandardCharsets.UTF_8)
                        .trim('\u0000', ' ', '\r', '\n')
                    if (str.length >= 2) {
                        extractedStrings.add(str)
                        if (chunkId == "Utf8" || chunkId == "tdmn") {
                            lastPropertyLabel[0] = str
                        }
                    }
                }
                "expr" -> {
                    val expr = bytes.copyOfRange(dataStart, dataEnd)
                        .toString(StandardCharsets.UTF_8)
                        .trim('\u0000', ' ')
                    if (expr.isNotEmpty()) {
                        expressionTexts.add(expr)
                        extractedStrings.add(expr)
                    }
                }
                "cdat" -> {
                    val label = lastPropertyLabel[0]
                    val len = dataEnd - dataStart
                    if (label.isNotBlank() && len >= 8) {
                        val doubles = mutableListOf<Double>()
                        var pos = dataStart
                        while (pos + 8 <= dataEnd && doubles.size < 4) {
                            val d = buf.getDouble(pos)
                            if (d.isFinite() && abs(d) < 10_000.0) {
                                doubles.add(d)
                            }
                            pos += 8
                        }
                        if (doubles.isNotEmpty()) {
                            propertyNumbers.add(PropertyNumericObservation(label, doubles))
                        }
                    }
                }
            }

            // RIFX / RIFF chunks are padded to even byte boundaries
            val paddedSize = chunkSizeLong + (chunkSizeLong and 1L)
            val nextOffset = dataStart.toLong() + paddedSize
            if (nextOffset <= offset || nextOffset > end) break
            offset = nextOffset.toInt()
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
                    if (runs.size >= 1500) break
                }
                sb.setLength(0)
            }
        }
        if (sb.length >= minLen && runs.size < 1500) {
            runs.add(sb.toString())
        }
        return runs
    }

    private fun extractUtf16Strings(bytes: ByteArray, bigEndian: Boolean, minLen: Int = 4): List<String> {
        val maxScan = minOf(bytes.size, 256 * 1024)
        val runs = ArrayList<String>(64)
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < maxScan) {
            val hi = if (bigEndian) bytes[i].toInt() and 0xFF else bytes[i + 1].toInt() and 0xFF
            val lo = if (bigEndian) bytes[i + 1].toInt() and 0xFF else bytes[i].toInt() and 0xFF
            if (hi == 0 && lo in 32..126) {
                sb.append(lo.toChar())
            } else {
                if (sb.length >= minLen) {
                    runs.add(sb.toString())
                    if (runs.size >= 400) break
                }
                sb.setLength(0)
            }
            i += 2
        }
        if (sb.length >= minLen && runs.size < 400) {
            runs.add(sb.toString())
        }
        return runs
    }
}
