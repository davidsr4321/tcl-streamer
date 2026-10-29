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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/**
 * A pixel-art cat that wanders the dimmed screen while the TV sits idle.
 *
 * It runs at 12 frames a second, which suits the pixel look and keeps the weak TV chip cool.
 */
@Composable
fun CatScreensaver() {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))) {
        val density = LocalDensity.current
        val pixel = with(density) { PIXEL.toPx() }
        val speed = with(density) { SPEED.toPx() }
        val catW = SPRITE_W * pixel
        val catH = SPRITE_H * pixel
        val maxX = constraints.maxWidth - catW
        val maxY = constraints.maxHeight - catH

        val cat = remember(maxX, maxY) {
            Cat(Random.nextFloat() * maxX, maxY * (0.3f + Random.nextFloat() * 0.6f))
        }
        var frame by remember { mutableIntStateOf(0) }

        LaunchedEffect(cat) {
            var last = System.nanoTime()
            while (true) {
                delay(FRAME_MS)
                val now = System.nanoTime()
                cat.update((now - last) / 1e9f, maxX, maxY, speed)
                last = now
                frame++
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            frame // redraw on every tick
            val sprite = cat.sprite()
            // Snap to the pixel grid so the cat moves in chunky steps, like an old console.
            val left = floor(cat.x / pixel) * pixel
            val top = floor(cat.y / pixel) * pixel
            for (row in sprite.indices) {
                val line = sprite[row]
                for (col in line.indices) {
                    val color = PALETTE[line[col]] ?: continue
                    val drawCol = if (cat.facingRight) col else SPRITE_W - 1 - col
                    drawRect(
                        color = color,
                        topLeft = Offset(left + drawCol * pixel, top + row * pixel),
                        size = Size(pixel, pixel),
                    )
                }
            }
        }
    }
}

private class Cat(var x: Float, var y: Float) {
    var facingRight = true
    private var targetX = x
    private var targetY = y
    private var restLeft = 1.5f
    private var walkClock = 0f
    private var blinkClock = 0f

    fun update(dt: Float, maxX: Float, maxY: Float, speed: Float) {
        blinkClock += dt
        if (restLeft > 0f) {
            restLeft -= dt
            if (restLeft <= 0f) pickTarget(maxX, maxY)
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
        if (dx != 0f) facingRight = dx > 0f
        walkClock += dt
    }

    private fun pickTarget(maxX: Float, maxY: Float) {
        targetX = Random.nextFloat() * maxX
        // Mostly roam the lower part of the screen, the way a cat keeps to the floor.
        targetY = maxY * (0.25f + Random.nextFloat() * 0.75f)
    }

    fun sprite(): Array<String> = when {
        restLeft > 0f -> if (blinkClock % 3f < 0.18f) SIT_BLINK else SIT
        (walkClock / 0.16f).toInt() % 2 == 0 -> WALK_A
        else -> WALK_B
    }
}

private val PIXEL = 6.dp
/** Walking speed, in dp per second. */
private val SPEED = 48.dp
private const val FRAME_MS = 83L
private const val SPRITE_W = 16
private const val SPRITE_H = 11

private val PALETTE = mapOf(
    'B' to Color(0xFFE8964A), // ginger fur
    'D' to Color(0xFFB5602A), // stripes
    'W' to Color(0xFFF6E7D2), // muzzle and chest
    'E' to Color(0xFF1A1A1A), // eyes
    'P' to Color(0xFFF2A1B0), // nose
)

private val WALK_A = arrayOf(
    "..........B...B.",
    "..........BB.BB.",
    "..........BBBBB.",
    "D.........BEBEB.",
    "B.........BBPBB.",
    ".B..BBBBBBBWWWB.",
    ".BBBBDBBDBBBWB..",
    "..BBBDBBDBBBBB..",
    "..BBBBBBBBBBBB..",
    "..B.B......B.B..",
    "..B.B......B.B..",
)

private val WALK_B = arrayOf(
    "..........B...B.",
    "..........BB.BB.",
    "..........BBBBB.",
    "D.........BEBEB.",
    "B.........BBPBB.",
    ".B..BBBBBBBWWWB.",
    ".BBBBDBBDBBBWB..",
    "..BBBDBBDBBBBB..",
    "..BBBBBBBBBBBB..",
    "...B.B....B.B...",
    "...B.B....B.B...",
)

private val SIT = arrayOf(
    "..........B...B.",
    "..........BB.BB.",
    "..........BBBBB.",
    "..........BEBEB.",
    "..........BBPBB.",
    ".......BBBBWWWB.",
    "......BBDBBBWB..",
    "......BBDBBBBB..",
    "......BBBBBBBB..",
    "..BBBBBBBBBBBB..",
    "..........B.B...",
)

private val SIT_BLINK = SIT.copyOf().also { it[3] = "..........BDBDB." }
