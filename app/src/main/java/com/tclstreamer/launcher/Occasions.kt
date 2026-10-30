package com.tclstreamer.launcher

import android.icu.util.Calendar
import android.icu.util.HebrewCalendar
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A day worth decorating the top shelf for. */
sealed interface Occasion {
    data class Birthday(val name: String) : Occasion
    data object Halloween : Occasion
    data class Hanukkah(val night: Int) : Occasion
    data object Christmas : Occasion
    data object NewYear : Occasion
}

fun occasionOn(date: LocalDate, config: DnDConfig): Occasion? {
    config.birthdays.firstOrNull { it.day == MonthDay.from(date) }?.let { return Occasion.Birthday(it.name) }
    val holidays = config.holidays
    val day = date.dayOfMonth
    if ("newyear" in holidays && (date.month == Month.DECEMBER && day == 31 || date.month == Month.JANUARY && day == 1)) {
        return Occasion.NewYear
    }
    if ("halloween" in holidays && date.month == Month.OCTOBER && day >= 24) return Occasion.Halloween
    if ("hanukkah" in holidays) hanukkahNight(date)?.let { return Occasion.Hanukkah(it) }
    if ("christmas" in holidays && date.month == Month.DECEMBER && day in 20..26) return Occasion.Christmas
    return null
}

/** Which of the eight days of Hanukkah this is (from 25 Kislev), or null. */
private fun hanukkahNight(date: LocalDate): Int? {
    val zone = ZoneId.systemDefault()
    val today = HebrewCalendar().apply { timeInMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() }
    // The Hebrew year starts in the autumn, so this year's Kislev is the one coming up or just past.
    val start = HebrewCalendar(today.get(Calendar.YEAR), HebrewCalendar.KISLEV, 25)
    val first = Instant.ofEpochMilli(start.timeInMillis).atZone(zone).toLocalDate()
    val night = ChronoUnit.DAYS.between(first, date).toInt() + 1
    return night.takeIf { it in 1..8 }
}

// ---- Drawing, in scene pixels: 240×75 cells of `cell` px each. ----

private class Cells(val scope: DrawScope, val cell: Float) {
    fun px(x: Int, y: Int, color: Color) = scope.drawRect(color, Offset(x * cell, y * cell), Size(cell, cell))
    fun rect(x: Int, y: Int, w: Int, h: Int, color: Color) =
        scope.drawRect(color, Offset(x * cell, y * cell), Size(w * cell, h * cell))
    fun sprite(rows: List<String>, x: Int, y: Int, colors: Map<Char, Color>) {
        rows.forEachIndexed { r, line -> line.forEachIndexed { c, ch -> colors[ch]?.let { px(x + c, y + r, it) } } }
    }
}

/** Stars twinkling in the sky. Drawn behind the scene, so they only show where the sky does. */
fun DrawScope.drawNightSky(cell: Float, tick: Int) {
    val g = Cells(this, cell)
    for (i in 0 until 46) {
        val x = (i * 97 + i * i * 13) % ShelfArt.W
        val y = (i * 31 + i * i * 7) % 34
        val bright = (tick / 4 + i) % 9 != 0
        g.px(x, y, Color.White.copy(alpha = if (bright) 0.75f else 0.25f))
    }
}

/** Rain, snow, fog and clouds over an outdoor scene, and the dark of night over any scene. */
fun DrawScope.drawAmbience(ambience: Ambience, indoor: Boolean, cell: Float, tick: Int) {
    val g = Cells(this, cell)
    val w = ShelfArt.W
    val h = ShelfArt.H
    if (!indoor) {
        when (ambience.weather) {
            Weather.CLOUDY -> g.rect(0, 0, w, 40, Color(0x2E9AA0AE))
            Weather.FOG -> g.rect(0, 0, w, h, Color(0x47C8CCD4))
            Weather.RAIN, Weather.STORM -> {
                g.rect(0, 0, w, h, Color(0x2A101828))
                val drop = Color(0x99A8C8F0)
                for (i in 0 until 70) {
                    val y = (i * 37 + tick * 4) % (h + 6) - 3
                    val x = ((i * 53 + i * i * 7) - y / 3).mod(w)
                    g.px(x, y, drop)
                    g.px(x, y + 1, drop)
                    g.px(x - 1, y + 2, drop)
                }
                if (ambience.weather == Weather.STORM && tick % 70 < 2) g.rect(0, 0, w, h, Color(0x40FFFFFF))
            }
            Weather.SNOW -> drawSnow(g, tick, 50)
            Weather.CLEAR -> Unit
        }
    }
    when (ambience.daylight) {
        Daylight.NIGHT -> g.rect(0, 0, w, h, Color(10, 10, 40).copy(alpha = if (indoor) 0.16f else 0.32f))
        Daylight.DUSK -> g.rect(0, 0, w, h, Color(60, 20, 10).copy(alpha = 0.12f))
        Daylight.DAY -> Unit
    }
}

private fun drawSnow(g: Cells, tick: Int, count: Int) {
    for (i in 0 until count) {
        val y = (i * 29 + tick) % (ShelfArt.H + 4) - 2
        val sway = intArrayOf(0, 1, 1, 0)[(tick / 6 + i) % 4]
        g.px((i * 47 + i * i * 3 + sway).mod(ShelfArt.W), y, Color(0xDDFFFFFF))
    }
}

/** Banners, candles, bats and fireworks for the day. */
fun DrawScope.drawOccasion(occasion: Occasion, cell: Float, tick: Int) {
    val g = Cells(this, cell)
    when (occasion) {
        is Occasion.Birthday -> {
            confetti(g, tick)
            banner(g, "HAPPY BIRTHDAY ${occasion.name.uppercase()}", tick)
            for ((x, color) in listOf(144 to Color(0xFFE85A6A), 150 to Color(0xFF5AB0E8), 228 to Color(0xFFF2D23A))) {
                val y = 14 + if ((tick / 12 + x) % 2 == 0) 0 else 1
                g.sprite(listOf(".##.", "####", "####", ".##.", "..#."), x, y, mapOf('#' to color))
                g.rect(x + 2, y + 5, 1, 8, Color(0x99FFFFFF))
            }
        }
        Occasion.Halloween -> {
            for (i in 0 until 4) {
                val x = ShelfArt.W - ((tick + i * 70) % (ShelfArt.W + 20)) + 10
                val y = 6 + i * 5 + if ((tick / 3 + i) % 4 < 2) 0 else 1
                val wings = if ((tick + i) % 4 < 2) listOf("#.#.#", ".###.") else listOf(".###.", "#.#.#")
                g.sprite(wings, x, y, mapOf('#' to Color(0xFF141018)))
            }
            for (x in intArrayOf(142, 230)) {
                val eye = if ((tick / 5 + x) % 3 == 0) Color(0xFFFFE07A) else Color(0xFFFFB02E)
                g.sprite(listOf("..g..", ".ooo.", "oyoyo", ".ooo."), x, 54, mapOf('g' to Color(0xFF3D6A32), 'o' to Color(0xFFE07A1A), 'y' to eye))
            }
        }
        is Occasion.Hanukkah -> menorah(g, occasion.night, tick)
        Occasion.Christmas -> {
            drawSnow(g, tick, 40)
            val colors = listOf(Color(0xFFE04040), Color(0xFF40C060), Color(0xFFF2D23A), Color(0xFF4A90E8))
            for (x in 2 until ShelfArt.W step 6) {
                val sag = if (x % 12 == 2) 0 else 1
                g.px(x, 1 + sag, Color(0xFF2A3A2A))
                val on = (x / 6 + tick / 6) % 2 == 0
                g.px(x, 2 + sag, colors[(x / 6) % colors.size].copy(alpha = if (on) 1f else 0.35f))
            }
        }
        Occasion.NewYear -> {
            banner(g, "HAPPY NEW YEAR", tick)
            fireworks(g, tick)
        }
    }
}

private val PARTY = listOf(Color(0xFFE85A6A), Color(0xFFF2D23A), Color(0xFF5AD0E8), Color(0xFFFFFFFF), Color(0xFF9A7AE8))

private fun confetti(g: Cells, tick: Int) {
    for (i in 0 until 40) {
        val y = (i * 23 + tick) % (ShelfArt.H + 4) - 2
        val x = (i * 61 + i * i * 5 + (tick / 4 + i) % 3).mod(ShelfArt.W)
        g.px(x, y, PARTY[i % PARTY.size])
    }
}

private fun banner(g: Cells, text: String, tick: Int) {
    val glyphs = text.mapNotNull { FONT[it] }
    val width = glyphs.size * 4 - 1
    var x = ShelfArt.W / 2 - width / 2
    glyphs.forEachIndexed { i, glyph ->
        val color = PARTY[(i + tick / 4) % PARTY.size]
        glyph.forEachIndexed { r, line -> line.forEachIndexed { c, ch -> if (ch == '#') g.px(x + c, 4 + r, color) } }
        x += 4
    }
}

private fun menorah(g: Cells, night: Int, tick: Int) {
    val gold = Color(0xFFC9A040)
    val candle = Color(0xFFDCE6F5)
    val left = 110
    g.rect(left, 13, 19, 1, gold)
    g.rect(left + 9, 14, 1, 4, gold)
    g.rect(left + 6, 18, 7, 1, gold)
    for (k in 0 until 9) {
        // Four candles each side, with the raised helper candle in the middle.
        val x = when {
            k < 4 -> left + k * 2
            k == 4 -> left + 9
            else -> left + k * 2 + 2
        }
        val shamash = k == 4
        val top = if (shamash) 7 else 9
        g.rect(x, top, 1, 13 - top, candle)
        // Candles are added from the right, one more each night; the helper candle is always lit.
        val lit = shamash || (k > 4 && 8 - k < night) || (k < 4 && 4 + (4 - k) <= night)
        if (lit) {
            val flicker = (tick / 3 + k) % 3 == 0
            g.px(x, top - 1, if (flicker) Color(0xFFFFE07A) else Color(0xFFFFB02E))
            if (!flicker) g.px(x, top - 2, Color(0x88FFE07A))
        }
    }
}

private fun fireworks(g: Cells, tick: Int) {
    val bursts = listOf(Triple(150, 18, 0), Triple(205, 12, 12), Triple(176, 24, 24))
    for ((index, burst) in bursts.withIndex()) {
        val (cx, cy, start) = burst
        val age = (tick + 36 - start) % 36
        if (age >= 14) continue
        val r = minOf(age, 7)
        val color = PARTY[index % PARTY.size].copy(alpha = if (age < 8) 1f else (14 - age) / 6f)
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) g.px(cx + dx * r, cy + dy * r, color)
        val d = r * 7 / 10
        for ((dx, dy) in listOf(1 to 1, -1 to 1, 1 to -1, -1 to -1)) g.px(cx + dx * d, cy + dy * d, color)
    }
}

/** A 3×5 pixel font for banners. Letters the font lacks are skipped. */
private val FONT: Map<Char, List<String>> = mapOf(
    'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"),
    'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
    'C' to listOf(".##", "#..", "#..", "#..", ".##"),
    'D' to listOf("##.", "#.#", "#.#", "#.#", "##."),
    'E' to listOf("###", "#..", "##.", "#..", "###"),
    'F' to listOf("###", "#..", "##.", "#..", "#.."),
    'G' to listOf(".##", "#..", "#.#", "#.#", ".##"),
    'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"),
    'I' to listOf("###", ".#.", ".#.", ".#.", "###"),
    'J' to listOf("..#", "..#", "..#", "#.#", ".#."),
    'K' to listOf("#.#", "#.#", "##.", "#.#", "#.#"),
    'L' to listOf("#..", "#..", "#..", "#..", "###"),
    'M' to listOf("#.#", "###", "###", "#.#", "#.#"),
    'N' to listOf("##.", "#.#", "#.#", "#.#", "#.#"),
    'O' to listOf(".#.", "#.#", "#.#", "#.#", ".#."),
    'P' to listOf("##.", "#.#", "##.", "#..", "#.."),
    'Q' to listOf(".#.", "#.#", "#.#", "##.", ".##"),
    'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"),
    'S' to listOf(".##", "#..", ".#.", "..#", "##."),
    'T' to listOf("###", ".#.", ".#.", ".#.", ".#."),
    'U' to listOf("#.#", "#.#", "#.#", "#.#", "###"),
    'V' to listOf("#.#", "#.#", "#.#", "#.#", ".#."),
    'W' to listOf("#.#", "#.#", "###", "###", "#.#"),
    'X' to listOf("#.#", "#.#", ".#.", "#.#", "#.#"),
    'Y' to listOf("#.#", "#.#", ".#.", ".#.", ".#."),
    'Z' to listOf("###", "..#", ".#.", "#..", "###"),
    ' ' to listOf("...", "...", "...", "...", "..."),
    '!' to listOf(".#.", ".#.", ".#.", "...", ".#."),
)
