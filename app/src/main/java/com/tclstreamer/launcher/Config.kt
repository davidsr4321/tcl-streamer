package com.tclstreamer.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.MonthDay

/**
 * Personal settings, kept on the TV rather than in the public repo. Put them there with:
 * adb push dnd.json /sdcard/Android/data/com.tclstreamer.launcher/files/dnd.json
 * Everything is optional; dnd.example.json in the repo shows the shape.
 */
class DnDConfig(
    /** Where to fetch the weather for. Without these D&D works it out from the internet connection. */
    val latitude: Double?,
    val longitude: Double?,
    val birthdays: List<Birthday>,
    /** Which of halloween, hanukkah, christmas and newyear to decorate for. */
    val holidays: Set<String>,
    val plexUrl: String?,
    val plexToken: String?,
    val stremioAuthKey: String?,
) {
    class Birthday(val name: String, val day: MonthDay)

    companion object {
        private val ALL_HOLIDAYS = setOf("halloween", "hanukkah", "christmas", "newyear")

        fun file(context: Context) = File(context.getExternalFilesDir(null), "dnd.json")

        fun load(context: Context): DnDConfig {
            val json = runCatching { JSONObject(file(context).readText()) }.getOrNull() ?: JSONObject()
            val birthdays = json.optJSONArray("birthdays")?.let { list ->
                (0 until list.length()).mapNotNull { i ->
                    val item = list.optJSONObject(i) ?: return@mapNotNull null
                    // "MM-DD", e.g. "03-14".
                    val parts = item.optString("date").split("-").mapNotNull { it.trim().toIntOrNull() }
                    val day = parts.takeIf { it.size == 2 }?.let { runCatching { MonthDay.of(it[0], it[1]) }.getOrNull() }
                    day?.let { Birthday(item.optString("name"), it) }
                }
            }.orEmpty()
            val holidays = json.optJSONArray("holidays")?.let { list ->
                (0 until list.length()).map { list.optString(it).lowercase() }.toSet()
            } ?: ALL_HOLIDAYS
            val plex = json.optJSONObject("plex")
            return DnDConfig(
                latitude = json.optDouble("latitude").takeUnless { it.isNaN() },
                longitude = json.optDouble("longitude").takeUnless { it.isNaN() },
                birthdays = birthdays,
                holidays = holidays,
                plexUrl = plex?.optString("url")?.trimEnd('/')?.ifBlank { null },
                plexToken = plex?.optString("token")?.ifBlank { null },
                stremioAuthKey = json.optString("stremioAuthKey").ifBlank { null },
            )
        }
    }
}
