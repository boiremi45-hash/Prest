package com.example.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AspectPreset
import com.example.data.model.FfxPreset
import com.example.data.model.LunaProjectSummary
import com.example.domain.video.UiState
import com.example.ui.theme.CyberCyan
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
fun HomeScreen(
    presetsState: UiState<List<FfxPreset>>,
    recentProjects: List<LunaProjectSummary>,
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit,
    onRequestCreateDialog: () -> Unit,
    onCreateProject: (String, AspectPreset, String, Int) -> Unit,
    onOpenProject: (LunaProjectSummary) -> Unit,
    onSelectTemplate: (FfxPreset) -> Unit,
    onNavigateLibrary: () -> Unit,
    onImportFfxClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = (presetsState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        // LUNA Top Brand Header (Screenshot 2)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onNavigateLibrary,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Browse Preset Library",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LUNA",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    LunaCrescentMark(modifier = Modifier.size(26.dp))
                }

                IconButton(
                    onClick = onImportFfxClick,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileUpload,
                        contentDescription = "Import .ffx or .cube file",
                        tint = LunaMint,
                    )
                }
            }
        }

        // Explore Templates Horizontal Avatar Carousel (Screenshot 2)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Explore Templates",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(onClick = onNavigateLibrary) {
                    Text(
                        text = "View all",
                        style = MaterialTheme.typography.labelLarge,
                        color = LunaMint,
                    )
                }
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(presets.take(8), key = { it.id.ifBlank { it.name } }) { preset ->
                    TemplateCircleItem(
                        preset = preset,
                        onClick = { onSelectTemplate(preset) },
                    )
                }
            }
        }

        // Latest Projects Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Latest projects",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(onClick = onImportFfxClick) {
                    Icon(
                        imageVector = Icons.Filled.CollectionsBookmark,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Import .ffx/.cube",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyberCyan,
                    )
                }
            }
        }

        // Dashed "+ Create New Project" Card (Screenshot 2)
        item {
            val dashColor = OutlineStrong
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianSurface)
                    .drawBehind {
                        drawRoundRect(
                            color = dashColor,
                            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                            style = Stroke(
                                width = 1.8.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
                            ),
                        )
                    }
                    .clickable(onClick = onRequestCreateDialog)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .testTag("create_new_project_card"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(LunaMint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = ObsidianBackground,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Create New Project",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Multi-track timeline with .ffx & 3D .cube presets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Project Cards (Screenshot 2)
        items(recentProjects, key = { it.id }) { project ->
            ProjectSummaryCard(
                project = project,
                onClick = { onOpenProject(project) },
            )
        }
    }
}

@Composable
private fun LunaCrescentMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            brush = Brush.sweepGradient(listOf(LunaMint, CyberCyan, ElectricViolet, LunaMint)),
            startAngle = -65f,
            sweepAngle = 225f,
            useCenter = false,
            style = stroke,
        )
        drawArc(
            color = LunaMint.copy(alpha = 0.7f),
            startAngle = -35f,
            sweepAngle = 155f,
            useCenter = false,
            topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
            size = size * 0.64f,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun TemplateCircleItem(
    preset: FfxPreset,
    onClick: () -> Unit,
) {
    val badgeColors = when {
        preset.presetFormat == "CUBE_LUT" -> listOf(LunaMint, CyberCyan)
        preset.shakeIntensity > 0.2 -> listOf(ElectricViolet, CyberCyan)
        preset.glowIntensity > 0.25 -> listOf(StudioAmber, ElectricViolet)
        else -> listOf(LunaMint, ElectricViolet)
    }

    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
            .testTag("template_circle_${preset.id.ifBlank { preset.name }}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .border(2.5.dp, Brush.linearGradient(badgeColors), CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(badgeColors.first().copy(alpha = 0.35f), ObsidianSurfaceContainer)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = preset.name,
                tint = badgeColors.first(),
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProjectSummaryCard(
    project: LunaProjectSummary,
    onClick: () -> Unit,
) {
    val totalSec = (project.durationMs / 1000L).coerceAtLeast(1L)
    val durationLabel = String.format(
        Locale.US,
        "00:%02d:%02d",
        totalSec / 60L,
        totalSec % 60L,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ObsidianSurfaceContainer)
            .border(1.dp, OutlineSubtle, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("project_card_${project.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail with duration pill (Screenshot 2)
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 82.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(ObsidianSurfaceHigh, LunaMintContainer)
                    )
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.MovieFilter,
                contentDescription = null,
                tint = LunaMint.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp),
            )
            Surface(
                color = ObsidianBackground.copy(alpha = 0.88f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            ) {
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "FX: ${project.activePresetName}",
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProjectMetaItem(Icons.Filled.AspectRatio, project.aspectPreset.label)
                ProjectMetaItem(Icons.Filled.CropFree, project.resolutionLabel)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProjectMetaItem(Icons.Filled.Layers, "${project.layerCount} layers")
                ProjectMetaItem(Icons.Filled.Speed, "${project.fps}fps")
            }
        }
    }
}

@Composable
private fun ProjectMetaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CreateProjectDialog(
    defaultTitle: String,
    onDismiss: () -> Unit,
    onCreate: (title: String, aspect: AspectPreset, resolution: String, fps: Int) -> Unit,
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var aspect by remember { mutableStateOf(AspectPreset.RATIO_16_9) }
    var resolution by remember { mutableStateOf("1080p") }
    var fps by remember { mutableIntStateOf(60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceHigh,
        title = {
            Text("Create New Project", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AspectPreset.entries.forEach { ratio ->
                        FilterChip(
                            selected = aspect == ratio,
                            onClick = { aspect = ratio },
                            label = { Text(ratio.label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LunaMintContainer,
                                selectedLabelColor = LunaMint,
                            ),
                        )
                    }
                }
                Text("Resolution & Frame Rate", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("720p", "1080p", "1440p", "4K").forEach { res ->
                        FilterChip(
                            selected = resolution == res,
                            onClick = { resolution = res },
                            label = { Text(res, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LunaMintContainer,
                                selectedLabelColor = LunaMint,
                            ),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60).forEach { f ->
                        FilterChip(
                            selected = fps == f,
                            onClick = { fps = f },
                            label = { Text("${f}fps", style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LunaMintContainer,
                                selectedLabelColor = LunaMint,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, aspect, resolution, fps) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LunaMint,
                    contentColor = ObsidianBackground,
                ),
            ) {
                Text("Create Project")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
