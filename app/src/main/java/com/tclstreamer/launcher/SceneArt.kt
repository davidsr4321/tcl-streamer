package com.tclstreamer.launcher

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** A pixel-art backdrop for the top shelf, picked from the focused app's main colour. */
enum class Scene { VOLCANO, DESERT, FOREST, OCEAN, NIGHT, CITY }

/** Red apps get the volcano, orange the desert, and so on round the colour wheel. Grey gets the city. */
fun sceneForHue(hue: Float?): Scene = when {
    hue == null -> Scene.CITY
    hue < 18f || hue >= 330f -> Scene.VOLCANO
    hue < 65f -> Scene.DESERT
    hue < 165f -> Scene.FOREST
    hue < 245f -> Scene.OCEAN
    else -> Scene.NIGHT
}

/**
 * Paints each scene once into a tiny 240×75 bitmap. Drawn without filtering at 4 dp per pixel it
 * fills the 960×300 dp shelf, and all six together take under half a megabyte.
 */
object SceneArt {
    const val W = 240
    const val H = 75
    /** Row the characters stand on. */
    const val GROUND = 57

    private val cache = EnumMap<Scene, ImageBitmap>(Scene::class.java)

    @Synchronized
    fun image(scene: Scene): ImageBitmap = cache.getOrPut(scene) {
        val painter = Painter(Random(scene.ordinal * 7919 + 13))
        when (scene) {
            Scene.VOLCANO -> painter.volcano()
            Scene.DESERT -> painter.desert()
            Scene.FOREST -> painter.forest()
            Scene.OCEAN -> painter.ocean()
            Scene.NIGHT -> painter.night()
            Scene.CITY -> painter.city()
        }
        Bitmap.createBitmap(painter.pixels, W, H, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

private class Painter(private val rnd: Random) {
    val pixels = IntArray(SceneArt.W * SceneArt.H)

    private val w = SceneArt.W
    private val h = SceneArt.H
    private val ground = SceneArt.GROUND

    fun volcano() {
        sky(0xFF1A0608, 0xFF260A0B, 0xFF360E0E, 0xFF4A1410, 0xFF621C12, 0xFF7A2614)
        stars(30, 50, 0xFFFF8A3A, 0xFFFFC266, 0xFFD8451C) // embers
        ridge(0xFF2E0E0D, 44f, 3f, 0.05f, 1f)
        val cx = 172
        for (x in cx - 60..cx + 60) {
            val d = abs(x - cx)
            val top = if (d < 6) 12 else (10 + d * 0.78f).roundToInt()
            rect(x, top, x + 1, ground, if (x > cx) 0xFF2A0C0B else 0xFF3A1210)
        }
        rect(cx - 5, 11, cx + 6, 14, 0xFFFF5A1F)
        rect(cx - 3, 10, cx + 4, 11, 0xFFFFB347)
        // Lava runs down both flanks.
        for (dir in intArrayOf(-1, 1)) {
            var x = cx + dir * 3
            for (y in 14 until ground) {
                if (rnd.nextFloat() < 0.35f) x += dir
                set(x, y, 0xFFFF7A2A)
                set(x + dir, y, 0xFFC2361A)
            }
        }
        repeat(18) {
            set(cx - 4 + rnd.nextInt(9), 2 + rnd.nextInt(8), if (rnd.nextBoolean()) 0xFF4A2A2A else 0xFF3A2222)
        }
        soil(0xFF22100E, 0xFFD8451C, 70)
    }

    fun desert() {
        sky(0xFF2B1A3A, 0xFF4A2240, 0xFF7A3440, 0xFFB0503A, 0xFFD8743A, 0xFFF0A24A)
        disc(70, 36, 13, 0xFFFFD27A)
        rect(56, 34, 85, 35, 0xFFF0A24A)
        rect(56, 40, 85, 41, 0xFFF0A24A)
        ridge(0xFFC0703A, 42f, 3f, 0.04f, 0f)
        ridge(0xFF9A5228, 49f, 2f, 0.07f, 1.5f)
        val cactus = 0xFF3F6A3A
        for (x in intArrayOf(150, 196, 222)) {
            val top = ground - (10 + x % 5)
            rect(x, top, x + 2, ground, cactus)
            rect(x - 3, top + 4, x - 2, top + 8, cactus)
            rect(x - 2, top + 7, x, top + 8, cactus)
            rect(x + 3, top + 2, x + 4, top + 6, cactus)
            rect(x + 2, top + 5, x + 3, top + 6, cactus)
        }
        soil(0xFF7A4020, 0xFFA05A2C, 60)
    }

    fun forest() {
        sky(0xFF0C2226, 0xFF10302E, 0xFF164036, 0xFF20523E, 0xFF2E6648, 0xFF3F7A52)
        disc(204, 14, 6, 0xFFDFF2C4)
        ridge(0xFF1D4632, 38f, 4f, 0.035f, 2f)
        trees(26, 12, 20, 0xFF16392A)
        trees(14, 18, 28, 0xFF0F2C20)
        soil(0xFF173620, 0xFF2F6A3A, 90)
    }

    fun ocean() {
        sky(0xFF050F24, 0xFF081834, 0xFF0B2246, 0xFF102E5A, 0xFF173C6E)
        stars(60, 34, 0xFFFFFFFF, 0xFFB8D0FF, 0xFF7FA6E6)
        disc(196, 14, 7, 0xFFE8F0FF)
        set(194, 12, 0xFFC8D6F0)
        set(198, 16, 0xFFC8D6F0)
        set(197, 11, 0xFFC8D6F0)
        rect(0, 38, w, ground, 0xFF0C2A52)
        repeat(70) {
            val x = rnd.nextInt(w)
            val y = 39 + rnd.nextInt(17)
            rect(x, y, x + 2 + rnd.nextInt(4), y + 1, 0xFF2F63A0)
        }
        // The moon's reflection on the water.
        for (y in 39 until ground step 2) {
            val half = 1 + rnd.nextInt(3)
            rect(196 - half, y, 196 + half, y + 1, 0xFF9CC4FF)
        }
        soil(0xFFC8B07A, 0xFFA8925E, 80)
    }

    fun night() {
        sky(0xFF0E0820, 0xFF160C30, 0xFF201242, 0xFF2C1856, 0xFF3C206A, 0xFF4C2A7C)
        stars(80, 40, 0xFFFFFFFF, 0xFFE0C8FF, 0xFFA88CE0)
        disc(200, 13, 7, 0xFFF4ECFF)
        disc(203, 11, 6, 0xFF160C30) // bite out of the moon for a crescent
        // A soft aurora ribbon.
        for (x in 0 until w) {
            val y = (18 + 5 * sin(x * 0.045f) + 2 * sin(x * 0.13f)).roundToInt()
            set(x, y, 0xFF6A4FB0)
            set(x, y + 1, 0xFF8A6FD0)
            if (x % 3 != 0) set(x, y + 2, 0xFF5A3F98)
        }
        ridge(0xFF25183F, 36f, 7f, 0.03f, 0.5f)
        ridge(0xFF1A1030, 46f, 4f, 0.06f, 2.2f)
        soil(0xFF140C26, 0xFF2C1C4A, 50)
    }

    fun city() {
        sky(0xFF12141C, 0xFF181B26, 0xFF1F2332, 0xFF282D40, 0xFF343A50)
        stars(25, 20, 0xFFC8CCD8)
        var x = 0
        while (x < w) {
            val bw = 8 + rnd.nextInt(10)
            val bh = 12 + rnd.nextInt(26)
            rect(x, ground - bh, x + bw, ground, if (rnd.nextBoolean()) 0xFF191B25 else 0xFF20232F)
            for (wy in ground - bh + 2 until ground - 2 step 3) {
                for (wx in x + 2 until x + bw - 1 step 2) {
                    if (rnd.nextFloat() < 0.3f) set(wx, wy, if (rnd.nextFloat() < 0.8f) 0xFFF2D48A else 0xFF8AB4F2)
                }
            }
            x += bw + if (rnd.nextFloat() < 0.3f) 1 else 0
        }
        soil(0xFF13141B, 0xFF2A2C36, 40)
    }

    // ---- Drawing primitives ----

    private fun set(x: Int, y: Int, color: Long) {
        if (x in 0 until w && y in 0 until h) pixels[y * w + x] = color.toInt()
    }

    private fun rect(x0: Int, y0: Int, x1: Int, y1: Int, color: Long) {
        for (y in y0 until y1) for (x in x0 until x1) set(x, y, color)
    }

    /** Flat colour bands from the top of the sky down to the horizon. */
    private fun sky(vararg bands: Long) {
        for (y in 0 until ground) rect(0, y, w, y + 1, bands[y * bands.size / ground])
    }

    private fun stars(count: Int, maxY: Int, vararg colors: Long) {
        for (i in 0 until count) set(rnd.nextInt(w), rnd.nextInt(maxY), colors[i % colors.size])
    }

    private fun disc(cx: Int, cy: Int, r: Int, color: Long) {
        for (y in -r..r) for (x in -r..r) if (x * x + y * y <= r * r + r) set(cx + x, cy + y, color)
    }

    /** A rolling silhouette filled down to the bottom edge. */
    private fun ridge(color: Long, base: Float, amp: Float, freq: Float, phase: Float) {
        for (x in 0 until w) {
            val top = (base + amp * sin(x * freq + phase) + amp * 0.5f * sin(x * freq * 2.3f + phase * 1.7f)).roundToInt()
            rect(x, top, x + 1, h, color)
        }
    }

    private fun trees(count: Int, minH: Int, maxH: Int, color: Long) {
        repeat(count) {
            val x = rnd.nextInt(w)
            val th = minH + rnd.nextInt(maxH - minH + 1)
            for (y in 0 until th) {
                val half = (y / 2.2f).toInt()
                rect(x - half, ground - th + y, x + half + 1, ground - th + y + 1, color)
            }
            rect(x, ground - 2, x + 1, ground, 0xFF3A2A1A)
        }
    }

    private fun soil(color: Long, fleck: Long, flecks: Int) {
        rect(0, ground, w, h, color)
        repeat(flecks) { set(rnd.nextInt(w), ground + 1 + rnd.nextInt(h - ground - 1), fleck) }
    }
}
