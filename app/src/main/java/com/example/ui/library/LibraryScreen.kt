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
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FfxPreset
import com.example.data.model.PresetCategory
import com.example.domain.video.UiState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepAmberContainer
import com.example.ui.theme.DeepVioletContainer
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LunaMint
import com.example.ui.theme.LunaMintContainer
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
    var inspectingPreset by remember { mutableStateOf<Pair<FfxPreset, Boolean>?>(null) }
    var deletingPreset by remember { mutableStateOf<FfxPreset?>(null) }

    val filterOptions = remember {
        listOf("All", "3D LUT", "FFX", "Favorites") + PresetCategory.entries.map { it.label }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LUNA Preset Vault",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CollectionsBookmark,
                        contentDescription = null,
                        tint = LunaMint,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val countText = when (presetsState) {
                        is UiState.Success -> "${presetsState.data.size} presets (.ffx & .cube LUT)"
                        is UiState.Loading -> "Loading preset vault..."
                        is UiState.Error -> "Could not load presets"
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, LunaMint.copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("import_ffx_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileUpload,
                        contentDescription = null,
                        tint = LunaMint,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.import_ffx),
                        color = LunaMint,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search .ffx, .cube LUT, ADBE module, or tag...",
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
                focusedBorderColor = LunaMint,
                unfocusedBorderColor = OutlineSubtle,
                focusedContainerColor = ObsidianSurfaceContainer,
                unfocusedContainerColor = ObsidianSurfaceContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input"),
        )

        Spacer(modifier = Modifier.height(10.dp))

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
                    label = { Text(category, style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LunaMintContainer,
                        selectedLabelColor = LunaMint,
                        containerColor = ObsidianSurfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = OutlineSubtle,
                        selectedBorderColor = LunaMint,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (presetsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LunaMint)
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
                            "3D LUT" -> preset.presetFormat == "CUBE_LUT" || preset.lutSize >= 2
                            "FFX" -> preset.presetFormat != "CUBE_LUT"
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
                    EmptyVaultCard(
                        hasAny = presetsState.data.isNotEmpty(),
                        onImportClick = onImportFfxClick,
                        onSeedStarterPack = onSeedStarterPack,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filtered, key = { it.id }) { preset ->
                            VaultPresetCard(
                                preset = preset,
                                isActive = preset.id == activePresetId,
                                onApply = { onApplyPreset(preset) },
                                onToggleFavorite = { onToggleFavorite(preset) },
                                onInspect = { inspectingPreset = preset to false },
                                onExportFfx = { onExportFfxFile(preset) },
                                onDelete = { deletingPreset = preset },
                            )
                        }
                    }
                }
            }
        }
    }

    inspectingPreset?.let { (preset, isNew) ->
        PresetMetadataDialog(
            initial = preset,
            isNew = isNew,
            onDismiss = { inspectingPreset = null },
            onSave = { updated ->
                onSavePreset(updated, isNew)
                inspectingPreset = null
            },
        )
    }

    deletingPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletingPreset = null },
            containerColor = ObsidianSurfaceHigh,
            title = { Text("Delete Preset?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Remove \"${preset.name}\" (${preset.fileName}) from LUNA Vault?",
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
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingPreset = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaultPresetCard(
    preset: FfxPreset,
    isActive: Boolean,
    onApply: () -> Unit,
    onToggleFavorite: () -> Unit,
    onInspect: () -> Unit,
    onExportFfx: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor = if (isActive) LunaMint else OutlineSubtle

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
                    .background(if (isActive) LunaMintContainer else DeepVioletContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    tint = if (isActive) LunaMint else ElectricViolet,
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (preset.presetFormat == "CUBE_LUT") LunaMintContainer else DeepVioletContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = if (preset.presetFormat == "CUBE_LUT") "3D LUT" else "FFX",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (preset.presetFormat == "CUBE_LUT") LunaMint else CyberCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
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
                    contentDescription = if (preset.isFavorite) "Unfavorite" else "Favorite",
                    tint = if (preset.isFavorite) StudioAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (preset.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (preset.unsupportedModules.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = DeepAmberContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Desktop plugin(s): ${preset.unsupportedModules.first()} — export as .cube LUT for exact match",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 2,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
            if (preset.lutSize >= 2) {
                Surface(color = LunaMintContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "${preset.lutSize}³ Cube",
                        style = MaterialTheme.typography.labelSmall,
                        color = LunaMint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Button(
                onClick = onApply,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LunaMint,
                    contentColor = ObsidianBackground,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isActive) "Open in Editor" else "Apply to Timeline", style = MaterialTheme.typography.labelLarge)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onInspect) {
                    Icon(Icons.Filled.Info, contentDescription = "Inspect preset metadata", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onExportFfx) {
                    Icon(Icons.Filled.Download, contentDescription = "Export preset descriptor", tint = CyberCyan)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete preset", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun EmptyVaultCard(
    hasAny: Boolean,
    onImportClick: () -> Unit,
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
                .background(LunaMintContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MovieFilter, contentDescription = null, tint = LunaMint, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (hasAny) "No matching presets" else "Your LUNA Vault is Empty",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasAny) "Clear your search filter or switch preset tabs." else "Import an Adobe After Effects .ffx file or 3D .cube LUT.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasAny) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onImportClick) { Text("Import .ffx / .cube") }
                Button(
                    onClick = onSeedStarterPack,
                    colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
                ) { Text("Load Starter Pack") }
            }
        }
    }
}

@Composable
private fun PresetMetadataDialog(
    initial: FfxPreset,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (FfxPreset) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var category by remember { mutableStateOf(initial.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceHigh,
        title = {
            Text(if (isNew) "New LUNA Preset" else "Preset Inspector (Read-Only FX)", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Notes") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetCategory.entries.forEach { cat ->
                        FilterChip(selected = category == cat.label, onClick = { category = cat.label }, label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                if (initial.rawHeaderSnippet.isNotBlank()) {
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, OutlineStrong, RoundedCornerShape(10.dp)),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("DECODED STREAM (${initial.ffxVersion})", style = MaterialTheme.typography.labelSmall, color = LunaMint)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(initial.rawHeaderSnippet, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(initial.copy(name = name.trim().ifBlank { initial.name }, category = category, description = description.trim())) },
                colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
            ) { Text("Save Metadata") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
