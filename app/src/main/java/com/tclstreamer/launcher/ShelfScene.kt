package com.tclstreamer.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** What the two friends get up to in the top shelf while an app is focused. */
enum class Activity(val asset: String) {
    COUCH("couch"),
    EATING("eating"),
    READING("reading"),
    PROGRAMMING("programming"),
    CAT("cat"),
    HIKING("hiking"),
    RUNNING("running"),
    SWIMMING("swimming"),
    CLIMBING("climbing"),
}

/**
 * The pixel-art activities, drawn by tools/scenes and baked into assets/scenes: each is a 6×4
 * grid of 24 frames of 240×75 pixels, with the sky (or the wall, indoors) left see-through so it
 * can be filled with the focused app's own colour.
 */
object ShelfArt {
    const val W = 240
    const val H = 75
    const val FRAMES = 24
    private const val COLUMNS = 6
    private const val BAND_ROWS = 8
    private val BAND_VALUES = floatArrayOf(0.10f, 0.13f, 0.17f, 0.21f, 0.26f, 0.31f, 0.37f, 0.43f)

    // A decoded sheet is 1.7 MB, so only the few most recently shown are kept.
    private val cache = object : LinkedHashMap<Activity, ImageBitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Activity, ImageBitmap>) = size > 3
    }

    @Synchronized
    fun sheet(context: Context, activity: Activity): ImageBitmap = cache.getOrPut(activity) {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.assets.open("scenes/${activity.asset}.png").use {
            BitmapFactory.decodeStream(it, null, options)!!.asImageBitmap()
        }
    }

    fun frameOffset(frame: Int) = IntOffset((frame % COLUMNS) * W, (frame / COLUMNS) * H)

    /** Eight bands of the app's hue, darkest at the top. Grey apps get a nearly grey sky. */
    fun sky(hue: Float?): List<Color> = BAND_VALUES.map { v ->
        Color.hsv(hue ?: 0f, if (hue == null) 0.12f else 0.6f, v)
    }

    fun bandTop(band: Int) = band * BAND_ROWS
    fun bandHeight(band: Int) = if (band == BAND_VALUES.lastIndex) H - bandTop(band) else BAND_ROWS
}

/** One activity playing on a loop at 12 frames a second, in front of a sky of the app's colour. */
@Composable
fun ShelfScene(activity: Activity, hue: Float?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sheet = remember(activity) { ShelfArt.sheet(context, activity) }
    val sky = remember(hue) { ShelfArt.sky(hue) }
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(FRAME_MS)
            withFrameNanos { } // waits while nothing is on screen
            frame = (frame + 1) % ShelfArt.FRAMES
        }
    }

    Canvas(modifier) {
        val cell = size.height / ShelfArt.H
        sky.forEachIndexed { band, color ->
            drawRect(
                color,
                Offset(0f, ShelfArt.bandTop(band) * cell),
                Size(size.width, ShelfArt.bandHeight(band) * cell),
            )
        }
        drawImage(
            sheet,
            srcOffset = ShelfArt.frameOffset(frame),
            srcSize = IntSize(ShelfArt.W, ShelfArt.H),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }
}
