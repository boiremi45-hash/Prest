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

    @Test
    fun keyframeZoomAndPositionOnly_keepsRotationLockedAtZero() {
        val k1 = KeyframeNode(
            id = "k1",
            timeMs = 0L,
            easingToNext = EasingGraph.LINEAR,
            posX = 0f,
            posY = 0f,
            zoomPercent = 100f,
            rotateDeg = 0f,
        )
        val k2 = KeyframeNode(
            id = "k2",
            timeMs = 4_000L,
            easingToNext = EasingGraph.LINEAR,
            posX = 0.45f,
            posY = -0.30f,
            zoomPercent = 240f,
            rotateDeg = 0f,
        )
        listOf(0L, 1_000L, 2_000L, 3_000L, 4_000L).forEach { t ->
            val eval = KeyframeEvaluator.evaluate(
                keyframes = listOf(k1, k2),
                localTimeMs = t,
                baseTransform = TransformSettings(),
                baseOpacity = 1f,
                baseAdjust = AdjustSettings(),
                baseFilterIntensity = 1f,
            )
            assertEquals("Rotation must stay locked at 0 deg at $t ms", 0f, eval.rotateDeg, 0.0001f)
        }
        val mid = KeyframeEvaluator.evaluate(
            keyframes = listOf(k1, k2),
            localTimeMs = 2_000L,
            baseTransform = TransformSettings(),
            baseOpacity = 1f,
            baseAdjust = AdjustSettings(),
            baseFilterIntensity = 1f,
        )
        assertEquals(170f, mid.zoomPercent, 0.01f)
        assertEquals(0.225f, mid.posX, 0.001f)
        assertEquals(-0.15f, mid.posY, 0.001f)

        val txBase = TextTrackItem(text = "ZoomOnly", startMs = 0L, endMs = 4_000L)
        val tk1 = TextKeyframeNode(id = "tk1", timeMs = 0L, scale = 1.0f, rotationDeg = 0f)
        val tk2 = TextKeyframeNode(id = "tk2", timeMs = 4_000L, scale = 2.2f, rotationDeg = 0f)
        val txMid = TextKeyframeEvaluator.evaluate(listOf(tk1, tk2), 2_000L, txBase)
        assertEquals(1.6f, txMid.scale, 0.01f)
        assertEquals(0f, txMid.rotationDeg, 0.0001f)
    }

    @Test
    fun timelineWidth_scalesProportionallyWithDurationAt60PxPerSecond() {
        val pxPerSec = 60f
        val oneSecWidth = com.example.data.model.computeTimelineWidthPx(1_000L, pxPerSec)
        val twentySecWidth = com.example.data.model.computeTimelineWidthPx(20_000L, pxPerSec)
        assertEquals(60f, oneSecWidth, 0.01f)
        assertEquals(1200f, twentySecWidth, 0.01f)
        assertEquals(20f, twentySecWidth / oneSecWidth, 0.01f)
    }

    @Test
    fun smoothReverseSpeedCurve_bezierRampMirrorsWithZeroDiscontinuityAtJoin() {
        val clip1Speed = SpeedSettings(
            mode = SpeedMode.CURVE,
            smoothSlowMo = true,
            smoothSlowMoBetterQuality = true,
            curvePoints = listOf(10.0f, 0.1f),
            curveControlPoints = listOf(
                com.example.data.model.SpeedCurvePoint(0f, 10.0f),
                com.example.data.model.SpeedCurvePoint(1f, 0.1f),
            ),
            curveBezierSmooth = true,
        )
        val clip2Speed = clip1Speed.mirrored()

        assertEquals(10.0f, clip1Speed.speedAtProgress(0f), 0.001f)
        assertEquals(0.1f, clip1Speed.speedAtProgress(1f), 0.001f)
        // Smooth bezier midpoint falls cleanly halfway (5.05x) while staying strictly monotone
        assertEquals(5.05f, clip1Speed.speedAtProgress(0.5f), 0.05f)
        assertTrue(clip1Speed.speedAtProgress(0.25f) > clip1Speed.speedAtProgress(0.5f))
        assertTrue(clip1Speed.speedAtProgress(0.5f) > clip1Speed.speedAtProgress(0.75f))

        // Mirrored curve matches exact 0.1X at join point with zero jump and rises to 10.0X
        assertEquals(0.1f, clip2Speed.speedAtProgress(0f), 0.001f)
        assertEquals(10.0f, clip2Speed.speedAtProgress(1f), 0.001f)
        assertEquals(clip1Speed.speedAtProgress(1f), clip2Speed.speedAtProgress(0f), 0.0001f)

        // Timeline <-> source progress round-trip consistency across curved speed
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { sp ->
            val tp = clip1Speed.timelineProgressAtSourceProgress(sp)
            val back = clip1Speed.sourceProgressAtTimelineProgress(tp)
            assertEquals("Round-trip mismatch at sp=$sp", sp, back, 0.03f)
        }
    }

    @Test
    fun smoothReverseAutoTrim_selectsBest02To05sSegmentAndPreservesShortClips() {
        val longClip = VideoClipSegment(
            id = "long",
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 10_000L,
            sourceDurationMs = 10_000L,
        )
        val longWindow = com.example.ui.studio.StudioViewModel.selectBestSmoothReverseWindow(longClip)
        val longLen = longWindow.second - longWindow.first
        assertTrue("Expected 200..500ms window, got $longLen", longLen in 200L..500L)
        assertEquals(400L, longLen)

        val beatAnchoredClip = longClip.copy(beatMarkersMs = listOf(4_500L))
        val beatWindow = com.example.ui.studio.StudioViewModel.selectBestSmoothReverseWindow(beatAnchoredClip)
        assertEquals(400L, beatWindow.second - beatWindow.first)
        assertEquals(4_500L, beatWindow.second)

        val shortClip = VideoClipSegment(
            id = "short",
            timelineStartMs = 0L,
            trimStartMs = 120L,
            trimEndMs = 470L, // 350ms already <= 500ms
            sourceDurationMs = 1_000L,
        )
        val shortWindow = com.example.ui.studio.StudioViewModel.selectBestSmoothReverseWindow(shortClip)
        assertEquals(120L, shortWindow.first)
        assertEquals(470L, shortWindow.second)
    }

    @Test
    fun smoothReversePair_buildsContiguousMirroredPairWithCubicEasingAndZeroRotation() {
        val sourceClip = VideoClipSegment(
            id = "c1",
            sourceUriString = "file:///tmp/sample.mp4",
            displayName = "Clip 1",
            timelineStartMs = 2_000L,
            trimStartMs = 0L,
            trimEndMs = 8_000L,
            sourceDurationMs = 8_000L,
        )
        val (clip1, clip2) = com.example.ui.studio.StudioViewModel.buildSmoothReversePair(
            sourceClip = sourceClip,
            reversedUriString = "file:///tmp/sample_rev.mp4",
            reversedClipId = "c2",
            groupId = "grp-1",
        )

        assertEquals(com.example.data.model.SmoothReverseRole.ORIGINAL, clip1.smoothReverseRole)
        assertEquals(com.example.data.model.SmoothReverseRole.REVERSED, clip2.smoothReverseRole)
        assertEquals("grp-1", clip1.smoothReverseGroupId)
        assertEquals("grp-1", clip2.smoothReverseGroupId)

        // Step 2: Better Quality Smooth Slow-Mo & 10.0x <-> 0.1x speed curves
        assertTrue(clip1.speed.smoothSlowMo && clip1.speed.smoothSlowMoBetterQuality)
        assertTrue(clip2.speed.smoothSlowMo && clip2.speed.smoothSlowMoBetterQuality)
        assertEquals(10.0f, clip1.speed.speedAtProgress(0f), 0.01f)
        assertEquals(0.1f, clip1.speed.speedAtProgress(1f), 0.01f)
        assertEquals(0.1f, clip2.speed.speedAtProgress(0f), 0.01f)
        assertEquals(10.0f, clip2.speed.speedAtProgress(1f), 0.01f)

        // Step 3 & 5: Cubic Out on Clip 1, Cubic In on Clip 2, Zoom 100%->140%->100%, Y 0->+0.20->0, Rotation locked 0
        assertEquals(2, clip1.keyframes.size)
        assertEquals(EasingGraph.CUBIC_OUT, clip1.keyframes[0].easingToNext)
        assertEquals(100f, clip1.keyframes[0].zoomPercent, 0.01f)
        assertEquals(140f, clip1.keyframes[1].zoomPercent, 0.01f)
        assertEquals(0f, clip1.keyframes[0].posY, 0.001f)
        assertEquals(0.20f, clip1.keyframes[1].posY, 0.001f)

        assertEquals(2, clip2.keyframes.size)
        assertEquals(EasingGraph.CUBIC_IN, clip2.keyframes[0].easingToNext)
        assertEquals(140f, clip2.keyframes[0].zoomPercent, 0.01f)
        assertEquals(100f, clip2.keyframes[1].zoomPercent, 0.01f)
        assertEquals(0.20f, clip2.keyframes[0].posY, 0.001f)
        assertEquals(0f, clip2.keyframes[1].posY, 0.001f)

        val d1 = clip1.timelineDurationMs()
        listOf(0L, d1 / 4, d1 / 2, (d1 * 3) / 4, d1).forEach { t ->
            val e1 = KeyframeEvaluator.evaluate(clip1.keyframes, t, clip1.transform, 1f, clip1.adjust, 1f)
            assertEquals(0f, e1.rotateDeg, 0.0001f)
            val e2 = KeyframeEvaluator.evaluate(clip2.keyframes, t, clip2.transform, 1f, clip2.adjust, 1f)
            assertEquals(0f, e2.rotateDeg, 0.0001f)
        }

        // Step 4: Clip 2 reversed & meets Clip 1 with zero gap and zero overlap
        assertTrue(clip2.isReversed)
        assertEquals(clip1.timelineStartMs + clip1.timelineDurationMs(), clip2.timelineStartMs)
        assertEquals(clip1.baseDurationMs(), clip2.baseDurationMs())
    }

    @Test
    fun timesliceNodeMode_viralDefaultsAnimateAndLegacyMigration() {
        val viral = com.example.data.model.TimesliceSettings.viralNodePreset()
        assertTrue(viral.isNodeModeActive())
        assertTrue(!viral.isCapCutModeActive())
        assertEquals(5, viral.clampedSliceCount())
        assertEquals(0f, viral.clampedAngleDeg(), 0.01f)
        assertEquals(2, viral.clampedFrameInterval())
        assertEquals(25, viral.clampedFrameOffset())
        assertEquals(0.50f, viral.clampedFeather(), 0.001f)
        assertTrue(!viral.animate)

        // Frame time formula: T + (i * FrameInterval + FrameOffset) * frameDurationMs at 30fps
        val fps = 30f
        val frameMs = 1000f / fps
        val t0 = viral.sampleNodeTimeMs(0L, 0, 0L, 4_000L, fps)
        val t1 = viral.sampleNodeTimeMs(0L, 1, 0L, 4_000L, fps)
        val t4 = viral.sampleNodeTimeMs(0L, 4, 0L, 4_000L, fps)
        assertEquals((25f * frameMs).toLong(), t0)
        assertEquals(((2f + 25f) * frameMs).toLong(), t1)
        assertEquals(((8f + 25f) * frameMs).toLong(), t4)
        assertTrue(t4 > t1 && t1 > t0)

        // Animate = true increases effective offset over elapsed playback time
        val animated = viral.copy(animate = true)
        assertTrue(animated.effectiveOffsetFrames(1_000L, fps) > animated.effectiveOffsetFrames(0L, fps))

        // Legacy saved project JSON containing old Timeslice fields migrates cleanly to Node Mode (5, 0, 2, 25, 0.50)
        val legacyJson = """
            {
              "projectId": "legacy-ts",
              "title": "Old Project",
              "snapshot": {
                "clips": [
                  {
                    "id": "clip-1",
                    "timeslice": {
                      "enabled": true,
                      "slices": 10,
                      "direction": "LEFT_TO_RIGHT",
                      "timeSpreadSec": 0.3,
                      "feather": 25.0,
                      "intensity": 1.0,
                      "previewEnabled": true
                    }
                  }
                ]
              }
            }
        """.trimIndent()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val migrated = json.decodeFromString(com.example.data.model.PersistedProjectState.serializer(), legacyJson)
        val migratedTs = migrated.snapshot.clips.first().timeslice
        assertTrue(migratedTs.isNodeModeActive())
        assertEquals(5, migratedTs.clampedSliceCount())
        assertEquals(0f, migratedTs.clampedAngleDeg(), 0.01f)
        assertEquals(2, migratedTs.clampedFrameInterval())
        assertEquals(25, migratedTs.clampedFrameOffset())
        assertEquals(0.50f, migratedTs.clampedFeather(), 0.001f)
    }

    @Test
    fun timesliceCapCutMode_delayGlitchAndStretchLeftPersistCleanly() {
        val clip1 = VideoClipSegment(
            id = "c1",
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 3_000L,
            sourceDurationMs = 6_000L,
            timeslice = com.example.data.model.TimesliceSettings(
                enabled = true,
                mode = com.example.data.model.TimesliceMode.CAPCUT,
                capcutAmount = 100f,
                capcutSpeed = 100f,
                capcutRotateDeg = 15f,
                capcutStretchDurationSec = 0.4f,
                capcutApplyToAllVideos = true,
                capcutEffectLayerId = "fx-delay",
                capcutTransitionAfterClipId = "c1",
            ),
        )
        val delayFx = com.example.data.model.VideoEffectLayer(
            id = "fx-delay",
            type = com.example.data.model.CapCutEffectType.DELAY_GLITCH,
            intensity = 1f,
            startMs = 2_350L,
            endMs = 3_650L,
            applyToAllVideos = true,
            speed = 100f,
            rotateDeg = 15f,
        )
        val stretchTrans = com.example.data.model.TransitionSpec(
            afterClipId = "c1",
            type = com.example.data.model.TransitionType.STRETCH_LEFT,
            durationSec = 0.4f,
        )
        val state = com.example.data.model.PersistedProjectState(
            projectId = "ts-capcut",
            title = "CapCut Ghost Edit",
            snapshot = com.example.data.model.TimelineSnapshot(
                clips = listOf(clip1),
                effectLayers = listOf(delayFx),
                transitions = listOf(stretchTrans),
            ),
        )
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val encoded = json.encodeToString(com.example.data.model.PersistedProjectState.serializer(), state)
        val decoded = json.decodeFromString(com.example.data.model.PersistedProjectState.serializer(), encoded)

        val dClip = decoded.snapshot.clips.first()
        assertTrue(dClip.timeslice.isCapCutModeActive())
        assertTrue(!dClip.timeslice.isNodeModeActive())
        assertEquals(100f, dClip.timeslice.capcutAmount, 0.01f)
        assertEquals(100f, dClip.timeslice.capcutSpeed, 0.01f)
        assertEquals(15f, dClip.timeslice.capcutRotateDeg, 0.01f)
        assertEquals(0.4f, dClip.timeslice.capcutStretchDurationSec, 0.001f)
        assertTrue(dClip.timeslice.capcutApplyToAllVideos)
        assertEquals(com.example.data.model.CapCutEffectType.DELAY_GLITCH, decoded.snapshot.effectLayers.first().type)
        assertTrue(decoded.snapshot.effectLayers.first().applyToAllVideos)
        assertEquals(com.example.data.model.TransitionType.STRETCH_LEFT, decoded.snapshot.transitions.first().type)
        assertEquals(0.4f, decoded.snapshot.transitions.first().durationSec, 0.001f)
    }

    @Test
    fun multiTrackTimeline_isolatesPerTrackSpansAndSupportsDynamicVideoTracks() {
        val v1a = VideoClipSegment(id = "v1a", trackIndex = 0, timelineStartMs = 0L, trimStartMs = 0L, trimEndMs = 5_000L, sourceDurationMs = 5_000L)
        val v1b = VideoClipSegment(id = "v1b", trackIndex = 0, timelineStartMs = 5_000L, trimStartMs = 0L, trimEndMs = 4_000L, sourceDurationMs = 4_000L)
        val v2Pip = VideoClipSegment(id = "v2", trackIndex = 1, timelineStartMs = 1_500L, trimStartMs = 0L, trimEndMs = 6_000L, sourceDurationMs = 6_000L)
        val v4Extra = VideoClipSegment(id = "v4", trackIndex = 3, timelineStartMs = 2_000L, trimStartMs = 0L, trimEndMs = 9_000L, sourceDurationMs = 9_000L)
        val allClips = listOf(v4Extra, v2Pip, v1b, v1a)

        val v1Spans = com.example.data.model.computeTrackSpans(allClips, 0)
        val v2Spans = com.example.data.model.computeTrackSpans(allClips, 1)
        val v3Spans = com.example.data.model.computeTrackSpans(allClips, 2)
        val v4Spans = com.example.data.model.computeTrackSpans(allClips, 3)

        assertEquals(listOf("v1a", "v1b"), v1Spans.map { it.clip.id })
        assertEquals(listOf("v2"), v2Spans.map { it.clip.id })
        assertEquals(1_500L, v2Spans[0].startMs)
        assertEquals(7_500L, v2Spans[0].endMs)
        assertTrue(v3Spans.isEmpty())
        assertEquals(listOf("v4"), v4Spans.map { it.clip.id })

        // Dynamic video track count automatically expands to cover V4 (4 video tracks + TEXT + A1 + A2)
        val snap = com.example.data.model.TimelineSnapshot(clips = allClips)
        assertEquals(4, snap.effectiveVideoTrackCount())
        val normalized = snap.normalizedTrackConfigs()
        assertTrue(normalized.any { it.kind == com.example.data.model.TimelineTrackKind.VIDEO && it.label == "V4" })
        assertTrue(normalized.any { it.kind == com.example.data.model.TimelineTrackKind.TEXT && it.label == "TEXT" })
        assertTrue(normalized.any { it.kind == com.example.data.model.TimelineTrackKind.AUDIO && it.label == "A1" })
        assertTrue(normalized.any { it.kind == com.example.data.model.TimelineTrackKind.AUDIO && it.label == "A2" })

        // Total duration covers V4 reaching 11,000ms without altering V1's span timings
        assertEquals(11_000L, com.example.data.model.computeTotalVideoDurationMs(allClips))
    }

    @Test
    fun multiTrackConfigs_preserveHideLockMuteHeightAndAudioSlotsThroughSerialization() {
        val customTracks = com.example.data.model.defaultTimelineTracks(3).map { cfg ->
            when (cfg.id) {
                "track_video_1" -> cfg.copy(isHidden = true, isLocked = true, isMuted = true, heightDp = 82f)
                "track_text_0" -> cfg.copy(isLocked = true)
                "track_audio_1" -> cfg.copy(isMuted = true)
                else -> cfg
            }
        }
        val snapshot = com.example.data.model.TimelineSnapshot(
            clips = listOf(
                VideoClipSegment(id = "c1", trackIndex = 0, timelineStartMs = 0L, trimEndMs = 4_000L),
                VideoClipSegment(id = "c2", trackIndex = 1, timelineStartMs = 1_000L, trimEndMs = 3_000L),
            ),
            audioTracks = listOf(
                com.example.data.model.AudioTrackItem(id = "a1", title = "BG Music", trackIndex = 0, startMs = 0L, endMs = 4_000L),
                com.example.data.model.AudioTrackItem(id = "a2", title = "Voiceover", trackIndex = 1, startMs = 500L, endMs = 3_500L),
            ),
            trackConfigs = customTracks,
        )
        val state = com.example.data.model.PersistedProjectState(
            projectId = "mt-proj",
            title = "Multi-Track Edit",
            snapshot = snapshot,
        )
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(com.example.data.model.PersistedProjectState.serializer(), state)
        val decoded = json.decodeFromString(com.example.data.model.PersistedProjectState.serializer(), encoded)

        val dSnap = decoded.snapshot
        assertTrue(!dSnap.isVideoTrackHidden(0))
        assertTrue(dSnap.isVideoTrackHidden(1))
        assertTrue(dSnap.isVideoTrackLocked(1))
        assertTrue(dSnap.isVideoTrackMuted(1))
        assertEquals(82f, dSnap.trackConfigFor(com.example.data.model.TimelineTrackKind.VIDEO, 1)?.heightDp ?: 0f, 0.01f)
        assertTrue(dSnap.isTextTrackLocked())
        assertTrue(!dSnap.isAudioTrackMuted(0))
        assertTrue(dSnap.isAudioTrackMuted(1))
        assertEquals(0, dSnap.audioTracks[0].trackIndex)
        assertEquals(1, dSnap.audioTracks[1].trackIndex)
    }

    @Test
    fun reversePcmAlignment_trimsAndPadsSilenceToMatchExactVideoDuration() {
        // 48kHz stereo 16-bit PCM = 48_000 * 4 bytes per second = 192_000 bytes/sec; 500ms = 96_000 bytes
        val shortPcm = ByteArray(48_000) { 0x2A } // 250ms
        val padded = com.example.domain.video.VideoReverseProcessor.alignPcmToExactDuration(
            pcmBytes = shortPcm,
            sampleRate = 48_000,
            channelCount = 2,
            targetDurationMs = 500L,
        )
        assertEquals(96_000, padded.size)
        assertEquals(0x2A.toByte(), padded[0])
        assertEquals(0x2A.toByte(), padded[47_999])
        assertEquals(0.toByte(), padded[48_000])
        assertEquals(0.toByte(), padded[95_999])

        val longPcm = ByteArray(144_000) { 0x15 } // 750ms
        val trimmed = com.example.domain.video.VideoReverseProcessor.alignPcmToExactDuration(
            pcmBytes = longPcm,
            sampleRate = 48_000,
            channelCount = 2,
            targetDurationMs = 500L,
        )
        assertEquals(96_000, trimmed.size)
        assertEquals(0x15.toByte(), trimmed[95_999])
    }

    @Test
    fun exportAudioWindowClamping_enforcesVideoDurationBounds() {
        val clamped = com.example.domain.video.VideoExportManager.clampAudioWindowToVideoDuration(
            startMs = 400L,
            endMs = 2_500L,
            totalVideoDurationMs = 1_000L,
        )
        assertEquals(400L to 1_000L, clamped)

        val dropped = com.example.domain.video.VideoExportManager.clampAudioWindowToVideoDuration(
            startMs = 1_000L,
            endMs = 2_000L,
            totalVideoDurationMs = 1_000L,
        )
        assertEquals(null, dropped)

        val tooShort = com.example.domain.video.VideoExportManager.clampAudioWindowToVideoDuration(
            startMs = 990L,
            endMs = 1_500L,
            totalVideoDurationMs = 1_000L,
        )
        assertEquals(null, tooShort)
    }

    @Test
    fun playheadStrictSplitAndKeyframeGuards_enforcePlayheadOnTarget() {
        // Clip spans 2000ms..6000ms
        assertTrue(!com.example.ui.studio.StudioViewModel.isPlayheadInsideRange(1_999L, 2_000L, 6_000L))
        assertTrue(com.example.ui.studio.StudioViewModel.isPlayheadInsideRange(2_000L, 2_000L, 6_000L))
        assertTrue(com.example.ui.studio.StudioViewModel.isPlayheadInsideRange(6_000L, 2_000L, 6_000L))
        assertTrue(!com.example.ui.studio.StudioViewModel.isPlayheadInsideRange(6_001L, 2_000L, 6_000L))

        // Split requires at least 200ms on both left and right halves
        assertTrue(!com.example.ui.studio.StudioViewModel.canSplitAtPlayhead(1_500L, 2_000L, 6_000L))
        assertTrue(!com.example.ui.studio.StudioViewModel.canSplitAtPlayhead(2_100L, 2_000L, 6_000L))
        assertTrue(com.example.ui.studio.StudioViewModel.canSplitAtPlayhead(2_200L, 2_000L, 6_000L))
        assertTrue(com.example.ui.studio.StudioViewModel.canSplitAtPlayhead(5_800L, 2_000L, 6_000L))
        assertTrue(!com.example.ui.studio.StudioViewModel.canSplitAtPlayhead(5_900L, 2_000L, 6_000L))
        assertTrue(!com.example.ui.studio.StudioViewModel.canSplitAtPlayhead(7_000L, 2_000L, 6_000L))

        assertEquals("00:00", com.example.ui.studio.StudioViewModel.formatSplitTimeMs(0L))
        assertEquals("00:05", com.example.ui.studio.StudioViewModel.formatSplitTimeMs(5_400L))
        assertEquals("01:05", com.example.ui.studio.StudioViewModel.formatSplitTimeMs(65_000L))
    }
}

