package com.github.damontecres.wholphin.tvmode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTest {
    @Test
    fun foldsBreaksIntoTheirProgramme() {
        val entries =
            Guide.entries(
                listOf(
                    slot("a", 0.0, 11.0, group = "g1"),
                    slot("ad", 11.0, 12.0, SlotKind.COMMERCIAL, group = "g1"),
                    slot("a2", 12.0, 23.0, group = "g1"),
                    slot("f", 23.0, 30.0, SlotKind.FILLER, group = "g1"),
                    slot("off", 30.0, 90.0, SlotKind.FILLER, group = "g2"),
                ),
            )

        assertEquals(2, entries.size)
        assertEquals("Show a", entries[0].title)
        assertEquals(T0, entries[0].startMs)
        assertEquals(T0 + 30 * MIN, entries[0].endMs)
        assertTrue(entries[1].offAir)
        assertEquals("Off air", entries[1].title)

        val (now, next) = Guide.nowAndNext(entries, T0 + 15 * MIN)
        assertEquals("g1", now?.guideGroup)
        assertEquals("g2", next?.guideGroup)
    }
}
