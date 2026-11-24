package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.AspectPreset
import com.example.data.model.LunaProjectSummary

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val aspectPresetName: String,
    val resolutionLabel: String,
    val fps: Int,
    val durationMs: Long,
    val activePresetName: String,
    val layerCount: Int,
    val videoDisplayName: String?,
    val thumbnailPath: String?,
    val playheadMs: Long,
    val canvasFitModeName: String,
    val timelineZoom: Float,
    val snapshotJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toSummary(): LunaProjectSummary {
        val aspect = runCatching { AspectPreset.valueOf(aspectPresetName) }.getOrDefault(AspectPreset.RATIO_9_16)
        return LunaProjectSummary(
            id = id,
            title = title,
            aspectPreset = aspect,
            resolutionLabel = resolutionLabel,
            fps = fps,
            durationMs = durationMs,
            activePresetName = activePresetName,
            layerCount = layerCount,
            videoDisplayName = videoDisplayName,
            thumbnailPath = thumbnailPath,
            updatedAtMillis = updatedAtMillis,
        )
    }
}
