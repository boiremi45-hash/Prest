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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Text
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
import com.example.data.model.TransitionSpec
import com.example.data.model.TransitionType
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

        Spacer(modifier = Modifier.height(6.dp))

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
