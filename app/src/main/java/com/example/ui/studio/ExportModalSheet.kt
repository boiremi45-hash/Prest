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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.domain.video.ExportCodec
import com.example.domain.video.ExportConfig
import com.example.domain.video.ExportResolution
import com.example.domain.video.ExportUiState
import com.example.domain.video.VideoMetadata
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.DeepVioletContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.StudioSuccess
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportModalSheet(
    video: VideoMetadata,
    preset: FfxPreset,
    exportState: ExportUiState,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onSaveCopyAs: (ExportRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val defaultBaseName = remember(video.displayName, preset.name) {
        val clipBase = video.displayName.substringBeforeLast('.').take(20).ifBlank { "Clip" }
        val fxBase = preset.name.replace(Regex("[^a-zA-Z0-9]+"), "_").trim('_').take(18)
        "${clipBase}_$fxBase"
    }

    var fileName by remember(defaultBaseName) { mutableStateOf(defaultBaseName) }
    var resolution by remember { mutableStateOf(ExportResolution.FHD_1080P) }
    var codec by remember { mutableStateOf(ExportCodec.H264) }
    var bitrateMbps by remember { mutableIntStateOf(24) }
    var removeAudio by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            if (exportState !is ExportUiState.Rendering) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = ObsidianSurfaceHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.HighQuality,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "High-Quality Render & Download",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                if (exportState !is ExportUiState.Rendering) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close export sheet")
                    }
                }
            }

            // Active summary pill
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OutlineSubtle, RoundedCornerShape(14.dp)),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "SOURCE: ${video.displayName} (${video.width}x${video.height})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "PRESET: ${preset.fileName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan,
                    )
                }
            }

            when (exportState) {
                is ExportUiState.Rendering -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianSurfaceContainer)
                            .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = exportState.stageLabel,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${exportState.progressPercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = CyberCyan,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { exportState.progressPercent / 100f },
                            color = CyberCyan,
                            trackColor = ObsidianSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Text(
                            text = "Encoding video frames with hardware OpenGL shaders...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = onCancelExport,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Cancel Render")
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
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = StudioSuccess,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Saved to Local Drive!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StudioSuccess,
                                )
                                Text(
                                    text = exportState.savedLocationLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Text(
                            text = "${record.resolutionLabel} • ${record.codecLabel} • ${record.bitrateMbps} Mbps • $sizeMb",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    val uri = runCatching { Uri.parse(record.localUriString) }.getOrNull()
                                    if (uri != null) {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "video/mp4")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(intent) }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberCyan,
                                    contentColor = ObsidianBackground,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play Video")
                            }

                            OutlinedButton(
                                onClick = { onSaveCopyAs(record) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save As")
                            }

                            IconButton(
                                onClick = {
                                    val uri = runCatching { Uri.parse(record.localUriString) }.getOrNull()
                                    if (uri != null) {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(share, "Share Export")) }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share exported video")
                            }
                        }
                    }
                }

                is ExportUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = exportState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                ExportUiState.Idle -> Unit
            }

            if (exportState !is ExportUiState.Rendering) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Output File Name (.mp4)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Export Resolution",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExportResolution.entries.forEach { res ->
                        val selected = resolution == res
                        FilterChip(
                            selected = selected,
                            onClick = { resolution = res },
                            label = {
                                Text(
                                    text = when (res) {
                                        ExportResolution.ORIGINAL -> "Orig"
                                        ExportResolution.UHD_4K -> "4K"
                                        ExportResolution.FHD_1080P -> "1080p"
                                        ExportResolution.HD_720P -> "720p"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DeepVioletContainer,
                                selectedLabelColor = Color.White,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text(
                    text = "Hardware Codec",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExportCodec.entries.forEach { c ->
                        val selected = codec == c
                        FilterChip(
                            selected = selected,
                            onClick = { codec = c },
                            label = { Text(c.label, style = MaterialTheme.typography.labelLarge) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DeepCyanContainer,
                                selectedLabelColor = CyberCyan,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Target Video Bitrate", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "$bitrateMbps Mbps",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan,
                    )
                }
                Slider(
                    value = bitrateMbps.toFloat(),
                    onValueChange = { bitrateMbps = it.toInt() },
                    valueRange = 8f..60f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyberCyan,
                        activeTrackColor = CyberCyan,
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Mute Audio Track", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Strip original audio during export",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = removeAudio,
                        onCheckedChange = { removeAudio = it },
                    )
                }

                Button(
                    onClick = {
                        onStartExport(
                            ExportConfig(
                                fileName = fileName,
                                resolution = resolution,
                                codec = codec,
                                bitrateMbps = bitrateMbps,
                                removeAudio = removeAudio,
                            )
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_render_export_button"),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (exportState is ExportUiState.Success) "Render Another Copy" else "Render & Download MP4",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
