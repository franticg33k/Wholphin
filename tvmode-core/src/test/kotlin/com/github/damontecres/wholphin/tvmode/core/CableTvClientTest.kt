package com.github.damontecres.wholphin.tvmode.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CableTvClientTest {
    // Captured from a Jellyfin 12.0 server running the plugin; note the omitted null fields.
    private val scheduleJson =
        """
        {"serverTime":"2026-09-26T10:24:15.1234567Z","from":"2026-09-26T10:22:00Z","to":"2026-09-26T10:30:00Z",
         "scheduleVersion":"22436948cccb","channels":[{"channelId":"ch-ads","number":"0007","scheduleVersion":"d66b359113e8",
         "slots":[
          {"slotId":"s-1","kind":"program","start":"2026-09-26T10:22:00Z","end":"2026-09-26T10:23:20Z","itemId":"5b1c","mediaSourceId":"5b1c",
           "inPointMs":0,"outPointMs":80023,"title":"Test Show","episode":"S01E02","guideGroup":"g-1"},
          {"slotId":"s-1-1","kind":"filler","start":"2026-09-26T10:23:55Z","end":"2026-09-26T10:24:00Z","inPointMs":0,"outPointMs":5000,
           "guideGroup":"g-1"},
          {"slotId":"s-2","kind":"program","start":"2026-09-26T10:25:00Z","end":"2026-09-26T10:26:40Z","itemId":"428e","mediaSourceId":"428e",
           "inPointMs":0,"outPointMs":100000,"title":"Short Film (1999)","guideGroup":"g-2","lineup":"Movie Hour","premiere":true,"futureField":1}
         ]}]}
        """.trimIndent()

    @Test
    fun parsesTheScheduleAndSyncsTheClock() =
        runTest {
            val requests = mutableListOf<Pair<String, Map<String, String>>>()
            val client =
                CableTvClient(
                    { path, query ->
                        requests += path to query
                        scheduleJson
                    },
                )

            val result = client.schedule(listOf("ch-ads"), T0, T0 + 10 * MIN)

            assertEquals("CableTv/Schedule", requests.single().first)
            assertEquals("ch-ads", requests.single().second["channelIds"])
            val slots = result.single().slots
            assertEquals(3, slots.size)
            assertEquals(SlotKind.FILLER, slots[1].kind)
            assertNull(slots[1].itemId)
            assertEquals(false, slots[1].isPlayable)
            assertEquals("Movie Hour", slots[2].lineup)
            assertTrue(slots[2].premiere)
            assertEquals(
                java.time.Instant
                    .parse("2026-09-26T10:22:00Z")
                    .toEpochMilli(),
                slots[0].startMs,
            )
            assertTrue(client.clock.isSynced)
        }

    @Test
    fun parsesChannelLogosAndCategories() =
        runTest {
            val json =
                """
                {"serverTime":"2026-09-26T12:50:20.0794892Z","channels":[
                 {"channelId":"ch-toons","number":"12","name":"Toons","logoUrl":"http://jf:8096/CableTv/Logo/ch-toons?v=ed112998",
                  "scheduleVersion":"cba07ae8933b","poolSize":5,"category":"Kids"},
                 {"channelId":"ch-old","number":"30","name":"Old server","scheduleVersion":"c8fe","poolSize":1,"category":" "}
                ]}
                """.trimIndent()
            val client = CableTvClient({ _, _ -> json })

            val channels = client.channels()

            assertEquals("http://jf:8096/CableTv/Logo/ch-toons?v=ed112998", channels[0].logoUrl)
            assertEquals("Kids", channels[0].category)
            assertNull(channels[1].logoUrl)
            assertNull(channels[1].category)
        }
}
