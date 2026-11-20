package com.example.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BlendStyle
import com.example.data.model.FfxPreset
import com.example.data.model.LayerKind
import com.example.data.model.LayerShapeType
import com.example.data.model.TimelineLayer
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepAmberContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LunaMint
import com.example.ui.theme.LunaMintContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.StudioAmber
import java.util.Locale
import kotlin.math.roundToInt

enum class LayerToolPanel {
    NONE,
    SPEED,
    COLOR_FILL,
    BLEND_OPACITY,
    TRANSFORM,
    EDIT_SHAPE_OR_TEXT,
    PRESETS_PICKER,
    EFFECTS_INSPECTOR,
}

enum class AddLayerTab(val label: String) {
    SHAPE("Shape"),
    MEDIA("Media"),
    PRESET_FX("Preset FX"),
    TEXT("Text"),
    TEMPLATE("Template"),
}

@Composable
fun SelectedLayerToolGrid(
    layer: TimelineLayer,
    onOpenPanel: (LayerToolPanel) -> Unit,
    onTrimLeft: () -> Unit,
    onSplit: () -> Unit,
    onTrimRight: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBorderShadow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Row 1 (5 utility buttons matching Screenshot 3)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactToolTile(
                icon = Icons.Filled.Speed,
                badgeText = "${layer.speedMultiplier}x",
                highlighted = layer.speedMultiplier != 1.0f,
                contentDesc = "Playback Speed",
                onClick = { onOpenPanel(LayerToolPanel.SPEED) },
                modifier = Modifier.weight(1f).testTag("tool_speed"),
            )
            CompactToolTile(
                icon = Icons.Filled.KeyboardDoubleArrowLeft,
                badgeText = "Trim L",
                highlighted = false,
                contentDesc = "Trim Left at Playhead",
                onClick = onTrimLeft,
                modifier = Modifier.weight(1f).testTag("tool_trim_left"),
            )
            CompactToolTile(
                icon = Icons.Filled.ContentCut,
                badgeText = "Split",
                highlighted = false,
                contentDesc = "Split Layer at Playhead",
                onClick = onSplit,
                modifier = Modifier.weight(1f).testTag("tool_split"),
            )
            CompactToolTile(
                icon = Icons.Filled.KeyboardDoubleArrowRight,
                badgeText = "Trim R",
                highlighted = false,
                contentDesc = "Trim Right at Playhead",
                onClick = onTrimRight,
                modifier = Modifier.weight(1f).testTag("tool_trim_right"),
            )
            CompactToolTile(
                icon = if (layer.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                badgeText = if (layer.isMuted) "Muted" else "Audio",
                highlighted = layer.isMuted,
                contentDesc = "Toggle Audio Mute",
                onClick = onToggleMute,
                modifier = Modifier.weight(1f).testTag("tool_mute"),
            )
        }

        // Row 2 (3 tiles matching Screenshot 3)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabeledToolTile(
                icon = Icons.Filled.FormatColorFill,
                label = "Color & Fill",
                active = false,
                onClick = { onOpenPanel(LayerToolPanel.COLOR_FILL) },
                modifier = Modifier.weight(1f).testTag("tool_color_fill"),
            )
            LabeledToolTile(
                icon = Icons.Filled.BorderOuter,
                label = "Border & Shadow",
                active = layer.hasBorderShadow,
                onClick = onToggleBorderShadow,
                modifier = Modifier.weight(1f).testTag("tool_border_shadow"),
            )
            LabeledToolTile(
                icon = Icons.Filled.Layers,
                label = "Blending & Opacity",
                active = layer.blendStyle != BlendStyle.NORMAL || layer.opacity < 0.99f,
                onClick = { onOpenPanel(LayerToolPanel.BLEND_OPACITY) },
                modifier = Modifier.weight(1f).testTag("tool_blending"),
            )
        }

        // Row 3 (4 tiles matching Screenshot 3)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabeledToolTile(
                icon = Icons.Filled.OpenWith,
                label = "Move & Transform",
                active = layer.rotationStepDegrees != 0 || layer.flipHorizontal,
                onClick = { onOpenPanel(LayerToolPanel.TRANSFORM) },
                modifier = Modifier.weight(1f).testTag("tool_transform"),
            )
            LabeledToolTile(
                icon = if (layer.kind == LayerKind.TEXT) Icons.Filled.TextFields else Icons.Filled.Category,
                label = if (layer.kind == LayerKind.TEXT) "Edit Text" else "Edit Shape",
                active = layer.kind == LayerKind.SHAPE || layer.kind == LayerKind.TEXT,
                onClick = { onOpenPanel(LayerToolPanel.EDIT_SHAPE_OR_TEXT) },
                modifier = Modifier.weight(1f).testTag("tool_edit_shape"),
            )
            LabeledToolTile(
                icon = Icons.Filled.StarOutline,
                label = "Presets",
                active = false,
                onClick = { onOpenPanel(LayerToolPanel.PRESETS_PICKER) },
                modifier = Modifier.weight(1f).testTag("tool_presets"),
            )
            LabeledToolTile(
                icon = Icons.Filled.AutoAwesome,
                label = "Effects",
                active = layer.preset != null,
                onClick = { onOpenPanel(LayerToolPanel.EFFECTS_INSPECTOR) },
                modifier = Modifier.weight(1f).testTag("tool_effects_inspector"),
            )
        }
    }
}

@Composable
private fun CompactToolTile(
    icon: ImageVector,
    badgeText: String,
    highlighted: Boolean,
    contentDesc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) LunaMintContainer else ObsidianSurfaceContainer)
            .border(
                1.dp,
                if (highlighted) LunaMint else OutlineSubtle,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (highlighted) LunaMint else Color.White,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) LunaMint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun LabeledToolTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) LunaMintContainer else ObsidianSurfaceContainer)
            .border(
                1.dp,
                if (active) LunaMint else OutlineSubtle,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) LunaMint else Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) LunaMint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AddLayerDrawer(
    presets: List<FfxPreset>,
    onClose: () -> Unit,
    onPickVideo: () -> Unit,
    onImportFfx: () -> Unit,
    onAddPresetLayer: (FfxPreset) -> Unit,
    onAddShapeLayer: (LayerShapeType) -> Unit,
    onAddTextLayer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(AddLayerTab.SHAPE) }
    var draftText by remember { mutableStateOf("LUNA FX") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .border(1.dp, OutlineSubtle, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .padding(bottom = 12.dp),
    ) {
        // Top Category Bar (Screenshot 4)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurfaceContainer)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddLayerTab.entries.forEach { tab ->
                val active = selectedTab == tab
                val icon = when (tab) {
                    AddLayerTab.SHAPE -> Icons.Filled.Category
                    AddLayerTab.MEDIA -> Icons.Filled.PhotoLibrary
                    AddLayerTab.PRESET_FX -> Icons.Filled.AutoAwesome
                    AddLayerTab.TEXT -> Icons.Filled.TextFields
                    AddLayerTab.TEMPLATE -> Icons.Filled.Dashboard
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedTab = tab }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = if (active) LunaMint else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) LunaMint else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close add layer panel")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(215.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Left content area
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    AddLayerTab.SHAPE -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(LayerShapeType.entries) { shape ->
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ObsidianSurfaceContainer)
                                        .border(1.dp, OutlineSubtle, RoundedCornerShape(10.dp))
                                        .clickable {
                                            onAddShapeLayer(shape)
                                            onClose()
                                        }
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(
                                                if (shape == LayerShapeType.CIRCLE) CircleShape
                                                else RoundedCornerShape(6.dp)
                                            )
                                            .background(Color(0xFFD8DCE8)),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = shape.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    AddLayerTab.MEDIA -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianSurfaceContainer)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Replace or Add Video Clip", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Uses Android Photo Picker with full timeline audio & video support.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    onPickVideo()
                                    onClose()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LunaMint,
                                    contentColor = ObsidianBackground,
                                ),
                            ) {
                                Text("Choose Video from Device")
                            }
                        }
                    }
                    AddLayerTab.PRESET_FX, AddLayerTab.TEMPLATE -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets, key = { it.id.ifBlank { it.name } }) { preset ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ObsidianSurfaceContainer)
                                        .clickable {
                                            onAddPresetLayer(preset)
                                            onClose()
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${preset.presetFormat} • ${preset.effectModules.size} modules",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyberCyan,
                                        )
                                    }
                                    Icon(Icons.Filled.AddCircleOutline, contentDescription = null, tint = LunaMint)
                                }
                            }
                        }
                    }
                    AddLayerTab.TEXT -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = draftText,
                                onValueChange = { draftText = it },
                                label = { Text("Overlay Caption") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    onAddTextLayer(draftText)
                                    onClose()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LunaMint,
                                    contentColor = ObsidianBackground,
                                ),
                            ) {
                                Text("Add Text Layer")
                            }
                        }
                    }
                }
            }

            // Right utility strip (Screenshot 4)
            Column(
                modifier = Modifier
                    .width(92.dp)
                    .background(ObsidianSurfaceContainer, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onImportFfx()
                            onClose()
                        }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, tint = LunaMint)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import .ffx\n/ .cube",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedTab = AddLayerTab.TEXT }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.TextFields, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Text", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun LayerToolPanelDialog(
    panel: LayerToolPanel,
    layer: TimelineLayer,
    presets: List<FfxPreset>,
    onDismiss: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetColor: (Long) -> Unit,
    onSetBlendOpacity: (BlendStyle, Float) -> Unit,
    onUpdateTransform: (Int, Boolean) -> Unit,
    onUpdateShapeOrText: (LayerShapeType?, String?) -> Unit,
    onApplyPresetToLayer: (FfxPreset) -> Unit,
    onImportFfx: () -> Unit,
) {
    if (panel == LayerToolPanel.NONE) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceHigh,
        title = {
            Text(
                text = when (panel) {
                    LayerToolPanel.SPEED -> "Layer Playback Speed"
                    LayerToolPanel.COLOR_FILL -> "Layer Color & Tint Accent"
                    LayerToolPanel.BLEND_OPACITY -> "Blending & Layer Opacity"
                    LayerToolPanel.TRANSFORM -> "Move & Transform"
                    LayerToolPanel.EDIT_SHAPE_OR_TEXT -> if (layer.kind == LayerKind.TEXT) "Edit Text Layer" else "Edit Shape Geometry"
                    LayerToolPanel.PRESETS_PICKER -> "Apply .ffx / .cube Preset"
                    LayerToolPanel.EFFECTS_INSPECTOR -> "Effects Inspector (Exact Preset Values)"
                    LayerToolPanel.NONE -> ""
                },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (panel) {
                    LayerToolPanel.SPEED -> {
                        Text(
                            text = "Discrete clip time-stretch (0.5x slow-mo to 2x speed-ramp):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f).forEach { spd ->
                                FilterChip(
                                    selected = kotlin.math.abs(layer.speedMultiplier - spd) < 0.05f,
                                    onClick = {
                                        onSetSpeed(spd)
                                        onDismiss()
                                    },
                                    label = { Text("${spd}x") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LunaMintContainer,
                                        selectedLabelColor = LunaMint,
                                    ),
                                )
                            }
                        }
                    }

                    LayerToolPanel.COLOR_FILL -> {
                        val swatches = listOf(
                            0xFF4BB8C9L,
                            0xFF45C6A2L,
                            0xFF8B5CF6L,
                            0xFFF59E0BL,
                            0xFFEF4444L,
                            0xFFF4F5FAL,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            swatches.forEach { hex ->
                                val selected = layer.accentColorHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(
                                            if (selected) 3.dp else 1.dp,
                                            if (selected) Color.White else OutlineSubtle,
                                            CircleShape,
                                        )
                                        .clickable {
                                            onSetColor(hex)
                                            onDismiss()
                                        },
                                )
                            }
                        }
                    }

                    LayerToolPanel.BLEND_OPACITY -> {
                        var chosenBlend by remember { mutableStateOf(layer.blendStyle) }
                        var chosenOpacity by remember { mutableFloatStateOf(layer.opacity) }
                        Text("Blend Mode", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BlendStyle.entries.forEach { mode ->
                                FilterChip(
                                    selected = chosenBlend == mode,
                                    onClick = {
                                        chosenBlend = mode
                                        onSetBlendOpacity(chosenBlend, chosenOpacity)
                                    },
                                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                        Text("Layer Opacity", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { alpha ->
                                FilterChip(
                                    selected = kotlin.math.abs(chosenOpacity - alpha) < 0.08f,
                                    onClick = {
                                        chosenOpacity = alpha
                                        onSetBlendOpacity(chosenBlend, chosenOpacity)
                                    },
                                    label = { Text("${(alpha * 100).roundToInt()}%") },
                                )
                            }
                        }
                    }

                    LayerToolPanel.TRANSFORM -> {
                        var rot by remember { mutableIntStateOf(layer.rotationStepDegrees) }
                        var flipped by remember { mutableStateOf(layer.flipHorizontal) }
                        Text("Rotation Angle", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(-90, 0, 90, 180).forEach { deg ->
                                FilterChip(
                                    selected = rot == deg,
                                    onClick = {
                                        rot = deg
                                        onUpdateTransform(rot, flipped)
                                    },
                                    label = { Text("${deg}°") },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    flipped = !flipped
                                    onUpdateTransform(rot, flipped)
                                },
                            ) {
                                Icon(Icons.Filled.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (flipped) "Flipped Horz" else "Flip Horizontal")
                            }
                            OutlinedButton(
                                onClick = {
                                    rot = (rot + 90) % 360
                                    onUpdateTransform(rot, flipped)
                                },
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+90°")
                            }
                        }
                    }

                    LayerToolPanel.EDIT_SHAPE_OR_TEXT -> {
                        if (layer.kind == LayerKind.TEXT) {
                            var text by remember { mutableStateOf(layer.textContent) }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text("Text Content") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    onUpdateShapeOrText(null, text)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
                            ) {
                                Text("Update Text")
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LayerShapeType.entries.forEach { shape ->
                                    OutlinedButton(
                                        onClick = {
                                            onUpdateShapeOrText(shape, null)
                                            onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(shape.label)
                                    }
                                }
                            }
                        }
                    }

                    LayerToolPanel.PRESETS_PICKER -> {
                        OutlinedButton(
                            onClick = {
                                onImportFfx()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, tint = LunaMint)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import .ffx or .cube file", color = LunaMint)
                        }
                        presets.take(8).forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianSurfaceContainer)
                                    .clickable {
                                        onApplyPresetToLayer(p)
                                        onDismiss()
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${p.presetFormat} • ${p.category}", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                                }
                                Text("Apply", color = LunaMint, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    LayerToolPanel.EFFECTS_INSPECTOR -> {
                        val preset = layer.preset
                        if (preset == null) {
                            Text(
                                text = "This layer uses direct geometric/text properties (${layer.blendStyle.label}, ${(layer.opacity * 100).roundToInt()}% opacity). Select a Preset FX layer or apply a .ffx / .cube file to inspect shader parameters.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // Read-only inspector — ZERO editable sliders so preset integrity is guaranteed!
                            Surface(
                                color = ObsidianSurface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${preset.name} (${preset.presetFormat})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = LunaMint,
                                    )
                                    Text(
                                        text = preset.ffxVersion,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan,
                                    )
                                    if (preset.lutSize >= 2) {
                                        Text(
                                            text = "3D LUT Pipeline: ${preset.lutSize}x${preset.lutSize}x${preset.lutSize} OpenGL SingleColorLut",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = LunaMint,
                                        )
                                    }
                                }
                            }

                            if (preset.effectModules.isNotEmpty()) {
                                Text("Active Mobile Shader Stack", style = MaterialTheme.typography.labelMedium)
                                preset.effectModules.forEach { mod ->
                                    Text("• $mod", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                }
                            }

                            if (preset.unsupportedModules.isNotEmpty()) {
                                Surface(
                                    color = DeepAmberContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Desktop-Only Plugins Detected", style = MaterialTheme.typography.labelMedium, color = StudioAmber)
                                        }
                                        preset.unsupportedModules.forEach { plug ->
                                            Text(
                                                text = "• $plug — use a .cube LUT export from After Effects for exact color grading",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                color = ObsidianSurface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Locked Baked Parameters (Read-Only)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val stats = listOf(
                                        "Exposure" to String.format(Locale.US, "%+.2f", preset.exposure),
                                        "Contrast" to String.format(Locale.US, "%+.2f", preset.contrast),
                                        "Saturation" to String.format(Locale.US, "%+.0f", preset.saturation),
                                        "Temp / Tint" to String.format(Locale.US, "%+.2f / %+.2f", preset.temperature, preset.tint),
                                        "Glow / Vignette" to String.format(Locale.US, "%.2f / %.2f", preset.glowIntensity, preset.vignetteIntensity),
                                        "Shake / Zoom / Blur" to String.format(Locale.US, "%.2f / %.2f / %.1fpx", preset.shakeIntensity, preset.zoomPulse, preset.blurRadius),
                                    )
                                    stats.forEach { (k, v) ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(v, style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    LayerToolPanel.NONE -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = LunaMint)
            }
        },
    )
}
