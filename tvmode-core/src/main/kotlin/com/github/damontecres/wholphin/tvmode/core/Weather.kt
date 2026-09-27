package com.github.damontecres.wholphin.tvmode.core

import kotlinx.serialization.Serializable

// GET /CableTv/Weather/{channelId}: a weather channel's forecast, drawn by the TV mode.

@Serializable
data class WeatherNow(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val windDirection: String,
    val pressure: Double,
    val code: Int,
    val condition: String,
)

@Serializable
data class WeatherDay(
    val date: String,
    val high: Double,
    val low: Double,
    val code: Int,
    val condition: String,
    val precipitationChance: Int? = null,
)

@Serializable
data class WeatherHour(
    val time: String,
    val temperature: Double,
    val code: Int,
    val precipitationChance: Int? = null,
)

@Serializable
data class WeatherReport(
    val location: String,
    val metric: Boolean = false,
    val updatedUtc: String,
    val current: WeatherNow,
    val daily: List<WeatherDay> = emptyList(),
    val hourly: List<WeatherHour> = emptyList(),
    val sunrise: String? = null,
    val sunset: String? = null,
) {
    val temperatureUnit: String get() = if (metric) "°C" else "°F"
    val speedUnit: String get() = if (metric) "km/h" else "mph"
    val pressureUnit: String get() = if (metric) "hPa" else "in"
}
