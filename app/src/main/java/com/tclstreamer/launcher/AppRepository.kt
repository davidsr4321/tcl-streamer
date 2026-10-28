package com.tclstreamer.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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

    /**
     * The banner at full-screen size for the top shelf. Only the focused app's artwork is
     * ever held at this size, so it is loaded on demand rather than cached.
     */
    fun loadShelfArt(app: TvApp): ImageBitmap? = runCatching {
        val pm = context.packageManager
        pm.getActivityInfo(app.component, 0).loadBanner(pm)
            ?.toBitmap(SHELF_W, SHELF_H)
            ?.asImageBitmap()
    }.getOrNull()

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
            component = intent.component!!,
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

    companion object {
        private const val BANNER_W = 320
        private const val BANNER_H = 180
        private const val ICON_PX = 96
        private const val SHELF_W = 960
        private const val SHELF_H = 540
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

        /** Stock launchers that would only add clutter to the grid. */
        private val HIDDEN = setOf(
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
        )
    }
}
