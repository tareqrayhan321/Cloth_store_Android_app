package com.imran.clothstore.ui.intro

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.imran.clothstore.R
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sqrt

/**
 * EMEAN Cloth Store ইন্ট্রো মোশন — ওয়েব (HTML/SVG) সংস্করণের হুবহু Compose পোর্ট।
 * ক্রিম পটে (#FBF7F2) মাঝখানে পূর্ণ লোগো; মোট দৈর্ঘ্য ≈ ৩.৪ সেকেন্ড + fade; ট্যাপ করলে স্কিপ।
 * টাইমলাইন: ইমব্লেম কলমে আঁকার মতো রেখা ধরে ফোটে (০.১০–১.৭৫) → তুলার ফুল পপ
 * (১.২০ / ১.৪৫ / ১.৬৫) → "EMEAN" পর্দা-সরণ (১.৪৫) → "— CLOTH STORE —" পর্দা-সরণ (১.৯০)
 * → শেষে হালকা breath।
 */

private val IntroBg = Color(0xFFFBF7F2)
private const val TOTAL = 3.4f
private const val FADE = 0.3f

/** CSS cubic-bezier(x1,y1,x2,y2) — x দিয়ে y বের করা (Newton + bisection fallback)। */
private fun cubicBezierY(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    var lo = 0f
    var hi = 1f
    var u = x
    repeat(24) {
        val v = 1f - u
        val bx = 3f * v * v * u * x1 + 3f * v * u * u * x2 + u * u * u
        if (bx < x) lo = u else hi = u
        u = (lo + hi) / 2f
    }
    val v = 1f - u
    return 3f * v * v * u * y1 + 3f * v * u * u * y2 + u * u * u
}

private fun pathFrom(pts: FloatArray): Path {
    val p = Path()
    if (pts.size >= 2) {
        p.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) {
            p.lineTo(pts[i], pts[i + 1])
            i += 2
        }
    }
    return p
}

/** সেগমেন্টের প্রথম [frac] অংশের পথ (আঁকার front)। */
private fun partialPath(pts: FloatArray, frac: Float): Path {
    val p = Path()
    val n = pts.size / 2
    if (n == 0) return p
    p.moveTo(pts[0], pts[1])
    if (frac <= 0f || n == 1) return p
    var total = 0f
    val lens = FloatArray(n)
    for (i in 1 until n) {
        val dx = pts[i * 2] - pts[(i - 1) * 2]
        val dy = pts[i * 2 + 1] - pts[(i - 1) * 2 + 1]
        total += sqrt(dx * dx + dy * dy)
        lens[i] = total
    }
    val target = total * min(frac, 1f)
    for (i in 1 until n) {
        if (lens[i] <= target) {
            p.lineTo(pts[i * 2], pts[i * 2 + 1])
        } else {
            val prev = lens[i - 1]
            val r = if (lens[i] > prev) (target - prev) / (lens[i] - prev) else 0f
            p.lineTo(
                pts[(i - 1) * 2] + (pts[i * 2] - pts[(i - 1) * 2]) * r,
                pts[(i - 1) * 2 + 1] + (pts[i * 2 + 1] - pts[(i - 1) * 2 + 1]) * r,
            )
            break
        }
    }
    return p
}

private fun DrawScope.drawLogoFull(logo: ImageBitmap, alpha: Float = 1f, blend: BlendMode = BlendMode.SrcOver) {
    drawImage(
        image = logo,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(logo.width, logo.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(2048, 682),
        alpha = alpha,
        blendMode = blend,
    )
}

/** ইমব্লেম: স্ট্রোক-মাস্কের ভেতর দিয়ে লোগো — ফুলের এলাকা বাদ (ওগুলো পপে আসবে)। */
private fun DrawScope.drawEmblemDrawn(logo: ImageBitmap, t: Float) {
    clipRect(0f, 0f, 625f, 682f) {
        drawContext.canvas.saveLayer(Rect(0f, 0f, 2048f, 682f), Paint())
        for (seg in EMEAN_SEGS) {
            val p = (t - seg.t) / seg.d
            if (p <= 0f) continue
            drawPath(
                path = partialPath(seg.pts, min(p, 1f)),
                color = Color.White,
                style = Stroke(width = seg.w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            )
        }
        // ফুল/কুঁড়ির জায়গা মাস্ক থেকে মুছে ফেলা
        for (poly in listOf(POLY_TOP, POLY_BUD, POLY_BOT)) {
            drawPath(path = pathFrom(poly).apply { close() }, color = Color.Black, blendMode = BlendMode.Clear)
        }
        drawLogoFull(logo, blend = BlendMode.SrcIn)
        drawContext.canvas.restore()
    }
}

private fun DrawScope.drawPop(logo: ImageBitmap, poly: FloatArray, center: FloatArray, t: Float, t0: Float) {
    val p = (t - t0) / 0.65f
    if (p <= 0f) return
    val e = cubicBezierY(min(p, 1f), 0.2f, 1.25f, 0.35f, 1f)
    val cx = center[0]
    val cy = center[1]
    withTransform({
        translate(cx, cy)
        scale(e, e)
        translate(-cx, -cy)
    }) {
        clipPath(pathFrom(poly).apply { close() }) {
            drawLogoFull(logo, alpha = e.coerceIn(0f, 1f))
        }
    }
}

/** লেখার লাইন: পর্দা ডানে সরলে বাম→ডান প্রকাশ। */
private fun DrawScope.drawTextLine(logo: ImageBitmap, t: Float, t0: Float, dur: Float, coverX0: Float, y0: Float, y1: Float) {
    val cover = if (t <= t0) {
        coverX0
    } else {
        coverX0 + 1450f * cubicBezierY(min((t - t0) / dur, 1f), 0.7f, 0f, 0.2f, 1f)
    }
    if (cover <= 626f) return
    clipRect(625f, y0, min(cover, 2048f), y1) {
        drawLogoFull(logo)
    }
}

private fun DrawScope.drawEmean(logo: ImageBitmap, t: Float) {
    // breath: ২.৩৫ সে. থেকে হালকা ফোলা (ease-in-out alternate)
    var s = 1f
    if (t > 2.35f) {
        val phase = ((t - 2.35f) / 1.15f) % 2f
        val tri = if (phase < 1f) phase else 2f - phase
        s = 1f + 0.02f * (0.5f - 0.5f * kotlin.math.cos(tri * PI.toFloat()))
    }
    withTransform({ scale(s, s, Offset(1024f, 341f)) }) {
        drawEmblemDrawn(logo, t)
        drawPop(logo, POLY_BOT, POLY_BOT_CENTER, t, 1.45f)
        drawPop(logo, POLY_TOP, POLY_TOP_CENTER, t, 1.20f)
        drawPop(logo, POLY_BUD, POLY_BUD_CENTER, t, 1.65f)
        drawTextLine(logo, t, 1.45f, 0.80f, 638f, 168f, 436f)
        drawTextLine(logo, t, 1.90f, 0.70f, 630f, 448f, 548f)
    }
}

@Composable
fun EmeanIntro(onDone: () -> Unit) {
    val context = LocalContext.current
    val logo = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.emean_logo).asImageBitmap()
    }
    var t by remember { mutableStateOf(0f) }
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (!finished) {
            finished = true
            onDone()
        }
    }
    LaunchedEffect(Unit) {
        var start = -1L
        while (!finished) {
            val now = withFrameNanos { it }
            if (start < 0L) start = now
            t = (now - start) / 1_000_000_000f
            if (t >= TOTAL + FADE) break
        }
        finish()
    }
    val fadeAlpha = when {
        t < TOTAL -> 1f
        t >= TOTAL + FADE -> 0f
        else -> 1f - (t - TOTAL) / FADE
    }
    Box(
        Modifier.fillMaxSize().background(IntroBg).clickable { finish() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).aspectRatio(2048f / 682f),
        ) {
            val scale = size.width / 2048f
            withTransform({ scale(scale, scale, Offset.Zero) }) {
                // fade পুরো লোগোতে
                drawContext.canvas.saveLayer(Rect(0f, 0f, 2048f, 682f), Paint().apply { alpha = fadeAlpha })
                drawEmean(logo, t)
                drawContext.canvas.restore()
            }
        }
    }
}
