package com.github.damontecres.wholphin.tvmode.core

import java.util.Locale

/** When subtitles show. */
enum class SubtitleMode(
    val label: String,
) {
    OFF("Off"),
    ON("On"),

    /** On when the soundtrack isn't in the viewer's language (foreign films, anime), off otherwise. */
    AUTO("Auto: when the audio isn't my language"),
    ;

    fun next(): SubtitleMode = entries[(ordinal + 1) % entries.size]
}

/** Choosing audio and subtitle tracks, and naming them. Pure, so it can be tested without a player. */
object TrackChoice {
    private val UNKNOWN = setOf("", "und", "zxx", "mis", "mul")

    /**
     * Whether Auto subtitles should show: when the audio plays in a language other than [wanted]. An audio track that
     * doesn't say its language gives no reason to show them.
     */
    fun autoSubtitles(
        audioLanguage: String?,
        wanted: String,
    ): Boolean {
        val audio = audioLanguage?.trim()?.lowercase().orEmpty()
        if (audio in UNKNOWN) return false
        return !SubtitleLanguages.matches(audio, wanted)
    }

    /** "Japanese" for "jpn" or "ja"; the upper-cased code when it isn't known; "Unknown" for none. */
    fun languageName(code: String?): String {
        val c = code?.trim().orEmpty()
        if (c.lowercase() in UNKNOWN) return "Unknown"
        for (variant in SubtitleLanguages.variants(c).sortedBy { it.length }) {
            val name = Locale(variant).getDisplayLanguage(Locale.ENGLISH)
            if (name.isNotBlank() && !name.equals(variant, ignoreCase = true)) return name
        }
        return c.uppercase()
    }

    /** A short codec name from a MIME type: "audio/eac3" is "EAC3". */
    fun codecName(mime: String?): String? =
        when {
            mime == null -> null
            "eac3" in mime -> "EAC3"
            "ac3" in mime -> "AC3"
            "true-hd" in mime || "truehd" in mime -> "TrueHD"
            "dts" in mime -> "DTS"
            "mp4a" in mime || "aac" in mime -> "AAC"
            "opus" in mime -> "Opus"
            "vorbis" in mime -> "Vorbis"
            "flac" in mime -> "FLAC"
            "mpeg" in mime -> "MP3"
            else -> null
        }

    /** "5.1" for 6 channels, "Stereo", "Mono". */
    fun channelLayout(channels: Int): String? =
        when (channels) {
            0, -1 -> null
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "$channels ch"
        }

    fun audioLabel(
        language: String?,
        title: String?,
        channels: Int,
        mime: String?,
        isDefault: Boolean,
    ): String {
        val extra = listOfNotNull(channelLayout(channels), codecName(mime))
        return listOfNotNull(
            languageName(language),
            title?.takeIf { it.isNotBlank() && !it.equals(languageName(language), ignoreCase = true) },
            extra.joinToString(" ").ifBlank { null },
            "default".takeIf { isDefault },
        ).joinToString(" · ")
    }

    fun subtitleLabel(
        language: String?,
        title: String?,
        forced: Boolean,
        external: Boolean,
        isDefault: Boolean,
    ): String =
        listOfNotNull(
            languageName(language),
            title?.takeIf { it.isNotBlank() && !it.equals(languageName(language), ignoreCase = true) },
            "forced".takeIf { forced },
            "file".takeIf { external },
            "default".takeIf { isDefault },
        ).joinToString(" · ")
}

/** Languages for the audio setting: "" follows the file's own default, "device" the device's language. */
val AUDIO_LANGUAGES: List<Pair<String, String>> =
    listOf("" to "File default (usually the original)", "device" to "Device language") +
        listOf(
            "eng" to "English",
            "jpn" to "Japanese",
            "kor" to "Korean",
            "spa" to "Spanish",
            "fre" to "French",
            "ger" to "German",
            "ita" to "Italian",
            "por" to "Portuguese",
            "chi" to "Chinese",
            "hin" to "Hindi",
            "nep" to "Nepali",
            "rus" to "Russian",
            "ara" to "Arabic",
        )
