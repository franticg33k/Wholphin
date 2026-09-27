package com.github.damontecres.wholphin.tvmode.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTest {
    @Test
    fun languageCodesMatchAcrossSpellings() {
        assertTrue(SubtitleLanguages.matches("eng", "en"))
        assertTrue(SubtitleLanguages.matches("en", "eng"))
        assertTrue(SubtitleLanguages.matches("ger", "de"))
        assertTrue(SubtitleLanguages.matches("fre", "fra"))
        assertFalse(SubtitleLanguages.matches("jpn", "eng"))
        assertFalse(SubtitleLanguages.matches(null, "eng"))
    }

    @Test
    fun detailsListSubtitleTracks() {
        val json =
            """
            {"Id":"a","MediaStreams":[
              {"Type":"Video","Codec":"h264","Index":0},
              {"Type":"Subtitle","Codec":"subrip","Language":"eng","DisplayTitle":"English","Index":2,"IsDefault":true},
              {"Type":"Subtitle","Codec":"PGSSUB","Language":"jpn","Index":3},
              {"Type":"Subtitle","Codec":"ass","Language":"eng","Index":4,"IsExternal":true,"IsForced":true}
            ]}
            """.trimIndent()
        val details = Json { ignoreUnknownKeys = true }.decodeFromString<JellyfinItemDto>(json).toDetails()

        assertEquals(listOf(2, 3, 4), details.subtitles.map { it.index })
        val ass = details.subtitles.last()
        assertTrue(ass.external && ass.forced && ass.loadable)
        assertEquals("ass", ass.format)
        assertEquals("vtt", details.subtitles.first().format)
        assertFalse(details.subtitles[1].loadable)
    }
}
