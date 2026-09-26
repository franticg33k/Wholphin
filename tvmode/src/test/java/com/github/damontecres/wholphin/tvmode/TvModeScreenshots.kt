package com.github.damontecres.wholphin.tvmode

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.damontecres.wholphin.tvmode.core.GuideEntry
import com.github.damontecres.wholphin.tvmode.core.ItemDetails
import com.github.damontecres.wholphin.tvmode.core.TvChannel
import com.github.damontecres.wholphin.tvmode.ui.BreakCard
import com.github.damontecres.wholphin.tvmode.ui.ClassicOverlay
import com.github.damontecres.wholphin.tvmode.ui.ColorBars
import com.github.damontecres.wholphin.tvmode.ui.CrtOverlay
import com.github.damontecres.wholphin.tvmode.ui.DigitOverlay
import com.github.damontecres.wholphin.tvmode.ui.FeaturePresentationCard
import com.github.damontecres.wholphin.tvmode.ui.FourThreeFrame
import com.github.damontecres.wholphin.tvmode.ui.GuideModel
import com.github.damontecres.wholphin.tvmode.ui.LineupOverlay
import com.github.damontecres.wholphin.tvmode.ui.LocalTvTheme
import com.github.damontecres.wholphin.tvmode.ui.OverlayData
import com.github.damontecres.wholphin.tvmode.ui.PausedScreensaver
import com.github.damontecres.wholphin.tvmode.ui.RatingBug
import com.github.damontecres.wholphin.tvmode.ui.RetroGuide
import com.github.damontecres.wholphin.tvmode.ui.SatelliteOverlay
import com.github.damontecres.wholphin.tvmode.ui.SearchSheet
import com.github.damontecres.wholphin.tvmode.ui.StillWatchingPrompt
import com.github.damontecres.wholphin.tvmode.ui.TuningSplash
import com.github.damontecres.wholphin.tvmode.ui.TvTheme
import com.github.damontecres.wholphin.tvmode.ui.UpNextCard
import com.github.damontecres.wholphin.tvmode.ui.Watermark
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the TV mode screens with sample data to `tvmode/build/screenshots/`, for checking layout changes without a
 * TV. Run with `./gradlew :tvmode:testDebugUnitTest --tests '*TvModeScreenshots*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-mdpi")
class TvModeScreenshots {
    @get:Rule
    val compose = createComposeRule()

    private val half = 30 * 60_000L
    private val windowStart = 1_790_000_000_000L - (1_790_000_000_000L % half)
    private val now = windowStart + 12 * 60_000L

    private val channels =
        listOf(
            TvChannel("ch-toons", "12", "Toon Town", null, "v", 40, "Kids"),
            TvChannel("ch-sitcoms", "201", "Sitcoms", null, "v", 80, "Comedy"),
            TvChannel("ch-movies", "300", "Movie Night", null, "v", 30, "Movies"),
            TvChannel("ch-drama", "205", "Drama TV", null, "v", 55),
            TvChannel("ch-90s", "409", "90s TV", null, "v", 70),
            TvChannel("ch-holiday", "600", "Holiday Classics", null, "v", 12),
        )

    private fun entry(
        id: String,
        title: String,
        startMin: Int,
        endMin: Int,
        episode: String? = null,
        episodeTitle: String? = null,
        movie: Boolean = false,
        year: Int? = null,
        rating: String? = null,
        premiere: Boolean = false,
    ) = GuideEntry(
        guideGroup = id,
        title = title,
        episode = episode,
        episodeTitle = episodeTitle,
        startMs = windowStart + startMin * 60_000L,
        endMs = windowStart + endMin * 60_000L,
        premiere = premiere,
        lineup = null,
        offAir = false,
        itemId = id,
        year = year,
        rating = rating,
        movie = movie,
    )

    private val rows =
        listOf(
            GuideRow(
                channels[0],
                listOf(
                    entry("a1", "Adventure Crew", -5, 25, "S2E8", "The Invisible Madman", rating = "TV-Y7"),
                    entry("a2", "Space Pals", 25, 55, "S1E49", "Did You Myth Me?", rating = "TV-Y7"),
                    entry("a3", "Adventure Crew", 55, 85, "S2E9", "Lost and Found", rating = "TV-Y7"),
                    entry("a4", "Space Pals", 85, 125, "S1E50", "Moon Pie", rating = "TV-Y7"),
                ),
            ),
            GuideRow(
                channels[1],
                listOf(
                    entry("b1", "The Apartment Years", 0, 30, "S5E12", "The One With the Lease", rating = "TV-PG"),
                    entry("b2", "Office Hours", 30, 60, "S3E2", "Casual Friday", rating = "TV-14", premiere = true),
                    entry("b3", "The Apartment Years", 60, 90, "S5E13", "Rent Day", rating = "TV-PG"),
                    entry("b4", "Office Hours", 90, 120, "S3E3", "Fire Drill", rating = "TV-14"),
                ),
            ),
            GuideRow(
                channels[2],
                listOf(
                    entry("c1", "Starship Harbor", -40, 76, movie = true, year = 1994, rating = "PG-13"),
                    entry("c2", "The Long Night", 76, 190, movie = true, year = 2001, rating = "R"),
                ),
            ),
            GuideRow(
                channels[3],
                listOf(
                    entry("d1", "Harbor Point", 0, 60, "S1E10", "Primates", rating = "TV-14"),
                    entry("d2", "County General", 60, 120, "S4E1", "Night Shift", rating = "TV-14"),
                ),
            ),
            GuideRow(
                channels[4],
                listOf(
                    entry("e1", "Mall Rats", 0, 30, "S2E3", "Food Court", rating = "TV-PG"),
                    entry("e2", "Pager Days", 30, 60, "S1E7", "Beep Beep", rating = "TV-PG"),
                    entry("e3", "Mall Rats", 60, 90, "S2E4", "Arcade", rating = "TV-PG"),
                    entry("e4", "Pager Days", 90, 120, "S1E8", "Call Waiting", rating = "TV-PG"),
                ),
            ),
            GuideRow(
                channels[5],
                listOf(
                    entry("f1", "A Snowy Christmas", -20, 70, movie = true, year = 1988, rating = "G"),
                    entry("f2", "Elf Academy", 70, 160, movie = true, year = 2003, rating = "PG"),
                ),
            ),
        )

    private val details =
        mapOf(
            "d1" to
                ItemDetails(
                    itemId = "d1",
                    overview =
                        "When a crate of escaped monkeys turns up at the docks, the harbour patrol has one night to round them up " +
                            "before the storm hits.",
                    officialRating = "TV-14",
                    communityRating = 8.4f,
                    year = 2009,
                    airDate = "2009-12-13",
                    runtimeMinutes = 59,
                    quality = "HD",
                    videoCodec = "H.264",
                    audio = "AAC Stereo",
                    logoItemId = null,
                    backdropItemId = null,
                ),
        )

    private val scene = mutableStateOf<@Composable () -> Unit>({})
    private val currentTheme = mutableStateOf(TvThemeId.RETRO)
    private var started = false

    private fun render(
        name: String,
        theme: TvThemeId = TvThemeId.RETRO,
        content: @Composable () -> Unit,
    ) {
        // The rule allows one setContent per test, so later renders swap the scene through state.
        currentTheme.value = theme
        scene.value = content
        if (!started) {
            started = true
            compose.setContent {
                CompositionLocalProvider(LocalTvTheme provides TvTheme.of(currentTheme.value)) {
                    Box(Modifier.fillMaxSize()) { scene.value() }
                }
            }
        }
        compose.waitForIdle()
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun guideModel(settings: TvModeSettings) =
        GuideModel(
            rows = rows,
            categories = listOf("Kids", "Comedy", "Movies"),
            category = null,
            current = channels[3],
            windowStartMs = windowStart,
            nowMs = now,
            clock = mutableStateOf(now),
            details = mutableStateOf(details),
            settings = settings,
            images = { _, _, _ -> "" },
            onCategory = {},
            onTune = {},
            onShift = { false },
            onFocusItem = {},
            onFullscreen = {},
            onSettings = {},
            onExit = {},
        )

    @Test
    fun guides() {
        for (theme in TvThemeId.entries) {
            render("guide-${theme.name.lowercase()}", theme) {
                RetroGuide(guideModel(TvModeSettings(theme = theme)), preview = { ColorBars(Modifier.fillMaxSize()) })
            }
        }
    }

    @Test
    fun search() {
        render("guide-search") {
            RetroGuide(guideModel(TvModeSettings()), preview = { ColorBars(Modifier.fillMaxSize()) })
            SearchSheet(rows, now, onTune = {})
        }
    }

    @Test
    fun overlays() {
        val data =
            OverlayData(
                channel = channels[3],
                now = rows[3].entries[0],
                next = rows[3].entries[1],
                upcoming = rows[4].entries.take(3),
                details = mutableStateOf(details),
                nowMs = now,
                clock = mutableStateOf(now),
                clock24h = false,
                showMediaInfo = true,
                images = { _, _, _ -> "" },
            )
        render("overlay-classic") {
            ColorBars(Modifier.fillMaxSize())
            ClassicOverlay(data)
        }
        render("overlay-lineup") {
            ColorBars(Modifier.fillMaxSize())
            LineupOverlay(data)
            DigitOverlay("20", 3, false)
        }
        render("overlay-satellite") {
            ColorBars(Modifier.fillMaxSize())
            SatelliteOverlay(data)
        }
        render("extras-watermark-rating-upnext") {
            ColorBars(Modifier.fillMaxSize())
            val logoChannel = channels[3]
            Watermark(logoChannel, WatermarkMode.ALWAYS, Corner.TOP_RIGHT, 70, mutableStateOf(0L))
            RatingBug(ProgramStart(rows[3].entries[0], now - 1_000), mutableStateOf(now))
            UpNextCard(rows[3].entries[0].copy(endMs = now + 40_000), rows[3].entries[1], mutableStateOf(now), false)
        }
        render("extras-feature") {
            ColorBars(Modifier.fillMaxSize())
            FeaturePresentationCard(ProgramStart(rows[2].entries[0], now - 500), mutableStateOf(now))
        }
        render("extras-break") { BreakCard(channels[3], now + 95_000, rows[3].entries[1], mutableStateOf(now), Modifier.fillMaxSize()) }
        render("extras-bezel") {
            FourThreeFrame(bezel = true) { ColorBars(Modifier.fillMaxSize()) }
            CrtOverlay(Scanlines.MEDIUM, vignette = false)
        }
        render("extras-paused") {
            ColorBars(Modifier.fillMaxSize())
            PausedScreensaver(channels[3])
        }
        render("extras-still-watching") {
            ColorBars(Modifier.fillMaxSize())
            StillWatchingPrompt(now + 42_000, mutableStateOf(now))
        }
        render("tuning-static") { TuningSplash(TuningStyle.STATIC, channels[3], true, false, Modifier.fillMaxSize()) }
        render("tuning-standby") { TuningSplash(TuningStyle.STANDBY, channels[3], true, false, Modifier.fillMaxSize()) }
    }
}
