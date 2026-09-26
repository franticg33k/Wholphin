package com.github.damontecres.wholphin.tvmode.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.github.damontecres.wholphin.tvmode.R
import com.github.damontecres.wholphin.tvmode.TvThemeId
import com.github.damontecres.wholphin.tvmode.core.ContentType

/** Colour roles for the TV mode, and the font the guide uses. */
data class TvTheme(
    val background: Color,
    val topBar: Color,
    val panel: Color,
    val channelCell: Color,
    val channelCellFocused: Color,
    val channelNumber: Color,
    val channelName: Color,
    val timeRow: Color,
    val timeRowText: Color,
    val divider: Color,
    val programCells: List<Color>,
    val movieCell: Color,
    val showCell: Color,
    val kidsCell: Color,
    val offAirCell: Color,
    val focusedCell: Color,
    val focusedText: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val nowLine: Color,
    val chipBorder: Color,
    val overlayScrim: Color,
    val font: FontFamily,
    val roundedCells: Boolean = true,
) {
    /** This theme with the viewer's accent and focus colours, when they chose any. */
    fun customized(
        accent: Long?,
        focus: Long?,
    ): TvTheme {
        var theme = this
        if (accent != null) theme = theme.copy(accent = Color(accent), nowLine = Color(accent))
        if (focus != null) {
            val color = Color(focus)
            // Dark text on light focus colours, white on dark ones.
            val light = color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f > 0.6f
            theme = theme.copy(focusedCell = color, focusedText = if (light) Color(0xFF14202C) else Color.White)
        }
        return theme
    }

    fun cellColor(
        type: ContentType,
        index: Int,
        byType: Boolean,
    ): Color =
        when {
            type == ContentType.OFF_AIR -> offAirCell
            !byType -> programCells[Math.floorMod(index, programCells.size)]
            type == ContentType.MOVIE -> movieCell
            type == ContentType.KIDS -> kidsCell
            else -> showCell
        }

    companion object {
        private val mono = FontFamily(Font(R.font.share_tech_mono))

        fun of(id: TvThemeId): TvTheme =
            when (id) {
                TvThemeId.RETRO -> retro
                TvThemeId.MIDNIGHT -> midnight
                TvThemeId.PHOSPHOR -> phosphor
                TvThemeId.MODERN -> modern
            }

        // Navy-and-steel cable guide with pastel cells and a yellow focus.
        private val retro =
            TvTheme(
                background = Color(0xFF1B3A55),
                topBar = Color(0xFF0E2538),
                panel = Color(0xFF15304A),
                channelCell = Color(0xFF02203F),
                channelCellFocused = Color(0xFF3E5F55),
                channelNumber = Color(0xFFFFFFFF),
                channelName = Color(0xFFB9C6D6),
                timeRow = Color(0xFF0E2538),
                timeRowText = Color(0xFFE6EDF5),
                divider = Color(0xFF455667),
                programCells = listOf(Color(0xFF4B6C8B), Color(0xFF5B8A5B), Color(0xFF7B5B8B), Color(0xFF5B8A8A), Color(0xFF8B5B5B)),
                movieCell = Color(0xFF7B5B8B),
                showCell = Color(0xFF4B6C8B),
                kidsCell = Color(0xFF5B8A5B),
                offAirCell = Color(0xFF2B3B4B),
                focusedCell = Color(0xFFF5E5A2),
                focusedText = Color(0xFF14202C),
                text = Color.White,
                textSecondary = Color(0xFFC7D2DF),
                accent = Color(0xFFE9C648),
                nowLine = Color(0xFFE9C648),
                chipBorder = Color(0xFF9FB2C6),
                overlayScrim = Color(0xCC061422),
                font = mono,
            )

        // Near-black glass with an indigo accent.
        private val midnight =
            TvTheme(
                background = Color(0xFF101014),
                topBar = Color(0xFF17171E),
                panel = Color(0xFF15151B),
                channelCell = Color(0xFF1C1C25),
                channelCellFocused = Color(0xFF2E3170),
                channelNumber = Color.White,
                channelName = Color(0xFFA7A9BE),
                timeRow = Color(0xFF17171E),
                timeRowText = Color(0xFFD9DAE8),
                divider = Color(0xFF2C2C3A),
                programCells = listOf(Color(0xFF242431), Color(0xFF2A2A39), Color(0xFF262635), Color(0xFF2D2D3D)),
                movieCell = Color(0xFF30284A),
                showCell = Color(0xFF232838),
                kidsCell = Color(0xFF203A33),
                offAirCell = Color(0xFF18181F),
                focusedCell = Color(0xFF5C60EE),
                focusedText = Color.White,
                text = Color.White,
                textSecondary = Color(0xFFA7A9BE),
                accent = Color(0xFF8A8DF5),
                nowLine = Color(0xFF8A8DF5),
                chipBorder = Color(0xFF4A4B66),
                overlayScrim = Color(0xD90B0B10),
                font = FontFamily.SansSerif,
            )

        // Green phosphor terminal.
        private val phosphor =
            TvTheme(
                background = Color(0xFF030603),
                topBar = Color(0xFF071007),
                panel = Color(0xFF050B05),
                channelCell = Color(0xFF081208),
                channelCellFocused = Color(0xFF12361A),
                channelNumber = Color(0xFF43FF1E),
                channelName = Color(0xFF7DDC7D),
                timeRow = Color(0xFF071007),
                timeRowText = Color(0xFF43FF1E),
                divider = Color(0xFF1B4A1B),
                programCells = listOf(Color(0xFF0B1E0B), Color(0xFF0E260E)),
                movieCell = Color(0xFF12301A),
                showCell = Color(0xFF0B1E0B),
                kidsCell = Color(0xFF16361A),
                offAirCell = Color(0xFF050B05),
                focusedCell = Color(0xFF43FF1E),
                focusedText = Color(0xFF021002),
                text = Color(0xFF7DFF7D),
                textSecondary = Color(0xFF4DB34D),
                accent = Color(0xFF43FF1E),
                nowLine = Color(0xFF43FF1E),
                chipBorder = Color(0xFF2E7A2E),
                overlayScrim = Color(0xE0010401),
                font = mono,
                roundedCells = false,
            )

        // Material dark greys with a blue accent.
        private val modern =
            TvTheme(
                background = Color(0xFF121212),
                topBar = Color(0xFF1B1B1B),
                panel = Color(0xFF181818),
                channelCell = Color(0xFF1F1F1F),
                channelCellFocused = Color(0xFF0B5F86),
                channelNumber = Color.White,
                channelName = Color(0xFFB0B0B0),
                timeRow = Color(0xFF1B1B1B),
                timeRowText = Color(0xFFE0E0E0),
                divider = Color(0xFF333333),
                programCells = listOf(Color(0xFF2A2A2A), Color(0xFF303030)),
                movieCell = Color(0xFF2D2A38),
                showCell = Color(0xFF26303A),
                kidsCell = Color(0xFF263A2E),
                offAirCell = Color(0xFF1A1A1A),
                focusedCell = Color(0xFF14A6E0),
                focusedText = Color.White,
                text = Color.White,
                textSecondary = Color(0xFFB0B0B0),
                accent = Color(0xFF14A6E0),
                nowLine = Color(0xFF14A6E0),
                chipBorder = Color(0xFF555555),
                overlayScrim = Color(0xD9101010),
                font = FontFamily.SansSerif,
            )
    }
}

val LocalTvTheme = staticCompositionLocalOf { TvTheme.of(TvThemeId.RETRO) }
