package com.example.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.model.AudioTrackItem
import com.example.data.model.ClipTimelineSpan
import com.example.data.model.OverlayTrackItem
import com.example.data.model.SmoothReverseRole
import com.example.data.model.TextTrackItem
import com.example.data.model.TimelineTrackConfig
import com.example.data.model.TimelineTrackKind
import com.example.data.model.TransitionSpec
import com.example.data.model.TransitionType
import com.example.data.model.VideoEffectLayer
import com.example.data.model.computeTimelineWidthPx
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LunaMint
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val TRACK_HEADER_WIDTH = 86.dp
private val RULER_HEIGHT = 28.dp

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
    trackConfigs: List<TimelineTrackConfig>,
    videoTrackCount: Int,
    filmstrip: List<ImageBitmap>,
    clipThumbnails: Map<String, List<ImageBitmap>>,
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
    onScrubStart: () -> Unit = {},
    onCommitHistory: () -> Unit,
    onSelectClip: (String?) -> Unit,
    onSelectClipKeyframe: (clipId: String, kfId: String) -> Unit,
    onSelectTextKeyframe: (textId: String, kfId: String) -> Unit,
    onSelectClipVolumeKeyframe: (clipId: String, kfId: String) -> Unit,
    onSelectAudioVolumeKeyframe: (audioId: String, kfId: String) -> Unit,
    onMoveClip: (clipId: String, deltaMs: Long) -> Unit,
    onMoveClipToTrack: (clipId: String, targetTrackIndex: Int) -> Unit,
    onMoveOverlay: (overlayId: String, deltaMs: Long) -> Unit,
    onMoveEffect: (effectId: String, deltaMs: Long) -> Unit,
    onMoveText: (textId: String, deltaMs: Long) -> Unit,
    onMoveAudio: (audioId: String, deltaMs: Long) -> Unit,
    onMoveAudioToSlot: (audioId: String, targetTrackIndex: Int) -> Unit,
    onTrimClipEdge: (clipId: String, isStart: Boolean, deltaTimelineMs: Long) -> Unit,
    onTrimOverlayEdge: (overlayId: String, isStart: Boolean, deltaMs: Long) -> Unit,
    onTrimEffectEdge: (effectId: String, isStart: Boolean, deltaMs: Long) -> Unit,
    onTrimTextEdge: (textId: String, isStart: Boolean, deltaMs: Long) -> Unit,
    onTrimAudioEdge: (audioId: String, isStart: Boolean, deltaMs: Long) -> Unit,
    onToggleMuteClip: (String) -> Unit,
    onOpenTransition: (afterClipId: String) -> Unit,
    onAddClipClick: () -> Unit,
    onAddClipToTrack: (trackIndex: Int) -> Unit,
    onAddAudioToSlot: (trackIndex: Int) -> Unit,
    onAddVideoTrack: () -> Unit,
    onToggleTrackHidden: (trackId: String) -> Unit,
    onToggleTrackLocked: (trackId: String) -> Unit,
    onToggleTrackMuted: (trackId: String) -> Unit,
    onResizeTrackHeight: (trackId: String, newHeightDp: Float) -> Unit,
    onToggleEndingCard: () -> Unit,
    onOpenSheet: (ActiveEditorSheet) -> Unit,
    onSelectOverlay: (String?) -> Unit,
    onSelectEffect: (String?) -> Unit,
    onSelectText: (String?) -> Unit,
    onSelectAudio: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pxPerSec = with(density) { (60.dp * timelineZoom).toPx() }.coerceAtLeast(24f)
    val latestPlayheadMs by rememberUpdatedState(playheadMs)
    val latestTotalDurationMs by rememberUpdatedState(totalDurationMs)
    val latestZoom by rememberUpdatedState(timelineZoom)
    val latestPxPerSec by rememberUpdatedState(pxPerSec)
    val latestOnUpdatePlayhead by rememberUpdatedState(onUpdatePlayhead)
    val latestOnUpdateZoom by rememberUpdatedState(onUpdateZoom)
    val latestOnScrubStart by rememberUpdatedState(onScrubStart)

    val safeVideoCount = videoTrackCount.coerceIn(1, 8)
    val textCfg = remember(trackConfigs) {
        trackConfigs.firstOrNull { it.kind == TimelineTrackKind.TEXT }
            ?: TimelineTrackConfig("track_text_0", TimelineTrackKind.TEXT, 0, "TEXT", "Text / Sticker", heightDp = 38f)
    }
    val textLanes = remember(textTracks) { assignTrackLanes(textTracks, { it.startMs }, { it.endMs }) }
    val overlayLanes = remember(overlays) { assignTrackLanes(overlays, { it.startMs }, { it.endMs }) }
    val effectLanes = remember(effectLayers) { assignTrackLanes(effectLayers, { it.startMs }, { it.endMs }) }

    val videoTrackEntries = remember(spans, trackConfigs, safeVideoCount) {
        ((safeVideoCount - 1) downTo 0).map { trackIdx ->
            val cfg = trackConfigs.firstOrNull { it.kind == TimelineTrackKind.VIDEO && it.trackIndex == trackIdx }
                ?: TimelineTrackConfig(
                    id = "track_video_$trackIdx",
                    kind = TimelineTrackKind.VIDEO,
                    trackIndex = trackIdx,
                    label = "V${trackIdx + 1}",
                    subtitle = if (trackIdx == 0) "Main video" else "Overlay",
                    heightDp = if (trackIdx == 0) 58f else 50f,
                )
            val trackSpans = spans.filter { it.clip.trackIndex == trackIdx }.sortedBy { it.startMs }
            val lanes = assignTrackLanes(trackSpans, { it.startMs }, { it.endMs }).ifEmpty { listOf(emptyList()) }
            Triple(trackIdx, cfg, lanes)
        }
    }

    val audioTrackEntries = remember(audioTracks, trackConfigs) {
        listOf(0, 1).map { slot ->
            val cfg = trackConfigs.firstOrNull { it.kind == TimelineTrackKind.AUDIO && it.trackIndex == slot }
                ?: TimelineTrackConfig(
                    id = "track_audio_$slot",
                    kind = TimelineTrackKind.AUDIO,
                    trackIndex = slot,
                    label = "A${slot + 1}",
                    subtitle = if (slot == 0) "Music" else "Voice",
                    heightDp = 38f,
                )
            val slotItems = audioTracks.filter { it.trackIndex.coerceIn(0, 1) == slot }
            val lanes = assignTrackLanes(slotItems, { it.startMs }, { it.endMs })
            Triple(slot, cfg, lanes)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBackground)
            .clipToBounds(),
    ) {
        val timelineViewportDp = (maxWidth - TRACK_HEADER_WIDTH).coerceAtLeast(160.dp)
        val viewportWidthPx = with(density) { timelineViewportDp.toPx() }.coerceAtLeast(1f)
        val halfViewportDp = timelineViewportDp * 0.5f
        val halfViewportPx = viewportWidthPx * 0.5f
        val timelineWidthPx = computeTimelineWidthPx(totalDurationMs, pxPerSec)
        val timelineWidthDp = with(density) { timelineWidthPx.toDp() }
        val maxExtraEndMs = maxOf(
            totalDurationMs,
            overlays.maxOfOrNull { it.endMs } ?: 0L,
            effectLayers.maxOfOrNull { it.endMs } ?: 0L,
            textTracks.maxOfOrNull { it.endMs } ?: 0L,
            audioTracks.maxOfOrNull { it.endMs } ?: 0L,
        )
        val extraTailDp = with(density) {
            (((maxExtraEndMs - totalDurationMs).coerceAtLeast(0L) / 1000f) * pxPerSec).toDp()
        }
        val rightPadDp = maxOf(halfViewportDp, 150.dp + extraTailDp)
        val scrollableWidthDp = halfViewportDp + timelineWidthDp + rightPadDp
        val maxTimelineScrollPx = timelineWidthPx.roundToInt().coerceAtLeast(0)
        val hScrollState = rememberScrollState()
        val vScrollState = rememberScrollState()

        LaunchedEffect(hScrollState.isScrollInProgress) {
            if (hScrollState.isScrollInProgress) {
                latestOnScrubStart()
            }
        }

        LaunchedEffect(hScrollState, pxPerSec, totalDurationMs, maxTimelineScrollPx) {
            snapshotFlow { hScrollState.value to hScrollState.isScrollInProgress }
                .collect { (scrollPx, scrolling) ->
                    if (scrolling) {
                        if (scrollPx > maxTimelineScrollPx) {
                            hScrollState.scrollTo(maxTimelineScrollPx)
                        }
                        val clampedPx = scrollPx.coerceIn(0, maxTimelineScrollPx)
                        val ms = ((clampedPx / latestPxPerSec) * 1000f)
                            .roundToLong()
                            .coerceIn(0L, latestTotalDurationMs)
                        if (ms != latestPlayheadMs) {
                            latestOnUpdatePlayhead(ms)
                        }
                    }
                }
        }

        LaunchedEffect(playheadMs, pxPerSec, totalDurationMs, hScrollState.maxValue) {
            if (!hScrollState.isScrollInProgress) {
                val target = ((playheadMs.coerceIn(0L, totalDurationMs) / 1000f) * pxPerSec)
                    .roundToInt()
                    .coerceIn(0, maxTimelineScrollPx)
                if (abs(hScrollState.value - target) > 1) {
                    hScrollState.scrollTo(target)
                }
            }
        }

        val textLaneDp = textCfg.heightDp.coerceIn(30f, 110f).dp
        val textTotalHeightDp = textLaneDp * maxOf(1, textLanes.size)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vScrollState),
        ) {
            // LEFT FIXED TRACK HEADERS COLUMN (V3, V2, V1, TEXT, A1, A2 + Hide/Lock/Mute/Resize + Add Track)
            Column(
                modifier = Modifier
                    .width(TRACK_HEADER_WIDTH)
                    .background(ObsidianSurface)
                    .padding(bottom = 10.dp),
            ) {
                // Ruler left cell: "+ Track" button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RULER_HEIGHT)
                        .background(ObsidianBackground)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianSurfaceContainer)
                            .border(1.dp, CyberCyan.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .clickable(onClick = onAddVideoTrack)
                            .padding(horizontal = 4.dp)
                            .testTag("add_video_track_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Track", tint = CyberCyan, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Add Track",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = CyberCyan,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // TEXT header cell
                key(textCfg.id) {
                    TrackControlHeaderCell(
                        config = textCfg,
                        rowHeightDp = textTotalHeightDp,
                        accentColor = Color(0xFFEA580C),
                        showMute = false,
                        onToggleHidden = { onToggleTrackHidden(textCfg.id) },
                        onToggleLocked = { onToggleTrackLocked(textCfg.id) },
                        onToggleMuted = {},
                        onResizeHeightDp = { h -> onResizeTrackHeight(textCfg.id, h) },
                    )
                }

                // VIDEO tracks headers (V3 -> V2 -> V1)
                videoTrackEntries.forEach { (trackIdx, cfg, lanes) ->
                    key(cfg.id) {
                        val laneHeightDp = cfg.heightDp.coerceIn(34f, 120f).dp
                        val rowHeightDp = laneHeightDp * lanes.size
                        val hasSelectedOnTrack = lanes.any { l -> l.any { it.clip.id == selectedClipId } }
                        TrackControlHeaderCell(
                            config = cfg,
                            rowHeightDp = rowHeightDp,
                            accentColor = if (hasSelectedOnTrack || trackIdx == 0) CyberCyan else Color.White,
                            showMute = true,
                            onToggleHidden = { onToggleTrackHidden(cfg.id) },
                            onToggleLocked = { onToggleTrackLocked(cfg.id) },
                            onToggleMuted = { onToggleTrackMuted(cfg.id) },
                            onResizeHeightDp = { h -> onResizeTrackHeight(cfg.id, h) },
                        )
                    }
                }

                if (overlayLanes.isNotEmpty() || effectLanes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                overlayLanes.forEachIndexed { idx, _ ->
                    key("hdr_ov_$idx") {
                        CompactAuxiliaryTrackHeader(label = "OV${idx + 1}", accentColor = ElectricViolet)
                    }
                }
                effectLanes.forEachIndexed { idx, _ ->
                    key("hdr_fx_$idx") {
                        CompactAuxiliaryTrackHeader(label = "FX${idx + 1}", accentColor = Color(0xFF7C3AED))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // AUDIO tracks headers (A1, A2)
                audioTrackEntries.forEach { (_, cfg, lanes) ->
                    key(cfg.id) {
                        val laneHeightDp = cfg.heightDp.coerceIn(30f, 100f).dp
                        val rowHeightDp = laneHeightDp * maxOf(1, lanes.size)
                        TrackControlHeaderCell(
                            config = cfg,
                            rowHeightDp = rowHeightDp,
                            accentColor = LunaMint,
                            showMute = true,
                            onToggleHidden = { onToggleTrackHidden(cfg.id) },
                            onToggleLocked = { onToggleTrackLocked(cfg.id) },
                            onToggleMuted = { onToggleTrackMuted(cfg.id) },
                            onResizeHeightDp = { h -> onResizeTrackHeight(cfg.id, h) },
                        )
                    }
                }
            }

            // RIGHT HORIZONTALLY SCROLLABLE MULTI-TRACK TIMELINE VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    // 2-finger pinch zoom intercepted on Initial pass so horizontalScroll doesn't fight pinch
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var zoomVal = latestZoom
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (abs(zoom - 1f) > 0.005f) {
                                        zoomVal = (zoomVal * zoom).coerceIn(0.45f, 3.8f)
                                        latestOnUpdateZoom(zoomVal)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(hScrollState)
                        .padding(bottom = 10.dp),
                ) {
                    // 1. Time Ruler row (strictly aligned with pixelsPerSecond across full duration)
                    val minLabelGapPx = with(density) { 48.dp.toPx() }
                    val secStep = remember(pxPerSec, minLabelGapPx) {
                        listOf(1, 2, 5, 10, 15, 30, 60).firstOrNull { it * pxPerSec >= minLabelGapPx } ?: 60
                    }
                    val maxTimelineSec = remember(totalDurationMs) {
                        ((totalDurationMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                    }
                    val visStartPx = hScrollState.value - 80f
                    val visEndPx = hScrollState.value + viewportWidthPx + 80f
                    Box(
                        modifier = Modifier
                            .width(scrollableWidthDp)
                            .height(RULER_HEIGHT)
                            .clipToBounds()
                            .background(ObsidianBackground),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (s in 0..maxTimelineSec) {
                                val x = halfViewportPx + s * pxPerSec
                                if (x in visStartPx..visEndPx) {
                                    val major = s % secStep == 0
                                    drawLine(
                                        color = Color.White.copy(alpha = if (major) 0.55f else 0.22f),
                                        start = Offset(x, size.height - if (major) 10.dp.toPx() else 5.dp.toPx()),
                                        end = Offset(x, size.height),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                            }
                        }
                        for (s in 0..maxTimelineSec step secStep) {
                            val xPx = halfViewportPx + s * pxPerSec
                            if (xPx in visStartPx..visEndPx) {
                                Text(
                                    text = formatRulerSeconds(s),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = TextSecondary,
                                    modifier = Modifier.offset { IntOffset((xPx - 14f).roundToInt(), 4) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. TEXT / Sticker Track (on top of all video tracks)
                    key(textCfg.id) {
                        Box(
                            modifier = Modifier
                                .width(scrollableWidthDp)
                                .height(textTotalHeightDp)
                                .clipToBounds()
                                .alpha(if (textCfg.isHidden) 0.35f else 1f)
                                .background(ObsidianSurfaceContainer.copy(alpha = 0.22f)),
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (textLanes.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .width(scrollableWidthDp)
                                            .height(textLaneDp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .offset { IntOffset(halfViewportPx.roundToInt(), 0) }
                                                .height((textLaneDp - 8.dp).coerceAtLeast(22.dp))
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(ObsidianSurfaceContainer)
                                                .clickable {
                                                    if (!textCfg.isLocked) onOpenSheet(ActiveEditorSheet.TEXT)
                                                }
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Filled.TextFields, null, tint = Color(0xFFEA580C), modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("+ Add text", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)
                                        }
                                    }
                                } else {
                                    textLanes.forEachIndexed { laneIdx, laneItems ->
                                        key("txt_lane_$laneIdx") {
                                            TimelineTrackLaneRow(
                                                items = laneItems,
                                                itemId = { it.id },
                                                startMsOf = { it.startMs },
                                                endMsOf = { it.endMs },
                                                labelOf = { it.text },
                                                pxPerSec = pxPerSec,
                                                baseOffsetPx = halfViewportPx,
                                                scrollableWidthDp = scrollableWidthDp,
                                                rowHeightDp = textLaneDp,
                                                containerColor = Color(0xFFEA580C),
                                                contentColor = Color.White,
                                                icon = Icons.Filled.TextFields,
                                                selectedId = selectedTextId,
                                                isLocked = textCfg.isLocked,
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
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(OutlineSubtle.copy(alpha = 0.4f)),
                            )
                        }
                    }

                    // 3. VIDEO TRACKS (V3 -> V2 -> V1, drag clips up/down across tracks, + Add per upper track, + Ending on V1)
                    val latestMaxVideoTrack by rememberUpdatedState(safeVideoCount - 1)
                    val latestOnMoveClipToTrack by rememberUpdatedState(onMoveClipToTrack)

                    videoTrackEntries.forEach { (trackIdx, cfg, lanes) ->
                        key(cfg.id) {
                        val laneHeightDp = cfg.heightDp.coerceIn(34f, 120f).dp
                        val laneHeightPx = with(density) { laneHeightDp.toPx() }.coerceAtLeast(32f)
                        val rowHeightDp = laneHeightDp * lanes.size
                        val isTrackEmpty = lanes.all { it.isEmpty() }

                        Box(
                            modifier = Modifier
                                .width(scrollableWidthDp)
                                .height(rowHeightDp)
                                .clipToBounds()
                                .alpha(if (cfg.isHidden) 0.35f else 1f)
                                .background(
                                    if (trackIdx % 2 == 0) ObsidianBackground else ObsidianSurfaceContainer.copy(alpha = 0.18f)
                                ),
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                lanes.forEachIndexed { subIdx, laneSpans ->
                                    Box(
                                        modifier = Modifier
                                            .width(scrollableWidthDp)
                                            .height(laneHeightDp),
                                    ) {
                                        laneSpans.forEachIndexed { idx, span ->
                                            val clip = span.clip
                                            val isSel = clip.id == selectedClipId
                                            val startPx = halfViewportPx + (span.startMs / 1000f) * pxPerSec
                                            val clipWidthDp = with(density) {
                                                (((span.endMs - span.startMs).coerceAtLeast(150L) / 1000f) * pxPerSec)
                                                    .toDp()
                                                    .coerceAtLeast(24.dp)
                                            }
                                            var isDraggingBody by remember(clip.id) { mutableStateOf(false) }
                                            val latestOnSelectClip by rememberUpdatedState(onSelectClip)
                                            val latestOnCommit by rememberUpdatedState(onCommitHistory)
                                            val latestOnMoveClip by rememberUpdatedState(onMoveClip)
                                            val latestClipTrackIdx by rememberUpdatedState(clip.trackIndex)
                                            val latestTrackLocked by rememberUpdatedState(cfg.isLocked)
                                            val latestLaneHeightPx by rememberUpdatedState(laneHeightPx)

                                            Box(
                                                modifier = Modifier
                                                    .offset { IntOffset(startPx.roundToInt(), 2) }
                                                    .width(clipWidthDp)
                                                    .height((laneHeightDp - 4.dp).coerceAtLeast(28.dp))
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
                                                    .clickable {
                                                        if (isSel) {
                                                            latestOnSelectClip(null)
                                                        } else {
                                                            latestOnSelectClip(clip.id)
                                                            latestOnScrubStart()
                                                            latestOnUpdatePlayhead(span.startMs)
                                                        }
                                                    }
                                                    .pointerInput(clip.id) {
                                                        var accumMs = 0f
                                                        var accumDy = 0f
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                if (latestTrackLocked) return@detectDragGesturesAfterLongPress
                                                                isDraggingBody = true
                                                                accumMs = 0f
                                                                accumDy = 0f
                                                                latestOnSelectClip(clip.id)
                                                                latestOnCommit()
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                if (latestTrackLocked) return@detectDragGesturesAfterLongPress
                                                                change.consume()
                                                                accumMs += (dragAmount.x / latestPxPerSec) * 1000f
                                                                val wholeMs = accumMs.toLong()
                                                                if (wholeMs != 0L) {
                                                                    accumMs -= wholeMs
                                                                    latestOnMoveClip(clip.id, wholeMs)
                                                                }
                                                                accumDy += dragAmount.y
                                                                val switchThresh = latestLaneHeightPx * 0.55f
                                                                if (abs(accumDy) >= switchThresh) {
                                                                    val dir = if (accumDy < 0f) 1 else -1 // Upward = higher track (V2, V3)
                                                                    val targetTrack = (latestClipTrackIdx + dir).coerceIn(0, latestMaxVideoTrack)
                                                                    if (targetTrack != latestClipTrackIdx) {
                                                                        latestOnMoveClipToTrack(clip.id, targetTrack)
                                                                    }
                                                                    accumDy = 0f
                                                                }
                                                            },
                                                            onDragEnd = { isDraggingBody = false },
                                                            onDragCancel = { isDraggingBody = false },
                                                        )
                                                    }
                                                    .testTag("timeline_clip_${trackIdx}_${subIdx}_$idx"),
                                            ) {
                                                // Full-duration filmstrip thumbnail tiles
                                                val clipThumbs = clipThumbnails[clip.id]?.takeIf { it.isNotEmpty() } ?: filmstrip
                                                Row(modifier = Modifier.fillMaxSize()) {
                                                    val tileCount = (clipWidthDp / 42.dp).roundToInt().coerceIn(1, 140)
                                                    for (t in 0 until tileCount) {
                                                        if (clipThumbs.isNotEmpty()) {
                                                            val rawIdx = ((t + 0.5f) / tileCount * clipThumbs.size).toInt()
                                                                .coerceIn(0, clipThumbs.lastIndex)
                                                            val bmpIdx = if (clip.isReversed && clipThumbnails[clip.id] == null) {
                                                                clipThumbs.lastIndex - rawIdx
                                                            } else rawIdx
                                                            val bmp = clipThumbs[bmpIdx]
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

                                                // Smooth Reverse / Speed / Freeze / Reverse / TIMESLICE pill badges
                                                val hasBaseBadge = clip.smoothReverseRole != SmoothReverseRole.NONE ||
                                                    abs(clip.speed.effectiveSpeedMultiplier() - 1f) > 0.05f ||
                                                    clip.isFrozen ||
                                                    clip.isReversed
                                                if (hasBaseBadge || clip.timeslice.enabled) {
                                                    Row(
                                                        modifier = Modifier
                                                            .align(Alignment.TopStart)
                                                            .padding(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        if (hasBaseBadge) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(ObsidianBackground.copy(alpha = 0.84f))
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                                    .testTag("timeline_clip_badge_${trackIdx}_$idx"),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                            ) {
                                                                if (clip.smoothReverseRole != SmoothReverseRole.NONE) {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.AutoAwesome,
                                                                        contentDescription = "Smooth Reverse",
                                                                        tint = if (clip.smoothReverseRole == SmoothReverseRole.REVERSED) CyberCyan else LunaMint,
                                                                        modifier = Modifier.size(10.dp),
                                                                    )
                                                                }
                                                                Text(
                                                                    text = when {
                                                                        clip.smoothReverseRole == SmoothReverseRole.ORIGINAL -> "Smooth"
                                                                        clip.smoothReverseRole == SmoothReverseRole.REVERSED -> "Rev Smooth"
                                                                        clip.isFrozen -> "Freeze"
                                                                        clip.isReversed -> "REVERSED"
                                                                        else -> String.format(Locale.US, "%.1fx", clip.speed.effectiveSpeedMultiplier())
                                                                    },
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                                                    color = if (clip.smoothReverseRole == SmoothReverseRole.ORIGINAL) LunaMint else CyberCyan,
                                                                )
                                                            }
                                                        }
                                                        if (clip.timeslice.enabled) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(ElectricViolet.copy(alpha = 0.90f))
                                                                    .clickable {
                                                                        latestOnSelectClip(clip.id)
                                                                        onOpenSheet(ActiveEditorSheet.TIMESLICE)
                                                                    }
                                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                                                    .testTag("timeline_clip_timeslice_badge_${trackIdx}_$idx"),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                Text(
                                                                    text = clip.timeslice.mode.badgeLabel,
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.ExtraBold),
                                                                    color = Color.White,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                // Draggable white Left & Right trim handles when selected & track unlocked
                                                if (isSel && !cfg.isLocked) {
                                                    TrimHandlesPair(
                                                        itemId = clip.id,
                                                        pxPerSec = pxPerSec,
                                                        maxWidthDp = clipWidthDp,
                                                        desiredHandleWidth = 18.dp,
                                                        cornerRadius = 8.dp,
                                                        startTag = "trim_clip_${trackIdx}_${idx}_start",
                                                        endTag = "trim_clip_${trackIdx}_${idx}_end",
                                                        onCommitHistory = onCommitHistory,
                                                        onTrimDeltaMs = { isStart, deltaMs ->
                                                            onTrimClipEdge(clip.id, isStart, deltaMs)
                                                        },
                                                    )
                                                }
                                            }

                                            // Keyframe Diamonds rendered in lane coordinates so 0ms/end diamonds center directly on white playhead line
                                            val clipDurF = (span.endMs - span.startMs).coerceAtLeast(1L).toFloat()
                                            val clipWidthPx = with(density) { clipWidthDp.toPx() }
                                            clip.keyframes.forEach { kf ->
                                                val frac = (kf.timeMs / clipDurF).coerceIn(0f, 1f)
                                                val cx = startPx + frac * clipWidthPx - with(density) { 11.dp.toPx() }
                                                val isKfSelected = kf.id == selectedKeyframeId
                                                TimelineDiamondPin(
                                                    offsetXPx = cx,
                                                    offsetYPx = with(density) { 14.dp.toPx() },
                                                    fillColor = if (isKfSelected) StudioAmber else CyberCyan,
                                                    isSelected = isKfSelected,
                                                    tag = "clip_kf_diamond_${kf.id}",
                                                    onClick = { onSelectClipKeyframe(clip.id, kf.id) },
                                                )
                                            }
                                            clip.volumeKeyframes.forEach { vkf ->
                                                val frac = (vkf.timeMs / clipDurF).coerceIn(0f, 1f)
                                                val cx = startPx + frac * clipWidthPx - with(density) { 11.dp.toPx() }
                                                val isVkSelected = vkf.id == selectedVolumeKeyframeId
                                                TimelineDiamondPin(
                                                    offsetXPx = cx,
                                                    offsetYPx = with(density) { 28.dp.toPx() },
                                                    fillColor = LunaMint,
                                                    isSelected = isVkSelected,
                                                    tag = "clip_vol_kf_diamond_${vkf.id}",
                                                    onClick = { onSelectClipVolumeKeyframe(clip.id, vkf.id) },
                                                )
                                            }

                                            // Transition Icon '[|]' at touching cut seams between adjacent clips on this track
                                            if (idx < laneSpans.lastIndex) {
                                                val nextSpan = laneSpans[idx + 1]
                                                if (abs(nextSpan.startMs - span.endMs) <= 80L) {
                                                    val tr = transitions.firstOrNull { it.afterClipId == clip.id }
                                                    val hasActiveTr = tr != null && tr.type != TransitionType.NONE
                                                    val seamX = halfViewportPx + (span.endMs / 1000f) * pxPerSec - with(density) { 13.dp.toPx() }
                                                    Box(
                                                        modifier = Modifier
                                                            .offset { IntOffset(seamX.roundToInt(), with(density) { 12.dp.toPx() }.roundToInt()) }
                                                            .size(26.dp)
                                                            .zIndex(4f)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (hasActiveTr) CyberCyan else Color.White)
                                                            .border(1.5.dp, ObsidianBackground, RoundedCornerShape(6.dp))
                                                            .clickable { onOpenTransition(clip.id) }
                                                            .testTag("transition_cut_${trackIdx}_$idx"),
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

                                        // V1 tail: Ending card + '+' append button pinned at exact totalDurationMs + floating Mute
                                        if (trackIdx == 0 && subIdx == 0) {
                                            val v1EndMs = totalDurationMs.coerceAtLeast(laneSpans.maxOfOrNull { it.endMs } ?: 0L)
                                            val tailStartPx = halfViewportPx + (v1EndMs / 1000f) * pxPerSec + with(density) { 8.dp.toPx() }
                                            Row(
                                                modifier = Modifier
                                                    .offset { IntOffset(tailStartPx.roundToInt(), 0) }
                                                    .width(126.dp)
                                                    .fillMaxHeight(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(74.dp)
                                                        .height((laneHeightDp - 8.dp).coerceAtLeast(30.dp))
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (hasEndingCard) ObsidianSurfaceHigh else ObsidianSurfaceContainer)
                                                        .border(1.dp, if (hasEndingCard) CyberCyan else OutlineSubtle, RoundedCornerShape(8.dp))
                                                        .clickable(onClick = onToggleEndingCard)
                                                        .testTag("timeline_add_ending_button"),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        text = if (hasEndingCard) "LUNA\nEnding ✓" else "+ Add\nending",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                        color = if (hasEndingCard) CyberCyan else TextSecondary,
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.White)
                                                        .clickable(onClick = onAddClipClick)
                                                        .testTag("timeline_add_clip_plus"),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(Icons.Filled.Add, contentDescription = "Append video clip", tint = ObsidianBackground, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            // Anchored Mute Clip button left of 00:00 (does not chase horizontal scroll)
                                            val firstClip = laneSpans.firstOrNull()?.clip
                                            if (firstClip != null && halfViewportPx > 60f) {
                                                val muteX = (halfViewportPx - with(density) { 58.dp.toPx() }).coerceAtLeast(4f)
                                                Row(
                                                    modifier = Modifier
                                                        .offset { IntOffset(muteX.roundToInt(), 0) }
                                                        .fillMaxHeight(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(ObsidianSurfaceContainer)
                                                            .clickable { onToggleMuteClip(firstClip.id) }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                    ) {
                                                        Icon(
                                                            imageVector = if (firstClip.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                                            contentDescription = "Mute clip",
                                                            tint = if (firstClip.isMuted) CyberCyan else Color.White,
                                                            modifier = Modifier.size(14.dp),
                                                        )
                                                        Text("Mute", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = TextSecondary)
                                                    }
                                                }
                                            }
                                        }

                                        // Upper tracks (V2, V3, V4+): Show '+ Add overlay/PIP' pill anchored at 00:00 when empty or trailing '+'
                                        if (trackIdx > 0 && subIdx == 0) {
                                            if (isTrackEmpty) {
                                                Row(
                                                    modifier = Modifier
                                                        .offset { IntOffset(halfViewportPx.roundToInt(), 8) }
                                                        .height((laneHeightDp - 16.dp).coerceAtLeast(24.dp))
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(ObsidianSurfaceContainer)
                                                        .border(1.dp, OutlineSubtle, RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            if (!cfg.isLocked) onAddClipToTrack(trackIdx)
                                                        }
                                                        .padding(horizontal = 10.dp)
                                                        .testTag("add_clip_track_V${trackIdx + 1}"),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(Icons.Filled.Add, null, tint = CyberCyan, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (trackIdx == 1) "+ Overlay / PIP (V2)" else "+ Add to V${trackIdx + 1}",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = TextSecondary,
                                                    )
                                                }
                                            } else {
                                                val trackEndMs = laneSpans.maxOfOrNull { it.endMs } ?: 0L
                                                val plusPx = halfViewportPx + (trackEndMs / 1000f) * pxPerSec + with(density) { 8.dp.toPx() }
                                                Box(
                                                    modifier = Modifier
                                                        .offset { IntOffset(plusPx.roundToInt(), 8) }
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(ObsidianSurfaceHigh)
                                                        .border(1.dp, CyberCyan.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            if (!cfg.isLocked) onAddClipToTrack(trackIdx)
                                                        }
                                                        .testTag("append_clip_track_V${trackIdx + 1}"),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(Icons.Filled.Add, contentDescription = "Add clip to V${trackIdx + 1}", tint = CyberCyan, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(OutlineSubtle.copy(alpha = 0.45f)),
                            )
                        }
                        }
                    }

                    if (overlayLanes.isNotEmpty() || effectLanes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 4. Overlay / Sticker Track Lanes
                    overlayLanes.forEachIndexed { laneIdx, laneItems ->
                        key("ov_lane_$laneIdx") {
                            TimelineTrackLaneRow(
                                items = laneItems,
                                itemId = { it.id },
                                startMsOf = { it.startMs },
                                endMsOf = { it.endMs },
                                labelOf = { it.displayName },
                                pxPerSec = pxPerSec,
                                baseOffsetPx = halfViewportPx,
                                scrollableWidthDp = scrollableWidthDp,
                                rowHeightDp = 34.dp,
                                containerColor = ElectricViolet.copy(alpha = 0.88f),
                                contentColor = Color.White,
                                icon = Icons.Filled.Layers,
                                selectedId = selectedOverlayId,
                                isLocked = false,
                                rowTagPrefix = "timeline_overlay_${laneIdx}",
                                onSelectToggle = { id -> onSelectOverlay(if (id == selectedOverlayId) null else id) },
                                onSelectForce = { id -> onSelectOverlay(id) },
                                onCommitHistory = onCommitHistory,
                                onMoveDeltaMs = onMoveOverlay,
                                onTrimDeltaMs = onTrimOverlayEdge,
                            )
                        }
                    }

                    // 5. Effect Track Lanes
                    effectLanes.forEachIndexed { laneIdx, laneItems ->
                        key("fx_lane_$laneIdx") {
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
                                baseOffsetPx = halfViewportPx,
                                scrollableWidthDp = scrollableWidthDp,
                                rowHeightDp = 34.dp,
                                containerColor = Color(0xFF7C3AED).copy(alpha = 0.9f),
                                contentColor = Color.White,
                                icon = Icons.Filled.AutoAwesome,
                                selectedId = selectedEffectId,
                                isLocked = false,
                                rowTagPrefix = "timeline_effect_${laneIdx}",
                                onSelectToggle = { id -> onSelectEffect(if (id == selectedEffectId) null else id) },
                                onSelectForce = { id -> onSelectEffect(id) },
                                onCommitHistory = onCommitHistory,
                                onMoveDeltaMs = onMoveEffect,
                                onTrimDeltaMs = onTrimEffectEdge,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 6. Audio Track 1 (A1: Music) & Audio Track 2 (A2: Sound / Voiceover)
                    val latestOnMoveAudioToSlot by rememberUpdatedState(onMoveAudioToSlot)
                    audioTrackEntries.forEach { (slot, cfg, lanes) ->
                        key(cfg.id) {
                        val auLaneDp = cfg.heightDp.coerceIn(30f, 100f).dp
                        val auLanePx = with(density) { auLaneDp.toPx() }.coerceAtLeast(28f)
                        val rowHeightDp = auLaneDp * maxOf(1, lanes.size)

                        Box(
                            modifier = Modifier
                                .width(scrollableWidthDp)
                                .height(rowHeightDp)
                                .clipToBounds()
                                .alpha(if (cfg.isHidden || cfg.isMuted) 0.42f else 1f)
                                .background(ObsidianSurfaceContainer.copy(alpha = 0.16f)),
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (lanes.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .width(scrollableWidthDp)
                                            .height(auLaneDp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .offset { IntOffset(halfViewportPx.roundToInt(), 0) }
                                                .height((auLaneDp - 8.dp).coerceAtLeast(22.dp))
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(ObsidianSurfaceContainer)
                                                .clickable {
                                                    if (!cfg.isLocked) onAddAudioToSlot(slot)
                                                }
                                                .padding(horizontal = 10.dp)
                                                .testTag("add_audio_track_A${slot + 1}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Filled.MusicNote, null, tint = LunaMint, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = if (slot == 0) "+ Music (A1)" else "+ Voice / SFX (A2)",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = TextSecondary,
                                            )
                                        }
                                    }
                                } else {
                                    lanes.forEachIndexed { laneIdx, laneItems ->
                                        key("au_${slot}_$laneIdx") {
                                            TimelineTrackLaneRow(
                                                items = laneItems,
                                                itemId = { it.id },
                                                startMsOf = { it.startMs },
                                                endMsOf = { it.endMs },
                                                labelOf = { it.title },
                                                pxPerSec = pxPerSec,
                                                baseOffsetPx = halfViewportPx,
                                                scrollableWidthDp = scrollableWidthDp,
                                                rowHeightDp = auLaneDp,
                                                containerColor = if (slot == 0) Color(0xFF0E7490) else Color(0xFF047857),
                                                contentColor = Color.White,
                                                icon = Icons.Filled.MusicNote,
                                                selectedId = selectedAudioId,
                                                isLocked = cfg.isLocked,
                                                rowTagPrefix = "timeline_audio_${slot}_${laneIdx}",
                                                keyframeTimesAndIds = { au -> au.volumeKeyframes.map { it.id to it.timeMs } },
                                                selectedKfId = selectedVolumeKeyframeId,
                                                diamondColor = LunaMint,
                                                slotIndex = slot,
                                                verticalSwitchThresholdPx = auLanePx * 0.55f,
                                                onSwitchVerticalSlot = { auId, dir ->
                                                    val targetSlot = (dir.coerceIn(-1, 1) + slot).coerceIn(0, 1)
                                                    if (targetSlot != slot) {
                                                        latestOnMoveAudioToSlot(auId, targetSlot)
                                                    }
                                                },
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
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(OutlineSubtle.copy(alpha = 0.35f)),
                            )
                        }
                        }
                    }
                }
            }
        }

        // BUG 4 FIX: Prominent draggable white playhead line + top downward triangle spanning ALL tracks
        Box(
            modifier = Modifier
                .offset(x = TRACK_HEADER_WIDTH + halfViewportDp - 14.dp)
                .width(28.dp)
                .fillMaxHeight()
                .zIndex(20f)
                .pointerInput(totalDurationMs, pxPerSec) {
                    var accumMs = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumMs = 0f
                            latestOnScrubStart()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumMs += (dragAmount / latestPxPerSec) * 1000f
                            val wholeMs = accumMs.toLong()
                            if (wholeMs != 0L) {
                                accumMs -= wholeMs
                                val nextMs = (latestPlayheadMs + wholeMs).coerceIn(0L, latestTotalDurationMs)
                                latestOnUpdatePlayhead(nextMs)
                            }
                        },
                    )
                }
                .testTag("timeline_playhead"),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * 0.5f
                val triHalfW = 7.dp.toPx()
                val triH = 10.dp.toPx()
                val tri = Path().apply {
                    moveTo(cx - triHalfW, 0f)
                    lineTo(cx + triHalfW, 0f)
                    lineTo(cx, triH)
                    close()
                }
                drawPath(tri, color = Color.White)
                drawLine(
                    color = Color.White,
                    start = Offset(cx, triH * 0.7f),
                    end = Offset(cx, size.height),
                    strokeWidth = 2.5.dp.toPx(),
                )
            }
        }
    }
}

@Composable
private fun TrackControlHeaderCell(
    config: TimelineTrackConfig,
    rowHeightDp: Dp,
    accentColor: Color,
    showMute: Boolean,
    onToggleHidden: () -> Unit,
    onToggleLocked: () -> Unit,
    onToggleMuted: () -> Unit,
    onResizeHeightDp: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val latestHeight by rememberUpdatedState(config.heightDp)
    val latestOnResize by rememberUpdatedState(onResizeHeightDp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeightDp)
            .background(ObsidianSurface)
            .padding(horizontal = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 2.dp, bottom = 7.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = config.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = if (config.isHidden) TextSecondary.copy(alpha = 0.45f) else accentColor,
                    maxLines = 1,
                )
                if (config.subtitle.isNotBlank() && rowHeightDp >= 44.dp) {
                    Text(
                        text = config.subtitle.substringBefore(" "),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                        color = TextSecondary.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (config.isHidden) ObsidianSurfaceHigh else Color.Transparent)
                        .clickable(onClick = onToggleHidden)
                        .testTag("track_hide_${config.label}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (config.isHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Hide ${config.label}",
                        tint = if (config.isHidden) StudioAmber else TextSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (config.isLocked) ObsidianSurfaceHigh else Color.Transparent)
                        .clickable(onClick = onToggleLocked)
                        .testTag("track_lock_${config.label}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (config.isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = "Lock ${config.label}",
                        tint = if (config.isLocked) CyberCyan else TextSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                }
                if (showMute) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (config.isMuted) ObsidianSurfaceHigh else Color.Transparent)
                            .clickable(onClick = onToggleMuted)
                            .testTag("track_mute_${config.label}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (config.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute ${config.label}",
                            tint = if (config.isMuted) Color(0xFFEF4444) else TextSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }

        // Bottom vertical height resize grip
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(7.dp)
                .pointerInput(config.id) {
                    var trackedH = latestHeight
                    detectVerticalDragGestures(
                        onDragStart = { trackedH = latestHeight },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.toDp().value }
                            trackedH = (trackedH + deltaDp).coerceIn(30f, 120f)
                            latestOnResize(trackedH)
                        },
                    )
                }
                .testTag("track_resize_${config.label}"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(OutlineSubtle.copy(alpha = 0.7f)),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(OutlineSubtle.copy(alpha = 0.45f)),
        )
    }
}

@Composable
private fun CompactAuxiliaryTrackHeader(
    label: String,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(ObsidianSurface)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = accentColor,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(OutlineSubtle.copy(alpha = 0.35f)),
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
    baseOffsetPx: Float,
    scrollableWidthDp: Dp,
    rowHeightDp: Dp = 34.dp,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedId: String?,
    isLocked: Boolean = false,
    rowTagPrefix: String,
    keyframeTimesAndIds: (T) -> List<Pair<String, Long>> = { emptyList() },
    selectedKfId: String? = null,
    diamondColor: Color = CyberCyan,
    slotIndex: Int = 0,
    verticalSwitchThresholdPx: Float = 24f,
    onSwitchVerticalSlot: ((id: String, dirDelta: Int) -> Unit)? = null,
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
    val latestLocked by rememberUpdatedState(isLocked)
    val latestSlotIndex by rememberUpdatedState(slotIndex)
    val latestOnSwitchSlot by rememberUpdatedState(onSwitchVerticalSlot)

    Box(
        modifier = Modifier
            .width(scrollableWidthDp)
            .height(rowHeightDp)
            .clipToBounds()
            .padding(vertical = 2.dp),
    ) {
        items.forEachIndexed { idx, item ->
            val id = itemId(item)
            val startMs = startMsOf(item)
            val endMs = endMsOf(item)
            val isSelected = id == selectedId
            val startPx = baseOffsetPx + (startMs / 1000f) * pxPerSec
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
                        var accumDy = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                if (latestLocked) return@detectDragGesturesAfterLongPress
                                isDraggingBody = true
                                accumMs = 0f
                                accumDy = 0f
                                latestOnSelectForce(id)
                                latestOnCommit()
                            },
                            onDrag = { change, dragAmount ->
                                if (latestLocked) return@detectDragGesturesAfterLongPress
                                change.consume()
                                accumMs += (dragAmount.x / latestPxPerSec) * 1000f
                                val wholeMs = accumMs.toLong()
                                if (wholeMs != 0L) {
                                    accumMs -= wholeMs
                                    latestOnMove(id, wholeMs)
                                }
                                if (latestOnSwitchSlot != null) {
                                    accumDy += dragAmount.y
                                    if (abs(accumDy) >= verticalSwitchThresholdPx) {
                                        // Audio slots go downward: A1 (0) is above A2 (1), so +dy switches +1
                                        val dir = if (accumDy > 0f) 1 else -1
                                        if ((latestSlotIndex + dir) != latestSlotIndex) {
                                            latestOnSwitchSlot?.invoke(id, dir)
                                        }
                                        accumDy = 0f
                                    }
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

                if (isSelected && !isLocked) {
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

            // Interactive tap-selectable Keyframe Diamonds centered cleanly on playhead coordinates
            val kfs = keyframeTimesAndIds(item)
            if (kfs.isNotEmpty() && onSelectKeyframe != null) {
                val durF = (endMs - startMs).coerceAtLeast(1L).toFloat()
                val barWpx = with(density) { barWidthDp.toPx() }
                kfs.forEach { (kfId, tMs) ->
                    val frac = (tMs / durF).coerceIn(0f, 1f)
                    val cx = startPx + frac * barWpx - with(density) { 11.dp.toPx() }
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
    maxWidthDp: Dp,
    desiredHandleWidth: Dp,
    cornerRadius: Dp,
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

private fun formatRulerSeconds(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}
