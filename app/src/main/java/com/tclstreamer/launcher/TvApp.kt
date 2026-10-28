package com.tclstreamer.launcher

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap

/** One launchable app, with artwork already scaled down to the size we draw it at. */
class TvApp(
    val packageName: String,
    val label: String,
    /** 16:9 Android TV banner, if the app ships one. */
    val banner: ImageBitmap?,
    /** Square icon, used when there is no banner. */
    val icon: ImageBitmap?,
    val launchIntent: Intent,
)

/** What the home screen shows: one hero app, a streaming row, and everything else. */
data class HomeContent(
    val hero: TvApp?,
    val streaming: List<TvApp>,
    val others: List<TvApp>,
)
