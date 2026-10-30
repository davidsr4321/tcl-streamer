package com.tclstreamer.launcher

import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/** One launchable app, with artwork already scaled down to the size we draw it at. */
class TvApp(
    val packageName: String,
    val label: String,
    /** 16:9 Android TV banner, if the app ships one. */
    val banner: ImageBitmap?,
    /** Square icon, used when there is no banner. */
    val icon: ImageBitmap?,
    /** A dark wash of the artwork's main colour, painted behind the top shelf. */
    val tint: Color,
    /** The app's main hue, which colours the sky of its top-shelf scene. Null for grey artwork. */
    val hue: Float?,
    /** What the two friends do in the top shelf while this app is focused. */
    val activity: Activity,
    val launchIntent: Intent,
)
