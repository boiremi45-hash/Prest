package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ExportRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val presetId: String = "",
    val presetName: String = "",
    val outputFileName: String = "",
    val resolutionLabel: String = "1080p Full HD",
    val codecLabel: String = "H.264 / AVC",
    val bitrateMbps: Long = 24L,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val localUriString: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)
