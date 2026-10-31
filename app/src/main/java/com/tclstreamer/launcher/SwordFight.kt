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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The idle screensaver: the screen dims and the two pixel friends duel with swords, one move
 * after another, swapping who attacks. Runs at 12 frames a second and freezes when not drawn.
 */
@Composable
fun SwordFightScreensaver() {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f))) {
        val pixel = with(LocalDensity.current) { FIGHT_PIXEL.toPx() }
        val duel = remember { Duel() }
        var frame by remember { mutableIntStateOf(0) }

        LaunchedEffect(duel) {
            var last = System.nanoTime()
            while (true) {
                delay(FRAME_MS)
                withFrameNanos { } // waits while nothing is on screen
                val now = System.nanoTime()
                duel.advance(((now - last) / 1e9f).coerceAtMost(0.25f))
                last = now
                frame++
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            frame // redraw on every tick
            val stage = Stage(
                scope = this,
                pixel = pixel,
                centerX = (size.width / pixel / 2).toInt(),
                ground = (size.height / pixel * 0.7f).toInt(),
            )
            stage.draw(duel)
        }
    }
}

// ---- Choreography ----

private enum class Body { STAND, STEP, DUCK, HURT, FALLEN }

/**
 * Where one fighter is at a moment: [dx] pixels toward the opponent, [dy] pixels off the ground,
 * and the sword's angle in degrees (0 points at the opponent, 90 straight up, -90 down).
 */
private data class Pose(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val sword: Float = GUARD,
    val body: Body = Body.STAND,
    val armed: Boolean = true,
)

/** Poses at set times. Positions and the sword glide between keys; body and grip switch at each key. */
private class Track(private vararg val keys: Pair<Float, Pose>) {
    fun at(t: Float): Pose {
        val i = keys.indexOfLast { it.first <= t }.coerceAtLeast(0)
        val (t0, from) = keys[i]
        if (i == keys.lastIndex) return from
        val (t1, to) = keys[i + 1]
        val f = ((t - t0) / (t1 - t0)).coerceIn(0f, 1f)
        return from.copy(
            dx = from.dx + (to.dx - from.dx) * f,
            dy = from.dy + (to.dy - from.dy) * f,
            sword = from.sword + (to.sword - from.sword) * f,
        )
    }
}

private class Move(
    val duration: Float,
    val attacker: Track,
    val defender: Track,
    /** Times the blades meet. */
    val clangs: List<Float> = emptyList(),
    /** Times dust kicks up under the defender. */
    val dust: List<Float> = emptyList(),
    /**
     * The defender's sword when it leaves their hand: dx is pixels out from the stage centre on
     * the defender's side, dy is height, sword is the angle.
     */
    val looseSword: Track? = null,
    val looseFrom: Float = 0f,
    val looseUntil: Float = 0f,
)

private fun p(
    dx: Float = 0f,
    dy: Float = 0f,
    sword: Float = GUARD,
    body: Body = Body.STAND,
    armed: Boolean = true,
) = Pose(dx, dy, sword, body, armed)

private val MOVES = listOf(
    // 1. Salute: both raise their swords, then drop to guard.
    Move(
        2.0f,
        Track(0f to p(), 0.4f to p(sword = 90f), 1.2f to p(sword = 90f), 1.6f to p()),
        Track(0f to p(), 0.6f to p(sword = 90f), 1.4f to p(sword = 90f), 1.8f to p()),
    ),
    // 2. Clash: both lunge and the blades lock.
    Move(
        1.3f,
        Track(0f to p(), 0.35f to p(dx = 3f, sword = 35f), 0.45f to p(dx = 3f, sword = 10f), 1.0f to p()),
        Track(0f to p(), 0.35f to p(dx = 3f, sword = 35f), 0.45f to p(dx = 3f, sword = 10f), 1.0f to p()),
        clangs = listOf(0.42f),
    ),
    // 3. Thrust and parry.
    Move(
        1.2f,
        Track(0f to p(), 0.15f to p(dx = -1f, sword = 5f), 0.35f to p(dx = 5f, sword = 0f), 0.7f to p(dx = 5f, sword = 0f), 1.0f to p()),
        Track(0f to p(), 0.3f to p(sword = 70f), 0.6f to p(sword = 70f), 0.9f to p()),
        clangs = listOf(0.35f),
    ),
    // 4. Overhead chop, blocked from a crouch.
    Move(
        1.4f,
        Track(0f to p(), 0.4f to p(dx = 1f, sword = 140f), 0.55f to p(dx = 4f, sword = -10f), 0.9f to p(dx = 4f, sword = -10f), 1.2f to p()),
        Track(0f to p(), 0.35f to p(sword = 75f), 0.55f to p(sword = 60f, body = Body.DUCK), 0.9f to p(sword = 60f, body = Body.DUCK), 1.2f to p()),
        clangs = listOf(0.53f),
    ),
    // 5. Jump attack: a leap and a downward slash at a ducking opponent.
    Move(
        1.5f,
        Track(
            0f to p(), 0.25f to p(sword = 60f, body = Body.DUCK), 0.35f to p(sword = 90f),
            0.6f to p(dx = 4f, dy = 12f, sword = 110f), 0.85f to p(dx = 6f, sword = -40f),
            1.1f to p(dx = 6f, sword = -40f), 1.4f to p(),
        ),
        Track(0f to p(), 0.5f to p(sword = 80f, body = Body.DUCK), 0.85f to p(sword = 70f, body = Body.DUCK), 1.1f to p()),
        clangs = listOf(0.82f),
        dust = listOf(0.85f),
    ),
    // 6. Low sweep, jumped over.
    Move(
        1.3f,
        Track(0f to p(), 0.3f to p(sword = -10f, body = Body.DUCK), 0.5f to p(dx = 5f, sword = -40f, body = Body.DUCK), 0.8f to p(dx = 5f, sword = -40f, body = Body.DUCK), 1.1f to p()),
        Track(0f to p(), 0.4f to p(), 0.5f to p(dy = 7f, sword = 60f), 0.7f to p(sword = 40f), 1.1f to p()),
        dust = listOf(0.72f),
    ),
    // 7. Spin slash: a full turn of the blade, caught on a high block.
    Move(
        1.4f,
        Track(0f to p(), 0.2f to p(), 0.65f to p(dx = 5f, sword = GUARD + 360f), 0.9f to p(dx = 5f, sword = GUARD + 360f), 1.2f to p(sword = GUARD + 360f)),
        Track(0f to p(), 0.4f to p(dx = -3f, sword = 70f), 0.9f to p(dx = -3f, sword = 70f), 1.2f to p()),
        clangs = listOf(0.63f),
    ),
    // 8. Flurry: four quick exchanges.
    Move(
        1.5f,
        Track(
            0f to p(), 0.15f to p(dx = 2f, sword = 40f), 0.3f to p(dx = 2f, sword = 5f), 0.45f to p(dx = 3f, sword = 45f),
            0.6f to p(dx = 3f, sword = 0f), 0.75f to p(dx = 2f, sword = 40f), 0.9f to p(dx = 2f, sword = 5f),
            1.05f to p(dx = 3f, sword = 45f), 1.2f to p(dx = 3f, sword = 10f), 1.45f to p(),
        ),
        Track(
            0f to p(), 0.15f to p(dx = 2f, sword = 5f), 0.3f to p(dx = 2f, sword = 40f), 0.45f to p(dx = 3f, sword = 0f),
            0.6f to p(dx = 3f, sword = 45f), 0.75f to p(dx = 2f, sword = 5f), 0.9f to p(dx = 2f, sword = 40f),
            1.05f to p(dx = 3f, sword = 0f), 1.2f to p(dx = 3f, sword = 35f), 1.45f to p(),
        ),
        clangs = listOf(0.22f, 0.52f, 0.82f, 1.12f),
    ),
    // 9. Disarm: the sword spins away and its owner fetches it.
    Move(
        2.4f,
        Track(0f to p(), 0.3f to p(dx = 2f, sword = -30f), 0.45f to p(dx = 4f, sword = 85f), 1.0f to p(dx = 4f, sword = 85f), 1.4f to p(dx = 2f), 2.2f to p()),
        Track(
            0f to p(), 0.4f to p(sword = 10f), 0.45f to p(body = Body.HURT, armed = false),
            0.8f to p(body = Body.STEP, armed = false), 1.2f to p(dx = -9f, body = Body.DUCK, armed = false),
            1.5f to p(dx = -9f, sword = 90f, body = Body.STEP), 2.0f to p(), 2.4f to p(),
        ),
        clangs = listOf(0.45f),
        looseSword = Track(0.45f to p(dx = 4f, dy = 6f, sword = 0f), 0.8f to p(dx = 10f, dy = 26f, sword = 400f), 1.15f to p(dx = 14f, dy = 9f, sword = 630f)),
        looseFrom = 0.45f,
        looseUntil = 1.5f,
    ),
    // 10. Knockback: a heavy blow sends the defender skidding.
    Move(
        1.5f,
        Track(0f to p(), 0.25f to p(dx = -1f, sword = 60f), 0.4f to p(dx = 5f, sword = 10f), 0.7f to p(dx = 5f, sword = 10f), 1.2f to p()),
        Track(
            0f to p(), 0.35f to p(sword = 70f), 0.42f to p(sword = 70f, body = Body.HURT),
            0.75f to p(dx = -7f, sword = 70f), 0.9f to p(dx = -7f, body = Body.STEP), 1.4f to p(),
        ),
        clangs = listOf(0.4f),
        dust = listOf(0.45f, 0.6f),
    ),
    // 11. Dodge roll: the thrust finds nothing but air.
    Move(
        1.6f,
        Track(0f to p(), 0.3f to p(dx = 6f, sword = 0f), 0.7f to p(dx = 6f, sword = 0f), 1.1f to p()),
        Track(
            0f to p(), 0.25f to p(body = Body.DUCK), 0.6f to p(dx = -9f, sword = GUARD + 360f),
            0.8f to p(dx = -9f, sword = GUARD + 360f, body = Body.STEP), 1.4f to p(sword = GUARD + 360f),
        ),
        dust = listOf(0.5f),
    ),
    // 12. Knockdown: one goes down, the other celebrates, then a hand up.
    Move(
        2.6f,
        Track(
            0f to p(), 0.3f to p(dx = 2f, sword = 100f), 0.45f to p(dx = 5f, sword = -20f), 0.8f to p(dx = 5f, sword = -20f),
            1.0f to p(dx = 5f, sword = 90f), 1.2f to p(dx = 5f, dy = 3f, sword = 90f), 1.35f to p(dx = 5f, sword = 90f),
            1.5f to p(dx = 5f, dy = 3f, sword = 90f), 1.65f to p(dx = 5f, sword = 90f), 2.2f to p(dx = 5f), 2.5f to p(),
        ),
        Track(
            0f to p(), 0.45f to p(body = Body.HURT), 0.55f to p(dx = -3f, body = Body.FALLEN, armed = false),
            1.9f to p(dx = -3f, body = Body.DUCK), 2.15f to p(dx = -3f, body = Body.STEP), 2.5f to p(),
        ),
        clangs = listOf(0.43f),
        dust = listOf(0.55f),
        looseSword = Track(0f to p(dx = 28f, dy = 1f, sword = 0f)),
        looseFrom = 0.55f,
        looseUntil = 1.9f,
    ),
    // 13. Feint: both circle back and step in, testing each other.
    Move(
        1.6f,
        Track(0f to p(body = Body.STEP), 0.4f to p(dx = -3f, sword = 30f), 0.8f to p(dx = -3f, sword = 30f, body = Body.STEP), 1.2f to p(dx = 1f, sword = 15f), 1.6f to p()),
        Track(0f to p(), 0.2f to p(body = Body.STEP), 0.6f to p(dx = -2f, sword = 35f), 1.0f to p(dx = -2f, body = Body.STEP), 1.4f to p()),
    ),
)

/** Plays moves back to back with a short breath between, and swaps who attacks at random. */
private class Duel {
    var move: Move = MOVES.random()
        private set
    var attackerIsLeft = true
        private set
    var t = 0f
        private set
    var clock = 0f
        private set
    var breathing = 0.8f
        private set

    fun advance(dt: Float) {
        clock += dt
        if (breathing > 0f) {
            breathing -= dt
            return
        }
        t += dt
        if (t >= move.duration) {
            t = 0f
            breathing = 0.3f + Random.nextFloat() * 0.5f
            move = (MOVES - move).random()
            attackerIsLeft = Random.nextBoolean()
        }
    }

    /** Between moves both stand on guard with the blade swaying a little. */
    private fun idle(phase: Float) = p(sword = GUARD + 6f * sin(clock * 4f + phase))

    fun left(): Pose = when {
        breathing > 0f -> idle(0f)
        attackerIsLeft -> move.attacker.at(t)
        else -> move.defender.at(t)
    }

    fun right(): Pose = when {
        breathing > 0f -> idle(1.7f)
        attackerIsLeft -> move.defender.at(t)
        else -> move.attacker.at(t)
    }
}

// ---- Drawing, on a grid of FIGHT_PIXEL-sized cells ----

private class Stage(
    private val scope: DrawScope,
    private val pixel: Float,
    private val centerX: Int,
    private val ground: Int,
) {
    private class Placed(val pal: Pal, val pose: Pose, val x: Int, val facing: Int) {
        /** The sprite as drawn, bottom row on the ground. */
        fun sprite(clock: Float): List<String> = when (pose.body) {
            Body.DUCK -> pal.crouch
            Body.STEP -> if ((clock / 0.15f).toInt() % 2 == 0) pal.walkA else pal.walkB
            else -> pal.stand
        }
    }

    fun draw(duel: Duel) {
        val left = Placed(DARK, duel.left(), centerX - 19 + duel.left().dx.roundToInt(), facing = 1)
        val right = Placed(GINGER, duel.right(), centerX + 7 - duel.right().dx.roundToInt(), facing = -1)
        val fighters = listOf(left, right)

        fighters.forEach { shadow(it) }

        val move = duel.move
        val fighting = duel.breathing <= 0f
        if (fighting && move.looseSword != null && duel.t in move.looseFrom..move.looseUntil) {
            val defenderSide = if (duel.attackerIsLeft) 1 else -1
            val loose = move.looseSword.at(duel.t)
            sword(centerX + defenderSide * loose.dx.roundToInt(), ground - 1 - loose.dy.roundToInt(), loose.sword, defenderSide)
        }

        val tips = fighters.map { fighter(it, duel.clock) }

        if (fighting) {
            move.clangs.forEach { at ->
                val age = duel.t - at
                if (age in -0.06f..0.14f && tips[0] != null && tips[1] != null) {
                    spark((tips[0]!!.first + tips[1]!!.first) / 2, (tips[0]!!.second + tips[1]!!.second) / 2, age)
                }
            }
            val defender = if (duel.attackerIsLeft) right else left
            move.dust.forEach { at ->
                val age = duel.t - at
                if (age in 0f..0.35f) dust(defender.x, age / 0.35f)
            }
        }
    }

    /** Draws one fighter and their sword; returns the blade tip, if they hold one. */
    private fun fighter(f: Placed, clock: Float): Pair<Int, Int>? {
        val pose = f.pose
        if (pose.body == Body.FALLEN) {
            lying(f)
            return null
        }
        val sprite = f.sprite(clock)
        val top = ground - pose.dy.roundToInt() - sprite.size
        val flash = pose.body == Body.HURT && (clock / 0.08f).toInt() % 2 == 0
        for (row in sprite.indices) for (col in sprite[row].indices) {
            val color = PALETTE[sprite[row][col]] ?: continue
            cell(f.x + col, top + row, if (flash) Color.White else color)
        }
        if (!pose.armed) return null
        val handX = if (f.facing > 0) f.x + SPRITE_W - 1 else f.x
        return sword(handX, top + f.pal.handRow, pose.sword, f.facing)
    }

    /** Flat on the back, head away from the opponent. */
    private fun lying(f: Placed) {
        val sprite = f.pal.stand
        for (row in sprite.indices) for (col in sprite[row].indices) {
            val color = PALETTE[sprite[row][col]] ?: continue
            val x = if (f.facing > 0) f.x - 4 + row else f.x + 15 - row
            cell(x, ground - SPRITE_W + col, color)
        }
    }

    /**
     * A pixel sword from the hand outward: grip, crossguard, blade, bright tip.
     * [facing] mirrors the angle so 0 always points at the opponent. Returns the tip.
     */
    private fun sword(handX: Int, handY: Int, degrees: Float, facing: Int): Pair<Int, Int> {
        val rad = Math.toRadians(degrees.toDouble())
        val vx = cos(rad).toFloat() * facing
        val vy = -sin(rad).toFloat()
        fun at(i: Int) = (handX + (vx * i).roundToInt()) to (handY + (vy * i).roundToInt())
        cell(handX, handY, GRIP)
        val (gx, gy) = at(1)
        cell(gx, gy, GUARD_GOLD)
        // The crossguard runs across the blade.
        cell(gx + (-vy).roundToInt(), gy + vx.roundToInt(), GUARD_GOLD)
        cell(gx - (-vy).roundToInt(), gy - vx.roundToInt(), GUARD_GOLD)
        for (i in 2 until BLADE) {
            val (x, y) = at(i)
            cell(x, y, STEEL)
        }
        val tip = at(BLADE)
        cell(tip.first, tip.second, Color.White)
        return tip
    }

    private fun spark(x: Int, y: Int, age: Float) {
        cell(x, y, Color.White)
        val gold = Color(0xFFFFD23F)
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) cell(x + dx, y + dy, gold)
        if (abs(age) < 0.06f) {
            val flame = Color(0xFFFF8A3A)
            for ((dx, dy) in listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2)) cell(x + dx, y + dy, flame)
        } else {
            for ((dx, dy) in listOf(1 to 1, -1 to -1, 1 to -1, -1 to 1)) cell(x + dx * 2, y + dy * 2, gold)
        }
    }

    private fun dust(x: Int, progress: Float) {
        val color = Color(0xFF8C877E).copy(alpha = 1f - progress)
        val spread = 1 + (progress * 4).toInt()
        for (i in 0..spread step 2) {
            cell(x - 1 - i, ground - 1 - i / 3, color)
            cell(x + SPRITE_W + i, ground - 1 - i / 3, color)
        }
    }

    private fun shadow(f: Placed) {
        val shrink = (f.pose.dy / 4).roundToInt()
        val color = Color.Black.copy(alpha = 0.45f)
        for (x in f.x + 1 + shrink until f.x + SPRITE_W - 1 - shrink) cell(x, ground, color)
    }

    private fun cell(x: Int, y: Int, color: Color) {
        scope.drawRect(color, Offset(x * pixel, y * pixel), Size(pixel, pixel))
    }
}

private val FIGHT_PIXEL = 6.dp
/** The resting sword angle: forward and a little raised. */
private const val GUARD = 20f
/** Sword length in cells, grip to tip. */
private const val BLADE = 10
private val GRIP = Color(0xFF5A3A20)
private val GUARD_GOLD = Color(0xFFC9A040)
private val STEEL = Color(0xFFD8DDE6)
