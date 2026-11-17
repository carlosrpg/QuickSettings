package com.carlosrpg.cherry.quicksettings.weather

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

class WeatherParseTest {
    @Test
    fun `parses an Open-Meteo reply`() {
        val json = """
            {"current":{"time":"2026-11-16T14:00","temperature_2m":23.6,"weather_code":2,"is_day":1},
             "daily":{"time":["2026-11-16","2026-11-17"],"weather_code":[61,95],
                      "temperature_2m_max":[27.4,25.0],"temperature_2m_min":[17.2,16.5]}}
        """.trimIndent()
        val f = parseForecast(json, fetchedAt = 7L)
        assertThat(f.tempC).isEqualTo(24)
        assertThat(f.kind).isEqualTo(WeatherKind.PARTLY_CLOUDY)
        assertThat(f.isDay).isTrue()
        assertThat(f.days).containsExactly(
            DayForecast(LocalDate.of(2026, 11, 16), WeatherKind.RAIN, 27, 17),
            DayForecast(LocalDate.of(2026, 11, 17), WeatherKind.THUNDER, 25, 17),
        ).inOrder()
    }

    @Test
    fun `wmo codes map to kinds`() {
        assertThat(weatherKind(0)).isEqualTo(WeatherKind.CLEAR)
        assertThat(weatherKind(3)).isEqualTo(WeatherKind.CLOUDY)
        assertThat(weatherKind(48)).isEqualTo(WeatherKind.FOG)
        assertThat(weatherKind(53)).isEqualTo(WeatherKind.DRIZZLE)
        assertThat(weatherKind(81)).isEqualTo(WeatherKind.RAIN)
        assertThat(weatherKind(86)).isEqualTo(WeatherKind.SNOW)
        assertThat(weatherKind(99)).isEqualTo(WeatherKind.THUNDER)
    }
}
