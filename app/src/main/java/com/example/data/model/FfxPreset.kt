package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

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

@IgnoreExtraProperties
data class FfxPreset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val fileName: String = "",
    val category: String = PresetCategory.COLOR_GRADE.label,
    val description: String = "",
    val sourceType: String = "studio",
    val ffxVersion: String = "AE 2024 (RIFX)",
    val effectModules: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @get:PropertyName("isFavorite")
    @field:PropertyName("isFavorite")
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
    val zoomPulse: Double = 0.0,
    val shakeIntensity: Double = 0.0,
    val rotationDegrees: Double = 0.0,
    val speedMultiplier: Double = 1.0,
    val letterboxRatio: Double = 0.0,
    val rawHeaderSnippet: String = "",
    val usageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun clamped(): FfxPreset = copy(
        name = name.trim().take(80).ifEmpty { "Untitled Preset" },
        fileName = fileName.trim().take(120).ifEmpty { "untitled.ffx" },
        category = PresetCategory.fromLabel(category).label,
        description = description.trim().take(400),
        ffxVersion = ffxVersion.trim().take(40),
        effectModules = effectModules.take(12).map { it.take(80) },
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
        zoomPulse = zoomPulse.coerceIn(0.0, 1.0),
        shakeIntensity = shakeIntensity.coerceIn(0.0, 1.0),
        rotationDegrees = rotationDegrees.coerceIn(-180.0, 180.0),
        speedMultiplier = speedMultiplier.coerceIn(0.25, 4.0),
        letterboxRatio = letterboxRatio.coerceIn(0.0, 0.35),
        rawHeaderSnippet = rawHeaderSnippet.take(1000),
        usageCount = usageCount.coerceAtLeast(0L),
    )
}
