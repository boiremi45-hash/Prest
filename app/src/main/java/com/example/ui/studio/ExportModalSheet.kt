package com.example.ui.studio

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.domain.video.ExportCodec
import com.example.domain.video.ExportConfig
import com.example.domain.video.ExportResolution
import com.example.domain.video.ExportUiState
import com.example.domain.video.VideoMetadata
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LunaMint
import com.example.ui.theme.LunaMintContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

private val RESOLUTIONS = listOf(
    ExportResolution.SD_480P,
    ExportResolution.HD_720P,
    ExportResolution.FHD_1080P,
    ExportResolution.QHD_2K,
    ExportResolution.UHD_4K,
)
private val FRAME_RATES = listOf(24, 25, 30, 50, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportModalSheet(
    video: VideoMetadata,
    timelineDurationMs: Long,
    preset: FfxPreset,
    exportState: ExportUiState,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onSaveCopyAs: (ExportRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val defaultBaseName = remember(video.displayName) {
        video.displayName.substringBeforeLast('.').take(20).ifBlank { "LUNA_Edit" } + "_LUNA"
    }

    var fileName by remember(defaultBaseName) { mutableStateOf(defaultBaseName) }
    var resIdx by remember { mutableIntStateOf(2) } // default 1080p
    var fpsIdx by remember { mutableIntStateOf(2) } // default 30fps
    var opticalFlow by remember { mutableStateOf(false) }
    var codec by remember { mutableStateOf(ExportCodec.H264) }
    var bitrateMbps by remember { mutableIntStateOf(24) }
    var removeAudio by remember { mutableStateOf(false) }

    val resolution = RESOLUTIONS[resIdx.coerceIn(0, RESOLUTIONS.lastIndex)]
    val frameRate = FRAME_RATES[fpsIdx.coerceIn(0, FRAME_RATES.lastIndex)]
    val estimatedMb = remember(timelineDurationMs, resolution, frameRate, bitrateMbps) {
        val sec = (timelineDurationMs / 1000.0).coerceAtLeast(1.0)
        val scaleRes = when (resolution) {
            ExportResolution.SD_480P -> 0.45
            ExportResolution.HD_720P -> 0.7
            ExportResolution.FHD_1080P -> 1.0
            ExportResolution.QHD_2K -> 1.4
            ExportResolution.UHD_4K -> 1.9
            ExportResolution.ORIGINAL -> 1.0
        }
        val fpsScale = frameRate / 30.0
        (sec * (bitrateMbps / 8.0) * scaleRes * (0.65 + 0.35 * fpsScale)).coerceAtLeast(1.2)
    }

    ModalBottomSheet(
        onDismissRequest = { if (exportState !is ExportUiState.Rendering) onDismiss() },
        sheetState = sheetState,
        containerColor = ObsidianSurfaceHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.HighQuality, contentDescription = null, tint = LunaMint, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Export Video", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                if (exportState !is ExportUiState.Rendering) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close export sheet", tint = Color.White)
                    }
                }
            }

            when (exportState) {
                is ExportUiState.Rendering -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianSurfaceContainer)
                            .border(1.dp, LunaMint.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Exporting...", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("${exportState.progressPercent}%", style = MaterialTheme.typography.titleLarge, color = LunaMint)
                        }
                        LinearProgressIndicator(
                            progress = { exportState.progressPercent / 100f },
                            color = LunaMint,
                            trackColor = ObsidianSurface,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        )
                        Text(exportState.stageLabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        OutlinedButton(onClick = onCancelExport, modifier = Modifier.align(Alignment.End)) {
                            Text("Cancel")
                        }
                    }
                }

                is ExportUiState.Success -> {
                    val record = exportState.record
                    val sizeMb = String.format(Locale.US, "%.2f MB", record.fileSizeBytes / (1024.0 * 1024.0))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianSurfaceContainer)
                            .border(1.dp, StudioSuccess.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StudioSuccess, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Saved to device", style = MaterialTheme.typography.titleMedium, color = StudioSuccess)
                                Text(exportState.savedLocationLabel, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            }
                        }
                        Text("${record.resolutionLabel} • ${record.codecLabel} • $sizeMb", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    runCatching { Uri.parse(record.localUriString) }.getOrNull()?.let { uri ->
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "video/mp4")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(intent) }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play")
                            }
                            OutlinedButton(onClick = { onSaveCopyAs(record) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save As")
                            }
                            IconButton(
                                onClick = {
                                    runCatching { Uri.parse(record.localUriString) }.getOrNull()?.let { uri ->
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(share, "Share LUNA Video")) }
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share exported video", tint = Color.White)
                            }
                        }
                    }
                }

                is ExportUiState.Error -> {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(exportState.message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
                    }
                }

                ExportUiState.Idle -> Unit
            }

            if (exportState !is ExportUiState.Rendering) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Export file name (.mp4)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Resolution Stepped Slider (480p / 720p / 1080p / 2K / 4K)
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Resolution", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text(resolution.shortLabel, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    }
                    Slider(
                        value = resIdx.toFloat(),
                        onValueChange = { resIdx = it.roundToInt().coerceIn(0, RESOLUTIONS.lastIndex) },
                        valueRange = 0f..(RESOLUTIONS.lastIndex.toFloat()),
                        steps = RESOLUTIONS.size - 2,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = CyberCyan),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        RESOLUTIONS.forEachIndexed { idx, r ->
                            Text(
                                text = r.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (idx == resIdx) CyberCyan else TextSecondary,
                            )
                        }
                    }
                }

                // Frame Rate Stepped Slider (24 / 25 / 30 / 50 / 60)
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Frame rate", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text("${frameRate}fps", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    }
                    Slider(
                        value = fpsIdx.toFloat(),
                        onValueChange = { fpsIdx = it.roundToInt().coerceIn(0, FRAME_RATES.lastIndex) },
                        valueRange = 0f..(FRAME_RATES.lastIndex.toFloat()),
                        steps = FRAME_RATES.size - 2,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = CyberCyan),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        FRAME_RATES.forEachIndexed { idx, f ->
                            Text(
                                text = "$f",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (idx == fpsIdx) CyberCyan else TextSecondary,
                            )
                        }
                    }
                }

                // Optical flow + Bitrate slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Optical flow", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text("Frame blending for slow-mo smoothness", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = opticalFlow,
                        onCheckedChange = { opticalFlow = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBackground, checkedTrackColor = CyberCyan),
                    )
                }

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bitrate (Mbps)", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text("${bitrateMbps} Mbps", style = MaterialTheme.typography.titleSmall, color = LunaMint)
                    }
                    Slider(
                        value = bitrateMbps.toFloat(),
                        onValueChange = { bitrateMbps = it.roundToInt().coerceIn(6, 60) },
                        valueRange = 6f..60f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = LunaMint),
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportCodec.entries.forEach { c ->
                        FilterChip(
                            selected = codec == c,
                            onClick = { codec = c },
                            label = { Text(c.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Estimated file size", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(
                        text = String.format(Locale.US, "%.1f MB", estimatedMb),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = CyberCyan,
                    )
                }

                Button(
                    onClick = {
                        onStartExport(
                            ExportConfig(
                                fileName = fileName,
                                resolution = resolution,
                                frameRate = frameRate,
                                opticalFlow = opticalFlow,
                                codec = codec,
                                bitrateMbps = bitrateMbps,
                                removeAudio = removeAudio,
                            )
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_render_export_button"),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (exportState is ExportUiState.Success) "Export Another Copy" else "Save to device",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}
