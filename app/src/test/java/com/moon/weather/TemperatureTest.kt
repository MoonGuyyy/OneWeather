package com.moon.weather

import com.moon.weather.utils.Temperature
import org.junit.Assert.assertEquals
import org.junit.Test

class TemperatureTest {
    @Test fun fractionalReadingsRoundRatherThanLoseTheirDecimals() {
        assertEquals(15, Temperature.value(14.9, true))
        assertEquals(14, Temperature.value(14.2, true))
        assertEquals(-15, Temperature.value(-14.9, true))
        assertEquals(-1, Temperature.value(-.6, true))
    }

    @Test fun fahrenheitConvertsBeforeRounding() {
        assertEquals(59, Temperature.value(14.9, false))
        assertEquals(31, Temperature.value(-.6, false))
        assertEquals(-40, Temperature.value(-40.0, false))
    }
}
