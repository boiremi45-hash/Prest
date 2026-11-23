package com.example.ui.studio

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.AdjustSettings
import com.example.data.model.AnimationSettings
import com.example.data.model.AspectPreset
import com.example.data.model.AudioTrackItem
import com.example.data.model.CanvasFitMode
import com.example.data.model.CapCutEffectType
import com.example.data.model.ClipTimelineSpan
import com.example.data.model.CropSettings
import com.example.data.model.EasingGraph
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.model.KeyframeEvaluator
import com.example.data.model.MaskSettings
import com.example.data.model.MotionBlurSettings
import com.example.data.model.OverlayTrackItem
import com.example.data.model.SpeedSettings
import com.example.data.model.StabilizeMode
import com.example.data.model.TextKeyframeEvaluator
import com.example.data.model.TextTrackItem
import com.example.data.model.TransformSettings
import com.example.data.model.TransitionType
import com.example.data.model.VideoClipSegment
import com.example.data.model.VolumeKeyframeEvaluator
import com.example.data.model.findActiveSpanIndex
import com.example.data.model.isTimelineItemActiveAt
import com.example.domain.video.ExportConfig
import com.example.domain.video.ExportUiState
import com.example.domain.video.LunaShaderParams
import com.example.domain.video.UiState
import com.example.domain.video.VideoEffectsEngine
import com.example.domain.video.VideoMetadata
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface PreviewHitTarget {
    data class Text(val id: String) : PreviewHitTarget
    data class Overlay(val id: String) : PreviewHitTarget
    data class Video(val clipId: String) : PreviewHitTarget
}

@OptIn(UnstableApi::class)
@Composable
fun StudioScreen(
    snapshot: TimelineSnapshot,
    spans: List<ClipTimelineSpan>,
    aspectPreset: AspectPreset,
    projectResolution: String,
    projectFps: Int,
    canvasFitMode: CanvasFitMode,
    selectedVideo: VideoMetadata?,
    filmstrip: List<ImageBitmap>,
    selectedClipId: String?,
    selectedOverlayId: String?,
    selectedEffectId: String?,
    selectedTextId: String?,
    selectedAudioId: String?,
    keyframeModeEnabled: Boolean,
    selectedKeyframeId: String?,
    selectedVolumeKeyframeId: String?,
    playheadMs: Long,
    totalDurationMs: Long,
    timelineZoom: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    bypassEffects: Boolean,
    presetsState: UiState<List<FfxPreset>>,
    exportState: ExportUiState,
    onNavigateBackHome: () -> Unit,
    onPickVideo: () -> Unit,
    onAppendVideoClip: () -> Unit,
    onReplaceClipVideo: () -> Unit,
    onPickOverlayMedia: () -> Unit,
    onPickAudioFile: () -> Unit,
    onImportFfxOrCube: () -> Unit,
    onSelectClip: (String?) -> Unit,
    onSelectOverlay: (String?) -> Unit,
    onSelectEffectLayer: (String?) -> Unit,
    onSelectText: (String?) -> Unit,
    onSelectAudio: (String?) -> Unit,
    onSelectClipKeyframe: (String, String) -> Unit,
    onSelectTextKeyframe: (String, String) -> Unit,
    onSelectClipVolumeKeyframe: (String, String) -> Unit,
    onSelectAudioVolumeKeyframe: (String, String) -> Unit,
    onClearAllSelections: () -> Unit,
    onCommitHistory: () -> Unit,
    onUpdatePlayhead: (Long) -> Unit,
    onUpdateZoom: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSplitClip: () -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onMoveOverlay: (String, Long) -> Unit,
    onMoveEffect: (String, Long) -> Unit,
    onMoveText: (String, Long) -> Unit,
    onMoveAudio: (String, Long) -> Unit,
    onTrimClipEdge: (String, Boolean, Long) -> Unit,
    onTrimOverlayEdge: (String, Boolean, Long) -> Unit,
    onTrimEffectEdge: (String, Boolean, Long) -> Unit,
    onTrimTextEdge: (String, Boolean, Long) -> Unit,
    onTrimAudioEdge: (String, Boolean, Long) -> Unit,
    onDuplicateClip: () -> Unit,
    onDeleteClip: () -> Unit,
    onFreezeClip: () -> Unit,
    onReverseClip: () -> Unit,
    onMoveClipToOverlay: () -> Unit,
    onToggleMuteClip: (String) -> Unit,
    onToggleBeat: () -> Unit,
    onUpdateVolume: (Int, Boolean, Boolean) -> Unit,
    onAddClipVolumeKeyframe: () -> Unit,
    onDeleteClipVolumeKeyframe: () -> Unit,
    onDeleteAllClipVolumeKeyframes: () -> Unit,
    onUpdateSpeed: (SpeedSettings) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateTransform: (TransformSettings) -> Unit,
    onFillCanvas: () -> Unit,
    onResetTransform: () -> Unit,
    onApplyPreviewVideoGesture: (Float, Float, Float, Float) -> Unit,
    onUpdateCrop: (CropSettings) -> Unit,
    onUpdateMask: (MaskSettings) -> Unit,
    onUpdateMotionBlur: (MotionBlurSettings) -> Unit,
    onUpdateStabilize: (StabilizeMode) -> Unit,
    onUpdateAnimation: (AnimationSettings) -> Unit,
    onToggleKeyframeMode: () -> Unit,
    onAddTransformKeyframe: () -> Unit,
    onDeleteTransformKeyframe: () -> Unit,
    onDeleteAllTransformKeyframes: () -> Unit,
    onToggleKeyframe: () -> Unit,
    onSetSegmentGraph: (String, EasingGraph) -> Unit,
    onApplyEffect: (CapCutEffectType, Float) -> Unit,
    onSplitEffect: (String) -> Unit,
    onDuplicateEffect: (String) -> Unit,
    onSetEffectObjectTarget: (String, Boolean) -> Unit,
    onDeleteEffect: (String) -> Unit,
    onUpdateTransition: (String, TransitionType, Float, Boolean) -> Unit,
    onUpdateAdjust: (AdjustSettings) -> Unit,
    onAutoAdjust: () -> Unit,
    onResetAdjust: () -> Unit,
    onSelectPreset: (FfxPreset) -> Unit,
    onChangeFilterIntensity: (Float) -> Unit,
    onUpdateOverlay: (OverlayTrackItem) -> Unit,
    onSplitOverlay: (String) -> Unit,
    onDuplicateOverlay: (String) -> Unit,
    onDeleteOverlay: (String) -> Unit,
    onAddTextOrCaption: (String, Boolean) -> Unit,
    onUpdateTextTrack: (TextTrackItem) -> Unit,
    onUpdateTextTransform: (Float, Float, Float, Float, Float, Boolean) -> Unit,
    onSplitText: (String) -> Unit,
    onGenerateAutoCaptions: () -> Unit,
    onDeleteText: (String) -> Unit,
    onUpdateAudioVolume: (String, Int, Boolean) -> Unit,
    onAddAudioVolumeKeyframe: (String) -> Unit,
    onDeleteAudioVolumeKeyframe: (String) -> Unit,
    onDeleteAllAudioVolumeKeyframes: (String) -> Unit,
    onSplitAudio: (String) -> Unit,
    onDuplicateAudio: (String) -> Unit,
    onDeleteAudio: (String) -> Unit,
    onToggleEndingCard: () -> Unit,
    onSelectAspect: (AspectPreset) -> Unit,
    onSelectBg: (Long) -> Unit,
    onCycleCanvasFit: () -> Unit,
    onToggleBypass: () -> Unit,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onResetExportState: () -> Unit,
    onSaveCopyAs: (ExportRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val presetsList = (presetsState as? UiState.Success)?.data.orEmpty()

    val playingSpanIdx = findActiveSpanIndex(spans, playheadMs)
    val hasActiveClipAtPlayhead = playingSpanIdx >= 0
    val previewSpan = if (playingSpanIdx >= 0) spans[playingSpanIdx] else spans.firstOrNull()
    val toolbarSpan = (selectedClipId?.let { id -> spans.firstOrNull { it.clip.id == id } }
        ?: previewSpan
        ?: spans.first())
    val activeClip: VideoClipSegment = toolbarSpan.clip
    val previewClip: VideoClipSegment = previewSpan?.clip ?: activeClip
    val localPlayheadMs = (playheadMs - toolbarSpan.startMs).coerceIn(0L, activeClip.timelineDurationMs())
    val previewLocalMs = (playheadMs - (previewSpan?.startMs ?: 0L)).coerceIn(0L, previewClip.timelineDurationMs())
    val previewEvalTransform = remember(
        previewClip.keyframes,
        previewClip.transform,
        previewClip.opacity,
        previewLocalMs,
        snapshot.adjust,
        snapshot.filterIntensity,
    ) {
        KeyframeEvaluator.evaluate(
            keyframes = previewClip.keyframes,
            localTimeMs = previewLocalMs,
            baseTransform = previewClip.transform,
            baseOpacity = previewClip.opacity,
            baseAdjust = snapshot.adjust,
            baseFilterIntensity = snapshot.filterIntensity,
        )
    }

    val selectedTextTrack = remember(snapshot.textTracks, selectedTextId) {
        snapshot.textTracks.firstOrNull { it.id == selectedTextId }
    }
    val selectedTextLocalMs = selectedTextTrack?.let {
        (playheadMs - it.startMs).coerceIn(0L, (it.endMs - it.startMs).coerceAtLeast(1L))
    } ?: 0L

    val isOnKeyframe = remember(activeClip.keyframes, localPlayheadMs, selectedTextTrack, selectedTextLocalMs, selectedKeyframeId) {
        if (selectedTextTrack != null) {
            selectedTextTrack.keyframes.any { it.id == selectedKeyframeId || abs(it.timeMs - selectedTextLocalMs) < 140L }
        } else {
            activeClip.keyframes.any { it.id == selectedKeyframeId || abs(it.timeMs - localPlayheadMs) < 140L }
        }
    }
    val betweenKeyframesSeg: Pair<String, EasingGraph>? = remember(
        activeClip.keyframes,
        localPlayheadMs,
        selectedTextTrack,
        selectedTextLocalMs,
    ) {
        if (selectedTextTrack != null) {
            TextKeyframeEvaluator.surroundingSegment(selectedTextTrack.keyframes, selectedTextLocalMs)
                ?.first?.let { it.id to it.easingToNext }
        } else {
            KeyframeEvaluator.surroundingSegment(activeClip.keyframes, localPlayheadMs)
                ?.first?.let { it.id to it.easingToNext }
        }
    }

    var activeSheet by remember { mutableStateOf(ActiveEditorSheet.NONE) }
    var editingTransitionAfterId by remember { mutableStateOf<String?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    val latestPlayheadMs by rememberUpdatedState(playheadMs)
    val latestSpans by rememberUpdatedState(spans)
    val latestTotalDurationMs by rememberUpdatedState(totalDurationMs)
    val latestOnUpdatePlayhead by rememberUpdatedState(onUpdatePlayhead)

    val hasAnySelection = selectedClipId != null ||
        selectedOverlayId != null ||
        selectedEffectId != null ||
        selectedTextId != null ||
        selectedAudioId != null
    BackHandler(enabled = activeSheet != ActiveEditorSheet.NONE || hasAnySelection) {
        if (activeSheet != ActiveEditorSheet.NONE) {
            activeSheet = ActiveEditorSheet.NONE
        } else {
            onClearAllSelections()
        }
    }

    // Compute cumulative ExoPlayer media offset so split/moved clips align GL effect timestamps
    val exoCumulativeStarts = remember(spans) {
        var acc = 0L
        spans.map { sp ->
            val out = acc
            acc += (sp.endMs - sp.startMs).coerceAtLeast(0L)
            out
        }
    }
    val activeSpanIdxForShader = playingSpanIdx.coerceIn(0, spans.lastIndex.coerceAtLeast(0))
    val currentTimelineOffsetMs = if (spans.isNotEmpty()) {
        spans[activeSpanIdxForShader].startMs - exoCumulativeStarts.getOrElse(activeSpanIdxForShader) { 0L }
    } else 0L

    // Live mutable reference so OpenGL shader uniforms update at 60fps
    val shaderParamsRef = remember { mutableStateOf(LunaShaderParams()) }
    val transitionWindows = remember(spans, snapshot.transitions) {
        VideoEffectsEngine.buildTransitionWindows(spans, snapshot.transitions)
    }
    LaunchedEffect(snapshot, previewClip, currentTimelineOffsetMs, bypassEffects) {
        shaderParamsRef.value = VideoEffectsEngine.makeShaderParams(
            clip = previewClip,
            adjust = snapshot.adjust,
            filterPreset = snapshot.activePreset,
            filterIntensity = snapshot.filterIntensity,
            effectLayers = snapshot.effectLayers,
            transitionWindows = transitionWindows,
            bypass = bypassEffects,
            timelineOffsetMs = currentTimelineOffsetMs,
        )
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }
    val audioPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(exoPlayer, audioPlayer) {
        onDispose {
            exoPlayer.release()
            audioPlayer.release()
        }
    }

    // Bind media items in timeline order when video/clip trims or splits change, restoring exact playhead position
    LaunchedEffect(selectedVideo?.uri, spans) {
        val primaryUri = selectedVideo?.uri ?: return@LaunchedEffect
        val items = spans.map { sp ->
            val clip = sp.clip
            val u = clip.sourceUriString?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() } ?: primaryUri
            val mb = MediaItem.Builder().setUri(u)
            if (clip.trimStartMs > 30L || clip.trimEndMs < clip.sourceDurationMs - 30L) {
                mb.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 250L))
                        .build()
                )
            }
            mb.build()
        }
        val wasPlaying = isPlaying
        exoPlayer.setMediaItems(items, false)
        exoPlayer.prepare()
        val curIdx = findActiveSpanIndex(spans, latestPlayheadMs)
        if (curIdx >= 0 && curIdx < items.size) {
            val localPos = (latestPlayheadMs - spans[curIdx].startMs).coerceAtLeast(0L)
            exoPlayer.seekTo(curIdx, localPos)
        }
        exoPlayer.playWhenReady = wasPlaying && curIdx >= 0
    }

    // Attach GPU Effects pipeline (Overlays & Texts render interactively in Compose above PlayerView so editing/dragging never stutters!)
    LaunchedEffect(
        snapshot.activePreset,
        snapshot.filterIntensity,
        snapshot.adjust.rgbCurves,
        snapshot.adjust.hsl,
        snapshot.adjust.colorWheel,
        snapshot.adjust.highlights,
        snapshot.adjust.shadows,
        snapshot.adjust.whites,
        snapshot.adjust.blacks,
        snapshot.adjust.brilliance,
        snapshot.adjust.fade,
        previewClip.stabilize,
        previewClip.crop,
        bypassEffects,
        selectedVideo?.uri,
    ) {
        if (selectedVideo == null) return@LaunchedEffect
        val fx = VideoEffectsEngine.buildPreviewEffects(
            context = context,
            activeClip = previewClip,
            allClips = snapshot.clips,
            transitions = snapshot.transitions,
            effectLayers = snapshot.effectLayers,
            overlays = snapshot.overlays,
            texts = snapshot.textTracks,
            adjust = snapshot.adjust,
            filterPreset = snapshot.activePreset,
            filterIntensity = snapshot.filterIntensity,
            shaderParamsProvider = { shaderParamsRef.value },
            bypass = bypassEffects,
        )
        exoPlayer.setVideoEffects(fx)
        if (!isPlaying) {
            exoPlayer.seekTo(exoPlayer.currentPosition)
        }
    }

    // Immediate frame refresh for real-time shader uniforms when paused
    LaunchedEffect(
        snapshot.adjust,
        snapshot.effectLayers,
        previewClip.transform,
        previewClip.mask,
        previewClip.motionBlur,
        previewClip.animation,
        previewClip.opacity,
        previewClip.keyframes,
        playheadMs,
    ) {
        if (selectedVideo != null && !isPlaying && hasActiveClipAtPlayhead) {
            exoPlayer.seekTo(exoPlayer.currentPosition)
        }
    }

    // Update live playback speed, audio pitch, and interpolated clip Volume Keyframes
    LaunchedEffect(
        previewClip.id,
        previewClip.speed,
        previewClip.volumePercent,
        previewClip.volumeKeyframes,
        previewClip.isMuted,
        previewLocalMs,
        bypassEffects,
    ) {
        val spd = if (bypassEffects) 1f else previewClip.speed.effectiveSpeedMultiplier().coerceIn(0.1f, 8f)
        val pitch = if (!bypassEffects && previewClip.speed.changeAudioPitch) spd.coerceIn(0.4f, 2.5f) else 1f
        exoPlayer.playbackParameters = PlaybackParameters(spd, pitch)
        val evalVol = VolumeKeyframeEvaluator.evaluate(
            keyframes = previewClip.volumeKeyframes,
            localTimeMs = previewLocalMs,
            baseVolumePercent = previewClip.volumePercent,
        )
        exoPlayer.volume = if (previewClip.isMuted) 0f else (evalVol / 100f).coerceIn(0f, 1f)
    }

    // Sync Audio Track items automatically during playback (including both halves after Audio Split + Volume Keyframes)
    val activeAudioTrack: AudioTrackItem? = remember(snapshot.audioTracks, playheadMs) {
        snapshot.audioTracks.lastOrNull { au ->
            val hasNext = snapshot.audioTracks.any { o -> o.id != au.id && abs(o.startMs - au.endMs) <= 20L }
            au.uriString != null && isTimelineItemActiveAt(au.startMs, au.endMs, playheadMs, hasNext)
        }
    }
    LaunchedEffect(activeAudioTrack?.id, activeAudioTrack?.uriString, isPlaying) {
        val track = activeAudioTrack
        val uriStr = track?.uriString
        if (!isPlaying || track == null || uriStr == null) {
            audioPlayer.pause()
        } else {
            audioPlayer.setMediaItem(MediaItem.fromUri(uriStr))
            audioPlayer.prepare()
            val seekOffset = (track.sourceStartMs + (latestPlayheadMs - track.startMs)).coerceAtLeast(0L)
            audioPlayer.seekTo(seekOffset)
            val localAu = (latestPlayheadMs - track.startMs).coerceIn(0L, (track.endMs - track.startMs).coerceAtLeast(1L))
            val evalAuVol = VolumeKeyframeEvaluator.evaluate(track.volumeKeyframes, localAu, track.volumePercent)
            audioPlayer.volume = (evalAuVol / 100f).coerceIn(0f, 1f)
            audioPlayer.playWhenReady = true
        }
    }
    LaunchedEffect(activeAudioTrack?.id, activeAudioTrack?.volumePercent, activeAudioTrack?.volumeKeyframes, playheadMs, isPlaying) {
        val track = activeAudioTrack ?: return@LaunchedEffect
        if (isPlaying) {
            val localAu = (playheadMs - track.startMs).coerceIn(0L, (track.endMs - track.startMs).coerceAtLeast(1L))
            val evalAuVol = VolumeKeyframeEvaluator.evaluate(track.volumeKeyframes, localAu, track.volumePercent)
            audioPlayer.volume = (evalAuVol / 100f).coerceIn(0f, 1f)
        }
    }

    // Master playback timeline loop handling contiguous split clips AND gaps between dragged clips
    LaunchedEffect(isPlaying, selectedVideo?.uri) {
        if (!isPlaying || selectedVideo == null) {
            exoPlayer.pause()
            return@LaunchedEffect
        }
        var lastSpanIdx = findActiveSpanIndex(latestSpans, latestPlayheadMs)
        while (isPlaying) {
            val spList = latestSpans
            val durTotal = latestTotalDurationMs
            val curPos = latestPlayheadMs
            val activeIdx = findActiveSpanIndex(spList, curPos)

            if (activeIdx >= 0) {
                if (exoPlayer.currentMediaItemIndex != activeIdx) {
                    val localPos = (curPos - spList[activeIdx].startMs).coerceAtLeast(0L)
                    exoPlayer.seekTo(activeIdx, localPos)
                }
                if (!exoPlayer.isPlaying) {
                    exoPlayer.play()
                }
                val exoIdx = exoPlayer.currentMediaItemIndex.coerceIn(0, spList.lastIndex)
                // If ExoPlayer stepped past a clip that had a gap before the next clip, enter gap playback
                if (lastSpanIdx in spList.indices && exoIdx > lastSpanIdx &&
                    spList[exoIdx].startMs > spList[lastSpanIdx].endMs + 40L &&
                    curPos < spList[exoIdx].startMs - 40L
                ) {
                    exoPlayer.pause()
                    val nextCur = (spList[lastSpanIdx].endMs + 40L).coerceAtMost(durTotal)
                    latestOnUpdatePlayhead(nextCur)
                    lastSpanIdx = -1
                } else {
                    val nextTimelinePos = (spList[exoIdx].startMs + exoPlayer.currentPosition).coerceIn(0L, durTotal)
                    latestOnUpdatePlayhead(nextTimelinePos)
                    lastSpanIdx = exoIdx
                }
            } else {
                // Timeline gap (no video clip under playhead) or ending card
                if (exoPlayer.isPlaying) exoPlayer.pause()
                val nextCur = curPos + 40L
                if (nextCur >= durTotal) {
                    latestOnUpdatePlayhead(0L)
                    lastSpanIdx = findActiveSpanIndex(spList, 0L)
                    if (lastSpanIdx >= 0) {
                        exoPlayer.seekTo(lastSpanIdx, (0L - spList[lastSpanIdx].startMs).coerceAtLeast(0L))
                    }
                } else {
                    latestOnUpdatePlayhead(nextCur)
                    val enteringIdx = findActiveSpanIndex(spList, nextCur)
                    if (enteringIdx >= 0) {
                        lastSpanIdx = enteringIdx
                        exoPlayer.seekTo(enteringIdx, (nextCur - spList[enteringIdx].startMs).coerceAtLeast(0L))
                        exoPlayer.play()
                    }
                }
            }
            delay(40L)
        }
    }

    // Active Overlays & Texts visible at playheadMs (regardless of selection, so BOTH split parts play automatically!)
    val activeOverlays = remember(snapshot.overlays, playheadMs) {
        snapshot.overlays.filter { ov ->
            val hasNext = snapshot.overlays.any { o -> o.id != ov.id && abs(o.startMs - ov.endMs) <= 20L }
            isTimelineItemActiveAt(ov.startMs, ov.endMs, playheadMs, hasNext)
        }
    }
    val activeTexts = remember(snapshot.textTracks, playheadMs) {
        snapshot.textTracks.filter { tx ->
            val hasNext = snapshot.textTracks.any { o -> o.id != tx.id && abs(o.startMs - tx.endMs) <= 20L }
            isTimelineItemActiveAt(tx.startMs, tx.endMs, playheadMs, hasNext)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
    ) {
        // 1. CAPCUT TOP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBackground)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBackHome, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Filled.Close, contentDescription = "Close editor", tint = Color.White)
            }
            IconButton(
                onClick = { activeSheet = ActiveEditorSheet.EFFECTS },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("top_search_effects"),
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search Effects & Tools", tint = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                color = ObsidianSurfaceContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(1.dp, OutlineSubtle, RoundedCornerShape(8.dp)),
            ) {
                Text(
                    text = "AI UHD",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = CyberCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = ObsidianSurfaceContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { showExportSheet = true },
            ) {
                Text(
                    text = projectResolution,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (selectedVideo == null) {
                        onPickVideo()
                    } else {
                        onResetExportState()
                        showExportSheet = true
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBackground),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp).testTag("export_video_button"),
            ) {
                Text("Export", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }

        // 2. VIDEO PREVIEW STAGE (Overlays & Texts sit ABOVE PlayerView with stationary gesture hit layer -> zero shake!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.46f)
                .background(Color(snapshot.canvasBackgroundArgb)),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(aspectPreset.ratio.coerceIn(0.45f, 1.85f), matchHeightConstraintsFirst = true)
                    .clipToBounds()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val halfW = with(density) { (maxWidth.toPx() * 0.5f).coerceAtLeast(1f) }
                val halfH = with(density) { (maxHeight.toPx() * 0.5f).coerceAtLeast(1f) }

                // A. Video Player Layer (TextureView + GPU/Compose graphicsLayer for live 60fps Pos/Zoom/Rotate/Mirror/Keyframe preview!)
                if (selectedVideo != null) {
                    AndroidView(
                        factory = { ctx ->
                            (LayoutInflater.from(ctx).inflate(com.example.R.layout.luna_player_view, null) as PlayerView).apply {
                                useController = false
                                isClickable = false
                                isFocusable = false
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
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (hasActiveClipAtPlayhead) previewEvalTransform.opacity.coerceIn(0f, 1f) else 0f)
                            .graphicsLayer {
                                val scale = (previewEvalTransform.zoomPercent / 100f).coerceIn(0.25f, 5f) * previewClip.crop.scaleCrop
                                val mirrorSign = if (previewClip.transform.mirrorHorizontal) -1f else 1f
                                translationX = previewEvalTransform.posX * halfW
                                translationY = -previewEvalTransform.posY * halfH
                                scaleX = scale * mirrorSign
                                scaleY = scale
                                rotationZ = previewEvalTransform.rotateDeg + previewClip.crop.rotateDeg
                            },
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
                        Icon(Icons.Filled.VideoLibrary, null, tint = CyberCyan, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Import Video Clip", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("Tap to load clip into LUNA CapCut timeline", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }

                // B. Active Overlays / Images Layer (rendered ABOVE PlayerView — both split halves show automatically!)
                activeOverlays.forEach { ov ->
                    val frameStep = if (ov.isVideo) ((playheadMs - ov.startMs).coerceAtLeast(0L) / 120L) else 0L
                    val processedBitmap by produceState<ImageBitmap?>(
                        initialValue = null,
                        ov.id,
                        ov.uriString,
                        ov.autoRemoveBg,
                        ov.chromaEnabled,
                        ov.chromaColorArgb,
                        ov.chromaIntensity,
                        ov.chromaShadow,
                        frameStep,
                    ) {
                        val bmp = withContext(Dispatchers.IO) {
                            VideoEffectsEngine.decodeOverlayBitmap(
                                context = context,
                                item = ov.copy(opacity = 1f),
                                timeMs = (playheadMs - ov.startMs).coerceAtLeast(0L),
                            )?.asImageBitmap()
                        }
                        if (bmp != null) value = bmp
                    }
                    val isSel = ov.id == selectedOverlayId
                    val boxSizeDp = (maxWidth * ov.scale.coerceIn(0.18f, 1.8f))
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset {
                                IntOffset((ov.posX * halfW).roundToInt(), (-ov.posY * halfH).roundToInt())
                            }
                            .size(boxSizeDp)
                            .graphicsLayer {
                                rotationZ = ov.rotationDeg
                                alpha = ov.opacity.coerceIn(0.05f, 1f)
                            }
                            .then(
                                if (isSel) {
                                    Modifier.border(2.dp, CyberCyan, RoundedCornerShape(6.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("preview_overlay_${ov.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (processedBitmap != null) {
                            Image(
                                bitmap = processedBitmap!!,
                                contentDescription = ov.displayName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ObsidianSurfaceContainer.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(ov.displayName, style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                        if (isSel) {
                            Text(
                                text = "Drag • Pinch • Rotate",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = CyberCyan,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .background(ObsidianBackground.copy(alpha = 0.72f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                // C. Active Texts & Captions Layer (rendered ABOVE PlayerView & Overlays — evaluates Text Keyframes at 60fps!)
                activeTexts.forEach { tx ->
                    val localTxMs = (playheadMs - tx.startMs).coerceIn(0L, (tx.endMs - tx.startMs).coerceAtLeast(1L))
                    val evalTx = TextKeyframeEvaluator.evaluate(tx.keyframes, localTxMs, tx)
                    val isSel = tx.id == selectedTextId
                    val bgModifier = when {
                        isSel -> Modifier
                            .border(1.5.dp, Color.White, RoundedCornerShape(6.dp))
                            .background(ObsidianBackground.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        tx.isCaption -> Modifier.background(ObsidianBackground.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                        else -> Modifier
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset {
                                IntOffset((evalTx.posX * halfW).roundToInt(), (-evalTx.posY * halfH).roundToInt())
                            }
                            .graphicsLayer {
                                val s = evalTx.scale.coerceIn(0.35f, 3.5f)
                                scaleX = s
                                scaleY = s
                                rotationZ = evalTx.rotationDeg
                                alpha = evalTx.opacity.coerceIn(0f, 1f)
                            }
                            .then(bgModifier)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("preview_text_${tx.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tx.text,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (tx.bold) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = Color(tx.colorArgb),
                        )
                    }
                }

                // D. Stationary Preview Hit-Test + Drag/Pinch/Rotate Layer ABOVE Video, Overlays & Texts
                val latestActiveTexts by rememberUpdatedState(activeTexts)
                val latestActiveOverlays by rememberUpdatedState(activeOverlays)
                val latestSelTextId by rememberUpdatedState(selectedTextId)
                val latestSelOverlayId by rememberUpdatedState(selectedOverlayId)
                val latestSelectedVideo by rememberUpdatedState(selectedVideo)
                val latestHasActiveClip by rememberUpdatedState(hasActiveClipAtPlayhead)
                val latestPreviewClip by rememberUpdatedState(previewClip)
                val latestPlayheadPos by rememberUpdatedState(playheadMs)
                val latestOnUpdateTextTransform by rememberUpdatedState(onUpdateTextTransform)
                val latestOnUpdateOverlay by rememberUpdatedState(onUpdateOverlay)
                val latestOnSelectText by rememberUpdatedState(onSelectText)
                val latestOnSelectOverlay by rememberUpdatedState(onSelectOverlay)
                val latestOnSelectClip by rememberUpdatedState(onSelectClip)
                val latestOnApplyPreviewVideoGesture by rememberUpdatedState(onApplyPreviewVideoGesture)
                val latestOnCommit by rememberUpdatedState(onCommitHistory)

                if (selectedVideo != null || latestActiveTexts.isNotEmpty() || latestActiveOverlays.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(halfW, halfH) {
                                val hitPadPx = with(density) { 26.dp.toPx() }
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val pt = down.position
                                    val curPlayMs = latestPlayheadPos

                                    // Hit-test topmost Texts first (using keyframe-evaluated positions), then Overlays, then Video Canvas
                                    val textsOrdered = latestActiveTexts.sortedByDescending { it.id == latestSelTextId }
                                    val hitText = textsOrdered.firstOrNull { tx ->
                                        val loc = (curPlayMs - tx.startMs).coerceIn(0L, (tx.endMs - tx.startMs).coerceAtLeast(1L))
                                        val ev = TextKeyframeEvaluator.evaluate(tx.keyframes, loc, tx)
                                        val cx = halfW + ev.posX * halfW
                                        val cy = halfH - ev.posY * halfH
                                        val hw = maxOf(
                                            with(density) { 64.dp.toPx() },
                                            tx.text.length * with(density) { 6.dp.toPx() } * ev.scale,
                                        ) + hitPadPx
                                        val hh = with(density) { 26.dp.toPx() } * ev.scale + hitPadPx
                                        abs(pt.x - cx) <= hw && abs(pt.y - cy) <= hh
                                    }

                                    val hitOverlay = if (hitText == null) {
                                        val ovOrdered = latestActiveOverlays.sortedByDescending { it.id == latestSelOverlayId }
                                        ovOrdered.firstOrNull { ov ->
                                            val cx = halfW + ov.posX * halfW
                                            val cy = halfH - ov.posY * halfH
                                            val halfSide = (halfW * ov.scale.coerceIn(0.2f, 1.8f)) + hitPadPx
                                            abs(pt.x - cx) <= halfSide && abs(pt.y - cy) <= halfSide
                                        }
                                    } else null

                                    val target: PreviewHitTarget? = when {
                                        hitText != null -> PreviewHitTarget.Text(hitText.id)
                                        hitOverlay != null -> PreviewHitTarget.Overlay(hitOverlay.id)
                                        latestSelectedVideo != null && latestHasActiveClip -> PreviewHitTarget.Video(latestPreviewClip.id)
                                        else -> null
                                    }
                                    if (target == null) return@awaitEachGesture

                                    down.consume()
                                    var moved = false
                                    val initTxEval = hitText?.let { tx ->
                                        val loc = (curPlayMs - tx.startMs).coerceIn(0L, (tx.endMs - tx.startMs).coerceAtLeast(1L))
                                        TextKeyframeEvaluator.evaluate(tx.keyframes, loc, tx)
                                    }
                                    var liveTxPosX = initTxEval?.posX ?: 0f
                                    var liveTxPosY = initTxEval?.posY ?: 0f
                                    var liveTxScale = initTxEval?.scale ?: 1f
                                    var liveTxRot = initTxEval?.rotationDeg ?: 0f
                                    val liveTxOp = initTxEval?.opacity ?: 1f
                                    var liveOverlay = hitOverlay

                                    do {
                                        val event = awaitPointerEvent()
                                        val pan = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        val rotation = event.calculateRotation()
                                        val pinchActive = event.changes.count { it.pressed } >= 2 &&
                                            event.calculateCentroidSize(useCurrent = false) > 0f

                                        val minPan = if (target is PreviewHitTarget.Video) 0.5f else 0.25f
                                        val hasTransform = abs(pan.x) > minPan || abs(pan.y) > minPan ||
                                            (pinchActive && (abs(zoom - 1f) > 0.002f || abs(rotation) > 0.05f))

                                        if (hasTransform) {
                                            if (!moved) {
                                                moved = true
                                                latestOnCommit()
                                                when (target) {
                                                    is PreviewHitTarget.Text -> latestOnSelectText(target.id)
                                                    is PreviewHitTarget.Overlay -> latestOnSelectOverlay(target.id)
                                                    is PreviewHitTarget.Video -> latestOnSelectClip(target.clipId)
                                                }
                                            }
                                            when (target) {
                                                is PreviewHitTarget.Text -> {
                                                    liveTxPosX = (liveTxPosX + pan.x / halfW).coerceIn(-1.2f, 1.2f)
                                                    liveTxPosY = (liveTxPosY - pan.y / halfH).coerceIn(-1.2f, 1.2f)
                                                    if (pinchActive) {
                                                        liveTxScale = (liveTxScale * zoom).coerceIn(0.35f, 3.5f)
                                                        liveTxRot += rotation
                                                    }
                                                    latestOnUpdateTextTransform(
                                                        liveTxPosX,
                                                        liveTxPosY,
                                                        liveTxScale * 100f,
                                                        liveTxRot,
                                                        liveTxOp,
                                                        false,
                                                    )
                                                }
                                                is PreviewHitTarget.Overlay -> {
                                                    val cur = liveOverlay ?: break
                                                    val next = cur.copy(
                                                        posX = (cur.posX + pan.x / halfW).coerceIn(-1.2f, 1.2f),
                                                        posY = (cur.posY - pan.y / halfH).coerceIn(-1.2f, 1.2f),
                                                        scale = if (pinchActive) (cur.scale * zoom).coerceIn(0.18f, 3.2f) else cur.scale,
                                                        rotationDeg = if (pinchActive) (cur.rotationDeg + rotation) else cur.rotationDeg,
                                                    )
                                                    liveOverlay = next
                                                    latestOnUpdateOverlay(next)
                                                }
                                                is PreviewHitTarget.Video -> {
                                                    latestOnApplyPreviewVideoGesture(
                                                        if (pinchActive) zoom else 1f,
                                                        pan.x / halfW,
                                                        -pan.y / halfH,
                                                        if (pinchActive) rotation else 0f,
                                                    )
                                                }
                                            }
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    if (!moved) {
                                        when (target) {
                                            is PreviewHitTarget.Text -> latestOnSelectText(target.id)
                                            is PreviewHitTarget.Overlay -> latestOnSelectOverlay(target.id)
                                            is PreviewHitTarget.Video -> latestOnSelectClip(target.clipId)
                                        }
                                    }
                                }
                            },
                    )
                }
            }

            // Top-right [ON / ORIGINAL] Compare badge
            Surface(
                color = if (bypassEffects) ObsidianSurfaceHigh else ObsidianSurface.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clickable(onClick = onToggleBypass)
                    .testTag("toggle_bypass_button"),
            ) {
                Text(
                    text = if (bypassEffects) "ORIGINAL" else "${snapshot.activePreset.presetFormat} ON",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bypassEffects) Color.White else CyberCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // 3. PLAYBACK & KEYFRAME TRANSPORT ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBackground)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatShortTime(playheadMs)} | ${formatShortTime(totalDurationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = {
                    if (selectedVideo == null) {
                        onPickVideo()
                    } else {
                        isPlaying = !isPlaying
                    }
                },
                modifier = Modifier.size(36.dp).testTag("play_pause_button"),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (betweenKeyframesSeg != null) {
                Surface(
                    color = ObsidianSurfaceContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .border(1.dp, CyberCyan, RoundedCornerShape(8.dp))
                        .clickable { activeSheet = ActiveEditorSheet.GRAPHS }
                        .testTag("keyframe_graphs_button"),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.GraphicEq, null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Graphs", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Diamond Keyframe ◆+ Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleKeyframe)
                    .testTag("keyframe_diamond_button"),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    val c = Offset(size.width * 0.5f, size.height * 0.5f)
                    val r = size.minDimension * 0.42f
                    val path = Path().apply {
                        moveTo(c.x, c.y - r)
                        lineTo(c.x + r, c.y)
                        lineTo(c.x, c.y + r)
                        lineTo(c.x - r, c.y)
                        close()
                    }
                    if (isOnKeyframe) {
                        drawPath(path, CyberCyan)
                    } else {
                        drawPath(path, Color.White, style = Stroke(1.8.dp.toPx()))
                    }
                }
            }

            IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(34.dp).testTag("toolbar_undo")) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (canUndo) Color.White else TextSecondary.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(34.dp).testTag("toolbar_redo")) {
                Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (canRedo) Color.White else TextSecondary.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onCycleCanvasFit, modifier = Modifier.size(34.dp).testTag("toolbar_fit")) {
                Icon(Icons.Filled.Fullscreen, "Cycle Fit/Fill", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // 4. CAPCUT MULTI-TRACK FILMSTRIP TIMELINE
        CapCutTimelineArea(
            spans = spans,
            transitions = snapshot.transitions,
            effectLayers = snapshot.effectLayers,
            overlays = snapshot.overlays,
            audioTracks = snapshot.audioTracks,
            textTracks = snapshot.textTracks,
            filmstrip = filmstrip,
            hasEndingCard = snapshot.hasEndingCard,
            playheadMs = playheadMs,
            totalDurationMs = totalDurationMs,
            timelineZoom = timelineZoom,
            selectedClipId = selectedClipId,
            selectedOverlayId = selectedOverlayId,
            selectedEffectId = selectedEffectId,
            selectedTextId = selectedTextId,
            selectedAudioId = selectedAudioId,
            selectedKeyframeId = selectedKeyframeId,
            selectedVolumeKeyframeId = selectedVolumeKeyframeId,
            onUpdatePlayhead = { ms ->
                onUpdatePlayhead(ms)
                if (selectedVideo != null) {
                    val spanIdx = findActiveSpanIndex(spans, ms)
                    if (spanIdx >= 0) {
                        val localPos = (ms - spans[spanIdx].startMs).coerceAtLeast(0L)
                        exoPlayer.seekTo(spanIdx, localPos)
                    }
                }
            },
            onUpdateZoom = onUpdateZoom,
            onCommitHistory = onCommitHistory,
            onSelectClip = onSelectClip,
            onSelectClipKeyframe = onSelectClipKeyframe,
            onSelectTextKeyframe = onSelectTextKeyframe,
            onSelectClipVolumeKeyframe = onSelectClipVolumeKeyframe,
            onSelectAudioVolumeKeyframe = onSelectAudioVolumeKeyframe,
            onMoveClip = onMoveClip,
            onMoveOverlay = onMoveOverlay,
            onMoveEffect = onMoveEffect,
            onMoveText = onMoveText,
            onMoveAudio = onMoveAudio,
            onTrimClipEdge = onTrimClipEdge,
            onTrimOverlayEdge = onTrimOverlayEdge,
            onTrimEffectEdge = onTrimEffectEdge,
            onTrimTextEdge = onTrimTextEdge,
            onTrimAudioEdge = onTrimAudioEdge,
            onToggleMuteClip = onToggleMuteClip,
            onOpenTransition = { afterId ->
                editingTransitionAfterId = afterId
                activeSheet = ActiveEditorSheet.TRANSITIONS
            },
            onAddClipClick = onAppendVideoClip,
            onToggleEndingCard = onToggleEndingCard,
            onOpenSheet = { activeSheet = it },
            onSelectOverlay = onSelectOverlay,
            onSelectEffect = onSelectEffectLayer,
            onSelectText = onSelectText,
            onSelectAudio = onSelectAudio,
            modifier = Modifier.weight(0.54f),
        )

        // 5. BOTTOM CONTEXTUAL PANEL / TOOLBAR
        when (activeSheet) {
            ActiveEditorSheet.NONE -> {
                when {
                    selectedEffectId != null -> {
                        val selFxId = selectedEffectId
                        CapCutEffectSelectedBar(
                            onBack = { onSelectEffectLayer(null) },
                            onSplit = { onSplitEffect(selFxId) },
                            onAdjust = { activeSheet = ActiveEditorSheet.EFFECTS },
                            onReplace = { activeSheet = ActiveEditorSheet.EFFECTS },
                            onDuplicate = { onDuplicateEffect(selFxId) },
                            onObject = { activeSheet = ActiveEditorSheet.EFFECT_OBJECT },
                            onDelete = { onDeleteEffect(selFxId) },
                        )
                    }
                    selectedTextId != null -> {
                        val selTxtId = selectedTextId
                        CapCutTextSelectedBar(
                            showGraphsButton = betweenKeyframesSeg != null,
                            onBack = { onSelectText(null) },
                            onAddText = {
                                onSelectText(null)
                                activeSheet = ActiveEditorSheet.TEXT
                            },
                            onSplit = { onSplitText(selTxtId) },
                            onStyle = { activeSheet = ActiveEditorSheet.TEXT },
                            onTransform = { activeSheet = ActiveEditorSheet.TRANSFORM },
                            onGraphs = { activeSheet = ActiveEditorSheet.GRAPHS },
                            onAnimations = { activeSheet = ActiveEditorSheet.ANIMATIONS },
                            onDelete = { onDeleteText(selTxtId) },
                        )
                    }
                    selectedOverlayId != null -> {
                        val selOvId = selectedOverlayId
                        CapCutOverlaySelectedBar(
                            onBack = { onSelectOverlay(null) },
                            onOpenOverlayTools = { activeSheet = ActiveEditorSheet.OVERLAY },
                            onTransform = { activeSheet = ActiveEditorSheet.TRANSFORM },
                            onSplit = { onSplitOverlay(selOvId) },
                            onDuplicate = { onDuplicateOverlay(selOvId) },
                            onDelete = { onDeleteOverlay(selOvId) },
                        )
                    }
                    selectedAudioId != null -> {
                        val selAuId = selectedAudioId
                        CapCutAudioSelectedBar(
                            onBack = { onSelectAudio(null) },
                            onVolume = { activeSheet = ActiveEditorSheet.AUDIO_VOLUME },
                            onSplit = { onSplitAudio(selAuId) },
                            onDuplicate = { onDuplicateAudio(selAuId) },
                            onDelete = { onDeleteAudio(selAuId) },
                        )
                    }
                    selectedClipId != null -> {
                        val canSplit = (localPlayheadMs >= 300L && activeClip.timelineDurationMs() - localPlayheadMs >= 300L)
                        CapCutClipSelectedBar(
                            clip = activeClip,
                            canSplit = canSplit,
                            showGraphsButton = betweenKeyframesSeg != null,
                            onBack = { onSelectClip(null) },
                            onSplit = onSplitClip,
                            onDelete = onDeleteClip,
                            onBeatToggle = onToggleBeat,
                            onDuplicate = onDuplicateClip,
                            onReplace = onReplaceClipVideo,
                            onMoveToOverlay = onMoveClipToOverlay,
                            onFreeze = onFreezeClip,
                            onReverseToggle = onReverseClip,
                            onOpenSheet = { activeSheet = it },
                        )
                    }
                    else -> {
                        CapCutMainBottomBar(
                            onSelectEditClip = { onSelectClip(activeClip.id) },
                            onOpenSheet = { activeSheet = it },
                        )
                    }
                }
            }
            ActiveEditorSheet.SPEED -> SpeedToolSheet(activeClip, onUpdateSpeed) { activeSheet = ActiveEditorSheet.NONE }
            ActiveEditorSheet.GRAPHS -> {
                val seg = betweenKeyframesSeg
                if (seg != null) {
                    KeyframeGraphsSheet(
                        currentGraph = seg.second,
                        onSelectGraph = { g -> onSetSegmentGraph(seg.first, g) },
                        onClose = { activeSheet = ActiveEditorSheet.NONE },
                    )
                } else {
                    activeSheet = ActiveEditorSheet.NONE
                }
            }
            ActiveEditorSheet.TRANSFORM -> {
                val selText = selectedTextTrack
                val selOverlay = selectedOverlayId?.let { oid -> snapshot.overlays.firstOrNull { it.id == oid } }
                when {
                    selText != null -> {
                        val evalTx = TextKeyframeEvaluator.evaluate(selText.keyframes, selectedTextLocalMs, selText)
                        TransformToolSheet(
                            targetLabel = "Text",
                            evaluatedTransform = TransformSettings(
                                posX = evalTx.posX,
                                posY = evalTx.posY,
                                zoomPercent = evalTx.scale * 100f,
                                rotateDeg = evalTx.rotationDeg,
                            ),
                            opacity = evalTx.opacity,
                            showMirror = false,
                            showOpacity = true,
                            keyframeModeEnabled = keyframeModeEnabled,
                            keyframeCount = selText.keyframes.size,
                            isOnKeyframe = isOnKeyframe,
                            hasSelectedKeyframe = selectedKeyframeId != null && selText.keyframes.any { it.id == selectedKeyframeId },
                            onToggleKeyframeMode = onToggleKeyframeMode,
                            onAddKeyframe = onAddTransformKeyframe,
                            onDeleteKeyframe = onDeleteTransformKeyframe,
                            onDeleteAllKeyframes = onDeleteAllTransformKeyframes,
                            onFillCanvas = onFillCanvas,
                            onReset = { onUpdateTextTransform(0f, -0.55f, 100f, 0f, 1f, true) },
                            onUpdateTransform = { tr, op ->
                                onUpdateTextTransform(tr.posX, tr.posY, tr.zoomPercent, tr.rotateDeg, op, false)
                            },
                            onClose = { activeSheet = ActiveEditorSheet.NONE },
                        )
                    }
                    selOverlay != null -> {
                        TransformToolSheet(
                            targetLabel = "Overlay",
                            evaluatedTransform = TransformSettings(
                                posX = selOverlay.posX,
                                posY = selOverlay.posY,
                                zoomPercent = selOverlay.scale * 100f,
                                rotateDeg = selOverlay.rotationDeg,
                            ),
                            opacity = selOverlay.opacity,
                            showMirror = false,
                            showOpacity = true,
                            keyframeModeEnabled = false,
                            keyframeCount = 0,
                            isOnKeyframe = false,
                            hasSelectedKeyframe = false,
                            onToggleKeyframeMode = onToggleKeyframeMode,
                            onAddKeyframe = {},
                            onDeleteKeyframe = {},
                            onDeleteAllKeyframes = {},
                            onFillCanvas = onFillCanvas,
                            onReset = {
                                onUpdateOverlay(selOverlay.copy(posX = 0f, posY = 0f, scale = 0.65f, rotationDeg = 0f, opacity = 1f))
                            },
                            onUpdateTransform = { tr, op ->
                                onUpdateOverlay(
                                    selOverlay.copy(
                                        posX = tr.posX,
                                        posY = tr.posY,
                                        scale = (tr.zoomPercent / 100f).coerceIn(0.18f, 3.5f),
                                        rotationDeg = tr.rotateDeg,
                                        opacity = op,
                                    )
                                )
                            },
                            onClose = { activeSheet = ActiveEditorSheet.NONE },
                        )
                    }
                    else -> {
                        val evalClip = KeyframeEvaluator.evaluate(
                            keyframes = activeClip.keyframes,
                            localTimeMs = localPlayheadMs,
                            baseTransform = activeClip.transform,
                            baseOpacity = activeClip.opacity,
                            baseAdjust = snapshot.adjust,
                            baseFilterIntensity = snapshot.filterIntensity,
                        )
                        val evalTr = TransformSettings(
                            posX = evalClip.posX,
                            posY = evalClip.posY,
                            zoomPercent = evalClip.zoomPercent,
                            rotateDeg = evalClip.rotateDeg,
                            mirrorHorizontal = activeClip.transform.mirrorHorizontal,
                        )
                        TransformToolSheet(
                            targetLabel = "Video",
                            evaluatedTransform = evalTr,
                            opacity = evalClip.opacity,
                            showMirror = true,
                            showOpacity = false,
                            keyframeModeEnabled = keyframeModeEnabled,
                            keyframeCount = activeClip.keyframes.size,
                            isOnKeyframe = activeClip.keyframes.any {
                                it.id == selectedKeyframeId || abs(it.timeMs - localPlayheadMs) < 140L
                            },
                            hasSelectedKeyframe = selectedKeyframeId != null && activeClip.keyframes.any { it.id == selectedKeyframeId },
                            onToggleKeyframeMode = onToggleKeyframeMode,
                            onAddKeyframe = onAddTransformKeyframe,
                            onDeleteKeyframe = onDeleteTransformKeyframe,
                            onDeleteAllKeyframes = onDeleteAllTransformKeyframes,
                            onFillCanvas = onFillCanvas,
                            onReset = onResetTransform,
                            onUpdateTransform = { tr, _ -> onUpdateTransform(tr) },
                            onClose = { activeSheet = ActiveEditorSheet.NONE },
                        )
                    }
                }
            }
            ActiveEditorSheet.CROP -> CropToolSheet(activeClip, onUpdateCrop) { activeSheet = ActiveEditorSheet.NONE }
            ActiveEditorSheet.MASK -> MaskToolSheet(activeClip, onUpdateMask) { activeSheet = ActiveEditorSheet.NONE }
            ActiveEditorSheet.MOTION_BLUR -> MotionBlurToolSheet(activeClip, onUpdateMotionBlur) { activeSheet = ActiveEditorSheet.NONE }
            ActiveEditorSheet.STABILIZE -> StabilizeToolSheet(activeClip, onUpdateStabilize) { activeSheet = ActiveEditorSheet.NONE }
            ActiveEditorSheet.VOLUME -> VolumeToolSheet(
                clip = activeClip,
                localPlayheadMs = localPlayheadMs,
                selectedVolumeKeyframeId = selectedVolumeKeyframeId,
                onUpdateVolume = onUpdateVolume,
                onAddVolumeKeyframe = onAddClipVolumeKeyframe,
                onDeleteVolumeKeyframe = onDeleteClipVolumeKeyframe,
                onDeleteAllVolumeKeyframes = onDeleteAllClipVolumeKeyframes,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
            ActiveEditorSheet.AUDIO_VOLUME -> {
                val selAudio = snapshot.audioTracks.firstOrNull { it.id == selectedAudioId } ?: snapshot.audioTracks.lastOrNull()
                if (selAudio != null) {
                    val localAuMs = (playheadMs - selAudio.startMs).coerceIn(0L, (selAudio.endMs - selAudio.startMs).coerceAtLeast(1L))
                    AudioTrackVolumeSheet(
                        audio = selAudio,
                        localPlayheadMs = localAuMs,
                        selectedVolumeKeyframeId = selectedVolumeKeyframeId,
                        onUpdateVolume = { vol, rn -> onUpdateAudioVolume(selAudio.id, vol, rn) },
                        onAddVolumeKeyframe = { onAddAudioVolumeKeyframe(selAudio.id) },
                        onDeleteVolumeKeyframe = { onDeleteAudioVolumeKeyframe(selAudio.id) },
                        onDeleteAllVolumeKeyframes = { onDeleteAllAudioVolumeKeyframes(selAudio.id) },
                        onClose = { activeSheet = ActiveEditorSheet.NONE },
                    )
                } else {
                    activeSheet = ActiveEditorSheet.NONE
                }
            }
            ActiveEditorSheet.OPACITY -> OpacityToolSheet(activeClip, onUpdateOpacity) { activeSheet = ActiveEditorSheet.NONE }
            ActiveEditorSheet.EFFECTS -> EffectsToolSheet(
                activeEffects = snapshot.effectLayers,
                selectedEffectId = selectedEffectId,
                onApplyEffect = onApplyEffect,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
            ActiveEditorSheet.EFFECT_OBJECT -> {
                val selFx = snapshot.effectLayers.firstOrNull { it.id == selectedEffectId } ?: snapshot.effectLayers.lastOrNull()
                if (selFx != null) {
                    EffectObjectToolSheet(
                        applyToAllVideos = selFx.applyToAllVideos,
                        effectLabel = selFx.type.label,
                        onSelectTarget = { all -> onSetEffectObjectTarget(selFx.id, all) },
                        onClose = { activeSheet = ActiveEditorSheet.NONE },
                    )
                } else {
                    activeSheet = ActiveEditorSheet.NONE
                }
            }
            ActiveEditorSheet.ANIMATIONS -> AnimationsToolSheet(activeClip.animation, onUpdateAnimation) { activeSheet = ActiveEditorSheet.NONE }
            ActiveEditorSheet.TRANSITIONS -> {
                val afterId = editingTransitionAfterId ?: spans.firstOrNull()?.clip?.id ?: activeClip.id
                TransitionsToolSheet(
                    afterClipId = afterId,
                    currentSpec = snapshot.transitions.firstOrNull { it.afterClipId == afterId },
                    onUpdateTransition = onUpdateTransition,
                    onClose = { activeSheet = ActiveEditorSheet.NONE },
                )
            }
            ActiveEditorSheet.ADJUST -> AdjustToolSheet(
                adjust = snapshot.adjust,
                onUpdateAdjust = onUpdateAdjust,
                onSmartAuto = onAutoAdjust,
                onReset = onResetAdjust,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
            ActiveEditorSheet.FILTERS -> FiltersToolSheet(
                presets = presetsList,
                activePreset = snapshot.activePreset,
                filterIntensity = snapshot.filterIntensity,
                onSelectPreset = onSelectPreset,
                onChangeIntensity = onChangeFilterIntensity,
                onImportFfxOrCube = onImportFfxOrCube,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
            ActiveEditorSheet.OVERLAY -> OverlayToolSheet(
                overlays = snapshot.overlays,
                selectedOverlayId = selectedOverlayId,
                onPickOverlayMedia = onPickOverlayMedia,
                onSelectOverlay = onSelectOverlay,
                onUpdateOverlay = onUpdateOverlay,
                onDeleteOverlay = onDeleteOverlay,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
            ActiveEditorSheet.TEXT, ActiveEditorSheet.CAPTIONS -> TextCaptionsToolSheet(
                isCaptionsMode = activeSheet == ActiveEditorSheet.CAPTIONS,
                texts = snapshot.textTracks,
                selectedTextId = selectedTextId,
                onSelectText = onSelectText,
                onAddText = onAddTextOrCaption,
                onUpdateText = onUpdateTextTrack,
                onGenerateAutoCaptions = onGenerateAutoCaptions,
                onDeleteText = onDeleteText,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
            ActiveEditorSheet.AUDIO -> AudioTrackToolSheet(
                audioTracks = snapshot.audioTracks,
                onPickAudioFile = onPickAudioFile,
                onDeleteAudio = onDeleteAudio,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
            ActiveEditorSheet.ASPECT_RATIO, ActiveEditorSheet.BACKGROUND -> AspectBackgroundToolSheet(
                isAspectMode = activeSheet == ActiveEditorSheet.ASPECT_RATIO,
                aspectPreset = aspectPreset,
                canvasBgArgb = snapshot.canvasBackgroundArgb,
                onSelectAspect = onSelectAspect,
                onSelectBg = onSelectBg,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
        }
    }

    if (showExportSheet && selectedVideo != null) {
        ExportModalSheet(
            video = selectedVideo,
            timelineDurationMs = totalDurationMs,
            preset = snapshot.activePreset,
            exportState = exportState,
            onStartExport = onStartExport,
            onCancelExport = onCancelExport,
            onSaveCopyAs = onSaveCopyAs,
            onDismiss = { showExportSheet = false },
        )
    }
}

private fun formatShortTime(ms: Long): String {
    val sec = (ms.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.US, "%02d:%02d", sec / 60L, sec % 60L)
}
