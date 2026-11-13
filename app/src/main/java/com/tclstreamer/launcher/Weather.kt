package com.tclstreamer.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

enum class Daylight { DAY, DUSK, NIGHT }

enum class Weather { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }

/** The weather right now, and today's sunrise and sunset. */
class Conditions(val temperature: Int, val weather: Weather, val sunrise: Instant, val sunset: Instant)

/** Everything outside the window that changes how the top shelf looks. */
class Ambience(val daylight: Daylight, val weather: Weather, val occasion: Occasion?)

object WeatherService {
    /** Open-Meteo needs no account. Returns null when offline. */
    suspend fun fetch(config: DnDConfig): Conditions? = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon) = if (config.latitude != null && config.longitude != null) {
                config.latitude to config.longitude
            } else {
                locate() ?: return@runCatching null
            }
            val json = JSONObject(
                get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code&daily=sunrise,sunset&timezone=auto&forecast_days=1"
                )
            )
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val offset = ZoneOffset.ofTotalSeconds(json.getInt("utc_offset_seconds"))
            fun time(key: String) = LocalDateTime.parse(daily.getJSONArray(key).getString(0)).toInstant(offset)
            Conditions(
                temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
                weather = weatherFor(current.getInt("weather_code")),
                sunrise = time("sunrise"),
                sunset = time("sunset"),
            )
        }.getOrNull()
    }

    /** A rough position from the internet connection, good enough for the weather. */
    private fun locate(): Pair<Double, Double>? = runCatching {
        val json = JSONObject(get("https://ipapi.co/json/"))
        json.getDouble("latitude") to json.getDouble("longitude")
    }.getOrNull()

    private fun weatherFor(code: Int) = when (code) {
        0, 1 -> Weather.CLEAR
        2, 3 -> Weather.CLOUDY
        45, 48 -> Weather.FOG
        in 51..67, in 80..82 -> Weather.RAIN
        in 71..77, 85, 86 -> Weather.SNOW
        in 95..99 -> Weather.STORM
        else -> Weather.CLEAR
    }

    private fun get(url: String): String = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("User-Agent", "DnD-launcher")
        inputStream.use { it.readBytes().decodeToString() }
    }
}

/** Half an hour either side of sunrise and sunset is dusk. Without weather, guess from the clock. */
fun daylightAt(now: Instant, conditions: Conditions?): Daylight {
    if (conditions == null) {
        val hour = LocalTime.now().hour
        return when (hour) {
            6, 18 -> Daylight.DUSK
            in 7..17 -> Daylight.DAY
            else -> Daylight.NIGHT
        }
    }
    val margin = Duration.ofMinutes(30)
    return when {
        now.isAfter(conditions.sunrise.minus(margin)) && now.isBefore(conditions.sunrise.plus(margin)) -> Daylight.DUSK
        now.isAfter(conditions.sunset.minus(margin)) && now.isBefore(conditions.sunset.plus(margin)) -> Daylight.DUSK
        now.isAfter(conditions.sunrise) && now.isBefore(conditions.sunset) -> Daylight.DAY
        else -> Daylight.NIGHT
    }
}
