package com.github.damontecres.wholphin.tvmode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TracksTest {
    @Test
    fun autoShowsSubtitlesWhenTheAudioIsForeign() {
        assertTrue(TrackChoice.autoSubtitles("jpn", "eng"))
        assertTrue(TrackChoice.autoSubtitles("ja", "eng"))
        assertTrue(TrackChoice.autoSubtitles("fre", "eng"))
        assertTrue(TrackChoice.autoSubtitles("hin", "nep"))
    }

    @Test
    fun autoStaysOffWhenTheAudioIsMyLanguage_inAnyCodeSpelling() {
        assertFalse(TrackChoice.autoSubtitles("eng", "eng"))
        assertFalse(TrackChoice.autoSubtitles("en", "eng"))
        assertFalse(TrackChoice.autoSubtitles("eng", "en"))
        assertFalse(TrackChoice.autoSubtitles("ger", "deu"))
    }

    @Test
    fun autoStaysOffWhenTheAudioDoesntSayWhatLanguageItIs() {
        listOf(null, "", "und", "UND", "zxx").forEach {
            assertFalse("audio=$it", TrackChoice.autoSubtitles(it, "eng"))
        }
    }

    @Test
    fun languageNamesAreReadable() {
        assertEquals("Japanese", TrackChoice.languageName("jpn"))
        assertEquals("Japanese", TrackChoice.languageName("ja"))
        assertEquals("English", TrackChoice.languageName("eng"))
        assertEquals("French", TrackChoice.languageName("fre"))
        assertEquals("German", TrackChoice.languageName("ger"))
        assertEquals("Unknown", TrackChoice.languageName(null))
        assertEquals("Unknown", TrackChoice.languageName("und"))
        assertEquals("ZZZ", TrackChoice.languageName("zzz"))
    }

    @Test
    fun audioLabelsTellTracksApart() {
        assertEquals(
            "Japanese · 5.1 EAC3 · default",
            TrackChoice.audioLabel("jpn", null, 6, "audio/eac3", isDefault = true),
        )
        assertEquals(
            "English · Commentary · Stereo AAC",
            TrackChoice.audioLabel("eng", "Commentary", 2, "audio/mp4a-latm", isDefault = false),
        )
        // A title that only repeats the language adds nothing.
        assertEquals("Spanish · Stereo", TrackChoice.audioLabel("spa", "Spanish", 2, null, isDefault = false))
    }

    @Test
    fun subtitleLabelsShowForcedAndFiles() {
        assertEquals("English", TrackChoice.subtitleLabel("eng", null, forced = false, external = false, isDefault = false))
        assertEquals(
            "English · forced · file",
            TrackChoice.subtitleLabel("eng", null, forced = true, external = true, isDefault = false),
        )
    }

    @Test
    fun subtitleModesCycle() {
        assertEquals(SubtitleMode.ON, SubtitleMode.OFF.next())
        assertEquals(SubtitleMode.AUTO, SubtitleMode.ON.next())
        assertEquals(SubtitleMode.OFF, SubtitleMode.AUTO.next())
    }
}
