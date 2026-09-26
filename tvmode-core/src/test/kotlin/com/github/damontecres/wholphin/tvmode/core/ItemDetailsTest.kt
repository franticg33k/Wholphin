package com.github.damontecres.wholphin.tvmode.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemDetailsTest {
    @Test
    fun readsEpisodeDetails() =
        runTest {
            val json =
                """
                {"Id":"ep1","Name":"Primates","Type":"Episode","SeriesId":"s1","SeriesName":"Show","Overview":"Monkeys.",
                 "OfficialRating":"TV-G","CommunityRating":8.4,"PremiereDate":"2009-12-13T00:00:00.0000000Z","RunTimeTicks":35400000000,
                 "ImageTags":{"Primary":"x"},"ParentLogoItemId":"s1","ParentBackdropItemId":"s1","ParentBackdropImageTags":["b"],
                 "MediaStreams":[{"Type":"Video","Codec":"h264","Width":1920,"Height":1080},
                                 {"Type":"Audio","Codec":"aac","Channels":2,"IsDefault":true},{"Type":"Subtitle"}],
                 "SomethingNew":1}
                """.trimIndent()
            val requests = mutableListOf<String>()
            val details =
                CableTvClient({ path, _ ->
                    requests += path
                    json
                }).details("ep1")

            assertEquals("Items/ep1", requests.single())
            assertEquals("Monkeys.", details.overview)
            assertEquals(8.4f, details.communityRating!!, 0.01f)
            assertEquals("2009-12-13", details.airDate)
            assertEquals(59, details.runtimeMinutes)
            assertEquals("HD", details.quality)
            assertEquals("H.264", details.videoCodec)
            assertEquals("AAC Stereo", details.audio)
            assertEquals("s1", details.logoItemId)
            assertEquals("s1", details.backdropItemId)
        }

    @Test
    fun handlesSparseItems() =
        runTest {
            val details = CableTvClient({ _, _ -> """{"Id":"m1","ImageTags":{"Logo":"l"}}""" }).details("m1")
            assertNull(details.quality)
            assertNull(details.runtimeMinutes)
            assertEquals("m1", details.logoItemId)
            assertNull(details.backdropItemId)
        }

    @Test
    fun guideEntriesCarryContentType() {
        val slot = slot("p", 0.0, 30.0).copy(rating = "TV-Y7")
        val movie = slot("m", 30.0, 120.0).copy(movie = true)
        val entries = Guide.entries(listOf(slot, movie))
        assertEquals(ContentType.KIDS, entries[0].contentType)
        assertEquals(ContentType.MOVIE, entries[1].contentType)
        assertEquals(slot.itemId, entries[0].itemId)
    }
}
