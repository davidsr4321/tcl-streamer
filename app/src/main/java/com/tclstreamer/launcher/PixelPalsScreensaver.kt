package com.tclstreamer.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/**
 * Two pixel-art friends who stroll around the dimmed screen together while the TV sits idle.
 *
 * It runs at 12 frames a second, which suits the pixel look and keeps the weak TV chip cool.
 */
@Composable
fun PixelPalsScreensaver() {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))) {
        val density = LocalDensity.current
        val pixel = with(density) { PIXEL.toPx() }
        val speed = with(density) { SPEED.toPx() }
        val maxX = constraints.maxWidth - GROUP_W * pixel
        val maxY = constraints.maxHeight - SPRITE_H * pixel

        val pals = remember(maxX, maxY) {
            Stroll(Random.nextFloat() * maxX, maxY * (0.3f + Random.nextFloat() * 0.6f))
        }
        var frame by remember { mutableIntStateOf(0) }

        LaunchedEffect(pals) {
            var last = System.nanoTime()
            while (true) {
                delay(FRAME_MS)
                val now = System.nanoTime()
                pals.update((now - last) / 1e9f, maxX, maxY, speed)
                last = now
                frame++
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            frame // redraw on every tick
            // Snap to the pixel grid so they move in chunky steps, like an old console.
            val left = floor(pals.x / pixel) * pixel
            val top = floor(pals.y / pixel) * pixel
            drawSprite(pals.sprite(DARK, blinkOffset = 0f), left, top, pixel)
            drawSprite(pals.sprite(GINGER, blinkOffset = 1.4f), left + (SPRITE_W + GAP) * pixel, top, pixel)
        }
    }
}

private fun DrawScope.drawSprite(sprite: List<String>, left: Float, top: Float, pixel: Float) {
    for (row in sprite.indices) {
        val line = sprite[row]
        for (col in line.indices) {
            val color = PALETTE[line[col]] ?: continue
            drawRect(color, Offset(left + col * pixel, top + row * pixel), Size(pixel, pixel))
        }
    }
}

/** Where the pair is headed, and whether they are walking or taking a break. */
private class Stroll(var x: Float, var y: Float) {
    private var targetX = x
    private var targetY = y
    private var restLeft = 1.5f
    private var walkClock = 0f
    private var clock = 0f

    fun update(dt: Float, maxX: Float, maxY: Float, speed: Float) {
        clock += dt
        if (restLeft > 0f) {
            restLeft -= dt
            if (restLeft <= 0f) {
                targetX = Random.nextFloat() * maxX
                targetY = maxY * (0.25f + Random.nextFloat() * 0.75f)
            }
            return
        }
        val dx = targetX - x
        val dy = targetY - y
        val dist = hypot(dx, dy)
        if (dist < 1f) {
            restLeft = 2f + Random.nextFloat() * 5f
            return
        }
        val step = min(dist, speed * dt)
        x += dx / dist * step
        y += dy / dist * step
        walkClock += dt
    }

    fun sprite(pal: Pal, blinkOffset: Float): List<String> = when {
        restLeft > 0f -> if ((clock + blinkOffset) % 3.2f < 0.18f) pal.blink else pal.stand
        (walkClock / 0.18f).toInt() % 2 == 0 -> pal.walkA
        else -> pal.walkB
    }
}

/** One character: a standing pose, plus blink and two walking frames built from it. */
private class Pal(val stand: List<String>) {
    private val pants = stand[17][2]
    val blink = stand.mapIndexed { i, line -> if (i == EYE_ROW) line.replace('E', 's') else line }
    val walkA = stand.take(18) + "..$pants$pants$pants...$pants$pants.." + ".KKKK...KK.."
    val walkB = stand.take(18) + "..$pants$pants...$pants$pants$pants.." + "..KK...KKKK."
}

private val PIXEL = 5.dp
/** Walking speed, in dp per second. */
private val SPEED = 42.dp
private const val FRAME_MS = 83L
private const val SPRITE_W = 12
private const val SPRITE_H = 20
private const val GAP = 2
private const val GROUP_W = SPRITE_W * 2 + GAP
private const val EYE_ROW = 4

private val PALETTE = mapOf(
    'S' to Color(0xFFE9B99A), // skin
    's' to Color(0xFFCF9A7C), // skin shade
    'E' to Color(0xFF2A1A12), // eyes
    'K' to Color(0xFF161616), // shoes
    'H' to Color(0xFF2B1D16), // dark hair
    'G' to Color(0xFF4A3F52), // sunglasses up on the head
    'B' to Color(0xFF3A2418), // dark beard
    'T' to Color(0xFFA98BD8), // lavender tee
    'P' to Color(0xFF2C3340), // dark trousers
    'R' to Color(0xFFD9783A), // ginger curls
    'r' to Color(0xFFF0A060), // curl highlights
    'F' to Color(0xFFC47A58), // freckles
    'b' to Color(0xFFC0602C), // ginger beard
    'D' to Color(0xFF3A2622), // dark tee
    'm' to Color(0xFFB0507A), // print on the tee
    'y' to Color(0xFFC9A040), // print on the tee
    'J' to Color(0xFF3D4F6E), // jeans
)

/** Dark hair with sunglasses pushed up, full beard, lavender tee. */
private val DARK = Pal(
    listOf(
        "...HHHHHH...",
        "..GGGHHGGG..",
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

/** Ginger curls, freckles, ginger beard, dark printed tee. */
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
        "DDmmDDDyyDDD",
        "SDmmDDDyyDDS",
        "S.DDDDDDDD.S",
        "..JJJJJJJJ..",
        "..JJJ..JJJ..",
        "..JJJ..JJJ..",
        "..KKK..KKK..",
    )
)
