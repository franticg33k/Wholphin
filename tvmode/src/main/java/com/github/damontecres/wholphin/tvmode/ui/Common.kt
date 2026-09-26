package com.github.damontecres.wholphin.tvmode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.tvmode.core.GuideEntry
import com.github.damontecres.wholphin.tvmode.core.ItemDetails
import com.github.damontecres.wholphin.tvmode.core.TvChannel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds image URLs; the view model forwards to the app. */
fun interface ImageUrls {
    fun url(
        itemId: String,
        type: String,
        maxHeight: Int,
    ): String
}

private val time12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val time24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val clock12 = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)
private val clock24 = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
private val airDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

fun formatTime(
    ms: Long,
    h24: Boolean,
): String = (if (h24) time24 else time12).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun formatClock(
    ms: Long,
    h24: Boolean,
): String = (if (h24) clock24 else clock12).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun formatRuntime(minutes: Int): String = if (minutes < 60) "$minutes min" else "${minutes / 60} hr ${minutes % 60} min"

fun formatAirDate(date: String?): String? = date?.let { runCatching { airDateFormat.format(LocalDate.parse(it)) }.getOrNull() }

/** "S1E10 - Primates", or the movie's year. */
fun episodeLine(entry: GuideEntry): String? =
    when {
        entry.episode != null && entry.episodeTitle != null -> "${entry.episode} - ${entry.episodeTitle}"
        entry.episode != null -> entry.episode
        entry.episodeTitle != null -> entry.episodeTitle
        entry.movie && entry.year != null -> entry.year.toString()
        else -> null
    }

/** Title as the guide shows it: movies carry their year. */
fun guideTitle(entry: GuideEntry): String =
    if (entry.movie && entry.year != null && !entry.title.contains("(${entry.year})")) "${entry.title} (${entry.year})" else entry.title

@Composable
fun TvText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalTvTheme.current.text,
    size: TextUnit = 16.sp,
    bold: Boolean = false,
    maxLines: Int = 1,
    style: TextStyle = TextStyle.Default,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontFamily = LocalTvTheme.current.font,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

/** An outlined metadata chip ("TV-PG", "HD", "59 min"), or a filled one. */
@Composable
fun MetaChip(
    text: String,
    filled: Boolean = false,
    color: Color = LocalTvTheme.current.chipBorder,
) {
    val theme = LocalTvTheme.current
    val shape = RoundedCornerShape(3.dp)
    TvText(
        text = text,
        size = 12.sp,
        color = if (filled) theme.focusedText else theme.text,
        modifier =
            Modifier
                .then(if (filled) Modifier.background(theme.accent, shape) else Modifier.border(1.dp, color, shape))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Chips for a programme: air date, rating, quality, audio, runtime, NEW, community rating. */
fun metaChips(
    entry: GuideEntry,
    details: ItemDetails?,
): List<Pair<String, Boolean>> =
    buildList {
        formatAirDate(details?.airDate)?.let { add(it to false) }
        (details?.officialRating ?: entry.rating)?.let { add(it to false) }
        details?.quality?.let { add(it to false) }
        details?.audio?.let { add(it to false) }
        details?.runtimeMinutes?.let { add(formatRuntime(it) to false) }
        if (entry.premiere) add("NEW" to false)
        details?.communityRating?.let { add(String.format(Locale.US, "★ %.1f", it) to true) }
    }

/** A channel's logo from the plugin; nothing when it has none. */
@Composable
fun ChannelLogo(
    channel: TvChannel?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val url = channel?.logoUrl ?: return
    AsyncImage(model = url, contentDescription = channel.name, contentScale = ContentScale.Fit, modifier = modifier, alpha = alpha)
}
