package com.tclstreamer.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap

/**
 * Finds the installed apps a TV launcher can open and orders them for the home screen:
 * streaming apps first (they fill the top row), then everything else by name.
 *
 * Artwork is decoded once per package and kept small, because the TV only has 1 GB of RAM.
 * Call [invalidate] when apps are installed, updated or removed.
 */
class AppRepository(private val context: Context) {

    private val cache = HashMap<String, TvApp>()

    @Synchronized
    fun invalidate() = cache.clear()

    @Synchronized
    fun load(): List<TvApp> {
        val pm = context.packageManager
        val apps = LinkedHashMap<String, TvApp>()

        // TV apps first, so their banner activity wins over a phone-style launcher activity.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName || pkg in apps || pkg in HIDDEN) continue
                apps[pkg] = cache.getOrPut(pkg) { toTvApp(info, category) }
            }
        }
        cache.keys.retainAll(apps.keys)

        val featured = FEATURED.mapNotNull { apps[it] }
        val rest = apps.values
            .filter { it !in featured }
            .sortedBy { it.label.lowercase() }
        return featured + rest
    }

    private fun toTvApp(info: ResolveInfo, category: String): TvApp {
        val pm = context.packageManager
        val activity = info.activityInfo
        val banner = activity.loadBanner(pm)?.toBitmap(BANNER_W, BANNER_H)
        val icon = if (banner == null) activity.loadIcon(pm)?.toBitmap(ICON_PX, ICON_PX) else null
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setClassName(activity.packageName, activity.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        return TvApp(
            packageName = activity.packageName,
            label = info.loadLabel(pm).toString(),
            banner = banner?.asImageBitmap(),
            icon = icon?.asImageBitmap(),
            tint = tintOf(banner ?: icon),
            hue = HUES[activity.packageName] ?: accentHue(banner ?: icon),
            activity = ACTIVITIES[activity.packageName]
                ?: Activity.entries[Math.floorMod(activity.packageName.hashCode(), Activity.entries.size)],
            launchIntent = intent,
        )
    }

    /** Average colour of the artwork, darkened so white text always reads on top of it. */
    private fun tintOf(bitmap: Bitmap?): Color {
        if (bitmap == null) return DEFAULT_TINT
        val average = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(average, hsl)
        hsl[2] = hsl[2].coerceAtMost(0.22f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    /**
     * The hue of the artwork's most vivid colour, or null for grey artwork. Each pixel votes for
     * its hue in proportion to how saturated and bright it is, so a red logo on a white banner
     * still reads as red.
     */
    private fun accentHue(bitmap: Bitmap?): Float? {
        if (bitmap == null) return null
        val small = Bitmap.createScaledBitmap(bitmap, 32, 18, true)
        val votes = FloatArray(HUE_BINS)
        val hsv = FloatArray(3)
        var total = 0f
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val pixel = small.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 128) continue
            android.graphics.Color.colorToHSV(pixel, hsv)
            val weight = hsv[1] * hsv[2]
            if (weight < 0.12f) continue
            votes[(hsv[0] / 360f * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)] += weight
            total += weight
        }
        if (total < 6f) return null
        val best = votes.indices.maxBy { votes[it] }
        return (best + 0.5f) * 360f / HUE_BINS
    }

    companion object {
        private const val BANNER_W = 320
        private const val BANNER_H = 180
        private const val ICON_PX = 96
        private const val HUE_BINS = 24
        private val DEFAULT_TINT = Color(0xFF1C1C22)

        /** Streaming apps, in the order they earn a spot in the top row. */
        val FEATURED = listOf(
            "com.stremio.one",                       // Stremio
            "com.netflix.ninja",                     // Netflix
            "com.google.android.youtube.tv",         // YouTube
            "com.liskovsoft.smarttubetv.beta",       // SmartTube
            "org.smarttube.stable",                  // SmartTube
            "com.amazon.amazonvideo.livingroom",     // Prime Video
            "com.disney.disneyplus",                 // Disney+
            "com.wbd.stream",                        // Max
            "com.apple.atve.androidtv.appletv",      // Apple TV
            "com.plexapp.android",                   // Plex
            "org.jellyfin.androidtv",                // Jellyfin
            "org.xbmc.kodi",                         // Kodi
            "com.spotify.tv.android",                // Spotify
            "org.videolan.vlc",                      // VLC
        )

        /** Known brand colours, so the big apps always get the right sky. */
        private val HUES = mapOf(
            "com.stremio.one" to 270f,
            "com.netflix.ninja" to 0f,
            "com.google.android.youtube.tv" to 0f,
            "com.liskovsoft.smarttubetv.beta" to 0f,
            "org.smarttube.stable" to 0f,
            "com.amazon.amazonvideo.livingroom" to 200f,
            "com.disney.disneyplus" to 220f,
            "com.wbd.stream" to 255f,
            "com.plexapp.android" to 45f,
            "org.jellyfin.androidtv" to 280f,
            "org.xbmc.kodi" to 200f,
            "com.spotify.tv.android" to 140f,
            "org.videolan.vlc" to 30f,
        )

        /** A fitting pastime for the big apps. Everything else gets one picked from its name. */
        private val ACTIVITIES = mapOf(
            "com.stremio.one" to Activity.COUCH,
            "com.netflix.ninja" to Activity.EATING,
            "com.google.android.youtube.tv" to Activity.CAT,
            "com.liskovsoft.smarttubetv.beta" to Activity.PROGRAMMING,
            "org.smarttube.stable" to Activity.PROGRAMMING,
            "com.amazon.amazonvideo.livingroom" to Activity.HIKING,
            "com.disney.disneyplus" to Activity.SWIMMING,
            "com.wbd.stream" to Activity.CLIMBING,
            "com.apple.atve.androidtv.appletv" to Activity.COUCH,
            "com.plexapp.android" to Activity.READING,
            "org.jellyfin.androidtv" to Activity.READING,
            "org.xbmc.kodi" to Activity.PROGRAMMING,
            "com.spotify.tv.android" to Activity.RUNNING,
            "org.videolan.vlc" to Activity.CAT,
        )

        /** Stock launchers that would only add clutter to the grid. */
        private val HIDDEN = setOf(
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
        )
    }
}
