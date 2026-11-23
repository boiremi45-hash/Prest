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

enum class ExportResolution(val label: String, val shortLabel: String, val targetHeight: Int?) {
    SD_480P("480p SD", "480p", 480),
    HD_720P("720p HD", "720p", 720),
    FHD_1080P("1080p Full HD", "1080p", 1080),
    QHD_2K("2K QHD", "2K/4K", 1440),
    UHD_4K("4K UHD", "4K", 2160),
    ORIGINAL("Original", "Orig", null),
}

enum class ExportCodec(val label: String, val mimeType: String) {
    H264("H.264 / AVC", "video/avc"),
    H265("H.265 / HEVC", "video/hevc"),
}

data class ExportConfig(
    val fileName: String = "LUNA_CapCut_Render",
    val resolution: ExportResolution = ExportResolution.FHD_1080P,
    val frameRate: Int = 30,
    val opticalFlow: Boolean = false,
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
