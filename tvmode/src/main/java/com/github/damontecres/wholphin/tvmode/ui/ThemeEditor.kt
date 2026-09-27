package com.github.damontecres.wholphin.tvmode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.damontecres.wholphin.tvmode.CustomTheme
import com.github.damontecres.wholphin.tvmode.TvThemeId

/** A colour role the editor can change, with how to read and replace it on a [TvTheme]. */
class ThemeRole(
    val key: String,
    val label: String,
    val get: (TvTheme) -> Color,
    val set: (TvTheme, Color) -> TvTheme,
)

/** Every editable colour role, grouped as the editor lists them. */
object ThemeRoles {
    val all: List<ThemeRole> =
        listOf(
            ThemeRole("background", "Background", { it.background }) { t, c -> t.copy(background = c) },
            ThemeRole("topBar", "Top bar", { it.topBar }) { t, c -> t.copy(topBar = c) },
            ThemeRole("panel", "Info panel", { it.panel }) { t, c -> t.copy(panel = c) },
            ThemeRole("accent", "Main colour (accent)", { it.accent }) { t, c -> t.copy(accent = c) },
            ThemeRole("nowLine", "Time indicator", { it.nowLine }) { t, c -> t.copy(nowLine = c) },
            ThemeRole("divider", "Dividers", { it.divider }) { t, c -> t.copy(divider = c) },
            ThemeRole("chipBorder", "Chip borders", { it.chipBorder }) { t, c -> t.copy(chipBorder = c) },
            ThemeRole("channelCell", "Channel cell", { it.channelCell }) { t, c -> t.copy(channelCell = c) },
            ThemeRole("channelCellFocused", "Focused channel", { it.channelCellFocused }) { t, c -> t.copy(channelCellFocused = c) },
            ThemeRole("channelNumber", "Channel number", { it.channelNumber }) { t, c -> t.copy(channelNumber = c) },
            ThemeRole("channelName", "Channel name", { it.channelName }) { t, c -> t.copy(channelName = c) },
            ThemeRole("timeRow", "Time row", { it.timeRow }) { t, c -> t.copy(timeRow = c) },
            ThemeRole("timeRowText", "Time row text", { it.timeRowText }) { t, c -> t.copy(timeRowText = c) },
            ThemeRole("showCell", "TV show cell", { it.showCell }) { t, c ->
                t.copy(
                    showCell = c,
                    programCells =
                        listOf(c) + t.programCells.drop(1),
                )
            },
            ThemeRole("movieCell", "Movie cell", { it.movieCell }) { t, c -> t.copy(movieCell = c) },
            ThemeRole("kidsCell", "Kids cell", { it.kidsCell }) { t, c -> t.copy(kidsCell = c) },
            ThemeRole("offAirCell", "Off-air cell", { it.offAirCell }) { t, c -> t.copy(offAirCell = c) },
            ThemeRole("focusedCell", "Focused programme", { it.focusedCell }) { t, c -> t.copy(focusedCell = c) },
            ThemeRole("focusedText", "Focused programme text", { it.focusedText }) { t, c -> t.copy(focusedText = c) },
            ThemeRole("text", "Text", { it.text }) { t, c -> t.copy(text = c) },
            ThemeRole("textSecondary", "Secondary text", { it.textSecondary }) { t, c -> t.copy(textSecondary = c) },
        )

    private val byKey = all.associateBy { it.key }

    /** [base] with the custom theme's colours applied. */
    fun apply(
        base: TvTheme,
        colors: Map<String, Long>,
    ): TvTheme = colors.entries.fold(base) { theme, (key, argb) -> byKey[key]?.set?.invoke(theme, Color(argb)) ?: theme }
}

/** Named colours for the swatch picker. */
val SWATCHES: List<Pair<String, Long>> =
    listOf(
        "White" to 0xFFFFFFFF,
        "Silver" to 0xFFC0C0C0,
        "Grey" to 0xFF808080,
        "Charcoal" to 0xFF36454F,
        "Black" to 0xFF000000,
        "Midnight" to 0xFF101014,
        "Navy" to 0xFF0B1E4A,
        "Retro Navy" to 0xFF1B3A55,
        "Deep Navy" to 0xFF02203F,
        "Royal Blue" to 0xFF2C4F9E,
        "Steel Blue" to 0xFF4B6C8B,
        "Sky Blue" to 0xFF7FD8FF,
        "Cyan" to 0xFF5BC0DE,
        "Teal" to 0xFF5B8A8A,
        "Dark Teal" to 0xFF0F4C4C,
        "Sea Green" to 0xFF2E8B57,
        "Green" to 0xFF5B8A5B,
        "Phosphor Green" to 0xFF43FF1E,
        "Lime" to 0xFF9ACD32,
        "Olive" to 0xFF6B6B2A,
        "Gold" to 0xFFE9C648,
        "Yellow" to 0xFFFFE63C,
        "Pale Yellow" to 0xFFF5E5A2,
        "Amber" to 0xFFFFA726,
        "Orange" to 0xFFFF9F43,
        "Burnt Orange" to 0xFFCC5500,
        "Coral" to 0xFFFF7F50,
        "Red" to 0xFFFF5A5F,
        "Crimson" to 0xFFB0102E,
        "Maroon" to 0xFF6B1E2E,
        "Pink" to 0xFFFF69B4,
        "Magenta" to 0xFFE05BD0,
        "Purple" to 0xFF7B5B8B,
        "Plum" to 0xFF5E3A63,
        "Indigo" to 0xFF5C60EE,
        "Lavender" to 0xFFB9A7E8,
        "Brown" to 0xFF6B4226,
        "Saddle Brown" to 0xFF8B4513,
        "Tan" to 0xFFD4A873,
        "Cream" to 0xFFF5E6D0,
        "Slate" to 0xFF455667,
        "Dark Slate" to 0xFF1C2833,
        "Pastel Blue" to 0xFFA7C7E7,
        "Pastel Green" to 0xFFB5E3B5,
        "Pastel Pink" to 0xFFF4C2C2,
    )

/**
 * The theme editor: pick or create a custom theme (a copy of a built-in one), rename it, change any colour role from
 * the swatches or a hex code, and use it.
 */
@Composable
fun ThemeEditorDialog(
    themes: List<CustomTheme>,
    currentBase: TvThemeId,
    nextId: () -> Int,
    onSave: (CustomTheme) -> Unit,
    onDelete: (Int) -> Unit,
    onUse: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf(themes.firstOrNull()) }
    var picking by remember { mutableStateOf<ThemeRole?>(null) }
    val first = remember { FocusRequester() }
    val working = editing
    val preview = working?.let { ThemeRoles.apply(TvTheme.of(it.base), it.colors) } ?: LocalTvTheme.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(
            Modifier
                .width(900.dp)
                .height(520.dp)
                .background(preview.panel, RoundedCornerShape(8.dp))
                .border(1.dp, preview.divider, RoundedCornerShape(8.dp))
                .padding(16.dp),
        ) {
            Column(Modifier.width(230.dp).fillMaxHeight()) {
                TvText("THEMES", size = 16.sp, bold = true, color = preview.accent)
                Spacer(Modifier.height(8.dp))
                EditorButton("+ New from current", preview, Modifier.focusRequester(first)) {
                    val theme = CustomTheme(nextId(), "Custom ${themes.size + 1}", currentBase, emptyMap())
                    onSave(theme)
                    editing = theme
                }
                LazyColumn {
                    items(themes, key = { it.id }) { theme ->
                        EditorButton(theme.name + if (theme.id == working?.id) "  ◂" else "", preview) { editing = theme }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (working == null) {
                    TvText("Make a theme from the current one to start.", color = preview.textSecondary)
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextEntry(working.name, preview, Modifier.weight(1f)) { name ->
                        val updated = working.copy(name = name.ifBlank { "Custom" })
                        editing = updated
                        onSave(updated)
                    }
                    Spacer(Modifier.width(8.dp))
                    EditorButton("Base: ${working.base.label}", preview) {
                        val updated = working.copy(base = TvThemeId.entries[(working.base.ordinal + 1) % TvThemeId.entries.size])
                        editing = updated
                        onSave(updated)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f)) {
                    items(ThemeRoles.all, key = { it.key }) { role ->
                        RoleRow(role, role.get(preview), working.colors.containsKey(role.key), preview) { picking = role }
                    }
                }
                Row {
                    EditorButton("Use this theme", preview) { onUse(working.id) }
                    Spacer(Modifier.width(8.dp))
                    EditorButton("Reset colours", preview) {
                        val updated = working.copy(colors = emptyMap())
                        editing = updated
                        onSave(updated)
                    }
                    Spacer(Modifier.width(8.dp))
                    EditorButton("Delete", preview) {
                        onDelete(working.id)
                        editing = themes.firstOrNull { it.id != working.id }
                    }
                }
            }
        }
    }
    picking?.let { role ->
        SwatchPicker(role, role.get(preview), preview, onPick = { color ->
            val updated = working!!.copy(colors = working.colors + (role.key to (color.toArgb().toLong() and 0xFFFFFFFFL)))
            editing = updated
            onSave(updated)
            picking = null
        }, onDismiss = { picking = null })
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun RoleRow(
    role: ThemeRole,
    color: Color,
    changed: Boolean,
    theme: TvTheme,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .background(if (focused) theme.focusedCell else Color.Transparent, RoundedCornerShape(4.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(color, RoundedCornerShape(3.dp))
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(8.dp))
        TvText(role.label + if (changed) " •" else "", size = 13.sp, color = if (focused) theme.focusedText else theme.text)
    }
}

@Composable
private fun SwatchPicker(
    role: ThemeRole,
    current: Color,
    theme: TvTheme,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(620.dp)
                .background(theme.panel, RoundedCornerShape(8.dp))
                .border(1.dp, theme.divider, RoundedCornerShape(8.dp))
                .padding(16.dp),
        ) {
            TvText(role.label.uppercase(), size = 16.sp, bold = true, color = theme.accent)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(9), modifier = Modifier.height(260.dp)) {
                items(SWATCHES, key = { it.first }) { (name, argb) ->
                    Swatch(
                        name,
                        Color(argb),
                        theme,
                        if (Color(argb) ==
                            current
                        ) {
                            Modifier.focusRequester(first)
                        } else {
                            Modifier
                        },
                    ) { onPick(Color(argb)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            val hex = String.format("#%06X", current.toArgb() and 0xFFFFFF)
            TextEntry(hex, theme, Modifier.fillMaxWidth(), label = "Hex (#RRGGBB or #AARRGGBB), then OK") { text ->
                parseHex(text)?.let(onPick)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private fun parseHex(text: String): Color? {
    val digits = text.trim().removePrefix("#")
    val value = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> null
    }
}

@Composable
private fun Swatch(
    name: String,
    color: Color,
    theme: TvTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .padding(3.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(color, RoundedCornerShape(4.dp))
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) theme.accent else Color.White.copy(alpha = 0.4f),
                    RoundedCornerShape(4.dp),
                ),
        )
        TvText(if (focused) name else "", size = 9.sp, color = theme.text)
    }
}

@Composable
private fun EditorButton(
    label: String,
    theme: TvTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    TvText(
        label,
        size = 13.sp,
        bold = true,
        color = if (focused) theme.focusedText else theme.text,
        modifier =
            modifier
                .padding(vertical = 2.dp)
                .background(if (focused) theme.focusedCell else theme.channelCell, RoundedCornerShape(4.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A text field that reports its value when focus leaves it (typing on a TV remote is slow; no need to react per key). */
@Composable
private fun TextEntry(
    value: String,
    theme: TvTheme,
    modifier: Modifier = Modifier,
    label: String? = null,
    onDone: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        label?.let { TvText(it, size = 11.sp, color = theme.textSecondary) }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = theme.text, fontSize = 16.sp, fontFamily = theme.font),
            cursorBrush = SolidColor(theme.accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (focused && !it.isFocused && text != value) onDone(text)
                        focused = it.isFocused
                    }.background(theme.channelCell, RoundedCornerShape(4.dp))
                    .border(1.dp, if (focused) theme.accent else theme.chipBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
