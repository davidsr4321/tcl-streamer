package com.tclstreamer.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Finds the installed apps a TV launcher can open and orders them for the home screen.
 *
 * Artwork is decoded once per package and kept small, because the TV only has 1 GB of RAM.
 * Call [invalidate] when apps are installed, updated or removed.
 */
class AppRepository(private val context: Context) {

    private val cache = HashMap<String, TvApp>()

    @Synchronized
    fun invalidate() = cache.clear()

    @Synchronized
    fun load(): HomeContent {
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

        return HomeContent(
            hero = featured.firstOrNull(),
            streaming = featured.drop(1),
            others = rest,
        )
    }

    private fun toTvApp(info: ResolveInfo, category: String): TvApp {
        val pm = context.packageManager
        val activity = info.activityInfo
        val banner = activity.loadBanner(pm)
        val icon = if (banner == null) activity.loadIcon(pm) else null
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setClassName(activity.packageName, activity.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        return TvApp(
            packageName = activity.packageName,
            label = info.loadLabel(pm).toString(),
            banner = banner?.toBitmap(BANNER_W, BANNER_H)?.asImageBitmap(),
            icon = icon?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap(),
            launchIntent = intent,
        )
    }

    companion object {
        private const val BANNER_W = 320
        private const val BANNER_H = 180
        private const val ICON_PX = 96

        /**
         * Streaming apps, in the order they earn a spot up front.
         * The first one installed becomes the hero card.
         */
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

        /** System entries that only add clutter to a living-room home screen. */
        private val HIDDEN = setOf(
            "com.android.tv.settings",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
        )
    }
}
