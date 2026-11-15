package com.example.ui.studio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.repository.PresetRepository
import com.example.domain.ffx.FfxParser
import com.example.domain.video.ExportConfig
import com.example.domain.video.ExportUiState
import com.example.domain.video.UiState
import com.example.domain.video.VideoExportManager
import com.example.domain.video.VideoMetadata
import com.google.firebase.firestore.FirebaseFirestoreException
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

@UnstableApi
class StudioViewModel(
    private val repository: PresetRepository,
    private val currentUserId: String,
    private val exportManager: VideoExportManager,
) : ViewModel() {

    val presetsState: StateFlow<UiState<List<FfxPreset>>> = repository.observePresets(currentUserId)
        .map<List<FfxPreset>, UiState<List<FfxPreset>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing presets", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load cloud .ffx library"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    val exportsState: StateFlow<UiState<List<ExportRecord>>> = repository.observeExports(currentUserId)
        .map<List<ExportRecord>, UiState<List<ExportRecord>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing exports", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load export history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    private val _selectedVideo = MutableStateFlow<VideoMetadata?>(null)
    val selectedVideo: StateFlow<VideoMetadata?> = _selectedVideo.asStateFlow()

    private val _activePreset = MutableStateFlow(PresetRepository.starterFfxPresets().first())
    val activePreset: StateFlow<FfxPreset> = _activePreset.asStateFlow()

    private val _bypassEffects = MutableStateFlow(false)
    val bypassEffects: StateFlow<Boolean> = _bypassEffects.asStateFlow()

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
                    _statusMessage.value = "Synced $added starter .ffx presets to your cloud library."
                }
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun selectVideo(context: Context, uri: Uri) {
        viewModelScope.launch {
            val metadata = VideoExportManager.readVideoMetadata(context, uri)
            _selectedVideo.value = metadata
            _exportState.value = ExportUiState.Idle
        }
    }

    fun applyPreset(preset: FfxPreset) {
        _activePreset.value = preset
        _bypassEffects.value = false
    }

    fun toggleBypass() {
        _bypassEffects.value = !_bypassEffects.value
    }

    fun updateWorkingPreset(transform: (FfxPreset) -> FfxPreset) {
        _activePreset.value = transform(_activePreset.value).clamped()
    }

    fun resetWorkingPreset() {
        _activePreset.value = FfxPreset(
            id = _activePreset.value.id,
            userId = _activePreset.value.userId,
            name = _activePreset.value.name,
            fileName = _activePreset.value.fileName,
            category = _activePreset.value.category,
            description = _activePreset.value.description,
            sourceType = _activePreset.value.sourceType,
            ffxVersion = _activePreset.value.ffxVersion,
            effectModules = _activePreset.value.effectModules,
            tags = _activePreset.value.tags,
            isFavorite = _activePreset.value.isFavorite,
            rawHeaderSnippet = _activePreset.value.rawHeaderSnippet,
            usageCount = _activePreset.value.usageCount,
            createdAt = _activePreset.value.createdAt,
        )
    }

    fun saveWorkingPresetToCloud(asNew: Boolean, overrideName: String? = null) {
        viewModelScope.launch {
            val current = _activePreset.value
            val targetName = overrideName?.trim()?.takeIf { it.isNotEmpty() } ?: current.name
            val targetFile = if (asNew && overrideName != null) {
                targetName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") + ".ffx"
            } else {
                current.fileName
            }

            if (asNew || current.id.isBlank()) {
                val created = current.copy(
                    id = "",
                    name = targetName,
                    fileName = targetFile,
                    sourceType = if (current.sourceType == "imported_ffx") "imported_ffx" else "custom",
                    usageCount = 0L,
                    createdAt = null,
                    updatedAt = null,
                )
                repository.createPreset(created)
                    .onSuccess { newId ->
                        _activePreset.value = created.copy(id = newId, userId = currentUserId)
                        _statusMessage.value = "Saved \"$targetName\" to your cloud library."
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Failed to save preset."
                    }
            } else {
                val updated = current.copy(name = targetName, fileName = targetFile)
                repository.updatePreset(updated)
                    .onSuccess {
                        _activePreset.value = updated
                        _statusMessage.value = "Updated \"$targetName\" in cloud."
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Failed to update preset."
                    }
            }
        }
    }

    fun saveOrCreatePreset(preset: FfxPreset, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew || preset.id.isBlank()) {
                repository.createPreset(preset)
                    .onSuccess { newId ->
                        _statusMessage.value = "Created \"${preset.name}\" in cloud library."
                        if (_activePreset.value.id.isBlank()) {
                            _activePreset.value = preset.copy(id = newId, userId = currentUserId)
                        }
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Failed to create preset."
                    }
            } else {
                repository.updatePreset(preset)
                    .onSuccess {
                        if (_activePreset.value.id == preset.id) {
                            _activePreset.value = preset
                        }
                        _statusMessage.value = "Saved changes to \"${preset.name}\"."
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
                .onSuccess {
                    _statusMessage.value = "Preset removed from cloud library."
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to delete preset."
                }
        }
    }

    fun importFfxFromUri(context: Context, uri: Uri, onImportedAndApplied: () -> Unit = {}) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val name = VideoExportManager.queryDisplayName(context, uri) ?: "imported_preset.ffx"
                val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes()
                } ?: ByteArray(0)
                if (bytes.isEmpty()) {
                    null
                } else {
                    FfxParser.parseFromBytes(name, bytes)
                }
            }
            if (parsed == null) {
                _statusMessage.value = "Could not read selected .ffx file."
                return@launch
            }
            repository.createPreset(parsed)
                .onSuccess { newId ->
                    val saved = parsed.copy(id = newId, userId = currentUserId)
                    _activePreset.value = saved
                    _bypassEffects.value = false
                    _statusMessage.value = "Imported ${parsed.fileName} (${parsed.effectModules.size} AE modules) to Cloud!"
                    onImportedAndApplied()
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to upload .ffx to cloud."
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
        _exportState.value = ExportUiState.Rendering(0, "Initializing Media3 hardware encoder...")

        exportManager.startExport(
            sourceUri = video.uri,
            preset = preset,
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
                .onSuccess {
                    _statusMessage.value = "Saved rendered MP4 copy to selected folder."
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Could not save copy."
                }
        }
    }

    fun deleteExportRecord(exportId: String) {
        if (exportId.isBlank()) return
        viewModelScope.launch {
            repository.deleteExportRecord(exportId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        exportManager.cancelExport()
    }

    private companion object {
        const val TAG = "StudioViewModel"
    }
}
