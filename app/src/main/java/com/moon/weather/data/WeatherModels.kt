package com.moon.weather.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    val current: CurrentWeather?,
    val hourly: HourlyWeather?,
    val daily: DailyWeather?,
    @Json(name = "utc_offset_seconds") val utcOffsetSeconds: Int? = 0
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    val time: String,
    @Json(name = "temperature_2m") val temperature: Double,
    @Json(name = "weather_code") val weatherCode: Int,
    @Json(name = "is_day") val isDay: Int,
    @Json(name = "relative_humidity_2m") val humidity: Double,
    @Json(name = "wind_speed_10m") val windSpeed: Double,
    @Json(name = "wind_direction_10m") val windDirection: Double,
    @Json(name = "pressure_msl") val pressure: Double,
    @Json(name = "uv_index") val uvIndex: Double,
    @Json(name = "visibility") val visibility: Double,
    @Json(name = "apparent_temperature") val apparentTemperature: Double,
    @Json(name = "dew_point_2m") val dewPoint: Double
)


@JsonClass(generateAdapter = true)
data class HourlyWeather(
    val time: List<String>,
    @Json(name = "temperature_2m") val temperatures: List<Double>,
    @Json(name = "precipitation_probability") val precipitationProbabilities: List<Int>,
    @Json(name = "weather_code") val weatherCodes: List<Int>,
    @Json(name = "relative_humidity_2m") val humidity: List<Double>,
    @Json(name = "wind_speed_10m") val windSpeeds: List<Double>,
    @Json(name = "wind_direction_10m") val windDirections: List<Double>,
    @Json(name = "pressure_msl") val pressures: List<Double>,
    @Json(name = "uv_index") val uvIndices: List<Double>,
    @Json(name = "visibility") val visibilities: List<Double>,
    @Json(name = "apparent_temperature") val apparentTemperatures: List<Double>,
    @Json(name = "dew_point_2m") val dewPoints: List<Double>
)

@JsonClass(generateAdapter = true)
data class DailyWeather(
    val time: List<String>,
    @Json(name = "weather_code") val weatherCodes: List<Int>,
    @Json(name = "temperature_2m_max") val maxTemperatures: List<Double>,
    @Json(name = "temperature_2m_min") val minTemperatures: List<Double>,
    @Json(name = "sunrise") val sunrises: List<String>,
    @Json(name = "sunset") val sunsets: List<String>,
    @Json(name = "uv_index_max") val maxUVIndices: List<Double>,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int>?
)
