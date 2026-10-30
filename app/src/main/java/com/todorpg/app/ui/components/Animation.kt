package com.todorpg.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.todorpg.app.data.HeroClass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Milliseconds since the app started drawing; drives every idle animation. */
val LocalClock = staticCompositionLocalOf<State<Long>> { error("No clock") }

/** Provides [LocalClock], ticking at ~30fps while the UI is visible. */
@Composable
fun ClockProvider(content: @Composable () -> Unit) {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                if (now - start - clock.longValue >= 33) clock.longValue = now - start
            }
        }
    }
    CompositionLocalProvider(LocalClock provides clock, content = content)
}

/** Twinkling night sky behind the menus. */
@Composable
fun Starfield(modifier: Modifier = Modifier) {
    val clock = LocalClock.current
    val stars = remember {
        val rng = kotlin.random.Random(42)
        List(70) { Triple(rng.nextFloat(), rng.nextFloat(), rng.nextFloat()) }
    }
    Canvas(modifier) {
        val t = clock.value / 1000f
        val u = 2.5f * density
        for ((x, y, p) in stars) {
            val a = 0.25f + 0.75f * (0.5f + 0.5f * sin(t * (1f + p * 2f) + p * 20f))
            val s = if (p > 0.85f) u * 1.6f else u
            drawRect(Color(1f, 1f, 0.9f, a * 0.8f), Offset(x * size.width, y * size.height), Size(s, s))
        }
    }
}

private fun DrawScope.pixel(color: Color, x: Float, y: Float, u: Float, alpha: Float = 1f) {
    drawRect(color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)), Offset(x - u / 2, y - u / 2), Size(u, u))
}

private val NoteGlyph = listOf("..KK", "..KK", "..K.", "..K.", "KKK.", "KKK.")

/**
 * Per-class attack effect drawn over the target. [progress] runs 0..1.
 * Everything is built from chunky square "pixels" to match the sprites.
 */
fun DrawScope.attackEffect(heroClass: HeroClass, progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val cx = size.width / 2
    val cy = size.height / 2
    val r = size.minDimension / 2
    val u = size.minDimension / 20
    when (heroClass) {
        HeroClass.KNIGHT, HeroClass.SAMURAI -> {
            val slashes = if (heroClass == HeroClass.SAMURAI) listOf(1f, -1f) else listOf(1f)
            slashes.forEachIndexed { i, dir ->
                val p = ((progress - i * 0.2f) / 0.6f).coerceIn(0f, 1f)
                if (p <= 0f) return@forEachIndexed
                val steps = 18
                val head = (p * steps).toInt()
                for (s in 0..head) {
                    val f = s / steps.toFloat()
                    val x = cx + dir * (r - 2 * r * f) * 0.9f
                    val y = cy - r * 0.9f + 2 * r * f * 0.9f
                    val fade = 1f - (head - s) / 8f - (progress - 0.6f).coerceAtLeast(0f) * 2.5f
                    pixel(Color.White, x, y, u * 1.4f, fade)
                    pixel(Color(0xFFA0E0FF), x + u, y, u, fade * 0.7f)
                }
            }
        }
        HeroClass.MONK -> {
            val spots = listOf(-0.3f to -0.2f, 0.25f to 0.1f, -0.05f to 0.35f)
            spots.forEachIndexed { i, (ox, oy) ->
                val p = ((progress - i * 0.22f) / 0.35f).coerceIn(0f, 1f)
                if (p <= 0f || p >= 1f) return@forEachIndexed
                val x = cx + ox * r * 1.6f
                val y = cy + oy * r * 1.6f
                val spread = u * (1 + p * 3)
                pixel(Color.White, x, y, u * 1.5f, 1 - p)
                for (k in 0 until 8) {
                    val a = k * PI.toFloat() / 4
                    pixel(Color(0xFFFFD040), x + cos(a) * spread, y + sin(a) * spread, u, 1 - p)
                }
            }
        }
        HeroClass.RANGER -> {
            val fly = (progress / 0.45f).coerceIn(0f, 1f)
            if (progress < 0.45f) {
                val x = size.width * 1.4f - (size.width * 1.4f - cx) * fly
                for (k in 0 until 6) pixel(if (k == 0) Color(0xFFE0E0E0) else Color(0xFF8B5A2B), x + k * u, cy, u)
                pixel(Color.White, x + 6 * u, cy - u, u)
                pixel(Color.White, x + 6 * u, cy + u, u)
            } else {
                val p = (progress - 0.45f) / 0.55f
                for (k in 0 until 10) {
                    val a = k * PI.toFloat() / 5
                    pixel(Color(0xFFFFF0A0), cx + cos(a) * u * (1 + p * 5), cy + sin(a) * u * (1 + p * 5), u, 1 - p)
                }
            }
        }
        HeroClass.MAGE -> {
            for (k in 0 until 22) {
                val seed = k * 37 % 22 / 22f
                val p = ((progress * 1.4f - seed * 0.4f)).coerceIn(0f, 1f)
                if (p <= 0f || p >= 1f) continue
                val x = cx + (seed - 0.5f) * r * 1.6f + sin(p * 9 + k) * u
                val y = cy + r * 0.8f - p * r * 1.8f
                val color = when {
                    p < 0.3f -> Color(0xFFFFF080)
                    p < 0.6f -> Color(0xFFFF9020)
                    else -> Color(0xFFE02010)
                }
                pixel(color, x, y, u * (1.6f - p), 1.2f - p)
            }
        }
        HeroClass.CLERIC -> {
            val beam = if (progress < 0.5f) progress * 2 else (1 - progress) * 2
            val w = r * 0.5f * beam
            drawRect(Color(0x66FFFFE0), Offset(cx - w / 2, 0f), Size(w, size.height))
            for (k in 0 until 10) {
                val seed = (k * 53 % 10) / 10f
                val y = size.height * (1 - ((progress + seed) % 1f))
                val x = cx + (seed - 0.5f) * r * 1.2f
                pixel(Color(0xFFFFF8C0), x, y, u, beam * 1.5f)
                pixel(Color(0xFFFFF8C0), x - u, y, u * 0.6f, beam)
                pixel(Color(0xFFFFF8C0), x + u, y, u * 0.6f, beam)
            }
        }
        HeroClass.BARD -> {
            for (k in 0 until 4) {
                val p = (progress * 1.3f - k * 0.1f).coerceIn(0f, 1f)
                if (p <= 0f || p >= 1f) continue
                val x0 = cx + (k - 1.5f) * r * 0.5f + sin(p * 8 + k) * u * 2
                val y0 = cy + r * 0.6f - p * r * 1.6f
                val color = listOf(Color(0xFFFF80C0), Color(0xFF80F0FF), Color(0xFFFFE060), Color(0xFFA0FF80))[k]
                NoteGlyph.forEachIndexed { gy, row ->
                    row.forEachIndexed { gx, ch ->
                        if (ch == 'K') pixel(color, x0 + gx * u * 0.7f, y0 + gy * u * 0.7f, u * 0.7f, 1.3f - p)
                    }
                }
            }
        }
        HeroClass.DRUID -> {
            for (k in 0 until 14) {
                val a = k * 2 * PI.toFloat() / 14 + progress * 7
                val rad = r * (1.1f - progress * 0.8f)
                val color = if (k % 2 == 0) Color(0xFF60D050) else Color(0xFF30A040)
                pixel(color, cx + cos(a) * rad, cy + sin(a) * rad * 0.8f, u * 1.2f, 1.4f - progress)
            }
        }
    }
}

/** Rising green "+" sparkles for healing. */
fun DrawScope.healEffect(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val u = size.minDimension / 14
    for (k in 0 until 5) {
        val p = (progress * 1.3f - k * 0.08f).coerceIn(0f, 1f)
        val x = size.width * (0.15f + k * 0.18f)
        val y = size.height * (0.9f - p * 0.8f)
        val c = Color(0xFF80FF90)
        val a = 1.3f - p
        pixel(c, x, y, u, a)
        pixel(c, x - u, y, u, a)
        pixel(c, x + u, y, u, a)
        pixel(c, x, y - u, u, a)
        pixel(c, x, y + u, u, a)
    }
}

/** Gold sparkles bursting outward, used for level-ups and quest completion. */
fun DrawScope.sparkleBurst(progress: Float, count: Int = 12, color: Color = Color(0xFFFFE070)) {
    if (progress <= 0f || progress >= 1f) return
    val cx = size.width / 2
    val cy = size.height / 2
    val u = size.minDimension / 16
    for (k in 0 until count) {
        val a = k * 2 * PI.toFloat() / count
        val rad = size.minDimension * 0.15f + progress * size.minDimension * 0.45f
        val x = cx + cos(a) * rad
        val y = cy + sin(a) * rad
        val alpha = 1f - progress
        pixel(color, x, y, u, alpha)
        if (k % 2 == 0) {
            pixel(Color.White, x - u, y, u * 0.6f, alpha)
            pixel(Color.White, x + u, y, u * 0.6f, alpha)
        }
    }
}

/** Ice shards raining onto the target (the Mage's Frost skill). */
fun DrawScope.iceEffect(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val u = size.minDimension / 20
    for (k in 0 until 12) {
        val seed = (k * 41 % 12) / 12f
        val p = (progress * 1.5f - seed * 0.5f).coerceIn(0f, 1f)
        if (p <= 0f || p >= 1f) continue
        val x = size.width * (0.1f + seed * 0.8f)
        val y = -u * 4 + p * (size.height * 0.75f)
        val a = if (p > 0.8f) (1 - p) * 5 else 1f
        for (j in 0 until 4) pixel(Color(0xFFB0F0FF), x, y + j * u, u, a * (1f - j * 0.2f))
        pixel(Color.White, x, y + 4 * u, u * 1.2f, a)
    }
    val frost = if (progress > 0.5f) (1 - progress) * 2 else progress * 2
    drawRect(Color(0x5560C8FF).copy(alpha = 0.35f * frost), Offset.Zero, size)
}

/** Rotating ring of runes under a casting hero. */
fun DrawScope.castCircle(time: Long, color: Color) {
    val cx = size.width / 2
    val cy = size.height * 0.92f
    val u = size.minDimension / 18
    val n = 14
    for (k in 0 until n) {
        val a = k * 2 * PI.toFloat() / n + time / 300f
        val x = cx + cos(a) * size.width * 0.5f
        val y = cy + sin(a) * size.height * 0.12f
        pixel(color, x, y, u, 0.5f + 0.5f * sin(time / 120f + k))
    }
    // Motes rising from the circle.
    for (k in 0 until 5) {
        val p = ((time / 900f) + k / 5f) % 1f
        pixel(color, cx + (k - 2) * u * 2.2f, cy - p * size.height * 0.9f, u * 0.8f, 1 - p)
    }
}

/** White arc in front of a hero mid-swing. Heroes face left. */
fun DrawScope.swingArc(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val u = size.minDimension / 14
    val steps = 10
    val head = (progress * steps * 1.6f).toInt().coerceAtMost(steps)
    for (s in 0..head) {
        val a = (-100f + s * 14f) * PI.toFloat() / 180f
        val r = size.minDimension * 0.62f
        val x = size.width * 0.35f + cos(a) * r * -1f
        val y = size.height * 0.55f + sin(a) * r
        val fade = (1f - (head - s) / 6f) * (1f - progress)
        pixel(Color.White, x, y, u * 1.2f, fade * 1.6f)
    }
}

/** A small pixel shield in front of a guarding hero. */
fun DrawScope.guardShield(time: Long) {
    val glyph = listOf(".KKKK.", "KBBBBK", "KBWWBK", "KBWWBK", ".KBBK.", "..KK..")
    val u = size.minDimension / 14
    val x0 = -u * 2
    val y0 = size.height * 0.35f + sin(time / 250f) * u * 0.5f
    glyph.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            val color = when (c) {
                'K' -> Color(0xFF101018)
                'B' -> Color(0xFF4080E0)
                'W' -> Color(0xFFE0F0FF)
                else -> return@forEachIndexed
            }
            drawRect(color, Offset(x0 + x * u, y0 + y * u), Size(u + 0.5f, u + 0.5f))
        }
    }
}
