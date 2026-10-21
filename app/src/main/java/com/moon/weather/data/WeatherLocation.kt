package com.moon.weather.data

data class WeatherLocation(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String = ""
)

/** Geocoding may change labels or country metadata for an already saved place. */
internal fun WeatherLocation.samePlace(other: WeatherLocation): Boolean =
    kotlin.math.abs(lat - other.lat) < .01 && kotlin.math.abs(lon - other.lon) < .01

internal fun WeatherLocation.pageKey(): String = "city:$lat:$lon"
