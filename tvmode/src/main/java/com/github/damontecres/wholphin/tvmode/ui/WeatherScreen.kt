package com.github.damontecres.wholphin.tvmode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.wholphin.tvmode.core.TvChannel
import com.github.damontecres.wholphin.tvmode.core.WeatherReport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val weatherBlue = Brush.verticalGradient(listOf(Color(0xFF102080), Color(0xFF001040)))
private val weatherYellow = Color(0xFFFFE63C)
private val weatherPale = Color(0xFFC8D6F5)
private const val PAGE_MS = 10_000L

/**
 * A weather channel, drawn on the TV from the plugin's forecast: current conditions, the extended forecast and the
 * next hours, rotating every ten seconds, in the style of a 90s weather channel.
 */
@Composable
fun WeatherScreen(
    channel: TvChannel?,
    report: WeatherReport?,
    error: String?,
    clock: State<Long>,
    clock24h: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = if (compact) 0.45f else 1f

    fun size(value: Int): TextUnit = (value * scale).sp
    Column(modifier.fillMaxSize().background(weatherBlue)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xE0060C30))
                .padding(horizontal = (40 * scale).dp, vertical = (14 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvText("LOCAL FORECAST", size = size(34), bold = true, color = weatherYellow)
            Spacer(Modifier.weight(1f))
            TvText((report?.location ?: channel?.name ?: "").uppercase(), size = size(24), color = Color.White)
            Spacer(Modifier.width((24 * scale).dp))
            TvText(formatClock(clock.value, clock24h), size = size(24), color = Color.White)
        }
        Box(Modifier.fillMaxSize().padding(horizontal = (48 * scale).dp, vertical = (24 * scale).dp)) {
            if (report == null) {
                TvText(
                    error ?: "Loading the forecast…",
                    size = size(28),
                    color = weatherPale,
                    maxLines = 3,
                    modifier = Modifier.align(Alignment.Center),
                )
                return@Box
            }
            when ((clock.value / PAGE_MS) % 3) {
                0L -> CurrentPage(report, ::size)
                1L -> ExtendedPage(report, ::size, scale)
                else -> HourlyPage(report, ::size, scale, clock24h)
            }
        }
    }
}

@Composable
private fun CurrentPage(
    report: WeatherReport,
    size: (Int) -> TextUnit,
) {
    val now = report.current
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            TvText("CURRENT CONDITIONS", size = size(22), color = weatherPale)
            TvText("${now.temperature.roundToInt()}${report.temperatureUnit}", size = size(120), bold = true, color = Color.White)
            TvText(now.condition, size = size(36), color = weatherYellow)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Detail("Feels like", "${now.feelsLike.roundToInt()}${report.temperatureUnit}", size)
            Detail("Humidity", "${now.humidity}%", size)
            Detail(
                "Wind",
                if (now.windSpeed <
                    1
                ) {
                    "Calm"
                } else {
                    "${now.windDirection} ${now.windSpeed.roundToInt()} ${report.speedUnit}"
                },
                size,
            )
            Detail("Pressure", "${now.pressure} ${report.pressureUnit}", size)
            if (report.sunrise != null && report.sunset != null) Detail("Sun", "${report.sunrise} – ${report.sunset}", size)
        }
    }
}

@Composable
private fun Detail(
    label: String,
    value: String,
    size: (Int) -> TextUnit,
) {
    Row {
        TvText(label, size = size(26), color = weatherPale, modifier = Modifier.width(220.dp))
        TvText(value, size = size(26), color = Color.White)
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.US)

@Composable
private fun ExtendedPage(
    report: WeatherReport,
    size: (Int) -> TextUnit,
    scale: Float,
) {
    Column(Modifier.fillMaxSize()) {
        TvText("EXTENDED FORECAST", size = size(22), color = weatherPale)
        Spacer(Modifier.height((12 * scale).dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
            report.daily.take(5).forEachIndexed { i, day ->
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0x22FFFFFF), RoundedCornerShape((10 * scale).dp))
                        .padding((10 * scale).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val name =
                        if (i ==
                            0
                        ) {
                            "TODAY"
                        } else {
                            runCatching { dayFormat.format(LocalDate.parse(day.date)).uppercase() }.getOrDefault(day.date)
                        }
                    TvText(name, size = size(28), bold = true, color = weatherYellow)
                    TvText(day.condition, size = size(22), color = Color.White)
                    TvText("Hi ${day.high.roundToInt()}", size = size(30), color = Color.White)
                    TvText("Lo ${day.low.roundToInt()}", size = size(30), color = weatherPale)
                    day.precipitationChance?.let { TvText("$it%", size = size(24), color = Color(0xFF7FD8FF)) }
                }
            }
        }
    }
}

private val hourFormat12 = DateTimeFormatter.ofPattern("h a", Locale.US)
private val hourFormat24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

@Composable
private fun HourlyPage(
    report: WeatherReport,
    size: (Int) -> TextUnit,
    scale: Float,
    clock24h: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        TvText("THE NEXT HOURS", size = size(22), color = weatherPale)
        Spacer(Modifier.height((12 * scale).dp))
        report.hourly.take(8).forEach { hour ->
            Row(Modifier.fillMaxWidth().padding(vertical = (4 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                val label =
                    runCatching {
                        (if (clock24h) hourFormat24 else hourFormat12).format(
                            LocalDateTime.parse(hour.time),
                        )
                    }.getOrDefault(hour.time)
                TvText(label, size = size(26), color = weatherYellow, modifier = Modifier.width((160 * scale).dp))
                TvText(
                    "${hour.temperature.roundToInt()}${report.temperatureUnit}",
                    size = size(26),
                    color = Color.White,
                    modifier =
                        Modifier.width(
                            (
                                140 *
                                    scale
                            ).dp,
                        ),
                )
                TvText(describe(hour.code), size = size(24), color = Color.White, modifier = Modifier.weight(1f))
                hour.precipitationChance?.let { TvText("$it%", size = size(24), color = Color(0xFF7FD8FF)) }
            }
        }
    }
}

private fun describe(code: Int): String =
    when (code) {
        0 -> "Sunny"
        1 -> "Mostly sunny"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        61, 63, 80, 81 -> "Rain"
        65, 82 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorms"
        else -> "--"
    }
