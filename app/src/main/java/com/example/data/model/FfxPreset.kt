package com.example.data.model

import kotlinx.serialization.Serializable

enum class PresetCategory(val label: String) {
    COLOR_GRADE("Color Grade"),
    MOTION_SHAKE("Motion & Shake"),
    GLOW_OPTICS("Glow & Optics"),
    FILM_RETRO("Film & Retro"),
    TRANSFORM("Transform"),
    CUSTOM_FFX("Custom .ffx");

    companion object {
        fun fromLabel(label: String): PresetCategory =
            entries.firstOrNull { it.label == label } ?: CUSTOM_FFX
    }
}

@Serializable
data class FfxPreset(
    val id: String = "",
    val name: String = "",
    val fileName: String = "",
    val category: String = PresetCategory.COLOR_GRADE.label,
    val description: String = "",
    val sourceType: String = "studio",
    val presetFormat: String = "FFX",
    val ffxVersion: String = "AE 2024 (RIFX)",
    val effectModules: List<String> = emptyList(),
    val unsupportedModules: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
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
    val rawHeaderSnippet: String = "",
    val usageCount: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
) {
    fun clamped(): FfxPreset = copy(
        name = name.trim().take(80).ifEmpty { "Untitled Preset" },
        fileName = fileName.trim().take(120).ifEmpty { "untitled.ffx" },
        category = PresetCategory.fromLabel(category).label,
        description = description.trim().take(400),
        presetFormat = if (presetFormat == "CUBE_LUT") "CUBE_LUT" else "FFX",
        ffxVersion = ffxVersion.trim().take(40),
        effectModules = effectModules.take(16).map { it.take(80) },
        unsupportedModules = unsupportedModules.take(16).map { it.take(80) },
        tags = tags.take(10).map { it.take(32) },
        exposure = exposure.coerceIn(-1.0, 1.0),
        contrast = contrast.coerceIn(-1.0, 1.0),
        saturation = saturation.coerceIn(-100.0, 100.0),
        hueShift = hueShift.coerceIn(-180.0, 180.0),
        temperature = temperature.coerceIn(-1.0, 1.0),
        tint = tint.coerceIn(-1.0, 1.0),
        vignetteIntensity = vignetteIntensity.coerceIn(0.0, 1.0),
        glowIntensity = glowIntensity.coerceIn(0.0, 1.0),
        grainAmount = grainAmount.coerceIn(0.0, 1.0),
        blurRadius = blurRadius.coerceIn(0.0, 25.0),
        zoomPulse = zoomPulse.coerceIn(0.0, 1.0),
        shakeIntensity = shakeIntensity.coerceIn(0.0, 1.0),
        rotationDegrees = rotationDegrees.coerceIn(-180.0, 180.0),
        speedMultiplier = speedMultiplier.coerceIn(0.25, 4.0),
        letterboxRatio = letterboxRatio.coerceIn(0.0, 0.35),
        lutSize = lutSize.coerceIn(0, 65),
        rawHeaderSnippet = rawHeaderSnippet.take(1000),
        usageCount = usageCount.coerceAtLeast(0L),
    )
}
