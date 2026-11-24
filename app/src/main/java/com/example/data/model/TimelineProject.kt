package com.example.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class LayerKind {
    VIDEO,
    PRESET_FX,
    SHAPE,
    TEXT,
}

enum class LayerShapeType(val label: String) {
    LETTERBOX("Cinema Bars"),
    VIGNETTE_FRAME("Vignette Frame"),
    BORDER_FRAME("Glow Border"),
    CIRCLE("Circle Mask"),
    ROUNDED_RECT("Rounded Frame"),
    STAR_BADGE("Star Flare"),
}

enum class BlendStyle(val label: String, val alphaDefault: Float) {
    NORMAL("Normal", 1.0f),
    SOFT_GLOW("Soft Glow", 0.55f),
    SCREEN("Screen", 0.70f),
    SUBTLE("Subtle Tint", 0.35f),
}

@Serializable
enum class AspectPreset(val label: String, val ratio: Float) {
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_1_1("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
}

@Serializable
enum class CanvasFitMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    WIDE_CINEMA("2.39:1"),
}

data class TimelineLayer(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: LayerKind,
    val startMs: Long = 0L,
    val endMs: Long = 10_000L,
    val isVisible: Boolean = true,
    val preset: FfxPreset? = null,
    val shapeType: LayerShapeType? = null,
    val textContent: String = "",
    val accentColorHex: Long = 0xFF4BB8C9L,
    val hasBorderShadow: Boolean = false,
    val blendStyle: BlendStyle = BlendStyle.NORMAL,
    val opacity: Float = 1.0f,
    val rotationStepDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val speedMultiplier: Float = 1.0f,
    val isMuted: Boolean = false,
)

@Serializable
data class LunaProjectSummary(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "LUNA Project 1",
    val aspectPreset: AspectPreset = AspectPreset.RATIO_16_9,
    val resolutionLabel: String = "1080p",
    val fps: Int = 60,
    val durationMs: Long = 10_000L,
    val activePresetName: String = "Teal & Orange Blockbuster",
    val layerCount: Int = 1,
    val videoDisplayName: String? = null,
    val thumbnailPath: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)
