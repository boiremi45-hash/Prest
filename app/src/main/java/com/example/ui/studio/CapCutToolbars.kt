package com.example.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LensBlur
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vignette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoClipSegment
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LunaMint
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextSecondary

enum class ActiveEditorSheet {
    NONE,
    SPEED,
    GRAPHS,
    TRANSFORM,
    CROP,
    MASK,
    MOTION_BLUR,
    STABILIZE,
    VOLUME,
    AUDIO_VOLUME,
    OPACITY,
    EFFECTS,
    EFFECT_OBJECT,
    ANIMATIONS,
    TRANSITIONS,
    ADJUST,
    FILTERS,
    OVERLAY,
    TEXT,
    CAPTIONS,
    AUDIO,
    ASPECT_RATIO,
    BACKGROUND,
}

@Composable
fun CapCutMainBottomBar(
    onSelectEditClip: () -> Unit,
    onOpenSheet: (ActiveEditorSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBackground)
            .navigationBarsPadding()
            .height(64.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarActionItem(Icons.Filled.ContentCut, "Edit", "menu_edit", onClick = onSelectEditClip)
        ToolbarActionItem(Icons.Filled.MusicNote, "Audio", "menu_audio") { onOpenSheet(ActiveEditorSheet.AUDIO) }
        ToolbarActionItem(Icons.Filled.TextFields, "Text", "menu_text") { onOpenSheet(ActiveEditorSheet.TEXT) }
        ToolbarActionItem(Icons.Filled.AutoAwesome, "Effects", "menu_effects") { onOpenSheet(ActiveEditorSheet.EFFECTS) }
        ToolbarActionItem(Icons.Filled.Layers, "Overlay", "menu_overlay") { onOpenSheet(ActiveEditorSheet.OVERLAY) }
        ToolbarActionItem(Icons.Filled.ClosedCaption, "Captions", "menu_captions") { onOpenSheet(ActiveEditorSheet.CAPTIONS) }
        ToolbarActionItem(Icons.Filled.FilterVintage, "Filters", "menu_filters", highlightColor = LunaMint) {
            onOpenSheet(ActiveEditorSheet.FILTERS)
        }
        ToolbarActionItem(Icons.Filled.Tune, "Adjust", "menu_adjust") { onOpenSheet(ActiveEditorSheet.ADJUST) }
        ToolbarActionItem(Icons.Filled.AspectRatio, "Ratio", "menu_ratio") { onOpenSheet(ActiveEditorSheet.ASPECT_RATIO) }
        ToolbarActionItem(Icons.Filled.Palette, "Background", "menu_background") { onOpenSheet(ActiveEditorSheet.BACKGROUND) }
    }
}

@Composable
fun CapCutClipSelectedBar(
    clip: VideoClipSegment,
    canSplit: Boolean,
    showGraphsButton: Boolean,
    onBack: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onBeatToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onReplace: () -> Unit,
    onMoveToOverlay: () -> Unit,
    onFreeze: () -> Unit,
    onReverseToggle: () -> Unit,
    onOpenSheet: (ActiveEditorSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBackground)
            .navigationBarsPadding()
            .height(64.dp)
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left sticky '<' back button matching CapCut clip menu
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianSurfaceContainer)
                .border(1.dp, OutlineSubtle, RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .testTag("clip_menu_back"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Exit Clip Menu",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
                .padding(end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showGraphsButton) {
                ToolbarActionItem(
                    icon = Icons.Filled.GraphicEq,
                    label = "Graphs",
                    tag = "clip_tool_graphs",
                    highlightColor = CyberCyan,
                    onClick = { onOpenSheet(ActiveEditorSheet.GRAPHS) },
                )
            }
            ToolbarActionItem(
                icon = Icons.Filled.ContentCut,
                label = "Split",
                tag = "clip_tool_split",
                highlightColor = if (canSplit) Color.White else TextSecondary.copy(alpha = 0.4f),
                onClick = onSplit,
            )
            ToolbarActionItem(Icons.AutoMirrored.Filled.VolumeUp, "Volume", "clip_tool_volume") {
                onOpenSheet(ActiveEditorSheet.VOLUME)
            }
            ToolbarActionItem(Icons.Filled.Animation, "Animations", "clip_tool_animations") {
                onOpenSheet(ActiveEditorSheet.ANIMATIONS)
            }
            ToolbarActionItem(Icons.Filled.AutoAwesome, "Effects", "clip_tool_effects") {
                onOpenSheet(ActiveEditorSheet.EFFECTS)
            }
            ToolbarActionItem(Icons.Filled.DeleteOutline, "Delete", "clip_tool_delete", onClick = onDelete)
            ToolbarActionItem(Icons.Filled.Speed, "Speed", "clip_tool_speed") {
                onOpenSheet(ActiveEditorSheet.SPEED)
            }
            ToolbarActionItem(Icons.Filled.GraphicEq, "Beats", "clip_tool_beats", onClick = onBeatToggle)
            ToolbarActionItem(Icons.Filled.Crop, "Crop", "clip_tool_crop") {
                onOpenSheet(ActiveEditorSheet.CROP)
            }
            ToolbarActionItem(Icons.Filled.FileCopy, "Duplicate", "clip_tool_duplicate", onClick = onDuplicate)
            ToolbarActionItem(Icons.Filled.SwapHoriz, "Replace", "clip_tool_replace", onClick = onReplace)
            ToolbarActionItem(Icons.Filled.Layers, "Overlay", "clip_tool_overlay", onClick = onMoveToOverlay)
            ToolbarActionItem(Icons.Filled.Tune, "Adjust", "clip_tool_adjust") {
                onOpenSheet(ActiveEditorSheet.ADJUST)
            }
            ToolbarActionItem(Icons.Filled.Opacity, "Opacity", "clip_tool_opacity") {
                onOpenSheet(ActiveEditorSheet.OPACITY)
            }
            ToolbarActionItem(
                icon = Icons.Filled.BlurOn,
                label = "Motion Blur",
                tag = "clip_tool_motion_blur",
                highlightColor = if (clip.motionBlur.enabled) CyberCyan else Color.White,
            ) { onOpenSheet(ActiveEditorSheet.MOTION_BLUR) }
            ToolbarActionItem(Icons.Filled.OpenWith, "Transform", "clip_tool_transform") {
                onOpenSheet(ActiveEditorSheet.TRANSFORM)
            }
            ToolbarActionItem(
                icon = Icons.Filled.AcUnit,
                label = "Freeze",
                tag = "clip_tool_freeze",
                highlightColor = if (clip.isFrozen) CyberCyan else Color.White,
                onClick = onFreeze,
            )
            ToolbarActionItem(
                icon = Icons.Filled.Restore,
                label = "Reverse",
                tag = "clip_tool_reverse",
                highlightColor = if (clip.isReversed) CyberCyan else Color.White,
                onClick = onReverseToggle,
            )
            ToolbarActionItem(Icons.Filled.Vignette, "Mask", "clip_tool_mask") {
                onOpenSheet(ActiveEditorSheet.MASK)
            }
            ToolbarActionItem(Icons.Filled.LensBlur, "Stabilize", "clip_tool_stabilize") {
                onOpenSheet(ActiveEditorSheet.STABILIZE)
            }
        }
    }
}

@Composable
fun CapCutEffectSelectedBar(
    onBack: () -> Unit,
    onSplit: () -> Unit,
    onAdjust: () -> Unit,
    onReplace: () -> Unit,
    onDuplicate: () -> Unit,
    onObject: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CapCutContextualTrackBar(backTag = "effect_menu_back", onBack = onBack, modifier = modifier) {
        ToolbarActionItem(Icons.Filled.ContentCut, "Split", "effect_tool_split", onClick = onSplit)
        ToolbarActionItem(Icons.Filled.Tune, "Adjust", "effect_tool_adjust", onClick = onAdjust)
        ToolbarActionItem(Icons.Filled.SwapHoriz, "Replace", "effect_tool_replace", onClick = onReplace)
        ToolbarActionItem(Icons.Filled.FileCopy, "Duplicate", "effect_tool_duplicate", onClick = onDuplicate)
        ToolbarActionItem(Icons.Filled.Category, "Object", "effect_tool_object", onClick = onObject)
        ToolbarActionItem(Icons.Filled.DeleteOutline, "Delete", "effect_tool_delete", onClick = onDelete)
    }
}

@Composable
fun CapCutTextSelectedBar(
    showGraphsButton: Boolean,
    onBack: () -> Unit,
    onAddText: () -> Unit,
    onSplit: () -> Unit,
    onStyle: () -> Unit,
    onTransform: () -> Unit,
    onGraphs: () -> Unit,
    onAnimations: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CapCutContextualTrackBar(backTag = "text_menu_back", onBack = onBack, modifier = modifier) {
        if (showGraphsButton) {
            ToolbarActionItem(Icons.Filled.GraphicEq, "Graphs", "text_tool_graphs", highlightColor = CyberCyan, onClick = onGraphs)
        }
        ToolbarActionItem(Icons.Filled.OpenWith, "Transform", "text_tool_transform", highlightColor = CyberCyan, onClick = onTransform)
        ToolbarActionItem(Icons.Filled.ContentCut, "Split", "text_tool_split", onClick = onSplit)
        ToolbarActionItem(Icons.Filled.Edit, "Style", "text_tool_style", onClick = onStyle)
        ToolbarActionItem(Icons.Filled.TextFields, "Add text", "text_tool_add", onClick = onAddText)
        ToolbarActionItem(Icons.Filled.Animation, "Animations", "text_tool_animations", onClick = onAnimations)
        ToolbarActionItem(Icons.Filled.DeleteOutline, "Delete", "text_tool_delete", onClick = onDelete)
    }
}

@Composable
fun CapCutOverlaySelectedBar(
    onBack: () -> Unit,
    onOpenOverlayTools: () -> Unit,
    onTransform: () -> Unit,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CapCutContextualTrackBar(backTag = "overlay_menu_back", onBack = onBack, modifier = modifier) {
        ToolbarActionItem(Icons.Filled.OpenWith, "Transform", "overlay_tool_transform", highlightColor = CyberCyan, onClick = onTransform)
        ToolbarActionItem(Icons.Filled.Layers, "Overlay FX", "overlay_tool_edit", highlightColor = CyberCyan, onClick = onOpenOverlayTools)
        ToolbarActionItem(Icons.Filled.ContentCut, "Split", "overlay_tool_split", onClick = onSplit)
        ToolbarActionItem(Icons.Filled.FileCopy, "Duplicate", "overlay_tool_duplicate", onClick = onDuplicate)
        ToolbarActionItem(Icons.Filled.DeleteOutline, "Delete", "overlay_tool_delete", onClick = onDelete)
    }
}

@Composable
fun CapCutAudioSelectedBar(
    onBack: () -> Unit,
    onVolume: () -> Unit,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CapCutContextualTrackBar(backTag = "audio_menu_back", onBack = onBack, modifier = modifier) {
        ToolbarActionItem(Icons.AutoMirrored.Filled.VolumeUp, "Volume", "audio_tool_volume", onClick = onVolume)
        ToolbarActionItem(Icons.Filled.ContentCut, "Split", "audio_tool_split", onClick = onSplit)
        ToolbarActionItem(Icons.Filled.FileCopy, "Duplicate", "audio_tool_duplicate", onClick = onDuplicate)
        ToolbarActionItem(Icons.Filled.DeleteOutline, "Delete", "audio_tool_delete", onClick = onDelete)
    }
}

@Composable
private fun CapCutContextualTrackBar(
    backTag: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBackground)
            .navigationBarsPadding()
            .height(64.dp)
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianSurfaceContainer)
                .border(1.dp, OutlineSubtle, RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .testTag(backTag),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
                .padding(end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun ToolbarActionItem(
    icon: ImageVector,
    label: String,
    tag: String,
    highlightColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = highlightColor,
            modifier = Modifier.size(21.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = highlightColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
