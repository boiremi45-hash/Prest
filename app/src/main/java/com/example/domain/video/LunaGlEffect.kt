package com.example.domain.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.data.model.AdjustSettings
import com.example.data.model.AnimationSettings
import com.example.data.model.ClipAnimationType
import com.example.data.model.KeyframeEvaluator
import com.example.data.model.KeyframeNode
import com.example.data.model.MaskShape
import com.example.data.model.MotionBlurSettings
import com.example.data.model.TransformSettings
import com.example.data.model.TransitionSpec
import com.example.data.model.TransitionType
import com.example.data.model.VideoEffectLayer
import kotlin.math.abs

data class TimedTransitionWindow(
    val boundaryMs: Long,
    val type: TransitionType,
    val durationMs: Long,
)

data class LunaShaderParams(
    val bypass: Boolean = false,
    val adjust: AdjustSettings = AdjustSettings(),
    val presetExposure: Float = 0f,
    val presetContrast: Float = 0f,
    val presetSaturation: Float = 0f,
    val presetHueDeg: Float = 0f,
    val presetTemperature: Float = 0f,
    val presetTint: Float = 0f,
    val presetVignette: Float = 0f,
    val presetGlow: Float = 0f,
    val presetGrain: Float = 0f,
    val effectLayers: List<VideoEffectLayer> = emptyList(),
    val keyframes: List<KeyframeNode> = emptyList(),
    val baseTransform: TransformSettings = TransformSettings(),
    val baseOpacity: Float = 1f,
    val maskShape: MaskShape = MaskShape.NONE,
    val maskFeather: Float = 0.08f,
    val maskInvert: Boolean = false,
    val maskScale: Float = 0.72f,
    val motionBlur: MotionBlurSettings = MotionBlurSettings(),
    val clipDurationMs: Long = 8_000L,
    val animation: AnimationSettings = AnimationSettings(),
    val transitions: List<TimedTransitionWindow> = emptyList(),
    val opacityScale: Float = 1f,
    val timelineOffsetMs: Long = 0L,
    val cutFlashPunchOnStart: Boolean = false,
)

@UnstableApi
class LunaGlEffect(
    private val paramsProvider: () -> LunaShaderParams,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return LunaShaderProgram(useHdr = useHdr, paramsProvider = paramsProvider)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}

@UnstableApi
private class LunaShaderProgram(
    useHdr: Boolean,
    private val paramsProvider: () -> LunaShaderParams,
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var width = 1
    private var height = 1

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth.coerceAtLeast(1)
        height = inputHeight.coerceAtLeast(1)
        if (glProgram == null) {
            try {
                glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER).apply {
                    setBufferAttribute(
                        "aFramePosition",
                        GlUtil.getNormalizedCoordinateBounds(),
                        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
                    )
                }
            } catch (e: Exception) {
                throw VideoFrameProcessingException(e)
            }
        }
        return Size(width, height)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return
        val params = paramsProvider()
        val timeMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
        val tSec = (presentationTimeUs / 1_000_000.0f)

        val evaluated = KeyframeEvaluator.evaluate(
            keyframes = params.keyframes,
            localTimeMs = timeMs,
            baseTransform = params.baseTransform,
            baseOpacity = params.baseOpacity,
            baseAdjust = params.adjust,
            baseFilterIntensity = 1f,
        )

        val timelineMs = (timeMs + params.timelineOffsetMs).coerceAtLeast(0L)
        val activeFx = if (params.bypass) {
            null
        } else {
            params.effectLayers.lastOrNull { fx ->
                val hasNextContiguous = params.effectLayers.any { other ->
                    other.id != fx.id && abs(other.startMs - fx.endMs) <= 20L
                }
                com.example.data.model.isTimelineItemActiveAt(
                    startMs = fx.startMs,
                    endMs = fx.endMs,
                    playheadMs = timelineMs,
                    hasNextContiguous = hasNextContiguous,
                )
            }
        }

        // Animation state evaluated at presentation timestamp
        var animCode = 0
        var animProg = 0f
        if (!params.bypass) {
            val anim = params.animation
            val inDurMs = (anim.inDurationSec * 1000f).toLong().coerceAtLeast(100L)
            val outDurMs = (anim.outDurationSec * 1000f).toLong().coerceAtLeast(100L)
            val comboDurMs = (anim.comboDurationSec * 1000f).toLong().coerceAtLeast(100L)
            when {
                anim.inType != ClipAnimationType.NONE && timeMs <= inDurMs -> {
                    animCode = anim.inType.code
                    animProg = (timeMs.toFloat() / inDurMs.toFloat()).coerceIn(0f, 1f)
                }
                anim.outType != ClipAnimationType.NONE &&
                    params.clipDurationMs > outDurMs &&
                    timeMs >= (params.clipDurationMs - outDurMs) -> {
                    animCode = anim.outType.code
                    animProg = 1f - ((timeMs - (params.clipDurationMs - outDurMs)).toFloat() / outDurMs.toFloat()).coerceIn(0f, 1f)
                }
                anim.comboType != ClipAnimationType.NONE && timeMs <= comboDurMs -> {
                    animCode = anim.comboType.code
                    animProg = (timeMs.toFloat() / comboDurMs.toFloat()).coerceIn(0f, 1f)
                }
            }
        }

        // Transition state across clip cut boundaries
        var transCode = 0
        var transProg = 0f
        if (!params.bypass) {
            for (tr in params.transitions) {
                val half = (tr.durationMs / 2L).coerceAtLeast(50L)
                if (tr.type != TransitionType.NONE &&
                    timelineMs >= tr.boundaryMs - half &&
                    timelineMs <= tr.boundaryMs + half
                ) {
                    transCode = tr.type.code
                    // Triangle bell 0 -> 1 (at cut) -> 0
                    val dist = abs(timelineMs - tr.boundaryMs).toFloat() / half.toFloat()
                    transProg = (1f - dist).coerceIn(0f, 1f)
                    break
                }
            }
        }
        var cutFlashAmt = 0f
        if (!params.bypass && params.cutFlashPunchOnStart && timeMs in 0L..55L) {
            cutFlashAmt = (1f - (timeMs / 55f)).coerceIn(0f, 1f)
        }

        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatUniform("uTimeSec", tSec)
            program.setFloatsUniform("uTexelSize", floatArrayOf(1f / width, 1f / height))

            if (params.bypass) {
                program.setFloatUniform("uBypass", 1f)
            } else {
                program.setFloatUniform("uBypass", 0f)
            }

            val bright = ((evaluated.brightness / 50f) * 0.45f + params.presetExposure * 0.4f).coerceIn(-0.9f, 0.9f)
            val contr = (1f + (evaluated.contrast / 50f) * 0.65f + params.presetContrast * 0.6f).coerceIn(0.25f, 2.2f)
            val sat = (1f + (evaluated.saturation / 50f) * 0.9f + (params.presetSaturation / 100f)).coerceIn(0f, 2.5f)
            val hueRad = ((params.adjust.hue / 50f) * 0.9f + params.presetHueDeg * 0.01745f)
            val temp = (params.adjust.temp / 50f * 0.35f + params.presetTemperature * 0.25f).coerceIn(-0.6f, 0.6f)
            val sharpenAmt = ((params.adjust.sharpen / 100f) * 1.4f + (params.adjust.clarity / 50f) * 0.65f).coerceIn(-0.5f, 2.0f)
            val vignetteAmt = ((params.adjust.vignette / 50f) + params.presetVignette).coerceIn(-0.8f, 1.2f)
            val grainAmt = ((params.adjust.grain / 100f) * 0.22f + params.presetGrain * 0.15f).coerceIn(0f, 0.35f)

            program.setFloatUniform("uBrightness", bright)
            program.setFloatUniform("uContrast", contr)
            program.setFloatUniform("uSaturation", sat)
            program.setFloatUniform("uHueRad", hueRad)
            program.setFloatUniform("uTemp", temp)
            program.setFloatUniform("uTint", params.presetTint)
            program.setFloatUniform("uSharpen", sharpenAmt)
            program.setFloatUniform("uVignette", vignetteAmt)
            program.setFloatUniform("uGrain", grainAmt)
            program.setFloatUniform("uGlow", params.presetGlow)
            program.setFloatUniform("uOpacity", (evaluated.opacity * params.opacityScale).coerceIn(0.05f, 1f))

            program.setIntUniform("uEffectCode", activeFx?.type?.code ?: 0)
            program.setFloatUniform("uEffectIntensity", activeFx?.intensity ?: 0f)

            program.setIntUniform("uMaskCode", if (params.bypass) 0 else params.maskShape.code)
            program.setFloatUniform("uMaskScale", params.maskScale.coerceIn(0.15f, 1.4f))
            program.setFloatUniform("uMaskFeather", params.maskFeather.coerceIn(0.01f, 0.45f))
            program.setFloatUniform("uMaskInvert", if (params.maskInvert) 1f else 0f)

            val mb = if (!params.bypass && params.motionBlur.enabled) params.motionBlur.blurAmount else 0f
            program.setFloatUniform("uMotionBlur", mb)
            program.setIntUniform("uAnimCode", animCode)
            program.setFloatUniform("uAnimProg", animProg)
            program.setIntUniform("uTransCode", transCode)
            program.setFloatUniform("uTransProg", transProg)
            program.setFloatUniform("uCutFlash", cutFlashAmt)

            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram?.delete()
        } catch (_: Exception) {
        }
        glProgram = null
    }

    private companion object {
        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
    gl_Position = aFramePosition;
    vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        const val FRAGMENT_SHADER = """
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uTimeSec;
uniform vec2 uTexelSize;
uniform float uBypass;

uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uHueRad;
uniform float uTemp;
uniform float uTint;
uniform float uSharpen;
uniform float uVignette;
uniform float uGrain;
uniform float uGlow;
uniform float uOpacity;

uniform int uEffectCode;
uniform float uEffectIntensity;

uniform int uMaskCode;
uniform float uMaskScale;
uniform float uMaskFeather;
uniform float uMaskInvert;

uniform float uMotionBlur;
uniform int uAnimCode;
uniform float uAnimProg;
uniform int uTransCode;
uniform float uTransProg;
uniform float uCutFlash;

varying vec2 vTexSamplingCoord;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 shiftHue(vec3 col, float angle) {
    const vec3 k = vec3(0.57735, 0.57735, 0.57735);
    float cosAngle = cos(angle);
    return col * cosAngle + cross(k, col) * sin(angle) + k * dot(k, col) * (1.0 - cosAngle);
}

void main() {
    vec2 uv = vTexSamplingCoord;
    if (uBypass > 0.5) {
        gl_FragColor = texture2D(uTexSampler, uv);
        return;
    }

    vec2 sampleUv = uv;

    // 1. Animation spatial distortion
    float animAlpha = 1.0;
    if (uAnimCode == 2) { // Stretched In
        float p = clamp(uAnimProg, 0.05, 1.0);
        sampleUv.x = (sampleUv.x - 0.5) / p + 0.5;
    } else if (uAnimCode == 4) { // Fade Aside
        float p = 1.0 - uAnimProg;
        sampleUv.x -= p * 0.45;
        animAlpha = uAnimProg;
    } else if (uAnimCode == 7) { // Glitch
        float jitter = (1.0 - uAnimProg) * 0.06 * sin(uv.y * 48.0 + uTimeSec * 40.0);
        sampleUv.x += jitter;
    }

    // 2. Transition spatial distortion
    if (uTransCode == 2) { // Glitch Split
        float splitOff = uTransProg * 0.09 * (step(0.5, fract(uv.y * 5.0)) * 2.0 - 1.0);
        sampleUv.x += splitOff;
    } else if (uTransCode == 5) { // Light Twist
        vec2 c = sampleUv - 0.5;
        float r = length(c);
        float a = uTransProg * 1.4 * (1.0 - r);
        float ca = cos(a);
        float sa = sin(a);
        sampleUv = vec2(c.x * ca - c.y * sa, c.x * sa + c.y * ca) + 0.5;
    } else if (uTransCode == 6) { // Scanline Glitch
        sampleUv.x += uTransProg * 0.05 * sin(uv.y * 120.0 + uTimeSec * 30.0);
    } else if (uTransCode == 7) { // Pulse Fade
        sampleUv = (sampleUv - 0.5) * (1.0 - uTransProg * 0.22) + 0.5;
    }

    // 3. VFX Coordinate transforms (Matrix Panels=1, Photo Strip=6, Tracking Glitch=12, Retro Rays=9)
    float fxI = clamp(uEffectIntensity, 0.0, 1.0);
    if (uEffectCode == 1) { // Matrix Panels (3x3 tile + subtle parallax)
        float tileMix = step(0.15, fxI);
        vec2 tiled = fract(sampleUv * (1.0 + floor(fxI * 2.0 + 1.0)));
        sampleUv = mix(sampleUv, tiled, tileMix);
    } else if (uEffectCode == 6) { // Photo Strip (3 vertical panels)
        sampleUv = vec2(sampleUv.x, fract(sampleUv.y * 3.0));
    } else if (uEffectCode == 12) { // Tracking Glitch
        float band = step(0.82 - fxI * 0.35, sin(uv.y * 22.0 + uTimeSec * 12.0));
        sampleUv.x += band * 0.06 * fxI * sin(uTimeSec * 55.0);
    }

    vec4 baseSample = texture2D(uTexSampler, clamp(sampleUv, 0.001, 0.999));
    vec3 color = baseSample.rgb;

    // 4. Blur Effect (code=3) or Dreamland (code=5) or Motion Blur
    if (uEffectCode == 3 && fxI > 0.02) {
        vec2 d = uTexelSize * (1.5 + fxI * 7.5);
        vec3 acc = color;
        acc += texture2D(uTexSampler, clamp(sampleUv + vec2(d.x, 0.0), 0.001, 0.999)).rgb;
        acc += texture2D(uTexSampler, clamp(sampleUv - vec2(d.x, 0.0), 0.001, 0.999)).rgb;
        acc += texture2D(uTexSampler, clamp(sampleUv + vec2(0.0, d.y), 0.001, 0.999)).rgb;
        acc += texture2D(uTexSampler, clamp(sampleUv - vec2(0.0, d.y), 0.001, 0.999)).rgb;
        acc += texture2D(uTexSampler, clamp(sampleUv + vec2(d.x, d.y), 0.001, 0.999)).rgb;
        acc += texture2D(uTexSampler, clamp(sampleUv + vec2(-d.x, d.y), 0.001, 0.999)).rgb;
        color = acc / 7.0;
    } else if (uMotionBlur > 0.02) {
        vec2 mbStep = vec2(uMotionBlur * 0.015, uMotionBlur * 0.005);
        vec3 mbAcc = color;
        mbAcc += texture2D(uTexSampler, clamp(sampleUv + mbStep, 0.001, 0.999)).rgb;
        mbAcc += texture2D(uTexSampler, clamp(sampleUv - mbStep, 0.001, 0.999)).rgb;
        mbAcc += texture2D(uTexSampler, clamp(sampleUv + mbStep * 2.0, 0.001, 0.999)).rgb;
        mbAcc += texture2D(uTexSampler, clamp(sampleUv - mbStep * 2.0, 0.001, 0.999)).rgb;
        color = mbAcc / 5.0;
    }

    // 5. Sharpen & Clarity
    if (abs(uSharpen) > 0.02) {
        vec3 n = texture2D(uTexSampler, clamp(sampleUv + vec2(0.0, uTexelSize.y), 0.001, 0.999)).rgb;
        vec3 s = texture2D(uTexSampler, clamp(sampleUv - vec2(0.0, uTexelSize.y), 0.001, 0.999)).rgb;
        vec3 e = texture2D(uTexSampler, clamp(sampleUv + vec2(uTexelSize.x, 0.0), 0.001, 0.999)).rgb;
        vec3 w = texture2D(uTexSampler, clamp(sampleUv - vec2(uTexelSize.x, 0.0), 0.001, 0.999)).rgb;
        vec3 blur4 = 0.25 * (n + s + e + w);
        color = clamp(color + (color - blur4) * uSharpen, 0.0, 1.0);
    }

    // 6. CapCut VFX Pixel Effects
    if (uEffectCode == 1) { // Matrix Panels tint + grid border
        vec2 cell = fract(uv * 3.0);
        float border = step(0.95, max(cell.x, cell.y)) + step(min(cell.x, cell.y), 0.04);
        color = mix(color, vec3(color.r * 0.65, color.g * 1.18, color.b * 0.85), fxI * 0.65);
        color += vec3(0.05, 0.85, 0.55) * border * fxI * 0.55;
    } else if (uEffectCode == 2) { // Analog Snow
        float snow = hash12(uv * 380.0 + vec2(floor(uTimeSec * 24.0) * 19.0, 7.0));
        float flake = step(0.89 - fxI * 0.14, snow);
        float scan = sin(uv.y * 220.0 + uTimeSec * 14.0) * 0.05 * fxI;
        color = clamp(color + vec3(flake * fxI * 0.85) + scan, 0.0, 1.0);
    } else if (uEffectCode == 4) { // Flash Copies (strobe ghost exposure)
        float beat = 0.5 + 0.5 * sin(uTimeSec * 16.0);
        vec3 ghost = texture2D(uTexSampler, clamp(sampleUv + vec2(0.02 * fxI, 0.0), 0.001, 0.999)).rgb;
        color = mix(color, max(color, ghost) * (1.0 + beat * 0.65), fxI * beat);
    } else if (uEffectCode == 5) { // Dreamland
        vec2 d = uTexelSize * 5.0;
        vec3 bloom = 0.5 * (
            texture2D(uTexSampler, clamp(sampleUv + d, 0.001, 0.999)).rgb +
            texture2D(uTexSampler, clamp(sampleUv - d, 0.001, 0.999)).rgb
        );
        color = mix(color, clamp(color * 0.85 + bloom * 0.45 + vec3(0.14, 0.05, 0.18), 0.0, 1.0), fxI * 0.85);
    } else if (uEffectCode == 6) { // Photo Strip dark border bars
        float edge = step(0.91, fract(uv.y * 3.0)) + step(fract(uv.y * 3.0), 0.07);
        float side = step(0.92, uv.x) + step(uv.x, 0.08);
        color = mix(color, vec3(0.06, 0.06, 0.08), clamp(edge + side, 0.0, 1.0) * fxI);
    } else if (uEffectCode == 7) { // Laser Grid
        vec2 g = fract(vec2(uv.x * 8.0 + uTimeSec * 0.6, uv.y * 8.0 - uTimeSec * 0.8));
        float line = smoothstep(0.92, 0.98, max(g.x, g.y));
        color += vec3(0.10, 0.92, 0.95) * line * fxI * 0.9;
    } else if (uEffectCode == 8) { // Neon Ray
        float sweep = abs(fract(uv.x + uv.y * 0.5 - uTimeSec * 0.55) - 0.5);
        float ray = smoothstep(0.14, 0.0, sweep);
        color += vec3(0.65, 0.25, 1.0) * ray * fxI * 0.9;
    } else if (uEffectCode == 9) { // Retro Rays
        float angle = atan(uv.y - 0.5, uv.x - 0.5);
        float rays = pow(0.5 + 0.5 * sin(angle * 10.0 + uTimeSec * 2.5), 3.0);
        vec3 shiftR = texture2D(uTexSampler, clamp(sampleUv + vec2(0.008 * fxI, 0.0), 0.001, 0.999)).rgb;
        color.r = shiftR.r;
        color += vec3(1.0, 0.52, 0.22) * rays * fxI * 0.45;
    } else if (uEffectCode == 10) { // LED Matrix
        vec2 cells = floor(uv * vec2(44.0, 26.0)) / vec2(44.0, 26.0);
        vec3 ledColor = texture2D(uTexSampler, clamp(cells, 0.001, 0.999)).rgb;
        vec2 local = fract(uv * vec2(44.0, 26.0)) - 0.5;
        float dotMask = smoothstep(0.46, 0.24, length(local));
        color = mix(color, ledColor * dotMask * 1.2, fxI);
    } else if (uEffectCode == 11) { // Golden Ray
        float dist = length(uv - vec2(0.5, 0.15));
        float sun = smoothstep(0.85, 0.0, dist);
        float ang = atan(uv.y - 0.15, uv.x - 0.5);
        float beams = 0.5 + 0.5 * sin(ang * 14.0 - uTimeSec * 1.6);
        color += vec3(1.0, 0.80, 0.32) * (sun * 0.45 + beams * sun * 0.45) * fxI;
    } else if (uEffectCode == 12) { // Tracking Glitch RGB split
        float rC = texture2D(uTexSampler, clamp(sampleUv + vec2(0.015 * fxI, 0.0), 0.001, 0.999)).r;
        float bC = texture2D(uTexSampler, clamp(sampleUv - vec2(0.015 * fxI, 0.0), 0.001, 0.999)).b;
        color = vec3(rC, color.g, bC);
    }

    // 7. Standard Color Adjustments
    color = color + vec3(uBrightness);
    color = (color - 0.5) * uContrast + 0.5;
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(luma), color, uSaturation);
    if (abs(uHueRad) > 0.01) {
        color = shiftHue(color, uHueRad);
    }
    color.r += uTemp * 0.28 + uTint * 0.10;
    color.g -= uTint * 0.18;
    color.b -= uTemp * 0.28 - uTint * 0.10;
    if (uGlow > 0.01) {
        color += color * uGlow * 0.22;
    }

    // 8. Vignette & Grain
    if (abs(uVignette) > 0.01) {
        float dist = distance(uv, vec2(0.5));
        float vFactor = smoothstep(0.28, 0.86, dist);
        color *= clamp(1.0 - vFactor * uVignette * 0.78, 0.05, 1.8);
    }
    if (uGrain > 0.005) {
        float noise = hash12(uv * 420.0 + uTimeSec * 13.0) - 0.5;
        color += vec3(noise * uGrain);
    }

    // 9. Animations overlay (Trickling=1, LaserScan=3, Ghost=5, FluidBurn=6)
    if (uAnimCode == 1) { // Trickling
        float wave = smoothstep(uv.y - 0.08, uv.y + 0.08, uAnimProg);
        animAlpha = wave;
    } else if (uAnimCode == 3) { // Laser Scan
        float linePos = uAnimProg;
        float laser = smoothstep(0.035, 0.0, abs(uv.x - linePos));
        color = mix(vec3(0.0), color, step(uv.x, linePos));
        color += vec3(0.12, 0.95, 0.85) * laser;
    } else if (uAnimCode == 5) { // Ghost
        float off = (1.0 - uAnimProg) * 0.07;
        vec3 gCol = texture2D(uTexSampler, clamp(uv - vec2(off, 0.0), 0.001, 0.999)).rgb;
        color = mix(gCol, color, 0.6);
        animAlpha = clamp(uAnimProg * 1.15, 0.0, 1.0);
    } else if (uAnimCode == 6) { // Fluid Burn
        float burn = (1.0 - uAnimProg);
        color += vec3(1.0, 0.42, 0.12) * burn * 0.85;
        animAlpha = smoothstep(0.0, 0.35, uAnimProg);
    }

    // 10. Transition overlays (DreamyFlash=1, PumpkinDrop=3, SparklePops=4, PulseFade=7)
    if (uTransCode == 1) { // Dreamy Flash
        color = mix(color, vec3(1.0, 0.96, 1.0), uTransProg * 0.92);
    } else if (uTransCode == 3) { // Pumpkin Drop
        color = mix(color, vec3(1.0, 0.52, 0.12), uTransProg * 0.55);
    } else if (uTransCode == 4) { // Sparkle Pops
        float sp = step(0.95, hash12(floor(uv * 28.0) + floor(uTimeSec * 12.0)));
        color += vec3(1.0, 0.95, 0.80) * sp * uTransProg * 1.4;
    } else if (uTransCode == 7) { // Pulse Fade
        color *= (1.0 - uTransProg * 0.85);
    }

    // 10b. Crisp 0.05s white flash for back-to-back short reversed clips
    if (uCutFlash > 0.005) {
        color = mix(color, vec3(1.0, 1.0, 1.0), uCutFlash * 0.88);
    }

    // 11. Shape Masking (Split=1, Filmstrip=2, Circle=3, Rectangle=4, Heart=5, Star=6)
    float maskAlpha = 1.0;
    if (uMaskCode > 0) {
        vec2 p = (uv - 0.5) * vec2(1.777, 1.0); // aspect-corrected
        float sc = clamp(uMaskScale, 0.15, 1.4) * 0.5;
        float sdf = 0.0;
        if (uMaskCode == 1) { // Split
            sdf = abs(p.y) - sc * 0.8;
        } else if (uMaskCode == 2) { // Filmstrip
            sdf = abs(p.y) - sc * 0.55;
        } else if (uMaskCode == 3) { // Circle
            sdf = length(p) - sc;
        } else if (uMaskCode == 4) { // Rectangle
            vec2 dRect = abs(p) - vec2(sc * 1.35, sc * 0.82);
            sdf = length(max(dRect, 0.0)) + min(max(dRect.x, dRect.y), 0.0);
        } else if (uMaskCode == 5) { // Heart
            vec2 hp = p / max(sc * 1.15, 0.1);
            hp.y = -hp.y + 0.15;
            float a = hp.x * hp.x + (hp.y - sqrt(abs(hp.x)) * 0.65) * (hp.y - sqrt(abs(hp.x)) * 0.65);
            sdf = (a - 0.65) * 0.4;
        } else if (uMaskCode == 6) { // Star
            float ang = atan(p.y, p.x);
            float rad = length(p);
            float starR = sc * (0.68 + 0.32 * cos(ang * 5.0));
            sdf = rad - starR;
        }
        float inside = 1.0 - smoothstep(-uMaskFeather, uMaskFeather, sdf);
        maskAlpha = (uMaskInvert > 0.5) ? (1.0 - inside) : inside;
    }

    float finalAlpha = clamp(uOpacity * animAlpha * maskAlpha, 0.0, 1.0);
    gl_FragColor = vec4(clamp(color, 0.0, 1.0) * finalAlpha, finalAlpha);
}
"""
    }
}

/**
 * Applies Auto-Remove Background (luma/border backdrop matte) and/or Chroma Key color removal
 * cleanly on static or frame-sampled overlay Bitmaps for GPU OverlayEffect composition.
 */
object OverlayMatteProcessor {
    fun processBitmapMatte(
        src: Bitmap,
        autoRemoveBg: Boolean,
        chromaEnabled: Boolean,
        chromaColorArgb: Long,
        chromaIntensity: Float,
        chromaShadow: Float,
        opacity: Float,
    ): Bitmap {
        val w = src.width.coerceAtMost(720).coerceAtLeast(1)
        val h = src.height.coerceAtMost(720).coerceAtLeast(1)
        val scaled = if (src.width != w || src.height != h) {
            Bitmap.createScaledBitmap(src, w, h, true)
        } else {
            src.copy(Bitmap.Config.ARGB_8888, true)
        }
        if (!autoRemoveBg && !chromaEnabled && opacity >= 0.99f) {
            return scaled
        }

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val keyColor = if (autoRemoveBg && !chromaEnabled) {
            // Sample average corner backdrop color for automatic subject cutout
            pixels[0]
        } else {
            chromaColorArgb.toInt()
        }
        val kr = Color.red(keyColor) / 255f
        val kg = Color.green(keyColor) / 255f
        val kb = Color.blue(keyColor) / 255f
        val threshold = (if (autoRemoveBg) 0.28f else chromaIntensity.coerceIn(0.05f, 0.85f))
        val softness = (0.08f + chromaShadow * 0.25f).coerceIn(0.04f, 0.4f)

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c) / 255f
            val r = Color.red(c) / 255f
            val g = Color.green(c) / 255f
            val b = Color.blue(c) / 255f

            var matte = 1f
            if (autoRemoveBg || chromaEnabled) {
                val dr = r - kr
                val dg = g - kg
                val db = b - kb
                val dist = kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
                matte = ((dist - threshold * 0.7f) / softness).coerceIn(0f, 1f)
                // Edge feather protection toward image center for Auto Remove BG
                if (autoRemoveBg) {
                    val x = (i % w) / w.toFloat() - 0.5f
                    val y = (i / w) / h.toFloat() - 0.5f
                    val radial = kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
                    if (radial < 0.22f) {
                        matte = maxOf(matte, 0.88f)
                    }
                }
            }
            val finalA = ((a * matte * opacity) * 255f).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(finalA, (r * 255f).toInt(), (g * 255f).toInt(), (b * 255f).toInt())
        }
        scaled.setPixels(pixels, 0, w, 0, 0, w, h)
        return scaled
    }
}
