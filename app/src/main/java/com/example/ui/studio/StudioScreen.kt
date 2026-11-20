package com.example.ui.studio

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.AspectPreset
import com.example.data.model.BlendStyle
import com.example.data.model.CanvasFitMode
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.model.LayerKind
import com.example.data.model.LayerShapeType
import com.example.data.model.TimelineLayer
import com.example.domain.video.ExportConfig
import com.example.domain.video.ExportUiState
import com.example.domain.video.UiState
import com.example.domain.video.VideoEffectsEngine
import com.example.domain.video.VideoMetadata
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LunaMint
import com.example.ui.theme.LunaMintContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineSubtle
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun StudioScreen(
    projectTitle: String,
    aspectPreset: AspectPreset,
    projectResolution: String,
    projectFps: Int,
    canvasFitMode: CanvasFitMode,
    selectedVideo: VideoMetadata?,
    activePreset: FfxPreset,
    layers: List<TimelineLayer>,
    selectedLayerId: String?,
    playheadMs: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    bypassEffects: Boolean,
    presetsState: UiState<List<FfxPreset>>,
    exportState: ExportUiState,
    onNavigateBackHome: () -> Unit,
    onUpdateProjectSettings: (String, AspectPreset, String, Int) -> Unit,
    onPickVideo: () -> Unit,
    onImportFfx: () -> Unit,
    onSelectPreset: (FfxPreset) -> Unit,
    onAddPresetLayer: (FfxPreset) -> Unit,
    onAddShapeLayer: (LayerShapeType) -> Unit,
    onAddTextLayer: (String) -> Unit,
    onSelectLayer: (String?) -> Unit,
    onToggleLayerVisibility: (String) -> Unit,
    onUpdatePlayhead: (Long) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDuplicateLayer: () -> Unit,
    onDeleteSelectedLayer: () -> Unit,
    onCycleCanvasFit: () -> Unit,
    onTrimLeft: () -> Unit,
    onSplit: () -> Unit,
    onTrimRight: () -> Unit,
    onToggleMute: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetColor: (Long) -> Unit,
    onToggleBorderShadow: () -> Unit,
    onSetBlendOpacity: (BlendStyle, Float) -> Unit,
    onUpdateTransform: (Int, Boolean) -> Unit,
    onUpdateShapeOrText: (LayerShapeType?, String?) -> Unit,
    onToggleBypass: () -> Unit,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onResetExportState: () -> Unit,
    onSaveCopyAs: (ExportRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val presetsList = (presetsState as? UiState.Success)?.data.orEmpty()
    val selectedLayer = layers.firstOrNull { it.id == selectedLayerId }
    val clipDurationMs = selectedVideo?.durationMs?.takeIf { it > 500L } ?: 15_000L

    var showExportSheet by remember { mutableStateOf(false) }
    var showProjectSettingsDialog by remember { mutableStateOf(false) }
    var showAddLayerDrawer by remember { mutableStateOf(false) }
    var activeToolPanel by remember { mutableStateOf(LayerToolPanel.NONE) }
    var viewportZoomed by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Bind picked video URI
    LaunchedEffect(selectedVideo?.uri) {
        val video = selectedVideo ?: return@LaunchedEffect
        exoPlayer.setMediaItem(MediaItem.fromUri(video.uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Re-apply GPU timeline effects whenever layers, bypass, or duration change
    LaunchedEffect(layers, bypassEffects, clipDurationMs, selectedVideo?.uri) {
        val effects = VideoEffectsEngine.buildPreviewEffectsForTimeline(
            layers = layers,
            clipDurationMs = clipDurationMs,
            bypass = bypassEffects,
        )
        exoPlayer.setVideoEffects(effects)
        val primaryVideo = layers.firstOrNull { it.kind == LayerKind.VIDEO }
        val speed = ((primaryVideo?.speedMultiplier ?: 1f) * activePreset.speedMultiplier.toFloat())
            .coerceIn(0.25f, 4.0f)
        exoPlayer.setPlaybackSpeed(if (bypassEffects) 1.0f else speed)
        exoPlayer.volume = if (primaryVideo?.isMuted == true) 0f else 1f
    }

    // Keep timeline playhead synced with ExoPlayer while playing
    LaunchedEffect(isPlaying, selectedVideo?.uri) {
        while (isPlaying && selectedVideo != null) {
            onUpdatePlayhead(exoPlayer.currentPosition.coerceIn(0L, clipDurationMs))
            delay(65L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Alight Motion-inspired Top Bar (Screenshot 1 & 3)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBackground)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (selectedLayer != null) {
                            onSelectLayer(null)
                        } else {
                            onNavigateBackHome()
                        }
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (selectedLayer != null) "Deselect layer" else "Back to Projects",
                        tint = Color.White,
                    )
                }

                Text(
                    text = selectedLayer?.title ?: projectTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (selectedLayer == null) TextDecoration.Underline else TextDecoration.None
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showProjectSettingsDialog = true }
                        .padding(horizontal = 6.dp),
                )

                if (selectedLayer != null) {
                    IconButton(onClick = onDuplicateLayer) {
                        Icon(Icons.Filled.FilterNone, contentDescription = "Duplicate layer", tint = Color.White)
                    }
                    if (selectedLayer.kind != LayerKind.VIDEO) {
                        IconButton(onClick = onDeleteSelectedLayer) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete layer", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    IconButton(
                        onClick = { showProjectSettingsDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Project Settings", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            if (selectedVideo == null) {
                                onPickVideo()
                            } else {
                                onResetExportState()
                                showExportSheet = true
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_video_button"),
                    ) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Export Rendered Video", tint = LunaMint)
                    }
                }
            }

            // 2. Video Preview Canvas Viewport (Screenshots 1 & 3)
            val displayRatio = if (viewportZoomed) 1f else aspectPreset.ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
                    .background(Color(0xFF040509)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(displayRatio.coerceIn(0.55f, 1.85f), matchHeightConstraintsFirst = true)
                        .padding(if (selectedLayer != null) 4.dp else 0.dp)
                        .background(Color(0xFF0A0C14)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedVideo != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = when (canvasFitMode) {
                                        CanvasFitMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        CanvasFitMode.FILL, CanvasFitMode.WIDE_CINEMA -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    }
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    player = exoPlayer
                                }
                            },
                            update = { pv ->
                                pv.resizeMode = when (canvasFitMode) {
                                    CanvasFitMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    CanvasFitMode.FILL, CanvasFitMode.WIDE_CINEMA -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(ObsidianSurfaceContainer)
                                .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
                                .clickable(onClick = onPickVideo)
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                                .testTag("pick_video_button"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = null,
                                tint = LunaMint,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Import Video Clip", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                text = "Tap to load video for .ffx & .cube grading",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Selection corner handles overlay (Screenshot 3)
                    if (selectedLayer != null) {
                        SelectionBoundingHandles(modifier = Modifier.fillMaxSize())
                    }
                }

                // Top-left Zoom toggle (Screenshots 1 & 3)
                IconButton(
                    onClick = { viewportZoomed = !viewportZoomed },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianSurface.copy(alpha = 0.72f))
                        .size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomIn,
                        contentDescription = "Toggle Canvas Zoom",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Top-right Bypass / Original Compare pill
                Surface(
                    color = if (bypassEffects) LunaMintContainer else ObsidianSurface.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clickable(onClick = onToggleBypass)
                        .testTag("toggle_bypass_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Compare,
                            contentDescription = "Toggle Original Bypass",
                            tint = if (bypassEffects) LunaMint else Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (bypassEffects) "ORIGINAL" else activePreset.presetFormat,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bypassEffects) LunaMint else CyberCyan,
                        )
                    }
                }
            }

            // 3. Transport & Quick-Action Toolbar (Screenshots 1 & 3)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.testTag("toolbar_undo")) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    )
                }
                IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.testTag("toolbar_redo")) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    )
                }
                IconButton(
                    onClick = {
                        onUpdatePlayhead(0L)
                        if (selectedVideo != null) exoPlayer.seekTo(0L)
                    },
                ) {
                    Icon(Icons.Filled.FastRewind, contentDescription = "Jump to Start", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        if (selectedVideo == null) {
                            onPickVideo()
                        } else if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier.testTag("play_pause_button"),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(
                    onClick = {
                        onUpdatePlayhead(clipDurationMs)
                        if (selectedVideo != null) exoPlayer.seekTo(clipDurationMs)
                    },
                ) {
                    Icon(Icons.Filled.FastForward, contentDescription = "Jump to End", tint = Color.White)
                }
                IconButton(onClick = onDuplicateLayer, modifier = Modifier.testTag("toolbar_duplicate")) {
                    Icon(Icons.Filled.FileCopy, contentDescription = "Duplicate Layer", tint = Color.White)
                }
                IconButton(onClick = onCycleCanvasFit, modifier = Modifier.testTag("toolbar_fit")) {
                    Icon(Icons.Filled.CropFree, contentDescription = "Cycle Fit Fill Frame", tint = Color.White)
                }
            }

            // 4. Multi-Track Timeline Section (Screenshots 1 & 3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (selectedLayer != null || showAddLayerDrawer) 0.22f else 0.58f)
                    .background(ObsidianBackground)
                    .pointerInput(clipDurationMs) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            val deltaMs = (-dragAmount * 14f).toLong()
                            val next = (playheadMs + deltaMs).coerceIn(0L, clipDurationMs)
                            onUpdatePlayhead(next)
                            if (selectedVideo != null) {
                                exoPlayer.seekTo(next)
                            }
                        }
                    },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Tick Ruler + Timecode Badge (Screenshots 1 & 3)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(ObsidianSurface),
                    ) {
                        TimelineRulerCanvas(
                            playheadMs = playheadMs,
                            clipDurationMs = clipDurationMs,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Surface(
                            color = ObsidianSurfaceHigh,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            Text(
                                text = formatTimecode(playheadMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // Tracks List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 6.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(layers, key = { it.id }) { layer ->
                            TimelineTrackRow(
                                layer = layer,
                                clipDurationMs = clipDurationMs,
                                isSelected = layer.id == selectedLayerId,
                                onToggleVisibility = { onToggleLayerVisibility(layer.id) },
                                onSelect = { onSelectLayer(layer.id) },
                            )
                        }
                    }
                }

                // Centered White Playhead Vertical Line
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(1.6.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.9f)),
                )
            }

            // 5. Contextual Bottom Area: Selected Layer Tool Grid (Screenshot 3) OR Add Layer Drawer (Screenshot 4)
            if (showAddLayerDrawer) {
                AddLayerDrawer(
                    presets = presetsList,
                    onClose = { showAddLayerDrawer = false },
                    onPickVideo = onPickVideo,
                    onImportFfx = onImportFfx,
                    onAddPresetLayer = onAddPresetLayer,
                    onAddShapeLayer = onAddShapeLayer,
                    onAddTextLayer = onAddTextLayer,
                )
            } else if (selectedLayer != null) {
                SelectedLayerToolGrid(
                    layer = selectedLayer,
                    onOpenPanel = { panel -> activeToolPanel = panel },
                    onTrimLeft = onTrimLeft,
                    onSplit = onSplit,
                    onTrimRight = onTrimRight,
                    onToggleMute = onToggleMute,
                    onToggleBorderShadow = onToggleBorderShadow,
                )
            }
        }

        // Mint-green '+' FAB on bottom right when in timeline overview mode (Screenshot 1)
        if (selectedLayer == null && !showAddLayerDrawer) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selectedVideo != null) {
                    Surface(
                        color = ObsidianSurfaceContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .border(1.dp, OutlineSubtle, RoundedCornerShape(14.dp))
                            .clickable {
                                onResetExportState()
                                showExportSheet = true
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Render MP4", style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { showAddLayerDrawer = true },
                    shape = CircleShape,
                    containerColor = ObsidianSurface,
                    contentColor = LunaMint,
                    modifier = Modifier
                        .size(56.dp)
                        .border(2.5.dp, LunaMint, CircleShape)
                        .testTag("add_layer_fab"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Layer or Preset",
                        tint = LunaMint,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }

    if (selectedLayer != null && activeToolPanel != LayerToolPanel.NONE) {
        LayerToolPanelDialog(
            panel = activeToolPanel,
            layer = selectedLayer,
            presets = presetsList,
            onDismiss = { activeToolPanel = LayerToolPanel.NONE },
            onSetSpeed = onSetSpeed,
            onSetColor = onSetColor,
            onSetBlendOpacity = onSetBlendOpacity,
            onUpdateTransform = onUpdateTransform,
            onUpdateShapeOrText = onUpdateShapeOrText,
            onApplyPresetToLayer = { preset -> onSelectPreset(preset) },
            onImportFfx = onImportFfx,
        )
    }

    if (showProjectSettingsDialog) {
        ProjectSettingsDialog(
            initialTitle = projectTitle,
            initialAspect = aspectPreset,
            initialResolution = projectResolution,
            initialFps = projectFps,
            onDismiss = { showProjectSettingsDialog = false },
            onSave = { t, a, r, f ->
                onUpdateProjectSettings(t, a, r, f)
                showProjectSettingsDialog = false
            },
        )
    }

    if (showExportSheet && selectedVideo != null) {
        ExportModalSheet(
            video = selectedVideo,
            preset = activePreset,
            exportState = exportState,
            onStartExport = onStartExport,
            onCancelExport = onCancelExport,
            onSaveCopyAs = onSaveCopyAs,
            onDismiss = { showExportSheet = false },
        )
    }
}

@Composable
private fun TimelineTrackRow(
    layer: TimelineLayer,
    clipDurationMs: Long,
    isSelected: Boolean,
    onToggleVisibility: () -> Unit,
    onSelect: () -> Unit,
) {
    val barColor = Color(layer.accentColorHex)
    val safeDur = clipDurationMs.coerceAtLeast(1_000L).toFloat()
    val startFraction = (layer.startMs / safeDur).coerceIn(0f, 0.9f)
    val endFraction = (layer.endMs / safeDur).coerceIn(startFraction + 0.08f, 1f)
    val widthFraction = (endFraction - startFraction).coerceIn(0.12f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left sticky eye visibility + layer badge (Screenshots 1 & 3)
        Row(
            modifier = Modifier
                .width(68.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(ObsidianSurfaceContainer)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = if (layer.isVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = if (layer.isVisible) "Hide layer" else "Show layer",
                tint = if (layer.isVisible) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onToggleVisibility),
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor.copy(alpha = if (layer.isVisible) 1f else 0.35f)),
            )
        }

        if (isSelected) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianSurfaceHigh)
                    .clickable(onClick = onSelect),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Deselect layer", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Track timeline bar positioned by [startMs..endMs]
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .offset { IntOffset((startFraction * 180f).roundToInt(), 0) }
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor.copy(alpha = if (layer.isVisible) 0.92f else 0.35f))
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable(onClick = onSelect)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = layer.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (layer.kind == LayerKind.VIDEO) Color.White else ObsidianBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = null,
                        tint = if (layer.kind == LayerKind.VIDEO) Color.White.copy(alpha = 0.7f) else ObsidianBackground.copy(alpha = 0.75f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRulerCanvas(
    playheadMs: Long,
    clipDurationMs: Long,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centerX = size.width * 0.5f
        val offsetRatio = (playheadMs.toFloat() / clipDurationMs.coerceAtLeast(1L).toFloat())
        val shift = -(offsetRatio * size.width * 0.4f)
        val tickStep = 14.dp.toPx()
        var idx = -20
        while (idx <= 45) {
            val x = centerX + shift + idx * tickStep
            if (x in 0f..size.width) {
                val isMajor = idx % 5 == 0
                drawLine(
                    color = Color.White.copy(alpha = if (isMajor) 0.65f else 0.25f),
                    start = Offset(x, size.height - if (isMajor) 14.dp.toPx() else 7.dp.toPx()),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            idx++
        }
    }
}

@Composable
private fun SelectionBoundingHandles(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeColor = Color.White.copy(alpha = 0.9f)
        drawRect(
            color = strokeColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )
        val r = 4.dp.toPx()
        val points = listOf(
            Offset(0f, 0f),
            Offset(size.width * 0.5f, 0f),
            Offset(size.width, 0f),
            Offset(0f, size.height * 0.5f),
            Offset(size.width, size.height * 0.5f),
            Offset(0f, size.height),
            Offset(size.width * 0.5f, size.height),
            Offset(size.width, size.height),
        )
        points.forEach { p ->
            drawCircle(color = Color.White, radius = r, center = p)
        }
    }
}

@Composable
private fun ProjectSettingsDialog(
    initialTitle: String,
    initialAspect: AspectPreset,
    initialResolution: String,
    initialFps: Int,
    onDismiss: () -> Unit,
    onSave: (String, AspectPreset, String, Int) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var aspect by remember { mutableStateOf(initialAspect) }
    var res by remember { mutableStateOf(initialResolution) }
    var fps by remember { mutableIntStateOf(initialFps) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceHigh,
        title = { Text("Project Settings", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Canvas Ratio", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AspectPreset.entries.forEach { ratio ->
                        FilterChip(
                            selected = aspect == ratio,
                            onClick = { aspect = ratio },
                            label = { Text(ratio.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LunaMintContainer,
                                selectedLabelColor = LunaMint,
                            ),
                        )
                    }
                }
                Text("Preview & Frame Rate", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("720p", "1080p", "1440p", "4K").forEach { r ->
                        FilterChip(selected = res == r, onClick = { res = r }, label = { Text(r, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60).forEach { f ->
                        FilterChip(selected = fps == f, onClick = { fps = f }, label = { Text("${f}fps") })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, aspect, res, fps) },
                colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatTimecode(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val frames = ((safe % 1000L) * 30L / 1000L).coerceIn(0L, 29L)
    return String.format(Locale.US, "%02d:%02d:%02d", mins, secs, frames)
}
