package com.example.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.model.AudioTrackItem
import com.example.data.model.ClipTimelineSpan
import com.example.data.model.OverlayTrackItem
import com.example.data.model.TextTrackItem
import com.example.data.model.TransitionSpec
import com.example.data.model.TransitionType
import com.example.data.model.VideoEffectLayer
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LunaMint
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Packs non-overlapping items (including adjacent split halves) onto the same horizontal lane
 * so Split results remain cleanly side-by-side instead of stacking on separate vertical rows.
 */
fun <T> assignTrackLanes(
    items: List<T>,
    startOf: (T) -> Long,
    endOf: (T) -> Long,
): List<List<T>> {
    if (items.isEmpty()) return emptyList()
    val sorted = items.sortedBy { startOf(it) }
    val lanes = mutableListOf<MutableList<T>>()
    val laneEnds = mutableListOf<Long>()
    for (item in sorted) {
        val s = startOf(item)
        val e = endOf(item).coerceAtLeast(s + 100L)
        var placed = false
        for (i in lanes.indices) {
            if (laneEnds[i] <= s + 25L) {
                lanes[i].add(item)
                laneEnds[i] = e
                placed = true
                break
            }
        }
        if (!placed) {
            lanes.add(mutableListOf(item))
            laneEnds.add(e)
        }
    }
    return lanes
}

@Composable
fun CapCutTimelineArea(
    spans: List<ClipTimelineSpan>,
    transitions: List<TransitionSpec>,
    effectLayers: List<VideoEffectLayer>,
    overlays: List<OverlayTrackItem>,
    audioTracks: List<AudioTrackItem>,
    textTracks: List<TextTrackItem>,
    filmstrip: List<ImageBitmap>,
    hasEndingCard: Boolean,
    playheadMs: Long,
    totalDurationMs: Long,
    timelineZoom: Float,
    selectedClipId: String?,
    selectedOverlayId: String?,
    selectedEffectId: String?,
    selectedTextId: String?,
    selectedAudioId: String?,
    selectedKeyframeId: String?,
    selectedVolumeKeyframeId: String?,
    onUpdatePlayhead: (Long) -> Unit,
    onUpdateZoom: (Float) -> Unit,
    onCommitHistory: () -> Unit,
    onSelectClip: (String?) -> Unit,
    onSelectClipKeyframe: (clipId: String, kfId: String) -> Unit,
    onSelectTextKeyframe: (textId: String, kfId: String) -> Unit,
    onSelectClipVolumeKeyframe: (clipId: String, kfId: String) -> Unit,
    onSelectAudioVolumeKeyframe: (audioId: String, kfId: String) -> Unit,
    onMoveClip: (clipId: String, deltaMs: Long) -> Unit,
    onMoveOverlay: (overlayId: String, deltaMs: Long) -> Unit,
    onMoveEffect: (effectId: String, deltaMs: Long) -> Unit,
    onMoveText: (textId: String, deltaMs: Long) -> Unit,
    onMoveAudio: (audioId: String, deltaMs: Long) -> Unit,
    onTrimClipEdge: (clipId: String, isStart: Boolean, deltaTimelineMs: Long) -> Unit,
    onTrimOverlayEdge: (overlayId: String, isStart: Boolean, deltaMs: Long) -> Unit,
    onTrimEffectEdge: (effectId: String, isStart: Boolean, deltaMs: Long) -> Unit,
    onTrimTextEdge: (textId: String, isStart: Boolean, deltaMs: Long) -> Unit,
    onTrimAudioEdge: (audioId: String, isStart: Boolean, deltaMs: Long) -> Unit,
    onToggleMuteClip: (String) -> Unit,
    onOpenTransition: (afterClipId: String) -> Unit,
    onAddClipClick: () -> Unit,
    onToggleEndingCard: () -> Unit,
    onOpenSheet: (ActiveEditorSheet) -> Unit,
    onSelectOverlay: (String?) -> Unit,
    onSelectEffect: (String?) -> Unit,
    onSelectText: (String?) -> Unit,
    onSelectAudio: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pxPerSec = with(density) { (48.dp * timelineZoom).toPx() }.coerceAtLeast(24f)
    val latestPlayheadMs by rememberUpdatedState(playheadMs)
    val latestTotalDurationMs by rememberUpdatedState(totalDurationMs)
    val latestZoom by rememberUpdatedState(timelineZoom)
    val latestPxPerSec by rememberUpdatedState(pxPerSec)
    val latestOnUpdatePlayhead by rememberUpdatedState(onUpdatePlayhead)
    val latestOnUpdateZoom by rememberUpdatedState(onUpdateZoom)

    val videoLanes = remember(spans) {
        assignTrackLanes(spans, { it.startMs }, { it.endMs }).ifEmpty { listOf(spans) }
    }
    val overlayLanes = remember(overlays) { assignTrackLanes(overlays, { it.startMs }, { it.endMs }) }
    val effectLanes = remember(effectLayers) { assignTrackLanes(effectLayers, { it.startMs }, { it.endMs }) }
    val textLanes = remember(textTracks) { assignTrackLanes(textTracks, { it.startMs }, { it.endMs }) }
    val audioLanes = remember(audioTracks) { assignTrackLanes(audioTracks, { it.startMs }, { it.endMs }) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBackground)
            .clipToBounds()
            // Unified multi-touch pinch zoom (2+ fingers) & unconsumed 1-finger scrub left/right
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var scrubAccumMs = 0f
                    var zoomVal = latestZoom
                    var activePlayhead = latestPlayheadMs
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoom = event.calculateZoom()
                            if (abs(zoom - 1f) > 0.005f) {
                                zoomVal = (zoomVal * zoom).coerceIn(0.45f, 3.8f)
                                latestOnUpdateZoom(zoomVal)
                                event.changes.forEach { it.consume() }
                            }
                        } else if (pressedCount == 1) {
                            val unconsumedMove = event.changes.firstOrNull { !it.isConsumed && it.positionChanged() }
                            if (unconsumedMove != null) {
                                val pan = event.calculatePan()
                                if (abs(pan.x) > abs(pan.y) && abs(pan.x) > 0.1f) {
                                    scrubAccumMs += (-pan.x / latestPxPerSec) * 1000f
                                    val wholeMs = scrubAccumMs.toLong()
                                    if (wholeMs != 0L) {
                                        scrubAccumMs -= wholeMs
                                        activePlayhead = (activePlayhead + wholeMs).coerceIn(0L, latestTotalDurationMs)
                                        latestOnUpdatePlayhead(activePlayhead)
                                    }
                                    unconsumedMove.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val timelineWidthPx = with(density) { maxWidth.toPx() }
        val halfWidthPx = timelineWidthPx * 0.5f
        val playheadXOffsetPx = halfWidthPx - (playheadMs / 1000f) * pxPerSec

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 10.dp),
        ) {
            // 1. Time Ruler row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(ObsidianBackground),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val secStep = if (timelineZoom < 0.8f) 2 else 1
                    val maxSec = (totalDurationMs / 1000L).toInt() + 4
                    for (s in 0..maxSec) {
                        val x = playheadXOffsetPx + s * pxPerSec
                        if (x in -20f..(size.width + 20f)) {
                            val major = s % secStep == 0
                            drawLine(
                                color = Color.White.copy(alpha = if (major) 0.5f else 0.2f),
                                start = Offset(x, size.height - if (major) 10.dp.toPx() else 5.dp.toPx()),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    }
                }
                val secStep = if (timelineZoom < 0.8f) 2 else 1
                val maxLabelSec = (totalDurationMs / 1000L).toInt() + 1
                for (s in 0..maxLabelSec step secStep) {
                    val xPx = playheadXOffsetPx + s * pxPerSec
                    if (xPx in 8f..(timelineWidthPx - 36f)) {
                        Text(
                            text = String.format(Locale.US, "00:%02d", s),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextSecondary,
                            modifier = Modifier.offset { IntOffset((xPx - 12f).roundToInt(), 2) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Main Video Track Lanes (long-press body to drag left/right, tap to select & reveal edge trims, '[|]' transitions at cuts)
            videoLanes.forEachIndexed { laneIdx, laneSpans ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                ) {
                    laneSpans.forEachIndexed { idx, span ->
                        val clip = span.clip
                        val isSel = clip.id == selectedClipId
                        val startPx = playheadXOffsetPx + (span.startMs / 1000f) * pxPerSec
                        val clipWidthDp = with(density) {
                            (((span.endMs - span.startMs).coerceAtLeast(150L) / 1000f) * pxPerSec).toDp().coerceAtLeast(24.dp)
                        }
                        var isDraggingBody by remember(clip.id) { mutableStateOf(false) }
                        val latestOnSelectClip by rememberUpdatedState(onSelectClip)
                        val latestOnCommit by rememberUpdatedState(onCommitHistory)
                        val latestOnMoveClip by rememberUpdatedState(onMoveClip)

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(startPx.roundToInt(), 2) }
                                .width(clipWidthDp)
                                .height(54.dp)
                                .zIndex(if (isDraggingBody) 3f else if (isSel) 2f else 1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianSurfaceHigh)
                                .border(
                                    width = if (isDraggingBody || isSel) 2.5.dp else 1.dp,
                                    color = when {
                                        isDraggingBody -> CyberCyan
                                        isSel -> Color.White
                                        else -> OutlineSubtle
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { latestOnSelectClip(if (isSel) null else clip.id) }
                                .pointerInput(clip.id) {
                                    var accumMs = 0f
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            isDraggingBody = true
                                            accumMs = 0f
                                            latestOnSelectClip(clip.id)
                                            latestOnCommit()
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            accumMs += (dragAmount.x / latestPxPerSec) * 1000f
                                            val wholeMs = accumMs.toLong()
                                            if (wholeMs != 0L) {
                                                accumMs -= wholeMs
                                                latestOnMoveClip(clip.id, wholeMs)
                                            }
                                        },
                                        onDragEnd = { isDraggingBody = false },
                                        onDragCancel = { isDraggingBody = false },
                                    )
                                }
                                .testTag("timeline_clip_${laneIdx}_$idx"),
                        ) {
                            // Filmstrip thumbnail tiles
                            Row(modifier = Modifier.fillMaxSize()) {
                                val tileCount = (clipWidthDp / 42.dp).roundToInt().coerceIn(2, 12)
                                for (t in 0 until tileCount) {
                                    if (filmstrip.isNotEmpty()) {
                                        val bmp = filmstrip[(t * filmstrip.size / tileCount).coerceIn(0, filmstrip.lastIndex)]
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(if (t % 2 == 0) ObsidianSurfaceContainer else ObsidianSurfaceHigh),
                                        )
                                    }
                                }
                            }

                            // Beat markers drawn right inside clip
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val dur = (span.endMs - span.startMs).coerceAtLeast(1L).toFloat()
                                clip.beatMarkersMs.forEach { bm ->
                                    val frac = (bm / dur).coerceIn(0.02f, 0.98f)
                                    drawCircle(
                                        color = StudioAmber,
                                        radius = 3.5.dp.toPx(),
                                        center = Offset(frac * size.width, size.height - 6.dp.toPx()),
                                    )
                                }
                            }

                            // Interactive Tap-Selectable Transform Keyframe Diamonds (Cyan) & Volume Keyframe Diamonds (Mint)
                            val clipDurF = (span.endMs - span.startMs).coerceAtLeast(1L).toFloat()
                            val clipWidthPx = with(density) { clipWidthDp.toPx() }
                            clip.keyframes.forEach { kf ->
                                val frac = (kf.timeMs / clipDurF).coerceIn(0.03f, 0.97f)
                                val cx = frac * clipWidthPx - with(density) { 12.dp.toPx() }
                                val isKfSelected = kf.id == selectedKeyframeId
                                TimelineDiamondPin(
                                    offsetXPx = cx,
                                    offsetYPx = with(density) { 15.dp.toPx() },
                                    fillColor = if (isKfSelected) StudioAmber else CyberCyan,
                                    isSelected = isKfSelected,
                                    tag = "clip_kf_diamond_${kf.id}",
                                    onClick = { onSelectClipKeyframe(clip.id, kf.id) },
                                )
                            }
                            clip.volumeKeyframes.forEach { vkf ->
                                val frac = (vkf.timeMs / clipDurF).coerceIn(0.03f, 0.97f)
                                val cx = frac * clipWidthPx - with(density) { 11.dp.toPx() }
                                val isVkSelected = vkf.id == selectedVolumeKeyframeId
                                TimelineDiamondPin(
                                    offsetXPx = cx,
                                    offsetYPx = with(density) { 32.dp.toPx() },
                                    fillColor = LunaMint,
                                    isSelected = isVkSelected,
                                    tag = "clip_vol_kf_diamond_${vkf.id}",
                                    onClick = { onSelectClipVolumeKeyframe(clip.id, vkf.id) },
                                )
                            }

                            // Speed/Freeze/Reverse pill badge
                            if (abs(clip.speed.effectiveSpeedMultiplier() - 1f) > 0.05f || clip.isFrozen || clip.isReversed) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ObsidianBackground.copy(alpha = 0.78f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = when {
                                            clip.isFrozen -> "Freeze"
                                            clip.isReversed -> "Rev"
                                            else -> String.format(Locale.US, "%.1fx", clip.speed.effectiveSpeedMultiplier())
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = CyberCyan,
                                    )
                                }
                            }

                            // Draggable white Left (start) & Right (end) trim handles when clip is selected
                            if (isSel) {
                                TrimHandlesPair(
                                    itemId = clip.id,
                                    pxPerSec = pxPerSec,
                                    maxWidthDp = clipWidthDp,
                                    desiredHandleWidth = 18.dp,
                                    cornerRadius = 8.dp,
                                    startTag = "trim_clip_${laneIdx}_${idx}_start",
                                    endTag = "trim_clip_${laneIdx}_${idx}_end",
                                    onCommitHistory = onCommitHistory,
                                    onTrimDeltaMs = { isStart, deltaMs ->
                                        onTrimClipEdge(clip.id, isStart, deltaMs)
                                    },
                                )
                            }
                        }

                        // Transition Icon '[|]' at touching cut seams between adjacent clips
                        if (idx < laneSpans.lastIndex) {
                            val nextSpan = laneSpans[idx + 1]
                            if (abs(nextSpan.startMs - span.endMs) <= 80L) {
                                val tr = transitions.firstOrNull { it.afterClipId == clip.id }
                                val hasActiveTr = tr != null && tr.type != TransitionType.NONE
                                val seamX = playheadXOffsetPx + (span.endMs / 1000f) * pxPerSec - with(density) { 13.dp.toPx() }
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(seamX.roundToInt(), with(density) { 16.dp.toPx() }.roundToInt()) }
                                        .size(26.dp)
                                        .zIndex(4f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (hasActiveTr) CyberCyan else Color.White)
                                        .border(1.5.dp, ObsidianBackground, RoundedCornerShape(6.dp))
                                        .clickable { onOpenTransition(clip.id) }
                                        .testTag("transition_cut_${laneIdx}_$idx"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                        contentDescription = "Edit transition",
                                        tint = ObsidianBackground,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Ending card + '+' append button pinned after end of primary video track (lane 0)
                    if (laneIdx == 0) {
                        val mainEndMs = spans.maxOfOrNull { it.endMs } ?: 0L
                        val tailStartPx = playheadXOffsetPx + (mainEndMs / 1000f) * pxPerSec + with(density) { 8.dp.toPx() }
                        Row(
                            modifier = Modifier
                                .offset { IntOffset(tailStartPx.roundToInt(), 0) }
                                .fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(74.dp)
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (hasEndingCard) ObsidianSurfaceHigh else ObsidianSurfaceContainer)
                                    .border(1.dp, if (hasEndingCard) CyberCyan else OutlineSubtle, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onToggleEndingCard),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (hasEndingCard) "LUNA\nEnding ✓" else "+ Add\nending",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (hasEndingCard) CyberCyan else TextSecondary,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .clickable(onClick = onAddClipClick)
                                    .testTag("timeline_add_clip_plus"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Append video clip", tint = ObsidianBackground, modifier = Modifier.size(20.dp))
                            }
                        }

                        // Floating Left-edge Mute Clip button
                        val firstClip = spans.firstOrNull()?.clip
                        if (firstClip != null && playheadXOffsetPx > 72f) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ObsidianSurfaceContainer)
                                        .clickable { onToggleMuteClip(firstClip.id) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = if (firstClip.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Mute clip",
                                        tint = if (firstClip.isMuted) CyberCyan else Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text("Mute", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Overlay / Image Track Lanes (side-by-side on same lane when split/non-overlapping!)
            overlayLanes.forEachIndexed { laneIdx, laneItems ->
                TimelineTrackLaneRow(
                    items = laneItems,
                    itemId = { it.id },
                    startMsOf = { it.startMs },
                    endMsOf = { it.endMs },
                    labelOf = { it.displayName },
                    pxPerSec = pxPerSec,
                    playheadXOffsetPx = playheadXOffsetPx,
                    containerColor = ElectricViolet.copy(alpha = 0.88f),
                    contentColor = Color.White,
                    icon = Icons.Filled.Layers,
                    selectedId = selectedOverlayId,
                    rowTagPrefix = "timeline_overlay_${laneIdx}",
                    onSelectToggle = { id -> onSelectOverlay(if (id == selectedOverlayId) null else id) },
                    onSelectForce = { id -> onSelectOverlay(id) },
                    onCommitHistory = onCommitHistory,
                    onMoveDeltaMs = onMoveOverlay,
                    onTrimDeltaMs = onTrimOverlayEdge,
                )
            }

            // 4. Effect Track Lanes (side-by-side on same lane when split/non-overlapping!)
            effectLanes.forEachIndexed { laneIdx, laneItems ->
                TimelineTrackLaneRow(
                    items = laneItems,
                    itemId = { it.id },
                    startMsOf = { it.startMs },
                    endMsOf = { it.endMs },
                    labelOf = { fx ->
                        val objLabel = if (fx.applyToAllVideos) "All videos" else "Main video"
                        "$objLabel | ${fx.type.label}"
                    },
                    pxPerSec = pxPerSec,
                    playheadXOffsetPx = playheadXOffsetPx,
                    containerColor = Color(0xFF7C3AED).copy(alpha = 0.9f),
                    contentColor = Color.White,
                    icon = Icons.Filled.AutoAwesome,
                    selectedId = selectedEffectId,
                    rowTagPrefix = "timeline_effect_${laneIdx}",
                    onSelectToggle = { id -> onSelectEffect(if (id == selectedEffectId) null else id) },
                    onSelectForce = { id -> onSelectEffect(id) },
                    onCommitHistory = onCommitHistory,
                    onMoveDeltaMs = onMoveEffect,
                    onTrimDeltaMs = onTrimEffectEdge,
                )
            }

            // 5. Text / Captions Track Lanes (Text IS a clip — same lane, drag, trim, split side-by-side!)
            if (textLanes.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(32.dp).padding(vertical = 2.dp)) {
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(playheadXOffsetPx.roundToInt().coerceAtLeast(16), 0) }
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianSurfaceContainer)
                            .clickable { onOpenSheet(ActiveEditorSheet.TEXT) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.TextFields, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add text", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            } else {
                textLanes.forEachIndexed { laneIdx, laneItems ->
                    TimelineTrackLaneRow(
                        items = laneItems,
                        itemId = { it.id },
                        startMsOf = { it.startMs },
                        endMsOf = { it.endMs },
                        labelOf = { it.text },
                        pxPerSec = pxPerSec,
                        playheadXOffsetPx = playheadXOffsetPx,
                        containerColor = Color(0xFFEA580C),
                        contentColor = Color.White,
                        icon = Icons.Filled.TextFields,
                        selectedId = selectedTextId,
                        rowTagPrefix = "timeline_text_${laneIdx}",
                        keyframeTimesAndIds = { tx -> tx.keyframes.map { it.id to it.timeMs } },
                        selectedKfId = selectedKeyframeId,
                        diamondColor = CyberCyan,
                        onSelectKeyframe = onSelectTextKeyframe,
                        onSelectToggle = { id -> onSelectText(if (id == selectedTextId) null else id) },
                        onSelectForce = { id -> onSelectText(id) },
                        onCommitHistory = onCommitHistory,
                        onMoveDeltaMs = onMoveText,
                        onTrimDeltaMs = onTrimTextEdge,
                    )
                }
            }

            // 6. Audio Track Lanes (side-by-side on same lane when split/non-overlapping!)
            if (audioLanes.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(32.dp).padding(vertical = 2.dp)) {
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(playheadXOffsetPx.roundToInt().coerceAtLeast(16), 0) }
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianSurfaceContainer)
                            .clickable { onOpenSheet(ActiveEditorSheet.AUDIO) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = LunaMint, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add audio", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            } else {
                audioLanes.forEachIndexed { laneIdx, laneItems ->
                    TimelineTrackLaneRow(
                        items = laneItems,
                        itemId = { it.id },
                        startMsOf = { it.startMs },
                        endMsOf = { it.endMs },
                        labelOf = { it.title },
                        pxPerSec = pxPerSec,
                        playheadXOffsetPx = playheadXOffsetPx,
                        containerColor = Color(0xFF0E7490),
                        contentColor = Color.White,
                        icon = Icons.Filled.MusicNote,
                        selectedId = selectedAudioId,
                        rowTagPrefix = "timeline_audio_${laneIdx}",
                        keyframeTimesAndIds = { au -> au.volumeKeyframes.map { it.id to it.timeMs } },
                        selectedKfId = selectedVolumeKeyframeId,
                        diamondColor = LunaMint,
                        onSelectKeyframe = onSelectAudioVolumeKeyframe,
                        onSelectToggle = { id -> onSelectAudio(if (id == selectedAudioId) null else id) },
                        onSelectForce = { id -> onSelectAudio(id) },
                        onCommitHistory = onCommitHistory,
                        onMoveDeltaMs = onMoveAudio,
                        onTrimDeltaMs = onTrimAudioEdge,
                    )
                }
            }
        }

        // Centered crisp white Playhead vertical line
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White),
        )
    }
}

@Composable
private fun <T> TimelineTrackLaneRow(
    items: List<T>,
    itemId: (T) -> String,
    startMsOf: (T) -> Long,
    endMsOf: (T) -> Long,
    labelOf: (T) -> String,
    pxPerSec: Float,
    playheadXOffsetPx: Float,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedId: String?,
    rowTagPrefix: String,
    keyframeTimesAndIds: (T) -> List<Pair<String, Long>> = { emptyList() },
    selectedKfId: String? = null,
    diamondColor: Color = CyberCyan,
    onSelectKeyframe: ((itemId: String, kfId: String) -> Unit)? = null,
    onSelectToggle: (String) -> Unit,
    onSelectForce: (String) -> Unit,
    onCommitHistory: () -> Unit,
    onMoveDeltaMs: (id: String, deltaMs: Long) -> Unit,
    onTrimDeltaMs: (id: String, isStart: Boolean, deltaMs: Long) -> Unit,
) {
    val density = LocalDensity.current
    val latestPxPerSec by rememberUpdatedState(pxPerSec)
    val latestOnSelectToggle by rememberUpdatedState(onSelectToggle)
    val latestOnSelectForce by rememberUpdatedState(onSelectForce)
    val latestOnCommit by rememberUpdatedState(onCommitHistory)
    val latestOnMove by rememberUpdatedState(onMoveDeltaMs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(vertical = 2.dp),
    ) {
        items.forEachIndexed { idx, item ->
            val id = itemId(item)
            val startMs = startMsOf(item)
            val endMs = endMsOf(item)
            val isSelected = id == selectedId
            val startPx = playheadXOffsetPx + (startMs / 1000f) * pxPerSec
            val barWidthDp = with(density) {
                (((endMs - startMs).coerceAtLeast(150L) / 1000f) * pxPerSec).toDp().coerceAtLeast(24.dp)
            }
            var isDraggingBody by remember(id) { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .offset { IntOffset(startPx.roundToInt(), 0) }
                    .width(barWidthDp)
                    .fillMaxHeight()
                    .zIndex(if (isDraggingBody) 3f else if (isSelected) 2f else 1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(containerColor)
                    .border(
                        width = if (isDraggingBody || isSelected) 2.dp else 0.5.dp,
                        color = when {
                            isDraggingBody -> CyberCyan
                            isSelected -> Color.White
                            else -> Color.White.copy(alpha = 0.25f)
                        },
                        shape = RoundedCornerShape(7.dp),
                    )
                    .clickable { latestOnSelectToggle(id) }
                    .pointerInput(id) {
                        var accumMs = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isDraggingBody = true
                                accumMs = 0f
                                latestOnSelectForce(id)
                                latestOnCommit()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumMs += (dragAmount.x / latestPxPerSec) * 1000f
                                val wholeMs = accumMs.toLong()
                                if (wholeMs != 0L) {
                                    accumMs -= wholeMs
                                    latestOnMove(id, wholeMs)
                                }
                            },
                            onDragEnd = { isDraggingBody = false },
                            onDragCancel = { isDraggingBody = false },
                        )
                    }
                    .testTag("${rowTagPrefix}_$idx"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isSelected) 18.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = contentColor, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = labelOf(item),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Interactive tap-selectable Keyframe Diamonds on Text & Audio items
                val kfs = keyframeTimesAndIds(item)
                if (kfs.isNotEmpty() && onSelectKeyframe != null) {
                    val durF = (endMs - startMs).coerceAtLeast(1L).toFloat()
                    val barWpx = with(density) { barWidthDp.toPx() }
                    kfs.forEach { (kfId, tMs) ->
                        val frac = (tMs / durF).coerceIn(0.04f, 0.96f)
                        val cx = frac * barWpx - with(density) { 11.dp.toPx() }
                        val isKfSel = kfId == selectedKfId
                        TimelineDiamondPin(
                            offsetXPx = cx,
                            offsetYPx = with(density) { 4.dp.toPx() },
                            fillColor = if (isKfSel) StudioAmber else diamondColor,
                            isSelected = isKfSel,
                            tag = "${rowTagPrefix}_kf_$kfId",
                            onClick = { onSelectKeyframe(id, kfId) },
                        )
                    }
                }

                if (isSelected) {
                    TrimHandlesPair(
                        itemId = id,
                        pxPerSec = pxPerSec,
                        maxWidthDp = barWidthDp,
                        desiredHandleWidth = 16.dp,
                        cornerRadius = 7.dp,
                        startTag = "${rowTagPrefix}_${idx}_trim_start",
                        endTag = "${rowTagPrefix}_${idx}_trim_end",
                        onCommitHistory = onCommitHistory,
                        onTrimDeltaMs = { isStart, delta -> onTrimDeltaMs(id, isStart, delta) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineDiamondPin(
    offsetXPx: Float,
    offsetYPx: Float,
    fillColor: Color,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt()) }
            .size(22.dp)
            .zIndex(5f)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(13.dp)) {
            val c = Offset(size.width * 0.5f, size.height * 0.5f)
            val r = size.minDimension * 0.44f
            val diamond = Path().apply {
                moveTo(c.x, c.y - r)
                lineTo(c.x + r, c.y)
                lineTo(c.x, c.y + r)
                lineTo(c.x - r, c.y)
                close()
            }
            drawPath(diamond, fillColor)
            drawPath(
                diamond,
                color = if (isSelected) Color.White else ObsidianBackground,
                style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TrimHandlesPair(
    itemId: String,
    pxPerSec: Float,
    maxWidthDp: androidx.compose.ui.unit.Dp,
    desiredHandleWidth: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    startTag: String,
    endTag: String,
    onCommitHistory: () -> Unit,
    onTrimDeltaMs: (isStart: Boolean, deltaMs: Long) -> Unit,
) {
    val latestOnTrim by rememberUpdatedState(onTrimDeltaMs)
    val latestOnCommit by rememberUpdatedState(onCommitHistory)
    val latestPxPerSec by rememberUpdatedState(pxPerSec)
    val effectiveHandleWidth = (maxWidthDp / 3f).coerceIn(10.dp, desiredHandleWidth)

    // Left white trim handle (trims from start)
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .width(effectiveHandleWidth)
            .fillMaxHeight()
            .background(Color.White, RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius))
            .pointerInput(itemId) {
                var accumMs = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumMs = 0f
                        latestOnCommit()
                    },
                    onHorizontalDrag = { change, drag ->
                        change.consume()
                        accumMs += (drag / latestPxPerSec) * 1000f
                        val wholeMs = accumMs.toLong()
                        if (wholeMs != 0L) {
                            accumMs -= wholeMs
                            latestOnTrim(true, wholeMs)
                        }
                    },
                )
            }
            .testTag(startTag),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).height(14.dp).background(ObsidianBackground))
    }

    // Right white trim handle (trims from behind / end)
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(effectiveHandleWidth)
            .fillMaxHeight()
            .background(Color.White, RoundedCornerShape(topEnd = cornerRadius, bottomEnd = cornerRadius))
            .pointerInput(itemId) {
                var accumMs = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumMs = 0f
                        latestOnCommit()
                    },
                    onHorizontalDrag = { change, drag ->
                        change.consume()
                        accumMs += (drag / latestPxPerSec) * 1000f
                        val wholeMs = accumMs.toLong()
                        if (wholeMs != 0L) {
                            accumMs -= wholeMs
                            latestOnTrim(false, wholeMs)
                        }
                    },
                )
            }
            .testTag(endTag),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).height(14.dp).background(ObsidianBackground))
    }
}
