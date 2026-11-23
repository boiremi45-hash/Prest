package com.example.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdjustSettings
import com.example.data.model.AspectPreset
import com.example.data.model.AudioTrackItem
import com.example.data.model.CurveChannel
import com.example.data.model.CurvePoint
import com.example.data.model.HslBandValue
import com.example.data.model.HslColorBand
import com.example.data.model.OverlayBlendMode
import com.example.data.model.OverlayTrackItem
import com.example.data.model.RgbCurvesSpec
import com.example.data.model.TextTrackItem
import com.example.data.model.VolumeKeyframeEvaluator
import com.example.domain.video.RgbCurveLutBuilder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LunaMint
import com.example.ui.theme.LunaMintContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class AdjustItem(val label: String) {
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    SATURATION("Saturation"),
    BRILLIANCE("Brilliance"),
    SHARPEN("Sharpen"),
    CLARITY("Clarity"),
    HIGHLIGHTS("Highlights"),
    SHADOWS("Shadows"),
    WHITES("Whites"),
    BLACKS("Blacks"),
    TEMP("Temp"),
    HUE("Hue"),
    FADE("Fade"),
    VIGNETTE("Vignette"),
    GRAIN("Grain"),
    HSL("HSL"),
    GRAPHS("Graphs"),
    COLOR_WHEEL("Color wheel"),
}

@Composable
fun AdjustToolSheet(
    adjust: AdjustSettings,
    onUpdateAdjust: (AdjustSettings) -> Unit,
    onSmartAuto: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    var selectedItem by remember { mutableStateOf(AdjustItem.BRIGHTNESS) }
    var selectedHslBand by remember { mutableStateOf(HslColorBand.RED) }
    var selectedCurveChannel by remember { mutableStateOf(CurveChannel.WHITE) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = false,
                    onClick = onSmartAuto,
                    label = { Text("Auto adjust", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Filled.AutoFixHigh, null, modifier = Modifier.size(15.dp)) },
                )
                TextButton(onClick = onReset) {
                    Text("Reset", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            Text("Adjust", style = MaterialTheme.typography.titleSmall, color = Color.White)
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Check, contentDescription = "Apply Adjust", tint = LunaMint)
            }
        }

        // Tool strip matching CapCut Adjust bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AdjustItem.entries.forEach { item ->
                val active = selectedItem == item
                FilterChip(
                    selected = active,
                    onClick = { selectedItem = item },
                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LunaMintContainer,
                        selectedLabelColor = LunaMint,
                    ),
                    modifier = Modifier.testTag("adjust_${item.name.lowercase()}"),
                )
            }
        }

        when (selectedItem) {
            AdjustItem.GRAPHS -> {
                RgbCurvesEditor(
                    curves = adjust.rgbCurves,
                    selectedChannel = selectedCurveChannel,
                    onSelectChannel = { selectedCurveChannel = it },
                    onUpdateCurves = { onUpdateAdjust(adjust.copy(rgbCurves = it)) },
                )
            }
            AdjustItem.HSL -> {
                val bandVal = adjust.hsl[selectedHslBand] ?: HslBandValue()
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HslColorBand.entries.forEach { band ->
                            val isSel = band == selectedHslBand
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(band.colorArgb))
                                    .border(if (isSel) 2.5.dp else 1.dp, if (isSel) Color.White else OutlineSubtle, CircleShape)
                                    .clickable { selectedHslBand = band },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LabeledSlider("${selectedHslBand.label} Hue", bandVal.hue, -50f..50f, "${bandVal.hue.roundToInt()}") { v ->
                        onUpdateAdjust(adjust.copy(hsl = adjust.hsl + (selectedHslBand to bandVal.copy(hue = v))))
                    }
                    LabeledSlider("${selectedHslBand.label} Saturation", bandVal.saturation, -50f..50f, "${bandVal.saturation.roundToInt()}") { v ->
                        onUpdateAdjust(adjust.copy(hsl = adjust.hsl + (selectedHslBand to bandVal.copy(saturation = v))))
                    }
                    LabeledSlider("${selectedHslBand.label} Lightness", bandVal.lightness, -50f..50f, "${bandVal.lightness.roundToInt()}") { v ->
                        onUpdateAdjust(adjust.copy(hsl = adjust.hsl + (selectedHslBand to bandVal.copy(lightness = v))))
                    }
                }
            }
            AdjustItem.COLOR_WHEEL -> {
                val cw = adjust.colorWheel
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp).height(180.dp).verticalScroll(rememberScrollState()),
                ) {
                    LabeledSlider("Shadows Tint (Lift)", cw.liftAmount * 100f, 0f..100f, "${(cw.liftAmount * 100).roundToInt()}") { v ->
                        onUpdateAdjust(adjust.copy(colorWheel = cw.copy(liftAmount = v / 100f)))
                    }
                    LabeledSlider("Shadows Hue", cw.liftHueDeg, 0f..360f, "${cw.liftHueDeg.roundToInt()}°") { v ->
                        onUpdateAdjust(adjust.copy(colorWheel = cw.copy(liftHueDeg = v)))
                    }
                    LabeledSlider("Midtones Tint (Gamma)", cw.gammaAmount * 100f, 0f..100f, "${(cw.gammaAmount * 100).roundToInt()}") { v ->
                        onUpdateAdjust(adjust.copy(colorWheel = cw.copy(gammaAmount = v / 100f)))
                    }
                    LabeledSlider("Midtones Hue", cw.gammaHueDeg, 0f..360f, "${cw.gammaHueDeg.roundToInt()}°") { v ->
                        onUpdateAdjust(adjust.copy(colorWheel = cw.copy(gammaHueDeg = v)))
                    }
                    LabeledSlider("Highlights Tint (Gain)", cw.gainAmount * 100f, 0f..100f, "${(cw.gainAmount * 100).roundToInt()}") { v ->
                        onUpdateAdjust(adjust.copy(colorWheel = cw.copy(gainAmount = v / 100f)))
                    }
                    LabeledSlider("Highlights Hue", cw.gainHueDeg, 0f..360f, "${cw.gainHueDeg.roundToInt()}°") { v ->
                        onUpdateAdjust(adjust.copy(colorWheel = cw.copy(gainHueDeg = v)))
                    }
                }
            }
            else -> {
                val (valNow, range, setter) = rememberSliderBinding(selectedItem, adjust, onUpdateAdjust)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    LabeledSlider(
                        label = selectedItem.label,
                        value = valNow,
                        range = range,
                        readout = "${valNow.roundToInt()}",
                        onValueChange = setter,
                    )
                }
            }
        }
    }
}

private fun rememberSliderBinding(
    item: AdjustItem,
    a: AdjustSettings,
    onUpdate: (AdjustSettings) -> Unit,
): Triple<Float, ClosedFloatingPointRange<Float>, (Float) -> Unit> = when (item) {
    AdjustItem.BRIGHTNESS -> Triple(a.brightness, -50f..50f) { onUpdate(a.copy(brightness = it)) }
    AdjustItem.CONTRAST -> Triple(a.contrast, -50f..50f) { onUpdate(a.copy(contrast = it)) }
    AdjustItem.SATURATION -> Triple(a.saturation, -50f..50f) { onUpdate(a.copy(saturation = it)) }
    AdjustItem.BRILLIANCE -> Triple(a.brilliance, -50f..50f) { onUpdate(a.copy(brilliance = it)) }
    AdjustItem.SHARPEN -> Triple(a.sharpen, 0f..100f) { onUpdate(a.copy(sharpen = it)) }
    AdjustItem.CLARITY -> Triple(a.clarity, -50f..50f) { onUpdate(a.copy(clarity = it)) }
    AdjustItem.HIGHLIGHTS -> Triple(a.highlights, -50f..50f) { onUpdate(a.copy(highlights = it)) }
    AdjustItem.SHADOWS -> Triple(a.shadows, -50f..50f) { onUpdate(a.copy(shadows = it)) }
    AdjustItem.WHITES -> Triple(a.whites, -50f..50f) { onUpdate(a.copy(whites = it)) }
    AdjustItem.BLACKS -> Triple(a.blacks, -50f..50f) { onUpdate(a.copy(blacks = it)) }
    AdjustItem.TEMP -> Triple(a.temp, -50f..50f) { onUpdate(a.copy(temp = it)) }
    AdjustItem.HUE -> Triple(a.hue, -50f..50f) { onUpdate(a.copy(hue = it)) }
    AdjustItem.FADE -> Triple(a.fade, 0f..100f) { onUpdate(a.copy(fade = it)) }
    AdjustItem.VIGNETTE -> Triple(a.vignette, -50f..50f) { onUpdate(a.copy(vignette = it)) }
    else -> Triple(a.grain, 0f..100f) { onUpdate(a.copy(grain = it)) }
}

@Composable
fun RgbCurvesEditor(
    curves: RgbCurvesSpec,
    selectedChannel: CurveChannel,
    onSelectChannel: (CurveChannel) -> Unit,
    onUpdateCurves: (RgbCurvesSpec) -> Unit,
) {
    val pts = curves.pointsFor(selectedChannel).sortedBy { it.x }
    var activePointIdx by remember(selectedChannel) { mutableIntStateOf(2.coerceAtMost(pts.lastIndex)) }
    val activePt = pts.getOrElse(activePointIdx) { CurvePoint(0.5f, 0.5f) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CurveChannel.entries.forEach { ch ->
                    val sel = selectedChannel == ch
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(ch.accentArgb))
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) CyberCyan else OutlineSubtle, CircleShape)
                            .clickable { onSelectChannel(ch) }
                            .testTag("curve_channel_${ch.name.lowercase()}"),
                    )
                }
            }
            Text(
                text = "(${(activePt.x * 255).roundToInt()}, ${(activePt.y * 255).roundToInt()})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = CyberCyan,
            )
            TextButton(onClick = {
                val def = RgbCurvesSpec.defaultCurvePoints()
                val next = when (selectedChannel) {
                    CurveChannel.WHITE -> curves.copy(white = def)
                    CurveChannel.RED -> curves.copy(red = def)
                    CurveChannel.GREEN -> curves.copy(green = def)
                    CurveChannel.BLUE -> curves.copy(blue = def)
                }
                onUpdateCurves(next)
            }) {
                Icon(Icons.Filled.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Curve", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        // Interactive Draggable RGB Curve Graph Box
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianBackground)
                .border(1.dp, OutlineSubtle, RoundedCornerShape(10.dp))
                .pointerInput(selectedChannel, pts) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val normX = (offset.x / size.width).coerceIn(0f, 1f)
                            val nearest = pts.indices.minByOrNull { abs(pts[it].x - normX) } ?: 2
                            activePointIdx = nearest
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                            val ny = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            val updated = pts.toMutableList()
                            val clampedX = when (activePointIdx) {
                                0 -> 0f
                                updated.lastIndex -> 1f
                                else -> nx.coerceIn(updated[activePointIdx - 1].x + 0.04f, updated[activePointIdx + 1].x - 0.04f)
                            }
                            updated[activePointIdx] = CurvePoint(clampedX, ny)
                            val nextSpec = when (selectedChannel) {
                                CurveChannel.WHITE -> curves.copy(white = updated)
                                CurveChannel.RED -> curves.copy(red = updated)
                                CurveChannel.GREEN -> curves.copy(green = updated)
                                CurveChannel.BLUE -> curves.copy(blue = updated)
                            }
                            onUpdateCurves(nextSpec)
                        },
                    )
                },
        ) {
            // 4x4 CapCut grid lines
            for (g in 1..3) {
                val gx = size.width * (g / 4f)
                val gy = size.height * (g / 4f)
                drawLine(OutlineSubtle.copy(alpha = 0.55f), Offset(gx, 0f), Offset(gx, size.height), 1.dp.toPx())
                drawLine(OutlineSubtle.copy(alpha = 0.55f), Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx())
            }
            // Baseline diagonal
            drawLine(OutlineSubtle, Offset(0f, size.height), Offset(size.width, 0f), 1.dp.toPx())

            // Spline path
            val path = Path()
            val samples = 40
            for (i in 0..samples) {
                val xNorm = i / samples.toFloat()
                val yNorm = RgbCurveLutBuilder.evaluateCurve(pts, xNorm)
                val px = xNorm * size.width
                val py = (1f - yNorm) * size.height
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, Color(selectedChannel.accentArgb), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))

            pts.forEachIndexed { idx, p ->
                val center = Offset(p.x * size.width, (1f - p.y) * size.height)
                drawCircle(
                    color = if (idx == activePointIdx) CyberCyan else Color.White,
                    radius = if (idx == activePointIdx) 6.dp.toPx() else 4.5.dp.toPx(),
                    center = center,
                )
            }
        }
    }
}

@Composable
fun OverlayToolSheet(
    overlays: List<OverlayTrackItem>,
    selectedOverlayId: String?,
    onPickOverlayMedia: () -> Unit,
    onSelectOverlay: (String?) -> Unit,
    onUpdateOverlay: (OverlayTrackItem) -> Unit,
    onDeleteOverlay: (String) -> Unit,
    onClose: () -> Unit,
) {
    val active = overlays.firstOrNull { it.id == selectedOverlayId } ?: overlays.lastOrNull()
    val chromaColors = listOf(0xFF1FE066L, 0xFF2563EBL, 0xFFFFFFFFL, 0xFF111111L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        SheetHeader(title = "Overlay PiP & Chroma Key", subtitle = active?.displayName, onClose = onClose)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPickOverlayMedia,
                colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
                modifier = Modifier.testTag("add_overlay_button"),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add overlay")
            }
            overlays.forEach { ov ->
                FilterChip(
                    selected = active?.id == ov.id,
                    onClick = { onSelectOverlay(ov.id) },
                    label = { Text(ov.displayName.take(12), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        if (active != null) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).height(185.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Auto remove background", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Switch(
                        checked = active.autoRemoveBg,
                        onCheckedChange = { onUpdateOverlay(active.copy(autoRemoveBg = it)) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Colorize, null, tint = LunaMint, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chroma key", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    Switch(
                        checked = active.chromaEnabled,
                        onCheckedChange = { onUpdateOverlay(active.copy(chromaEnabled = it)) },
                    )
                }
                if (active.chromaEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Key color:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        chromaColors.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(if (active.chromaColorArgb == hex) 2.5.dp else 1.dp, Color.White, CircleShape)
                                    .clickable { onUpdateOverlay(active.copy(chromaColorArgb = hex)) },
                            )
                        }
                    }
                    LabeledSlider("Chroma Intensity", active.chromaIntensity * 100f, 5f..85f, "${(active.chromaIntensity * 100).roundToInt()}%") {
                        onUpdateOverlay(active.copy(chromaIntensity = it / 100f))
                    }
                    LabeledSlider("Chroma Shadow", active.chromaShadow * 100f, 0f..100f, "${(active.chromaShadow * 100).roundToInt()}%") {
                        onUpdateOverlay(active.copy(chromaShadow = it / 100f))
                    }
                }
                LabeledSlider("Overlay Opacity", active.opacity * 100f, 0f..100f, "${(active.opacity * 100).roundToInt()}%") {
                    onUpdateOverlay(active.copy(opacity = it / 100f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OverlayBlendMode.entries.forEach { bm ->
                        FilterChip(
                            selected = active.blendMode == bm,
                            onClick = { onUpdateOverlay(active.copy(blendMode = bm)) },
                            label = { Text(bm.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                TextButton(onClick = { onDeleteOverlay(active.id) }) {
                    Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove Overlay", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun TextCaptionsToolSheet(
    isCaptionsMode: Boolean,
    texts: List<TextTrackItem>,
    selectedTextId: String?,
    onSelectText: (String) -> Unit,
    onAddText: (String, Boolean) -> Unit,
    onUpdateText: (TextTrackItem) -> Unit,
    onGenerateAutoCaptions: () -> Unit,
    onDeleteText: (String) -> Unit,
    onClose: () -> Unit,
) {
    val selectedText = texts.firstOrNull { it.id == selectedTextId }
    var draft by remember(selectedText?.id) { mutableStateOf(selectedText?.text ?: "") }
    val palette = listOf(0xFFFFFFFFL, 0xFFFACC15L, 0xFF00E5FFL, 0xFFFB7185L, 0xFF34D399L, 0xFF111827L)

    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 12.dp)) {
        SheetHeader(
            title = when {
                selectedText != null -> "Edit Text & Style"
                isCaptionsMode -> "Auto Captions"
                else -> "Add Text"
            },
            subtitle = selectedText?.let { "${it.startMs / 1000f}s – ${it.endMs / 1000f}s" },
            onClose = onClose,
        )
        Column(modifier = Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { next ->
                        draft = next
                        if (selectedText != null) {
                            onUpdateText(selectedText.copy(text = next))
                        }
                    },
                    placeholder = { Text(if (isCaptionsMode) "Caption subtitle line..." else "Enter overlay text...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (selectedText != null) {
                            onUpdateText(selectedText.copy(text = draft.ifBlank { selectedText.text }))
                            onClose()
                        } else {
                            onAddText(draft, isCaptionsMode)
                            draft = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
                ) { Text(if (selectedText != null) "Apply" else "Add") }
            }
            if (selectedText != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Color:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    palette.forEach { argb ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(if (selectedText.colorArgb == argb) 2.5.dp else 1.dp, CyberCyan, CircleShape)
                                .clickable { onUpdateText(selectedText.copy(colorArgb = argb)) },
                        )
                    }
                    FilterChip(
                        selected = selectedText.bold,
                        onClick = { onUpdateText(selectedText.copy(bold = !selectedText.bold)) },
                        label = { Text("Bold", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            if (isCaptionsMode) {
                OutlinedButton(onClick = onGenerateAutoCaptions, modifier = Modifier.fillMaxWidth()) {
                    Text("Generate Synced Captions for Clip", color = CyberCyan)
                }
            }
            if (texts.isNotEmpty()) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    texts.forEach { item ->
                        FilterChip(
                            selected = item.id == selectedTextId,
                            onClick = { onSelectText(item.id) },
                            label = { Text(item.text.take(16), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EffectObjectToolSheet(
    applyToAllVideos: Boolean,
    effectLabel: String,
    onSelectTarget: (applyToAllVideos: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 14.dp)) {
        SheetHeader(title = "Object", subtitle = effectLabel, onClose = onClose)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = applyToAllVideos,
                onClick = { onSelectTarget(true) },
                label = { Text("All videos") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                modifier = Modifier.weight(1f).testTag("effect_object_all"),
            )
            FilterChip(
                selected = !applyToAllVideos,
                onClick = { onSelectTarget(false) },
                label = { Text("Main video") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                modifier = Modifier.weight(1f).testTag("effect_object_main"),
            )
        }
    }
}

@Composable
fun AudioTrackVolumeSheet(
    audio: AudioTrackItem,
    localPlayheadMs: Long,
    selectedVolumeKeyframeId: String?,
    onUpdateVolume: (Int, Boolean) -> Unit,
    onAddVolumeKeyframe: () -> Unit,
    onDeleteVolumeKeyframe: () -> Unit,
    onDeleteAllVolumeKeyframes: () -> Unit,
    onClose: () -> Unit,
) {
    val evaluatedVol = VolumeKeyframeEvaluator.evaluate(
        keyframes = audio.volumeKeyframes,
        localTimeMs = localPlayheadMs,
        baseVolumePercent = audio.volumePercent,
    )
    val isOnVolKf = audio.volumeKeyframes.any {
        it.id == selectedVolumeKeyframeId || abs(it.timeMs - localPlayheadMs) < 140L
    }
    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 12.dp)) {
        SheetHeader(
            title = "Audio Volume",
            subtitle = buildString {
                append("${audio.title} (${evaluatedVol}%)")
                if (audio.volumeKeyframes.isNotEmpty()) append(" • ${audio.volumeKeyframes.size} KFs")
            },
            onClose = onClose,
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LabeledSlider("Volume (0–1000%)", evaluatedVol.toFloat(), 0f..1000f, "${evaluatedVol}%") {
                onUpdateVolume(it.roundToInt(), audio.reduceNoise)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onAddVolumeKeyframe,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LunaMint),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp).testTag("audio_add_volume_keyframe"),
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Volume Keyframe ◆", style = MaterialTheme.typography.labelSmall)
                }
                if (audio.volumeKeyframes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onDeleteVolumeKeyframe,
                        enabled = isOnVolKf,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp).testTag("audio_delete_volume_keyframe"),
                    ) {
                        Text("Delete KF", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = onDeleteAllVolumeKeyframes,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp).testTag("audio_delete_all_volume_keyframes"),
                    ) {
                        Text("Clear All", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Reduce noise", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Switch(
                    checked = audio.reduceNoise,
                    onCheckedChange = { onUpdateVolume(evaluatedVol, it) },
                )
            }
        }
    }
}

@Composable
fun AudioTrackToolSheet(
    audioTracks: List<AudioTrackItem>,
    onPickAudioFile: () -> Unit,
    onDeleteAudio: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 14.dp)) {
        SheetHeader(title = "Audio", subtitle = "${audioTracks.size} tracks", onClose = onClose)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPickAudioFile,
                colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Sound / Music")
            }
            audioTracks.forEach { tr ->
                FilterChip(
                    selected = true,
                    onClick = { onDeleteAudio(tr.id) },
                    label = { Text("${tr.title.take(14)} ×", style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

@Composable
fun AspectBackgroundToolSheet(
    isAspectMode: Boolean,
    aspectPreset: AspectPreset,
    canvasBgArgb: Long,
    onSelectAspect: (AspectPreset) -> Unit,
    onSelectBg: (Long) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 14.dp)) {
        SheetHeader(title = if (isAspectMode) "Aspect Ratio" else "Canvas Background", onClose = onClose)
        if (isAspectMode) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectPreset.entries.forEach { ratio ->
                    FilterChip(
                        selected = aspectPreset == ratio,
                        onClick = { onSelectAspect(ratio) },
                        label = { Text(ratio.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                    )
                }
            }
        } else {
            val colors = listOf(0xFF05060AL, 0xFF13141FL, 0xFF1E293BL, 0xFF2D1B4EL, 0xFF0E3F34L, 0xFFF4F5FAL)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                colors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (canvasBgArgb == c) 2.5.dp else 1.dp, Color.White, CircleShape)
                            .clickable { onSelectBg(c) },
                    )
                }
            }
        }
    }
}
