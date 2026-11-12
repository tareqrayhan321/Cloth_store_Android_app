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
 * সাদা পটে (#FFFFFF — হোম স্ক্রিনের BodyBg-র হুবহু একই রঙ; তারেকের নির্দেশ, 2026-11-12)
 * মাঝখানে পূর্ণ লোগো; মোট দৈর্ঘ্য ≈ ৩.৪ সেকেন্ড + fade; ট্যাপ করলে স্কিপ।
 * টাইমলাইন: ইমব্লেম কলমে আঁকার মতো রেখা ধরে ফোটে (০.১০–১.৭৫) → তুলার ফুল পপ
 * (১.২০ / ১.৪৫ / ১.৬৫) → "EMEAN" পর্দা-সরণ (১.৪৫) → "— CLOTH STORE —" পর্দা-সরণ (১.৯০)
 * → শেষে হালকা breath।
 *
 * পারফরম্যান্স (2026-11-10 ল্যাগ-ফিক্স): সব সেগমেন্টের পূর্ণ পথ ও দৈর্ঘ্য-তালিকা একবারই
 * বানানো হয় (remember) — প্রতি ফ্রেমে শুধু চলমান ২-৩টা সেগমেন্টের আংশিক পথ হিসাব হয়,
 * শেষ হওয়া সেগমেন্ট ক্যাশ করা পথেই আঁকা হয়। মাস্ক-লেয়ার শুধু ইমব্লেম এলাকায়
 * (625×682), আর fade-লেয়ার কেবল fade চলাকালেই বসে।
 *
 * স্মুথ মোশন (2026-11-10 রাত, তারেকের ভিডিও দেখে): ফোন ব্যস্ত থাকলে ফ্রেম বাদ পড়ে —
 * ঘড়ির সময় সরাসরি আঁকায় বসালে অ্যানিমেশন লাফ দিয়ে "ছিঁড়ে ছিঁড়ে" এগোয়। তাই আঁকার
 * সময় (shown) আসল সময়ের (target) পেছনে মসৃণভাবে টানা হয় (lerp): ঝাঁকুনি এলেও গতি
 * ধারাবাহিক থাকে, ধীরে ধীরে ধরে ফেলে। আর পুরো 2048×682 ছবি প্রতি ফ্রেমে না টেনে
 * ইমব্লেম ও লেখার অংশ আগেই আলাদা ছোট বিটম্যাপে কেটে রাখা হয়।
 *
 * আরও স্মুথ (2026-11-12, তারেকের "এখনো ছিঁড়ে ছিঁড়ে আসে" মন্তব্যের পর): আসল ডেটার
 * ১৫০টা সেগমেন্টের অনেকগুলোই মাত্র ০.০২ সেকেন্ডের — ফ্রেম বাদ পড়লেই পুরো টুকরো
 * একবারে ফুটে উঠত। এখন buildBands পরস্পর-লাগোয়া সেগমেন্ট জোড়া দিয়ে একটানা
 * কলম-স্ট্রোক (ব্যান্ড) বানায়, আর প্রতি ব্যান্ডের বিন্দু Catmull-Rom দিয়ে ঘন করা —
 * কলমের ডগা কোণে কোণে না লাফিয়ে বক্ররেখা ধরে গ্লাইড করে। সময়ের গাঁটে আসল
 * সেগমেন্ট-ছন্দই থাকে, তাই কোরিওগ্রাফি হুবহু আগেরটাই, শুধু চলনটা একটানা।
 */

private val IntroBg = Color(0xFFFFFFFF)

/** আঁকার ডেটা — অ্যাপ চালুতে একবারই তৈরি (প্রথম ফ্রেমের পথে আসে না) */
private val BAND_DRAWS: List<BandDraw> by lazy { buildBands(EMEAN_SEGS) }
private val FLOWER_PATHS: List<Path> by lazy {
    listOf(POLY_BOT, POLY_TOP, POLY_BUD).map { pathFrom(it).apply { close() } }
}
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

/** একটানা কলম-স্ট্রোকের (ব্যান্ড) আগাম-বানানো আঁকার ডেটা: ঘন করা বিন্দু, জমা দৈর্ঘ্য, সময়-গাঁট। */
private class BandDraw(
    val w: Float,
    val startT: Float,
    val endT: Float,
    private val pts: FloatArray,
    private val cum: FloatArray,
    private val knotT: FloatArray,
    private val knotD: FloatArray,
) {
    val total: Float = cum.lastOrNull() ?: 0f
    val full: Path = pathFrom(pts)

    /** [t] সময়ে কলমের ডগা কত দূর এগোবে — আসল সেগমেন্ট-শেষের সময়-গাঁট ধরে ইন্টারপোলেশন। */
    fun distAt(t: Float): Float {
        if (t <= startT) return 0f
        if (t >= endT) return total
        var i = 1
        while (i < knotT.size && knotT[i] < t) i++
        if (i >= knotT.size) return total
        val t0 = knotT[i - 1]
        val t1 = knotT[i]
        val d0 = knotD[i - 1]
        val d1 = knotD[i]
        val r = if (t1 > t0) ((t - t0) / (t1 - t0)).coerceIn(0f, 1f) else 1f
        return d0 + (d1 - d0) * r
    }

    /** [t] সময়ের আংশিক পথ — ক্যাশ করা দৈর্ঘ্য-তালিকা দিয়ে (প্রতি ফ্রেমে sqrt নয়)। */
    fun partialPathAt(t: Float): Path {
        val target = distAt(t)
        val p = Path()
        val n = pts.size / 2
        if (n == 0) return p
        p.moveTo(pts[0], pts[1])
        if (target <= 0f || n == 1) return p
        for (i in 1 until n) {
            if (cum[i] <= target) {
                p.lineTo(pts[i * 2], pts[i * 2 + 1])
            } else {
                val prev = cum[i - 1]
                val r = if (cum[i] > prev) (target - prev) / (cum[i] - prev) else 0f
                p.lineTo(
                    pts[(i - 1) * 2] + (pts[i * 2] - pts[(i - 1) * 2]) * r,
                    pts[(i - 1) * 2 + 1] + (pts[i * 2 + 1] - pts[(i - 1) * 2 + 1]) * r,
                )
                break
            }
        }
        return p
    }
}

/**
 * সেগমেন্ট → ব্যান্ড: যে সেগমেন্ট আগেরটার শেষ বিন্দু থেকে (≤৫ একক) প্রায় সঙ্গে সঙ্গে
 * (সময়ের ফাঁক ≤০.০৩৫ সে.) শুরু, সেগুলো এক শৃঙ্খল; শৃঙ্খলের ভেতরে প্রস্থের ধাপ
 * ৫-এর বেশি হলেই নতুন ব্যান্ড। (অ্যালগরিদম আগে স্ক্রিপ্টে যাচাইকৃত: ১৫০ → ৫৮ শৃঙ্খল।)
 */
private fun buildBands(segs: List<IntroSeg>): List<BandDraw> {
    val order = segs.indices.sortedBy { segs[it].t }
    val chains = mutableListOf<MutableList<IntroSeg>>()
    val tailX = mutableListOf<Float>()
    val tailY = mutableListOf<Float>()
    val tailT = mutableListOf<Float>()
    for (idx in order) {
        val s = segs[idx]
        var best = -1
        var bestGap = Float.MAX_VALUE
        for (c in chains.indices) {
            val gap = s.t - tailT[c]
            if (gap < -0.035f || gap > 0.035f) continue
            val dx = tailX[c] - s.pts[0]
            val dy = tailY[c] - s.pts[1]
            if (dx * dx + dy * dy > 25f) continue
            val ag = kotlin.math.abs(gap)
            if (ag < bestGap) {
                bestGap = ag
                best = c
            }
        }
        if (best < 0) {
            chains.add(mutableListOf(s))
            tailX.add(s.pts[s.pts.size - 2])
            tailY.add(s.pts[s.pts.size - 1])
            tailT.add(s.t + s.d)
        } else {
            chains[best].add(s)
            tailX[best] = s.pts[s.pts.size - 2]
            tailY[best] = s.pts[s.pts.size - 1]
            tailT[best] = s.t + s.d
        }
    }
    val bands = mutableListOf<BandDraw>()
    for (chain in chains) {
        var cur = mutableListOf<IntroSeg>()
        for (s in chain) {
            if (cur.isNotEmpty() && kotlin.math.abs(s.w - cur[0].w) > 5f) {
                bands.add(makeBand(cur))
                cur = mutableListOf()
            }
            cur.add(s)
        }
        if (cur.isNotEmpty()) bands.add(makeBand(cur))
    }
    return bands
}

/** এক ব্যান্ডের ঘন (Catmull-Rom) বিন্দু, জমা দৈর্ঘ্য আর সময়-গাঁট বানানো। */
private fun makeBand(bandSegs: List<IntroSeg>): BandDraw {
    // জয়েন্টের প্রায়-হুবহু ডুপ্লিকেট বাদ দিয়ে আসল বিন্দুর তালিকা + প্রতি সেগমেন্ট-শেষের সূচক
    val raw = mutableListOf<Float>()
    val jointIdx = mutableListOf<Int>()
    for (s in bandSegs) {
        var i = 0
        if (raw.isNotEmpty()) {
            val dx = s.pts[0] - raw[raw.size - 2]
            val dy = s.pts[1] - raw[raw.size - 1]
            if (dx * dx + dy * dy < 0.25f) i = 2
        }
        while (i + 1 < s.pts.size) {
            raw.add(s.pts[i])
            raw.add(s.pts[i + 1])
            i += 2
        }
        jointIdx.add(raw.size / 2 - 1)
    }
    val n = raw.size / 2
    // Catmull-Rom: প্রতি স্প্যানে ৩টা করে মধ্যবিন্দু — ডগা কোণে না লাফিয়ে বক্র ধরে চলে
    val dense = mutableListOf<Float>()
    val denseIdx = IntArray(n)
    fun px(k: Int): Float = raw[k.coerceIn(0, n - 1) * 2]
    fun py(k: Int): Float = raw[k.coerceIn(0, n - 1) * 2 + 1]
    if (n == 1) {
        dense.add(raw[0])
        dense.add(raw[1])
    } else {
        for (i in 0 until n - 1) {
            denseIdx[i] = dense.size / 2
            dense.add(px(i))
            dense.add(py(i))
            for (j in 1..3) {
                val u = j / 4f
                val u2 = u * u
                val u3 = u2 * u
                dense.add(
                    0.5f * (2f * px(i) + (-px(i - 1) + px(i + 1)) * u +
                        (2f * px(i - 1) - 5f * px(i) + 4f * px(i + 1) - px(i + 2)) * u2 +
                        (-px(i - 1) + 3f * px(i) - 3f * px(i + 1) + px(i + 2)) * u3)
                )
                dense.add(
                    0.5f * (2f * py(i) + (-py(i - 1) + py(i + 1)) * u +
                        (2f * py(i - 1) - 5f * py(i) + 4f * py(i + 1) - py(i + 2)) * u2 +
                        (-py(i - 1) + 3f * py(i) - 3f * py(i + 1) + py(i + 2)) * u3)
                )
            }
        }
        denseIdx[n - 1] = dense.size / 2
        dense.add(px(n - 1))
        dense.add(py(n - 1))
    }
    val pts = dense.toFloatArray()
    val m = pts.size / 2
    val cum = FloatArray(m)
    var acc = 0f
    for (i in 1 until m) {
        val dx = pts[i * 2] - pts[(i - 1) * 2]
        val dy = pts[i * 2 + 1] - pts[(i - 1) * 2 + 1]
        acc += sqrt(dx * dx + dy * dy)
        cum[i] = acc
    }
    // সময়-গাঁট: (শুরুর সময়, ০) + প্রতি আসল সেগমেন্ট-শেষে (শেষ-সময়, ততদূর পৌঁছানো দৈর্ঘ্য)
    val knotT = mutableListOf(bandSegs[0].t)
    val knotD = mutableListOf(0f)
    for (k in bandSegs.indices) {
        val endTime = bandSegs[k].t + bandSegs[k].d
        knotT.add(maxOf(endTime, knotT[k]))
        knotD.add(cum[denseIdx[jointIdx[k]]])
    }
    return BandDraw(
        w = bandSegs[0].w,
        startT = bandSegs[0].t,
        endT = bandSegs[bandSegs.size - 1].t + bandSegs[bandSegs.size - 1].d,
        pts = pts,
        cum = cum,
        knotT = knotT.toFloatArray(),
        knotD = knotD.toFloatArray(),
    )
}

/** আগেই কেটে রাখা ছোট বিটম্যাপ (ইমব্লেম/লেখার অংশ) তার আসল জায়গায় আঁকা — পুরো ছবির চেয়ে হালকা। */
private fun DrawScope.drawLogoRegion(
    bmp: ImageBitmap, dstX: Int, dstY: Int, w: Int, h: Int,
    alpha: Float = 1f, blend: BlendMode = BlendMode.SrcOver,
) {
    drawImage(
        image = bmp,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bmp.width, bmp.height),
        dstOffset = IntOffset(dstX, dstY),
        dstSize = IntSize(w, h),
        alpha = alpha,
        blendMode = blend,
    )
}

/** ইমব্লেম: স্ট্রোক-মাস্কের ভেতর দিয়ে লোগো — ফুলের এলাকা বাদ (ওগুলো পপে আসবে)। */
private fun DrawScope.drawEmblemDrawn(logo: ImageBitmap, bands: List<BandDraw>, flowerPaths: List<Path>, t: Float) {
    clipRect(0f, 0f, 625f, 682f) {
        // লেয়ার শুধু ইমব্লেম এলাকা জুড়ে (আগে পুরো 2048 প্রশস্ত ছিল — ল্যাগের একটা কারণ)
        drawContext.canvas.saveLayer(Rect(0f, 0f, 625f, 682f), Paint())
        for (band in bands) {
            if (t <= band.startT) continue
            drawPath(
                path = if (t >= band.endT) band.full else band.partialPathAt(t),
                color = Color.White,
                style = Stroke(width = band.w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            )
        }
        // ফুল/কুঁড়ির জায়গা মাস্ক থেকে মুছে ফেলা
        for (polyPath in flowerPaths) {
            drawPath(path = polyPath, color = Color.Black, blendMode = BlendMode.Clear)
        }
        drawLogoRegion(logo, 0, 0, 625, 682, blend = BlendMode.SrcIn)
        drawContext.canvas.restore()
    }
}

private fun DrawScope.drawPop(logo: ImageBitmap, polyPath: Path, center: FloatArray, t: Float, t0: Float) {
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
        clipPath(polyPath) {
            drawLogoRegion(logo, 0, 0, 625, 682, alpha = e.coerceIn(0f, 1f))
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
        drawLogoRegion(logo, 625, 150, 1423, 410)
    }
}

private fun DrawScope.drawEmean(
    emblemBmp: ImageBitmap,
    textBmp: ImageBitmap,
    bands: List<BandDraw>,
    flowerPaths: List<Path>,
    t: Float,
) {
    // breath: ২.৩৫ সে. থেকে হালকা ফোলা (ease-in-out alternate)
    var s = 1f
    if (t > 2.35f) {
        val phase = ((t - 2.35f) / 1.15f) % 2f
        val tri = if (phase < 1f) phase else 2f - phase
        s = 1f + 0.02f * (0.5f - 0.5f * kotlin.math.cos(tri * PI.toFloat()))
    }
    withTransform({ scale(s, s, Offset(1024f, 341f)) }) {
        drawEmblemDrawn(emblemBmp, bands, flowerPaths, t)
        drawPop(emblemBmp, flowerPaths[0], POLY_BOT_CENTER, t, 1.45f)
        drawPop(emblemBmp, flowerPaths[1], POLY_TOP_CENTER, t, 1.20f)
        drawPop(emblemBmp, flowerPaths[2], POLY_BUD_CENTER, t, 1.65f)
        drawTextLine(textBmp, t, 1.45f, 0.80f, 638f, 168f, 436f)
        drawTextLine(textBmp, t, 1.90f, 0.70f, 630f, 448f, 548f)
    }
}

@Composable
fun EmeanIntro(onDone: () -> Unit) {
    val context = LocalContext.current
    // বিটম্যাপ ব্যাকগ্রাউন্ড থ্রেডে ডিকোড হয় — প্রথম ফ্রেম শুধু সাদা পট (স্প্ল্যাশের রঙই),
    // ছবি তৈরি হলেই ঘড়ি শুরু; আঁকার মাঝে কোনো ভারী কাজ থাকে না।
    var assets by remember { mutableStateOf<Pair<ImageBitmap, ImageBitmap>?>(null) }
    var t by remember { mutableStateOf(0f) }
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (!finished) {
            finished = true
            onDone()
        }
    }
    LaunchedEffect(Unit) {
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            // আঁকার ডেটাও এখানেই গরম করে রাখা হয় (lazy) — প্রথম ফ্রেমে কোনো হিসাব নয়
            BAND_DRAWS.size
            FLOWER_PATHS.size
            val full = BitmapFactory.decodeResource(context.resources, R.drawable.emean_logo)
            val emblem = android.graphics.Bitmap.createBitmap(full, 0, 0, 625, 682).asImageBitmap()
            val text = android.graphics.Bitmap.createBitmap(full, 625, 150, 1423, 410).asImageBitmap()
            emblem to text
        }
        assets = loaded
        var start = -1L
        var prev = -1L
        var shown = 0f
        while (!finished) {
            val now = withFrameNanos { it }
            if (start < 0L) {
                start = now
                prev = now
            }
            val target = (now - start) / 1_000_000_000f
            // dt-র ঊর্ধ্বসীমা ৫০ms (আগে ১০০ ছিল) — একটা বড় ফ্রেম-ঝাঁকুনিতে shown
            // এক লাফে বেশি এগিয়ে "ছিঁড়ে" যাওয়া কমে; ধীরে ধীরেই ধরে ফেলে।
            val dtMs = ((now - prev) / 1_000_000f).coerceIn(1f, 50f)
            prev = now
            // মসৃণ অনুসরণ: প্রতি ফ্রেমে ফাঁকের একটা অংশ এগোনো হয় (60fps-এ ~45%) —
            // ফ্রেম বাদ পড়লেও আঁকা লাফ দেয় না, গ্লাইড করে ধরে ফেলে।
            val k = 1f - Math.pow(0.55, (dtMs / 16.7).toDouble()).toFloat()
            shown += (target - shown) * k
            if (target > TOTAL && shown > target - 0.02f) shown = target
            t = shown
            if (target >= TOTAL + FADE && shown >= TOTAL + FADE - 0.03f) break
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
        val a = assets
        if (a != null) {
            Canvas(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).aspectRatio(2048f / 682f),
            ) {
                val scale = size.width / 2048f
                withTransform({ scale(scale, scale, Offset.Zero) }) {
                    if (fadeAlpha < 1f) {
                        // fade-লেয়ার কেবল fade-এর ০.৩ সেকেন্ডেই (আগে প্রতি ফ্রেমে বসত)
                        drawContext.canvas.saveLayer(Rect(0f, 0f, 2048f, 682f), Paint().apply { alpha = fadeAlpha })
                        drawEmean(a.first, a.second, BAND_DRAWS, FLOWER_PATHS, t)
                        drawContext.canvas.restore()
                    } else {
                        drawEmean(a.first, a.second, BAND_DRAWS, FLOWER_PATHS, t)
                    }
                }
            }
        }
    }
}
