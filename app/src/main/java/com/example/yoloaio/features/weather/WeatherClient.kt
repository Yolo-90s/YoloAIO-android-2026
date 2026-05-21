package com.example.yoloaio.features.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone

object WeatherClient {

    private const val TAG = "Weather"
    private const val BASE_CURRENT = "https://api.openweathermap.org/data/2.5/weather"
    private const val BASE_FORECAST = "https://api.openweathermap.org/data/2.5/forecast"

    /**
     * Fetches current conditions + 5-day/3-hour forecast in parallel and merges
     * them. If the forecast call fails we still return the current snapshot —
     * the screen can render hero + stats without forecast data.
     */
    suspend fun fetch(
        lat: Double,
        lon: Double,
        apiKey: String
    ): Result<WeatherInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "Weather API key missing" }
            coroutineScope {
                val currentDeferred = async {
                    JSONObject(httpGet("$BASE_CURRENT?lat=$lat&lon=$lon&units=metric&appid=$apiKey"))
                }
                val forecastDeferred = async {
                    runCatching {
                        JSONObject(
                            httpGet("$BASE_FORECAST?lat=$lat&lon=$lon&units=metric&appid=$apiKey")
                        )
                    }.getOrNull()
                }
                val current = parseCurrent(currentDeferred.await())
                val forecast = forecastDeferred.await()?.let { parseForecast(it) }
                if (forecast != null) {
                    current.copy(hourly = forecast.first, daily = forecast.second)
                } else current
            }
        }.onFailure { Log.e(TAG, "fetch failed", it) }
    }

    private fun parseCurrent(root: JSONObject): WeatherInfo {
        val main = root.getJSONObject("main")
        val weather = root.getJSONArray("weather").getJSONObject(0)
        val sys = root.optJSONObject("sys") ?: JSONObject()
        val wind = root.optJSONObject("wind") ?: JSONObject()
        val clouds = root.optJSONObject("clouds") ?: JSONObject()

        val nowMs = System.currentTimeMillis()
        val sunriseMs = sys.optLong("sunrise", 0) * 1000L
        val sunsetMs = sys.optLong("sunset", 0) * 1000L
        val isDay = if (sunriseMs > 0 && sunsetMs > 0) {
            nowMs in sunriseMs..sunsetMs
        } else true

        return WeatherInfo(
            locationName = root.optString("name").ifBlank { "Your location" },
            countryCode = sys.optString("country"),
            tempC = main.optDouble("temp", 0.0),
            feelsLikeC = main.optDouble("feels_like", 0.0),
            tempMinC = main.optDouble("temp_min", 0.0),
            tempMaxC = main.optDouble("temp_max", 0.0),
            humidityPct = main.optInt("humidity", 0),
            windKph = wind.optDouble("speed", 0.0) * 3.6,
            pressureHpa = main.optInt("pressure", 0),
            description = weather.optString("description").replaceFirstChar { it.titlecase() },
            condition = mapCondition(weather.optString("main"), isDay),
            sunriseEpochMs = sunriseMs,
            sunsetEpochMs = sunsetMs,
            observedAtEpochMs = nowMs,
            cloudsPct = clouds.optInt("all", 0),
            visibilityKm = root.optInt("visibility", 0) / 1000.0
        )
    }

    /**
     * Returns (hourly, daily). Hourly = first 8 forecast slots (~24h at 3h
     * resolution). Daily = aggregated per local-date min/max + noon condition,
     * capped at 5 entries.
     */
    private fun parseForecast(root: JSONObject): Pair<List<HourEntry>, List<DayEntry>> {
        val list: JSONArray = root.optJSONArray("list") ?: return emptyList<HourEntry>() to emptyList()
        val entries = (0 until list.length()).map { i -> list.getJSONObject(i) }

        val hourly = entries.take(8).map { entry ->
            val dtMs = entry.optLong("dt", 0) * 1000L
            val weatherObj = entry.optJSONArray("weather")?.optJSONObject(0)
            val main = weatherObj?.optString("main").orEmpty()
            val tempC = entry.optJSONObject("main")?.optDouble("temp", 0.0) ?: 0.0
            val pop = (entry.optDouble("pop", 0.0) * 100.0).toInt().coerceIn(0, 100)
            HourEntry(
                timeEpochMs = dtMs,
                tempC = tempC,
                condition = mapCondition(main, hourIsDay(dtMs)),
                popPct = pop
            )
        }

        // Group by local calendar date.
        val tz = TimeZone.getDefault()
        val byDate = entries.groupBy { entry ->
            val cal = Calendar.getInstance(tz).apply {
                timeInMillis = entry.optLong("dt", 0) * 1000L
            }
            // yyyy-doy as a sortable key
            cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        }

        val daily = byDate
            .toSortedMap()
            .values
            .map { dayEntries ->
                val temps = dayEntries.mapNotNull {
                    it.optJSONObject("main")?.optDouble("temp", Double.NaN)
                        ?.takeIf { !it.isNaN() }
                }
                val min = temps.minOrNull() ?: 0.0
                val max = temps.maxOrNull() ?: 0.0
                // Use the entry closest to local noon for the condition icon.
                val noonEntry = dayEntries.minByOrNull { entry ->
                    val cal = Calendar.getInstance(tz).apply {
                        timeInMillis = entry.optLong("dt", 0) * 1000L
                    }
                    val hr = cal.get(Calendar.HOUR_OF_DAY)
                    kotlin.math.abs(hr - 12)
                } ?: dayEntries.first()
                val dtMs = noonEntry.optLong("dt", 0) * 1000L
                val main = noonEntry.optJSONArray("weather")
                    ?.optJSONObject(0)?.optString("main").orEmpty()
                val pop = dayEntries.maxOfOrNull {
                    (it.optDouble("pop", 0.0) * 100.0).toInt()
                }?.coerceIn(0, 100) ?: 0
                DayEntry(
                    dateEpochMs = dtMs,
                    minC = min,
                    maxC = max,
                    condition = mapCondition(main, isDay = true),
                    popPct = pop
                )
            }
            .take(5)

        return hourly to daily
    }

    private fun hourIsDay(epochMs: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        val hr = cal.get(Calendar.HOUR_OF_DAY)
        return hr in 6..18
    }

    private fun mapCondition(main: String, isDay: Boolean): WeatherCondition = when (main) {
        "Clear" -> if (isDay) WeatherCondition.ClearDay else WeatherCondition.ClearNight
        "Clouds" -> WeatherCondition.Cloudy
        "Rain", "Drizzle" -> WeatherCondition.Rain
        "Thunderstorm" -> WeatherCondition.Thunderstorm
        "Snow" -> WeatherCondition.Snow
        "Mist", "Smoke", "Haze", "Dust", "Fog", "Sand", "Ash", "Squall", "Tornado" ->
            WeatherCondition.Mist
        else -> if (isDay) WeatherCondition.Cloudy else WeatherCondition.ClearNight
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; YoloAIO) Mobile")
        }
        try {
            val code = conn.responseCode
            Log.d(TAG, "← HTTP $code  $url")
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.take(200) ?: "no body"
                error("HTTP $code · $err")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
