package com.example.domain.lut

import android.graphics.Color
import android.util.Base64
import com.example.data.model.FfxPreset
import com.example.data.model.PresetCategory
import kotlin.math.pow
import kotlin.math.roundToInt

object CubeLutParser {

    data class ParsedCubeLut(
        val title: String,
        val size: Int,
        val encodedRgbBase64: String,
    )

    fun looksLikeCubeFile(fileName: String, text: String): Boolean {
        if (fileName.endsWith(".cube", ignoreCase = true)) return true
        return text.contains("LUT_3D_SIZE") || text.contains("LUT_1D_SIZE")
    }

    fun parseToPreset(fileName: String, text: String): FfxPreset {
        val parsed = parseCubeText(fileName, text)
        val cleanFileName = fileName.substringAfterLast('/').ifBlank { "grade.cube" }
        val displayTitle = parsed.title.ifBlank {
            cleanFileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
                .ifBlank { "Imported 3D LUT" }
        }
        return FfxPreset(
            name = displayTitle,
            fileName = cleanFileName,
            category = PresetCategory.COLOR_GRADE.label,
            description = "Exact ${parsed.size}x${parsed.size}x${parsed.size} 3D LUT color grade (.cube) applied via OpenGL texture lookup.",
            sourceType = "imported_cube",
            presetFormat = "CUBE_LUT",
            ffxVersion = "3D LUT (${parsed.size}³)",
            effectModules = listOf("3D LUT (${parsed.size}x${parsed.size}x${parsed.size})"),
            unsupportedModules = emptyList(),
            tags = listOf("cube", "lut", "color-grade"),
            lutSize = parsed.size,
            lutData = parsed.encodedRgbBase64,
            rawHeaderSnippet = "CUBE 3D LUT • Size ${parsed.size}³ • ${parsed.title}",
        ).clamped()
    }

    fun parseCubeText(fileName: String, text: String): ParsedCubeLut {
        var title = fileName.substringAfterLast('/').substringBeforeLast('.')
        var size3d = 0
        var size1d = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        val entries = ArrayList<FloatArray>(4096)

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach

            when {
                line.startsWith("TITLE", ignoreCase = true) -> {
                    val extracted = line.substringAfter(' ').trim().trim('"')
                    if (extracted.isNotEmpty()) title = extracted
                }
                line.startsWith("LUT_3D_SIZE", ignoreCase = true) -> {
                    size3d = line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: 0
                }
                line.startsWith("LUT_1D_SIZE", ignoreCase = true) -> {
                    size1d = line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: 0
                }
                line.startsWith("DOMAIN_MIN", ignoreCase = true) -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        domainMin = floatArrayOf(
                            parts[1].toFloatOrNull() ?: 0f,
                            parts[2].toFloatOrNull() ?: 0f,
                            parts[3].toFloatOrNull() ?: 0f,
                        )
                    }
                }
                line.startsWith("DOMAIN_MAX", ignoreCase = true) -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        domainMax = floatArrayOf(
                            parts[1].toFloatOrNull() ?: 1f,
                            parts[2].toFloatOrNull() ?: 1f,
                            parts[3].toFloatOrNull() ?: 1f,
                        )
                    }
                }
                else -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val r = parts[0].toFloatOrNull()
                        val g = parts[1].toFloatOrNull()
                        val b = parts[2].toFloatOrNull()
                        if (r != null && g != null && b != null) {
                            entries.add(floatArrayOf(r, g, b))
                        }
                    }
                }
            }
        }

        val spanR = (domainMax[0] - domainMin[0]).takeIf { kotlin.math.abs(it) > 1e-5f } ?: 1f
        val spanG = (domainMax[1] - domainMin[1]).takeIf { kotlin.math.abs(it) > 1e-5f } ?: 1f
        val spanB = (domainMax[2] - domainMin[2]).takeIf { kotlin.math.abs(it) > 1e-5f } ?: 1f

        fun normByte(v: Float, minV: Float, span: Float): Byte {
            val n = ((v - minV) / span).coerceIn(0f, 1f)
            return (n * 255f).roundToInt().coerceIn(0, 255).toByte()
        }

        if (size3d > 1 && entries.size >= size3d * size3d * size3d) {
            val targetSize = if (size3d > 33) 33 else size3d
            val bytes = ByteArray(targetSize * targetSize * targetSize * 3)
            var outIdx = 0
            // Adobe .cube order: B outer, G middle, R inner -> write directly as [r][g][b]
            for (r in 0 until targetSize) {
                val srcR = if (targetSize == size3d) r else ((r.toFloat() / (targetSize - 1)) * (size3d - 1)).roundToInt().coerceIn(0, size3d - 1)
                for (g in 0 until targetSize) {
                    val srcG = if (targetSize == size3d) g else ((g.toFloat() / (targetSize - 1)) * (size3d - 1)).roundToInt().coerceIn(0, size3d - 1)
                    for (b in 0 until targetSize) {
                        val srcB = if (targetSize == size3d) b else ((b.toFloat() / (targetSize - 1)) * (size3d - 1)).roundToInt().coerceIn(0, size3d - 1)
                        val entryIndex = srcB * size3d * size3d + srcG * size3d + srcR
                        val rgb = entries[entryIndex]
                        bytes[outIdx++] = normByte(rgb[0], domainMin[0], spanR)
                        bytes[outIdx++] = normByte(rgb[1], domainMin[1], spanG)
                        bytes[outIdx++] = normByte(rgb[2], domainMin[2], spanB)
                    }
                }
            }
            return ParsedCubeLut(
                title = title,
                size = targetSize,
                encodedRgbBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            )
        }

        if (size1d > 1 && entries.size >= size1d) {
            val targetSize = 17
            val bytes = ByteArray(targetSize * targetSize * targetSize * 3)
            var outIdx = 0
            for (r in 0 until targetSize) {
                val iR = ((r.toFloat() / (targetSize - 1)) * (size1d - 1)).roundToInt().coerceIn(0, size1d - 1)
                for (g in 0 until targetSize) {
                    val iG = ((g.toFloat() / (targetSize - 1)) * (size1d - 1)).roundToInt().coerceIn(0, size1d - 1)
                    for (b in 0 until targetSize) {
                        val iB = ((b.toFloat() / (targetSize - 1)) * (size1d - 1)).roundToInt().coerceIn(0, size1d - 1)
                        bytes[outIdx++] = normByte(entries[iR][0], domainMin[0], spanR)
                        bytes[outIdx++] = normByte(entries[iG][1], domainMin[1], spanG)
                        bytes[outIdx++] = normByte(entries[iB][2], domainMin[2], spanB)
                    }
                }
            }
            return ParsedCubeLut(
                title = title,
                size = targetSize,
                encodedRgbBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            )
        }

        throw IllegalArgumentException("Invalid .cube LUT file: missing LUT_3D_SIZE or RGB table entries.")
    }

    /**
     * Decodes encoded RGB triplets into Media3 SingleColorLut.createFromCube(cube[r][g][b]) format.
     */
    fun decodeToCubeArray(size: Int, encodedData: String): Array<Array<IntArray>>? {
        if (size < 2 || encodedData.isBlank()) return null
        val bytes = runCatching { Base64.decode(encodedData, Base64.DEFAULT) }.getOrNull() ?: return null
        val expected = size * size * size * 3
        if (bytes.size < expected) return null

        var idx = 0
        return Array(size) {
            Array(size) {
                IntArray(size) {
                    val r = bytes[idx++].toInt() and 0xFF
                    val g = bytes[idx++].toInt() and 0xFF
                    val b = bytes[idx++].toInt() and 0xFF
                    Color.rgb(r, g, b)
                }
            }
        }
    }

    /**
     * Synthesizes a smooth 17x17x17 3D LUT for After Effects Curves / Levels / Split-Tone CC presets.
     */
    fun buildToneCurveCube(
        shadowCrush: Float = 0f,
        highlightLift: Float = 0f,
        gamma: Float = 1f,
        warmSplit: Float = 0f,
    ): Array<Array<IntArray>> {
        val n = 17
        val safeGamma = gamma.coerceIn(0.45f, 2.2f)
        fun curveChannel(x: Float, splitOffset: Float): Int {
            var v = x.pow(1f / safeGamma)
            // Soft S-curve shadow crush & highlight lift
            if (shadowCrush > 0.01f) {
                v = ((v - shadowCrush * 0.14f) / (1f - shadowCrush * 0.14f)).coerceIn(0f, 1f)
            }
            if (highlightLift != 0f) {
                v = (v + highlightLift * 0.12f * v * v).coerceIn(0f, 1f)
            }
            // Split-tone highlights vs shadows
            if (splitOffset != 0f) {
                val shadowWeight = (1f - v) * v * 2f
                val highlightWeight = v * (1f - v) * 2f
                v = (v + splitOffset * (highlightWeight - shadowWeight) * 0.18f).coerceIn(0f, 1f)
            }
            return (v * 255f).roundToInt().coerceIn(0, 255)
        }

        return Array(n) { r ->
            val rf = r / (n - 1f)
            Array(n) { g ->
                val gf = g / (n - 1f)
                IntArray(n) { b ->
                    val bf = b / (n - 1f)
                    Color.rgb(
                        curveChannel(rf, warmSplit),
                        curveChannel(gf, 0f),
                        curveChannel(bf, -warmSplit),
                    )
                }
            }
        }
    }
}
