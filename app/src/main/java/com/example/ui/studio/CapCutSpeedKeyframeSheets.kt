package com.example.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CropAspect
import com.example.data.model.CropSettings
import com.example.data.model.CurveSpeedPreset
import com.example.data.model.EasingGraph
import com.example.data.model.MaskShape
import com.example.data.model.MaskSettings
import com.example.data.model.MotionBlurDirection
import com.example.data.model.MotionBlurSettings
import com.example.data.model.SmoothReverseConfig
import com.example.data.model.SpeedCurvePoint
import com.example.data.model.SpeedMode
import com.example.data.model.SpeedSettings
import com.example.data.model.StabilizeMode
import com.example.data.model.TransformSettings
import com.example.data.model.VelocityPreset
import com.example.data.model.VideoClipSegment
import com.example.data.model.VolumeKeyframeEvaluator
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LunaMint
import com.example.ui.theme.LunaMintContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun SheetHeader(
    title: String,
    subtitle: String? = null,
    onReset: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (onReset != null) {
            TextButton(onClick = onReset) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = CyberCyan, maxLines = 1)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Check, contentDescription = "Apply and close", tint = LunaMint)
        }
    }
}

@Composable
fun SpeedToolSheet(
    clip: VideoClipSegment,
    onUpdateSpeed: (SpeedSettings) -> Unit,
    onClose: () -> Unit,
) {
    val speed = clip.speed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
    ) {
        val baseSec = clip.baseDurationMs() / 1000f
        val newSec = clip.timelineDurationMs() / 1000f
        val durLabel = String.format(Locale.US, "Duration: %.1fs -> %.1fs", baseSec, newSec)
        SheetHeader(
            title = "Speed",
            subtitle = durLabel,
            onReset = { onUpdateSpeed(SpeedSettings()) },
            onClose = onClose,
        )

        // Sub-tabs: Normal | Curve | Velocity effects
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedMode.entries.forEach { m ->
                FilterChip(
                    selected = speed.mode == m,
                    onClick = { onUpdateSpeed(speed.copy(mode = m)) },
                    label = { Text(m.label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LunaMintContainer,
                        selectedLabelColor = LunaMint,
                    ),
                )
            }
        }

        when (speed.mode) {
            SpeedMode.NORMAL -> {
                // Logarithmic mapping [0..1] <-> [0.1x .. 100x] (0.1, 1.0=0.33, 10=0.66, 100=1.0)
                val pos = ((ln(speed.normalSpeed.coerceIn(0.1f, 100f).toDouble()) - ln(0.1)) / (ln(100.0) - ln(0.1))).toFloat()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Speed Multiplier", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text(
                            text = if (speed.normalSpeed >= 10f) "${speed.normalSpeed.roundToInt()}x"
                            else String.format(Locale.US, "%.1fx", speed.normalSpeed),
                            style = MaterialTheme.typography.titleSmall,
                            color = CyberCyan,
                        )
                    }
                    Slider(
                        value = pos,
                        onValueChange = { p ->
                            val raw = (0.1 * (1000.0).pow(p.toDouble())).toFloat().coerceIn(0.1f, 100f)
                            val snapped = if (raw < 2f) (raw * 10f).roundToInt() / 10f else raw.roundToInt().toFloat()
                            onUpdateSpeed(speed.copy(normalSpeed = snapped))
                        },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = CyberCyan),
                        modifier = Modifier.testTag("speed_normal_slider"),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("0.1x", "1x", "2x", "5x", "10x", "100x").forEach { tick ->
                            Text(tick, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Make it smoother (Smooth slow-mo)", style = MaterialTheme.typography.labelLarge, color = Color.White)
                            if (speed.smoothSlowMo) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !speed.smoothSlowMoBetterQuality,
                                        onClick = { onUpdateSpeed(speed.copy(smoothSlowMoBetterQuality = false)) },
                                        label = { Text("Faster process", style = MaterialTheme.typography.labelSmall) },
                                    )
                                    FilterChip(
                                        selected = speed.smoothSlowMoBetterQuality,
                                        onClick = { onUpdateSpeed(speed.copy(smoothSlowMoBetterQuality = true)) },
                                        label = { Text("Better quality", style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = speed.smoothSlowMo,
                            onCheckedChange = { onUpdateSpeed(speed.copy(smoothSlowMo = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBackground, checkedTrackColor = LunaMint),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpdateSpeed(speed.copy(changeAudioPitch = !speed.changeAudioPitch)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = speed.changeAudioPitch,
                            onCheckedChange = { onUpdateSpeed(speed.copy(changeAudioPitch = it)) },
                            colors = CheckboxDefaults.colors(checkedColor = LunaMint),
                        )
                        Text("Change audio pitch", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }

            SpeedMode.CURVE -> {
                val ctrl = speed.resolvedCurveControlPoints()
                var selectedIdx by remember(ctrl.size) { mutableIntStateOf(0) }
                val safeSelIdx = selectedIdx.coerceIn(0, (ctrl.size - 1).coerceAtLeast(0))
                val latestCtrl by rememberUpdatedState(ctrl)
                val latestSpeed by rememberUpdatedState(speed)

                fun speedToNormY(spd: Float): Float =
                    ((ln(spd.coerceIn(0.1f, 10f).toDouble()) - ln(0.1)) / (ln(10.0) - ln(0.1))).toFloat().coerceIn(0f, 1f)

                fun normYToSpeed(norm: Float): Float {
                    val raw = (0.1 * (100.0).pow(norm.coerceIn(0f, 1f).toDouble())).toFloat().coerceIn(0.1f, 10f)
                    return (raw * 10f).roundToInt() / 10f
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(262.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CurveSpeedPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = speed.curvePreset == preset,
                                onClick = {
                                    val pts = preset.points
                                    val cPts = pts.mapIndexed { idx, v ->
                                        SpeedCurvePoint(idx / (pts.size - 1f).coerceAtLeast(1f), v)
                                    }
                                    onUpdateSpeed(
                                        speed.copy(
                                            curvePreset = preset,
                                            curvePoints = pts,
                                            curveControlPoints = cPts,
                                        )
                                    )
                                },
                                label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LunaMintContainer,
                                    selectedLabelColor = LunaMint,
                                ),
                            )
                        }
                    }

                    // Interactive 0.1X .. 10.0X Bezier Curve Canvas with Drag & Beat Points
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianSurfaceContainer)
                            .pointerInput(ctrl.size) {
                                detectTapGestures { offset ->
                                    val list = latestCtrl
                                    if (list.isEmpty()) return@detectTapGestures
                                    val padPx = 14.dp.toPx()
                                    val drawW = (size.width - padPx * 2f).coerceAtLeast(1f)
                                    val drawH = (size.height - padPx * 2f).coerceAtLeast(1f)
                                    var bestIdx = 0
                                    var bestDist = Float.MAX_VALUE
                                    list.forEachIndexed { idx, pt ->
                                        val px = padPx + pt.position01 * drawW
                                        val py = padPx + (1f - speedToNormY(pt.speed)) * drawH
                                        val d = (offset.x - px) * (offset.x - px) + (offset.y - py) * (offset.y - py)
                                        if (d < bestDist) {
                                            bestDist = d
                                            bestIdx = idx
                                        }
                                    }
                                    selectedIdx = bestIdx
                                }
                            }
                            .pointerInput(ctrl.size) {
                                var dragIdx = -1
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val list = latestCtrl
                                        if (list.isEmpty()) return@detectDragGestures
                                        val padPx = 14.dp.toPx()
                                        val drawW = (size.width - padPx * 2f).coerceAtLeast(1f)
                                        val drawH = (size.height - padPx * 2f).coerceAtLeast(1f)
                                        var bestIdx = 0
                                        var bestDist = Float.MAX_VALUE
                                        list.forEachIndexed { idx, pt ->
                                            val px = padPx + pt.position01 * drawW
                                            val py = padPx + (1f - speedToNormY(pt.speed)) * drawH
                                            val d = (start.x - px) * (start.x - px) + (start.y - py) * (start.y - py)
                                            if (d < bestDist) {
                                                bestDist = d
                                                bestIdx = idx
                                            }
                                        }
                                        dragIdx = bestIdx
                                        selectedIdx = bestIdx
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val list = latestCtrl.toMutableList()
                                        if (dragIdx !in list.indices) return@detectDragGestures
                                        val padPx = 14.dp.toPx()
                                        val drawW = (size.width - padPx * 2f).coerceAtLeast(1f)
                                        val drawH = (size.height - padPx * 2f).coerceAtLeast(1f)
                                        val normY = 1f - ((change.position.y - padPx) / drawH).coerceIn(0f, 1f)
                                        val newSpd = normYToSpeed(normY)
                                        val curPt = list[dragIdx]
                                        val newPos = if (dragIdx == 0) {
                                            0f
                                        } else if (dragIdx == list.lastIndex) {
                                            1f
                                        } else {
                                            val minP = list[dragIdx - 1].position01 + 0.04f
                                            val maxP = list[dragIdx + 1].position01 - 0.04f
                                            ((change.position.x - padPx) / drawW).coerceIn(minP, maxP.coerceAtLeast(minP))
                                        }
                                        list[dragIdx] = curPt.copy(position01 = newPos, speed = newSpd)
                                        onUpdateSpeed(
                                            latestSpeed.copy(
                                                curvePreset = CurveSpeedPreset.CUSTOM,
                                                curvePoints = list.map { it.speed },
                                                curveControlPoints = list,
                                            )
                                        )
                                    },
                                )
                            }
                            .testTag("speed_curve_canvas"),
                    ) {
                        val pad = 14.dp.toPx()
                        val drawW = (size.width - pad * 2f).coerceAtLeast(1f)
                        val drawH = (size.height - pad * 2f).coerceAtLeast(1f)
                        val baseOneY = pad + (1f - speedToNormY(1.0f)) * drawH
                        drawLine(OutlineSubtle, Offset(pad, baseOneY), Offset(pad + drawW, baseOneY), 1.dp.toPx())

                        val path = Path()
                        val steps = 40
                        for (s in 0..steps) {
                            val t = s / steps.toFloat()
                            val spd = speed.speedAtProgress(t)
                            val x = pad + t * drawW
                            val y = pad + (1f - speedToNormY(spd)) * drawH
                            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, CyberCyan, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))

                        ctrl.forEachIndexed { i, pt ->
                            val cx = pad + pt.position01 * drawW
                            val cy = pad + (1f - speedToNormY(pt.speed)) * drawH
                            val isSel = i == safeSelIdx
                            drawCircle(
                                color = if (isSel) LunaMint else Color.White,
                                radius = if (isSel) 6.5.dp.toPx() else 4.5.dp.toPx(),
                                center = Offset(cx, cy),
                            )
                        }
                    }

                    // Beat Point Chips
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ctrl.forEachIndexed { idx, pt ->
                            FilterChip(
                                selected = safeSelIdx == idx,
                                onClick = { selectedIdx = idx },
                                label = {
                                    Text(
                                        "P${idx + 1} ${String.format(Locale.US, "%.1fx", pt.speed)}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }

                    // Actions: Add Point / Delete Point / Mirror / Smooth Bezier / Smooth Slow-Mo
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                val list = ctrl.toMutableList()
                                val insertAfter = safeSelIdx.coerceIn(0, (list.lastIndex - 1).coerceAtLeast(0))
                                val nextIdx = (insertAfter + 1).coerceAtMost(list.lastIndex)
                                val midPos = ((list[insertAfter].position01 + list[nextIdx].position01) * 0.5f)
                                    .coerceIn(0.05f, 0.95f)
                                val midSpd = speed.speedAtProgress(midPos)
                                val updated = (list + SpeedCurvePoint(midPos, midSpd)).sortedBy { it.position01 }
                                selectedIdx = updated.indexOfFirst { abs(it.position01 - midPos) < 0.002f }.coerceAtLeast(0)
                                onUpdateSpeed(
                                    speed.copy(
                                        curvePreset = CurveSpeedPreset.CUSTOM,
                                        curvePoints = updated.map { it.speed },
                                        curveControlPoints = updated,
                                    )
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp).testTag("speed_curve_add_point"),
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Point", style = MaterialTheme.typography.labelSmall)
                        }
                        if (ctrl.size > 2) {
                            OutlinedButton(
                                onClick = {
                                    val removeIdx = safeSelIdx.coerceIn(1, ctrl.lastIndex - 1)
                                    val updated = ctrl.filterIndexed { i, _ -> i != removeIdx }
                                    selectedIdx = (removeIdx - 1).coerceAtLeast(0)
                                    onUpdateSpeed(
                                        speed.copy(
                                            curvePreset = CurveSpeedPreset.CUSTOM,
                                            curvePoints = updated.map { it.speed },
                                            curveControlPoints = updated,
                                        )
                                    )
                                },
                                enabled = safeSelIdx in 1 until ctrl.lastIndex,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp).testTag("speed_curve_delete_point"),
                            ) {
                                Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Delete", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        FilterChip(
                            selected = false,
                            onClick = { onUpdateSpeed(speed.mirrored()) },
                            leadingIcon = { Icon(Icons.Filled.SwapHoriz, null, tint = CyberCyan, modifier = Modifier.size(15.dp)) },
                            label = { Text("Mirror", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("speed_curve_mirror"),
                        )
                        FilterChip(
                            selected = speed.curveBezierSmooth,
                            onClick = {
                                onUpdateSpeed(
                                    speed.copy(
                                        curveBezierSmooth = !speed.curveBezierSmooth,
                                        curveControlPoints = ctrl,
                                    )
                                )
                            },
                            label = { Text("Smooth Bezier", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                        )
                        FilterChip(
                            selected = speed.smoothSlowMo && speed.smoothSlowMoBetterQuality,
                            onClick = {
                                val next = !(speed.smoothSlowMo && speed.smoothSlowMoBetterQuality)
                                onUpdateSpeed(speed.copy(smoothSlowMo = next, smoothSlowMoBetterQuality = next))
                            },
                            label = { Text("Better Quality", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                        )
                    }

                    val curPt = ctrl.getOrElse(safeSelIdx) { SpeedCurvePoint(0f, 1f) }
                    val curSliderNorm = speedToNormY(curPt.speed)
                    LabeledSlider(
                        label = "Point P${safeSelIdx + 1} Speed (0.1x–10.0x)",
                        value = curSliderNorm,
                        range = 0f..1f,
                        readout = String.format(Locale.US, "%.1fx", curPt.speed),
                    ) { normVal ->
                        val nextSpd = normYToSpeed(normVal)
                        val updated = ctrl.toMutableList()
                        updated[safeSelIdx] = curPt.copy(speed = nextSpd)
                        onUpdateSpeed(
                            speed.copy(
                                curvePreset = CurveSpeedPreset.CUSTOM,
                                curvePoints = updated.map { it.speed },
                                curveControlPoints = updated,
                            )
                        )
                    }
                }
            }

            SpeedMode.VELOCITY -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VelocityPreset.entries.forEach { vp ->
                        val active = speed.velocityPreset == vp
                        Column(
                            modifier = Modifier
                                .width(88.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) LunaMintContainer else ObsidianSurfaceContainer)
                                .border(1.dp, if (active) LunaMint else OutlineSubtle, RoundedCornerShape(12.dp))
                                .clickable { onUpdateSpeed(speed.copy(velocityPreset = vp)) }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = vp.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) LunaMint else Color.White,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeyframeGraphsSheet(
    currentGraph: EasingGraph,
    onSelectGraph: (EasingGraph) -> Unit,
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
            title = "Graphs",
            subtitle = "Easing curve between keyframes (${currentGraph.label})",
            onClose = onClose,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EasingGraph.entries.forEach { graph ->
                val selected = currentGraph == graph
                Column(
                    modifier = Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) LunaMintContainer else ObsidianSurfaceContainer)
                        .border(1.5.dp, if (selected) LunaMint else OutlineSubtle, RoundedCornerShape(10.dp))
                        .clickable { onSelectGraph(graph) }
                        .padding(6.dp)
                        .testTag("graph_${graph.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianBackground)
                            .padding(6.dp),
                    ) {
                        val path = Path()
                        val steps = 20
                        for (i in 0..steps) {
                            val t = i / steps.toFloat()
                            val v = graph.evaluate(t).coerceIn(-0.25f, 1.35f)
                            val x = t * size.width
                            val y = size.height * (1f - (v * 0.72f + 0.14f))
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = if (selected) LunaMint else Color.White,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = graph.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (selected) LunaMint else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun TransformToolSheet(
    targetLabel: String,
    evaluatedTransform: TransformSettings,
    opacity: Float,
    showMirror: Boolean,
    showOpacity: Boolean,
    keyframeModeEnabled: Boolean,
    keyframeCount: Int,
    isOnKeyframe: Boolean,
    hasSelectedKeyframe: Boolean,
    onToggleKeyframeMode: () -> Unit,
    onAddKeyframe: () -> Unit,
    onDeleteKeyframe: () -> Unit,
    onDeleteAllKeyframes: () -> Unit,
    onFillCanvas: () -> Unit,
    onReset: () -> Unit,
    onUpdateTransform: (TransformSettings, Float) -> Unit,
    onClose: () -> Unit,
) {
    val tr = evaluatedTransform
    val latestTr by androidx.compose.runtime.rememberUpdatedState(tr)
    val latestOpacity by androidx.compose.runtime.rememberUpdatedState(opacity)
    val maxZoomRange = maxOf(300f, ceil(tr.zoomPercent))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
    ) {
        SheetHeader(
            title = "Transform • $targetLabel",
            subtitle = buildString {
                append("Zoom ${tr.zoomPercent.roundToInt()}% • ${tr.rotateDeg.roundToInt()}°")
                if (keyframeCount > 0) append(" • $keyframeCount KFs")
            },
            onReset = onReset,
            onClose = onClose,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(258.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = false,
                    onClick = onFillCanvas,
                    label = { Text("Fill Canvas") },
                    leadingIcon = { Icon(Icons.Filled.Fullscreen, null, tint = CyberCyan, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("transform_fill_canvas"),
                )
                FilterChip(
                    selected = false,
                    onClick = { onUpdateTransform(tr.copy(rotateDeg = (tr.rotateDeg + 90f) % 360f), opacity) },
                    label = { Text("Rotate +90°") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("transform_rotate_90"),
                )
                if (showMirror) {
                    FilterChip(
                        selected = tr.mirrorHorizontal,
                        onClick = { onUpdateTransform(tr.copy(mirrorHorizontal = !tr.mirrorHorizontal), opacity) },
                        label = { Text("Mirror") },
                        leadingIcon = { Icon(Icons.Filled.Flip, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                        modifier = Modifier.testTag("transform_mirror"),
                    )
                }
            }

            // Keyframe ON/OFF Toggle (Default = OFF) + Add/Delete/Delete All Keyframe Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = keyframeModeEnabled,
                    onClick = onToggleKeyframeMode,
                    label = {
                        Text(
                            if (keyframeModeEnabled) "Keyframe ON" else "Keyframe OFF",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.22f),
                        selectedLabelColor = CyberCyan,
                    ),
                    modifier = Modifier.testTag("transform_keyframe_toggle"),
                )
                if (keyframeModeEnabled || keyframeCount > 0) {
                    OutlinedButton(
                        onClick = onAddKeyframe,
                        enabled = keyframeModeEnabled,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp).testTag("transform_add_keyframe"),
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Keyframe", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onDeleteKeyframe,
                        enabled = isOnKeyframe || hasSelectedKeyframe,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp).testTag("transform_delete_keyframe"),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Delete KF", style = MaterialTheme.typography.labelSmall)
                    }
                    if (keyframeCount > 0) {
                        TextButton(
                            onClick = onDeleteAllKeyframes,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp).testTag("transform_delete_all_keyframes"),
                        ) {
                            Text("Delete All", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            LabeledSlider("Position X", tr.posX, -1f..1f, String.format(Locale.US, "%.2f", tr.posX)) {
                onUpdateTransform(latestTr.copy(posX = it), latestOpacity)
            }
            LabeledSlider("Position Y", tr.posY, -1f..1f, String.format(Locale.US, "%.2f", tr.posY)) {
                onUpdateTransform(latestTr.copy(posY = it), latestOpacity)
            }
            LabeledSlider(
                "Zoom",
                tr.zoomPercent.coerceIn(100f, maxZoomRange),
                100f..maxZoomRange,
                "${tr.zoomPercent.roundToInt()}%",
            ) {
                onUpdateTransform(latestTr.copy(zoomPercent = it), latestOpacity)
            }
            val normalizedRot = (((tr.rotateDeg % 360f) + 360f) % 360f).let { if (it >= 359.95f) 0f else it }
            LabeledSlider("Rotate", normalizedRot, 0f..360f, "${normalizedRot.roundToInt()}°") {
                onUpdateTransform(latestTr.copy(rotateDeg = if (it >= 359.95f) 0f else it), latestOpacity)
            }
            if (showOpacity) {
                LabeledSlider("Opacity", (opacity * 100f).coerceIn(0f, 100f), 0f..100f, "${(opacity * 100f).roundToInt()}%") {
                    onUpdateTransform(latestTr, (it / 100f).coerceIn(0f, 1f))
                }
            }
        }
    }
}

@Composable
fun CropToolSheet(clip: VideoClipSegment, onUpdateCrop: (CropSettings) -> Unit, onClose: () -> Unit) {
    val crop = clip.crop
    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 12.dp)) {
        SheetHeader(title = "Crop", onReset = { onUpdateCrop(CropSettings()) }, onClose = onClose)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CropAspect.entries.forEach { a ->
                FilterChip(
                    selected = crop.aspect == a,
                    onClick = { onUpdateCrop(crop.copy(aspect = a)) },
                    label = { Text(a.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            LabeledSlider("Crop Rotate", crop.rotateDeg, -45f..45f, "${crop.rotateDeg.roundToInt()}°") {
                onUpdateCrop(crop.copy(rotateDeg = it))
            }
            LabeledSlider("Crop Zoom", crop.scaleCrop, 1f..2.5f, String.format(Locale.US, "%.2fx", crop.scaleCrop)) {
                onUpdateCrop(crop.copy(scaleCrop = it))
            }
        }
    }
}

@Composable
fun MaskToolSheet(clip: VideoClipSegment, onUpdateMask: (MaskSettings) -> Unit, onClose: () -> Unit) {
    val mask = clip.mask
    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 12.dp)) {
        SheetHeader(title = "Mask", subtitle = mask.shape.label, onReset = { onUpdateMask(MaskSettings()) }, onClose = onClose)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaskShape.entries.forEach { shape ->
                FilterChip(
                    selected = mask.shape == shape,
                    onClick = { onUpdateMask(mask.copy(shape = shape)) },
                    label = { Text(shape.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                )
            }
        }
        if (mask.shape != MaskShape.NONE) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                LabeledSlider("Size", mask.sizeScale, 0.2f..1.35f, "${(mask.sizeScale * 100).roundToInt()}%") {
                    onUpdateMask(mask.copy(sizeScale = it))
                }
                LabeledSlider("Feather", mask.feather, 0.01f..0.4f, "${(mask.feather * 100).roundToInt()}%") {
                    onUpdateMask(mask.copy(feather = it))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = mask.invert,
                        onClick = { onUpdateMask(mask.copy(invert = !mask.invert)) },
                        label = { Text("Invert Mask") },
                    )
                }
            }
        }
    }
}

@Composable
fun MotionBlurToolSheet(clip: VideoClipSegment, onUpdate: (MotionBlurSettings) -> Unit, onClose: () -> Unit) {
    val mb = clip.motionBlur
    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 12.dp)) {
        SheetHeader(title = "Motion Blur", onReset = { onUpdate(MotionBlurSettings()) }, onClose = onClose)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            LabeledSlider("Blur", mb.blurAmount, 0f..1f, "${(mb.blurAmount * 100).roundToInt()}") {
                onUpdate(mb.copy(enabled = it > 0.01f, blurAmount = it))
            }
            LabeledSlider("Blend", mb.blendAmount, 0f..1f, "${(mb.blendAmount * 100).roundToInt()}") {
                onUpdate(mb.copy(enabled = true, blendAmount = it))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MotionBlurDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = mb.direction == dir,
                        onClick = { onUpdate(mb.copy(enabled = true, direction = dir)) },
                        label = { Text(dir.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}

@Composable
fun StabilizeToolSheet(clip: VideoClipSegment, onUpdate: (StabilizeMode) -> Unit, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 14.dp)) {
        SheetHeader(title = "Stabilize", subtitle = clip.stabilize.label, onClose = onClose)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StabilizeMode.entries.forEach { m ->
                FilterChip(
                    selected = clip.stabilize == m,
                    onClick = { onUpdate(m) },
                    label = { Text(m.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LunaMintContainer, selectedLabelColor = LunaMint),
                )
            }
        }
    }
}

@Composable
fun VolumeToolSheet(
    clip: VideoClipSegment,
    localPlayheadMs: Long,
    selectedVolumeKeyframeId: String?,
    onUpdateVolume: (Int, Boolean, Boolean) -> Unit,
    onAddVolumeKeyframe: () -> Unit,
    onDeleteVolumeKeyframe: () -> Unit,
    onDeleteAllVolumeKeyframes: () -> Unit,
    onClose: () -> Unit,
) {
    val evaluatedVol = VolumeKeyframeEvaluator.evaluate(
        keyframes = clip.volumeKeyframes,
        localTimeMs = localPlayheadMs,
        baseVolumePercent = clip.volumePercent,
    )
    val isOnVolKf = clip.volumeKeyframes.any {
        it.id == selectedVolumeKeyframeId || abs(it.timeMs - localPlayheadMs) < 140L
    }
    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 12.dp)) {
        SheetHeader(
            title = "Volume",
            subtitle = if (clip.volumeKeyframes.isNotEmpty()) "${evaluatedVol}% • ${clip.volumeKeyframes.size} Volume KFs" else "${evaluatedVol}%",
            onClose = onClose,
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LabeledSlider("Volume (0–1000%)", evaluatedVol.toFloat(), 0f..1000f, "${evaluatedVol}%") {
                onUpdateVolume(it.roundToInt(), clip.reduceNoise, false)
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
                    modifier = Modifier.height(32.dp).testTag("clip_add_volume_keyframe"),
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Volume Keyframe ◆", style = MaterialTheme.typography.labelSmall)
                }
                if (clip.volumeKeyframes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onDeleteVolumeKeyframe,
                        enabled = isOnVolKf,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp).testTag("clip_delete_volume_keyframe"),
                    ) {
                        Text("Delete KF", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = onDeleteAllVolumeKeyframes,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp).testTag("clip_delete_all_volume_keyframes"),
                    ) {
                        Text("Clear All", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Reduce noise", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Switch(
                    checked = clip.reduceNoise,
                    onCheckedChange = { onUpdateVolume(evaluatedVol, it, false) },
                )
            }
        }
    }
}

@Composable
fun OpacityToolSheet(clip: VideoClipSegment, onUpdateOpacity: (Float) -> Unit, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).navigationBarsPadding().padding(bottom = 12.dp)) {
        SheetHeader(title = "Opacity", subtitle = "${(clip.opacity * 100).roundToInt()}%", onReset = { onUpdateOpacity(1f) }, onClose = onClose)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            LabeledSlider("Opacity", clip.opacity * 100f, 0f..100f, "${(clip.opacity * 100).roundToInt()}%") {
                onUpdateOpacity(it / 100f)
            }
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(readout, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = LunaMint),
            modifier = Modifier.height(34.dp),
        )
    }
}

@Composable
fun ReverseClipsSheet(
    clips: List<VideoClipSegment>,
    clipThumbnails: Map<String, List<ImageBitmap>>,
    reverseProgress: ReverseProgressState?,
    onReverseSelected: (clipIds: List<String>, reverseAudio: Boolean) -> Unit,
    onReverseAll: (reverseAudio: Boolean) -> Unit,
    onSmoothReverse: () -> Unit,
    onClose: () -> Unit,
) {
    val importedClips = remember(clips) {
        clips.filter { !it.sourceUriString.isNullOrBlank() }.sortedBy { it.timelineStartMs }
    }
    var reverseAudio by remember { mutableStateOf(true) }
    val checkedIds = remember(importedClips) {
        mutableStateMapOf<String, Boolean>().apply {
            importedClips.forEach { put(it.id, !it.isReversed) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .testTag("reverse_clips_sheet"),
    ) {
        SheetHeader(
            title = "Reverse Clip",
            subtitle = "${importedClips.size} imported clip(s)",
            onClose = onClose,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianSurfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Reverse Audio Too",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Switch(
                    checked = reverseAudio,
                    onCheckedChange = { reverseAudio = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBackground,
                        checkedTrackColor = CyberCyan,
                    ),
                    modifier = Modifier.testTag("reverse_audio_switch"),
                )
            }

            if (reverseProgress != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianSurfaceHigh)
                        .border(1.dp, CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Reversing ${reverseProgress.clipName} (${reverseProgress.currentClipIndex}/${reverseProgress.totalClips})",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${reverseProgress.percent}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberCyan,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (reverseProgress.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                        color = CyberCyan,
                        trackColor = ObsidianSurfaceContainer,
                    )
                    Text(
                        text = reverseProgress.statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }

            if (importedClips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianSurfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Import at least one video clip to reverse",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    importedClips.forEachIndexed { idx, clip ->
                        val isChecked = checkedIds[clip.id] == true
                        val thumb = clipThumbnails[clip.id]?.firstOrNull()
                        val durSec = clip.timelineDurationMs() / 1000f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianSurfaceContainer)
                                .border(
                                    width = 1.dp,
                                    color = if (isChecked) CyberCyan.copy(alpha = 0.65f) else OutlineSubtle,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { checkedIds[clip.id] = !isChecked }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("reverse_clip_row_$idx"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 48.dp, height = 36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ObsidianSurfaceHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb,
                                        contentDescription = clip.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.MovieFilter,
                                        contentDescription = null,
                                        tint = LunaMint,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = clip.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1fs", durSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                    )
                                    if (clip.isReversed) {
                                        Surface(
                                            color = CyberCyan.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                text = "REVERSED",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                color = CyberCyan,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checkedIds[clip.id] = it },
                                colors = CheckboxDefaults.colors(checkedColor = CyberCyan, checkmarkColor = ObsidianBackground),
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onSmoothReverse()
                    onClose()
                },
                enabled = reverseProgress == null && importedClips.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
                modifier = Modifier.fillMaxWidth().testTag("smooth_reverse_from_sheet_button"),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Smooth Reverse (10x → 0.1x ⇄ Reversed)",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val selectedIds = importedClips.filter { checkedIds[it.id] == true }.map { it.id }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onReverseSelected(selectedIds, reverseAudio) },
                    enabled = reverseProgress == null && selectedIds.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f).testTag("reverse_selected_button"),
                ) {
                    Text("Reverse Selected", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = { onReverseAll(reverseAudio) },
                    enabled = reverseProgress == null && importedClips.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBackground),
                    modifier = Modifier.weight(1f).testTag("reverse_all_button"),
                ) {
                    Text(
                        text = "Reverse All Clips",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun SmoothReverseSettingsSheet(
    clip: VideoClipSegment,
    onUpdateConfig: (groupId: String, config: SmoothReverseConfig) -> Unit,
    onUndo: () -> Unit,
    onClose: () -> Unit,
) {
    val groupId = clip.smoothReverseGroupId
    val initCfg = clip.smoothReverseConfig ?: SmoothReverseConfig(segmentDurationMs = clip.baseDurationMs())
    var zoomPct by remember(clip.id, initCfg) { androidx.compose.runtime.mutableFloatStateOf(initCfg.zoomPercent) }
    var startSpeed by remember(clip.id, initCfg) { androidx.compose.runtime.mutableFloatStateOf(initCfg.startSpeed) }
    var endSpeed by remember(clip.id, initCfg) { androidx.compose.runtime.mutableFloatStateOf(initCfg.endSpeed) }
    var segMs by remember(clip.id, initCfg) { androidx.compose.runtime.mutableFloatStateOf(initCfg.segmentDurationMs.toFloat()) }
    val maxSegMs = (initCfg.originalTrimEndMs - initCfg.originalTrimStartMs).coerceIn(500L, 1_500L).toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .testTag("smooth_reverse_settings_sheet"),
    ) {
        SheetHeader(
            title = "Smooth Auto-Reverse",
            subtitle = String.format(
                Locale.US,
                "%.1fx → %.1fx ⇄ %.0f%% Zoom • %.2fs",
                startSpeed,
                endSpeed,
                zoomPct,
                segMs / 1000f,
            ),
            onClose = onClose,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LabeledSlider(
                label = "Zoom Target",
                value = zoomPct.coerceIn(100f, 240f),
                range = 100f..240f,
                readout = "${zoomPct.roundToInt()}%",
            ) { nextZoom ->
                zoomPct = nextZoom.roundToInt().toFloat()
                if (groupId != null) {
                    onUpdateConfig(
                        groupId,
                        initCfg.copy(
                            zoomPercent = zoomPct,
                            startSpeed = startSpeed,
                            endSpeed = endSpeed,
                            segmentDurationMs = segMs.roundToInt().toLong(),
                        ),
                    )
                }
            }
            LabeledSlider(
                label = "Peak Start / Return Speed",
                value = startSpeed.coerceIn(1f, 10f),
                range = 1f..10f,
                readout = String.format(Locale.US, "%.1fx", startSpeed),
            ) { nextStart ->
                startSpeed = (nextStart * 10f).roundToInt() / 10f
                if (groupId != null) {
                    onUpdateConfig(
                        groupId,
                        initCfg.copy(
                            zoomPercent = zoomPct,
                            startSpeed = startSpeed,
                            endSpeed = endSpeed,
                            segmentDurationMs = segMs.roundToInt().toLong(),
                        ),
                    )
                }
            }
            LabeledSlider(
                label = "Join Slow-Mo Speed",
                value = endSpeed.coerceIn(0.1f, 1.0f),
                range = 0.1f..1.0f,
                readout = String.format(Locale.US, "%.1fx", endSpeed),
            ) { nextEnd ->
                endSpeed = (nextEnd * 10f).roundToInt() / 10f
                if (groupId != null) {
                    onUpdateConfig(
                        groupId,
                        initCfg.copy(
                            zoomPercent = zoomPct,
                            startSpeed = startSpeed,
                            endSpeed = endSpeed,
                            segmentDurationMs = segMs.roundToInt().toLong(),
                        ),
                    )
                }
            }
            LabeledSlider(
                label = "Segment Length (source)",
                value = segMs.coerceIn(200f, maxSegMs),
                range = 200f..maxSegMs,
                readout = String.format(Locale.US, "%.2fs", segMs / 1000f),
            ) { nextLen ->
                segMs = ((nextLen / 20f).roundToInt() * 20).toFloat().coerceIn(200f, maxSegMs)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onUndo()
                        onClose()
                    },
                    modifier = Modifier.weight(1f).testTag("smooth_reverse_undo_button"),
                ) {
                    Text("Undo Effect", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = {
                        if (groupId != null) {
                            onUpdateConfig(
                                groupId,
                                initCfg.copy(
                                    zoomPercent = zoomPct,
                                    startSpeed = startSpeed,
                                    endSpeed = endSpeed,
                                    segmentDurationMs = segMs.roundToInt().toLong(),
                                ),
                            )
                        }
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LunaMint, contentColor = ObsidianBackground),
                    modifier = Modifier.weight(1f).testTag("smooth_reverse_apply_button"),
                ) {
                    Text("Apply Changes", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}


