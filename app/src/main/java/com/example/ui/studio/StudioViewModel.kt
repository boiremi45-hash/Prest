package com.example.ui.studio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.data.model.AdjustSettings
import com.example.data.model.AnimationSettings
import com.example.data.model.AspectPreset
import com.example.data.model.AudioTrackItem
import com.example.data.model.CanvasFitMode
import com.example.data.model.CapCutEffectType
import com.example.data.model.ClipTimelineSpan
import com.example.data.model.CropSettings
import com.example.data.model.CurvePoint
import com.example.data.model.EasingGraph
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.model.KeyframeEvaluator
import com.example.data.model.KeyframeNode
import com.example.data.model.LunaProjectSummary
import com.example.data.model.MaskSettings
import com.example.data.model.MotionBlurSettings
import com.example.data.model.OverlayTrackItem
import com.example.data.model.RgbCurvesSpec
import com.example.data.model.SpeedSettings
import com.example.data.model.StabilizeMode
import com.example.data.model.TextKeyframeEvaluator
import com.example.data.model.TextKeyframeNode
import com.example.data.model.TextTrackItem
import com.example.data.model.TransformSettings
import com.example.data.model.TransitionSpec
import com.example.data.model.TransitionType
import com.example.data.model.VideoClipSegment
import com.example.data.model.VideoEffectLayer
import com.example.data.model.VolumeKeyframeEvaluator
import com.example.data.model.VolumeKeyframeNode
import com.example.data.model.fillCanvasZoomPercent
import com.example.data.repository.PresetRepository
import com.example.domain.ffx.FfxParser
import com.example.domain.video.ExportConfig
import com.example.domain.video.ExportUiState
import com.example.domain.video.UiState
import com.example.domain.video.VideoEffectsEngine
import com.example.domain.video.VideoExportManager
import com.example.domain.video.VideoMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs

data class TimelineSnapshot(
    val clips: List<VideoClipSegment>,
    val transitions: List<TransitionSpec> = emptyList(),
    val effectLayers: List<VideoEffectLayer> = emptyList(),
    val overlays: List<OverlayTrackItem> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    val textTracks: List<TextTrackItem> = emptyList(),
    val adjust: AdjustSettings = AdjustSettings(),
    val activePreset: FfxPreset,
    val filterIntensity: Float = 1.0f,
    val hasEndingCard: Boolean = false,
    val canvasBackgroundArgb: Long = 0xFF05060AL,
)

@UnstableApi
class StudioViewModel(
    private val repository: PresetRepository,
    private val exportManager: VideoExportManager,
) : ViewModel() {

    val presetsState: StateFlow<UiState<List<FfxPreset>>> = repository.observePresets()
        .map<List<FfxPreset>, UiState<List<FfxPreset>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing presets", error)
            emit(UiState.Error(error.message ?: "Failed to load preset library"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val exportsState: StateFlow<UiState<List<ExportRecord>>> = repository.observeExports()
        .map<List<ExportRecord>, UiState<List<ExportRecord>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(UiState.Error(error.message ?: "Failed to load export history"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val starterPreset = PresetRepository.starterFfxPresets().first()
    private val _selectedVideo = MutableStateFlow<VideoMetadata?>(null)
    val selectedVideo: StateFlow<VideoMetadata?> = _selectedVideo.asStateFlow()

    private val _filmstripThumbnails = MutableStateFlow<List<ImageBitmap>>(emptyList())
    val filmstripThumbnails: StateFlow<List<ImageBitmap>> = _filmstripThumbnails.asStateFlow()

    private val _snapshot = MutableStateFlow(
        TimelineSnapshot(
            clips = listOf(VideoClipSegment(id = "clip_main_1", displayName = "Import Video Clip")),
            activePreset = starterPreset,
        )
    )
    val snapshot: StateFlow<TimelineSnapshot> = _snapshot.asStateFlow()

    val activePreset: StateFlow<FfxPreset> = _snapshot
        .map { it.activePreset }
        .stateIn(viewModelScope, SharingStarted.Eagerly, starterPreset)

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _selectedOverlayId = MutableStateFlow<String?>(null)
    val selectedOverlayId: StateFlow<String?> = _selectedOverlayId.asStateFlow()

    private val _selectedEffectId = MutableStateFlow<String?>(null)
    val selectedEffectId: StateFlow<String?> = _selectedEffectId.asStateFlow()

    private val _selectedTextId = MutableStateFlow<String?>(null)
    val selectedTextId: StateFlow<String?> = _selectedTextId.asStateFlow()

    private val _selectedAudioId = MutableStateFlow<String?>(null)
    val selectedAudioId: StateFlow<String?> = _selectedAudioId.asStateFlow()

    private val _keyframeModeEnabled = MutableStateFlow(false)
    val keyframeModeEnabled: StateFlow<Boolean> = _keyframeModeEnabled.asStateFlow()

    private val _selectedKeyframeId = MutableStateFlow<String?>(null)
    val selectedKeyframeId: StateFlow<String?> = _selectedKeyframeId.asStateFlow()

    private val _selectedVolumeKeyframeId = MutableStateFlow<String?>(null)
    val selectedVolumeKeyframeId: StateFlow<String?> = _selectedVolumeKeyframeId.asStateFlow()

    private val _bypassEffects = MutableStateFlow(false)
    val bypassEffects: StateFlow<Boolean> = _bypassEffects.asStateFlow()

    private val _projectTitle = MutableStateFlow("LUNA Studio Edit")
    val projectTitle: StateFlow<String> = _projectTitle.asStateFlow()

    private val _aspectPreset = MutableStateFlow(AspectPreset.RATIO_9_16)
    val aspectPreset: StateFlow<AspectPreset> = _aspectPreset.asStateFlow()

    private val _projectResolution = MutableStateFlow("1080p")
    val projectResolution: StateFlow<String> = _projectResolution.asStateFlow()

    private val _projectFps = MutableStateFlow(30)
    val projectFps: StateFlow<Int> = _projectFps.asStateFlow()

    private val _canvasFitMode = MutableStateFlow(CanvasFitMode.FIT)
    val canvasFitMode: StateFlow<CanvasFitMode> = _canvasFitMode.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _timelineZoom = MutableStateFlow(1.0f)
    val timelineZoom: StateFlow<Float> = _timelineZoom.asStateFlow()

    private val undoStack = ArrayDeque<TimelineSnapshot>()
    private val redoStack = ArrayDeque<TimelineSnapshot>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _recentProjects = MutableStateFlow(initialProjectsList(starterPreset))
    val recentProjects: StateFlow<List<LunaProjectSummary>> = _recentProjects.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        seedStarterPackIfEmpty()
    }

    fun seedStarterPackIfEmpty() {
        viewModelScope.launch {
            repository.seedStarterPackIfEmpty().onSuccess { added ->
                if (added > 0) _statusMessage.value = "Loaded $added LUNA studio presets."
            }
        }
    }

    fun clearStatusMessage() { _statusMessage.value = null }
    fun toggleBypass() { _bypassEffects.value = !_bypassEffects.value }
    fun updateTimelineZoom(zoom: Float) { _timelineZoom.value = zoom.coerceIn(0.45f, 3.8f) }
    fun setKeyframeMode(enabled: Boolean) { _keyframeModeEnabled.value = enabled }
    fun toggleKeyframeMode() { _keyframeModeEnabled.value = !_keyframeModeEnabled.value }

    fun clearAllTrackSelections() {
        _selectedClipId.value = null
        _selectedOverlayId.value = null
        _selectedEffectId.value = null
        _selectedTextId.value = null
        _selectedAudioId.value = null
        _selectedKeyframeId.value = null
        _selectedVolumeKeyframeId.value = null
    }

    fun selectClip(clipId: String?) {
        clearAllTrackSelections()
        _selectedClipId.value = clipId
    }

    fun selectOverlay(overlayId: String?) {
        clearAllTrackSelections()
        _selectedOverlayId.value = overlayId
    }

    fun selectEffectLayer(effectId: String?) {
        clearAllTrackSelections()
        _selectedEffectId.value = effectId
    }

    fun selectTextTrack(textId: String?) {
        clearAllTrackSelections()
        _selectedTextId.value = textId
    }

    fun selectAudioTrack(audioId: String?) {
        clearAllTrackSelections()
        _selectedAudioId.value = audioId
    }

    fun commitTimelineHistory() {
        undoStack.addLast(_snapshot.value)
        if (undoStack.size > 35) undoStack.removeFirst()
        redoStack.clear()
        syncUndoRedoFlags()
    }

    fun createNewProject(
        title: String,
        aspect: AspectPreset,
        resolutionLabel: String,
        fps: Int,
        preset: FfxPreset = _snapshot.value.activePreset,
    ) {
        _projectTitle.value = title.trim().ifBlank { "LUNA Project ${_recentProjects.value.size + 1}" }
        _aspectPreset.value = aspect
        _projectResolution.value = resolutionLabel
        _projectFps.value = fps
        _bypassEffects.value = false
        undoStack.clear()
        redoStack.clear()
        syncUndoRedoFlags()
        val video = _selectedVideo.value
        val dur = video?.durationMs?.takeIf { it > 400L } ?: 8_000L
        _snapshot.value = TimelineSnapshot(
            clips = listOf(
                VideoClipSegment(
                    id = "clip_main_1",
                    sourceUriString = video?.uri?.toString(),
                    displayName = video?.displayName ?: "Import Video Clip",
                    trimStartMs = 0L,
                    trimEndMs = dur,
                    sourceDurationMs = dur,
                )
            ),
            activePreset = preset,
        )
        _selectedClipId.value = null
        _playheadMs.value = 0L
        upsertCurrentProjectSummary(dur)
    }

    fun openProjectSummary(project: LunaProjectSummary, presets: List<FfxPreset>) {
        _projectTitle.value = project.title
        _aspectPreset.value = project.aspectPreset
        _projectResolution.value = project.resolutionLabel
        _projectFps.value = project.fps
        val matched = presets.firstOrNull { it.name == project.activePresetName } ?: _snapshot.value.activePreset
        applyPreset(matched)
    }

    fun updateProjectSettings(title: String, aspect: AspectPreset, res: String, fps: Int) {
        _projectTitle.value = title.trim().ifBlank { _projectTitle.value }
        _aspectPreset.value = aspect
        _projectResolution.value = res
        _projectFps.value = fps
        upsertCurrentProjectSummary(totalTimelineDurationMs())
    }

    fun setAspectPreset(aspect: AspectPreset) {
        _aspectPreset.value = aspect
    }

    fun cycleCanvasFitMode() {
        val entries = CanvasFitMode.entries
        _canvasFitMode.value = entries[(entries.indexOf(_canvasFitMode.value) + 1) % entries.size]
    }

    fun selectVideo(context: Context, uri: Uri, replaceSelectedClip: Boolean = false, appendNew: Boolean = false) {
        viewModelScope.launch {
            val metadata = VideoExportManager.readVideoMetadata(context, uri)
            val dur = metadata.durationMs.takeIf { it > 400L } ?: 8_000L
            val thumbs = VideoExportManager.extractFilmstripThumbnails(context, uri, dur, count = 10)
            if (thumbs.isNotEmpty() && (!appendNew || _filmstripThumbnails.value.isEmpty())) {
                _filmstripThumbnails.value = thumbs
            }
            if (_selectedVideo.value == null || !appendNew) {
                _selectedVideo.value = metadata
            }
            _exportState.value = ExportUiState.Idle
            val nextTimelineStart = _snapshot.value.clips
                .maxOfOrNull { it.timelineStartMs + it.timelineDurationMs() } ?: 0L
            val newSegment = VideoClipSegment(
                sourceUriString = uri.toString(),
                displayName = metadata.displayName,
                timelineStartMs = if (appendNew) nextTimelineStart else 0L,
                trimStartMs = 0L,
                trimEndMs = dur,
                sourceDurationMs = dur,
            )
            mutate { s ->
                val updatedClips = when {
                    appendNew && _selectedVideo.value != null && s.clips.any { it.sourceUriString != null } ->
                        s.clips + newSegment
                    replaceSelectedClip && _selectedClipId.value != null ->
                        s.clips.map {
                            if (it.id == _selectedClipId.value) newSegment.copy(id = it.id, timelineStartMs = it.timelineStartMs) else it
                        }
                    else -> listOf(newSegment.copy(id = "clip_main_1", timelineStartMs = 0L))
                }
                s.copy(clips = updatedClips)
            }
            if (!appendNew) _playheadMs.value = 0L
            upsertCurrentProjectSummary(totalTimelineDurationMs())
        }
    }

    fun totalTimelineDurationMs(): Long {
        val snap = _snapshot.value
        val clipEnd = VideoEffectsEngine.computeClipSpans(snap.clips).maxOfOrNull { it.endMs } ?: 0L
        val ovEnd = snap.overlays.maxOfOrNull { it.endMs } ?: 0L
        val fxEnd = snap.effectLayers.maxOfOrNull { it.endMs } ?: 0L
        val txEnd = snap.textTracks.maxOfOrNull { it.endMs } ?: 0L
        val auEnd = snap.audioTracks.maxOfOrNull { it.endMs } ?: 0L
        val main = maxOf(clipEnd, ovEnd, fxEnd, txEnd, auEnd)
        val ending = if (snap.hasEndingCard) 2_000L else 0L
        return (main + ending).coerceAtLeast(1_000L)
    }

    fun clipSpans(): List<ClipTimelineSpan> = VideoEffectsEngine.computeClipSpans(_snapshot.value.clips)

    fun activeClipAtPlayhead(): VideoClipSegment {
        val spans = clipSpans()
        val t = _playheadMs.value
        val byId = _selectedClipId.value?.let { id -> _snapshot.value.clips.firstOrNull { it.id == id } }
        val activeIdx = com.example.data.model.findActiveSpanIndex(spans, t)
        return byId ?: spans.getOrNull(activeIdx)?.clip ?: spans.firstOrNull()?.clip ?: _snapshot.value.clips.first()
    }

    fun updatePlayhead(ms: Long) {
        _playheadMs.value = ms.coerceIn(0L, totalTimelineDurationMs())
    }

    // --- Clip Editing Operations ---
    fun splitSelectedOrActiveClip() {
        val spans = clipSpans()
        val t = _playheadMs.value
        val activeIdx = com.example.data.model.findActiveSpanIndex(spans, t)
        val targetSpan = (_selectedClipId.value?.let { id -> spans.firstOrNull { it.clip.id == id } }
            ?: spans.getOrNull(activeIdx)) ?: return
        val clip = targetSpan.clip
        val localMs = t - targetSpan.startMs
        val clipLen = targetSpan.endMs - targetSpan.startMs
        if (localMs < 300L || clipLen - localMs < 300L) {
            _statusMessage.value = "Move playhead inside clip to split."
            return
        }
        val spd = clip.speed.effectiveSpeedMultiplier().coerceAtLeast(0.1f)
        val splitSourceDelta = (localMs * spd).toLong()
        val splitSourceMs = (clip.trimStartMs + splitSourceDelta)
            .coerceIn(clip.trimStartMs + 250L, clip.trimEndMs - 250L)

        val first = clip.copy(
            timelineStartMs = targetSpan.startMs,
            trimEndMs = splitSourceMs,
            keyframes = clip.keyframes.filter { it.timeMs <= localMs },
            volumeKeyframes = clip.volumeKeyframes.filter { it.timeMs <= localMs },
        )
        val firstDur = first.timelineDurationMs()
        val secondId = UUID.randomUUID().toString()
        val second = clip.copy(
            id = secondId,
            timelineStartMs = targetSpan.startMs + firstDur,
            trimStartMs = splitSourceMs,
            keyframes = clip.keyframes.filter { it.timeMs >= localMs }.map { it.copy(timeMs = it.timeMs - localMs) },
            volumeKeyframes = clip.volumeKeyframes.filter { it.timeMs >= localMs }.map { it.copy(timeMs = it.timeMs - localMs) },
        )
        mutate { s ->
            val newClips = s.clips.flatMap { if (it.id == clip.id) listOf(first, second) else listOf(it) }
            val defaultTr = TransitionSpec(afterClipId = first.id, type = TransitionType.NONE, durationSec = 0.4f)
            s.copy(clips = newClips, transitions = s.transitions + defaultTr)
        }
        _selectedClipId.value = secondId
    }

    fun moveVideoClip(clipId: String, deltaMs: Long) {
        if (deltaMs == 0L) return
        mutate(pushHistory = false) { s ->
            s.copy(
                clips = s.clips.map { clip ->
                    if (clip.id != clipId) clip
                    else clip.copy(timelineStartMs = (clip.timelineStartMs + deltaMs).coerceAtLeast(0L))
                }
            )
        }
    }

    fun moveOverlay(overlayId: String, deltaMs: Long) {
        if (deltaMs == 0L) return
        mutate(pushHistory = false) { s ->
            s.copy(
                overlays = s.overlays.map { ov ->
                    if (ov.id != overlayId) ov
                    else {
                        val dur = (ov.endMs - ov.startMs).coerceAtLeast(300L)
                        val newStart = (ov.startMs + deltaMs).coerceAtLeast(0L)
                        ov.copy(startMs = newStart, endMs = newStart + dur)
                    }
                }
            )
        }
    }

    fun moveEffectLayer(effectId: String, deltaMs: Long) {
        if (deltaMs == 0L) return
        mutate(pushHistory = false) { s ->
            s.copy(
                effectLayers = s.effectLayers.map { fx ->
                    if (fx.id != effectId) fx
                    else {
                        val dur = (fx.endMs - fx.startMs).coerceAtLeast(300L)
                        val newStart = (fx.startMs + deltaMs).coerceAtLeast(0L)
                        fx.copy(startMs = newStart, endMs = newStart + dur)
                    }
                }
            )
        }
    }

    fun moveTextTrack(textId: String, deltaMs: Long) {
        if (deltaMs == 0L) return
        mutate(pushHistory = false) { s ->
            s.copy(
                textTracks = s.textTracks.map { tx ->
                    if (tx.id != textId) tx
                    else {
                        val dur = (tx.endMs - tx.startMs).coerceAtLeast(300L)
                        val newStart = (tx.startMs + deltaMs).coerceAtLeast(0L)
                        tx.copy(startMs = newStart, endMs = newStart + dur)
                    }
                }
            )
        }
    }

    fun moveAudioTrack(audioId: String, deltaMs: Long) {
        if (deltaMs == 0L) return
        mutate(pushHistory = false) { s ->
            s.copy(
                audioTracks = s.audioTracks.map { au ->
                    if (au.id != audioId) au
                    else {
                        val dur = (au.endMs - au.startMs).coerceAtLeast(300L)
                        val newStart = (au.startMs + deltaMs).coerceAtLeast(0L)
                        au.copy(startMs = newStart, endMs = newStart + dur)
                    }
                }
            )
        }
    }

    fun trimClipEdge(clipId: String, isStart: Boolean, deltaTimelineMs: Long) {
        mutate(pushHistory = false) { s ->
            val next = s.clips.map { clip ->
                if (clip.id != clipId) clip
                else {
                    val spd = clip.speed.effectiveSpeedMultiplier().coerceAtLeast(0.1f)
                    val deltaSourceMs = (deltaTimelineMs * spd).toLong()
                    if (isStart) {
                        val newSourceStart = (clip.trimStartMs + deltaSourceMs).coerceIn(0L, clip.trimEndMs - 300L)
                        val actualSourceDelta = newSourceStart - clip.trimStartMs
                        val actualTimelineDelta = (actualSourceDelta / spd).toLong()
                        val newTimelineStart = (clip.timelineStartMs + actualTimelineDelta).coerceAtLeast(0L)
                        clip.copy(trimStartMs = newSourceStart, timelineStartMs = newTimelineStart)
                    } else {
                        val newEnd = (clip.trimEndMs + deltaSourceMs).coerceIn(clip.trimStartMs + 300L, clip.sourceDurationMs)
                        clip.copy(trimEndMs = newEnd)
                    }
                }
            }
            s.copy(clips = next)
        }
    }

    fun trimEffectEdge(effectId: String, isStart: Boolean, deltaMs: Long) {
        val baseMax = totalTimelineDurationMs().coerceAtLeast(1_000L)
        mutate(pushHistory = false) { s ->
            s.copy(
                effectLayers = s.effectLayers.map { fx ->
                    if (fx.id != effectId) fx
                    else if (isStart) {
                        fx.copy(startMs = (fx.startMs + deltaMs).coerceIn(0L, fx.endMs - 300L))
                    } else {
                        val maxEnd = maxOf(baseMax + 4_000L, fx.endMs)
                        fx.copy(endMs = (fx.endMs + deltaMs).coerceIn(fx.startMs + 300L, maxEnd))
                    }
                }
            )
        }
    }

    fun trimOverlayEdge(overlayId: String, isStart: Boolean, deltaMs: Long) {
        val baseMax = totalTimelineDurationMs().coerceAtLeast(1_000L)
        mutate(pushHistory = false) { s ->
            s.copy(
                overlays = s.overlays.map { ov ->
                    if (ov.id != overlayId) ov
                    else if (isStart) {
                        ov.copy(startMs = (ov.startMs + deltaMs).coerceIn(0L, ov.endMs - 300L))
                    } else {
                        val maxEnd = maxOf(baseMax + 4_000L, ov.endMs)
                        ov.copy(endMs = (ov.endMs + deltaMs).coerceIn(ov.startMs + 300L, maxEnd))
                    }
                }
            )
        }
    }

    fun trimTextEdge(textId: String, isStart: Boolean, deltaMs: Long) {
        val baseMax = totalTimelineDurationMs().coerceAtLeast(1_000L)
        mutate(pushHistory = false) { s ->
            s.copy(
                textTracks = s.textTracks.map { tx ->
                    if (tx.id != textId) tx
                    else if (isStart) {
                        tx.copy(startMs = (tx.startMs + deltaMs).coerceIn(0L, tx.endMs - 300L))
                    } else {
                        val maxEnd = maxOf(baseMax + 4_000L, tx.endMs)
                        tx.copy(endMs = (tx.endMs + deltaMs).coerceIn(tx.startMs + 300L, maxEnd))
                    }
                }
            )
        }
    }

    fun trimAudioEdge(audioId: String, isStart: Boolean, deltaMs: Long) {
        val baseMax = totalTimelineDurationMs().coerceAtLeast(1_000L)
        mutate(pushHistory = false) { s ->
            s.copy(
                audioTracks = s.audioTracks.map { au ->
                    if (au.id != audioId) au
                    else if (isStart) {
                        val newStart = (au.startMs + deltaMs).coerceIn(0L, au.endMs - 300L)
                        val actualDelta = newStart - au.startMs
                        au.copy(
                            startMs = newStart,
                            sourceStartMs = (au.sourceStartMs + actualDelta).coerceAtLeast(0L),
                        )
                    } else {
                        val maxEnd = maxOf(baseMax + 4_000L, au.endMs)
                        au.copy(endMs = (au.endMs + deltaMs).coerceIn(au.startMs + 300L, maxEnd))
                    }
                }
            )
        }
    }

    fun duplicateSelectedClip() {
        val clip = activeClipAtPlayhead()
        val copyId = UUID.randomUUID().toString()
        val insertAt = clip.timelineStartMs + clip.timelineDurationMs()
        val clone = clip.copy(id = copyId, timelineStartMs = insertAt)
        val shift = clone.timelineDurationMs()
        mutate { s ->
            val sorted = s.clips.sortedBy { it.timelineStartMs }
            val result = mutableListOf<VideoClipSegment>()
            sorted.forEach { c ->
                if (c.id == clip.id) {
                    result.add(c)
                    result.add(clone)
                } else if (c.timelineStartMs >= insertAt - 20L) {
                    result.add(c.copy(timelineStartMs = c.timelineStartMs + shift))
                } else {
                    result.add(c)
                }
            }
            s.copy(clips = result)
        }
        _selectedClipId.value = copyId
    }

    fun deleteSelectedClip() {
        val id = _selectedClipId.value ?: return
        if (_snapshot.value.clips.size <= 1) {
            _statusMessage.value = "Main track requires at least one clip."
            return
        }
        mutate { s ->
            s.copy(
                clips = s.clips.filterNot { it.id == id },
                transitions = s.transitions.filterNot { it.afterClipId == id },
            )
        }
        _selectedClipId.value = null
    }

    fun freezeSelectedClipAtPlayhead() {
        val spans = clipSpans()
        val t = _playheadMs.value
        val span = _selectedClipId.value?.let { id -> spans.firstOrNull { it.clip.id == id } }
            ?: spans.firstOrNull { t in it.startMs..it.endMs } ?: return
        val clip = span.clip
        val localMs = (t - span.startMs).coerceIn(0L, clip.timelineDurationMs())
        val freezeSourceMs = clip.trimStartMs + (localMs * clip.speed.effectiveSpeedMultiplier()).toLong()
        val frozenId = UUID.randomUUID().toString()
        val insertAt = span.endMs
        val frozenClip = VideoClipSegment(
            id = frozenId,
            sourceUriString = clip.sourceUriString,
            displayName = "Freeze",
            timelineStartMs = insertAt,
            trimStartMs = freezeSourceMs,
            trimEndMs = freezeSourceMs + 250L,
            sourceDurationMs = clip.sourceDurationMs,
            isFrozen = true,
            freezeAtMs = freezeSourceMs,
            freezeDurationMs = 3_000L,
        )
        mutate { s ->
            val sorted = s.clips.sortedBy { it.timelineStartMs }
            val result = mutableListOf<VideoClipSegment>()
            sorted.forEach { c ->
                if (c.id == clip.id) {
                    result.add(c)
                    result.add(frozenClip)
                } else if (c.timelineStartMs >= insertAt - 20L) {
                    result.add(c.copy(timelineStartMs = c.timelineStartMs + 3_000L))
                } else {
                    result.add(c)
                }
            }
            s.copy(clips = result)
        }
        _selectedClipId.value = frozenId
        _statusMessage.value = "Inserted 3.0s Freeze Frame"
    }

    fun toggleReverseSelectedClip() {
        val target = activeClipAtPlayhead()
        mutateClip(target.id) { it.copy(isReversed = !it.isReversed) }
        _statusMessage.value = if (!target.isReversed) "Reverse enabled" else "Reverse disabled"
    }

    fun demoteSelectedClipToOverlay() {
        val clip = activeClipAtPlayhead()
        val uri = clip.sourceUriString
        if (uri == null) {
            _statusMessage.value = "Import a video first."
            return
        }
        val span = clipSpans().firstOrNull { it.clip.id == clip.id }
        val newOv = OverlayTrackItem(
            uriString = uri,
            isVideo = true,
            displayName = clip.displayName,
            startMs = span?.startMs ?: 0L,
            endMs = span?.endMs ?: 4_000L,
            scale = 0.55f,
        )
        mutate { s ->
            val nextClips = if (s.clips.size > 1) s.clips.filterNot { it.id == clip.id } else s.clips
            s.copy(clips = nextClips, overlays = s.overlays + newOv)
        }
        _selectedClipId.value = null
        _selectedOverlayId.value = newOv.id
    }

    // --- Speed / Volume / Beats / Opacity / Transform / Crop / Mask / MotionBlur / Stabilize ---
    fun updateSelectedClipSpeed(speed: SpeedSettings) {
        val target = activeClipAtPlayhead()
        mutateClip(target.id) { it.copy(speed = speed) }
    }

    fun updateSelectedClipVolume(volumePercent: Int, reduceNoise: Boolean, applyToAll: Boolean) {
        val target = activeClipAtPlayhead()
        val vol = volumePercent.coerceIn(0, 1000)
        val local = localPlayheadMs(target.id)
        mutate(pushHistory = false) { s ->
            s.copy(
                clips = s.clips.map { clip ->
                    when {
                        applyToAll -> clip.copy(volumePercent = vol, reduceNoise = reduceNoise)
                        clip.id != target.id -> clip
                        clip.volumeKeyframes.isNotEmpty() -> {
                            val nearKf = clip.volumeKeyframes.firstOrNull {
                                it.id == _selectedVolumeKeyframeId.value && abs(it.timeMs - local) < 180L
                            } ?: clip.volumeKeyframes.firstOrNull { abs(it.timeMs - local) < 140L }
                            val updatedKfs = if (nearKf != null) {
                                clip.volumeKeyframes.map { kf ->
                                    if (kf.id == nearKf.id) kf.copy(volumePercent = vol) else kf
                                }
                            } else {
                                val created = VolumeKeyframeNode(timeMs = local, volumePercent = vol)
                                _selectedVolumeKeyframeId.value = created.id
                                (clip.volumeKeyframes + created).sortedBy { it.timeMs }
                            }
                            clip.copy(reduceNoise = reduceNoise, volumeKeyframes = updatedKfs)
                        }
                        else -> clip.copy(volumePercent = vol, reduceNoise = reduceNoise)
                    }
                }
            )
        }
    }

    fun addOrUpdateClipVolumeKeyframeAtPlayhead(volumePercentOverride: Int? = null) {
        val clip = activeClipAtPlayhead()
        val local = localPlayheadMs(clip.id)
        val evaluatedVol = volumePercentOverride ?: VolumeKeyframeEvaluator.evaluate(
            keyframes = clip.volumeKeyframes,
            localTimeMs = local,
            baseVolumePercent = clip.volumePercent,
        )
        mutateClip(clip.id) { c ->
            val existing = c.volumeKeyframes.firstOrNull { abs(it.timeMs - local) < 140L }
            val next = if (existing != null) {
                _selectedVolumeKeyframeId.value = existing.id
                c.volumeKeyframes.map { if (it.id == existing.id) it.copy(volumePercent = evaluatedVol) else it }
            } else {
                val created = VolumeKeyframeNode(timeMs = local, volumePercent = evaluatedVol)
                _selectedVolumeKeyframeId.value = created.id
                (c.volumeKeyframes + created).sortedBy { it.timeMs }
            }
            c.copy(volumeKeyframes = next)
        }
    }

    fun deleteClipVolumeKeyframeAtPlayhead() {
        val clip = activeClipAtPlayhead()
        val local = localPlayheadMs(clip.id)
        val targetKf = clip.volumeKeyframes.firstOrNull { it.id == _selectedVolumeKeyframeId.value }
            ?: clip.volumeKeyframes.firstOrNull { abs(it.timeMs - local) < 140L }
            ?: return
        mutateClip(clip.id) { c ->
            c.copy(volumeKeyframes = c.volumeKeyframes.filterNot { it.id == targetKf.id })
        }
        if (_selectedVolumeKeyframeId.value == targetKf.id) _selectedVolumeKeyframeId.value = null
    }

    fun deleteAllClipVolumeKeyframes() {
        val clip = activeClipAtPlayhead()
        mutateClip(clip.id) { it.copy(volumeKeyframes = emptyList()) }
        _selectedVolumeKeyframeId.value = null
    }

    fun selectClipVolumeKeyframe(clipId: String, keyframeId: String) {
        val span = clipSpans().firstOrNull { it.clip.id == clipId } ?: return
        val kf = span.clip.volumeKeyframes.firstOrNull { it.id == keyframeId } ?: return
        selectClip(clipId)
        _selectedVolumeKeyframeId.value = keyframeId
        _playheadMs.value = (span.startMs + kf.timeMs).coerceIn(0L, totalTimelineDurationMs())
    }

    fun toggleMuteClip(clipId: String) {
        mutateClip(clipId) { it.copy(isMuted = !it.isMuted) }
    }

    fun toggleBeatAtPlayhead() {
        val spans = clipSpans()
        val t = _playheadMs.value
        val span = spans.firstOrNull { t in it.startMs..it.endMs } ?: spans.first()
        val local = (t - span.startMs).coerceAtLeast(0L)
        mutateClip(span.clip.id) { clip ->
            val existing = clip.beatMarkersMs.firstOrNull { abs(it - local) < 180L }
            val next = if (existing != null) clip.beatMarkersMs - existing else (clip.beatMarkersMs + local).sorted()
            clip.copy(beatMarkersMs = next)
        }
    }

    fun updateSelectedClipOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        val target = activeClipAtPlayhead()
        val local = localPlayheadMs(target.id)
        mutate(pushHistory = false) { s ->
            s.copy(
                clips = s.clips.map { clip ->
                    if (clip.id != target.id) clip
                    else if (_keyframeModeEnabled.value) {
                        val nearKf = clip.keyframes.firstOrNull { abs(it.timeMs - local) < 140L }
                        when {
                            nearKf != null -> clip.copy(
                                keyframes = clip.keyframes.map { if (it.id == nearKf.id) it.copy(opacity = clamped) else it }
                            )
                            clip.keyframes.isNotEmpty() -> {
                                val eval = evaluatedTransformNode(clip, local)
                                val created = eval.copy(id = UUID.randomUUID().toString(), timeMs = local, opacity = clamped)
                                _selectedKeyframeId.value = created.id
                                clip.copy(keyframes = (clip.keyframes + created).sortedBy { it.timeMs })
                            }
                            else -> clip.copy(opacity = clamped)
                        }
                    } else {
                        clip.copy(opacity = clamped)
                    }
                }
            )
        }
    }

    fun updateSelectedClipTransform(transform: TransformSettings, pushHistory: Boolean = false) {
        val target = activeClipAtPlayhead()
        val local = localPlayheadMs(target.id)
        mutate(pushHistory = pushHistory) { s ->
            s.copy(
                clips = s.clips.map { clip ->
                    if (clip.id != target.id) clip
                    else if (_keyframeModeEnabled.value) {
                        val nearKf = clip.keyframes.firstOrNull {
                            it.id == _selectedKeyframeId.value && abs(it.timeMs - local) < 180L
                        } ?: clip.keyframes.firstOrNull { abs(it.timeMs - local) < 140L }
                        when {
                            nearKf != null -> clip.copy(
                                transform = clip.transform.copy(mirrorHorizontal = transform.mirrorHorizontal),
                                keyframes = clip.keyframes.map { kf ->
                                    if (kf.id == nearKf.id) kf.copy(
                                        posX = transform.posX,
                                        posY = transform.posY,
                                        zoomPercent = transform.zoomPercent,
                                        rotateDeg = transform.rotateDeg,
                                    ) else kf
                                },
                            )
                            clip.keyframes.isNotEmpty() -> {
                                val baseEval = evaluatedTransformNode(clip, local)
                                val created = baseEval.copy(
                                    id = UUID.randomUUID().toString(),
                                    timeMs = local,
                                    posX = transform.posX,
                                    posY = transform.posY,
                                    zoomPercent = transform.zoomPercent,
                                    rotateDeg = transform.rotateDeg,
                                )
                                _selectedKeyframeId.value = created.id
                                clip.copy(
                                    transform = clip.transform.copy(mirrorHorizontal = transform.mirrorHorizontal),
                                    keyframes = (clip.keyframes + created).sortedBy { it.timeMs },
                                )
                            }
                            else -> clip.copy(transform = transform)
                        }
                    } else {
                        // Keyframe OFF: Always edit BASE transform for whole clip, never create keyframes!
                        clip.copy(transform = transform)
                    }
                }
            )
        }
    }

    fun fillSelectedClipCanvas() {
        val selOverlay = _selectedOverlayId.value?.let { oid -> _snapshot.value.overlays.firstOrNull { it.id == oid } }
        if (selOverlay != null) {
            updateOverlay(selOverlay.copy(posX = 0f, posY = 0f, scale = 1.0f))
            return
        }
        val selText = _selectedTextId.value?.let { tid -> _snapshot.value.textTracks.firstOrNull { it.id == tid } }
        if (selText != null) {
            val eval = evaluatedTextTransform(selText)
            updateSelectedTextTransform(
                posX = 0f,
                posY = 0f,
                zoomPercent = (eval.scale * 100f),
                rotateDeg = eval.rotationDeg,
                opacity = eval.opacity,
                pushHistory = true,
            )
            return
        }
        val clip = activeClipAtPlayhead()
        val eval = evaluatedClipTransform(clip)
        val meta = _selectedVideo.value
        val fillZoom = fillCanvasZoomPercent(
            videoWidth = meta?.width ?: 1920,
            videoHeight = meta?.height ?: 1080,
            canvasRatio = _aspectPreset.value.ratio,
            rotationDeg = eval.rotateDeg + clip.crop.rotateDeg,
        )
        updateSelectedClipTransform(
            transform = eval.copy(
                posX = 0f,
                posY = 0f,
                zoomPercent = fillZoom,
            ),
            pushHistory = true,
        )
    }

    fun resetSelectedClipTransform() {
        val clip = activeClipAtPlayhead()
        val local = localPlayheadMs(clip.id)
        if (_keyframeModeEnabled.value && clip.keyframes.any { abs(it.timeMs - local) < 140L }) {
            updateSelectedClipTransform(TransformSettings(), pushHistory = true)
        } else {
            mutateClip(clip.id) { c ->
                c.copy(transform = TransformSettings())
            }
        }
    }

    fun applyPreviewVideoGesture(zoomScaleFactor: Float, panDeltaX: Float, panDeltaY: Float, rotationDeltaDeg: Float) {
        val clip = activeClipAtPlayhead()
        val cur = evaluatedClipTransform(clip)
        val nextZoom = (cur.zoomPercent * zoomScaleFactor).coerceIn(100f, 360f)
        val nextPosX = (cur.posX + panDeltaX).coerceIn(-1.5f, 1.5f)
        val nextPosY = (cur.posY + panDeltaY).coerceIn(-1.5f, 1.5f)
        val nextRot = ((cur.rotateDeg + rotationDeltaDeg) % 360f + 360f) % 360f
        updateSelectedClipTransform(
            cur.copy(
                posX = nextPosX,
                posY = nextPosY,
                zoomPercent = nextZoom,
                rotateDeg = nextRot,
            ),
            pushHistory = false,
        )
    }

    fun evaluatedClipTransform(clip: VideoClipSegment): TransformSettings {
        val local = localPlayheadMs(clip.id)
        val eval = KeyframeEvaluator.evaluate(
            keyframes = clip.keyframes,
            localTimeMs = local,
            baseTransform = clip.transform,
            baseOpacity = clip.opacity,
            baseAdjust = _snapshot.value.adjust,
            baseFilterIntensity = _snapshot.value.filterIntensity,
        )
        return TransformSettings(
            posX = eval.posX,
            posY = eval.posY,
            zoomPercent = eval.zoomPercent,
            rotateDeg = eval.rotateDeg,
            mirrorHorizontal = clip.transform.mirrorHorizontal,
        )
    }

    private fun evaluatedTransformNode(clip: VideoClipSegment, localMs: Long): KeyframeNode {
        val eval = KeyframeEvaluator.evaluate(
            keyframes = clip.keyframes,
            localTimeMs = localMs,
            baseTransform = clip.transform,
            baseOpacity = clip.opacity,
            baseAdjust = _snapshot.value.adjust,
            baseFilterIntensity = _snapshot.value.filterIntensity,
        )
        val adj = _snapshot.value.adjust
        return KeyframeNode(
            timeMs = localMs,
            easingToNext = EasingGraph.LINEAR,
            posX = eval.posX,
            posY = eval.posY,
            zoomPercent = eval.zoomPercent,
            rotateDeg = eval.rotateDeg,
            opacity = eval.opacity,
            brightness = adj.brightness,
            contrast = adj.contrast,
            saturation = adj.saturation,
            filterIntensity = _snapshot.value.filterIntensity,
        )
    }

    fun evaluatedTextTransform(tx: TextTrackItem): com.example.data.model.EvaluatedTextState {
        val local = (_playheadMs.value - tx.startMs).coerceIn(0L, (tx.endMs - tx.startMs).coerceAtLeast(1L))
        return TextKeyframeEvaluator.evaluate(tx.keyframes, local, tx)
    }

    fun updateSelectedTextTransform(
        posX: Float,
        posY: Float,
        zoomPercent: Float,
        rotateDeg: Float,
        opacity: Float,
        pushHistory: Boolean = false,
    ) {
        val textId = _selectedTextId.value ?: return
        val scale = (zoomPercent / 100f).coerceIn(0.35f, 3.5f)
        val clampedOp = opacity.coerceIn(0f, 1f)
        mutate(pushHistory = pushHistory) { s ->
            s.copy(
                textTracks = s.textTracks.map { tx ->
                    if (tx.id != textId) tx
                    else {
                        val local = (_playheadMs.value - tx.startMs).coerceIn(0L, (tx.endMs - tx.startMs).coerceAtLeast(1L))
                        if (_keyframeModeEnabled.value) {
                            val nearKf = tx.keyframes.firstOrNull {
                                it.id == _selectedKeyframeId.value && abs(it.timeMs - local) < 180L
                            } ?: tx.keyframes.firstOrNull { abs(it.timeMs - local) < 140L }
                            when {
                                nearKf != null -> tx.copy(
                                    keyframes = tx.keyframes.map { kf ->
                                        if (kf.id == nearKf.id) kf.copy(
                                            posX = posX,
                                            posY = posY,
                                            scale = scale,
                                            rotationDeg = rotateDeg,
                                            opacity = clampedOp,
                                        ) else kf
                                    }
                                )
                                tx.keyframes.isNotEmpty() -> {
                                    val created = TextKeyframeNode(
                                        timeMs = local,
                                        posX = posX,
                                        posY = posY,
                                        scale = scale,
                                        rotationDeg = rotateDeg,
                                        opacity = clampedOp,
                                    )
                                    _selectedKeyframeId.value = created.id
                                    tx.copy(keyframes = (tx.keyframes + created).sortedBy { it.timeMs })
                                }
                                else -> tx.copy(
                                    posX = posX,
                                    posY = posY,
                                    scale = scale,
                                    rotationDeg = rotateDeg,
                                    opacity = clampedOp,
                                )
                            }
                        } else {
                            // Keyframe OFF: edit base text transform for entire text clip, never create keyframes!
                            tx.copy(
                                posX = posX,
                                posY = posY,
                                scale = scale,
                                rotationDeg = rotateDeg,
                                opacity = clampedOp,
                            )
                        }
                    }
                }
            )
        }
    }

    fun updateSelectedClipCrop(crop: CropSettings) {
        mutateClip(activeClipAtPlayhead().id) { it.copy(crop = crop) }
    }

    fun updateSelectedClipMask(mask: MaskSettings) {
        mutateClip(activeClipAtPlayhead().id) { it.copy(mask = mask) }
    }

    fun updateSelectedClipMotionBlur(mb: MotionBlurSettings) {
        mutateClip(activeClipAtPlayhead().id) { it.copy(motionBlur = mb) }
    }

    fun updateSelectedClipStabilize(mode: StabilizeMode) {
        mutateClip(activeClipAtPlayhead().id) { it.copy(stabilize = mode) }
    }

    fun updateSelectedClipAnimation(anim: AnimationSettings) {
        mutateClip(activeClipAtPlayhead().id) { it.copy(animation = anim) }
    }

    // --- Keyframes & Easing Graphs (Transform + Text ONLY; NEVER alters Audio Volume) ---
    fun addTransformKeyframeAtPlayhead() {
        val textId = _selectedTextId.value
        if (textId != null) {
            val tx = _snapshot.value.textTracks.firstOrNull { it.id == textId } ?: return
            val local = (_playheadMs.value - tx.startMs).coerceIn(0L, (tx.endMs - tx.startMs).coerceAtLeast(1L))
            val eval = TextKeyframeEvaluator.evaluate(tx.keyframes, local, tx)
            mutate { s ->
                s.copy(
                    textTracks = s.textTracks.map { item ->
                        if (item.id != textId) item
                        else {
                            val existing = item.keyframes.firstOrNull { abs(it.timeMs - local) < 140L }
                            val nextKfs = if (existing != null) {
                                _selectedKeyframeId.value = existing.id
                                item.keyframes.map {
                                    if (it.id == existing.id) it.copy(
                                        posX = eval.posX, posY = eval.posY, scale = eval.scale, rotationDeg = eval.rotationDeg, opacity = eval.opacity
                                    ) else it
                                }
                            } else {
                                val created = TextKeyframeNode(
                                    timeMs = local,
                                    posX = eval.posX,
                                    posY = eval.posY,
                                    scale = eval.scale,
                                    rotationDeg = eval.rotationDeg,
                                    opacity = eval.opacity,
                                )
                                _selectedKeyframeId.value = created.id
                                (item.keyframes + created).sortedBy { it.timeMs }
                            }
                            item.copy(keyframes = nextKfs)
                        }
                    }
                )
            }
            return
        }

        val clip = activeClipAtPlayhead()
        val localMs = localPlayheadMs(clip.id)
        mutateClip(clip.id) { c ->
            val node = evaluatedTransformNode(c, localMs)
            val existing = c.keyframes.firstOrNull { abs(it.timeMs - localMs) < 140L }
            val next = if (existing != null) {
                _selectedKeyframeId.value = existing.id
                c.keyframes.map {
                    if (it.id == existing.id) node.copy(id = existing.id, easingToNext = existing.easingToNext) else it
                }
            } else {
                _selectedKeyframeId.value = node.id
                (c.keyframes + node).sortedBy { it.timeMs }
            }
            // Note: clip.volumePercent and clip.volumeKeyframes remain untouched!
            c.copy(keyframes = next)
        }
    }

    fun deleteCurrentOrSelectedTransformKeyframe() {
        val textId = _selectedTextId.value
        if (textId != null) {
            val tx = _snapshot.value.textTracks.firstOrNull { it.id == textId } ?: return
            val local = (_playheadMs.value - tx.startMs).coerceIn(0L, (tx.endMs - tx.startMs).coerceAtLeast(1L))
            val targetKf = tx.keyframes.firstOrNull { it.id == _selectedKeyframeId.value }
                ?: tx.keyframes.firstOrNull { abs(it.timeMs - local) < 140L }
                ?: return
            mutate { s ->
                s.copy(
                    textTracks = s.textTracks.map {
                        if (it.id == textId) it.copy(keyframes = it.keyframes.filterNot { k -> k.id == targetKf.id }) else it
                    }
                )
            }
            if (_selectedKeyframeId.value == targetKf.id) _selectedKeyframeId.value = null
            return
        }

        val clip = activeClipAtPlayhead()
        val local = localPlayheadMs(clip.id)
        val targetKf = clip.keyframes.firstOrNull { it.id == _selectedKeyframeId.value }
            ?: clip.keyframes.firstOrNull { abs(it.timeMs - local) < 140L }
            ?: return
        mutateClip(clip.id) { c ->
            c.copy(keyframes = c.keyframes.filterNot { it.id == targetKf.id })
        }
        if (_selectedKeyframeId.value == targetKf.id) _selectedKeyframeId.value = null
    }

    fun deleteAllTransformKeyframes() {
        val textId = _selectedTextId.value
        if (textId != null) {
            mutate { s ->
                s.copy(textTracks = s.textTracks.map { if (it.id == textId) it.copy(keyframes = emptyList()) else it })
            }
            _selectedKeyframeId.value = null
            return
        }
        val clip = activeClipAtPlayhead()
        mutateClip(clip.id) { it.copy(keyframes = emptyList()) }
        _selectedKeyframeId.value = null
    }

    fun toggleKeyframeAtPlayhead() {
        val textId = _selectedTextId.value
        if (textId != null) {
            val tx = _snapshot.value.textTracks.firstOrNull { it.id == textId } ?: return
            val local = (_playheadMs.value - tx.startMs).coerceIn(0L, (tx.endMs - tx.startMs).coerceAtLeast(1L))
            val existing = tx.keyframes.firstOrNull { abs(it.timeMs - local) < 140L }
            if (existing != null) {
                mutate { s ->
                    s.copy(textTracks = s.textTracks.map {
                        if (it.id == textId) it.copy(keyframes = it.keyframes.filterNot { k -> k.id == existing.id }) else it
                    })
                }
                if (_selectedKeyframeId.value == existing.id) _selectedKeyframeId.value = null
            } else {
                addTransformKeyframeAtPlayhead()
            }
            return
        }

        val clip = activeClipAtPlayhead()
        val localMs = localPlayheadMs(clip.id)
        val existing = clip.keyframes.firstOrNull { abs(it.timeMs - localMs) < 140L }
        if (existing != null) {
            mutateClip(clip.id) { c -> c.copy(keyframes = c.keyframes.filterNot { it.id == existing.id }) }
            if (_selectedKeyframeId.value == existing.id) _selectedKeyframeId.value = null
        } else {
            addTransformKeyframeAtPlayhead()
        }
    }

    fun selectClipKeyframe(clipId: String, keyframeId: String) {
        val span = clipSpans().firstOrNull { it.clip.id == clipId } ?: return
        val kf = span.clip.keyframes.firstOrNull { it.id == keyframeId } ?: return
        selectClip(clipId)
        _selectedKeyframeId.value = keyframeId
        _keyframeModeEnabled.value = true
        _playheadMs.value = (span.startMs + kf.timeMs).coerceIn(0L, totalTimelineDurationMs())
    }

    fun selectTextKeyframe(textId: String, keyframeId: String) {
        val tx = _snapshot.value.textTracks.firstOrNull { it.id == textId } ?: return
        val kf = tx.keyframes.firstOrNull { it.id == keyframeId } ?: return
        selectTextTrack(textId)
        _selectedKeyframeId.value = keyframeId
        _keyframeModeEnabled.value = true
        _playheadMs.value = (tx.startMs + kf.timeMs).coerceIn(0L, totalTimelineDurationMs())
    }

    fun setKeyframeSegmentGraph(prevKeyframeId: String, graph: EasingGraph) {
        val textId = _selectedTextId.value
        if (textId != null) {
            mutate { s ->
                s.copy(
                    textTracks = s.textTracks.map { tx ->
                        if (tx.id == textId) tx.copy(keyframes = tx.keyframes.map { kf ->
                            if (kf.id == prevKeyframeId) kf.copy(easingToNext = graph) else kf
                        }) else tx
                    }
                )
            }
            return
        }
        val clip = activeClipAtPlayhead()
        mutateClip(clip.id) { c ->
            c.copy(
                keyframes = c.keyframes.map { kf ->
                    if (kf.id == prevKeyframeId) kf.copy(easingToNext = graph) else kf
                }
            )
        }
    }

    fun localPlayheadMs(clipId: String): Long {
        val span = clipSpans().firstOrNull { it.clip.id == clipId } ?: return _playheadMs.value
        return (_playheadMs.value - span.startMs).coerceIn(0L, span.endMs - span.startMs)
    }

    // --- Effects / Transitions / Adjust / Filters / Overlays / Audio / Text ---
    fun applyOrAddEffect(type: CapCutEffectType, intensity: Float = 0.75f) {
        val t = _playheadMs.value
        val dur = totalTimelineDurationMs()
        if (type == CapCutEffectType.NONE) {
            val id = _selectedEffectId.value ?: _snapshot.value.effectLayers.lastOrNull()?.id
            if (id != null) removeEffectLayer(id)
            return
        }
        mutate { s ->
            val targetId = _selectedEffectId.value
            val existing = s.effectLayers.firstOrNull { it.id == targetId || t in it.startMs..it.endMs }
            if (existing != null) {
                s.copy(
                    effectLayers = s.effectLayers.map {
                        if (it.id == existing.id) it.copy(type = type, intensity = intensity.coerceIn(0f, 1f)) else it
                    }
                )
            } else {
                val created = VideoEffectLayer(
                    type = type,
                    intensity = intensity.coerceIn(0f, 1f),
                    startMs = t.coerceIn(0L, (dur - 500L).coerceAtLeast(0L)),
                    endMs = (t + 3_000L).coerceAtMost(dur).coerceAtLeast((t + 500L).coerceAtMost(dur)),
                )
                clearAllTrackSelections()
                _selectedEffectId.value = created.id
                s.copy(effectLayers = s.effectLayers + created)
            }
        }
    }

    fun duplicateEffectLayer(id: String) {
        val src = _snapshot.value.effectLayers.firstOrNull { it.id == id } ?: return
        val dur = (src.endMs - src.startMs).coerceAtLeast(400L)
        val newStart = src.endMs
        val newEnd = newStart + dur
        val copy = src.copy(id = UUID.randomUUID().toString(), startMs = newStart, endMs = newEnd)
        mutate { s -> s.copy(effectLayers = s.effectLayers + copy) }
        selectEffectLayer(copy.id)
    }

    fun splitEffectAtPlayhead(id: String) {
        val fx = _snapshot.value.effectLayers.firstOrNull { it.id == id } ?: return
        val t = _playheadMs.value
        if (t <= fx.startMs + 250L || t >= fx.endMs - 250L) {
            _statusMessage.value = "Move playhead inside effect to split."
            return
        }
        val first = fx.copy(endMs = t)
        val second = fx.copy(id = UUID.randomUUID().toString(), startMs = t)
        mutate { s ->
            s.copy(effectLayers = s.effectLayers.flatMap { if (it.id == id) listOf(first, second) else listOf(it) })
        }
        selectEffectLayer(second.id)
    }

    fun setEffectObjectTarget(id: String, applyToAllVideos: Boolean) {
        mutate { s ->
            s.copy(
                effectLayers = s.effectLayers.map {
                    if (it.id == id) it.copy(applyToAllVideos = applyToAllVideos) else it
                }
            )
        }
    }

    fun removeEffectLayer(id: String) {
        mutate { s -> s.copy(effectLayers = s.effectLayers.filterNot { it.id == id }) }
        if (_selectedEffectId.value == id) _selectedEffectId.value = null
    }

    fun updateTransition(afterClipId: String, type: TransitionType, durationSec: Float, applyToAll: Boolean) {
        val safeDur = durationSec.coerceIn(0.1f, 0.8f)
        mutate { s ->
            val next = if (applyToAll) {
                s.clips.dropLast(1).map { TransitionSpec(afterClipId = it.id, type = type, durationSec = safeDur) }
            } else {
                val filtered = s.transitions.filterNot { it.afterClipId == afterClipId }
                filtered + TransitionSpec(afterClipId = afterClipId, type = type, durationSec = safeDur)
            }
            s.copy(transitions = next)
        }
    }

    fun updateAdjust(adjust: AdjustSettings) {
        mutate(pushHistory = false) { it.copy(adjust = adjust) }
    }

    fun commitAdjustSnapshot() {
        undoStack.addLast(_snapshot.value)
        if (undoStack.size > 35) undoStack.removeFirst()
        redoStack.clear()
        syncUndoRedoFlags()
    }

    fun applySmartAutoAdjust() {
        mutate { s ->
            s.copy(
                adjust = s.adjust.copy(
                    brightness = 4f,
                    contrast = 14f,
                    saturation = 12f,
                    brilliance = 16f,
                    clarity = 15f,
                    sharpen = 22f,
                    highlights = -12f,
                    shadows = 14f,
                    rgbCurves = RgbCurvesSpec(
                        white = listOf(
                            CurvePoint(0f, 0.02f),
                            CurvePoint(0.25f, 0.21f),
                            CurvePoint(0.55f, 0.58f),
                            CurvePoint(0.85f, 0.89f),
                            CurvePoint(1f, 1f),
                        )
                    ),
                )
            )
        }
        _statusMessage.value = "Applied Smart Auto Adjust"
    }

    fun resetAdjust() {
        mutate { it.copy(adjust = AdjustSettings()) }
    }

    fun applyPreset(preset: FfxPreset) {
        mutate { it.copy(activePreset = preset, filterIntensity = 1.0f) }
        _bypassEffects.value = false
    }

    fun setFilterIntensity(intensity: Float) {
        mutate(pushHistory = false) { it.copy(filterIntensity = intensity.coerceIn(0f, 1f)) }
    }

    fun addOverlayMedia(context: Context, uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            val name = VideoExportManager.queryDisplayName(context, uri) ?: if (isVideo) "Overlay.mp4" else "Overlay.png"
            val dur = totalTimelineDurationMs()
            val item = OverlayTrackItem(
                uriString = uri.toString(),
                isVideo = isVideo,
                displayName = name,
                startMs = 0L,
                endMs = dur.coerceAtMost(8_000L),
            )
            mutate { s -> s.copy(overlays = s.overlays + item) }
            _selectedOverlayId.value = item.id
            _selectedClipId.value = null
        }
    }

    fun updateOverlay(item: OverlayTrackItem) {
        mutate(pushHistory = false) { s ->
            s.copy(overlays = s.overlays.map { if (it.id == item.id) item else it })
        }
    }

    fun deleteOverlay(id: String) {
        mutate { s -> s.copy(overlays = s.overlays.filterNot { it.id == id }) }
        if (_selectedOverlayId.value == id) _selectedOverlayId.value = null
    }

    fun duplicateOverlay(id: String) {
        val src = _snapshot.value.overlays.firstOrNull { it.id == id } ?: return
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            posX = (src.posX + 0.08f).coerceIn(-0.85f, 0.85f),
            posY = (src.posY - 0.08f).coerceIn(-0.85f, 0.85f),
        )
        mutate { s -> s.copy(overlays = s.overlays + copy) }
        selectOverlay(copy.id)
    }

    fun splitOverlayAtPlayhead(id: String) {
        val ov = _snapshot.value.overlays.firstOrNull { it.id == id } ?: return
        val t = _playheadMs.value
        if (t <= ov.startMs + 250L || t >= ov.endMs - 250L) {
            _statusMessage.value = "Move playhead inside overlay to split."
            return
        }
        val first = ov.copy(endMs = t)
        val second = ov.copy(id = UUID.randomUUID().toString(), startMs = t)
        mutate { s ->
            s.copy(overlays = s.overlays.flatMap { if (it.id == id) listOf(first, second) else listOf(it) })
        }
        selectOverlay(second.id)
    }

    fun addAudioTrack(context: Context, uri: Uri?, fallbackTitle: String = "Audio Track") {
        viewModelScope.launch {
            val name = uri?.let { VideoExportManager.queryDisplayName(context, it) } ?: fallbackTitle
            val start = _playheadMs.value
            val end = totalTimelineDurationMs()
            val track = AudioTrackItem(uriString = uri?.toString(), title = name, startMs = start, endMs = end)
            mutate { s -> s.copy(audioTracks = s.audioTracks + track) }
            selectAudioTrack(track.id)
        }
    }

    fun updateAudioVolume(id: String, volumePercent: Int, reduceNoise: Boolean) {
        val vol = volumePercent.coerceIn(0, 1000)
        mutate(pushHistory = false) { s ->
            s.copy(
                audioTracks = s.audioTracks.map { au ->
                    if (au.id != id) au
                    else if (au.volumeKeyframes.isNotEmpty()) {
                        val local = (_playheadMs.value - au.startMs).coerceIn(0L, (au.endMs - au.startMs).coerceAtLeast(1L))
                        val nearKf = au.volumeKeyframes.firstOrNull {
                            it.id == _selectedVolumeKeyframeId.value && abs(it.timeMs - local) < 180L
                        } ?: au.volumeKeyframes.firstOrNull { abs(it.timeMs - local) < 140L }
                        val updatedKfs = if (nearKf != null) {
                            au.volumeKeyframes.map { if (it.id == nearKf.id) it.copy(volumePercent = vol) else it }
                        } else {
                            val created = VolumeKeyframeNode(timeMs = local, volumePercent = vol)
                            _selectedVolumeKeyframeId.value = created.id
                            (au.volumeKeyframes + created).sortedBy { it.timeMs }
                        }
                        au.copy(reduceNoise = reduceNoise, volumeKeyframes = updatedKfs)
                    } else {
                        au.copy(volumePercent = vol, reduceNoise = reduceNoise)
                    }
                }
            )
        }
    }

    fun addOrUpdateAudioVolumeKeyframeAtPlayhead(audioId: String, volumePercentOverride: Int? = null) {
        val au = _snapshot.value.audioTracks.firstOrNull { it.id == audioId } ?: return
        val local = (_playheadMs.value - au.startMs).coerceIn(0L, (au.endMs - au.startMs).coerceAtLeast(1L))
        val evaluatedVol = volumePercentOverride ?: VolumeKeyframeEvaluator.evaluate(
            keyframes = au.volumeKeyframes,
            localTimeMs = local,
            baseVolumePercent = au.volumePercent,
        )
        mutate { s ->
            s.copy(
                audioTracks = s.audioTracks.map { item ->
                    if (item.id != audioId) item
                    else {
                        val existing = item.volumeKeyframes.firstOrNull { abs(it.timeMs - local) < 140L }
                        val next = if (existing != null) {
                            _selectedVolumeKeyframeId.value = existing.id
                            item.volumeKeyframes.map { if (it.id == existing.id) it.copy(volumePercent = evaluatedVol) else it }
                        } else {
                            val created = VolumeKeyframeNode(timeMs = local, volumePercent = evaluatedVol)
                            _selectedVolumeKeyframeId.value = created.id
                            (item.volumeKeyframes + created).sortedBy { it.timeMs }
                        }
                        item.copy(volumeKeyframes = next)
                    }
                }
            )
        }
    }

    fun deleteAudioVolumeKeyframeAtPlayhead(audioId: String) {
        val au = _snapshot.value.audioTracks.firstOrNull { it.id == audioId } ?: return
        val local = (_playheadMs.value - au.startMs).coerceIn(0L, (au.endMs - au.startMs).coerceAtLeast(1L))
        val targetKf = au.volumeKeyframes.firstOrNull { it.id == _selectedVolumeKeyframeId.value }
            ?: au.volumeKeyframes.firstOrNull { abs(it.timeMs - local) < 140L }
            ?: return
        mutate { s ->
            s.copy(
                audioTracks = s.audioTracks.map {
                    if (it.id == audioId) it.copy(volumeKeyframes = it.volumeKeyframes.filterNot { k -> k.id == targetKf.id }) else it
                }
            )
        }
        if (_selectedVolumeKeyframeId.value == targetKf.id) _selectedVolumeKeyframeId.value = null
    }

    fun deleteAllAudioVolumeKeyframes(audioId: String) {
        mutate { s ->
            s.copy(
                audioTracks = s.audioTracks.map {
                    if (it.id == audioId) it.copy(volumeKeyframes = emptyList()) else it
                }
            )
        }
        _selectedVolumeKeyframeId.value = null
    }

    fun selectAudioVolumeKeyframe(audioId: String, keyframeId: String) {
        val au = _snapshot.value.audioTracks.firstOrNull { it.id == audioId } ?: return
        val kf = au.volumeKeyframes.firstOrNull { it.id == keyframeId } ?: return
        selectAudioTrack(audioId)
        _selectedVolumeKeyframeId.value = keyframeId
        _playheadMs.value = (au.startMs + kf.timeMs).coerceIn(0L, totalTimelineDurationMs())
    }

    fun duplicateAudioTrack(id: String) {
        val src = _snapshot.value.audioTracks.firstOrNull { it.id == id } ?: return
        val copy = src.copy(id = UUID.randomUUID().toString())
        mutate { s -> s.copy(audioTracks = s.audioTracks + copy) }
        selectAudioTrack(copy.id)
    }

    fun splitAudioAtPlayhead(id: String) {
        val au = _snapshot.value.audioTracks.firstOrNull { it.id == id } ?: return
        val t = _playheadMs.value
        if (t <= au.startMs + 250L || t >= au.endMs - 250L) {
            _statusMessage.value = "Move playhead inside audio to split."
            return
        }
        val localMs = t - au.startMs
        val first = au.copy(
            endMs = t,
            volumeKeyframes = au.volumeKeyframes.filter { it.timeMs <= localMs },
        )
        val second = au.copy(
            id = UUID.randomUUID().toString(),
            startMs = t,
            sourceStartMs = au.sourceStartMs + localMs,
            volumeKeyframes = au.volumeKeyframes.filter { it.timeMs >= localMs }.map { it.copy(timeMs = it.timeMs - localMs) },
        )
        mutate { s ->
            s.copy(audioTracks = s.audioTracks.flatMap { if (it.id == id) listOf(first, second) else listOf(it) })
        }
        selectAudioTrack(second.id)
    }

    fun deleteAudioTrack(id: String) {
        mutate { s -> s.copy(audioTracks = s.audioTracks.filterNot { it.id == id }) }
        if (_selectedAudioId.value == id) _selectedAudioId.value = null
    }

    fun addTextOrCaption(text: String, isCaption: Boolean) {
        val clean = text.trim().ifBlank { if (isCaption) "LUNA Auto Caption" else "LUNA Text" }
        val dur = totalTimelineDurationMs()
        val start = _playheadMs.value.coerceIn(0L, (dur - 500L).coerceAtLeast(0L))
        val end = (start + 3_500L).coerceAtMost(dur).coerceAtLeast((start + 500L).coerceAtMost(dur))
        val item = TextTrackItem(text = clean, isCaption = isCaption, startMs = start, endMs = end)
        mutate { s -> s.copy(textTracks = s.textTracks + item) }
        selectTextTrack(item.id)
    }

    fun updateTextTrack(item: TextTrackItem) {
        mutate(pushHistory = false) { s ->
            s.copy(textTracks = s.textTracks.map { if (it.id == item.id) item else it })
        }
    }

    fun splitTextAtPlayhead(id: String) {
        val tx = _snapshot.value.textTracks.firstOrNull { it.id == id } ?: return
        val t = _playheadMs.value
        if (t <= tx.startMs + 250L || t >= tx.endMs - 250L) {
            _statusMessage.value = "Move playhead inside text to split."
            return
        }
        val localMs = t - tx.startMs
        val first = tx.copy(
            endMs = t,
            keyframes = tx.keyframes.filter { it.timeMs <= localMs },
        )
        val second = tx.copy(
            id = UUID.randomUUID().toString(),
            startMs = t,
            keyframes = tx.keyframes.filter { it.timeMs >= localMs }.map { it.copy(timeMs = it.timeMs - localMs) },
        )
        mutate { s ->
            s.copy(textTracks = s.textTracks.flatMap { if (it.id == id) listOf(first, second) else listOf(it) })
        }
        selectTextTrack(second.id)
    }

    fun generateAutoCaptions() {
        val dur = totalTimelineDurationMs()
        val half = dur / 2L
        val generated = listOf(
            TextTrackItem(text = "Cinema grade initialized", isCaption = true, startMs = 0L, endMs = half.coerceAtLeast(1_200L)),
            TextTrackItem(text = "Smooth easing keyframes active", isCaption = true, startMs = half, endMs = dur),
        )
        mutate { s -> s.copy(textTracks = s.textTracks.filterNot { it.isCaption } + generated) }
        _statusMessage.value = "Generated synced timeline captions."
    }

    fun deleteTextTrack(id: String) {
        mutate { s -> s.copy(textTracks = s.textTracks.filterNot { it.id == id }) }
        if (_selectedTextId.value == id) _selectedTextId.value = null
    }

    fun toggleEndingCard() {
        mutate { s -> s.copy(hasEndingCard = !s.hasEndingCard) }
    }

    fun setCanvasBackground(argb: Long) {
        mutate { it.copy(canvasBackgroundArgb = argb) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        redoStack.addLast(_snapshot.value)
        _snapshot.value = prev
        syncUndoRedoFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(_snapshot.value)
        _snapshot.value = next
        syncUndoRedoFlags()
    }

    // --- Preset Vault & Import/Export ---
    fun saveOrCreatePreset(preset: FfxPreset, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew || preset.id.isBlank()) {
                repository.createPreset(preset).onSuccess { newId ->
                    val created = preset.copy(id = newId)
                    _statusMessage.value = "Saved \"${preset.name}\" to LUNA library."
                    applyPreset(created)
                }
            } else {
                repository.updatePreset(preset).onSuccess {
                    if (_snapshot.value.activePreset.id == preset.id) applyPreset(preset)
                    _statusMessage.value = "Updated \"${preset.name}\"."
                }
            }
        }
    }

    fun toggleFavorite(preset: FfxPreset) {
        if (preset.id.isBlank()) return
        viewModelScope.launch {
            val nextFav = !preset.isFavorite
            repository.toggleFavorite(preset.id, nextFav)
            if (_snapshot.value.activePreset.id == preset.id) {
                _snapshot.value = _snapshot.value.copy(activePreset = _snapshot.value.activePreset.copy(isFavorite = nextFav))
            }
        }
    }

    fun deletePreset(presetId: String) {
        if (presetId.isBlank()) return
        viewModelScope.launch { repository.deletePreset(presetId) }
    }

    fun importFfxFromUri(context: Context, uri: Uri, onImportedAndApplied: () -> Unit = {}) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val name = VideoExportManager.queryDisplayName(context, uri) ?: "imported_preset.ffx"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                if (bytes.isEmpty()) null else FfxParser.parseFromBytes(name, bytes)
            } ?: run {
                _statusMessage.value = "Could not read selected .ffx or .cube file."
                return@launch
            }
            repository.createPreset(parsed).onSuccess { newId ->
                val saved = parsed.copy(id = newId)
                applyPreset(saved)
                _statusMessage.value = "Imported ${saved.fileName} into LUNA Filters."
                onImportedAndApplied()
            }
        }
    }

    fun writeFfxDescriptorToUri(context: Context, preset: FfxPreset, destUri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val content = FfxParser.serializeToFfxDescriptor(preset)
                    context.contentResolver.openOutputStream(destUri)?.use { it.write(content.toByteArray()) }
                }
            }.onSuccess { _statusMessage.value = "Saved ${preset.fileName} to local drive." }
        }
    }

    fun startHighQualityExport(config: ExportConfig) {
        val video = _selectedVideo.value
        if (video == null) {
            _exportState.value = ExportUiState.Error("Import a video clip before exporting.")
            return
        }
        val snap = _snapshot.value
        _exportState.value = ExportUiState.Rendering(0, "Preparing Media3 Composition (${config.resolution.shortLabel} @ ${config.frameRate}fps)...")
        exportManager.startCapCutExport(
            sourceVideoUri = video.uri,
            clips = snap.clips,
            transitions = snap.transitions,
            effectLayers = snap.effectLayers,
            overlays = snap.overlays,
            texts = snap.textTracks,
            adjust = snap.adjust,
            activePreset = snap.activePreset,
            filterIntensity = snap.filterIntensity,
            config = config,
            onProgress = { pct ->
                _exportState.value = ExportUiState.Rendering(pct, "Exporting ${config.resolution.shortLabel} • ${config.frameRate}fps...")
            },
            onSuccess = { record, locationLabel ->
                _exportState.value = ExportUiState.Success(record, locationLabel)
                viewModelScope.launch {
                    repository.recordExport(record)
                    if (snap.activePreset.id.isNotBlank()) repository.incrementUsage(snap.activePreset.id)
                }
            },
            onError = { msg -> _exportState.value = ExportUiState.Error(msg) },
        )
    }

    fun cancelExport() {
        exportManager.cancelExport()
        _exportState.value = ExportUiState.Idle
    }

    fun resetExportState() { _exportState.value = ExportUiState.Idle }

    fun copyExportToDestination(context: Context, sourceUriString: String, destUri: Uri) {
        viewModelScope.launch {
            val uri = runCatching { Uri.parse(sourceUriString) }.getOrNull() ?: return@launch
            VideoExportManager.copyUriToDestination(context, uri, destUri).onSuccess {
                _statusMessage.value = "Saved rendered MP4 copy."
            }
        }
    }

    fun deleteExportRecord(exportId: String) {
        if (exportId.isBlank()) return
        viewModelScope.launch { repository.deleteExportRecord(exportId) }
    }

    private fun mutateClip(clipId: String, updater: (VideoClipSegment) -> VideoClipSegment) {
        mutate { s ->
            s.copy(clips = s.clips.map { if (it.id == clipId) updater(it) else it })
        }
    }

    private fun mutate(pushHistory: Boolean = true, transform: (TimelineSnapshot) -> TimelineSnapshot) {
        val current = _snapshot.value
        val updated = transform(current)
        if (updated != current) {
            if (pushHistory) {
                undoStack.addLast(current)
                if (undoStack.size > 35) undoStack.removeFirst()
                redoStack.clear()
                syncUndoRedoFlags()
            }
            _snapshot.value = updated
        }
    }

    private fun syncUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun upsertCurrentProjectSummary(durationMs: Long) {
        val title = _projectTitle.value
        val summary = LunaProjectSummary(
            id = title.lowercase().replace(' ', '_'),
            title = title,
            aspectPreset = _aspectPreset.value,
            resolutionLabel = _projectResolution.value,
            fps = _projectFps.value,
            durationMs = durationMs,
            activePresetName = _snapshot.value.activePreset.name,
            layerCount = _snapshot.value.clips.size + _snapshot.value.effectLayers.size + _snapshot.value.overlays.size,
            videoDisplayName = _selectedVideo.value?.displayName,
        )
        _recentProjects.value = listOf(summary) + _recentProjects.value.filterNot { it.title == title }
    }

    private companion object {
        const val TAG = "StudioViewModel"

        fun initialProjectsList(starterPreset: FfxPreset): List<LunaProjectSummary> = listOf(
            LunaProjectSummary("proj_1", "LUNA Studio Edit", AspectPreset.RATIO_9_16, "1080p", 30, 8_000L, starterPreset.name, 2),
            LunaProjectSummary("proj_2", "Night Cinema CC", AspectPreset.RATIO_1_1, "1440p", 60, 17_000L, "Cyber Neon Split-Tone", 3),
            LunaProjectSummary("proj_3", "35mm Film Look", AspectPreset.RATIO_4_3, "1080p", 30, 24_000L, "Kodak 2383 Film Grain", 2),
        )
    }
}
