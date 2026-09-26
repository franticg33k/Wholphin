package com.github.damontecres.wholphin.tvmode.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelKindsTest {
    @Test
    fun parsesKindsAndSpecialSlots() =
        runTest {
            val channels =
                """
                {"serverTime":"2026-09-26T16:00:00Z","channels":[
                 {"channelId":"s","number":"900","name":"News","scheduleVersion":"v","poolSize":1,"kind":"stream"},
                 {"channelId":"w","number":"901","name":"Weather","scheduleVersion":"v","poolSize":1,"kind":"weather"},
                 {"channelId":"m","number":"902","name":"Rock","scheduleVersion":"v","poolSize":3}]}
                """.trimIndent()
            val parsed = CableTvClient({ _, _ -> channels }).channels()
            assertEquals(listOf(ChannelKind.STREAM, ChannelKind.WEATHER, ChannelKind.STANDARD), parsed.map { it.kind })

            val schedule =
                """
                {"serverTime":"2026-09-26T16:00:00Z","from":"2026-09-26T16:00:00Z","to":"2026-09-26T17:00:00Z","scheduleVersion":"x","channels":[
                 {"channelId":"s","number":"900","scheduleVersion":"v","slots":[
                  {"slotId":"s1","kind":"stream","start":"2026-09-26T16:00:00Z","end":"2026-09-26T17:00:00Z","inPointMs":0,"outPointMs":3600000,
                   "title":"News","url":"https://example.com/live.m3u8","guideGroup":"g1"}]},
                 {"channelId":"m","number":"902","scheduleVersion":"v","slots":[
                  {"slotId":"m1","kind":"program","start":"2026-09-26T16:00:00Z","end":"2026-09-26T16:04:00Z","itemId":"t1","inPointMs":0,
                   "outPointMs":240000,"title":"Song","audio":true,"artist":"Band","album":"Record","guideGroup":"g2"},
                  {"slotId":"m2","kind":"program","start":"2026-09-26T16:04:00Z","end":"2026-09-26T16:06:00Z","itemId":"tr","inPointMs":0,
                   "outPointMs":120000,"title":"Film","episodeTitle":"Trailer","trailer":true,"ownerId":"o1","guideGroup":"g3"}]}]}
                """.trimIndent()
            val result = CableTvClient({ _, _ -> schedule }).schedule(listOf("s", "m"), T0, T0 + 60 * MIN)
            val stream = result[0].slots.single()
            assertEquals(SlotKind.STREAM, stream.kind)
            assertEquals("https://example.com/live.m3u8", stream.url)
            assertFalse(stream.isPlayable)
            val song = result[1].slots[0]
            assertTrue(song.audio)
            assertEquals("Band", song.artist)
            val trailer = result[1].slots[1]
            assertTrue(trailer.trailer)
            assertEquals("o1", trailer.ownerId)

            val entries = Guide.entries(result[0].slots)
            assertFalse(entries.single().offAir)
            assertEquals("stream", entries.single().kind)
        }

    @Test
    fun mergedGuideEntriesAreNotOffAir() =
        runTest {
            val json =
                """
                {"serverTime":"2026-09-26T16:00:00Z","from":"2026-09-26T16:00:00Z","to":"2026-09-26T17:00:00Z","channels":[
                 {"channelId":"m","scheduleVersion":"v","programs":[
                  {"guideGroup":"g","start":"2026-09-26T16:00:00Z","end":"2026-09-26T16:30:00Z","title":"Rock Radio","episodeTitle":"Band, Other","kind":"music"}]}]}
                """.trimIndent()
            val entry =
                CableTvClient({ _, _ -> json })
                    .guide(listOf("m"), T0, T0 + MIN)!!
                    .single()
                    .entries
                    .single()
            assertFalse(entry.offAir)
            assertNull(entry.itemId)
            assertEquals("music", entry.kind)
        }

    @Test
    fun parsesTheWeather() =
        runTest {
            val json =
                """
                {"location":"Chicago","metric":false,"updatedUtc":"2026-09-26T16:00:00Z",
                 "current":{"temperature":68.4,"feelsLike":67.1,"humidity":55,"windSpeed":9.3,"windDirection":"SW","pressure":30.01,"code":2,"condition":"Partly cloudy"},
                 "daily":[{"date":"2026-09-26","high":74.2,"low":58.1,"code":2,"condition":"Pt Cloudy","precipitationChance":10}],
                 "hourly":[{"time":"2026-09-26T11:00","temperature":68.0,"code":2,"precipitationChance":null}],"sunrise":"06:47","sunset":"18:44"}
                """.trimIndent()
            val paths = mutableListOf<String>()
            val report =
                CableTvClient({ path, _ ->
                    paths += path
                    json
                }).weather("w")
            assertEquals("CableTv/Weather/w", paths.single())
            assertEquals("Partly cloudy", report.current.condition)
            assertEquals("°F", report.temperatureUnit)
            assertNull(report.hourly.single().precipitationChance)
        }
}
