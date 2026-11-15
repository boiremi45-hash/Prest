package com.example.ui.studio

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.FfxPreset
import com.example.domain.video.ExportConfig
import com.example.domain.video.ExportUiState
import com.example.domain.video.UiState
import com.example.domain.video.VideoEffectsEngine
import com.example.domain.video.VideoMetadata
import com.example.data.model.ExportRecord
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.DeepVioletContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineSubtle
import kotlinx.coroutines.delay
import java.util.Locale

private enum class InspectorGroup(val label: String) {
    COLOR("Color"),
    OPTICS("Optics"),
    MOTION("Motion"),
}

@OptIn(UnstableApi::class)
@Composable
fun StudioScreen(
    selectedVideo: VideoMetadata?,
    activePreset: FfxPreset,
    bypassEffects: Boolean,
    presetsState: UiState<List<FfxPreset>>,
    exportState: ExportUiState,
    onPickVideo: () -> Unit,
    onImportFfx: () -> Unit,
    onSelectPreset: (FfxPreset) -> Unit,
    onToggleBypass: () -> Unit,
    onUpdatePreset: ((FfxPreset) -> FfxPreset) -> Unit,
    onResetPreset: () -> Unit,
    onSavePresetToCloud: (asNew: Boolean, customName: String?) -> Unit,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onResetExportState: () -> Unit,
    onSaveCopyAs: (ExportRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var inspectorGroup by remember { mutableStateOf(InspectorGroup.COLOR) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Poll playhead position cleanly while playing
    LaunchedEffect(exoPlayer, selectedVideo) {
        while (true) {
            if (selectedVideo != null) {
                currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration
                }
            }
            delay(150L)
        }
    }

    // Apply video URI + .ffx GPU effects cleanly with short debounce during slider drags
    LaunchedEffect(selectedVideo?.uri, activePreset, bypassEffects) {
        val video = selectedVideo ?: return@LaunchedEffect
        delay(140L)
        val resumePos = if (exoPlayer.playbackState != Player.STATE_IDLE) {
            exoPlayer.currentPosition.coerceAtLeast(0L)
        } else {
            0L
        }
        val wasPlaying = if (exoPlayer.playbackState == Player.STATE_IDLE) true else exoPlayer.playWhenReady
        val effects = VideoEffectsEngine.buildPreviewVideoEffects(activePreset, bypassEffects)
        val speed = if (bypassEffects) 1.0f else activePreset.speedMultiplier.toFloat().coerceIn(0.25f, 4.0f)

        exoPlayer.stop()
        exoPlayer.setVideoEffects(effects)
        exoPlayer.setMediaItem(MediaItem.fromUri(video.uri), resumePos)
        exoPlayer.setPlaybackSpeed(speed)
        exoPlayer.playWhenReady = wasPlaying
        exoPlayer.prepare()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Top Studio Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PresetFX Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = activePreset.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickVideo,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("pick_video_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.pick_video), style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = {
                        onResetExportState()
                        showExportSheet = true
                    },
                    enabled = selectedVideo != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("export_video_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.export_video), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live GPU Video Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurface)
                .border(1.dp, OutlineSubtle, RoundedCornerShape(18.dp)),
        ) {
            if (selectedVideo == null) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero_1794749939889),
                    contentDescription = "Select a video to start editing",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianBackground.copy(alpha = 0.25f),
                                    ObsidianBackground.copy(alpha = 0.85f),
                                )
                            )
                        )
                        .padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Load a Video to Preview .ffx Presets",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Real-time GPU effects preview & 4K/1080p local MP4 export",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onPickVideo,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                modifier = Modifier.testTag("viewport_pick_video_button"),
                            ) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Video")
                            }
                            OutlinedButton(onClick = onImportFfx) {
                                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import .ffx")
                            }
                        }
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { view ->
                        if (view.player !== exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Active FX / Bypass badge overlay
                Surface(
                    color = ObsidianBackground.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .border(1.dp, if (bypassEffects) OutlineSubtle else CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (bypassEffects) MaterialTheme.colorScheme.onSurfaceVariant else CyberCyan),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (bypassEffects) "ORIGINAL (FX BYPASSED)" else "FX: ${activePreset.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bypassEffects) MaterialTheme.colorScheme.onSurfaceVariant else CyberCyan,
                        )
                    }
                }

                Surface(
                    color = ObsidianBackground.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                ) {
                    Text(
                        text = "${selectedVideo.width}x${selectedVideo.height}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }

        // Transport Controls Bar (when video is loaded)
        if (selectedVideo != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurfaceContainer)
                    .border(1.dp, OutlineSubtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                        tint = CyberCyan,
                    )
                }

                Slider(
                    value = if (durationMs > 0) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                    onValueChange = { fraction ->
                        if (durationMs > 0) {
                            val target = (fraction * durationMs).toLong()
                            currentPosMs = target
                            exoPlayer.seekTo(target)
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = CyberCyan,
                        activeTrackColor = CyberCyan,
                        inactiveTrackColor = ObsidianSurfaceHigh,
                    ),
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = "${formatTimecode(currentPosMs)}/${formatTimecode(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )

                IconButton(
                    onClick = onToggleBypass,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Compare,
                        contentDescription = "Toggle Original vs FX",
                        tint = if (bypassEffects) MaterialTheme.colorScheme.onSurfaceVariant else ElectricViolet,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick .ffx Preset Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Quick .ffx Presets",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(onClick = onImportFfx) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyberCyan)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Import .ffx", color = CyberCyan, style = MaterialTheme.typography.labelLarge)
            }
        }

        val cloudPresets = (presetsState as? UiState.Success)?.data.orEmpty()
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(cloudPresets, key = { it.id }) { preset ->
                val isSelected = preset.id == activePreset.id
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) DeepCyanContainer else ObsidianSurfaceContainer)
                        .border(
                            1.2.dp,
                            if (isSelected) CyberCyan else OutlineSubtle,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelectPreset(preset) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoFixHigh,
                        contentDescription = null,
                        tint = if (isSelected) CyberCyan else ElectricViolet,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) CyberCyan else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Parameter Inspector Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurfaceContainer)
                .border(1.dp, OutlineSubtle, RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = ".ffx Effect Stack",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onResetPreset,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset sliders",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onSavePresetToCloud(false, null) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Update preset in cloud",
                            tint = CyberCyan,
                        )
                    }
                    IconButton(
                        onClick = { showSaveAsDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddCircleOutline,
                            contentDescription = "Save as new cloud .ffx preset",
                            tint = ElectricViolet,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InspectorGroup.entries.forEach { group ->
                    val selected = inspectorGroup == group
                    FilterChip(
                        selected = selected,
                        onClick = { inspectorGroup = group },
                        label = {
                            Text(
                                text = group.label,
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

            Spacer(modifier = Modifier.height(10.dp))

            when (inspectorGroup) {
                InspectorGroup.COLOR -> {
                    FxParameterSlider(
                        label = "Exposure",
                        value = activePreset.exposure.toFloat(),
                        range = -0.8f..0.8f,
                        valueText = String.format(Locale.US, "%+.2f", activePreset.exposure),
                        onValueChange = { v -> onUpdatePreset { it.copy(exposure = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Contrast",
                        value = activePreset.contrast.toFloat(),
                        range = -0.8f..0.8f,
                        valueText = String.format(Locale.US, "%+.2f", activePreset.contrast),
                        onValueChange = { v -> onUpdatePreset { it.copy(contrast = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Saturation",
                        value = activePreset.saturation.toFloat(),
                        range = -100f..100f,
                        valueText = String.format(Locale.US, "%+.0f", activePreset.saturation),
                        onValueChange = { v -> onUpdatePreset { it.copy(saturation = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Hue Shift",
                        value = activePreset.hueShift.toFloat(),
                        range = -180f..180f,
                        valueText = String.format(Locale.US, "%+.0f°", activePreset.hueShift),
                        onValueChange = { v -> onUpdatePreset { it.copy(hueShift = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Temperature",
                        value = activePreset.temperature.toFloat(),
                        range = -1f..1f,
                        valueText = String.format(Locale.US, "%+.2f", activePreset.temperature),
                        onValueChange = { v -> onUpdatePreset { it.copy(temperature = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Tint",
                        value = activePreset.tint.toFloat(),
                        range = -1f..1f,
                        valueText = String.format(Locale.US, "%+.2f", activePreset.tint),
                        onValueChange = { v -> onUpdatePreset { it.copy(tint = v.toDouble()) } },
                    )
                }

                InspectorGroup.OPTICS -> {
                    FxParameterSlider(
                        label = "Halation Glow",
                        value = activePreset.glowIntensity.toFloat(),
                        range = 0f..1f,
                        valueText = "${(activePreset.glowIntensity * 100).toInt()}%",
                        onValueChange = { v -> onUpdatePreset { it.copy(glowIntensity = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Optical Vignette",
                        value = activePreset.vignetteIntensity.toFloat(),
                        range = 0f..1f,
                        valueText = "${(activePreset.vignetteIntensity * 100).toInt()}%",
                        onValueChange = { v -> onUpdatePreset { it.copy(vignetteIntensity = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Film Grain (16mm)",
                        value = activePreset.grainAmount.toFloat(),
                        range = 0f..1f,
                        valueText = "${(activePreset.grainAmount * 100).toInt()}%",
                        onValueChange = { v -> onUpdatePreset { it.copy(grainAmount = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Anamorphic Letterbox",
                        value = activePreset.letterboxRatio.toFloat(),
                        range = 0f..0.35f,
                        valueText = "${(activePreset.letterboxRatio * 100).toInt()}%",
                        onValueChange = { v -> onUpdatePreset { it.copy(letterboxRatio = v.toDouble()) } },
                    )
                }

                InspectorGroup.MOTION -> {
                    FxParameterSlider(
                        label = "Zoom Pulse",
                        value = activePreset.zoomPulse.toFloat(),
                        range = 0f..1f,
                        valueText = "${(activePreset.zoomPulse * 100).toInt()}%",
                        onValueChange = { v -> onUpdatePreset { it.copy(zoomPulse = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Camera Shake (Wiggle)",
                        value = activePreset.shakeIntensity.toFloat(),
                        range = 0f..1f,
                        valueText = "${(activePreset.shakeIntensity * 100).toInt()}%",
                        onValueChange = { v -> onUpdatePreset { it.copy(shakeIntensity = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Rotation Angle",
                        value = activePreset.rotationDegrees.toFloat(),
                        range = -180f..180f,
                        valueText = String.format(Locale.US, "%+.0f°", activePreset.rotationDegrees),
                        onValueChange = { v -> onUpdatePreset { it.copy(rotationDegrees = v.toDouble()) } },
                    )
                    FxParameterSlider(
                        label = "Timewarp Speed",
                        value = activePreset.speedMultiplier.toFloat(),
                        range = 0.25f..2.5f,
                        valueText = String.format(Locale.US, "%.2fx", activePreset.speedMultiplier),
                        onValueChange = { v -> onUpdatePreset { it.copy(speedMultiplier = v.toDouble()) } },
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = { onSavePresetToCloud(false, null) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepVioletContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.save_to_cloud), style = MaterialTheme.typography.labelLarge)
            }
        }
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

    if (showSaveAsDialog) {
        var newName by remember { mutableStateOf("${activePreset.name} Remix") }
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            containerColor = ObsidianSurfaceHigh,
            title = { Text("Save as New Cloud .ffx Preset", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePresetToCloud(true, newName)
                        showSaveAsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                ) {
                    Text("Save to Cloud")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun FxParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = CyberCyan,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ElectricViolet,
                activeTrackColor = ElectricViolet,
                inactiveTrackColor = ObsidianSurfaceHigh,
            ),
        )
    }
}

private fun formatTimecode(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    val tenths = (ms % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
}
