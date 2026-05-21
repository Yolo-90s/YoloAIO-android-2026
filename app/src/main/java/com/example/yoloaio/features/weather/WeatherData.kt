package com.example.yoloaio.features.weather

/**
 * Coarse classification of the weather, mapped from OpenWeatherMap's `weather[0].main`
 * field. Drives which animated background renders, plus the gradient palette.
 *
 * Clear is split day/night so we can show a sun or a moon respectively.
 */
enum class WeatherCondition {
    ClearDay,
    ClearNight,
    Cloudy,
    Rain,
    Thunderstorm,
    Snow,
    Mist
}

data class HourEntry(
    val timeEpochMs: Long,
    val tempC: Double,
    val condition: WeatherCondition,
    val popPct: Int            // probability of precipitation 0–100
)

data class DayEntry(
    val dateEpochMs: Long,
    val minC: Double,
    val maxC: Double,
    val condition: WeatherCondition,
    val popPct: Int
)

data class WeatherInfo(
    val locationName: String,
    val countryCode: String,
    val tempC: Double,
    val feelsLikeC: Double,
    val tempMinC: Double,
    val tempMaxC: Double,
    val humidityPct: Int,
    val windKph: Double,
    val pressureHpa: Int,
    val description: String,
    val condition: WeatherCondition,
    val sunriseEpochMs: Long,
    val sunsetEpochMs: Long,
    val observedAtEpochMs: Long,
    // ── extended fields (default-empty so older code paths still compile)
    val cloudsPct: Int = 0,
    val visibilityKm: Double = 0.0,
    val hourly: List<HourEntry> = emptyList(),
    val daily: List<DayEntry> = emptyList()
)
