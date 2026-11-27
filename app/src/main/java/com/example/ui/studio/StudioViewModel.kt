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
import com.example.data.model.CurveSpeedPreset
import com.example.data.model.EasingGraph
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.model.KeyframeEvaluator
import com.example.data.model.KeyframeNode
import com.example.data.model.LunaProjectSummary
import com.example.data.model.MaskSettings
import com.example.data.model.MotionBlurSettings
import com.example.data.model.OverlayTrackItem
import com.example.data.model.PersistedProjectState
import com.example.data.model.PresetCategory
import com.example.data.model.RgbCurvesSpec
import com.example.data.model.SmoothReverseConfig
import com.example.data.model.SmoothReverseRole
import com.example.data.model.SpeedCurvePoint
import com.example.data.model.SpeedMode
import com.example.data.model.SpeedSettings
import com.example.data.model.StabilizeMode
import com.example.data.model.TextKeyframeEvaluator
import com.example.data.model.TextKeyframeNode
import com.example.data.model.TextTrackItem
import com.example.data.model.TimelineSnapshot
import com.example.data.model.TimelineTrackConfig
import com.example.data.model.TimelineTrackKind
import com.example.data.model.TimesliceMode
import com.example.data.model.TimesliceSettings
import com.example.data.model.TransformSettings
import com.example.data.model.TransitionSpec
import com.example.data.model.TransitionType
import com.example.data.model.VideoClipSegment
import com.example.data.model.VideoEffectLayer
import com.example.data.model.VolumeKeyframeEvaluator
import com.example.data.model.VolumeKeyframeNode
import com.example.data.model.computeTotalVideoDurationMs
import com.example.data.model.computeTrackSpans
import com.example.data.model.defaultTimelineTracks
import com.example.data.model.fillCanvasZoomPercent
import com.example.data.repository.PresetRepository
import com.example.domain.ffx.FfxParser
import com.example.domain.video.ExportConfig
import com.example.domain.video.ExportUiState
import com.example.domain.video.UiState
import com.example.domain.video.VideoEffectsEngine
import com.example.domain.video.VideoExportManager
import com.example.domain.video.VideoMetadata
import com.example.domain.video.VideoReverseProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

enum class AutosaveStatus {
    IDLE,
    SAVING,
    SAVED,
}

data class ReverseProgressState(
    val currentClipIndex: Int,
    val totalClips: Int,
    val clipName: String,
    val percent: Int,
    val statusLabel: String,
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

    private val _clipThumbnails = MutableStateFlow<Map<String, List<ImageBitmap>>>(emptyMap())
    val clipThumbnails: StateFlow<Map<String, List<ImageBitmap>>> = _clipThumbnails.asStateFlow()

    private val _activeProjectId = MutableStateFlow(UUID.randomUUID().toString())
    private val _activeProjectThumbnailPath = MutableStateFlow<String?>(null)

    private val _autosaveStatus = MutableStateFlow(AutosaveStatus.IDLE)
    val autosaveStatus: StateFlow<AutosaveStatus> = _autosaveStatus.asStateFlow()

    private val _reverseProgress = MutableStateFlow<ReverseProgressState?>(null)
    val reverseProgress: StateFlow<ReverseProgressState?> = _reverseProgress.asStateFlow()

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

    val recentProjects: StateFlow<List<LunaProjectSummary>> = repository.observeProjects()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var autosaveJob: Job? = null
    private var hasModifiedInSession = false

    init {
        seedStarterPackIfEmpty()
        viewModelScope.launch {
            val saved = repository.getMostRecentProjectState()
            if (saved != null && !hasModifiedInSession) {
                applyPersistedState(saved, appContext = null)
            }
        }
    }

    fun seedStarterPackIfEmpty() {
        viewModelScope.launch {
            repository.seedStarterPackIfEmpty().onSuccess { added ->
                if (added > 0) _statusMessage.value = "Loaded $added LUNA studio presets."
            }
        }
    }

    fun clearStatusMessage() { _statusMessage.value = null }
    fun postStatusMessage(message: String) { _statusMessage.value = message }
    fun toggleBypass() { _bypassEffects.value = !_bypassEffects.value }
    fun updateTimelineZoom(zoom: Float) {
        _timelineZoom.value = zoom.coerceIn(0.45f, 3.8f)
        scheduleAutosave()
    }
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
        preset: FfxPreset = starterPreset,
    ) {
        hasModifiedInSession = true
        val newId = UUID.randomUUID().toString()
        _activeProjectId.value = newId
        _activeProjectThumbnailPath.value = null
        _projectTitle.value = title.trim().ifBlank { "LUNA Project ${recentProjects.value.size + 1}" }
        _aspectPreset.value = aspect
        _projectResolution.value = resolutionLabel
        _projectFps.value = fps
        _canvasFitMode.value = CanvasFitMode.FIT
        _timelineZoom.value = 1.0f
        _bypassEffects.value = false
        clearAllTrackSelections()
        _selectedVideo.value = null
        _filmstripThumbnails.value = emptyList()
        _clipThumbnails.value = emptyMap()
        _exportState.value = ExportUiState.Idle
        undoStack.clear()
        redoStack.clear()
        syncUndoRedoFlags()

        _snapshot.value = TimelineSnapshot(
            clips = listOf(VideoClipSegment(id = UUID.randomUUID().toString(), displayName = "Import Video Clip")),
            activePreset = preset,
        )
        _playheadMs.value = 0L
        saveProjectImmediately()
    }

    fun openProjectSummary(project: LunaProjectSummary, presets: List<FfxPreset>, context: Context? = null) {
        hasModifiedInSession = true
        viewModelScope.launch {
            val loaded = repository.loadProjectState(project.id).getOrNull()
            if (loaded != null) {
                applyPersistedState(loaded, context)
            } else {
                _activeProjectId.value = project.id
                _projectTitle.value = project.title
                _aspectPreset.value = project.aspectPreset
                _projectResolution.value = project.resolutionLabel
                _projectFps.value = project.fps
                val matched = presets.firstOrNull { it.name == project.activePresetName } ?: _snapshot.value.activePreset
                applyPreset(matched)
            }
        }
    }

    fun restoreProjectThumbnailsIfNeeded(context: Context) {
        val clipsWithMedia = _snapshot.value.clips.filter { !it.effectiveUriString().isNullOrBlank() }
        if (clipsWithMedia.isEmpty()) return
        viewModelScope.launch {
            if (_selectedVideo.value == null) {
                val firstUri = clipsWithMedia.first().effectiveUriString()?.let { runCatching { Uri.parse(it) }.getOrNull() }
                if (firstUri != null) {
                    _selectedVideo.value = VideoExportManager.readVideoMetadata(context, firstUri)
                }
            }
            val currentMap = _clipThumbnails.value.toMutableMap()
            var changed = false
            for (clip in clipsWithMedia) {
                if (currentMap[clip.id].isNullOrEmpty()) {
                    val uri = clip.effectiveUriString()?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: continue
                    val thumbs = VideoExportManager.extractFilmstripThumbnails(
                        context = context,
                        uri = uri,
                        durationMs = clip.effectiveSourceDurationMs(),
                        trimStartMs = clip.effectiveTrimStartMs(),
                        trimEndMs = clip.effectiveTrimEndMs(),
                        isReversed = clip.isReversed && !clip.hasReversedMediaReady(),
                    )
                    if (thumbs.isNotEmpty()) {
                        currentMap[clip.id] = thumbs
                        changed = true
                    }
                }
            }
            if (changed) {
                _clipThumbnails.value = currentMap
                if (_filmstripThumbnails.value.isEmpty()) {
                    _filmstripThumbnails.value = clipsWithMedia.firstOrNull()?.id?.let { currentMap[it] }.orEmpty()
                }
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            if (_activeProjectId.value == projectId) {
                val next = repository.getMostRecentProjectState()
                if (next != null) {
                    applyPersistedState(next, appContext = null)
                } else {
                    _activeProjectId.value = UUID.randomUUID().toString()
                    _activeProjectThumbnailPath.value = null
                    _selectedVideo.value = null
                    _filmstripThumbnails.value = emptyList()
                    _clipThumbnails.value = emptyMap()
                    _snapshot.value = TimelineSnapshot(
                        clips = listOf(VideoClipSegment(id = UUID.randomUUID().toString(), displayName = "Import Video Clip")),
                        activePreset = starterPreset,
                    )
                    _playheadMs.value = 0L
                }
            }
            _statusMessage.value = "Project deleted"
        }
    }

    fun flushAutosave() {
        autosaveJob?.cancel()
        saveProjectImmediately()
    }

    private fun applyPersistedState(state: PersistedProjectState, appContext: Context?) {
        _activeProjectId.value = state.projectId
        _activeProjectThumbnailPath.value = state.thumbnailPath
        _projectTitle.value = state.title
        _aspectPreset.value = state.aspectPreset
        _projectResolution.value = state.resolutionLabel
        _projectFps.value = state.fps
        _canvasFitMode.value = state.canvasFitMode
        _timelineZoom.value = state.timelineZoom.coerceIn(0.45f, 3.8f)
        clearAllTrackSelections()
        undoStack.clear()
        redoStack.clear()
        syncUndoRedoFlags()
        _snapshot.value = state.snapshot.copy(
            videoTrackCount = state.snapshot.effectiveVideoTrackCount(),
            trackConfigs = state.snapshot.normalizedTrackConfigs(),
        )
        _playheadMs.value = state.playheadMs.coerceIn(0L, computeTotalVideoDurationMs(state.snapshot.clips))
        _filmstripThumbnails.value = emptyList()
        _clipThumbnails.value = emptyMap()

        val firstUriStr = state.snapshot.clips.firstOrNull { !it.effectiveUriString().isNullOrBlank() }?.effectiveUriString()
        val firstUri = firstUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (firstUri == null) {
            _selectedVideo.value = null
        } else if (appContext != null) {
            restoreProjectThumbnailsIfNeeded(appContext)
        } else {
            val firstClip = state.snapshot.clips.first { !it.effectiveUriString().isNullOrBlank() }
            _selectedVideo.value = VideoMetadata(
                uri = firstUri,
                displayName = firstClip.displayName,
                width = firstClip.width,
                height = firstClip.height,
                durationMs = firstClip.sourceDurationMs,
            )
        }
        _autosaveStatus.value = AutosaveStatus.SAVED
    }

    fun updateProjectSettings(title: String, aspect: AspectPreset, res: String, fps: Int) {
        hasModifiedInSession = true
        _projectTitle.value = title.trim().ifBlank { _projectTitle.value }
        _aspectPreset.value = aspect
        _projectResolution.value = res
        _projectFps.value = fps
        scheduleAutosave()
    }

    fun setAspectPreset(aspect: AspectPreset) {
        hasModifiedInSession = true
        _aspectPreset.value = aspect
        scheduleAutosave()
    }

    fun cycleCanvasFitMode() {
        hasModifiedInSession = true
        val entries = CanvasFitMode.entries
        _canvasFitMode.value = entries[(entries.indexOf(_canvasFitMode.value) + 1) % entries.size]
        scheduleAutosave()
    }

    fun selectVideo(
        context: Context,
        uri: Uri,
        replaceSelectedClip: Boolean = false,
        appendNew: Boolean = false,
        targetTrackIndex: Int = 0,
    ) {
        hasModifiedInSession = true
        viewModelScope.launch {
            val persistedUri = repository.persistMediaLocally(context, uri, "videos")
            val metadata = VideoExportManager.readVideoMetadata(context, persistedUri)
            val dur = metadata.durationMs.takeIf { it > 400L } ?: 8_000L
            val clipId = if (replaceSelectedClip && _selectedClipId.value != null) {
                _selectedClipId.value!!
            } else if (!appendNew && targetTrackIndex == 0 && _snapshot.value.clips.all { it.sourceUriString.isNullOrBlank() }) {
                _snapshot.value.clips.firstOrNull()?.id ?: UUID.randomUUID().toString()
            } else {
                UUID.randomUUID().toString()
            }
            val thumbs = VideoExportManager.extractFilmstripThumbnails(
                context = context,
                uri = persistedUri,
                durationMs = dur,
                trimStartMs = 0L,
                trimEndMs = dur,
            )
            if (thumbs.isNotEmpty()) {
                _clipThumbnails.value = _clipThumbnails.value + (clipId to thumbs)
                if (!appendNew || _filmstripThumbnails.value.isEmpty()) {
                    _filmstripThumbnails.value = thumbs
                }
            }
            if (_selectedVideo.value == null || (!appendNew && targetTrackIndex == 0)) {
                _selectedVideo.value = metadata
            }
            _exportState.value = ExportUiState.Idle
            val realClipsOnTarget = _snapshot.value.clips.filter {
                !it.sourceUriString.isNullOrBlank() && it.trackIndex == targetTrackIndex
            }
            val nextTimelineStart = if (targetTrackIndex == 0) {
                realClipsOnTarget.maxOfOrNull { it.timelineStartMs + it.timelineDurationMs() } ?: 0L
            } else {
                val playPos = _playheadMs.value
                val trackEnd = realClipsOnTarget.maxOfOrNull { it.timelineStartMs + it.timelineDurationMs() } ?: 0L
                if (realClipsOnTarget.any { playPos in it.timelineStartMs..(it.timelineStartMs + it.timelineDurationMs()) }) {
                    trackEnd
                } else {
                    playPos
                }
            }
            val safeTrack = targetTrackIndex.coerceIn(0, _snapshot.value.effectiveVideoTrackCount() - 1)
            val newSegment = VideoClipSegment(
                id = clipId,
                sourceUriString = persistedUri.toString(),
                displayName = metadata.displayName,
                trackIndex = safeTrack,
                width = metadata.width,
                height = metadata.height,
                timelineStartMs = if (appendNew && (_snapshot.value.clips.any { !it.sourceUriString.isNullOrBlank() } || safeTrack > 0)) nextTimelineStart else 0L,
                trimStartMs = 0L,
                trimEndMs = dur,
                sourceDurationMs = dur,
            )
            mutate { s ->
                val updatedClips = when {
                    appendNew && (s.clips.any { !it.sourceUriString.isNullOrBlank() } || safeTrack > 0) ->
                        s.clips + newSegment
                    replaceSelectedClip && _selectedClipId.value != null ->
                        s.clips.map {
                            if (it.id == _selectedClipId.value) {
                                newSegment.copy(id = it.id, trackIndex = it.trackIndex, timelineStartMs = it.timelineStartMs)
                            } else it
                        }
                    else -> listOf(newSegment.copy(timelineStartMs = 0L))
                }
                s.copy(clips = updatedClips)
            }
            if (safeTrack > 0) {
                selectClip(clipId)
            } else if (!appendNew) {
                _playheadMs.value = 0L
            }
            val thumbPath = repository.saveProjectThumbnail(context, _activeProjectId.value, persistedUri, 0L)
            if (thumbPath != null) _activeProjectThumbnailPath.value = thumbPath
            saveProjectImmediately()
        }
    }

    fun totalTimelineDurationMs(): Long = computeTotalVideoDurationMs(_snapshot.value.clips)

    fun clipSpans(): List<ClipTimelineSpan> = VideoEffectsEngine.computeClipSpans(_snapshot.value.clips)

    fun trackSpans(trackIndex: Int): List<ClipTimelineSpan> = computeTrackSpans(_snapshot.value.clips, trackIndex)

    fun activeClipAtPlayhead(): VideoClipSegment {
        val snap = _snapshot.value
        val t = _playheadMs.value
        val byId = _selectedClipId.value?.let { id -> snap.clips.firstOrNull { it.id == id } }
        if (byId != null) return byId

        val v1Spans = computeTrackSpans(snap.clips, 0)
        val v1Idx = com.example.data.model.findActiveSpanIndex(v1Spans, t)
        if (v1Idx >= 0 && !snap.isVideoTrackHidden(0)) return v1Spans[v1Idx].clip

        for (idx in (snap.effectiveVideoTrackCount() - 1) downTo 0) {
            if (!snap.isVideoTrackHidden(idx)) {
                val trackSp = computeTrackSpans(snap.clips, idx)
                val active = com.example.data.model.findActiveSpanIndex(trackSp, t)
                if (active >= 0) return trackSp[active].clip
            }
        }
        return v1Spans.firstOrNull()?.clip
            ?: snap.clips.firstOrNull()
            ?: VideoClipSegment(id = "clip_main_1", displayName = "Import Video Clip")
    }

    fun updatePlayhead(ms: Long) {
        _playheadMs.value = ms.coerceIn(0L, totalTimelineDurationMs())
    }

    // --- Multi-Track Layout Controls ---
    fun addVideoTrack() {
        val curCount = _snapshot.value.effectiveVideoTrackCount()
        if (curCount >= 8) {
            _statusMessage.value = "Maximum 8 video tracks supported."
            return
        }
        val nextIdx = curCount
        val nextTrack = TimelineTrackConfig(
            id = "track_video_$nextIdx",
            kind = TimelineTrackKind.VIDEO,
            trackIndex = nextIdx,
            label = "V${nextIdx + 1}",
            subtitle = "Video layer",
            heightDp = 50f,
        )
        mutate { s ->
            val baseConfigs = s.normalizedTrackConfigs()
            val insertAfterIdx = baseConfigs.indexOfLast { it.kind == TimelineTrackKind.VIDEO }
            val updated = if (insertAfterIdx >= 0) {
                baseConfigs.toMutableList().apply { add(insertAfterIdx + 1, nextTrack) }
            } else {
                baseConfigs + nextTrack
            }
            s.copy(videoTrackCount = curCount + 1, trackConfigs = updated)
        }
        _statusMessage.value = "Added track V${nextIdx + 1}"
    }

    fun toggleTrackHidden(trackId: String) {
        mutate { s ->
            val updated = s.normalizedTrackConfigs().map { cfg ->
                if (cfg.id == trackId) cfg.copy(isHidden = !cfg.isHidden) else cfg
            }
            s.copy(trackConfigs = updated)
        }
    }

    fun toggleTrackLocked(trackId: String) {
        mutate { s ->
            val updated = s.normalizedTrackConfigs().map { cfg ->
                if (cfg.id == trackId) cfg.copy(isLocked = !cfg.isLocked) else cfg
            }
            s.copy(trackConfigs = updated)
        }
    }

    fun toggleTrackMuted(trackId: String) {
        mutate { s ->
            val updated = s.normalizedTrackConfigs().map { cfg ->
                if (cfg.id == trackId) cfg.copy(isMuted = !cfg.isMuted) else cfg
            }
            s.copy(trackConfigs = updated)
        }
    }

    fun resizeTrackHeight(trackId: String, newHeightDp: Float) {
        val clamped = newHeightDp.coerceIn(30f, 120f)
        mutate(pushHistory = false) { s ->
            val updated = s.normalizedTrackConfigs().map { cfg ->
                if (cfg.id == trackId) cfg.copy(heightDp = clamped) else cfg
            }
            s.copy(trackConfigs = updated)
        }
    }

    fun moveVideoClipToTrack(clipId: String, targetTrackIndex: Int) {
        val snap = _snapshot.value
        val clip = snap.clips.firstOrNull { it.id == clipId } ?: return
        val maxIdx = snap.effectiveVideoTrackCount() - 1
        val safeTarget = targetTrackIndex.coerceIn(0, maxIdx)
        if (safeTarget == clip.trackIndex) return
        if (snap.isVideoTrackLocked(clip.trackIndex) || snap.isVideoTrackLocked(safeTarget)) {
            _statusMessage.value = "Unlock track before moving clips."
            return
        }
        mutate(pushHistory = false) { s ->
            s.copy(
                clips = s.clips.map { c ->
                    if (c.id == clipId) c.copy(trackIndex = safeTarget) else c
                }
            )
        }
    }

    fun moveAudioToSlot(audioId: String, targetTrackIndex: Int) {
        val snap = _snapshot.value
        val au = snap.audioTracks.firstOrNull { it.id == audioId } ?: return
        val safeTarget = targetTrackIndex.coerceIn(0, 1)
        if (safeTarget == au.trackIndex) return
        if (snap.isAudioTrackLocked(au.trackIndex) || snap.isAudioTrackLocked(safeTarget)) {
            _statusMessage.value = "Unlock audio track before moving."
            return
        }
        mutate(pushHistory = false) { s ->
            s.copy(
                audioTracks = s.audioTracks.map { item ->
                    if (item.id == audioId) item.copy(trackIndex = safeTarget) else item
                }
            )
        }
    }

    // --- Clip Editing Operations ---
    fun splitSelectedOrActiveClip() {
        val snap = _snapshot.value
        val t = _playheadMs.value
        val targetClip = _selectedClipId.value?.let { id -> snap.clips.firstOrNull { it.id == id } }
            ?: activeClipAtPlayhead()
        val trackSpans = computeTrackSpans(snap.clips, targetClip.trackIndex)
        val targetSpan = trackSpans.firstOrNull { it.clip.id == targetClip.id } ?: return
        val clip = targetSpan.clip
        if (snap.isVideoTrackLocked(clip.trackIndex)) {
            _statusMessage.value = "V${clip.trackIndex + 1} is locked."
            return
        }
        val localMs = t - targetSpan.startMs
        val clipLen = targetSpan.endMs - targetSpan.startMs
        if (!isPlayheadInsideRange(t, targetSpan.startMs, targetSpan.endMs)) {
            _statusMessage.value = "Move playhead onto clip first"
            return
        }
        if (localMs < 200L || clipLen - localMs < 200L) {
            _statusMessage.value = "Move playhead further inside clip to split."
            return
        }
        val spd = clip.speed.effectiveSpeedMultiplier().coerceAtLeast(0.1f)
        val splitSourceDelta = (localMs * spd).toLong()
        val splitSourceMs = (clip.trimStartMs + splitSourceDelta)
            .coerceIn(clip.trimStartMs + 100L, (clip.trimEndMs - 100L).coerceAtLeast(clip.trimStartMs + 100L))

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
        val curThumbs = _clipThumbnails.value[clip.id]
        if (!curThumbs.isNullOrEmpty() && curThumbs.size >= 2) {
            val midIdx = (curThumbs.size * localMs / clipLen.coerceAtLeast(1L)).toInt().coerceIn(1, curThumbs.lastIndex)
            _clipThumbnails.value = _clipThumbnails.value +
                (clip.id to curThumbs.subList(0, midIdx)) +
                (secondId to curThumbs.subList(midIdx, curThumbs.size))
        }
        _selectedClipId.value = secondId
        _statusMessage.value = "Split at ${formatSplitTimeMs(t)}"
        saveProjectImmediately()
    }

    fun moveVideoClip(clipId: String, deltaMs: Long) {
        if (deltaMs == 0L) return
        val snap = _snapshot.value
        val clipTarget = snap.clips.firstOrNull { it.id == clipId } ?: return
        if (snap.isVideoTrackLocked(clipTarget.trackIndex)) return
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
        if (deltaMs == 0L || _snapshot.value.isTextTrackLocked()) return
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
        val auTarget = _snapshot.value.audioTracks.firstOrNull { it.id == audioId } ?: return
        if (_snapshot.value.isAudioTrackLocked(auTarget.trackIndex)) return
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
        val snap = _snapshot.value
        val targetClip = snap.clips.firstOrNull { it.id == clipId } ?: return
        if (snap.isVideoTrackLocked(targetClip.trackIndex)) return
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
        if (_snapshot.value.isTextTrackLocked()) return
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
        val auTarget = _snapshot.value.audioTracks.firstOrNull { it.id == audioId } ?: return
        if (_snapshot.value.isAudioTrackLocked(auTarget.trackIndex)) return
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
        if (_snapshot.value.isVideoTrackLocked(clip.trackIndex)) {
            _statusMessage.value = "V${clip.trackIndex + 1} is locked."
            return
        }
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
                } else if (c.trackIndex == clip.trackIndex && c.timelineStartMs >= insertAt - 20L) {
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
        val snap = _snapshot.value
        val targetClip = _selectedClipId.value?.let { id -> snap.clips.firstOrNull { it.id == id } }
            ?: activeClipAtPlayhead()
        val id = targetClip.id
        if (snap.isVideoTrackLocked(targetClip.trackIndex)) {
            _statusMessage.value = "V${targetClip.trackIndex + 1} is locked."
            return
        }
        val currentClips = snap.clips
        val hasImportedClips = currentClips.any { !it.sourceUriString.isNullOrBlank() }
        if (currentClips.isEmpty() || !hasImportedClips) {
            _statusMessage.value = "No video clip to delete."
            return
        }
        if (currentClips.size <= 1) {
            mutate { s ->
                s.copy(
                    clips = listOf(VideoClipSegment(id = UUID.randomUUID().toString(), displayName = "Import Video Clip")),
                    transitions = emptyList(),
                )
            }
            _selectedVideo.value = null
            _filmstripThumbnails.value = emptyList()
            _clipThumbnails.value = emptyMap()
            _selectedClipId.value = null
            _playheadMs.value = 0L
            saveProjectImmediately()
            return
        }
        mutate { s ->
            val remaining = s.clips.filterNot { it.id == id }
            val updated = if (targetClip.trackIndex == 0) {
                var cursor = 0L
                val v1Compacted = remaining.filter { it.trackIndex == 0 }
                    .sortedBy { it.timelineStartMs }
                    .map { c ->
                        val u = c.copy(timelineStartMs = cursor)
                        cursor += u.timelineDurationMs()
                        u
                    }
                val otherTracks = remaining.filter { it.trackIndex != 0 }
                (v1Compacted + otherTracks).sortedWith(compareBy({ it.trackIndex }, { it.timelineStartMs }))
            } else {
                remaining
            }
            s.copy(
                clips = updated,
                transitions = s.transitions.filterNot { it.afterClipId == id },
            )
        }
        _clipThumbnails.value = _clipThumbnails.value - id
        _selectedClipId.value = null
        _playheadMs.value = _playheadMs.value.coerceIn(0L, totalTimelineDurationMs())
        saveProjectImmediately()
    }

    fun freezeSelectedClipAtPlayhead() {
        val snap = _snapshot.value
        val t = _playheadMs.value
        val target = _selectedClipId.value?.let { id -> snap.clips.firstOrNull { it.id == id } }
            ?: activeClipAtPlayhead()
        val span = computeTrackSpans(snap.clips, target.trackIndex).firstOrNull { it.clip.id == target.id } ?: return
        val clip = span.clip
        if (snap.isVideoTrackLocked(clip.trackIndex)) {
            _statusMessage.value = "V${clip.trackIndex + 1} is locked."
            return
        }
        val localMs = (t - span.startMs).coerceIn(0L, clip.timelineDurationMs())
        val freezeSourceMs = clip.trimStartMs + (localMs * clip.speed.effectiveSpeedMultiplier()).toLong()
        val frozenId = UUID.randomUUID().toString()
        val insertAt = span.endMs
        val frozenClip = VideoClipSegment(
            id = frozenId,
            sourceUriString = clip.sourceUriString,
            displayName = "Freeze",
            trackIndex = clip.trackIndex,
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
                } else if (c.trackIndex == clip.trackIndex && c.timelineStartMs >= insertAt - 20L) {
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

    fun toggleReverseSelectedClip(context: Context, reverseAudio: Boolean = true) {
        val target = activeClipAtPlayhead()
        if (_snapshot.value.isVideoTrackLocked(target.trackIndex)) {
            _statusMessage.value = "V${target.trackIndex + 1} is locked."
            return
        }
        if (target.isReversed) {
            mutateClip(target.id) { it.copy(isReversed = false, cutFlashPunchOnStart = false) }
            _statusMessage.value = "Restored original forward playback"
            refreshClipThumbnailsAsync(context, target.copy(isReversed = false, cutFlashPunchOnStart = false))
            saveProjectImmediately()
            return
        }
        reverseMultipleClips(
            context = context,
            clipIds = listOf(target.id),
            isBatchReverseAll = false,
            reverseAudio = reverseAudio,
        )
    }

    fun reverseMultipleClips(
        context: Context,
        clipIds: List<String>,
        isBatchReverseAll: Boolean,
        reverseAudio: Boolean,
    ) {
        val snap = _snapshot.value
        val allClips = snap.clips.sortedBy { it.timelineStartMs }
        val targets = allClips.filter {
            it.id in clipIds && !it.sourceUriString.isNullOrBlank() && !snap.isVideoTrackLocked(it.trackIndex)
        }
        if (targets.isEmpty()) {
            _statusMessage.value = "Import a video clip before reversing."
            return
        }
        viewModelScope.launch {
            val total = targets.size
            val updatedClipsMap = mutableMapOf<String, VideoClipSegment>()
            targets.forEachIndexed { idx, clip ->
                val srcUri = clip.sourceUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
                if (srcUri != null) {
                    val isShortCut = shouldApplyShortReverseCutEffect(clip, isBatchReverseAll) && idx > 0
                    val reusableExisting = clip.reversedUriString?.takeIf {
                        clip.reverseAudio == reverseAudio && java.io.File(Uri.parse(it).path ?: "").exists()
                    }
                    if (reusableExisting != null) {
                        val updated = clip.copy(
                            isReversed = true,
                            reverseAudio = reverseAudio,
                            isMuted = if (!reverseAudio) true else clip.isMuted,
                            cutFlashPunchOnStart = isShortCut,
                        )
                        updatedClipsMap[clip.id] = updated
                    } else {
                        _reverseProgress.value = ReverseProgressState(
                            currentClipIndex = idx + 1,
                            totalClips = total,
                            clipName = clip.displayName,
                            percent = 0,
                            statusLabel = "Starting reverse...",
                        )
                        val res = VideoReverseProcessor.generateReversedClipMp4(
                            context = context,
                            sourceUri = srcUri,
                            clipId = clip.id,
                            trimStartMs = clip.trimStartMs,
                            trimEndMs = clip.trimEndMs,
                            reverseAudio = reverseAudio,
                            targetWidth = clip.width,
                            targetHeight = clip.height,
                            minFrameGapUs = if (clip.baseDurationMs() <= 800L) 16_000L else 30_000L,
                            onProgress = { pct, label ->
                                _reverseProgress.value = ReverseProgressState(
                                    currentClipIndex = idx + 1,
                                    totalClips = total,
                                    clipName = clip.displayName,
                                    percent = pct,
                                    statusLabel = label,
                                )
                            },
                        )
                        res.onSuccess { out ->
                            val updated = clip.copy(
                                isReversed = true,
                                reversedUriString = out.reversedUri.toString(),
                                reverseAudio = reverseAudio,
                                isMuted = if (!reverseAudio) true else clip.isMuted,
                                cutFlashPunchOnStart = isShortCut,
                            )
                            updatedClipsMap[clip.id] = updated
                        }.onFailure { err ->
                            _statusMessage.value = "Reverse failed (${clip.displayName}): ${err.localizedMessage}"
                        }
                    }
                }
            }
            _reverseProgress.value = null
            if (updatedClipsMap.isNotEmpty()) {
                mutate { s ->
                    val mapped = s.clips.sortedBy { it.timelineStartMs }.map { c -> updatedClipsMap[c.id] ?: c }
                    if (isBatchReverseAll) {
                        // Ensure seamless back-to-back playback with 0 gaps or black frames
                        var cursor = 0L
                        val packed = mapped.map { c ->
                            val adj = c.copy(timelineStartMs = cursor)
                            cursor += adj.timelineDurationMs()
                            adj
                        }
                        s.copy(clips = packed)
                    } else {
                        s.copy(clips = mapped)
                    }
                }
                updatedClipsMap.values.forEach { refreshClipThumbnailsAsync(context, it) }
                saveProjectImmediately()
                _statusMessage.value = if (isBatchReverseAll) {
                    "Reversed all ${updatedClipsMap.size} clips cleanly."
                } else {
                    "Reversed ${updatedClipsMap.size} clip(s) frame-by-frame."
                }
            }
        }
    }

    fun applySmoothAutoReverse(
        context: Context,
        customConfig: SmoothReverseConfig? = null,
    ) {
        val target = activeClipAtPlayhead()
        if (_snapshot.value.isVideoTrackLocked(target.trackIndex)) {
            _statusMessage.value = "V${target.trackIndex + 1} is locked."
            return
        }
        val srcUriStr = target.sourceUriString
        val srcUri = srcUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (srcUri == null) {
            _statusMessage.value = "Import a video clip before using Smooth Reverse."
            return
        }
        val playheadLocal = (_playheadMs.value - target.timelineStartMs).coerceIn(0L, target.timelineDurationMs())
        val baseConfig = customConfig ?: SmoothReverseConfig(
            originalTrimStartMs = target.trimStartMs,
            originalTrimEndMs = target.trimEndMs,
        )
        val trimWindow = selectBestSmoothReverseWindow(
            clip = target,
            preferredLengthMs = if (customConfig != null) customConfig.segmentDurationMs else null,
            playheadLocalMs = playheadLocal,
        )
        val finalConfig = baseConfig.copy(
            segmentDurationMs = (trimWindow.second - trimWindow.first).coerceAtLeast(200L),
            originalTrimStartMs = target.trimStartMs,
            originalTrimEndMs = target.trimEndMs,
        )

        viewModelScope.launch {
            val reversedClipId = UUID.randomUUID().toString()
            val groupId = UUID.randomUUID().toString()
            _reverseProgress.value = ReverseProgressState(
                currentClipIndex = 1,
                totalClips = 1,
                clipName = "${target.displayName} • Smooth Reverse",
                percent = 0,
                statusLabel = "Auto-trimming & reversing frames...",
            )
            val revRes = VideoReverseProcessor.generateReversedClipMp4(
                context = context,
                sourceUri = srcUri,
                clipId = reversedClipId,
                trimStartMs = trimWindow.first,
                trimEndMs = trimWindow.second,
                reverseAudio = false,
                targetWidth = target.width,
                targetHeight = target.height,
                minFrameGapUs = 14_000L,
                onProgress = { pct, label ->
                    _reverseProgress.value = ReverseProgressState(
                        currentClipIndex = 1,
                        totalClips = 1,
                        clipName = "${target.displayName} • Smooth Reverse",
                        percent = pct,
                        statusLabel = label,
                    )
                },
            )
            _reverseProgress.value = null

            revRes.onSuccess { out ->
                val (clip1, clip2) = buildSmoothReversePair(
                    sourceClip = target,
                    reversedUriString = out.reversedUri.toString(),
                    reversedClipId = reversedClipId,
                    groupId = groupId,
                    config = finalConfig,
                    trimWindow = trimWindow,
                )
                mutate(pushHistory = true) { s ->
                    val sorted = s.clips.sortedBy { it.timelineStartMs }
                    val newClips = mutableListOf<VideoClipSegment>()
                    var prevEndOnTrack = 0L
                    var inserted = false
                    val targetTrack = target.trackIndex
                    sorted.forEach { c ->
                        if (c.trackIndex != targetTrack) {
                            newClips.add(c)
                        } else if (c.id == target.id) {
                            val c1 = clip1.copy(timelineStartMs = maxOf(prevEndOnTrack, target.timelineStartMs))
                            val c2 = clip2.copy(timelineStartMs = c1.timelineStartMs + c1.timelineDurationMs())
                            newClips.add(c1)
                            newClips.add(c2)
                            prevEndOnTrack = c2.timelineStartMs + c2.timelineDurationMs()
                            inserted = true
                        } else if (inserted) {
                            val shifted = c.copy(timelineStartMs = maxOf(prevEndOnTrack, c.timelineStartMs + (clip1.timelineDurationMs() + clip2.timelineDurationMs() - target.timelineDurationMs())))
                            newClips.add(shifted)
                            prevEndOnTrack = shifted.timelineStartMs + shifted.timelineDurationMs()
                        } else {
                            newClips.add(c)
                            prevEndOnTrack = maxOf(prevEndOnTrack, c.timelineStartMs + c.timelineDurationMs())
                        }
                    }
                    s.copy(clips = newClips)
                }
                refreshClipThumbnailsAsync(context, clip1)
                refreshClipThumbnailsAsync(context, clip2)
                _selectedClipId.value = clip1.id
                _playheadMs.value = clip1.timelineStartMs
                saveProjectImmediately()
                _statusMessage.value = "Applied Smooth Auto-Reverse (10x → 0.1x ⇄ 0.1x → 10x)"
            }.onFailure { err ->
                _statusMessage.value = "Smooth Reverse failed: ${err.localizedMessage}"
            }
        }
    }

    fun updateSmoothReverseConfig(
        context: Context,
        groupId: String,
        newConfig: SmoothReverseConfig,
        commitHistory: Boolean = false,
    ) {
        val groupClips = _snapshot.value.clips
            .filter { it.smoothReverseGroupId == groupId }
            .sortedBy { it.timelineStartMs }
        val orig = groupClips.firstOrNull { it.smoothReverseRole == SmoothReverseRole.ORIGINAL } ?: return
        val rev = groupClips.firstOrNull { it.smoothReverseRole == SmoothReverseRole.REVERSED } ?: return
        val oldSegLen = orig.baseDurationMs()
        val maxSpan = (orig.sourceDurationMs - orig.trimStartMs).coerceAtLeast(200L)
        val newSegLen = newConfig.segmentDurationMs.coerceIn(200L, minOf(2_000L, maxSpan))
        val newTrimEnd = (orig.trimStartMs + newSegLen).coerceAtMost(orig.sourceDurationMs)
        val trimChanged = abs(newTrimEnd - orig.trimEndMs) >= 20L

        if (!trimChanged) {
            val (u1, u2) = buildSmoothReversePair(
                sourceClip = orig,
                reversedUriString = rev.reversedUriString,
                reversedClipId = rev.id,
                groupId = groupId,
                config = newConfig.copy(segmentDurationMs = orig.baseDurationMs()),
                trimWindow = orig.trimStartMs to orig.trimEndMs,
            )
            mutate(pushHistory = commitHistory) { s ->
                val sorted = s.clips.sortedBy { it.timelineStartMs }
                val oldGroupEnd = rev.timelineStartMs + rev.timelineDurationMs()
                val newGroupEnd = u2.timelineStartMs + u2.timelineDurationMs()
                val tailDelta = newGroupEnd - oldGroupEnd
                s.copy(
                    clips = sorted.map { c ->
                        when (c.id) {
                            orig.id -> u1
                            rev.id -> u2
                            else -> if (c.trackIndex == orig.trackIndex && c.timelineStartMs >= oldGroupEnd - 25L) {
                                c.copy(timelineStartMs = maxOf(newGroupEnd, c.timelineStartMs + tailDelta))
                            } else c
                        }
                    }
                )
            }
            return
        }

        val srcUri = orig.sourceUriString?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return
        viewModelScope.launch {
            _reverseProgress.value = ReverseProgressState(
                currentClipIndex = 1,
                totalClips = 1,
                clipName = "${orig.displayName} • Adjust Length",
                percent = 0,
                statusLabel = "Updating Smooth Reverse segment...",
            )
            val revRes = VideoReverseProcessor.generateReversedClipMp4(
                context = context,
                sourceUri = srcUri,
                clipId = rev.id,
                trimStartMs = orig.trimStartMs,
                trimEndMs = newTrimEnd,
                reverseAudio = rev.reverseAudio,
                targetWidth = orig.width,
                targetHeight = orig.height,
                minFrameGapUs = 14_000L,
                onProgress = { pct, label ->
                    _reverseProgress.value = ReverseProgressState(
                        currentClipIndex = 1,
                        totalClips = 1,
                        clipName = "${orig.displayName} • Adjust Length",
                        percent = pct,
                        statusLabel = label,
                    )
                },
            )
            _reverseProgress.value = null
            revRes.onSuccess { out ->
                val (u1, u2) = buildSmoothReversePair(
                    sourceClip = orig,
                    reversedUriString = out.reversedUri.toString(),
                    reversedClipId = rev.id,
                    groupId = groupId,
                    config = newConfig.copy(segmentDurationMs = newTrimEnd - orig.trimStartMs),
                    trimWindow = orig.trimStartMs to newTrimEnd,
                )
                mutate(pushHistory = true) { s ->
                    val sorted = s.clips.sortedBy { it.timelineStartMs }
                    val oldGroupEnd = rev.timelineStartMs + rev.timelineDurationMs()
                    val newGroupEnd = u2.timelineStartMs + u2.timelineDurationMs()
                    val tailDelta = newGroupEnd - oldGroupEnd
                    s.copy(
                        clips = sorted.map { c ->
                            when (c.id) {
                                orig.id -> u1
                                rev.id -> u2
                                else -> if (c.timelineStartMs >= oldGroupEnd - 25L) {
                                    c.copy(timelineStartMs = maxOf(newGroupEnd, c.timelineStartMs + tailDelta))
                                } else c
                            }
                        }
                    )
                }
                refreshClipThumbnailsAsync(context, u1)
                refreshClipThumbnailsAsync(context, u2)
                saveProjectImmediately()
            }
        }
    }

    private fun refreshClipThumbnailsAsync(context: Context, clip: VideoClipSegment) {
        val uriStr = clip.effectiveUriString() ?: return
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return
        viewModelScope.launch {
            val thumbs = VideoExportManager.extractFilmstripThumbnails(
                context = context,
                uri = uri,
                durationMs = clip.effectiveSourceDurationMs(),
                trimStartMs = clip.effectiveTrimStartMs(),
                trimEndMs = clip.effectiveTrimEndMs(),
                isReversed = clip.isReversed && !clip.hasReversedMediaReady(),
            )
            if (thumbs.isNotEmpty()) {
                _clipThumbnails.value = _clipThumbnails.value + (clip.id to thumbs)
                if (_snapshot.value.clips.firstOrNull()?.id == clip.id) {
                    _filmstripThumbnails.value = thumbs
                }
            }
        }
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
        if (_snapshot.value.isVideoTrackLocked(target.trackIndex)) return
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
        if (_snapshot.value.isVideoTrackLocked(target.trackIndex)) return
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
        val nextRot = if (abs(rotationDeltaDeg) < 0.0001f) cur.rotateDeg else (cur.rotateDeg + rotationDeltaDeg)
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
            baseAdjust = clip.adjust,
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
            baseAdjust = clip.adjust,
            baseFilterIntensity = _snapshot.value.filterIntensity,
        )
        val adj = clip.adjust
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
        if (_snapshot.value.isTextTrackLocked()) return
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
        val t = _playheadMs.value
        val textId = _selectedTextId.value
        if (textId != null) {
            val tx = _snapshot.value.textTracks.firstOrNull { it.id == textId } ?: return
            if (!isPlayheadInsideRange(t, tx.startMs, tx.endMs)) {
                _statusMessage.value = "Move playhead onto clip first"
                return
            }
            val local = (t - tx.startMs).coerceIn(0L, (tx.endMs - tx.startMs).coerceAtLeast(1L))
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
        val span = computeTrackSpans(_snapshot.value.clips, clip.trackIndex)
            .firstOrNull { it.clip.id == clip.id } ?: return
        if (!isPlayheadInsideRange(t, span.startMs, span.endMs)) {
            _statusMessage.value = "Move playhead onto clip first"
            return
        }
        val localMs = (t - span.startMs).coerceIn(0L, span.endMs - span.startMs)
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
        val t = _playheadMs.value
        val textId = _selectedTextId.value
        if (textId != null) {
            val tx = _snapshot.value.textTracks.firstOrNull { it.id == textId } ?: return
            if (!isPlayheadInsideRange(t, tx.startMs, tx.endMs)) {
                _statusMessage.value = "Move playhead onto clip first"
                return
            }
            val local = (t - tx.startMs).coerceIn(0L, (tx.endMs - tx.startMs).coerceAtLeast(1L))
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
        val span = computeTrackSpans(_snapshot.value.clips, clip.trackIndex)
            .firstOrNull { it.clip.id == clip.id } ?: return
        if (!isPlayheadInsideRange(t, span.startMs, span.endMs)) {
            _statusMessage.value = "Move playhead onto clip first"
            return
        }
        val localMs = (t - span.startMs).coerceIn(0L, span.endMs - span.startMs)
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
        if (!isPlayheadInsideRange(t, fx.startMs, fx.endMs)) {
            _statusMessage.value = "Move playhead onto clip first"
            return
        }
        if (t < fx.startMs + 200L || t > fx.endMs - 200L) {
            _statusMessage.value = "Move playhead further inside effect to split."
            return
        }
        val first = fx.copy(endMs = t)
        val second = fx.copy(id = UUID.randomUUID().toString(), startMs = t)
        mutate { s ->
            s.copy(effectLayers = s.effectLayers.flatMap { if (it.id == id) listOf(first, second) else listOf(it) })
        }
        selectEffectLayer(second.id)
        _statusMessage.value = "Split at ${formatSplitTimeMs(t)}"
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

    fun applyCapCutTimeslice(
        amount: Float = 100f,
        speed: Float = 100f,
        rotateDeg: Float = 15f,
        stretchDurationSec: Float = 0.4f,
        previewEnabled: Boolean = true,
    ) {
        val snap = _snapshot.value
        val activeClip = activeClipAtPlayhead()
        val trackIdx = activeClip.trackIndex
        if (snap.isVideoTrackLocked(trackIdx)) {
            _statusMessage.value = "V${trackIdx + 1} is locked."
            return
        }
        val playhead = _playheadMs.value
        val safeAmount = amount.coerceIn(0f, 100f)
        val safeSpeed = speed.coerceIn(0f, 100f)
        val safeRotate = rotateDeg.coerceIn(0f, 360f)
        val safeStretchDur = stretchDurationSec.coerceIn(0.3f, 0.5f)
        val effectLayerId = UUID.randomUUID().toString()

        var selectedClip1Id = activeClip.id
        var splitThumbnailUpdate: Pair<String, List<ImageBitmap>>? = null
        var secondSplitThumbUpdate: Pair<String, List<ImageBitmap>>? = null

        mutate { s ->
            // Clean any prior CapCut Timeslice effect/transition artifacts first
            val oldCapCutLayerIds = s.clips.mapNotNull { it.timeslice.capcutEffectLayerId }.toSet()
            val oldCapCutTransClipIds = s.clips.mapNotNull { it.timeslice.capcutTransitionAfterClipId }.toSet()
            var workingClips = s.clips.map { c ->
                if (c.timeslice.isCapCutModeActive()) c.copy(timeslice = TimesliceSettings()) else c
            }
            var workingEffects = s.effectLayers.filterNot { it.id in oldCapCutLayerIds }
            var workingTransitions = s.transitions.filterNot { it.afterClipId in oldCapCutTransClipIds }

            var trackSpans = computeTrackSpans(workingClips, trackIdx)
            if (trackSpans.isEmpty()) {
                return@mutate s
            }

            // Step 5: If only 1 clip on track, auto-split at playhead (or midpoint if at edges)
            if (trackSpans.size == 1) {
                val onlySpan = trackSpans.first()
                val onlyClip = onlySpan.clip
                val clipLen = (onlySpan.endMs - onlySpan.startMs).coerceAtLeast(1L)
                if (clipLen >= 450L) {
                    val minSplit = onlySpan.startMs + 200L
                    val maxSplit = onlySpan.endMs - 200L
                    val splitT = if (playhead in minSplit..maxSplit) {
                        playhead
                    } else {
                        (onlySpan.startMs + onlySpan.endMs) / 2L
                    }
                    val localMs = (splitT - onlySpan.startMs).coerceIn(200L, clipLen - 200L)
                    val spd = onlyClip.speed.effectiveSpeedMultiplier().coerceAtLeast(0.1f)
                    val splitSourceMs = (onlyClip.trimStartMs + (localMs * spd).toLong())
                        .coerceIn(onlyClip.trimStartMs + 100L, (onlyClip.trimEndMs - 100L).coerceAtLeast(onlyClip.trimStartMs + 100L))
                    val first = onlyClip.copy(
                        timelineStartMs = onlySpan.startMs,
                        trimEndMs = splitSourceMs,
                        keyframes = onlyClip.keyframes.filter { it.timeMs <= localMs },
                        volumeKeyframes = onlyClip.volumeKeyframes.filter { it.timeMs <= localMs },
                    )
                    val firstDur = first.timelineDurationMs()
                    val secondId = UUID.randomUUID().toString()
                    val second = onlyClip.copy(
                        id = secondId,
                        timelineStartMs = onlySpan.startMs + firstDur,
                        trimStartMs = splitSourceMs,
                        keyframes = onlyClip.keyframes.filter { it.timeMs >= localMs }.map { it.copy(timeMs = it.timeMs - localMs) },
                        volumeKeyframes = onlyClip.volumeKeyframes.filter { it.timeMs >= localMs }.map { it.copy(timeMs = it.timeMs - localMs) },
                    )
                    workingClips = workingClips.flatMap { if (it.id == onlyClip.id) listOf(first, second) else listOf(it) }
                    val curThumbs = _clipThumbnails.value[onlyClip.id]
                    if (!curThumbs.isNullOrEmpty() && curThumbs.size >= 2) {
                        val midIdx = (curThumbs.size * localMs / clipLen.coerceAtLeast(1L)).toInt().coerceIn(1, curThumbs.lastIndex)
                        splitThumbnailUpdate = onlyClip.id to curThumbs.subList(0, midIdx)
                        secondSplitThumbUpdate = secondId to curThumbs.subList(midIdx, curThumbs.size)
                    }
                    trackSpans = computeTrackSpans(workingClips, trackIdx)
                }
            }

            // Pick Clip 1 + Clip 2 boundary nearest playhead (or first pair)
            val pairIdx = if (trackSpans.size >= 2) {
                var bestIdx = 0
                var bestDist = Long.MAX_VALUE
                for (idx in 0 until trackSpans.lastIndex) {
                    val d = kotlin.math.abs(trackSpans[idx].endMs - playhead)
                    if (d < bestDist) {
                        bestDist = d
                        bestIdx = idx
                    }
                }
                bestIdx
            } else 0

            val span1 = trackSpans[pairIdx]
            val span2 = trackSpans.getOrNull(pairIdx + 1)
            val cutMs = span1.endMs
            selectedClip1Id = span1.clip.id

            // Step 1-3: Auto-apply Delay Glitch spanning END of Clip 1 + START of Clip 2, Object = All Videos (V1+V2)
            val fxStart = if (span2 != null) {
                (cutMs - 650L).coerceAtLeast(span1.startMs)
            } else {
                span1.startMs
            }
            val fxEnd = if (span2 != null) {
                (cutMs + 650L).coerceAtMost(span2.endMs)
            } else {
                span1.endMs
            }
            val delayGlitchLayer = VideoEffectLayer(
                id = effectLayerId,
                type = CapCutEffectType.DELAY_GLITCH,
                intensity = (safeAmount / 100f).coerceIn(0f, 1f),
                startMs = fxStart,
                endMs = fxEnd.coerceAtLeast(fxStart + 250L),
                applyToAllVideos = true,
                speed = safeSpeed,
                rotateDeg = safeRotate,
            )
            workingEffects = workingEffects + delayGlitchLayer

            // Step 4: Auto-add Stretch Left transition between Clip 1 and Clip 2
            if (span2 != null) {
                workingTransitions = workingTransitions.filterNot { it.afterClipId == span1.clip.id } +
                    TransitionSpec(
                        afterClipId = span1.clip.id,
                        type = TransitionType.STRETCH_LEFT,
                        durationSec = safeStretchDur,
                    )
            }

            val tsSettings = TimesliceSettings(
                enabled = true,
                mode = TimesliceMode.CAPCUT,
                previewEnabled = previewEnabled,
                capcutAmount = safeAmount,
                capcutSpeed = safeSpeed,
                capcutRotateDeg = safeRotate,
                capcutStretchDurationSec = safeStretchDur,
                capcutApplyToAllVideos = true,
                capcutEffectLayerId = effectLayerId,
                capcutTransitionAfterClipId = if (span2 != null) span1.clip.id else null,
            )
            val finalClips = workingClips.map { c ->
                if (c.id == span1.clip.id) c.copy(timeslice = tsSettings) else c
            }

            s.copy(
                clips = finalClips,
                effectLayers = workingEffects,
                transitions = workingTransitions,
            )
        }

        splitThumbnailUpdate?.let { (id, thumbs) ->
            _clipThumbnails.value = _clipThumbnails.value + (id to thumbs)
        }
        secondSplitThumbUpdate?.let { (id, thumbs) ->
            _clipThumbnails.value = _clipThumbnails.value + (id to thumbs)
        }
        _selectedClipId.value = selectedClip1Id
        _statusMessage.value = "Applied CapCut Timeslice (Delay Glitch + Stretch Left)"
        saveProjectImmediately()
    }

    fun applyNodeTimeslice(settings: TimesliceSettings = TimesliceSettings.viralNodePreset()) {
        val target = activeClipAtPlayhead()
        if (_snapshot.value.isVideoTrackLocked(target.trackIndex)) {
            _statusMessage.value = "V${target.trackIndex + 1} is locked."
            return
        }
        val oldFxId = target.timeslice.capcutEffectLayerId
        val oldTrClipId = target.timeslice.capcutTransitionAfterClipId
        val nodeCfg = settings.copy(
            enabled = true,
            mode = TimesliceMode.NODE,
            previewEnabled = true,
            capcutEffectLayerId = null,
            capcutTransitionAfterClipId = null,
        )
        mutate { s ->
            s.copy(
                clips = s.clips.map { c ->
                    if (c.id == target.id) c.copy(timeslice = nodeCfg) else c
                },
                effectLayers = if (oldFxId != null) s.effectLayers.filterNot { it.id == oldFxId } else s.effectLayers,
                transitions = if (oldTrClipId != null) s.transitions.filterNot { it.afterClipId == oldTrClipId } else s.transitions,
            )
        }
        _selectedClipId.value = target.id
        _statusMessage.value = "Applied Time Slice (Node Mode) to ${target.displayName}"
        saveProjectImmediately()
    }

    fun updateTimesliceSettings(settings: TimesliceSettings, commitHistory: Boolean = false) {
        val target = activeClipAtPlayhead()
        if (_snapshot.value.isVideoTrackLocked(target.trackIndex)) return
        mutate(pushHistory = commitHistory) { s ->
            val updatedClips = s.clips.map { c ->
                if (c.id == target.id) c.copy(timeslice = settings) else c
            }
            if (settings.isCapCutModeActive()) {
                val fxId = settings.capcutEffectLayerId
                val trClipId = settings.capcutTransitionAfterClipId
                val updatedFx = if (fxId != null) {
                    s.effectLayers.map { fx ->
                        if (fx.id == fxId) {
                            fx.copy(
                                intensity = (settings.capcutAmount / 100f).coerceIn(0f, 1f),
                                speed = settings.capcutSpeed.coerceIn(0f, 100f),
                                rotateDeg = settings.capcutRotateDeg.coerceIn(0f, 360f),
                                applyToAllVideos = settings.capcutApplyToAllVideos,
                            )
                        } else fx
                    }
                } else s.effectLayers

                val updatedTr = if (trClipId != null) {
                    s.transitions.map { tr ->
                        if (tr.afterClipId == trClipId) {
                            tr.copy(durationSec = settings.capcutStretchDurationSec.coerceIn(0.3f, 0.5f))
                        } else tr
                    }
                } else s.transitions

                s.copy(
                    clips = updatedClips,
                    effectLayers = updatedFx,
                    transitions = updatedTr,
                )
            } else {
                s.copy(clips = updatedClips)
            }
        }
    }

    fun toggleTimeslicePreview() {
        val target = activeClipAtPlayhead()
        if (!target.timeslice.enabled) return
        val next = target.timeslice.copy(previewEnabled = !target.timeslice.previewEnabled)
        updateTimesliceSettings(next, commitHistory = true)
        _statusMessage.value = if (next.previewEnabled) "Timeslice preview ON" else "Timeslice preview OFF (kept for Export)"
    }

    fun removeTimeslice() {
        val target = activeClipAtPlayhead()
        val fxId = target.timeslice.capcutEffectLayerId
        val trClipId = target.timeslice.capcutTransitionAfterClipId
        mutate { s ->
            s.copy(
                clips = s.clips.map { c ->
                    if (c.id == target.id) c.copy(timeslice = TimesliceSettings()) else c
                },
                effectLayers = if (fxId != null) s.effectLayers.filterNot { it.id == fxId } else s.effectLayers,
                transitions = if (trClipId != null) s.transitions.filterNot { it.afterClipId == trClipId } else s.transitions,
            )
        }
        _statusMessage.value = "Removed Timeslice from ${target.displayName}"
        saveProjectImmediately()
    }

    fun saveTimesliceAsPreset() {
        val target = activeClipAtPlayhead()
        val ts = target.timeslice
        if (!ts.enabled) {
            _statusMessage.value = "Apply a Timeslice mode first"
            return
        }
        val preset = if (ts.isCapCutModeActive()) {
            FfxPreset(
                name = "CapCut Timeslice Ghost",
                fileName = "luna_capcut_timeslice.ffx",
                category = PresetCategory.MOTION_SHAKE.label,
                description = "CapCut Timeslice: Delay Glitch (Amount ${ts.capcutAmount.toInt()}, Speed ${ts.capcutSpeed.toInt()}, Rotate ${ts.capcutRotateDeg.toInt()}°) + Stretch Left ${String.format(java.util.Locale.US, "%.2fs", ts.capcutStretchDurationSec)}",
                sourceType = "studio_timeslice",
                presetFormat = "FFX",
                ffxVersion = "LUNA Timeslice v2",
                effectModules = listOf("Delay Glitch", "Stretch Left", "All Videos V1+V2"),
                tags = listOf("LUNA", "Timeslice", "CapCut Mode", "Ghost"),
                rotationDegrees = ts.capcutRotateDeg.toDouble().coerceIn(-180.0, 180.0),
                glowIntensity = 0.25,
                shakeIntensity = (ts.capcutAmount / 100.0) * 0.2,
            )
        } else {
            FfxPreset(
                name = "Node Time Slice ${ts.clampedSliceCount()}x",
                fileName = "luna_node_timeslice.ffx",
                category = PresetCategory.MOTION_SHAKE.label,
                description = "Node Time Slice: Count=${ts.clampedSliceCount()}, Angle=${ts.clampedAngleDeg().toInt()}°, Interval=${ts.clampedFrameInterval()}, Offset=${ts.clampedFrameOffset()}, Feather=${String.format(java.util.Locale.US, "%.2f", ts.clampedFeather())}, Animate=${ts.animate}",
                sourceType = "studio_timeslice",
                presetFormat = "FFX",
                ffxVersion = "LUNA Timeslice v2",
                effectModules = listOf("Time Slice", "Node Pro"),
                tags = listOf("LUNA", "Timeslice", "Node Mode", "Viral"),
            )
        }
        viewModelScope.launch {
            repository.createPreset(preset).onSuccess {
                _statusMessage.value = "Saved \"${preset.name}\" to Preset Vault"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Could not save Timeslice preset"
            }
        }
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
        val target = activeClipAtPlayhead()
        if (_snapshot.value.isVideoTrackLocked(target.trackIndex)) return
        val targetId = target.id
        mutate(pushHistory = false) { s ->
            s.copy(
                clips = s.clips.map { if (it.id == targetId) it.copy(adjust = adjust) else it }
            )
        }
    }

    fun commitAdjustSnapshot() {
        undoStack.addLast(_snapshot.value)
        if (undoStack.size > 35) undoStack.removeFirst()
        redoStack.clear()
        syncUndoRedoFlags()
    }

    fun applySmartAutoAdjust() {
        val targetId = activeClipAtPlayhead().id
        mutate { s ->
            s.copy(
                clips = s.clips.map { clip ->
                    if (clip.id != targetId) clip
                    else clip.copy(
                        adjust = clip.adjust.copy(
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
            )
        }
        _statusMessage.value = "Applied Smart Auto Adjust"
    }

    fun resetAdjust() {
        val targetId = activeClipAtPlayhead().id
        mutateClip(targetId) { it.copy(adjust = AdjustSettings()) }
    }

    fun resetSelectedClipAll() {
        val targetId = activeClipAtPlayhead().id
        mutateClip(targetId) {
            it.copy(
                transform = TransformSettings(),
                crop = CropSettings(),
                adjust = AdjustSettings(),
                opacity = 1.0f,
                motionBlur = MotionBlurSettings(),
                stabilize = StabilizeMode.OFF,
                speed = SpeedSettings(),
                volumePercent = 100,
                isMuted = false,
                animation = AnimationSettings(),
                mask = MaskSettings(),
                smoothReverseRole = SmoothReverseRole.NONE,
                smoothReverseGroupId = null,
                smoothReverseConfig = null,
                timeslice = TimesliceSettings(),
                keyframes = emptyList(),
                volumeKeyframes = emptyList(),
            )
        }
        _statusMessage.value = "Reset selected clip settings"
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
        if (!isPlayheadInsideRange(t, ov.startMs, ov.endMs)) {
            _statusMessage.value = "Move playhead onto clip first"
            return
        }
        if (t < ov.startMs + 200L || t > ov.endMs - 200L) {
            _statusMessage.value = "Move playhead further inside overlay to split."
            return
        }
        val first = ov.copy(endMs = t)
        val second = ov.copy(id = UUID.randomUUID().toString(), startMs = t)
        mutate { s ->
            s.copy(overlays = s.overlays.flatMap { if (it.id == id) listOf(first, second) else listOf(it) })
        }
        selectOverlay(second.id)
        _statusMessage.value = "Split at ${formatSplitTimeMs(t)}"
    }

    fun addAudioTrack(context: Context, uri: Uri?, fallbackTitle: String = "Audio Track", targetTrackIndex: Int = 0) {
        viewModelScope.launch {
            val name = uri?.let { VideoExportManager.queryDisplayName(context, it) } ?: fallbackTitle
            val start = _playheadMs.value
            val end = totalTimelineDurationMs()
            val track = AudioTrackItem(
                uriString = uri?.toString(),
                title = name,
                trackIndex = targetTrackIndex.coerceIn(0, 1),
                startMs = start,
                endMs = end,
            )
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
        if (_snapshot.value.isAudioTrackLocked(au.trackIndex)) {
            _statusMessage.value = "A${au.trackIndex + 1} is locked."
            return
        }
        val t = _playheadMs.value
        if (!isPlayheadInsideRange(t, au.startMs, au.endMs)) {
            _statusMessage.value = "Move playhead onto clip first"
            return
        }
        if (t < au.startMs + 200L || t > au.endMs - 200L) {
            _statusMessage.value = "Move playhead further inside audio to split."
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
        _statusMessage.value = "Split at ${formatSplitTimeMs(t)}"
    }

    fun deleteAudioTrack(id: String) {
        val au = _snapshot.value.audioTracks.firstOrNull { it.id == id }
        if (au != null && _snapshot.value.isAudioTrackLocked(au.trackIndex)) {
            _statusMessage.value = "A${au.trackIndex + 1} is locked."
            return
        }
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
        if (_snapshot.value.isTextTrackLocked()) {
            _statusMessage.value = "TEXT track is locked."
            return
        }
        val tx = _snapshot.value.textTracks.firstOrNull { it.id == id } ?: return
        val t = _playheadMs.value
        if (!isPlayheadInsideRange(t, tx.startMs, tx.endMs)) {
            _statusMessage.value = "Move playhead onto clip first"
            return
        }
        if (t < tx.startMs + 200L || t > tx.endMs - 200L) {
            _statusMessage.value = "Move playhead further inside text to split."
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
        _statusMessage.value = "Split at ${formatSplitTimeMs(t)}"
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
        if (_snapshot.value.isTextTrackLocked()) {
            _statusMessage.value = "TEXT track is locked."
            return
        }
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
        _exportState.value = ExportUiState.Rendering(0, "Preparing Multi-Track Composition (${config.resolution.shortLabel} @ ${config.frameRate}fps)...")
        exportManager.startCapCutExport(
            sourceVideoUri = video.uri,
            snapshot = snap,
            aspectPreset = _aspectPreset.value,
            canvasFitMode = _canvasFitMode.value,
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
        val target = _snapshot.value.clips.firstOrNull { it.id == clipId }
        if (target != null && _snapshot.value.isVideoTrackLocked(target.trackIndex)) {
            _statusMessage.value = "V${target.trackIndex + 1} is locked."
            return
        }
        mutate { s ->
            s.copy(clips = s.clips.map { if (it.id == clipId) updater(it) else it })
        }
    }

    private fun mutate(pushHistory: Boolean = true, transform: (TimelineSnapshot) -> TimelineSnapshot) {
        hasModifiedInSession = true
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
            scheduleAutosave()
        }
    }

    private fun syncUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        _autosaveStatus.value = AutosaveStatus.SAVING
        autosaveJob = viewModelScope.launch {
            delay(350L)
            persistSnapshotNow()
        }
    }

    private fun saveProjectImmediately() {
        autosaveJob?.cancel()
        _autosaveStatus.value = AutosaveStatus.SAVING
        viewModelScope.launch {
            persistSnapshotNow()
        }
    }

    private suspend fun persistSnapshotNow() {
        val state = PersistedProjectState(
            projectId = _activeProjectId.value,
            title = _projectTitle.value,
            aspectPreset = _aspectPreset.value,
            resolutionLabel = _projectResolution.value,
            fps = _projectFps.value,
            canvasFitMode = _canvasFitMode.value,
            playheadMs = _playheadMs.value,
            timelineZoom = _timelineZoom.value,
            thumbnailPath = _activeProjectThumbnailPath.value,
            snapshot = _snapshot.value,
        )
        repository.saveProjectState(state)
        _autosaveStatus.value = AutosaveStatus.SAVED
    }

    companion object {
        private const val TAG = "StudioViewModel"

        fun shouldApplyShortReverseCutEffect(clip: VideoClipSegment, isBatchReverseAll: Boolean): Boolean {
            val dur = clip.timelineDurationMs()
            return isBatchReverseAll && dur in 450L..1_050L
        }

        /**
         * Step 1 — Auto Trim:
         * If selected clip > 500ms, auto-trim to best 0.2s–0.5s segment (default 400ms),
         * prioritizing beat marker -> playhead -> energetic 35% anchor. Preserves <= 500ms clips.
         */
        fun selectBestSmoothReverseWindow(
            clip: VideoClipSegment,
            preferredLengthMs: Long? = null,
            playheadLocalMs: Long? = null,
        ): Pair<Long, Long> {
            val trimStart = clip.trimStartMs.coerceAtLeast(0L)
            val trimEnd = clip.trimEndMs.coerceAtLeast(trimStart + 200L)
            val span = trimEnd - trimStart
            val targetLen = when {
                preferredLengthMs != null -> preferredLengthMs.coerceIn(200L, span)
                span <= 500L -> return trimStart to trimEnd
                else -> 400L.coerceIn(200L, 500L)
            }
            val spd = clip.speed.effectiveSpeedMultiplier().coerceAtLeast(0.1f)
            val anchorSourceMs = when {
                clip.beatMarkersMs.isNotEmpty() -> {
                    val nearestBeat = if (playheadLocalMs != null) {
                        clip.beatMarkersMs.minByOrNull { abs(it - playheadLocalMs) } ?: clip.beatMarkersMs.first()
                    } else {
                        clip.beatMarkersMs.first()
                    }
                    // End the forward clip right on the beat so the reversed return bounces off the beat
                    (trimStart + (nearestBeat * spd).toLong() - targetLen)
                }
                playheadLocalMs != null && playheadLocalMs > 0L -> {
                    trimStart + (playheadLocalMs * spd).toLong()
                }
                else -> {
                    trimStart + (span * 0.35f).toLong()
                }
            }
            val winStart = anchorSourceMs.coerceIn(trimStart, (trimEnd - targetLen).coerceAtLeast(trimStart))
            val winEnd = (winStart + targetLen).coerceAtMost(trimEnd)
            return winStart to winEnd
        }

        /**
         * Steps 2–5 — Builds [Original Smooth] + [Reversed Smooth] pair with mirrored 2-point
         * bezier speed ramp (10.0x->0.1x & 0.1x->10.0x), Smooth Slow-Mo Better Quality,
         * non-rotating Zoom+Y keyframes (CUBIC_OUT -> CUBIC_IN), and zero gap/overlap.
         */
        fun buildSmoothReversePair(
            sourceClip: VideoClipSegment,
            reversedUriString: String?,
            reversedClipId: String,
            groupId: String,
            config: SmoothReverseConfig = SmoothReverseConfig(),
            trimWindow: Pair<Long, Long> = selectBestSmoothReverseWindow(sourceClip),
        ): Pair<VideoClipSegment, VideoClipSegment> {
            val winStart = trimWindow.first.coerceAtLeast(0L)
            val winEnd = trimWindow.second.coerceAtLeast(winStart + 200L)
            val startSpd = config.startSpeed.coerceIn(0.1f, 10.0f)
            val endSpd = config.endSpeed.coerceIn(0.1f, 10.0f)
            val zoomPct = config.zoomPercent.coerceIn(100f, 300f)

            val speed1 = SpeedSettings(
                mode = SpeedMode.CURVE,
                smoothSlowMo = true,
                smoothSlowMoBetterQuality = true,
                curvePreset = CurveSpeedPreset.CUSTOM,
                curvePoints = listOf(startSpd, endSpd),
                curveControlPoints = listOf(
                    SpeedCurvePoint(0f, startSpd),
                    SpeedCurvePoint(1f, endSpd),
                ),
                curveBezierSmooth = true,
            )
            val temp1 = sourceClip.copy(
                trimStartMs = winStart,
                trimEndMs = winEnd,
                isReversed = false,
                reversedUriString = null,
                speed = speed1,
            )
            val dur1 = temp1.timelineDurationMs()
            val clip1Kfs = listOf(
                KeyframeNode(
                    id = UUID.randomUUID().toString(),
                    timeMs = 0L,
                    easingToNext = EasingGraph.CUBIC_OUT,
                    posX = 0f,   // Center X = 50
                    posY = 0f,   // Center Y = 50
                    zoomPercent = 100f,
                    rotateDeg = 0f,
                ),
                KeyframeNode(
                    id = UUID.randomUUID().toString(),
                    timeMs = dur1,
                    easingToNext = EasingGraph.LINEAR,
                    posX = 0f,    // X = 50
                    posY = 0.20f, // Y = 70 (+0.20 normalized)
                    zoomPercent = zoomPct,
                    rotateDeg = 0f,
                ),
            )
            val clip1 = temp1.copy(
                displayName = "${sourceClip.displayName} (Smooth)",
                cutFlashPunchOnStart = false,
                smoothReverseRole = SmoothReverseRole.ORIGINAL,
                smoothReverseGroupId = groupId,
                smoothReverseConfig = config.copy(segmentDurationMs = winEnd - winStart),
                transform = sourceClip.transform.copy(
                    posX = 0f,
                    posY = 0f,
                    zoomPercent = 100f,
                    rotateDeg = 0f,
                ),
                keyframes = clip1Kfs,
            )

            val speed2 = speed1.mirrored() // 0.1X -> 10.0X seamless mirror
            val clip2Start = clip1.timelineStartMs + dur1
            val temp2 = sourceClip.copy(
                id = reversedClipId,
                displayName = "${sourceClip.displayName} (Rev Smooth)",
                timelineStartMs = clip2Start,
                trimStartMs = winStart,
                trimEndMs = winEnd,
                isReversed = true,
                reversedUriString = reversedUriString,
                reverseAudio = false,
                isMuted = true,
                cutFlashPunchOnStart = false,
                smoothReverseRole = SmoothReverseRole.REVERSED,
                smoothReverseGroupId = groupId,
                smoothReverseConfig = config.copy(segmentDurationMs = winEnd - winStart),
                speed = speed2,
                transform = sourceClip.transform.copy(
                    posX = 0f,
                    posY = 0f,
                    zoomPercent = 100f,
                    rotateDeg = 0f,
                ),
            )
            val dur2 = temp2.timelineDurationMs()
            val clip2Kfs = listOf(
                KeyframeNode(
                    id = UUID.randomUUID().toString(),
                    timeMs = 0L,
                    easingToNext = EasingGraph.CUBIC_IN,
                    posX = 0f,
                    posY = 0.20f,
                    zoomPercent = zoomPct,
                    rotateDeg = 0f,
                ),
                KeyframeNode(
                    id = UUID.randomUUID().toString(),
                    timeMs = dur2,
                    easingToNext = EasingGraph.LINEAR,
                    posX = 0f,
                    posY = 0f,
                    zoomPercent = 100f,
                    rotateDeg = 0f,
                ),
            )
            val clip2 = temp2.copy(keyframes = clip2Kfs)
            return clip1 to clip2
        }

        fun isPlayheadInsideRange(playheadMs: Long, startMs: Long, endMs: Long): Boolean =
            playheadMs in startMs..endMs

        fun canSplitAtPlayhead(
            playheadMs: Long,
            startMs: Long,
            endMs: Long,
            minPartMs: Long = 200L,
        ): Boolean = playheadMs >= startMs + minPartMs && playheadMs <= endMs - minPartMs

        fun formatSplitTimeMs(ms: Long): String {
            val totalSec = (ms.coerceAtLeast(0L) / 1000L)
            return String.format(java.util.Locale.US, "%02d:%02d", totalSec / 60L, totalSec % 60L)
        }
    }
}
