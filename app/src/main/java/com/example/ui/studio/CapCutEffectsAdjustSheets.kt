package com.example.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnimationCategory
import com.example.data.model.AnimationSettings
import com.example.data.model.CapCutEffectType
import com.example.data.model.ClipAnimationType
import com.example.data.model.EffectMainTab
import com.example.data.model.EffectSubCategory
import com.example.data.model.FfxPreset
import com.example.data.model.TimesliceMode
import com.example.data.model.TimesliceSettings
import com.example.data.model.TransitionSpec
import com.example.data.model.TransitionType
import com.example.data.model.VideoClipSegment
import com.example.data.model.VideoEffectLayer
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LunaMint
import com.example.ui.theme.LunaMintContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EffectsToolSheet(
    activeEffects: List<VideoEffectLayer>,
    selectedEffectId: String?,
    timesliceActive: Boolean = false,
    onOpenTimeslice: () -> Unit = {},
    onApplyEffect: (CapCutEffectType, Float) -> Unit,
    onClose: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(EffectMainTab.VIDEO) }
    var selectedCat by remember { mutableStateOf<EffectSubCategory?>(null) }
    var query by remember { mutableStateOf("") }
    val currentLayer = activeEffects.firstOrNull { it.id == selectedEffectId } ?: activeEffects.lastOrNull()
    var intensity by remember(currentLayer?.id) { mutableFloatStateOf(currentLayer?.intensity ?: 0.75f) }

    val filteredEffects = remember(selectedTab, selectedCat, query) {
        val base = CapCutEffectType.entries.filter { it != CapCutEffectType.NONE }
        val qMatch = if (query.isBlank()) base else base.filter { it.label.contains(query, ignoreCase = true) }
        val catMatch = if (selectedCat == null) qMatch else qMatch.filter { it.category == selectedCat }
        val tabMatch = catMatch.filter { it.mainTab == selectedTab }
        listOf(CapCutEffectType.NONE) + if (query.isNotBlank() || tabMatch.isEmpty()) catMatch else (tabMatch + catMatch.filterNot { it in tabMatch })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
    ) {
        // Tabs: Video effects | Body effects | Style | AI effects
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EffectMainTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LunaMintContainer,
                            selectedLabelColor = LunaMint,
                        ),
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Check, contentDescription = "Apply effects", tint = LunaMint)
            }
        }

        // Search + Categories (Trending | Classic | NEW)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search FX...", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp)) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(44.dp),
            )
            EffectSubCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCat == cat,
                    onClick = { selectedCat = if (selectedCat == cat) null else cat },
                    label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Featured LUNA-exclusive Timeslice button right inside Effects tab
        if (query.isBlank() || "timeslice".contains(query.trim(), ignoreCase = true)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                ElectricViolet.copy(alpha = if (timesliceActive) 0.34f else 0.22f),
                                CyberCyan.copy(alpha = 0.14f),
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        if (timesliceActive) ElectricViolet else CyberCyan.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(onClick = onOpenTimeslice)
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("fx_timeslice_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElectricViolet.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ViewWeek,
                        contentDescription = "Timeslice",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Timeslice",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        text = "CapCut Ghost Mode (Delay Glitch + Stretch) & Node Pro Time Slice",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                Surface(
                    color = if (timesliceActive) ElectricViolet else CyberCyan.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = if (timesliceActive) "ACTIVE" else "2 MODES",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = if (timesliceActive) Color.White else CyberCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Effect tiles horizontal carousel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filteredEffects.forEach { fx ->
                val isSelected = currentLayer?.type == fx || (fx == CapCutEffectType.NONE && currentLayer == null)
                val accent = when (fx) {
                    CapCutEffectType.MATRIX_PANELS -> LunaMint
                    CapCutEffectType.ANALOG_SNOW -> Color(0xFFD1D5DB)
                    CapCutEffectType.BLUR -> CyberCyan
                    CapCutEffectType.FLASH_COPIES -> Color.White
                    CapCutEffectType.DREAMLAND -> Color(0xFFF472B6)
                    CapCutEffectType.PHOTO_STRIP -> Color(0xFF94A3B8)
                    CapCutEffectType.LASER_GRID -> CyberCyan
                    CapCutEffectType.NEON_RAY -> ElectricViolet
                    CapCutEffectType.RETRO_RAYS -> StudioAmber
                    CapCutEffectType.LED_MATRIX -> Color(0xFF38BDF8)
                    CapCutEffectType.GOLDEN_RAY -> Color(0xFFFBBF24)
                    CapCutEffectType.TRACKING_GLITCH -> Color(0xFFFB7185)
                    CapCutEffectType.DELAY_GLITCH -> ElectricViolet
                    CapCutEffectType.NONE -> TextSecondary
                }
                Column(
                    modifier = Modifier
                        .width(74.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) LunaMintContainer else ObsidianSurfaceContainer)
                        .border(1.5.dp, if (isSelected) LunaMint else OutlineSubtle, RoundedCornerShape(10.dp))
                        .clickable { onApplyEffect(fx, intensity) }
                        .padding(6.dp)
                        .testTag("fx_${fx.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (fx == CapCutEffectType.NONE) Brush.linearGradient(listOf(ObsidianBackground, ObsidianSurface))
                                else Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), ObsidianBackground))
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (fx == CapCutEffectType.NONE) Icons.Filled.Block else Icons.Filled.AutoAwesome,
                            contentDescription = fx.label,
                            tint = accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fx.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isSelected) LunaMint else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (currentLayer != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                LabeledSlider(
                    label = "Intensity (${currentLayer.type.label})",
                    value = intensity * 100f,
                    range = 0f..100f,
                    readout = "${(intensity * 100).roundToInt()}%",
                ) { v ->
                    intensity = v / 100f
                    onApplyEffect(currentLayer.type, intensity)
                }
            }
        }
    }
}

@Composable
fun AnimationsToolSheet(
    animation: AnimationSettings,
    onUpdateAnimation: (AnimationSettings) -> Unit,
    onClose: () -> Unit,
) {
    var cat by remember { mutableStateOf(AnimationCategory.IN) }
    val selectedType = when (cat) {
        AnimationCategory.IN -> animation.inType
        AnimationCategory.OUT -> animation.outType
        AnimationCategory.COMBO -> animation.comboType
    }
    val selectedDur = when (cat) {
        AnimationCategory.IN -> animation.inDurationSec
        AnimationCategory.OUT -> animation.outDurationSec
        AnimationCategory.COMBO -> animation.comboDurationSec
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        SheetHeader(title = "Animations", subtitle = "${cat.label}: ${selectedType.label}", onClose = onClose)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimationCategory.entries.forEach { c ->
                FilterChip(
                    selected = cat == c,
                    onClick = { cat = c },
                    label = { Text(c.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClipAnimationType.entries.forEach { animType ->
                val isSel = selectedType == animType
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) LunaMintContainer else ObsidianSurfaceContainer)
                        .border(1.5.dp, if (isSel) LunaMint else OutlineSubtle, RoundedCornerShape(10.dp))
                        .clickable {
                            val next = when (cat) {
                                AnimationCategory.IN -> animation.copy(inType = animType)
                                AnimationCategory.OUT -> animation.copy(outType = animType)
                                AnimationCategory.COMBO -> animation.copy(comboType = animType)
                            }
                            onUpdateAnimation(next)
                        }
                        .padding(8.dp)
                        .testTag("anim_${animType.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = animType.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) LunaMint else Color.White,
                        maxLines = 2,
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            LabeledSlider(
                label = "Duration",
                value = selectedDur,
                range = 0.1f..3.0f,
                readout = String.format(Locale.US, "%.1fs", selectedDur),
            ) { sec ->
                val next = when (cat) {
                    AnimationCategory.IN -> animation.copy(inDurationSec = sec)
                    AnimationCategory.OUT -> animation.copy(outDurationSec = sec)
                    AnimationCategory.COMBO -> animation.copy(comboDurationSec = sec)
                }
                onUpdateAnimation(next)
            }
        }
    }
}

@Composable
fun TransitionsToolSheet(
    afterClipId: String,
    currentSpec: TransitionSpec?,
    onUpdateTransition: (afterClipId: String, TransitionType, Float, applyToAll: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var selectedType by remember(currentSpec?.id) { mutableStateOf(currentSpec?.type ?: TransitionType.NONE) }
    var durationSec by remember(currentSpec?.id) { mutableFloatStateOf(currentSpec?.durationSec ?: 0.4f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        SheetHeader(
            title = "Transitions",
            subtitle = "${selectedType.label} (${String.format(Locale.US, "%.1fs", durationSec)})",
            onClose = onClose,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransitionType.entries.forEach { tType ->
                val active = selectedType == tType
                Column(
                    modifier = Modifier
                        .width(82.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) LunaMintContainer else ObsidianSurfaceContainer)
                        .border(1.5.dp, if (active) LunaMint else OutlineSubtle, RoundedCornerShape(10.dp))
                        .clickable {
                            selectedType = tType
                            onUpdateTransition(afterClipId, tType, durationSec, false)
                        }
                        .padding(10.dp)
                        .testTag("trans_${tType.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tType.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) LunaMint else Color.White,
                        maxLines = 2,
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            LabeledSlider(
                label = "Transition Duration (0.1s – 0.8s)",
                value = durationSec,
                range = 0.1f..0.8f,
                readout = String.format(Locale.US, "%.1fs", durationSec),
            ) { s ->
                durationSec = s
                onUpdateTransition(afterClipId, selectedType, s, false)
            }
            OutlinedButton(
                onClick = {
                    onUpdateTransition(afterClipId, selectedType, durationSec, true)
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply to all cuts", color = CyberCyan)
            }
        }
    }
}

@Composable
fun FiltersToolSheet(
    presets: List<FfxPreset>,
    activePreset: FfxPreset,
    filterIntensity: Float,
    onSelectPreset: (FfxPreset) -> Unit,
    onChangeIntensity: (Float) -> Unit,
    onImportFfxOrCube: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        SheetHeader(
            title = "LUNA Filters (.cube & .ffx)",
            subtitle = "${activePreset.name} (${activePreset.presetFormat})",
            onClose = onClose,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Import custom .cube/.ffx card
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianSurfaceContainer)
                    .border(1.dp, LunaMint, RoundedCornerShape(10.dp))
                    .clickable(onClick = onImportFfxOrCube)
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.FileUpload, null, tint = LunaMint, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Import\n.cube/.ffx", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = LunaMint)
            }

            presets.forEach { preset ->
                val selected = activePreset.name == preset.name
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) LunaMintContainer else ObsidianSurfaceContainer)
                        .border(1.5.dp, if (selected) LunaMint else OutlineSubtle, RoundedCornerShape(10.dp))
                        .clickable { onSelectPreset(preset) }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) LunaMint else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = preset.presetFormat,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = CyberCyan,
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            LabeledSlider(
                label = "Filter Strength",
                value = filterIntensity * 100f,
                range = 0f..100f,
                readout = "${(filterIntensity * 100).roundToInt()}%",
            ) { onChangeIntensity(it / 100f) }
        }
    }
}

@Composable
fun TimesliceToolSheet(
    clip: VideoClipSegment,
    onApplyCapCutMode: () -> Unit,
    onApplyNodeMode: () -> Unit,
    onUpdateTimeslice: (TimesliceSettings, Boolean) -> Unit,
    onTogglePreview: () -> Unit,
    onRemoveTimeslice: () -> Unit,
    onSaveAsPreset: () -> Unit,
    onClose: () -> Unit,
) {
    var localSettings by remember(clip.id, clip.timeslice) { mutableStateOf(clip.timeslice) }
    val clipDurSec = clip.baseDurationMs() / 1000f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 14.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ViewWeek,
                        contentDescription = null,
                        tint = ElectricViolet,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Timeslice",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    if (localSettings.enabled) {
                        Surface(
                            color = if (localSettings.isNodeModeActive()) CyberCyan.copy(alpha = 0.2f) else ElectricViolet.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = localSettings.mode.badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = if (localSettings.isNodeModeActive()) CyberCyan else Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "${clip.displayName} • ${String.format(Locale.US, "%.1fs", clipDurSec)} (Choose Mode 1 or Mode 2)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("timeslice_done_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Done Timeslice",
                    tint = LunaMint,
                )
            }
        }

        // 2 Mode Selection Cards: CapCut Mode + Node Mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val capcutSelected = localSettings.isCapCutModeActive()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (capcutSelected) ElectricViolet.copy(alpha = 0.22f)
                        else ObsidianSurfaceContainer
                    )
                    .border(
                        width = if (capcutSelected) 1.5.dp else 1.dp,
                        color = if (capcutSelected) ElectricViolet else OutlineSubtle,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable(onClick = onApplyCapCutMode)
                    .padding(12.dp)
                    .testTag("timeslice_capcut_mode_card"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "CapCut Mode",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Surface(
                        color = ElectricViolet.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "Easy / Ghost",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = "Delay Glitch across cut (V1+V2) + Stretch Left transition (0.4s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = onApplyCapCutMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (capcutSelected) ElectricViolet else ObsidianSurface,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("timeslice_apply_capcut_btn"),
                ) {
                    Text(
                        text = if (capcutSelected) "Applied" else "Apply",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            val nodeSelected = localSettings.isNodeModeActive()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (nodeSelected) CyberCyan.copy(alpha = 0.16f)
                        else ObsidianSurfaceContainer
                    )
                    .border(
                        width = if (nodeSelected) 1.5.dp else 1.dp,
                        color = if (nodeSelected) CyberCyan else OutlineSubtle,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable(onClick = onApplyNodeMode)
                    .padding(12.dp)
                    .testTag("timeslice_node_mode_card"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Node Mode",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Surface(
                        color = CyberCyan.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "Real / Pro",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = "Real Time Slice strips at T + (i·Interval + Offset) with soft feather",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = onApplyNodeMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (nodeSelected) CyberCyan else ObsidianSurface,
                        contentColor = if (nodeSelected) ObsidianBackground else Color.White,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("timeslice_apply_node_btn"),
                ) {
                    Text(
                        text = if (nodeSelected) "Applied" else "Apply",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (localSettings.enabled) {
            if (localSettings.isCapCutModeActive()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianSurfaceContainer)
                            .border(1.dp, ElectricViolet.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Layers, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Object: All Videos (V1+V2) • Delay Glitch + Stretch Left",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }

                    TimesliceParamSlider(
                        label = "Amount",
                        value = localSettings.capcutAmount,
                        range = 0f..100f,
                        steps = 0,
                        readout = "${localSettings.capcutAmount.roundToInt()}",
                        sliderTag = "timeslice_capcut_amount_slider",
                        onValueChange = { v ->
                            val next = localSettings.copy(capcutAmount = v.coerceIn(0f, 100f))
                            localSettings = next
                            onUpdateTimeslice(next, false)
                        },
                        onValueChangeFinished = { onUpdateTimeslice(localSettings, true) },
                    )

                    TimesliceParamSlider(
                        label = "Speed",
                        value = localSettings.capcutSpeed,
                        range = 0f..100f,
                        steps = 0,
                        readout = "${localSettings.capcutSpeed.roundToInt()}",
                        sliderTag = "timeslice_capcut_speed_slider",
                        onValueChange = { v ->
                            val next = localSettings.copy(capcutSpeed = v.coerceIn(0f, 100f))
                            localSettings = next
                            onUpdateTimeslice(next, false)
                        },
                        onValueChangeFinished = { onUpdateTimeslice(localSettings, true) },
                    )

                    TimesliceParamSlider(
                        label = "Rotate",
                        value = localSettings.capcutRotateDeg,
                        range = 0f..360f,
                        steps = 0,
                        readout = "${localSettings.capcutRotateDeg.roundToInt()}°",
                        sliderTag = "timeslice_capcut_rotate_slider",
                        onValueChange = { v ->
                            val next = localSettings.copy(capcutRotateDeg = v.coerceIn(0f, 360f))
                            localSettings = next
                            onUpdateTimeslice(next, false)
                        },
                        onValueChangeFinished = { onUpdateTimeslice(localSettings, true) },
                    )

                    TimesliceParamSlider(
                        label = "Stretch Left Duration",
                        value = localSettings.capcutStretchDurationSec,
                        range = 0.3f..0.5f,
                        steps = 0,
                        readout = String.format(Locale.US, "%.2fs", localSettings.capcutStretchDurationSec),
                        sliderTag = "timeslice_capcut_stretch_slider",
                        onValueChange = { v ->
                            val snapped = ((v * 20f).roundToInt() / 20f).coerceIn(0.3f, 0.5f)
                            val next = localSettings.copy(capcutStretchDurationSec = snapped)
                            localSettings = next
                            onUpdateTimeslice(next, false)
                        },
                        onValueChangeFinished = { onUpdateTimeslice(localSettings, true) },
                    )
                }
            } else {
                // Node Mode: Viral Node preset button + Animate toggle + 5 exact sliders
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val preset = TimesliceSettings.viralNodePreset(
                                previewEnabled = localSettings.previewEnabled,
                                animate = localSettings.animate,
                            )
                            localSettings = preset
                            onUpdateTimeslice(preset, true)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("timeslice_viral_node_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Viral Node", color = CyberCyan, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianSurfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Animate",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Text(
                                text = if (localSettings.animate) "Offset moves over time" else "Static offset",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = localSettings.animate,
                            onCheckedChange = { checked ->
                                val next = localSettings.copy(
                                    enabled = true,
                                    mode = TimesliceMode.NODE,
                                    animate = checked,
                                )
                                localSettings = next
                                onUpdateTimeslice(next, true)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBackground,
                                checkedTrackColor = CyberCyan,
                            ),
                            modifier = Modifier.testTag("timeslice_animate_toggle"),
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Slice Count: 1 to 10 (default 5)
                    TimesliceParamSlider(
                        label = "Slice Count",
                        value = localSettings.clampedSliceCount().toFloat(),
                        range = 1f..10f,
                        steps = 8,
                        readout = "${localSettings.clampedSliceCount()}",
                        sliderTag = "timeslice_slice_count_slider",
                        onValueChange = { v ->
                            val next = localSettings.copy(
                                enabled = true,
                                mode = TimesliceMode.NODE,
                                sliceCount = v.roundToInt().coerceIn(1, 10),
                            )
                            localSettings = next
                            onUpdateTimeslice(next, false)
                        },
                        onValueChangeFinished = { onUpdateTimeslice(localSettings, true) },
                    )

                    // Slice Angle: 0° to 360° (default 0°, 0=Vertical, 90=Horizontal)
                    TimesliceParamSlider(
                        label = "Slice Angle",
                        value = localSettings.clampedAngleDeg(),
                        range = 0f..360f,
                        steps = 0,
                        readout = "${localSettings.clampedAngleDeg().roundToInt()}°" +
                            when (localSettings.clampedAngleDeg().roundToInt()) {
                                0, 180, 360 -> " (Vert)"
                                90, 270 -> " (Horiz)"
                                else -> ""
                            },
                        sliderTag = "timeslice_slice_angle_slider",
                        onValueChange = { v ->
                            val next = localSettings.copy(
                                enabled = true,
                                mode = TimesliceMode.NODE,
                                sliceAngleDeg = v.roundToInt().coerceIn(0, 360).toFloat(),
                            )
                            localSettings = next
                            onUpdateTimeslice(next, false)
                        },
                        onValueChangeFinished = { onUpdateTimeslice(localSettings, true) },
                    )

                    // Frame Interval: 0 to 10 (default 2)
                    TimesliceParamSlider(
                        label = "Frame Interval",
                        value = localSettings.clampedFrameInterval().toFloat(),
                        range = 0f..10f,
                        steps = 9,
                        readout = "${localSettings.clampedFrameInterval()}f",
                        sliderTag = "timeslice_frame_interval_slider",
                        onValueChange = { v ->
                            val next = localSettings.copy(
                                enabled = true,
                                mode = TimesliceMode.NODE,
                                frameInterval = v.roundToInt().coerceIn(0, 10),
                            )
                            localSettings = next
                            onUpdateTimeslice(next, false)
                        },
                        onValueChangeFinished = { onUpdateTimeslice(localSettings, true) },
                    )

                    // Frame Offset: 0 to 100 (default 25)
                    TimesliceParamSlider(
                        label = "Frame Offset",
                        value = localSettings.clampedFrameOffset().toFloat(),
                        range = 0f..100f,
                        steps = 0,
                        readout = "${localSettings.clampedFrameOffset()}f",
                        sliderTag = "timeslice_frame_offset_slider",
                        onValueChange = { v ->
                            val next = localSettings.copy(
                                enabled = true,
                                mode = TimesliceMode.NODE,
                                frameOffset = v.roundToInt().coerceIn(0, 100),
                            )
                            localSettings = next
                            onUpdateTimeslice(next, false)
                        },
                        onValueChangeFinished = { onUpdateTimeslice(localSettings, true) },
                    )

                    // Feather: 0.00 to 1.00 (default 0.50)
                    TimesliceParamSlider(
                        label = "Feather",
                        value = localSettings.clampedFeather(),
                        range = 0f..1f,
                        steps = 0,
                        readout = String.format(Locale.US, "%.2f", localSettings.clampedFeather()),
                        sliderTag = "timeslice_feather_slider",
                        onValueChange = { v ->
                            val snapped = ((v * 100f).roundToInt() / 100f).coerceIn(0f, 1f)
                            val next = localSettings.copy(
                                enabled = true,
                                mode = TimesliceMode.NODE,
                                sliceFeather = snapped,
                            )
                            localSettings = next
                            onUpdateTimeslice(next, false)
                        },
                        onValueChangeFinished = { onUpdateTimeslice(localSettings, true) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Shared Actions Row: [Preview], [Remove Timeslice], [Save as Preset]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onTogglePreview,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("timeslice_preview_button"),
                ) {
                    Icon(
                        imageVector = if (localSettings.previewEnabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Preview",
                        tint = if (localSettings.previewEnabled) CyberCyan else TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (localSettings.previewEnabled) "Preview ON" else "Preview OFF",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (localSettings.previewEnabled) CyberCyan else TextSecondary,
                        maxLines = 1,
                    )
                }

                OutlinedButton(
                    onClick = {
                        onRemoveTimeslice()
                        onClose()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("timeslice_remove_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Remove Timeslice",
                        tint = Color(0xFFFB7185),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFB7185),
                        maxLines = 1,
                    )
                }

                Button(
                    onClick = onSaveAsPreset,
                    colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("timeslice_save_preset_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkAdded,
                        contentDescription = "Save as Preset",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Save Preset",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimesliceParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    readout: String,
    sliderTag: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
            Text(readout, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ElectricViolet,
                activeTrackColor = ElectricViolet,
            ),
            modifier = Modifier.testTag(sliderTag),
        )
    }
}
