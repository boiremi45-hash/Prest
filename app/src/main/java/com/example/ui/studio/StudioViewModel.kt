package com.example.ui.studio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.data.model.AspectPreset
import com.example.data.model.BlendStyle
import com.example.data.model.CanvasFitMode
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.model.LayerKind
import com.example.data.model.LayerShapeType
import com.example.data.model.LunaProjectSummary
import com.example.data.model.TimelineLayer
import com.example.data.repository.PresetRepository
import com.example.domain.ffx.FfxParser
import com.example.domain.video.ExportConfig
import com.example.domain.video.ExportUiState
import com.example.domain.video.UiState
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    val exportsState: StateFlow<UiState<List<ExportRecord>>> = repository.observeExports()
        .map<List<ExportRecord>, UiState<List<ExportRecord>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing exports", error)
            emit(UiState.Error(error.message ?: "Failed to load export history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    private val _selectedVideo = MutableStateFlow<VideoMetadata?>(null)
    val selectedVideo: StateFlow<VideoMetadata?> = _selectedVideo.asStateFlow()

    private val starterPreset = PresetRepository.starterFfxPresets().first()
    private val _activePreset = MutableStateFlow(starterPreset)
    val activePreset: StateFlow<FfxPreset> = _activePreset.asStateFlow()

    private val _bypassEffects = MutableStateFlow(false)
    val bypassEffects: StateFlow<Boolean> = _bypassEffects.asStateFlow()

    private val _projectTitle = MutableStateFlow("LUNA Project 1")
    val projectTitle: StateFlow<String> = _projectTitle.asStateFlow()

    private val _aspectPreset = MutableStateFlow(AspectPreset.RATIO_16_9)
    val aspectPreset: StateFlow<AspectPreset> = _aspectPreset.asStateFlow()

    private val _projectResolution = MutableStateFlow("1080p")
    val projectResolution: StateFlow<String> = _projectResolution.asStateFlow()

    private val _projectFps = MutableStateFlow(60)
    val projectFps: StateFlow<Int> = _projectFps.asStateFlow()

    private val _canvasFitMode = MutableStateFlow(CanvasFitMode.FIT)
    val canvasFitMode: StateFlow<CanvasFitMode> = _canvasFitMode.asStateFlow()

    private val _layers = MutableStateFlow(defaultLayers(starterPreset, null, 15_000L))
    val layers: StateFlow<List<TimelineLayer>> = _layers.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId: StateFlow<String?> = _selectedLayerId.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val undoStack = ArrayDeque<List<TimelineLayer>>()
    private val redoStack = ArrayDeque<List<TimelineLayer>>()
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
            val result = repository.seedStarterPackIfEmpty()
            result.onSuccess { added ->
                if (added > 0) {
                    _statusMessage.value = "Loaded $added LUNA studio presets."
                }
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun createNewProject(
        title: String,
        aspect: AspectPreset,
        resolutionLabel: String,
        fps: Int,
        preset: FfxPreset = _activePreset.value,
    ) {
        val cleanTitle = title.trim().ifBlank { "LUNA Project ${_recentProjects.value.size + 1}" }
        _projectTitle.value = cleanTitle
        _aspectPreset.value = aspect
        _projectResolution.value = resolutionLabel
        _projectFps.value = fps
        _activePreset.value = preset
        _bypassEffects.value = false
        val durationMs = _selectedVideo.value?.durationMs?.takeIf { it > 0L } ?: 15_000L
        undoStack.clear()
        redoStack.clear()
        syncUndoRedoFlags()
        _layers.value = defaultLayers(preset, _selectedVideo.value, durationMs)
        _selectedLayerId.value = null
        _playheadMs.value = 0L
        upsertCurrentProjectSummary(durationMs)
    }

    fun openProjectSummary(project: LunaProjectSummary, presets: List<FfxPreset>) {
        _projectTitle.value = project.title
        _aspectPreset.value = project.aspectPreset
        _projectResolution.value = project.resolutionLabel
        _projectFps.value = project.fps
        val matched = presets.firstOrNull { it.name == project.activePresetName } ?: _activePreset.value
        applyPreset(matched)
    }

    fun updateProjectSettings(
        title: String,
        aspect: AspectPreset,
        resolutionLabel: String,
        fps: Int,
    ) {
        _projectTitle.value = title.trim().ifBlank { _projectTitle.value }
        _aspectPreset.value = aspect
        _projectResolution.value = resolutionLabel
        _projectFps.value = fps
        upsertCurrentProjectSummary(currentClipDurationMs())
    }

    fun cycleCanvasFitMode() {
        val entries = CanvasFitMode.entries
        val next = entries[(entries.indexOf(_canvasFitMode.value) + 1) % entries.size]
        _canvasFitMode.value = next
        _statusMessage.value = "Canvas framing: ${next.label}"
    }

    fun selectVideo(context: Context, uri: Uri) {
        viewModelScope.launch {
            val metadata = VideoExportManager.readVideoMetadata(context, uri)
            _selectedVideo.value = metadata
            _exportState.value = ExportUiState.Idle
            val dur = metadata.durationMs.takeIf { it > 500L } ?: 15_000L
            mutateLayers { current ->
                val withoutOldVideo = current.filterNot { it.kind == LayerKind.VIDEO }
                val newVideo = TimelineLayer(
                    id = "video_primary",
                    title = metadata.displayName,
                    kind = LayerKind.VIDEO,
                    startMs = 0L,
                    endMs = dur,
                    isVisible = true,
                    accentColorHex = 0xFF353A54L,
                )
                val syncedOthers = withoutOldVideo.map { layer ->
                    if (layer.kind == LayerKind.PRESET_FX && layer.endMs > dur) {
                        layer.copy(endMs = dur)
                    } else if (layer.kind == LayerKind.PRESET_FX && layer.startMs == 0L && layer.endMs == 15_000L) {
                        layer.copy(endMs = dur)
                    } else {
                        layer
                    }
                }
                syncedOthers + newVideo
            }
            _playheadMs.value = 0L
            upsertCurrentProjectSummary(dur)
        }
    }

    fun applyPreset(preset: FfxPreset) {
        _activePreset.value = preset
        _bypassEffects.value = false
        val dur = currentClipDurationMs()
        mutateLayers { current ->
            val fxIdx = current.indexOfFirst { it.kind == LayerKind.PRESET_FX }
            if (fxIdx >= 0) {
                current.toMutableList().apply {
                    val existing = this[fxIdx]
                    this[fxIdx] = existing.copy(
                        title = preset.name,
                        preset = preset,
                        isVisible = true,
                        accentColorHex = if (preset.presetFormat == "CUBE_LUT") 0xFF45C6A2L else 0xFF4BB8C9L,
                    )
                }
            } else {
                listOf(
                    TimelineLayer(
                        title = preset.name,
                        kind = LayerKind.PRESET_FX,
                        startMs = 0L,
                        endMs = dur,
                        isVisible = true,
                        preset = preset,
                        accentColorHex = if (preset.presetFormat == "CUBE_LUT") 0xFF45C6A2L else 0xFF4BB8C9L,
                    )
                ) + current
            }
        }
        upsertCurrentProjectSummary(dur)
    }

    fun addPresetLayer(preset: FfxPreset) {
        _activePreset.value = preset
        _bypassEffects.value = false
        val dur = currentClipDurationMs()
        val newLayer = TimelineLayer(
            title = preset.name,
            kind = LayerKind.PRESET_FX,
            startMs = 0L,
            endMs = dur,
            isVisible = true,
            preset = preset,
            accentColorHex = if (preset.presetFormat == "CUBE_LUT") 0xFF45C6A2L else 0xFF4BB8C9L,
        )
        mutateLayers { listOf(newLayer) + it }
        _selectedLayerId.value = newLayer.id
        _statusMessage.value = "Added \"${preset.name}\" FX layer to timeline."
    }

    fun addShapeLayer(shapeType: LayerShapeType, colorHex: Long = 0xFF4BB8C9L) {
        val dur = currentClipDurationMs()
        val newLayer = TimelineLayer(
            title = shapeType.label,
            kind = LayerKind.SHAPE,
            startMs = 0L,
            endMs = dur,
            isVisible = true,
            shapeType = shapeType,
            accentColorHex = colorHex,
        )
        mutateLayers { listOf(newLayer) + it }
        _selectedLayerId.value = newLayer.id
        _statusMessage.value = "Added ${shapeType.label} layer."
    }

    fun addTextLayer(text: String, colorHex: Long = 0xFFF4F5FAL) {
        val clean = text.trim().ifBlank { "LUNA" }
        val dur = currentClipDurationMs()
        val newLayer = TimelineLayer(
            title = "Text: $clean",
            kind = LayerKind.TEXT,
            startMs = 0L,
            endMs = dur,
            isVisible = true,
            textContent = clean,
            accentColorHex = colorHex,
        )
        mutateLayers { listOf(newLayer) + it }
        _selectedLayerId.value = newLayer.id
        _statusMessage.value = "Added text layer \"$clean\"."
    }

    fun toggleBypass() {
        _bypassEffects.value = !_bypassEffects.value
    }

    fun selectLayer(layerId: String?) {
        _selectedLayerId.value = if (_selectedLayerId.value == layerId) null else layerId
    }

    fun toggleLayerVisibility(layerId: String) {
        mutateLayers { list ->
            list.map { if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it }
        }
    }

    fun updatePlayhead(ms: Long) {
        _playheadMs.value = ms.coerceIn(0L, currentClipDurationMs())
    }

    fun duplicateSelectedLayer() {
        val targetId = _selectedLayerId.value ?: _layers.value.firstOrNull { it.kind != LayerKind.VIDEO }?.id
        val source = _layers.value.firstOrNull { it.id == targetId } ?: return
        if (source.kind == LayerKind.VIDEO) {
            _statusMessage.value = "Duplicate an FX, Shape, or Text layer."
            return
        }
        val clone = source.copy(
            id = UUID.randomUUID().toString(),
            title = "${source.title} Copy",
        )
        mutateLayers { listOf(clone) + it }
        _selectedLayerId.value = clone.id
    }

    fun deleteSelectedLayer() {
        val id = _selectedLayerId.value ?: return
        val target = _layers.value.firstOrNull { it.id == id } ?: return
        if (target.kind == LayerKind.VIDEO) {
            _statusMessage.value = "Base video layer cannot be deleted."
            return
        }
        mutateLayers { it.filterNot { layer -> layer.id == id } }
        _selectedLayerId.value = null
    }

    fun trimLeftAtPlayhead() {
        val id = _selectedLayerId.value ?: return
        val playhead = _playheadMs.value
        mutateLayers { list ->
            list.map { layer ->
                if (layer.id == id && playhead < layer.endMs - 300L) {
                    layer.copy(startMs = playhead.coerceAtLeast(0L))
                } else {
                    layer
                }
            }
        }
    }

    fun trimRightAtPlayhead() {
        val id = _selectedLayerId.value ?: return
        val playhead = _playheadMs.value
        mutateLayers { list ->
            list.map { layer ->
                if (layer.id == id && playhead > layer.startMs + 300L) {
                    layer.copy(endMs = playhead.coerceAtMost(currentClipDurationMs()))
                } else {
                    layer
                }
            }
        }
    }

    fun splitSelectedLayerAtPlayhead() {
        val id = _selectedLayerId.value ?: return
        val target = _layers.value.firstOrNull { it.id == id } ?: return
        val splitPoint = _playheadMs.value
        if (splitPoint <= target.startMs + 350L || splitPoint >= target.endMs - 350L) {
            _statusMessage.value = "Move playhead inside the layer bar to split."
            return
        }
        if (target.kind == LayerKind.VIDEO) {
            _statusMessage.value = "Select an FX, Shape, or Text layer to split across segments."
            return
        }
        val firstHalf = target.copy(endMs = splitPoint)
        val secondHalf = target.copy(
            id = UUID.randomUUID().toString(),
            title = "${target.title} (2)",
            startMs = splitPoint,
        )
        mutateLayers { list ->
            list.flatMap { layer ->
                if (layer.id == id) listOf(secondHalf, firstHalf) else listOf(layer)
            }
        }
        _selectedLayerId.value = secondHalf.id
    }

    fun setSelectedLayerSpeed(speed: Float) {
        val id = _selectedLayerId.value ?: return
        mutateLayers { list ->
            list.map { if (it.id == id) it.copy(speedMultiplier = speed.coerceIn(0.25f, 4.0f)) else it }
        }
    }

    fun toggleSelectedLayerMute() {
        val id = _selectedLayerId.value ?: return
        mutateLayers { list ->
            list.map { if (it.id == id) it.copy(isMuted = !it.isMuted) else it }
        }
    }

    fun setSelectedLayerColor(colorHex: Long) {
        val id = _selectedLayerId.value ?: return
        mutateLayers { list ->
            list.map { if (it.id == id) it.copy(accentColorHex = colorHex) else it }
        }
    }

    fun toggleSelectedLayerBorderShadow() {
        val id = _selectedLayerId.value ?: return
        mutateLayers { list ->
            list.map { if (it.id == id) it.copy(hasBorderShadow = !it.hasBorderShadow) else it }
        }
    }

    fun setSelectedLayerBlendAndOpacity(blendStyle: BlendStyle, opacity: Float) {
        val id = _selectedLayerId.value ?: return
        mutateLayers { list ->
            list.map {
                if (it.id == id) {
                    it.copy(blendStyle = blendStyle, opacity = opacity.coerceIn(0.1f, 1.0f))
                } else {
                    it
                }
            }
        }
    }

    fun updateSelectedLayerTransform(rotationDegrees: Int, flipHorizontal: Boolean) {
        val id = _selectedLayerId.value ?: return
        mutateLayers { list ->
            list.map {
                if (it.id == id) {
                    it.copy(rotationStepDegrees = rotationDegrees, flipHorizontal = flipHorizontal)
                } else {
                    it
                }
            }
        }
    }

    fun updateSelectedLayerShapeOrText(shapeType: LayerShapeType? = null, textContent: String? = null) {
        val id = _selectedLayerId.value ?: return
        mutateLayers { list ->
            list.map { layer ->
                if (layer.id != id) {
                    layer
                } else {
                    layer.copy(
                        shapeType = shapeType ?: layer.shapeType,
                        textContent = textContent ?: layer.textContent,
                        title = when {
                            shapeType != null -> shapeType.label
                            textContent != null -> "Text: ${textContent.ifBlank { "LUNA" }}"
                            else -> layer.title
                        },
                    )
                }
            }
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        redoStack.addLast(_layers.value)
        _layers.value = previous
        syncUndoRedoFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(_layers.value)
        _layers.value = next
        syncUndoRedoFlags()
    }

    fun saveOrCreatePreset(preset: FfxPreset, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew || preset.id.isBlank()) {
                repository.createPreset(preset)
                    .onSuccess { newId ->
                        val created = preset.copy(id = newId)
                        _statusMessage.value = "Saved \"${preset.name}\" to LUNA library."
                        applyPreset(created)
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Failed to create preset."
                    }
            } else {
                repository.updatePreset(preset)
                    .onSuccess {
                        if (_activePreset.value.id == preset.id) {
                            applyPreset(preset)
                        }
                        _statusMessage.value = "Updated \"${preset.name}\"."
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Failed to update preset."
                    }
            }
        }
    }

    fun toggleFavorite(preset: FfxPreset) {
        if (preset.id.isBlank()) return
        viewModelScope.launch {
            val nextFav = !preset.isFavorite
            repository.toggleFavorite(preset.id, nextFav)
            if (_activePreset.value.id == preset.id) {
                _activePreset.value = _activePreset.value.copy(isFavorite = nextFav)
            }
        }
    }

    fun deletePreset(presetId: String) {
        if (presetId.isBlank()) return
        viewModelScope.launch {
            repository.deletePreset(presetId)
                .onSuccess { _statusMessage.value = "Preset deleted from library." }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Delete failed." }
        }
    }

    fun importFfxFromUri(context: Context, uri: Uri, onImportedAndApplied: () -> Unit = {}) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val name = VideoExportManager.queryDisplayName(context, uri) ?: "imported_preset.ffx"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                if (bytes.isEmpty()) null else FfxParser.parseFromBytes(name, bytes)
            }
            if (parsed == null) {
                _statusMessage.value = "Could not read selected .ffx or .cube file."
                return@launch
            }
            repository.createPreset(parsed)
                .onSuccess { newId ->
                    val saved = parsed.copy(id = newId)
                    applyPreset(saved)
                    val warnSuffix = if (saved.unsupportedModules.isNotEmpty()) {
                        " (${saved.unsupportedModules.size} desktop-only plugin alert)"
                    } else {
                        ""
                    }
                    _statusMessage.value = "Imported ${saved.fileName}$warnSuffix"
                    onImportedAndApplied()
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to save imported preset."
                }
        }
    }

    fun writeFfxDescriptorToUri(context: Context, preset: FfxPreset, destUri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = FfxParser.serializeToFfxDescriptor(preset)
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output file")
                }
            }
            result.onSuccess {
                _statusMessage.value = "Saved ${preset.fileName} to local drive."
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to export .ffx file."
            }
        }
    }

    fun startHighQualityExport(config: ExportConfig) {
        val video = _selectedVideo.value
        if (video == null) {
            _exportState.value = ExportUiState.Error("Select a video clip first before exporting.")
            return
        }
        val preset = _activePreset.value
        val activeLayers = _layers.value
        val clipDuration = currentClipDurationMs()
        _exportState.value = ExportUiState.Rendering(0, "Initializing Media3 GPU pipeline...")

        exportManager.startExport(
            sourceUri = video.uri,
            clipDurationMs = clipDuration,
            layers = activeLayers,
            activePreset = preset,
            config = config,
            onProgress = { pct ->
                _exportState.value = ExportUiState.Rendering(
                    progressPercent = pct,
                    stageLabel = "Rendering ${config.resolution.label} (${config.codec.label})...",
                )
            },
            onSuccess = { record, locationLabel ->
                _exportState.value = ExportUiState.Success(record, locationLabel)
                viewModelScope.launch {
                    repository.recordExport(record)
                    if (preset.id.isNotBlank()) {
                        repository.incrementUsage(preset.id)
                    }
                }
            },
            onError = { message ->
                _exportState.value = ExportUiState.Error(message)
            },
        )
    }

    fun cancelExport() {
        exportManager.cancelExport()
        _exportState.value = ExportUiState.Idle
    }

    fun resetExportState() {
        _exportState.value = ExportUiState.Idle
    }

    fun copyExportToDestination(context: Context, sourceUriString: String, destUri: Uri) {
        viewModelScope.launch {
            val sourceUri = runCatching { Uri.parse(sourceUriString) }.getOrNull() ?: return@launch
            VideoExportManager.copyUriToDestination(context, sourceUri, destUri)
                .onSuccess { _statusMessage.value = "Saved rendered MP4 copy to selected folder." }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not save copy." }
        }
    }

    fun deleteExportRecord(exportId: String) {
        if (exportId.isBlank()) return
        viewModelScope.launch { repository.deleteExportRecord(exportId) }
    }

    fun currentClipDurationMs(): Long {
        return _selectedVideo.value?.durationMs?.takeIf { it > 0L } ?: 15_000L
    }

    private fun mutateLayers(transform: (List<TimelineLayer>) -> List<TimelineLayer>) {
        val current = _layers.value
        val updated = transform(current)
        if (updated != current) {
            undoStack.addLast(current)
            if (undoStack.size > 30) undoStack.removeFirst()
            redoStack.clear()
            _layers.value = updated
            syncUndoRedoFlags()
        }
    }

    private fun syncUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun upsertCurrentProjectSummary(durationMs: Long) {
        val currentTitle = _projectTitle.value
        val updated = LunaProjectSummary(
            id = currentTitle.lowercase().replace(' ', '_'),
            title = currentTitle,
            aspectPreset = _aspectPreset.value,
            resolutionLabel = _projectResolution.value,
            fps = _projectFps.value,
            durationMs = durationMs,
            activePresetName = _activePreset.value.name,
            layerCount = _layers.value.size,
            videoDisplayName = _selectedVideo.value?.displayName,
        )
        val remaining = _recentProjects.value.filterNot { it.title == currentTitle }
        _recentProjects.value = listOf(updated) + remaining
    }

    private companion object {
        const val TAG = "StudioViewModel"

        fun defaultLayers(
            preset: FfxPreset,
            video: VideoMetadata?,
            durationMs: Long,
        ): List<TimelineLayer> = listOf(
            TimelineLayer(
                id = "fx_primary",
                title = preset.name,
                kind = LayerKind.PRESET_FX,
                startMs = 0L,
                endMs = durationMs,
                isVisible = true,
                preset = preset,
                accentColorHex = 0xFF4BB8C9L,
            ),
            TimelineLayer(
                id = "video_primary",
                title = video?.displayName ?: "Tap to import clip",
                kind = LayerKind.VIDEO,
                startMs = 0L,
                endMs = durationMs,
                isVisible = true,
                accentColorHex = 0xFF353A54L,
            ),
        )

        fun initialProjectsList(starterPreset: FfxPreset): List<LunaProjectSummary> = listOf(
            LunaProjectSummary(
                id = "proj_1",
                title = "LUNA Project 1",
                aspectPreset = AspectPreset.RATIO_16_9,
                resolutionLabel = "1080p",
                fps = 60,
                durationMs = 15_000L,
                activePresetName = starterPreset.name,
                layerCount = 2,
            ),
            LunaProjectSummary(
                id = "proj_2",
                title = "Night Cinema CC",
                aspectPreset = AspectPreset.RATIO_1_1,
                resolutionLabel = "1440p",
                fps = 60,
                durationMs = 17_000L,
                activePresetName = "Cyber Neon Split-Tone",
                layerCount = 3,
            ),
            LunaProjectSummary(
                id = "proj_3",
                title = "35mm Film Look",
                aspectPreset = AspectPreset.RATIO_4_3,
                resolutionLabel = "1080p",
                fps = 30,
                durationMs = 24_000L,
                activePresetName = "Kodak 2383 Film Grain",
                layerCount = 2,
            ),
        )
    }
}
