package com.tclstreamer.launcher

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** A tile in the quick settings row under the apps. */
class QuickTile(
    val key: String,
    val label: String,
    /** A second line under the label, such as the time left on the sleep timer. */
    val detail: String?,
    val icon: List<String>,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/** The TV's HDMI inputs as (input id, name), leaving out the devices found behind them over CEC. */
fun hdmiInputs(context: Context): List<Pair<String, String>> {
    val tv = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
    return runCatching {
        tv.tvInputList
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !it.isHidden(context) }
            .map { it.id to (it.loadCustomLabel(context) ?: it.loadLabel(context)).toString() }
    }.getOrDefault(emptyList())
}

/** 11×9 pixel icons for the quick tiles. */
object QuickIcons {
    val WIFI = listOf(
        "..#######..",
        ".#.......#.",
        "#..#####..#",
        "..#.....#..",
        ".#..###..#.",
        "...#...#...",
        ".....#.....",
        "....###....",
        ".....#.....",
    )
    val HDMI = listOf(
        "...........",
        "###########",
        "#.........#",
        "#.#.#.#.#.#",
        ".#.......#.",
        "..#######..",
        "....#.#....",
        "....#.#....",
        "....###....",
    )
    val MOON = listOf(
        "....###....",
        "..##.......",
        ".#.........",
        "#........#.",
        "#.........#",
        "#.........#",
        ".#.......#.",
        "..##...##..",
        "....###....",
    )
    val UPDATE = listOf(
        "....###....",
        "....###....",
        "....###....",
        ".#########.",
        "..#######..",
        "...#####...",
        "....###....",
        ".....#.....",
        "###########",
    )
    val GEAR = listOf(
        "....#.#....",
        ".#.#####.#.",
        "..#######..",
        ".###...###.",
        "####...####",
        ".###...###.",
        "..#######..",
        ".#.#####.#.",
        "....#.#....",
    )
}
