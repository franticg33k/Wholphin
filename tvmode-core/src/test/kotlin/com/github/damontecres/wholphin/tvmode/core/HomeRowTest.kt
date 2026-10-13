package com.github.damontecres.wholphin.tvmode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowTest {
    private fun entry(
        title: String,
        startMin: Int,
        endMin: Int,
        offAir: Boolean = false,
        episode: String? = null,
        episodeTitle: String? = null,
    ) = GuideEntry(
        guideGroup = "g-$title-$startMin",
        title = title,
        episode = episode,
        episodeTitle = episodeTitle,
        startMs = T0 + startMin * MIN,
        endMs = T0 + endMin * MIN,
        premiere = false,
        lineup = null,
        offAir = offAir,
        itemId = if (offAir) null else "item-$title",
    )

    private fun guide(
        channel: String,
        vararg entries: GuideEntry,
    ) = ChannelGuide(channel, "v1", entries.toList())

    @Test
    fun picksWhatAirsNow_withTheNextProgramme() {
        val channels = listOf(channel("a", "2"))
        val rows =
            HomeRow.onNow(
                channels,
                listOf(guide("a", entry("Friends", 0, 30, episode = "S02E05", episodeTitle = "The One"), entry("Frasier", 30, 60))),
                T0 + 10 * MIN,
            )

        assertEquals(1, rows.size)
        assertEquals("Friends", rows[0].title)
        assertEquals("S02E05 · The One", rows[0].subtitle)
        assertEquals("Frasier", rows[0].nextTitle)
        assertEquals("item-Friends", rows[0].itemId)
        assertEquals("2", rows[0].number)
    }

    @Test
    fun skipsOffAirAndUnguidedChannels() {
        val channels = listOf(channel("a", "1"), channel("b", "2"), channel("c", "3"))
        val rows =
            HomeRow.onNow(
                channels,
                listOf(guide("a", entry("Off air", 0, 60, offAir = true)), guide("c", entry("Cheers", 0, 60))),
                T0 + 5 * MIN,
            )

        assertEquals(listOf("c"), rows.map { it.channelId })
        assertNull(rows[0].subtitle)
    }

    @Test
    fun ordersByChannelNumber_numerically_andCaps() {
        val channels = listOf(channel("x", "10"), channel("y", "2"), channel("z", "100"))
        val guides = channels.map { guide(it.id, entry("Show ${it.id}", 0, 60)) }

        assertEquals(listOf("y", "x", "z"), HomeRow.onNow(channels, guides, T0 + MIN).map { it.channelId })
        assertEquals(listOf("y", "x"), HomeRow.onNow(channels, guides, T0 + MIN, max = 2).map { it.channelId })
    }

    @Test
    fun nextIsNullWhenOffAirFollows() {
        val rows =
            HomeRow.onNow(
                listOf(channel("a", "1")),
                listOf(guide("a", entry("Late film", 0, 60), entry("Off air", 60, 120, offAir = true))),
                T0 + MIN,
            )
        assertNull(rows.single().nextTitle)
    }

    @Test
    fun linksRoundTrip() {
        assertEquals("wholphin://cabletv", CableTvLinks.uri())
        assertEquals("wholphin://cabletv?channel=ch-sit%20coms%26more", CableTvLinks.uri("ch-sit coms&more").replace("+", "%20"))
        assertEquals("ch-sit coms&more", CableTvLinks.parse(CableTvLinks.uri("ch-sit coms&more"))?.channelId)
        assertNull(CableTvLinks.parse("wholphin://cabletv")?.channelId)
        assertTrue(CableTvLinks.isLink("WHOLPHIN://CableTV?channel=x"))
    }

    @Test
    fun otherLinksAreNotOurs() {
        assertFalse(CableTvLinks.isLink("wholphin://search?query=x"))
        assertFalse(CableTvLinks.isLink("https://cabletv/"))
        assertFalse(CableTvLinks.isLink(null))
        assertFalse(CableTvLinks.isLink("not a uri at all"))
    }

    @Test
    fun launchRequestIsTakenOnce() {
        CableTvLaunch.request("ch-1")
        assertEquals("ch-1", CableTvLaunch.take())
        assertNull(CableTvLaunch.take())
    }
}
