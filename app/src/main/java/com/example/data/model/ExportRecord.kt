package com.example.data.model

data class ExportRecord(
    val id: String = "",
    val presetId: String = "",
    val presetName: String = "",
    val outputFileName: String = "",
    val resolutionLabel: String = "1080p Full HD",
    val codecLabel: String = "H.264 / AVC",
    val bitrateMbps: Long = 24L,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val localUriString: String = "",
    val createdAtMillis: Long = 0L,
)
