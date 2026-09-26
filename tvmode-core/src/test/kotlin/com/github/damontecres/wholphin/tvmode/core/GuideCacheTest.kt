package com.github.damontecres.wholphin.tvmode.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideCacheTest {
    private val json =
        """
        {"serverTime":"2026-09-26T10:24:15Z","from":"2026-09-26T10:00:00Z","to":"2026-09-26T12:00:00Z","channels":[
         {"channelId":"ch-1","scheduleVersion":"v1","programs":[
          {"guideGroup":"g-1","start":"2026-09-26T10:00:00Z","end":"2026-09-26T10:30:00Z","itemId":"a","title":"Show","episode":"S01E01",
           "rating":"TV-Y7"},
          {"guideGroup":"g-2","start":"2026-09-26T10:30:00Z","end":"2026-09-26T12:10:00Z","itemId":"m","title":"Film","year":1994,"movie":true},
          {"guideGroup":"g-3","start":"2026-09-26T12:10:00Z","end":"2026-09-26T12:30:00Z"}
         ]}]}
        """.trimIndent()

    @Test
    fun parsesTheGuideEndpoint() =
        runTest {
            val guide = CableTvClient({ _, _ -> json }).guide(listOf("ch-1"), T0, T0 + 60 * MIN)!!.single()
            assertEquals("v1", guide.scheduleVersion)
            assertEquals(ContentType.KIDS, guide.entries[0].contentType)
            assertEquals(ContentType.MOVIE, guide.entries[1].contentType)
            assertTrue(guide.entries[2].offAir)
            assertEquals("Off air", guide.entries[2].title)
        }

    @Test
    fun oldPluginWithoutTheEndpointGivesNull() =
        runTest {
            assertNull(CableTvClient({ _, _ -> throw CableTvHttpException(404, "not found") }).guide(emptyList(), T0, T0 + MIN))
        }

    @Test
    fun cachesByWindow() =
        runTest {
            val cache = GuideCache()
            val guide = CableTvClient({ _, _ -> json }).guide(listOf("ch-1"), T0, T0 + 60 * MIN)!!.single()
            val from = guide.entries.first().startMs
            val to = from + 120 * MIN
            cache.put(guide, from, to)
            assertTrue(cache.covers("ch-1", from, to - MIN))
            assertFalse(cache.covers("ch-1", from, to + MIN))
            assertEquals(2, cache.entriesBetween("ch-1", from, from + 60 * MIN).size)
            cache.invalidate("ch-1")
            assertNull(cache.version("ch-1"))
        }
}
