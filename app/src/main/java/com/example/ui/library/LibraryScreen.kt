package com.example.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FfxPreset
import com.example.data.model.PresetCategory
import com.example.domain.video.UiState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.DeepVioletContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineStrong
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.StudioAmber
import java.util.Locale

@Composable
fun LibraryScreen(
    presetsState: UiState<List<FfxPreset>>,
    activePresetId: String,
    onImportFfxClick: () -> Unit,
    onApplyPreset: (FfxPreset) -> Unit,
    onToggleFavorite: (FfxPreset) -> Unit,
    onSavePreset: (FfxPreset, isNew: Boolean) -> Unit,
    onExportFfxFile: (FfxPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSeedStarterPack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var editingPreset by remember { mutableStateOf<Pair<FfxPreset, Boolean>?>(null) }
    var deletingPreset by remember { mutableStateOf<FfxPreset?>(null) }

    val filterOptions = remember {
        listOf("All", "Favorites") + PresetCategory.entries.map { it.label }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Header + Cloud status
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cloud .ffx Library",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val countText = when (presetsState) {
                        is UiState.Success -> "${presetsState.data.size} presets synced in cloud"
                        is UiState.Loading -> "Syncing cloud library..."
                        is UiState.Error -> "Sync interrupted"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportFfxClick,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("import_ffx_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileUpload,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.import_ffx),
                        color = CyberCyan,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Button(
                    onClick = {
                        editingPreset = FfxPreset(
                            name = "Custom VFX Look",
                            fileName = "custom_vfx_look.ffx",
                            category = PresetCategory.CUSTOM_FFX.label,
                            sourceType = "custom",
                            effectModules = listOf("ADBE Lumetri", "ADBE Glow2"),
                            contrast = 0.2,
                            saturation = 10.0,
                        ) to true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("new_preset_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search .ffx name, ADBE module, or tag...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search presets",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = OutlineSubtle,
                focusedContainerColor = ObsidianSurfaceContainer,
                unfocusedContainerColor = ObsidianSurfaceContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input"),
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filterOptions.forEach { category ->
                val selected = selectedFilter == category
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = category },
                    label = {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DeepVioletContainer,
                        selectedLabelColor = Color.White,
                        containerColor = ObsidianSurfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = OutlineSubtle,
                        selectedBorderColor = ElectricViolet,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (presetsState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = ElectricViolet)
                }
            }

            is UiState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    Text(
                        text = presetsState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            is UiState.Success -> {
                val filtered = remember(presetsState.data, searchQuery, selectedFilter) {
                    presetsState.data.filter { preset ->
                        val matchesCategory = when (selectedFilter) {
                            "All" -> true
                            "Favorites" -> preset.isFavorite
                            else -> preset.category == selectedFilter
                        }
                        val q = searchQuery.trim().lowercase(Locale.US)
                        val matchesQuery = q.isEmpty() ||
                            preset.name.lowercase(Locale.US).contains(q) ||
                            preset.fileName.lowercase(Locale.US).contains(q) ||
                            preset.effectModules.any { it.lowercase(Locale.US).contains(q) } ||
                            preset.tags.any { it.lowercase(Locale.US).contains(q) }
                        matchesCategory && matchesQuery
                    }
                }

                if (filtered.isEmpty()) {
                    EmptyLibraryCard(
                        hasAnyPresets = presetsState.data.isNotEmpty(),
                        onImportFfxClick = onImportFfxClick,
                        onSeedStarterPack = onSeedStarterPack,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filtered, key = { it.id }) { preset ->
                            FfxPresetCard(
                                preset = preset,
                                isActive = preset.id == activePresetId,
                                onApply = { onApplyPreset(preset) },
                                onToggleFavorite = { onToggleFavorite(preset) },
                                onEdit = { editingPreset = preset to false },
                                onExportFfx = { onExportFfxFile(preset) },
                                onDelete = { deletingPreset = preset },
                            )
                        }
                    }
                }
            }
        }
    }

    editingPreset?.let { (preset, isNew) ->
        PresetEditorDialog(
            initial = preset,
            isNew = isNew,
            onDismiss = { editingPreset = null },
            onSave = { updated ->
                onSavePreset(updated, isNew)
                editingPreset = null
            },
        )
    }

    deletingPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletingPreset = null },
            containerColor = ObsidianSurfaceHigh,
            title = {
                Text("Delete .ffx Preset?", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    "Remove \"${preset.name}\" (${preset.fileName}) from your cloud library?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePreset(preset.id)
                        deletingPreset = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPreset = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FfxPresetCard(
    preset: FfxPreset,
    isActive: Boolean,
    onApply: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onExportFfx: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor = if (isActive) CyberCyan else OutlineSubtle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ObsidianSurfaceContainer)
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onApply)
            .padding(16.dp)
            .testTag("preset_card_${preset.id}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) DeepCyanContainer else DeepVioletContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    tint = if (isActive) CyberCyan else ElectricViolet,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = DeepCyanContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = preset.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = if (preset.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (preset.isFavorite) "Remove favorite" else "Favorite preset",
                    tint = if (preset.isFavorite) StudioAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (preset.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ADBE Module chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                color = ObsidianSurfaceHigh,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = preset.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            preset.effectModules.take(3).forEach { module ->
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, OutlineSubtle, RoundedCornerShape(8.dp)),
                ) {
                    Text(
                        text = module,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (preset.effectModules.size > 3) {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "+${preset.effectModules.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Button(
                onClick = onApply,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) CyberCyan else ElectricViolet,
                    contentColor = if (isActive) ObsidianBackground else Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isActive) "Open in Studio" else "Apply Preset",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Inspect and edit preset",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onExportFfx,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download .ffx file",
                        tint = CyberCyan,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete preset",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    hasAnyPresets: Boolean,
    onImportFfxClick: () -> Unit,
    onSeedStarterPack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ObsidianSurfaceContainer)
            .border(1.dp, OutlineSubtle, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DeepVioletContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MovieFilter,
                contentDescription = null,
                tint = ElectricViolet,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (hasAnyPresets) "No matching .ffx presets" else "Your Cloud .ffx Library is Empty",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasAnyPresets) {
                "Try clearing your search filter or switching preset categories."
            } else {
                "Import an Adobe After Effects .ffx file from your device or load our 6 studio .ffx presets."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasAnyPresets) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onImportFfxClick) {
                    Text("Import .ffx")
                }
                Button(
                    onClick = onSeedStarterPack,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                ) {
                    Text("Load Starter Pack")
                }
            }
        }
    }
}

@Composable
private fun PresetEditorDialog(
    initial: FfxPreset,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (FfxPreset) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var fileName by remember { mutableStateOf(initial.fileName) }
    var description by remember { mutableStateOf(initial.description) }
    var category by remember { mutableStateOf(initial.category) }
    var tagsText by remember { mutableStateOf(initial.tags.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceHigh,
        title = {
            Text(
                text = if (isNew) "Create Cloud .ffx Preset" else "Inspect & Edit .ffx Preset",
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(".ffx File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PresetCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat.label,
                            onClick = { category = cat.label },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial.rawHeaderSnippet.isNotBlank()) {
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, OutlineStrong, RoundedCornerShape(10.dp)),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "PARSED FFX METADATA (${initial.ffxVersion})",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = initial.rawHeaderSnippet,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ensuredFile = fileName.trim().let {
                        if (it.isEmpty()) "preset.ffx" else if (it.endsWith(".ffx", ignoreCase = true)) it else "$it.ffx"
                    }
                    val parsedTags = tagsText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .take(10)
                    onSave(
                        initial.copy(
                            name = name.trim().ifBlank { "Untitled Preset" },
                            fileName = ensuredFile,
                            category = category,
                            description = description.trim(),
                            tags = parsedTags,
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
            ) {
                Text(if (isNew) "Create & Sync" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
