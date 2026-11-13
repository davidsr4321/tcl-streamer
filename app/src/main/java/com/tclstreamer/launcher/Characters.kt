package com.tclstreamer.launcher

import androidx.compose.ui.graphics.Color

// The two friends, David and Dor, as pixel sprites, shared by the sword-fight screensaver. One letter is one
// pixel, coloured through PALETTE; '.' is see-through.

/** One character: a standing pose, plus blink and two walking frames built from it. */
internal class Pal(val name: String, val stand: List<String>) {
    private val pants = stand[stand.size - 3][2]
    val blink = stand.mapIndexed { i, line -> if (i == EYE_ROW) line.replace('E', 's') else line }
    val walkA = stand.dropLast(2) + "..$pants$pants$pants...$pants$pants.." + ".KKKK...KK.."
    val walkB = stand.dropLast(2) + "..$pants$pants...$pants$pants$pants.." + "..KK...KKKK."
    /** Knees bent: the legs fold away and the body sits on the shoes. */
    val crouch = stand.dropLast(3) + stand.last()
    /** The row with the hands, where a sword is held. */
    val handRow = stand.indices.first { it >= 10 && stand[it][0] == 'S' }
}

internal const val FRAME_MS = 83L
internal const val SPRITE_W = 12
/** Height of the tallest character. */
internal const val SPRITE_H = 20
private const val EYE_ROW = 4

internal val PALETTE = mapOf(
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

/** David: dark hair, full beard, lavender tee. */
internal val DAVID = Pal(
    "DAVID",
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

/** Dor: ginger curls, freckles, ginger beard, green tee. A little shorter. */
internal val DOR = Pal(
    "DOR",
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

/** A 5-cell-tall font for the name tags under the characters; '#' is lit. */
internal val NAME_FONT = mapOf(
    'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"),
    'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
    'D' to listOf("##.", "#.#", "#.#", "#.#", "##."),
    'F' to listOf("###", "#..", "##.", "#..", "#.."),
    'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"),
    'I' to listOf("#", "#", "#", "#", "#"),
    'K' to listOf("#.#", "#.#", "##.", "#.#", "#.#"),
    'M' to listOf("#...#", "##.##", "#.#.#", "#...#", "#...#"),
    'N' to listOf("#..#", "##.#", "#.##", "#..#", "#..#"),
    'O' to listOf(".#.", "#.#", "#.#", "#.#", ".#."),
    'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"),
    'S' to listOf(".##", "#..", ".#.", "..#", "##."),
    'T' to listOf("###", ".#.", ".#.", ".#.", ".#."),
    'V' to listOf("#.#", "#.#", "#.#", "#.#", ".#."),
)

/** Width of [text] in font cells, one blank column between letters. */
internal fun nameWidth(text: String) = text.sumOf { (NAME_FONT[it] ?: NAME_FONT.getValue('I'))[0].length + 1 } - 1
