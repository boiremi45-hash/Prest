package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ExportRecord

@Entity(tableName = "exports")
data class ExportRecordEntity(
    @PrimaryKey val id: String,
    val presetId: String,
    val presetName: String,
    val outputFileName: String,
    val resolutionLabel: String,
    val codecLabel: String,
    val bitrateMbps: Long,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val localUriString: String,
    val createdAtMillis: Long,
) {
    fun toDomain(): ExportRecord = ExportRecord(
        id = id,
        presetId = presetId,
        presetName = presetName,
        outputFileName = outputFileName,
        resolutionLabel = resolutionLabel,
        codecLabel = codecLabel,
        bitrateMbps = bitrateMbps,
        fileSizeBytes = fileSizeBytes,
        durationMs = durationMs,
        localUriString = localUriString,
        createdAtMillis = createdAtMillis,
    )
}

fun ExportRecord.toEntity(
    resolvedId: String = id,
    createdAt: Long = createdAtMillis,
): ExportRecordEntity = ExportRecordEntity(
    id = resolvedId,
    presetId = presetId,
    presetName = presetName,
    outputFileName = outputFileName,
    resolutionLabel = resolutionLabel,
    codecLabel = codecLabel,
    bitrateMbps = bitrateMbps,
    fileSizeBytes = fileSizeBytes,
    durationMs = durationMs,
    localUriString = localUriString,
    createdAtMillis = createdAt,
)
