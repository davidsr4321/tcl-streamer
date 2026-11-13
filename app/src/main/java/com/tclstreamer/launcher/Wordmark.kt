package com.tclstreamer.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** "D&D" in chunky pixel letters, ivory with a gold drop shadow, matching the pixel characters. */
@Composable
fun PixelWordmark(modifier: Modifier = Modifier, pixel: Dp = 5.dp) {
    val columns = GLYPHS.sumOf { it[0].length + 1 } - 1
    Canvas(modifier.size(pixel * (columns + 1), pixel * (GLYPH_H + 1))) {
        val cell = pixel.toPx()
        for ((color, shift) in listOf(SHADOW to 1, INK to 0)) {
            var left = 0
            for (glyph in GLYPHS) {
                for (row in glyph.indices) for (col in glyph[row].indices) {
                    if (glyph[row][col] != '#') continue
                    drawRect(color, Offset((left + col + shift) * cell, (row + shift) * cell), Size(cell, cell))
                }
                left += glyph[0].length + 1
            }
        }
    }
}

private const val GLYPH_H = 7
private val SHADOW = Color(0xFFC9A040)
private val INK = Color(0xFFF2EDE4)

private val LETTER_D = listOf(
    "####.",
    "#...#",
    "#...#",
    "#...#",
    "#...#",
    "#...#",
    "####.",
)

private val AMPERSAND = listOf(
    ".##...",
    "#..#..",
    "#..#..",
    ".##...",
    "#..#.#",
    "#...#.",
    ".###.#",
)

private val GLYPHS = listOf(LETTER_D, AMPERSAND, LETTER_D)
