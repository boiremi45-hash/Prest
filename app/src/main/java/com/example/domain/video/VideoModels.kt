package com.example.domain.video

import android.net.Uri
import com.example.data.model.ExportRecord

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class VideoMetadata(
    val uri: Uri,
    val displayName: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
)

enum class ExportResolution(val label: String, val targetHeight: Int?) {
    ORIGINAL("Original", null),
    UHD_4K("4K UHD (2160p)", 2160),
    FHD_1080P("1080p Full HD", 1080),
    HD_720P("720p HD", 720),
}

enum class ExportCodec(val label: String, val mimeType: String) {
    H264("H.264 / AVC", "video/avc"),
    H265("H.265 / HEVC", "video/hevc"),
}

data class ExportConfig(
    val fileName: String = "LUNA_Render",
    val resolution: ExportResolution = ExportResolution.FHD_1080P,
    val codec: ExportCodec = ExportCodec.H264,
    val bitrateMbps: Int = 24,
    val removeAudio: Boolean = false,
)

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Rendering(val progressPercent: Int, val stageLabel: String) : ExportUiState
    data class Success(val record: ExportRecord, val savedLocationLabel: String) : ExportUiState
    data class Error(val message: String) : ExportUiState
}
