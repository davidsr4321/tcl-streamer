package com.tclstreamer.launcher

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Something half-watched, to pick up where you left off. */
class WatchItem(
    val key: String,
    val title: String,
    /** How far through, from 0 to 1. */
    val progress: Float,
    val image: ImageBitmap?,
    /** Tried in order: a deep link to the title, then the app itself. */
    val intents: List<Intent>,
    val updatedAt: Long,
)

/**
 * Recently watched titles from Plex and Stremio, newest first. Both need their keys in dnd.json;
 * whichever is missing is skipped.
 */
object ContinueWatching {
    private const val MAX = 10
    private val images = HashMap<String, ImageBitmap?>()

    suspend fun load(config: DnDConfig): List<WatchItem> = withContext(Dispatchers.IO) {
        val plex = runCatching { plex(config) }.getOrDefault(emptyList())
        val stremio = runCatching { stremio(config) }.getOrDefault(emptyList())
        (plex + stremio).sortedByDescending { it.updatedAt }.take(MAX)
    }

    private fun plex(config: DnDConfig): List<WatchItem> {
        val base = config.plexUrl ?: return emptyList()
        val token = config.plexToken ?: return emptyList()
        val server = JSONObject(get("$base/identity", plexHeaders(token)))
            .getJSONObject("MediaContainer").optString("machineIdentifier")
        val body = runCatching { get("$base/hubs/continueWatching/items", plexHeaders(token)) }
            .getOrElse { get("$base/library/onDeck", plexHeaders(token)) }
        val items = JSONObject(body).getJSONObject("MediaContainer").optJSONArray("Metadata") ?: JSONArray()
        return (0 until minOf(items.length(), MAX)).map { i ->
            val m = items.getJSONObject(i)
            val show = m.optString("grandparentTitle")
            val title = if (show.isNotEmpty()) "$show · ${m.optString("title")}" else m.optString("title")
            val duration = m.optLong("duration", 0L)
            val art = m.optString("art").ifEmpty { m.optString("grandparentArt") }.ifEmpty { m.optString("thumb") }
            val image = art.takeIf { it.isNotEmpty() }?.let {
                "$base/photo/:/transcode?width=320&height=180&minSize=1&upscale=1" +
                    "&url=${URLEncoder.encode(it, "UTF-8")}&X-Plex-Token=$token"
            }
            val ratingKey = m.optString("ratingKey")
            WatchItem(
                key = "plex:$ratingKey",
                title = title,
                progress = if (duration > 0) m.optLong("viewOffset", 0L).toFloat() / duration else 0f,
                image = image?.let(::image),
                intents = listOfNotNull(
                    Intent(Intent.ACTION_VIEW, Uri.parse("plex://play/?metadataKey=/library/metadata/$ratingKey&server=$server"))
                        .setPackage(PLEX),
                    launchIntent(PLEX),
                ),
                updatedAt = m.optLong("lastViewedAt", m.optLong("updatedAt", 0L)) * 1000,
            )
        }
    }

    private fun stremio(config: DnDConfig): List<WatchItem> {
        val key = config.stremioAuthKey ?: return emptyList()
        val request = JSONObject()
            .put("authKey", key)
            .put("collection", "libraryItem")
            .put("ids", JSONArray())
            .put("all", true)
        val result = JSONObject(post("https://api.strem.io/api/datastoreGet", request.toString())).getJSONArray("result")
        val items = (0 until result.length()).map { result.getJSONObject(it) }.filter { item ->
            val state = item.optJSONObject("state") ?: return@filter false
            val offset = state.optLong("timeOffset", 0L)
            val duration = state.optLong("duration", 0L)
            !item.optBoolean("removed") && !item.optBoolean("temp") && offset > 0 &&
                (duration == 0L || offset < duration * 0.95)
        }.sortedByDescending { it.optString("_mtime") }.take(MAX)

        return items.map { item ->
            val id = item.optString("_id")
            val type = item.optString("type")
            val state = item.getJSONObject("state")
            val duration = state.optLong("duration", 0L)
            val video = state.optString("video_id")
            // The wide background suits a 16:9 tile better than the poster.
            val image = if (id.startsWith("tt")) "https://images.metahub.space/background/medium/$id/img" else item.optString("poster")
            WatchItem(
                key = "stremio:$id",
                title = item.optString("name"),
                progress = if (duration > 0) state.optLong("timeOffset").toFloat() / duration else 0f,
                image = image.takeIf { it.isNotEmpty() }?.let(::image),
                intents = listOfNotNull(
                    Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/$type/$id" + if (video.isNotEmpty()) "/$video" else ""))
                        .setPackage(STREMIO),
                    launchIntent(STREMIO),
                ),
                updatedAt = runCatching { java.time.Instant.parse(item.optString("_mtime")).toEpochMilli() }.getOrDefault(0L),
            )
        }
    }

    private const val PLEX = "com.plexapp.android"
    private const val STREMIO = "com.stremio.one"

    private fun launchIntent(pkg: String) = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        .setPackage(pkg)

    /** Downloaded once, shrunk to tile size so ten of them stay small. */
    private fun image(url: String): ImageBitmap? = synchronized(images) { images[url] } ?: runCatching {
        val bytes = (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000
            readTimeout = 15_000
            inputStream.use { it.readBytes() }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 320) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
    }.getOrNull().also { synchronized(images) { images[url] = it } }

    private fun plexHeaders(token: String) = mapOf("Accept" to "application/json", "X-Plex-Token" to token)

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000
            readTimeout = 15_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (responseCode !in 200..299) error("HTTP $responseCode")
            inputStream.use { it.readBytes().decodeToString() }
        }

    private fun post(url: String, body: String): String = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 10_000
        readTimeout = 15_000
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        outputStream.use { it.write(body.toByteArray()) }
        inputStream.use { it.readBytes().decodeToString() }
    }
}
