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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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
import com.example.data.model.SmoothReverseConfig
import com.example.data.model.SpeedMode
import com.example.data.model.SpeedSettings
import com.example.data.model.StabilizeMode
import com.example.data.model.TextKeyframeEvaluator
import com.example.data.model.TextTrackItem
import com.example.data.model.TimelineSnapshot
import com.example.data.model.TimesliceSettings
import com.example.data.model.TransformSettings
import com.example.data.model.TransitionType
import com.example.data.model.VideoClipSegment
import com.example.data.model.VolumeKeyframeEvaluator
import com.example.data.model.computeTrackSpans
import com.example.data.model.findActiveSpanIndex
import com.example.data.model.findPreviewSpanIndex
import com.example.data.model.isTimelineItemActiveAt
import com.example.domain.video.ExportConfig
import com.example.domain.video.ExportUiState
import com.example.domain.video.LunaShaderParams
import com.example.domain.video.TimesliceCompositor
import com.example.domain.video.TimeslicePreviewFramePool
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
    clipThumbnails: Map<String, List<ImageBitmap>>,
    autosaveStatus: AutosaveStatus,
    reverseProgress: ReverseProgressState?,
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
    onAppendVideoClipToTrack: (Int) -> Unit,
    onReplaceClipVideo: () -> Unit,
    onPickOverlayMedia: () -> Unit,
    onPickAudioFile: () -> Unit,
    onPickAudioToSlot: (Int) -> Unit,
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
    onMoveClipToTrack: (String, Int) -> Unit,
    onMoveOverlay: (String, Long) -> Unit,
    onMoveEffect: (String, Long) -> Unit,
    onMoveText: (String, Long) -> Unit,
    onMoveAudio: (String, Long) -> Unit,
    onMoveAudioToSlot: (String, Int) -> Unit,
    onTrimClipEdge: (String, Boolean, Long) -> Unit,
    onTrimOverlayEdge: (String, Boolean, Long) -> Unit,
    onTrimEffectEdge: (String, Boolean, Long) -> Unit,
    onTrimTextEdge: (String, Boolean, Long) -> Unit,
    onTrimAudioEdge: (String, Boolean, Long) -> Unit,
    onAddVideoTrack: () -> Unit,
    onToggleTrackHidden: (String) -> Unit,
    onToggleTrackLocked: (String) -> Unit,
    onToggleTrackMuted: (String) -> Unit,
    onResizeTrackHeight: (String, Float) -> Unit,
    onDuplicateClip: () -> Unit,
    onDeleteClip: () -> Unit,
    onFreezeClip: () -> Unit,
    onReverseClip: () -> Unit,
    onReverseMultipleClips: (List<String>, Boolean, Boolean) -> Unit,
    onSmoothAutoReverse: () -> Unit,
    onUpdateSmoothReverseConfig: (String, SmoothReverseConfig) -> Unit,
    onResetClipAll: () -> Unit,
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
    onApplyCapCutTimeslice: () -> Unit,
    onApplyNodeTimeslice: () -> Unit,
    onUpdateTimeslice: (TimesliceSettings, Boolean) -> Unit,
    onToggleTimeslicePreview: () -> Unit,
    onRemoveTimeslice: () -> Unit,
    onSaveTimeslicePreset: () -> Unit,
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
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val presetsList = (presetsState as? UiState.Success)?.data.orEmpty()

    val videoTrackCount = snapshot.effectiveVideoTrackCount()
    val trackConfigs = remember(snapshot.trackConfigs, videoTrackCount) { snapshot.normalizedTrackConfigs() }
    val v1Spans = remember(snapshot.clips) { computeTrackSpans(snapshot.clips, 0) }
    val playingSpanIdx = findPreviewSpanIndex(v1Spans, playheadMs)
    val v1ActiveAtPlayhead = playingSpanIdx >= 0 && selectedVideo != null && !snapshot.isVideoTrackHidden(0)
    val previewSpan = if (playingSpanIdx >= 0) v1Spans[playingSpanIdx] else v1Spans.firstOrNull()

    // Upper visible tracks active at playhead (V2, V3, ...), rendered in ascending Z-order above V1
    val upperTrackIndicesWithMedia = remember(snapshot.clips, videoTrackCount) {
        (1 until videoTrackCount).filter { tidx ->
            snapshot.clips.any { it.trackIndex == tidx && !it.sourceUriString.isNullOrBlank() }
        }
    }
    val upperActiveSpansAtPlayhead = remember(snapshot.clips, videoTrackCount, trackConfigs, playheadMs) {
        (1 until videoTrackCount).mapNotNull { tidx ->
            if (snapshot.isVideoTrackHidden(tidx)) null
            else {
                val tsp = computeTrackSpans(snapshot.clips, tidx)
                val idx = findActiveSpanIndex(tsp, playheadMs)
                if (idx >= 0) tsp[idx] else null
            }
        }
    }
    val hasAnyVideoMedia = selectedVideo != null || snapshot.clips.any { !it.sourceUriString.isNullOrBlank() }

    val toolbarSpan = (selectedClipId?.let { id -> spans.firstOrNull { it.clip.id == id } }
        ?: upperActiveSpansAtPlayhead.lastOrNull()
        ?: previewSpan
        ?: spans.first())
    val activeClip: VideoClipSegment = toolbarSpan.clip
    val previewClip: VideoClipSegment = previewSpan?.clip ?: activeClip
    val localPlayheadMs = (playheadMs - toolbarSpan.startMs).coerceIn(0L, activeClip.timelineDurationMs())
    val previewLocalMs = (playheadMs - (previewSpan?.startMs ?: 0L)).coerceIn(0L, previewClip.timelineDurationMs())
    val previewEvalTransform = remember(
        previewClip.id,
        previewClip.keyframes,
        previewClip.transform,
        previewClip.opacity,
        previewClip.adjust,
        previewLocalMs,
        snapshot.filterIntensity,
    ) {
        KeyframeEvaluator.evaluate(
            keyframes = previewClip.keyframes,
            localTimeMs = previewLocalMs,
            baseTransform = previewClip.transform,
            baseOpacity = previewClip.opacity,
            baseAdjust = previewClip.adjust,
            baseFilterIntensity = snapshot.filterIntensity,
        )
    }

    val selectedTextTrack = remember(snapshot.textTracks, selectedTextId) {
        snapshot.textTracks.firstOrNull { it.id == selectedTextId }
    }
    val selectedTextLocalMs = selectedTextTrack?.let {
        (playheadMs - it.startMs).coerceIn(0L, (it.endMs - it.startMs).coerceAtLeast(1L))
    } ?: 0L

    val isPlayheadOnSelectedTarget = remember(playheadMs, toolbarSpan, selectedTextTrack) {
        if (selectedTextTrack != null) {
            playheadMs in selectedTextTrack.startMs..selectedTextTrack.endMs
        } else {
            playheadMs in toolbarSpan.startMs..toolbarSpan.endMs
        }
    }

    val isOnKeyframe = remember(activeClip.keyframes, localPlayheadMs, selectedTextTrack, selectedTextLocalMs, selectedKeyframeId, isPlayheadOnSelectedTarget) {
        if (!isPlayheadOnSelectedTarget) {
            false
        } else if (selectedTextTrack != null) {
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
    val latestV1Spans by rememberUpdatedState(v1Spans)
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

    // Compute cumulative ExoPlayer media offset so split/moved V1 clips align GL effect timestamps
    val exoCumulativeStarts = remember(v1Spans) {
        var acc = 0L
        v1Spans.map { sp ->
            val out = acc
            acc += (sp.endMs - sp.startMs).coerceAtLeast(0L)
            out
        }
    }
    val activeSpanIdxForShader = playingSpanIdx.coerceIn(0, v1Spans.lastIndex.coerceAtLeast(0))
    val currentTimelineOffsetMs = if (v1Spans.isNotEmpty()) {
        v1Spans[activeSpanIdxForShader].startMs - exoCumulativeStarts.getOrElse(activeSpanIdxForShader) { 0L }
    } else 0L

    // Respect Preview ON/OFF toggle for CapCut Timeslice (kept in snapshot for Export, hidden in Preview when OFF)
    val hiddenCapCutEffectIds = remember(snapshot.clips) {
        snapshot.clips.filter { it.timeslice.isCapCutModeActive() && !it.timeslice.previewEnabled }
            .mapNotNull { it.timeslice.capcutEffectLayerId }.toSet()
    }
    val hiddenCapCutTransitionClipIds = remember(snapshot.clips) {
        snapshot.clips.filter { it.timeslice.isCapCutModeActive() && !it.timeslice.previewEnabled }
            .mapNotNull { it.timeslice.capcutTransitionAfterClipId }.toSet()
    }
    val previewEffectLayers = remember(snapshot.effectLayers, hiddenCapCutEffectIds) {
        if (hiddenCapCutEffectIds.isEmpty()) snapshot.effectLayers
        else snapshot.effectLayers.filterNot { it.id in hiddenCapCutEffectIds }
    }
    val previewTransitions = remember(snapshot.transitions, hiddenCapCutTransitionClipIds) {
        if (hiddenCapCutTransitionClipIds.isEmpty()) snapshot.transitions
        else snapshot.transitions.filterNot { it.afterClipId in hiddenCapCutTransitionClipIds }
    }

    // Live mutable reference so OpenGL shader uniforms update at 60fps for V1
    val shaderParamsRef = remember { mutableStateOf(LunaShaderParams()) }
    val transitionWindows = remember(v1Spans, previewTransitions) {
        VideoEffectsEngine.buildTransitionWindows(v1Spans, previewTransitions)
    }
    LaunchedEffect(snapshot, previewClip, currentTimelineOffsetMs, bypassEffects, previewEffectLayers, transitionWindows) {
        shaderParamsRef.value = VideoEffectsEngine.makeShaderParams(
            clip = previewClip,
            adjust = previewClip.adjust,
            filterPreset = snapshot.activePreset,
            filterIntensity = snapshot.filterIntensity,
            effectLayers = previewEffectLayers,
            transitionWindows = transitionWindows,
            bypass = bypassEffects,
            timelineOffsetMs = currentTimelineOffsetMs,
        )
    }

    val exoPlayer = remember(context) {
        buildPreviewExoPlayer(context)
    }
    val audioPlayerA1 = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    val audioPlayerA2 = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(exoPlayer, audioPlayerA1, audioPlayerA2) {
        onDispose {
            exoPlayer.release()
            audioPlayerA1.release()
            audioPlayerA2.release()
        }
    }

    // Bind V1 media items in timeline order (using effective/reversed media + exact trims), restoring exact playhead frame
    LaunchedEffect(selectedVideo?.uri, v1Spans) {
        val primaryUri = selectedVideo?.uri ?: return@LaunchedEffect
        val items = v1Spans.map { sp ->
            val clip = sp.clip
            val u = clip.effectiveUriString()?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() } ?: primaryUri
            val effStart = clip.effectiveTrimStartMs().coerceAtLeast(0L)
            val effEnd = clip.effectiveTrimEndMs().coerceAtLeast(effStart + 250L)
            val effSrcDur = clip.effectiveSourceDurationMs().coerceAtLeast(effEnd)
            val mb = MediaItem.Builder().setUri(u)
            if (effStart > 30L || effEnd < effSrcDur - 30L) {
                mb.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(effStart)
                        .setEndPositionMs(effEnd)
                        .build()
                )
            }
            mb.build()
        }
        val wasPlaying = isPlaying
        exoPlayer.setMediaItems(items, false)
        exoPlayer.prepare()
        val curIdx = findPreviewSpanIndex(v1Spans, latestPlayheadMs)
        if (curIdx >= 0 && curIdx < items.size) {
            val sp = v1Spans[curIdx]
            val spanDur = (sp.endMs - sp.startMs).coerceAtLeast(1L)
            val maxLocalTimeline = (spanDur - 25L).coerceAtLeast(0L)
            val localTimeline = (latestPlayheadMs - sp.startMs).coerceIn(0L, maxLocalTimeline)
            val localMedia = timelineToSourceMediaMs(sp.clip, localTimeline, spanDur)
            exoPlayer.seekTo(curIdx, localMedia)
        }
        exoPlayer.playWhenReady = wasPlaying && findActiveSpanIndex(v1Spans, latestPlayheadMs) >= 0
    }

    // Attach GPU Effects pipeline once per structural LUT/stabilize configuration (avoids GL teardown stutter on split cuts)
    LaunchedEffect(
        selectedVideo?.uri,
        bypassEffects,
        snapshot.activePreset.id,
        snapshot.activePreset.lutData,
        snapshot.filterIntensity,
        previewClip.adjust.rgbCurves,
        previewClip.adjust.hsl,
        previewClip.adjust.colorWheel,
        previewClip.adjust.highlights,
        previewClip.adjust.shadows,
        previewClip.adjust.whites,
        previewClip.adjust.blacks,
        previewClip.adjust.brilliance,
        previewClip.adjust.fade,
        previewClip.stabilize,
    ) {
        if (selectedVideo == null) return@LaunchedEffect
        val fx = VideoEffectsEngine.buildPreviewEffects(
            context = context,
            activeClip = previewClip,
            allClips = snapshot.clips,
            transitions = previewTransitions,
            effectLayers = previewEffectLayers,
            overlays = snapshot.overlays,
            texts = snapshot.textTracks,
            adjust = previewClip.adjust,
            filterPreset = snapshot.activePreset,
            filterIntensity = snapshot.filterIntensity,
            shaderParamsProvider = { shaderParamsRef.value },
            bypass = bypassEffects,
        )
        exoPlayer.setVideoEffects(fx)
        if (!isPlaying) {
            val targetIdx = findPreviewSpanIndex(v1Spans, playheadMs)
            if (targetIdx in v1Spans.indices) {
                val sp = v1Spans[targetIdx]
                val spanDur = (sp.endMs - sp.startMs).coerceAtLeast(1L)
                val maxLocalTimeline = (spanDur - 25L).coerceAtLeast(0L)
                val localTimeline = (playheadMs - sp.startMs).coerceIn(0L, maxLocalTimeline)
                val localMedia = timelineToSourceMediaMs(sp.clip, localTimeline, spanDur)
                exoPlayer.seekTo(targetIdx, localMedia)
            }
        }
    }

    // Immediate V1 frame refresh & exact playhead synchronization when paused
    LaunchedEffect(
        previewClip.id,
        previewClip.adjust,
        snapshot.effectLayers,
        previewClip.transform,
        previewClip.mask,
        previewClip.motionBlur,
        previewClip.animation,
        previewClip.opacity,
        previewClip.keyframes,
        previewClip.speed,
        playheadMs,
        v1Spans,
    ) {
        if (selectedVideo != null && !isPlaying) {
            val targetIdx = findPreviewSpanIndex(v1Spans, playheadMs)
            if (targetIdx in v1Spans.indices) {
                val sp = v1Spans[targetIdx]
                val spanDur = (sp.endMs - sp.startMs).coerceAtLeast(1L)
                val maxLocalTimeline = (spanDur - 25L).coerceAtLeast(0L)
                val localTimeline = (playheadMs - sp.startMs).coerceIn(0L, maxLocalTimeline)
                val localMedia = timelineToSourceMediaMs(sp.clip, localTimeline, spanDur)
                exoPlayer.seekTo(targetIdx, localMedia)
            }
        }
    }

    // Update live V1 playback speed, audio pitch, track mute, and interpolated clip Volume Keyframes
    val v1TrackMuted = snapshot.isVideoTrackMuted(0)
    LaunchedEffect(
        previewClip.id,
        previewClip.speed,
        previewClip.volumePercent,
        previewClip.volumeKeyframes,
        previewClip.isMuted,
        v1TrackMuted,
        previewLocalMs,
        bypassEffects,
    ) {
        val spanDur = ((previewSpan?.endMs ?: 0L) - (previewSpan?.startMs ?: 0L)).coerceAtLeast(1L)
        val spd = if (bypassEffects) {
            1f
        } else if (previewClip.speed.mode == SpeedMode.NORMAL) {
            previewClip.speed.effectiveSpeedMultiplier().coerceIn(0.1f, 8f)
        } else {
            val tp = (previewLocalMs.toFloat() / spanDur.toFloat()).coerceIn(0f, 1f)
            val sp = previewClip.speed.sourceProgressAtTimelineProgress(tp)
            previewClip.speed.speedAtProgress(sp).coerceIn(0.1f, 8f)
        }
        val pitch = if (!bypassEffects && previewClip.speed.changeAudioPitch) spd.coerceIn(0.4f, 2.5f) else 1f
        exoPlayer.playbackParameters = PlaybackParameters(spd, pitch)
        val evalVol = VolumeKeyframeEvaluator.evaluate(
            keyframes = previewClip.volumeKeyframes,
            localTimeMs = previewLocalMs,
            baseVolumePercent = previewClip.volumePercent,
        )
        exoPlayer.volume = if (previewClip.isMuted || v1TrackMuted) 0f else (evalVol / 100f).coerceIn(0f, 1f)
    }

    // Sync Audio Track 1 (A1) & Audio Track 2 (A2) simultaneously during playback (respecting Hide & Mute per track)
    AudioSlotPreviewSync(
        audioPlayer = audioPlayerA1,
        audioTracks = snapshot.audioTracks,
        slotIndex = 0,
        isTrackMuted = snapshot.isAudioTrackMuted(0) || snapshot.isAudioTrackHidden(0),
        playheadMs = playheadMs,
        isPlaying = isPlaying,
    )
    AudioSlotPreviewSync(
        audioPlayer = audioPlayerA2,
        audioTracks = snapshot.audioTracks,
        slotIndex = 1,
        isTrackMuted = snapshot.isAudioTrackMuted(1) || snapshot.isAudioTrackHidden(1),
        playheadMs = playheadMs,
        isPlaying = isPlaying,
    )

    // Master playback timeline loop handling V1 spans, gaps / upper-track extensions, and pausing cleanly at totalDurationMs
    LaunchedEffect(isPlaying, hasAnyVideoMedia) {
        if (!isPlaying || !hasAnyVideoMedia) {
            exoPlayer.pause()
            return@LaunchedEffect
        }
        val initialTotal = latestTotalDurationMs.coerceAtLeast(1_000L)
        if (latestPlayheadMs >= initialTotal - 40L) {
            latestOnUpdatePlayhead(0L)
            val startIdx = findActiveSpanIndex(latestV1Spans, 0L)
            if (startIdx >= 0) {
                val sp0 = latestV1Spans[startIdx]
                exoPlayer.seekTo(
                    startIdx,
                    timelineToSourceMediaMs(sp0.clip, (0L - sp0.startMs).coerceAtLeast(0L), sp0.endMs - sp0.startMs),
                )
            }
        }
        var lastSpanIdx = findActiveSpanIndex(latestV1Spans, latestPlayheadMs)

        fun stopAtTotalDuration(durTotal: Long, spList: List<ClipTimelineSpan>) {
            exoPlayer.pause()
            audioPlayerA1.pause()
            audioPlayerA2.pause()
            latestOnUpdatePlayhead(durTotal)
            val endIdx = findPreviewSpanIndex(spList, durTotal)
            if (endIdx in spList.indices) {
                val endSp = spList[endIdx]
                val spanDur = (endSp.endMs - endSp.startMs).coerceAtLeast(1L)
                val lastFrameTimelineMs = (spanDur - 25L).coerceAtLeast(0L)
                exoPlayer.seekTo(endIdx, timelineToSourceMediaMs(endSp.clip, lastFrameTimelineMs, spanDur))
            }
            isPlaying = false
        }

        while (isPlaying) {
            val spList = latestV1Spans
            val durTotal = latestTotalDurationMs.coerceAtLeast(1_000L)
            val curPos = latestPlayheadMs
            val activeIdx = findActiveSpanIndex(spList, curPos)

            if (activeIdx >= 0) {
                val activeSp = spList[activeIdx]
                val activeSpanDur = (activeSp.endMs - activeSp.startMs).coerceAtLeast(1L)
                if (exoPlayer.currentMediaItemIndex != activeIdx) {
                    val isSmoothGaplessBoundary = activeIdx > 0 &&
                        exoPlayer.currentMediaItemIndex == activeIdx - 1 &&
                        abs(activeSp.startMs - spList[activeIdx - 1].endMs) <= 40L &&
                        (curPos - activeSp.startMs) < 120L &&
                        exoPlayer.isPlaying
                    if (!isSmoothGaplessBoundary) {
                        val maxLocalTimeline = (activeSpanDur - 25L).coerceAtLeast(0L)
                        val localTimeline = (curPos - activeSp.startMs).coerceIn(0L, maxLocalTimeline)
                        exoPlayer.seekTo(activeIdx, timelineToSourceMediaMs(activeSp.clip, localTimeline, activeSpanDur))
                    }
                }
                if (!exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED) {
                    exoPlayer.play()
                }
                val exoIdx = exoPlayer.currentMediaItemIndex.coerceIn(0, spList.lastIndex)
                val exoSp = spList[exoIdx]
                val exoSpanDur = (exoSp.endMs - exoSp.startMs).coerceAtLeast(1L)
                if (!bypassEffects && exoSp.clip.speed.mode != SpeedMode.NORMAL) {
                    val spFrac = (exoPlayer.currentPosition.toFloat() / exoSp.clip.baseDurationMs().coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    val instSpd = exoSp.clip.speed.speedAtProgress(spFrac).coerceIn(0.1f, 8f)
                    val pitch = if (exoSp.clip.speed.changeAudioPitch) instSpd.coerceIn(0.4f, 2.5f) else 1f
                    exoPlayer.playbackParameters = PlaybackParameters(instSpd, pitch)
                }
                if (lastSpanIdx in spList.indices && exoIdx > lastSpanIdx &&
                    spList[exoIdx].startMs > spList[lastSpanIdx].endMs + 40L &&
                    curPos < spList[exoIdx].startMs - 40L
                ) {
                    exoPlayer.pause()
                    val nextCur = (spList[lastSpanIdx].endMs + 40L).coerceAtMost(durTotal)
                    if (nextCur >= durTotal) {
                        stopAtTotalDuration(durTotal, spList)
                        break
                    } else {
                        latestOnUpdatePlayhead(nextCur)
                        lastSpanIdx = -1
                    }
                } else {
                    val timelineOffsetInSpan = sourceMediaToTimelineMs(exoSp.clip, exoPlayer.currentPosition, exoSpanDur)
                    val nextTimelinePos = (exoSp.startMs + timelineOffsetInSpan).coerceIn(0L, durTotal)
                    if (exoPlayer.playbackState == Player.STATE_ENDED ||
                        curPos >= durTotal - 20L ||
                        nextTimelinePos >= durTotal - 20L
                    ) {
                        stopAtTotalDuration(durTotal, spList)
                        break
                    } else {
                        latestOnUpdatePlayhead(nextTimelinePos)
                        lastSpanIdx = exoIdx
                    }
                }
            } else {
                if (exoPlayer.isPlaying) exoPlayer.pause()
                val nextCur = curPos + 24L
                if (nextCur >= durTotal) {
                    stopAtTotalDuration(durTotal, spList)
                    break
                } else {
                    latestOnUpdatePlayhead(nextCur)
                    val enteringIdx = findActiveSpanIndex(spList, nextCur)
                    if (enteringIdx >= 0) {
                        lastSpanIdx = enteringIdx
                        val entSp = spList[enteringIdx]
                        val entSpanDur = (entSp.endMs - entSp.startMs).coerceAtLeast(1L)
                        exoPlayer.seekTo(
                            enteringIdx,
                            timelineToSourceMediaMs(entSp.clip, (nextCur - entSp.startMs).coerceAtLeast(0L), entSpanDur),
                        )
                        exoPlayer.play()
                    }
                }
            }
            delay(24L)
        }
    }

    // Active Overlays & Texts visible at playheadMs (respecting TEXT track Hide toggle!)
    val activeOverlays = remember(snapshot.overlays, playheadMs) {
        snapshot.overlays.filter { ov ->
            val hasNext = snapshot.overlays.any { o -> o.id != ov.id && abs(o.startMs - ov.endMs) <= 20L }
            isTimelineItemActiveAt(ov.startMs, ov.endMs, playheadMs, hasNext)
        }
    }
    val textTrackHidden = snapshot.isTextTrackHidden()
    val activeTexts = remember(snapshot.textTracks, playheadMs, textTrackHidden) {
        if (textTrackHidden) {
            emptyList()
        } else {
            snapshot.textTracks.filter { tx ->
                val hasNext = snapshot.textTracks.any { o -> o.id != tx.id && abs(o.startMs - tx.endMs) <= 20L }
                isTimelineItemActiveAt(tx.startMs, tx.endMs, playheadMs, hasNext)
            }
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
            if (autosaveStatus != AutosaveStatus.IDLE) {
                Surface(
                    color = ObsidianSurfaceContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (autosaveStatus == AutosaveStatus.SAVED) CyberCyan.copy(alpha = 0.45f) else OutlineSubtle,
                            RoundedCornerShape(8.dp),
                        )
                        .testTag("autosave_status_pill"),
                ) {
                    Text(
                        text = if (autosaveStatus == AutosaveStatus.SAVING) "Saving..." else "Autosaved",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (autosaveStatus == AutosaveStatus.SAVED) CyberCyan else TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
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

                // A. V1 Base Video Player Layer + Stacked Upper Video Tracks (V2, V3, V4+ rendered ON TOP!)
                if (hasAnyVideoMedia) {
                    val punchFraction = if (previewClip.cutFlashPunchOnStart && previewLocalMs in 0L..55L) {
                        1f - (previewLocalMs / 55f)
                    } else 0f
                    val previewTimesliceActive = !bypassEffects &&
                        previewClip.timeslice.isNodeModeActive() &&
                        previewClip.timeslice.previewEnabled
                    val timeslicePool = remember { TimeslicePreviewFramePool() }
                    var timesliceCacheTick by remember { mutableIntStateOf(0) }
                    val effClipUri = remember(previewClip.id, previewClip.isReversed, previewClip.reversedUriString, selectedVideo?.uri) {
                        previewClip.effectiveUriString()?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                            ?: selectedVideo?.uri
                    }
                    val effTrimStart = previewClip.effectiveTrimStartMs()
                    val effTrimEnd = previewClip.effectiveTrimEndMs()
                    val previewSpanDur = ((previewSpan?.endMs ?: 0L) - (previewSpan?.startMs ?: 0L)).coerceAtLeast(1L)
                    val currentSourceTimeMs = effTrimStart + timelineToSourceMediaMs(previewClip, previewLocalMs, previewSpanDur)
                    val warmCenterBucket = if (isPlaying) (currentSourceTimeMs / 650L) * 650L else currentSourceTimeMs
                    LaunchedEffect(
                        previewTimesliceActive,
                        effClipUri,
                        effTrimStart,
                        effTrimEnd,
                        warmCenterBucket,
                        projectFps,
                        previewClip.timeslice.sliceCount,
                        previewClip.timeslice.sliceAngleDeg,
                        previewClip.timeslice.frameInterval,
                        previewClip.timeslice.frameOffset,
                        previewClip.timeslice.sliceFeather,
                        previewClip.timeslice.animate,
                    ) {
                        if (previewTimesliceActive && effClipUri != null) {
                            val cacheKey = "${effClipUri}_${effTrimStart}_${effTrimEnd}"
                            timeslicePool.ensureWindowFrames(
                                context = context,
                                uri = effClipUri,
                                cacheKey = cacheKey,
                                clipStartMs = effTrimStart,
                                clipEndMs = effTrimEnd,
                                centerTimeMs = currentSourceTimeMs,
                                settings = previewClip.timeslice,
                                fps = projectFps.toFloat(),
                                onBatchDecoded = { timesliceCacheTick++ },
                            )
                        }
                    }

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
                            .alpha(if (v1ActiveAtPlayhead) previewEvalTransform.opacity.coerceIn(0f, 1f) else 0f)
                            .graphicsLayer {
                                val punchScale = 1f + punchFraction * 0.08f
                                val scale = (previewEvalTransform.zoomPercent / 100f).coerceIn(0.25f, 5f) * previewClip.crop.scaleCrop * punchScale
                                val mirrorSign = if (previewClip.transform.mirrorHorizontal) -1f else 1f
                                translationX = previewEvalTransform.posX * halfW
                                translationY = -previewEvalTransform.posY * halfH
                                scaleX = scale * mirrorSign
                                scaleY = scale
                                rotationZ = previewEvalTransform.rotateDeg + previewClip.crop.rotateDeg
                            },
                    )
                    if (previewTimesliceActive && v1ActiveAtPlayhead) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(previewEvalTransform.opacity.coerceIn(0f, 1f))
                                .graphicsLayer {
                                    val punchScale = 1f + punchFraction * 0.08f
                                    val scale = (previewEvalTransform.zoomPercent / 100f).coerceIn(0.25f, 5f) * previewClip.crop.scaleCrop * punchScale
                                    val mirrorSign = if (previewClip.transform.mirrorHorizontal) -1f else 1f
                                    translationX = previewEvalTransform.posX * halfW
                                    translationY = -previewEvalTransform.posY * halfH
                                    scaleX = scale * mirrorSign
                                    scaleY = scale
                                    rotationZ = previewEvalTransform.rotateDeg + previewClip.crop.rotateDeg
                                },
                        ) {
                            val tick = timesliceCacheTick
                            val srcMs = currentSourceTimeMs
                            if (tick >= 0) {
                                drawIntoCanvas { canvas ->
                                    TimesliceCompositor.drawTimesliceFrame(
                                        canvas = canvas.nativeCanvas,
                                        width = size.width,
                                        height = size.height,
                                        baseTimeMs = srcMs,
                                        clipStartMs = effTrimStart,
                                        clipEndMs = effTrimEnd,
                                        settings = previewClip.timeslice,
                                        fps = projectFps.toFloat(),
                                        fitMode = canvasFitMode,
                                        frameProvider = { reqMs -> timeslicePool.getNearestBitmap(reqMs) },
                                    )
                                }
                            }
                        }
                    }
                    if (punchFraction > 0.01f && v1ActiveAtPlayhead) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = (punchFraction * 0.72f).coerceIn(0f, 0.75f))),
                        )
                    }

                    // Upper Video Tracks (V2, V3, V4+...) composited in Z-order ON TOP of V1!
                    upperTrackIndicesWithMedia.forEach { trackIdx ->
                        key(trackIdx) {
                            UpperVideoTrackPreviewLayer(
                                trackIndex = trackIdx,
                                snapshot = snapshot,
                                canvasFitMode = canvasFitMode,
                                projectFps = projectFps,
                                bypassEffects = bypassEffects,
                                playheadMs = playheadMs,
                                isPlaying = isPlaying,
                                selectedClipId = selectedClipId,
                                halfW = halfW,
                                halfH = halfH,
                            )
                        }
                    }
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

                // B. Active Overlays / Images Layer (rendered ABOVE PlayerView strictly within [startMs, endMs)!)
                activeOverlays.forEach { ov ->
                    key(ov.id) {
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
                }

                // C. Active Texts & Captions Layer (strictly visible ONLY between startMs and endMs!)
                activeTexts.forEach { tx ->
                    key(tx.id) {
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
                }

                // D. Stationary Preview Hit-Test + Drag/Pinch/Rotate Layer ABOVE Video Tracks, Overlays & Texts
                val latestActiveTexts by rememberUpdatedState(activeTexts)
                val latestActiveOverlays by rememberUpdatedState(activeOverlays)
                val latestUpperActiveSpans by rememberUpdatedState(upperActiveSpansAtPlayhead)
                val latestSelTextId by rememberUpdatedState(selectedTextId)
                val latestSelOverlayId by rememberUpdatedState(selectedOverlayId)
                val latestSelClipId by rememberUpdatedState(selectedClipId)
                val latestHasAnyVideo by rememberUpdatedState(hasAnyVideoMedia)
                val latestV1HasActiveClip by rememberUpdatedState(v1ActiveAtPlayhead)
                val latestPreviewClip by rememberUpdatedState(previewClip)
                val latestFilterIntensity by rememberUpdatedState(snapshot.filterIntensity)
                val latestPlayheadPos by rememberUpdatedState(playheadMs)
                val latestOnUpdateTextTransform by rememberUpdatedState(onUpdateTextTransform)
                val latestOnUpdateOverlay by rememberUpdatedState(onUpdateOverlay)
                val latestOnSelectText by rememberUpdatedState(onSelectText)
                val latestOnSelectOverlay by rememberUpdatedState(onSelectOverlay)
                val latestOnSelectClip by rememberUpdatedState(onSelectClip)
                val latestOnApplyPreviewVideoGesture by rememberUpdatedState(onApplyPreviewVideoGesture)
                val latestOnCommit by rememberUpdatedState(onCommitHistory)

                if (latestHasAnyVideo || latestActiveTexts.isNotEmpty() || latestActiveOverlays.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(halfW, halfH) {
                                val hitPadPx = with(density) { 26.dp.toPx() }
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val pt = down.position
                                    val curPlayMs = latestPlayheadPos

                                    // Hit-test topmost Texts -> Overlays -> Upper Video Tracks (V(N)..V2) -> V1 Base Video
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

                                    val hitUpperVideo = if (hitText == null && hitOverlay == null) {
                                        val orderedUpper = latestUpperActiveSpans
                                            .sortedWith(
                                                compareByDescending<ClipTimelineSpan> { it.clip.id == latestSelClipId }
                                                    .thenByDescending { it.clip.trackIndex }
                                            )
                                        orderedUpper.firstOrNull { sp ->
                                            val clip = sp.clip
                                            val locMs = (curPlayMs - sp.startMs).coerceIn(0L, clip.timelineDurationMs())
                                            val ev = KeyframeEvaluator.evaluate(
                                                clip.keyframes, locMs, clip.transform, clip.opacity, clip.adjust, latestFilterIntensity
                                            )
                                            val cx = halfW + ev.posX * halfW
                                            val cy = halfH - ev.posY * halfH
                                            val s = (ev.zoomPercent / 100f).coerceIn(0.25f, 5f) * clip.crop.scaleCrop
                                            val hw = halfW * s + hitPadPx
                                            val hh = halfH * s + hitPadPx
                                            abs(pt.x - cx) <= hw && abs(pt.y - cy) <= hh
                                        }
                                    } else null

                                    val target: PreviewHitTarget? = when {
                                        hitText != null -> PreviewHitTarget.Text(hitText.id)
                                        hitOverlay != null -> PreviewHitTarget.Overlay(hitOverlay.id)
                                        hitUpperVideo != null -> PreviewHitTarget.Video(hitUpperVideo.clip.id)
                                        latestHasAnyVideo && latestV1HasActiveClip -> PreviewHitTarget.Video(latestPreviewClip.id)
                                        latestUpperActiveSpans.isNotEmpty() -> PreviewHitTarget.Video(latestUpperActiveSpans.last().clip.id)
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
                                            (pinchActive && (abs(zoom - 1f) > 0.002f || (target !is PreviewHitTarget.Video && abs(rotation) > 1.2f)))

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
                                                        if (abs(rotation) > 1.2f) liveTxRot += rotation
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
                                                        rotationDeg = if (pinchActive && abs(rotation) > 1.2f) (cur.rotationDeg + rotation) else cur.rotationDeg,
                                                    )
                                                    liveOverlay = next
                                                    latestOnUpdateOverlay(next)
                                                }
                                                is PreviewHitTarget.Video -> {
                                                    latestOnApplyPreviewVideoGesture(
                                                        if (pinchActive) zoom else 1f,
                                                        pan.x / halfW,
                                                        -pan.y / halfH,
                                                        0f,
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
                    if (!hasAnyVideoMedia) {
                        onPickVideo()
                    } else {
                        if (!isPlaying && playheadMs >= totalDurationMs - 40L) {
                            onUpdatePlayhead(0L)
                            if (v1Spans.isNotEmpty()) {
                                exoPlayer.seekTo(0, 0L)
                            }
                        }
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
                    .alpha(if (isPlayheadOnSelectedTarget) 1f else 0.4f)
                    .clickable {
                        if (isPlayheadOnSelectedTarget) {
                            onToggleKeyframe()
                        } else {
                            onShowToast("Move playhead onto clip first")
                        }
                    }
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
            trackConfigs = trackConfigs,
            videoTrackCount = videoTrackCount,
            filmstrip = filmstrip,
            clipThumbnails = clipThumbnails,
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
                    val spanIdx = findPreviewSpanIndex(v1Spans, ms)
                    if (spanIdx in v1Spans.indices) {
                        val sp = v1Spans[spanIdx]
                        val spanDur = (sp.endMs - sp.startMs).coerceAtLeast(1L)
                        val maxLocalTimeline = (spanDur - 25L).coerceAtLeast(0L)
                        val localTimeline = (ms - sp.startMs).coerceIn(0L, maxLocalTimeline)
                        val localMedia = timelineToSourceMediaMs(sp.clip, localTimeline, spanDur)
                        exoPlayer.seekTo(spanIdx, localMedia)
                    }
                }
            },
            onUpdateZoom = onUpdateZoom,
            onScrubStart = { isPlaying = false },
            onCommitHistory = onCommitHistory,
            onSelectClip = onSelectClip,
            onSelectClipKeyframe = onSelectClipKeyframe,
            onSelectTextKeyframe = onSelectTextKeyframe,
            onSelectClipVolumeKeyframe = onSelectClipVolumeKeyframe,
            onSelectAudioVolumeKeyframe = onSelectAudioVolumeKeyframe,
            onMoveClip = onMoveClip,
            onMoveClipToTrack = onMoveClipToTrack,
            onMoveOverlay = onMoveOverlay,
            onMoveEffect = onMoveEffect,
            onMoveText = onMoveText,
            onMoveAudio = onMoveAudio,
            onMoveAudioToSlot = onMoveAudioToSlot,
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
            onAddClipToTrack = onAppendVideoClipToTrack,
            onAddAudioToSlot = onPickAudioToSlot,
            onAddVideoTrack = onAddVideoTrack,
            onToggleTrackHidden = onToggleTrackHidden,
            onToggleTrackLocked = onToggleTrackLocked,
            onToggleTrackMuted = onToggleTrackMuted,
            onResizeTrackHeight = onResizeTrackHeight,
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
                        val selFx = snapshot.effectLayers.firstOrNull { it.id == selFxId }
                        val canSplitFx = selFx != null && StudioViewModel.canSplitAtPlayhead(playheadMs, selFx.startMs, selFx.endMs)
                        CapCutEffectSelectedBar(
                            canSplit = canSplitFx,
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
                        val canSplitTxt = selectedTextTrack != null &&
                            StudioViewModel.canSplitAtPlayhead(playheadMs, selectedTextTrack.startMs, selectedTextTrack.endMs)
                        CapCutTextSelectedBar(
                            canSplit = canSplitTxt,
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
                        val selOv = snapshot.overlays.firstOrNull { it.id == selOvId }
                        val canSplitOv = selOv != null && StudioViewModel.canSplitAtPlayhead(playheadMs, selOv.startMs, selOv.endMs)
                        CapCutOverlaySelectedBar(
                            canSplit = canSplitOv,
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
                        val selAu = snapshot.audioTracks.firstOrNull { it.id == selAuId }
                        val canSplitAu = selAu != null && StudioViewModel.canSplitAtPlayhead(playheadMs, selAu.startMs, selAu.endMs)
                        CapCutAudioSelectedBar(
                            canSplit = canSplitAu,
                            onBack = { onSelectAudio(null) },
                            onVolume = { activeSheet = ActiveEditorSheet.AUDIO_VOLUME },
                            onSplit = { onSplitAudio(selAuId) },
                            onDuplicate = { onDuplicateAudio(selAuId) },
                            onDelete = { onDeleteAudio(selAuId) },
                        )
                    }
                    selectedClipId != null -> {
                        val canSplit = StudioViewModel.canSplitAtPlayhead(playheadMs, toolbarSpan.startMs, toolbarSpan.endMs)
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
                            onSmoothReverse = onSmoothAutoReverse,
                            onResetClipAll = onResetClipAll,
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
                            isPlayheadOnTarget = isPlayheadOnSelectedTarget,
                            onOffClipAddKeyframeAttempt = { onShowToast("Move playhead onto clip first") },
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
                            baseAdjust = activeClip.adjust,
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
                            isOnKeyframe = isPlayheadOnSelectedTarget && activeClip.keyframes.any {
                                it.id == selectedKeyframeId || abs(it.timeMs - localPlayheadMs) < 140L
                            },
                            hasSelectedKeyframe = selectedKeyframeId != null && activeClip.keyframes.any { it.id == selectedKeyframeId },
                            isPlayheadOnTarget = isPlayheadOnSelectedTarget,
                            onOffClipAddKeyframeAttempt = { onShowToast("Move playhead onto clip first") },
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
                timesliceActive = activeClip.timeslice.enabled,
                onOpenTimeslice = { activeSheet = ActiveEditorSheet.TIMESLICE },
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
                adjust = activeClip.adjust,
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
            ActiveEditorSheet.REVERSE -> ReverseClipsSheet(
                clips = snapshot.clips,
                clipThumbnails = clipThumbnails,
                reverseProgress = reverseProgress,
                onReverseSelected = { ids, revAudio ->
                    onReverseMultipleClips(ids, false, revAudio)
                },
                onReverseAll = { revAudio ->
                    val allIds = snapshot.clips.filter { !it.sourceUriString.isNullOrBlank() }.map { it.id }
                    onReverseMultipleClips(allIds, true, revAudio)
                },
                onSmoothReverse = onSmoothAutoReverse,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
            ActiveEditorSheet.SMOOTH_REVERSE -> SmoothReverseSettingsSheet(
                clip = activeClip,
                onUpdateConfig = onUpdateSmoothReverseConfig,
                onUndo = onUndo,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
            ActiveEditorSheet.TIMESLICE -> TimesliceToolSheet(
                clip = activeClip,
                onApplyCapCutMode = onApplyCapCutTimeslice,
                onApplyNodeMode = onApplyNodeTimeslice,
                onUpdateTimeslice = onUpdateTimeslice,
                onTogglePreview = onToggleTimeslicePreview,
                onRemoveTimeslice = onRemoveTimeslice,
                onSaveAsPreset = onSaveTimeslicePreset,
                onClose = { activeSheet = ActiveEditorSheet.NONE },
            )
        }
    }

    if (reverseProgress != null && activeSheet != ActiveEditorSheet.REVERSE) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = ObsidianSurfaceHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .border(1.dp, CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Reversing ${reverseProgress.clipName} (${reverseProgress.currentClipIndex}/${reverseProgress.totalClips})",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { (reverseProgress.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                        color = CyberCyan,
                        trackColor = ObsidianSurfaceContainer,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(reverseProgress.statusLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${reverseProgress.percent}%", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                    }
                }
            }
        }
    }

    if (showExportSheet && (selectedVideo != null || hasAnyVideoMedia)) {
        val exportMeta = selectedVideo ?: spans.firstOrNull { !it.clip.effectiveUriString().isNullOrBlank() }?.let { sp ->
            VideoMetadata(
                uri = android.net.Uri.parse(sp.clip.effectiveUriString()!!),
                displayName = sp.clip.displayName,
                width = sp.clip.width,
                height = sp.clip.height,
                durationMs = sp.clip.sourceDurationMs,
            )
        }
        if (exportMeta != null) {
            ExportModalSheet(
                video = exportMeta,
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
}

@OptIn(UnstableApi::class)
@Composable
private fun UpperVideoTrackPreviewLayer(
    trackIndex: Int,
    snapshot: TimelineSnapshot,
    canvasFitMode: CanvasFitMode,
    projectFps: Int,
    bypassEffects: Boolean,
    playheadMs: Long,
    isPlaying: Boolean,
    selectedClipId: String?,
    halfW: Float,
    halfH: Float,
) {
    val context = LocalContext.current
    val trackSpans = remember(snapshot.clips, trackIndex) { computeTrackSpans(snapshot.clips, trackIndex) }
    val activeIdx = findActiveSpanIndex(trackSpans, playheadMs)
    val activeSpan = if (activeIdx >= 0) trackSpans[activeIdx] else null
    val fallbackClip = activeSpan?.clip ?: trackSpans.firstOrNull()?.clip ?: return

    val localMs = activeSpan?.let { (playheadMs - it.startMs).coerceIn(0L, it.clip.timelineDurationMs()) } ?: 0L
    val evalTransform = remember(
        fallbackClip.id,
        fallbackClip.keyframes,
        fallbackClip.transform,
        fallbackClip.opacity,
        fallbackClip.adjust,
        localMs,
        snapshot.filterIntensity,
    ) {
        KeyframeEvaluator.evaluate(
            keyframes = fallbackClip.keyframes,
            localTimeMs = localMs,
            baseTransform = fallbackClip.transform,
            baseOpacity = fallbackClip.opacity,
            baseAdjust = fallbackClip.adjust,
            baseFilterIntensity = snapshot.filterIntensity,
        )
    }

    val upperHiddenCapCutFxIds = remember(snapshot.clips) {
        snapshot.clips.filter { it.timeslice.isCapCutModeActive() && !it.timeslice.previewEnabled }
            .mapNotNull { it.timeslice.capcutEffectLayerId }.toSet()
    }
    val upperHiddenCapCutTransIds = remember(snapshot.clips) {
        snapshot.clips.filter { it.timeslice.isCapCutModeActive() && !it.timeslice.previewEnabled }
            .mapNotNull { it.timeslice.capcutTransitionAfterClipId }.toSet()
    }
    val upperSharedEffects = remember(snapshot.effectLayers, upperHiddenCapCutFxIds) {
        snapshot.effectLayers.filter { it.applyToAllVideos && it.id !in upperHiddenCapCutFxIds }
    }
    val upperVisibleTransitions = remember(snapshot.transitions, upperHiddenCapCutTransIds) {
        if (upperHiddenCapCutTransIds.isEmpty()) snapshot.transitions
        else snapshot.transitions.filterNot { it.afterClipId in upperHiddenCapCutTransIds }
    }
    val currentUpperOffsetMs = activeSpan?.startMs ?: trackSpans.firstOrNull()?.startMs ?: 0L

    val shaderParamsRef = remember { mutableStateOf(LunaShaderParams()) }
    val transitionWindows = remember(trackSpans, upperVisibleTransitions) {
        VideoEffectsEngine.buildTransitionWindows(trackSpans, upperVisibleTransitions)
    }
    LaunchedEffect(snapshot, fallbackClip, bypassEffects, upperSharedEffects, transitionWindows, currentUpperOffsetMs) {
        shaderParamsRef.value = VideoEffectsEngine.makeShaderParams(
            clip = fallbackClip,
            adjust = fallbackClip.adjust,
            filterPreset = snapshot.activePreset,
            filterIntensity = snapshot.filterIntensity,
            effectLayers = upperSharedEffects,
            transitionWindows = transitionWindows,
            bypass = bypassEffects,
            timelineOffsetMs = currentUpperOffsetMs,
        )
    }

    val upperPlayer = remember(context, trackIndex) {
        buildPreviewExoPlayer(context)
    }
    DisposableEffect(upperPlayer) {
        onDispose { upperPlayer.release() }
    }

    LaunchedEffect(trackSpans) {
        val items = trackSpans.mapNotNull { sp ->
            val u = sp.clip.effectiveUriString()?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val effStart = sp.clip.effectiveTrimStartMs().coerceAtLeast(0L)
            val effEnd = sp.clip.effectiveTrimEndMs().coerceAtLeast(effStart + 250L)
            val effSrcDur = sp.clip.effectiveSourceDurationMs().coerceAtLeast(effEnd)
            val mb = MediaItem.Builder().setUri(u)
            if (effStart > 30L || effEnd < effSrcDur - 30L) {
                mb.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(effStart)
                        .setEndPositionMs(effEnd)
                        .build()
                )
            }
            mb.build()
        }
        if (items.isNotEmpty()) {
            upperPlayer.setMediaItems(items, false)
            upperPlayer.prepare()
        }
    }

    LaunchedEffect(
        trackIndex,
        bypassEffects,
        snapshot.activePreset.id,
        snapshot.activePreset.lutData,
        snapshot.filterIntensity,
        fallbackClip.adjust.rgbCurves,
        fallbackClip.adjust.hsl,
        fallbackClip.adjust.colorWheel,
        fallbackClip.adjust.highlights,
        fallbackClip.adjust.shadows,
        fallbackClip.adjust.whites,
        fallbackClip.adjust.blacks,
        fallbackClip.adjust.brilliance,
        fallbackClip.adjust.fade,
        fallbackClip.stabilize,
    ) {
        val fx = VideoEffectsEngine.buildPreviewEffects(
            context = context,
            activeClip = fallbackClip,
            allClips = snapshot.clips,
            transitions = upperVisibleTransitions,
            effectLayers = upperSharedEffects,
            overlays = snapshot.overlays,
            texts = snapshot.textTracks,
            adjust = fallbackClip.adjust,
            filterPreset = snapshot.activePreset,
            filterIntensity = snapshot.filterIntensity,
            shaderParamsProvider = { shaderParamsRef.value },
            bypass = bypassEffects,
        )
        upperPlayer.setVideoEffects(fx)
    }

    LaunchedEffect(playheadMs, isPlaying, activeIdx, trackSpans) {
        if (activeIdx in trackSpans.indices) {
            val sp = trackSpans[activeIdx]
            val spanDur = (sp.endMs - sp.startMs).coerceAtLeast(1L)
            val maxLocal = (spanDur - 25L).coerceAtLeast(0L)
            val locTl = (playheadMs - sp.startMs).coerceIn(0L, maxLocal)
            val locMedia = timelineToSourceMediaMs(sp.clip, locTl, spanDur)
            val drift = abs(upperPlayer.currentPosition - locMedia)
            if (!isPlaying || upperPlayer.currentMediaItemIndex != activeIdx || drift > 140L) {
                upperPlayer.seekTo(activeIdx, locMedia)
            }
            upperPlayer.playWhenReady = isPlaying
        } else {
            upperPlayer.playWhenReady = false
            // Preload/seek upcoming upper-track clip ~550ms before playhead reaches it
            val upcomingIdx = trackSpans.indexOfFirst {
                it.startMs > playheadMs && (it.startMs - playheadMs) <= 550L
            }
            if (upcomingIdx in trackSpans.indices && upperPlayer.currentMediaItemIndex != upcomingIdx) {
                upperPlayer.seekTo(upcomingIdx, 0L)
            }
        }
    }

    val isTrackMuted = snapshot.isVideoTrackMuted(trackIndex)
    LaunchedEffect(
        fallbackClip.id,
        fallbackClip.speed,
        fallbackClip.volumePercent,
        fallbackClip.volumeKeyframes,
        fallbackClip.isMuted,
        isTrackMuted,
        localMs,
        bypassEffects,
    ) {
        val spanDur = ((activeSpan?.endMs ?: 0L) - (activeSpan?.startMs ?: 0L)).coerceAtLeast(1L)
        val spd = if (bypassEffects) {
            1f
        } else if (fallbackClip.speed.mode == SpeedMode.NORMAL) {
            fallbackClip.speed.effectiveSpeedMultiplier().coerceIn(0.1f, 8f)
        } else {
            val tp = (localMs.toFloat() / spanDur.toFloat()).coerceIn(0f, 1f)
            val sp = fallbackClip.speed.sourceProgressAtTimelineProgress(tp)
            fallbackClip.speed.speedAtProgress(sp).coerceIn(0.1f, 8f)
        }
        val pitch = if (!bypassEffects && fallbackClip.speed.changeAudioPitch) spd.coerceIn(0.4f, 2.5f) else 1f
        upperPlayer.playbackParameters = PlaybackParameters(spd, pitch)
        val evalVol = VolumeKeyframeEvaluator.evaluate(
            keyframes = fallbackClip.volumeKeyframes,
            localTimeMs = localMs,
            baseVolumePercent = fallbackClip.volumePercent,
        )
        upperPlayer.volume = if (fallbackClip.isMuted || isTrackMuted || activeSpan == null) 0f else (evalVol / 100f).coerceIn(0f, 1f)
    }

    if (activeSpan != null) {
        val punchFraction = if (fallbackClip.cutFlashPunchOnStart && localMs in 0L..55L) {
            1f - (localMs / 55f)
        } else 0f
        val timesliceActive = !bypassEffects &&
            fallbackClip.timeslice.isNodeModeActive() &&
            fallbackClip.timeslice.previewEnabled
        val timeslicePool = remember { TimeslicePreviewFramePool() }
        var timesliceCacheTick by remember { mutableIntStateOf(0) }
        val effUri = remember(fallbackClip.id, fallbackClip.isReversed, fallbackClip.reversedUriString) {
            fallbackClip.effectiveUriString()?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
        }
        val effStart = fallbackClip.effectiveTrimStartMs()
        val effEnd = fallbackClip.effectiveTrimEndMs()
        val spanDur = (activeSpan.endMs - activeSpan.startMs).coerceAtLeast(1L)
        val currentSourceMs = effStart + timelineToSourceMediaMs(fallbackClip, localMs, spanDur)
        val warmBucket = if (isPlaying) (currentSourceMs / 650L) * 650L else currentSourceMs

        LaunchedEffect(
            timesliceActive,
            effUri,
            effStart,
            effEnd,
            warmBucket,
            projectFps,
            fallbackClip.timeslice.sliceCount,
            fallbackClip.timeslice.sliceAngleDeg,
            fallbackClip.timeslice.frameInterval,
            fallbackClip.timeslice.frameOffset,
            fallbackClip.timeslice.sliceFeather,
            fallbackClip.timeslice.animate,
        ) {
            if (timesliceActive && effUri != null) {
                timeslicePool.ensureWindowFrames(
                    context = context,
                    uri = effUri,
                    cacheKey = "${effUri}_${effStart}_${effEnd}",
                    clipStartMs = effStart,
                    clipEndMs = effEnd,
                    centerTimeMs = currentSourceMs,
                    settings = fallbackClip.timeslice,
                    fps = projectFps.toFloat(),
                    onBatchDecoded = { timesliceCacheTick++ },
                )
            }
        }

        val isSelected = fallbackClip.id == selectedClipId
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(evalTransform.opacity.coerceIn(0f, 1f))
                .graphicsLayer {
                    val punchScale = 1f + punchFraction * 0.08f
                    val scale = (evalTransform.zoomPercent / 100f).coerceIn(0.25f, 5f) * fallbackClip.crop.scaleCrop * punchScale
                    val mirrorSign = if (fallbackClip.transform.mirrorHorizontal) -1f else 1f
                    translationX = evalTransform.posX * halfW
                    translationY = -evalTransform.posY * halfH
                    scaleX = scale * mirrorSign
                    scaleY = scale
                    rotationZ = evalTransform.rotateDeg + fallbackClip.crop.rotateDeg
                }
                .then(
                    if (isSelected) Modifier.border(2.dp, CyberCyan, RoundedCornerShape(4.dp))
                    else Modifier
                ),
        ) {
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
                        player = upperPlayer
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
            if (timesliceActive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tick = timesliceCacheTick
                    if (tick >= 0) {
                        drawIntoCanvas { canvas ->
                            TimesliceCompositor.drawTimesliceFrame(
                                canvas = canvas.nativeCanvas,
                                width = size.width,
                                height = size.height,
                                baseTimeMs = currentSourceMs,
                                clipStartMs = effStart,
                                clipEndMs = effEnd,
                                settings = fallbackClip.timeslice,
                                fps = projectFps.toFloat(),
                                fitMode = canvasFitMode,
                                frameProvider = { reqMs -> timeslicePool.getNearestBitmap(reqMs) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun AudioSlotPreviewSync(
    audioPlayer: ExoPlayer,
    audioTracks: List<AudioTrackItem>,
    slotIndex: Int,
    isTrackMuted: Boolean,
    playheadMs: Long,
    isPlaying: Boolean,
) {
    val activeTrack: AudioTrackItem? = remember(audioTracks, slotIndex, playheadMs) {
        val slotItems = audioTracks.filter { it.trackIndex.coerceIn(0, 1) == slotIndex }
        slotItems.lastOrNull { au ->
            val hasNext = slotItems.any { o -> o.id != au.id && abs(o.startMs - au.endMs) <= 20L }
            au.uriString != null && isTimelineItemActiveAt(au.startMs, au.endMs, playheadMs, hasNext)
        }
    }
    val latestPlayheadMs by rememberUpdatedState(playheadMs)
    LaunchedEffect(activeTrack?.id, activeTrack?.uriString, isPlaying, isTrackMuted) {
        val track = activeTrack
        val uriStr = track?.uriString
        if (!isPlaying || track == null || uriStr == null || isTrackMuted) {
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
    LaunchedEffect(activeTrack?.id, activeTrack?.volumePercent, activeTrack?.volumeKeyframes, playheadMs, isPlaying, isTrackMuted) {
        val track = activeTrack ?: return@LaunchedEffect
        if (isPlaying) {
            if (isTrackMuted) {
                audioPlayer.volume = 0f
            } else {
                val localAu = (playheadMs - track.startMs).coerceIn(0L, (track.endMs - track.startMs).coerceAtLeast(1L))
                val evalAuVol = VolumeKeyframeEvaluator.evaluate(track.volumeKeyframes, localAu, track.volumePercent)
                audioPlayer.volume = (evalAuVol / 100f).coerceIn(0f, 1f)
            }
        }
    }
}

private fun timelineToSourceMediaMs(
    clip: VideoClipSegment,
    localTimelineMs: Long,
    spanDurationMs: Long,
): Long {
    val baseDur = clip.baseDurationMs().coerceAtLeast(1L)
    if (clip.speed.mode == SpeedMode.NORMAL) {
        val spd = clip.speed.effectiveSpeedMultiplier().coerceIn(0.1f, 8f)
        return (localTimelineMs * spd).toLong().coerceIn(0L, (baseDur - 15L).coerceAtLeast(0L))
    }
    val spanDur = spanDurationMs.coerceAtLeast(1L)
    val tp = (localTimelineMs.toFloat() / spanDur.toFloat()).coerceIn(0f, 1f)
    val sp = clip.speed.sourceProgressAtTimelineProgress(tp)
    return (sp * baseDur).toLong().coerceIn(0L, (baseDur - 15L).coerceAtLeast(0L))
}

private fun sourceMediaToTimelineMs(
    clip: VideoClipSegment,
    sourceMediaMs: Long,
    spanDurationMs: Long,
): Long {
    val baseDur = clip.baseDurationMs().coerceAtLeast(1L)
    if (clip.speed.mode == SpeedMode.NORMAL) {
        val spd = clip.speed.effectiveSpeedMultiplier().coerceIn(0.1f, 8f)
        return (sourceMediaMs / spd).toLong().coerceIn(0L, spanDurationMs.coerceAtLeast(1L))
    }
    val spanDur = spanDurationMs.coerceAtLeast(1L)
    val sp = (sourceMediaMs.toFloat() / baseDur.toFloat()).coerceIn(0f, 1f)
    val tp = clip.speed.timelineProgressAtSourceProgress(sp)
    return (tp * spanDur).toLong().coerceIn(0L, spanDur)
}

private fun formatShortTime(ms: Long): String {
    val sec = (ms.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.US, "%02d:%02d", sec / 60L, sec % 60L)
}

@OptIn(UnstableApi::class)
private fun buildPreviewExoPlayer(context: android.content.Context): ExoPlayer {
    val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setMaxVideoSize(1280, 720)
                .build()
        )
    }
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(500, 4_000, 150, 250)
        .build()
    return ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.EXACT)
        }
}
