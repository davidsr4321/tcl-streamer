package com.tclstreamer.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/** The two pixel-art friends wandering the dimmed screen while the TV sits idle. */
@Composable
fun PixelPalsScreensaver() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))) {
        PixelPals(Modifier.fillMaxSize(), pixelSize = 5.dp)
    }
}

/**
 * The two friends, each wandering their own way around this box.
 *
 * With [groundY] set they walk left and right along that line, feet on the ground, between
 * [startPadding] and [endPadding]; without it they roam the lower three quarters of the box.
 * They move at 12 frames a second, which suits the pixel look and keeps the weak TV chip cool,
 * and they freeze whenever the screen is not being drawn.
 */
@Composable
fun PixelPals(
    modifier: Modifier,
    pixelSize: Dp,
    groundY: Dp? = null,
    startPadding: Dp = 0.dp,
    endPadding: Dp = 0.dp,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val pixel = with(density) { pixelSize.toPx() }
        val speed = with(density) { SPEED.toPx() }
        val bounds = with(density) {
            val maxX = constraints.maxWidth - endPadding.toPx() - SPRITE_W * pixel
            val bottom = constraints.maxHeight - SPRITE_H * pixel
            val ground = groundY?.toPx()?.minus(SPRITE_H * pixel)
            Bounds(
                minX = startPadding.toPx().coerceAtMost(maxX),
                maxX = maxX,
                minY = ground ?: (bottom * 0.25f),
                maxY = ground ?: bottom,
            )
        }

        val walkers = remember(bounds) { listOf(DARK, GINGER).map { Walker(it, bounds) } }
        var frame by remember { mutableIntStateOf(0) }

        LaunchedEffect(walkers) {
            var last = System.nanoTime()
            while (true) {
                delay(FRAME_MS)
                withFrameNanos { } // waits while nothing is on screen
                val now = System.nanoTime()
                // Cap the step so a long pause does not teleport anyone.
                val dt = ((now - last) / 1e9f).coerceAtMost(0.25f)
                walkers.forEach { it.update(dt, bounds, speed) }
                last = now
                frame++
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            frame // redraw on every tick
            // Whoever is lower on screen is nearer, so draw them last.
            walkers.sortedBy { it.y }.forEach { walker ->
                // Snap to the pixel grid so they move in chunky steps, like an old console.
                val left = floor(walker.x / pixel) * pixel
                val top = floor(walker.y / pixel) * pixel
                drawSprite(walker.sprite(), left, top, pixel)
            }
        }
    }
}

private data class Bounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
    fun randomX() = minX + Random.nextFloat() * (maxX - minX)
    fun randomY() = minY + Random.nextFloat() * (maxY - minY)
}

private fun DrawScope.drawSprite(sprite: List<String>, left: Float, groundTop: Float, pixel: Float) {
    // Shorter sprites stand on the same ground line as taller ones.
    val top = groundTop + (SPRITE_H - sprite.size) * pixel
    for (row in sprite.indices) {
        val line = sprite[row]
        for (col in line.indices) {
            val color = PALETTE[line[col]] ?: continue
            drawRect(color, Offset(left + col * pixel, top + row * pixel), Size(pixel, pixel))
        }
    }
}

/** One character's own wandering: where they are headed, and whether they are on a short break. */
private class Walker(private val pal: Pal, start: Bounds) {
    var x = start.randomX()
    var y = start.randomY()
    private var targetX = x
    private var targetY = y
    private var restLeft = Random.nextFloat() * 0.8f
    private var walkClock = 0f
    private var clock = Random.nextFloat() * 3f

    fun update(dt: Float, bounds: Bounds, speed: Float) {
        clock += dt
        if (restLeft > 0f) {
            restLeft -= dt
            if (restLeft <= 0f) {
                targetX = bounds.randomX()
                targetY = bounds.randomY()
            }
            return
        }
        val dx = targetX - x
        val dy = targetY - y
        val dist = hypot(dx, dy)
        if (dist < 1f) {
            restLeft = 0.4f + Random.nextFloat() * 1.2f
            return
        }
        val step = min(dist, speed * dt)
        x += dx / dist * step
        y += dy / dist * step
        walkClock += dt
    }

    fun sprite(): List<String> = when {
        restLeft > 0f -> if (clock % 3.2f < 0.18f) pal.blink else pal.stand
        (walkClock / 0.18f).toInt() % 2 == 0 -> pal.walkA
        else -> pal.walkB
    }
}

/** One character: a standing pose, plus blink and two walking frames built from it. */
private class Pal(val stand: List<String>) {
    private val pants = stand[stand.size - 3][2]
    val blink = stand.mapIndexed { i, line -> if (i == EYE_ROW) line.replace('E', 's') else line }
    val walkA = stand.dropLast(2) + "..$pants$pants$pants...$pants$pants.." + ".KKKK...KK.."
    val walkB = stand.dropLast(2) + "..$pants$pants...$pants$pants$pants.." + "..KK...KKKK."
}

/** Walking speed, in dp per second. */
private val SPEED = 42.dp
private const val FRAME_MS = 83L
private const val SPRITE_W = 12
/** Height of the tallest character. */
private const val SPRITE_H = 20
private const val EYE_ROW = 4

private val PALETTE = mapOf(
    'S' to Color(0xFFE9B99A), // skin
    's' to Color(0xFFCF9A7C), // skin shade
    'E' to Color(0xFF2A1A12), // eyes
    'K' to Color(0xFF161616), // shoes
    'H' to Color(0xFF2B1D16), // dark hair
    'B' to Color(0xFF3A2418), // dark beard
    'T' to Color(0xFFA98BD8), // lavender tee
    'P' to Color(0xFF2C3340), // dark trousers
    'R' to Color(0xFFD9783A), // ginger curls
    'r' to Color(0xFFF0A060), // curl highlights
    'F' to Color(0xFFC47A58), // freckles
    'b' to Color(0xFFC0602C), // ginger beard
    'D' to Color(0xFF4F7A4A), // green tee
    'J' to Color(0xFF3D4F6E), // jeans
)

/** Dark hair, full beard, lavender tee. */
private val DARK = Pal(
    listOf(
        "...HHHHHH...",
        "..HHHHHHHH..",
        "..HHHHHHHH..",
        "..HSSSSSSH..",
        "..SESSSSES..",
        ".sSSSSSSSSs.",
        "..BSSssSSB..",
        "..BBBBBBBB..",
        "..BBBSSBBB..",
        "...BBBBBB...",
        "....SSSS....",
        ".TTTTTTTTTT.",
        "TTTTTTTTTTTT",
        "TTTTTTTTTTTT",
        "STTTTTTTTTTS",
        "S.TTTTTTTT.S",
        "..PPPPPPPP..",
        "..PPP..PPP..",
        "..PPP..PPP..",
        "..KKK..KKK..",
    )
)

/** Ginger curls, freckles, ginger beard, green tee. A little shorter. */
private val GINGER = Pal(
    listOf(
        "..r.RR.rR...",
        "..RRRRRRRR..",
        ".RRrRRRRrRR.",
        "..RSSSSSSR..",
        "..SESSSSES..",
        ".sSFSSSSFSs.",
        "..bSSssSSb..",
        "..bSbbbbSb..",
        "..bbbSSbbb..",
        "...bbbbbb...",
        "....SSSS....",
        ".DDDDDDDDDD.",
        "DDDDDDDDDDDD",
        "SDDDDDDDDDDS",
        "S.DDDDDDDD.S",
        "..JJJJJJJJ..",
        "..JJJ..JJJ..",
        "..KKK..KKK..",
    )
)
