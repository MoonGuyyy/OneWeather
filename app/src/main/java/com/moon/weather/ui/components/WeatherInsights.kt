package com.moon.weather.ui.components

import com.moon.weather.data.WeatherResponse
import com.moon.weather.utils.Temperature
import kotlin.math.roundToInt

internal data class WeatherInsight(val category: String, val message: String, val value: String, val label: String)

/** Eight authored alternatives per category; selection is stable for each forecast day. */
internal object WeatherInsights {
    internal val messages = mapOf(
        "Today's outlook" to listOf(
            "Today's forecast high is {value}. Check the hourly outlook for changes.",
            "Plan your day around a forecast high of {value}.",
            "Temperatures are forecast to reach {value} today.",
            "Today's high is {value}. Keep an eye on the hourly forecast.",
            "The day's temperature peaks near {value}.",
            "Your local forecast calls for a high of {value}.",
            "Today's temperature outlook reaches {value}.",
            "A high of {value} is on the forecast for today."
        ),
        "Tomorrow's forecast" to listOf(
            "Tomorrow's high is {value}. A fresh outlook for your next day.",
            "Looking ahead: temperatures reach {value} tomorrow.",
            "Plan tomorrow around a forecast high of {value}.",
            "The next day's forecast reaches {value}.",
            "Tomorrow is forecast to peak near {value}.",
            "Your temperature outlook for tomorrow: a high of {value}.",
            "A high of {value} is on tomorrow's forecast.",
            "Tomorrow's temperatures climb to around {value}."
        ),
        "Overnight outlook" to listOf(
            "Tonight's forecast low is {value}. Keep a layer handy.",
            "Temperatures dip to {value} overnight.",
            "Heading out tonight? The low is {value}.",
            "The overnight outlook cools to {value}.",
            "Plan for a low of {value} tonight.",
            "Evening plans may need a layer: the low is {value}.",
            "Tonight's temperatures are forecast to reach a low of {value}.",
            "The day winds down with a forecast low of {value}."
        ),
        "Rain outlook" to listOf(
            "Precipitation chances peak at {value} in the next 24 hours.",
            "Your next-day precipitation outlook: up to {value}.",
            "The highest precipitation chance ahead is {value}.",
            "Planning time outside? Precipitation chances reach {value}.",
            "Check the hourly outlook: precipitation chances peak at {value}.",
            "Rain chances in the next 24 hours top out at {value}.",
            "The forecast puts precipitation chances at up to {value}.",
            "Your rain outlook reaches a maximum chance of {value}."
        ),
        "Wind outlook" to listOf(
            "Winds reach {value} over the next 24 hours.",
            "The wind outlook peaks near {value}.",
            "Planning an outdoor trip? Forecast winds reach {value}.",
            "Expect winds up to {value} in the next day.",
            "Your next-day wind forecast reaches {value}.",
            "The forecast breeze peaks at {value}.",
            "Keep the wind in mind: speeds reach {value}.",
            "Peak forecast winds are around {value}."
        ),
        "Heat" to listOf(
            "A hot day ahead, with temperatures reaching {value}.",
            "Temperatures climb to {value}. Take breaks in the shade.",
            "Today's high is {value}. Keep water close by.",
            "Plan outdoor time for cooler hours; the high is {value}.",
            "Expect a hot afternoon, peaking near {value}.",
            "Light clothing may feel best with a high of {value}.",
            "The forecast reaches {value}. Find a cool spot to recharge.",
            "Make room for shade today: temperatures peak at {value}."
        ),
        "Cold" to listOf(
            "A chilly day ahead, with temperatures as low as {value}.",
            "Wrap up before heading out; the low is {value}.",
            "An extra layer will help with a low of {value}.",
            "Keep something warm handy: temperatures dip to {value}.",
            "Cold air is on the forecast, with a low near {value}.",
            "A warm jacket may come in handy around {value}.",
            "Expect a crisp day; the forecast low is {value}.",
            "Cool conditions ahead, dipping to {value}."
        ),
        "Rain" to listOf(
            "Keep an umbrella handy; rain chances reach {value}.",
            "Wet weather could interrupt outdoor plans: {value} rain chance.",
            "A rain layer may come in handy with a {value} chance.",
            "Check the hourly forecast before heading out: {value} rain chance.",
            "Leave room for an indoor plan; rain chances peak at {value}.",
            "Showers are possible, with rain chances up to {value}.",
            "Keep your plans flexible: precipitation chances reach {value}.",
            "Consider a covered route with a {value} chance of rain."
        ),
        "Snow" to listOf(
            "Snow is in the forecast. Allow extra time outdoors.",
            "Snowy conditions ahead; choose shoes with good grip.",
            "Watch for slippery patches with snow in the forecast.",
            "A wintry forecast calls for warm layers.",
            "Snow could affect travel. Check local conditions before leaving.",
            "Keep your plans flexible around the forecast snow.",
            "Wintry weather ahead. Take care on exposed paths.",
            "Snow is possible; bring an extra warm layer."
        ),
        "Thunder" to listOf(
            "Thunderstorms are forecast. Keep an indoor option ready.",
            "Watch for changing skies with storms in the forecast.",
            "Plan a sheltered break if thunder develops.",
            "Check local alerts before outdoor plans; storms are possible.",
            "Thunderstorms may disrupt the day. Stay aware of local conditions.",
            "Stormy weather is forecast. Choose a sheltered place if thunder starts.",
            "Keep outdoor plans flexible around possible thunderstorms.",
            "If you hear thunder, head indoors. Storms are on the forecast."
        ),
        "Wind" to listOf(
            "Wind reaches {value}. Exposed spots may feel breezy.",
            "A breezy forecast, with winds around {value}.",
            "Secure light outdoor items; winds reach {value}.",
            "A windproof layer may help with winds of {value}.",
            "Expect a brisk breeze, reaching {value}.",
            "Consider a sheltered route with winds around {value}.",
            "Windy conditions ahead: speeds reach {value}.",
            "The breeze picks up to {value}. Keep a layer handy."
        ),
        "UV" to listOf(
            "UV reaches {value}. Consider shade and sun protection.",
            "Sun protection is useful today, with a UV peak of {value}.",
            "Bring a hat for time outside: UV peaks at {value}.",
            "Look for shade during peak sun hours; UV reaches {value}.",
            "Clouds can still let UV through. Today's peak is {value}.",
            "Plan a shaded break with a UV index up to {value}.",
            "Keep sunscreen handy; the forecast UV peak is {value}.",
            "Take care in the sun today: UV reaches {value}."
        ),
        "Fog" to listOf(
            "Fog is forecast. Visibility may change quickly.",
            "Allow extra time for travel with fog in the forecast.",
            "Misty conditions may obscure views today.",
            "Watch for reduced visibility in forecast fog.",
            "Keep an eye on local visibility before heading out.",
            "Foggy weather ahead; take extra care on the road.",
            "Views may be limited while the fog settles in.",
            "The forecast includes fog. Leave some time for a slower journey."
        ),
        "Warmer tomorrow" to listOf(
            "Tomorrow turns warmer, with a high of {value}.",
            "A warmer day is ahead: tomorrow peaks at {value}.",
            "Temperatures rise tomorrow, reaching {value}.",
            "Plan for warmer weather tomorrow, with a high near {value}.",
            "Tomorrow's high climbs to {value}. A lighter layer may help.",
            "A warmer afternoon is coming tomorrow: {value}.",
            "Expect a step up in temperature tomorrow, peaking at {value}.",
            "The forecast warms up tomorrow, with a high of {value}."
        ),
        "Cooler tomorrow" to listOf(
            "Tomorrow turns cooler, with a high of {value}.",
            "Keep a layer ready: tomorrow's high drops to {value}.",
            "Cooler air arrives tomorrow, with a high near {value}.",
            "Plan for a cooler day tomorrow, peaking at {value}.",
            "Tomorrow brings a lower high of {value}.",
            "Temperatures ease tomorrow; the high is {value}.",
            "An extra layer may suit tomorrow's cooler high of {value}.",
            "The forecast cools down tomorrow, reaching {value}."
        ),
        "Clear skies" to listOf(
            "Clear skies are forecast. Today's high is {value}.",
            "A bright forecast ahead, with a high of {value}.",
            "Expect mostly clear conditions, reaching {value} today.",
            "Sunshine is on the forecast, with a high near {value}.",
            "A clear day ahead. Temperatures peak at {value}.",
            "Enjoy the brighter skies; today's high is {value}.",
            "Mostly clear weather is forecast, reaching {value}.",
            "Today's forecast pairs clear skies with a high of {value}."
        ),
        "Cloud cover" to listOf(
            "Clouds feature in today's forecast, with a high of {value}.",
            "A cloudy outlook ahead, reaching {value} today.",
            "Expect some cloud cover, with temperatures up to {value}.",
            "Cloudier skies are forecast. Today's high is {value}.",
            "The forecast brings clouds and a high near {value}.",
            "A little cloud cover today, with a high of {value}.",
            "Keep a layer handy under cloudy skies; the high is {value}.",
            "Clouds are on the forecast, with temperatures reaching {value}."
        )
    )

    fun create(weather: WeatherResponse, celsius: Boolean, useMph: Boolean = false): List<WeatherInsight> {
        val current = weather.current
        val daily = weather.daily
        val hourly = weather.hourly
        val today = current?.time?.take(10)
        val day = daily?.time?.indexOf(today)?.takeIf { it >= 0 } ?: 0
        val hour = hourly?.time?.indexOfFirst { it >= (current?.time ?: "") }?.takeIf { it >= 0 }
        val range = if (hour != null) hour until minOf(hour + 24, hourly!!.time.size) else IntRange.EMPTY
        val upcomingCodes = range.mapNotNull { hourly?.weatherCodes?.getOrNull(it) }
        val codes = upcomingCodes + listOfNotNull(current?.weatherCode) +
            if (upcomingCodes.isEmpty() && current == null) listOfNotNull(daily?.weatherCodes?.getOrNull(day)) else emptyList()
        val highs = daily?.maxTemperatures?.getOrNull(day) ?: current?.temperature
        val low = daily?.minTemperatures?.getOrNull(day) ?: current?.temperature
        val rain = range.mapNotNull { hourly?.precipitationProbabilities?.getOrNull(it) }.maxOrNull()
            ?: daily?.precipitationProbabilityMax?.getOrNull(day)
        val wind = (range.mapNotNull { hourly?.windSpeeds?.getOrNull(it) } + listOfNotNull(current?.windSpeed)).filter { it.isFinite() }.maxOrNull()
        val uv = daily?.maxUVIndices?.getOrNull(day) ?: current?.uvIndex
        fun temp(value: Double) = "${Temperature.value(value, celsius)}°${if (celsius) "C" else "F"}"
        val result = mutableListOf<WeatherInsight>()
        val seed = (today ?: daily?.time?.getOrNull(day) ?: "").hashCode()
        fun add(category: String, value: String, label: String) {
            val variants = messages.getValue(category)
            val message = variants[Math.floorMod(seed + category.hashCode(), variants.size)].replace("{value}", value)
            result.add(WeatherInsight(category, message, value, label))
        }
        when {
            codes.any { it in 95..99 } -> add("Thunder", "Storms", "Next 24 hours")
            codes.any { it in 71..77 || it in 85..86 } -> add("Snow", "Snow", "Next 24 hours")
            rain != null && rain >= 45 -> add("Rain", "$rain%", "Rain chance")
        }
        if (highs != null && highs >= 30) add("Heat", temp(highs), "Today's high")
        else if (low != null && low <= 5) add("Cold", temp(low), "Today's low")
        if (wind != null && wind >= 25) add("Wind", "${(if (useMph) wind / 1.609344 else wind).roundToInt()} ${if (useMph) "mph" else "km/h"}", "Peak wind")
        if (codes.any { it == 45 || it == 48 }) add("Fog", "Fog", "Next 24 hours")
        if (uv != null && uv >= 5) add("UV", "${uv.roundToInt()}", "Peak UV")
        val tomorrow = daily?.maxTemperatures?.getOrNull(day + 1)
        if (tomorrow != null && highs != null) {
            if (tomorrow - highs >= 3) add("Warmer tomorrow", temp(tomorrow), "Tomorrow")
            else if (highs - tomorrow >= 3) add("Cooler tomorrow", temp(tomorrow), "Tomorrow")
            else if (result.size < 3) add("Tomorrow's forecast", temp(tomorrow), "Tomorrow")
        }
        if (result.size < 3 && highs != null && codes.isNotEmpty()) {
            val daytimeCodes = range.filter { hourly?.time?.getOrNull(it)?.substringAfter("T")?.take(2)?.toIntOrNull() in 8..18 }
                .mapNotNull { hourly?.weatherCodes?.getOrNull(it) }
            val representative = current?.weatherCode ?: daily?.weatherCodes?.getOrNull(day)
            if ((daytimeCodes.ifEmpty { listOfNotNull(representative) }).all { it == 0 || it == 1 })
                add("Clear skies", temp(highs), "Today's high")
            else if (representative == 2 || representative == 3) add("Cloud cover", temp(highs), "Today's high")
        }
        if (result.size < 3 && low != null && highs != null && highs - low >= 4 && result.none { it.category == "Cold" })
            add("Overnight outlook", temp(low), "Tonight's low")
        if (result.size < 3 && rain != null && result.none { it.category in listOf("Rain", "Snow", "Thunder") })
            add("Rain outlook", "$rain%", "Rain chance")
        if (result.size < 3 && wind != null && result.none { it.category == "Wind" })
            add("Wind outlook", "${(if (useMph) wind / 1.609344 else wind).roundToInt()} ${if (useMph) "mph" else "km/h"}", "Peak wind")
        if (result.isEmpty() && highs != null) add("Today's outlook", temp(highs), "Today's high")
        return result.take(4)
    }
}
