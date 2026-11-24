package com.example.domain

import com.example.data.model.AdjustSettings
import com.example.data.model.CurvePoint
import com.example.data.model.EasingGraph
import com.example.data.model.KeyframeEvaluator
import com.example.data.model.KeyframeNode
import com.example.data.model.RgbCurvesSpec
import com.example.data.model.SpeedMode
import com.example.data.model.SpeedSettings
import com.example.data.model.TextKeyframeEvaluator
import com.example.data.model.TextKeyframeNode
import com.example.data.model.TextTrackItem
import com.example.data.model.TransformSettings
import com.example.data.model.VideoClipSegment
import com.example.data.model.VolumeKeyframeEvaluator
import com.example.data.model.VolumeKeyframeNode
import com.example.data.model.fillCanvasZoomPercent
import com.example.domain.video.RgbCurveLutBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapCutEngineTest {

    @Test
    fun easingGraphs_endpointsAndSmoothBehavior() {
        EasingGraph.entries.forEach { graph ->
            val start = graph.evaluate(0f)
            val end = graph.evaluate(1f)
            assertEquals("End of ${graph.label}", 1f, end, 0.05f)
            if (graph != EasingGraph.NONE) {
                assertEquals("Start of ${graph.label}", 0f, start, 0.05f)
            }
            val mid = graph.evaluate(0.5f)
            assertTrue("Finite midpoint for ${graph.label}: $mid", mid.isFinite())
        }
        // Step hold check
        assertEquals(0f, EasingGraph.NONE.evaluate(0.75f), 0.001f)
        // Bounce intermediate height stays bounded & oscillates
        assertTrue(EasingGraph.BOUNCE.evaluate(0.8f) > 0.7f)
    }

    @Test
    fun keyframeEvaluator_interpolatesZoomPositionOpacityWithEasing() {
        val k1 = KeyframeNode(
            id = "k1",
            timeMs = 1_000L,
            easingToNext = EasingGraph.QUAD_IN,
            posX = 0f,
            zoomPercent = 100f,
            rotateDeg = 0f,
            opacity = 1f,
        )
        val k2 = KeyframeNode(
            id = "k2",
            timeMs = 3_000L,
            easingToNext = EasingGraph.LINEAR,
            posX = 0.8f,
            zoomPercent = 200f,
            rotateDeg = 180f,
            opacity = 0.2f,
        )
        val evalMid = KeyframeEvaluator.evaluate(
            keyframes = listOf(k1, k2),
            localTimeMs = 2_000L, // t = 0.5 -> QUAD_IN(0.5) = 0.25
            baseTransform = TransformSettings(),
            baseOpacity = 1f,
            baseAdjust = AdjustSettings(),
            baseFilterIntensity = 1f,
        )
        assertEquals(0.2f, evalMid.posX, 0.01f)
        assertEquals(125f, evalMid.zoomPercent, 0.5f)
        assertEquals(45f, evalMid.rotateDeg, 0.5f)
        assertEquals(0.8f, evalMid.opacity, 0.02f)

        val seg = KeyframeEvaluator.surroundingSegment(listOf(k1, k2), 2_000L)
        assertNotNull(seg)
        assertEquals("k1", seg?.first?.id)
        assertEquals("k2", seg?.second?.id)
    }

    @Test
    fun speedSettings_supports01xTo100xAndCurveSampling() {
        val fastClip = VideoClipSegment(
            trimStartMs = 0L,
            trimEndMs = 10_000L,
            speed = SpeedSettings(mode = SpeedMode.NORMAL, normalSpeed = 10f),
        )
        assertEquals(1_000L, fastClip.timelineDurationMs())

        val slowClip = VideoClipSegment(
            trimStartMs = 0L,
            trimEndMs = 2_000L,
            speed = SpeedSettings(mode = SpeedMode.NORMAL, normalSpeed = 0.1f),
        )
        assertEquals(20_000L, slowClip.timelineDurationMs())

        val curve = SpeedSettings(
            mode = SpeedMode.CURVE,
            curvePoints = listOf(1.0f, 2.0f, 4.0f, 2.0f, 1.0f),
        )
        assertEquals(4.0f, curve.speedAtProgress(0.5f), 0.01f)
    }

    @Test
    fun rgbCurveLutBuilder_generatesValid17CubeFromCurves() {
        val liftedWhite = RgbCurvesSpec(
            white = listOf(
                CurvePoint(0f, 0.1f),
                CurvePoint(0.5f, 0.65f),
                CurvePoint(1f, 1f),
            ),
            red = RgbCurvesSpec.defaultCurvePoints(),
            green = RgbCurvesSpec.defaultCurvePoints(),
            blue = RgbCurvesSpec.defaultCurvePoints(),
        )
        val adjust = AdjustSettings(rgbCurves = liftedWhite, fade = 20f)
        val cube = RgbCurveLutBuilder.buildCombinedLut(adjust)
        assertEquals(17, cube.size)
        assertEquals(17, cube[0].size)
        assertEquals(17, cube[0][0].size)
        // Black floor [0][0][0] should be visibly lifted > 20 due to lifted White curve + Fade
        val r0 = (cube[0][0][0] shr 16) and 0xFF
        assertTrue("Expected black floor lift, got $r0", r0 > 20)
    }

    @Test
    fun trackTrimming_startAndBehindClampSafelyAcrossClipsEffectsOverlaysAndTexts() {
        val clip = VideoClipSegment(
            trimStartMs = 0L,
            trimEndMs = 8_000L,
            sourceDurationMs = 8_000L,
            speed = SpeedSettings(normalSpeed = 2f),
        )
        // Trimming 1000ms timeline from start at 2x speed removes 2000ms source
        val afterStartTrim = clip.copy(
            trimStartMs = (clip.trimStartMs + (1_000L * clip.speed.effectiveSpeedMultiplier()).toLong())
                .coerceIn(0L, clip.trimEndMs - 300L),
        )
        assertEquals(2_000L, afterStartTrim.trimStartMs)
        // Trimming 500ms timeline from behind at 2x speed trims end back 1000ms source
        val afterEndTrim = afterStartTrim.copy(
            trimEndMs = (afterStartTrim.trimEndMs - (500L * clip.speed.effectiveSpeedMultiplier()).toLong())
                .coerceIn(afterStartTrim.trimStartMs + 300L, afterStartTrim.sourceDurationMs),
        )
        assertEquals(7_000L, afterEndTrim.trimEndMs)
        assertEquals(2_500L, afterEndTrim.timelineDurationMs())

        val fx = com.example.data.model.VideoEffectLayer(
            type = com.example.data.model.CapCutEffectType.FLASH_COPIES,
            startMs = 0L,
            endMs = 5_000L,
        )
        val fxStartTrim = fx.copy(startMs = (fx.startMs + 1_200L).coerceIn(0L, fx.endMs - 300L))
        val fxEndTrim = fxStartTrim.copy(endMs = (fxStartTrim.endMs - 1_500L).coerceIn(fxStartTrim.startMs + 300L, 8_000L))
        assertEquals(1_200L, fxEndTrim.startMs)
        assertEquals(3_500L, fxEndTrim.endMs)

        val tx = com.example.data.model.TextTrackItem(text = "hi", startMs = 500L, endMs = 4_000L)
        val txClampedStart = tx.copy(startMs = (tx.startMs + 9_000L).coerceIn(0L, tx.endMs - 300L))
        assertEquals(3_700L, txClampedStart.startMs)
    }

    @Test
    fun splitAndMoveAcrossAllTrackTypes_keepBothPartsContiguousAndSideBySide() {
        val textPart1 = com.example.data.model.TextTrackItem(
            id = "t1",
            text = "Part 1",
            startMs = 0L,
            endMs = 3_000L,
            posX = -0.55f,
            posY = 0.62f,
            scale = 1.45f,
            rotationDeg = 22f,
        )
        val textPart2 = textPart1.copy(id = "t2", text = "Part 2", startMs = 3_000L, endMs = 6_000L)

        // Lane packing puts contiguous split parts onto the exact same lane (side-by-side)
        val lanes = com.example.ui.studio.assignTrackLanes(
            items = listOf(textPart2, textPart1),
            startOf = { it.startMs },
            endOf = { it.endMs },
        )
        assertEquals(1, lanes.size)
        assertEquals(listOf("t1", "t2"), lanes[0].map { it.id })

        // Contiguous tie-breaking: Part 1 active before 3000ms, Part 2 active at and after 3000ms
        assertTrue(com.example.data.model.isTimelineItemActiveAt(0L, 3_000L, 1_500L, hasNextContiguous = true))
        assertTrue(!com.example.data.model.isTimelineItemActiveAt(0L, 3_000L, 3_000L, hasNextContiguous = true))
        assertTrue(com.example.data.model.isTimelineItemActiveAt(3_000L, 6_000L, 3_000L, hasNextContiguous = false))
        assertTrue(com.example.data.model.isTimelineItemActiveAt(3_000L, 6_000L, 4_500L, hasNextContiguous = false))

        // VideoClipSegment move left/right & left-edge trim keep timelineStartMs anchored properly
        val c1 = VideoClipSegment(id = "c1", timelineStartMs = 0L, trimStartMs = 0L, trimEndMs = 3_000L, sourceDurationMs = 8_000L)
        val c2 = VideoClipSegment(id = "c2", timelineStartMs = 5_000L, trimStartMs = 3_000L, trimEndMs = 8_000L, sourceDurationMs = 8_000L)
        val spans = com.example.domain.video.VideoEffectsEngine.computeClipSpans(listOf(c2, c1))
        assertEquals("c1", spans[0].clip.id)
        assertEquals(0L, spans[0].startMs)
        assertEquals(3_000L, spans[0].endMs)
        assertEquals("c2", spans[1].clip.id)
        assertEquals(5_000L, spans[1].startMs)
        assertEquals(10_000L, spans[1].endMs)

        // Playhead in gap [3000..5000] returns -1, inside clips returns matching index
        assertEquals(0, com.example.data.model.findActiveSpanIndex(spans, 2_000L))
        assertEquals(-1, com.example.data.model.findActiveSpanIndex(spans, 4_000L))
        assertEquals(1, com.example.data.model.findActiveSpanIndex(spans, 6_500L))
    }

    @Test
    fun fillCanvasZoomPercent_fillsPortraitSquareAndRotatedFramesWithoutBlackBars() {
        // 16:9 landscape fitted into 9:16 portrait requires (16/9)/(9/16) = 316.05% zoom
        val landscapeInPortrait = fillCanvasZoomPercent(1920, 1080, 9f / 16f, rotationDeg = 0f)
        assertEquals(316.05f, landscapeInPortrait, 1.0f)

        // 1:1 square inside 9:16 portrait requires 16/9 = 177.78%
        val squareInPortrait = fillCanvasZoomPercent(1080, 1080, 9f / 16f, rotationDeg = 0f)
        assertEquals(177.78f, squareInPortrait, 0.8f)

        // Same-aspect portrait 9:16 inside 9:16 requires 100%
        val exactMatch = fillCanvasZoomPercent(1080, 1920, 9f / 16f, rotationDeg = 0f)
        assertEquals(100f, exactMatch, 0.2f)

        // Rotating 9:16 clip by 90 degrees inside 9:16 requires ~177.78% zoom to cover top/bottom
        val rotatedPortrait = fillCanvasZoomPercent(1080, 1920, 9f / 16f, rotationDeg = 90f)
        assertEquals(177.78f, rotatedPortrait, 0.8f)
    }

    @Test
    fun textKeyframeEvaluator_interpolatesTextPositionScaleRotationAndOpacity() {
        val base = TextTrackItem(text = "Title", startMs = 1_000L, endMs = 5_000L)
        val k1 = TextKeyframeNode(
            id = "tk1",
            timeMs = 0L,
            easingToNext = EasingGraph.LINEAR,
            posX = -0.5f,
            posY = -0.5f,
            scale = 1.0f,
            rotationDeg = 0f,
            opacity = 1.0f,
        )
        val k2 = TextKeyframeNode(
            id = "tk2",
            timeMs = 2_000L,
            easingToNext = EasingGraph.LINEAR,
            posX = 0.5f,
            posY = 0.5f,
            scale = 2.0f,
            rotationDeg = 90f,
            opacity = 0.4f,
        )
        val mid = TextKeyframeEvaluator.evaluate(listOf(k1, k2), 1_000L, base)
        assertEquals(0f, mid.posX, 0.01f)
        assertEquals(0f, mid.posY, 0.01f)
        assertEquals(1.5f, mid.scale, 0.01f)
        assertEquals(45f, mid.rotationDeg, 0.5f)
        assertEquals(0.7f, mid.opacity, 0.01f)

        val seg = TextKeyframeEvaluator.surroundingSegment(listOf(k1, k2), 1_000L)
        assertNotNull(seg)
        assertEquals("tk1", seg?.first?.id)
        assertEquals("tk2", seg?.second?.id)
    }

    @Test
    fun volumeKeyframeEvaluator_interpolatesIndependentlyFromTransformKeyframes() {
        val clip = VideoClipSegment(
            trimStartMs = 0L,
            trimEndMs = 4_000L,
            volumePercent = 100,
            keyframes = listOf(
                KeyframeNode(id = "tk1", timeMs = 0L, zoomPercent = 100f),
                KeyframeNode(id = "tk2", timeMs = 4_000L, zoomPercent = 250f),
            ),
            volumeKeyframes = listOf(
                VolumeKeyframeNode(id = "vk1", timeMs = 0L, volumePercent = 100),
                VolumeKeyframeNode(id = "vk2", timeMs = 2_000L, volumePercent = 0),
                VolumeKeyframeNode(id = "vk3", timeMs = 4_000L, volumePercent = 100),
            ),
        )
        assertEquals(100, VolumeKeyframeEvaluator.evaluate(clip.volumeKeyframes, 0L, clip.volumePercent))
        assertEquals(50, VolumeKeyframeEvaluator.evaluate(clip.volumeKeyframes, 1_000L, clip.volumePercent))
        assertEquals(0, VolumeKeyframeEvaluator.evaluate(clip.volumeKeyframes, 2_000L, clip.volumePercent))
        assertEquals(50, VolumeKeyframeEvaluator.evaluate(clip.volumeKeyframes, 3_000L, clip.volumePercent))
        assertEquals(100, VolumeKeyframeEvaluator.evaluate(clip.volumeKeyframes, 4_000L, clip.volumePercent))

        // Without volume keyframes, slider/base volume applies to whole clip even with transform keyframes present
        val withoutVolKfs = clip.copy(volumeKeyframes = emptyList(), volumePercent = 75)
        assertEquals(75, VolumeKeyframeEvaluator.evaluate(withoutVolKfs.volumeKeyframes, 2_000L, withoutVolKfs.volumePercent))
    }

    @Test
    fun totalDuration_equalsVideoClipsOnlyAndSpanMaps19sContiguously() {
        val clip1 = VideoClipSegment(id = "c1", timelineStartMs = 0L, trimStartMs = 0L, trimEndMs = 10_000L, sourceDurationMs = 10_000L)
        val clip2 = VideoClipSegment(id = "c2", timelineStartMs = 10_000L, trimStartMs = 0L, trimEndMs = 9_000L, sourceDurationMs = 9_000L)
        val clips = listOf(clip1, clip2)
        // Total duration strictly equals sum of video clips (19,000ms), never inflated by Text/Audio/Ending
        assertEquals(19_000L, com.example.data.model.computeTotalVideoDurationMs(clips))

        val spans = com.example.domain.video.VideoEffectsEngine.computeClipSpans(clips)
        assertEquals(0L, spans[0].startMs)
        assertEquals(10_000L, spans[0].endMs)
        assertEquals(10_000L, spans[1].startMs)
        assertEquals(19_000L, spans[1].endMs)
        assertEquals(0, com.example.data.model.findPreviewSpanIndex(spans, 2_000L))
        assertEquals(1, com.example.data.model.findPreviewSpanIndex(spans, 15_000L))
        assertEquals(1, com.example.data.model.findPreviewSpanIndex(spans, 19_000L))
    }

    @Test
    fun textVisibility_strictWindowAndPerClipAdjustIsolation() {
        // 3s text starting at 5s is hidden before 5s and hidden cleanly at/after 8s
        assertTrue(!com.example.data.model.isTimelineItemActiveAt(5_000L, 8_000L, 4_999L))
        assertTrue(com.example.data.model.isTimelineItemActiveAt(5_000L, 8_000L, 5_000L))
        assertTrue(com.example.data.model.isTimelineItemActiveAt(5_000L, 8_000L, 7_999L))
        assertTrue(!com.example.data.model.isTimelineItemActiveAt(5_000L, 8_000L, 8_000L))

        // Per-clip Adjust & Transform values stay strictly isolated between clips
        val c1 = VideoClipSegment(id = "c1", adjust = AdjustSettings(brightness = 35f, contrast = 15f))
        val c2 = VideoClipSegment(id = "c2", adjust = AdjustSettings())
        assertEquals(35f, c1.adjust.brightness, 0.01f)
        assertEquals(0f, c2.adjust.brightness, 0.01f)
    }

    @Test
    fun persistedProjectState_andShortReverseCutRule() {
        val state = com.example.data.model.PersistedProjectState(
            projectId = "proj-1",
            title = "My Edit",
            playheadMs = 4_250L,
            snapshot = com.example.data.model.TimelineSnapshot(
                clips = listOf(
                    VideoClipSegment(
                        id = "v1",
                        trimStartMs = 0L,
                        trimEndMs = 800L,
                        sourceDurationMs = 800L,
                        isReversed = true,
                        reverseAudio = true,
                        adjust = AdjustSettings(saturation = 18f),
                    ),
                ),
                textTracks = listOf(TextTrackItem(id = "tx1", text = "Hello", startMs = 1_000L, endMs = 3_000L)),
            ),
        )
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(com.example.data.model.PersistedProjectState.serializer(), state)
        val decoded = json.decodeFromString(com.example.data.model.PersistedProjectState.serializer(), encoded)
        assertEquals("proj-1", decoded.projectId)
        assertEquals(4_250L, decoded.playheadMs)
        assertEquals(18f, decoded.snapshot.clips.first().adjust.saturation, 0.01f)
        assertEquals("Hello", decoded.snapshot.textTracks.first().text)

        val shortClip = VideoClipSegment(id = "s", trimStartMs = 0L, trimEndMs = 750L, sourceDurationMs = 750L)
        val longClip = VideoClipSegment(id = "l", trimStartMs = 0L, trimEndMs = 2_500L, sourceDurationMs = 2_500L)
        // Single short clip reversed alone -> Style A (no flash/punch cut)
        assertTrue(!com.example.ui.studio.StudioViewModel.shouldApplyShortReverseCutEffect(shortClip, isBatchReverseAll = false))
        // Batch Reverse All Clips with 0.5s-1.0s clip -> Style B (flash/punch cut active)
        assertTrue(com.example.ui.studio.StudioViewModel.shouldApplyShortReverseCutEffect(shortClip, isBatchReverseAll = true))
        // Batch Reverse All Clips with >1.0s clip -> Style A
        assertTrue(!com.example.ui.studio.StudioViewModel.shouldApplyShortReverseCutEffect(longClip, isBatchReverseAll = true))
    }
}

