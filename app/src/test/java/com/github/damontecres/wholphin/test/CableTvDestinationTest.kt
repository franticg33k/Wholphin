package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.tvmode.core.CableTvChannelMap
import com.github.damontecres.wholphin.tvmode.core.TvChannel
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cable TV channels listed in the app's Live TV rows open the TV mode; other channels still play in Live TV. */
class CableTvDestinationTest {
    private val cableChannelItem = UUID.randomUUID()
    private val otherChannelItem = UUID.randomUUID()

    private fun channelDto(id: UUID) = BaseItemDto(id = id, type = BaseItemKind.TV_CHANNEL, name = "Channel", seriesId = null)

    private fun programDto(channel: UUID) =
        BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.PROGRAM, name = "Show", channelId = channel, seriesId = null)

    private fun register() =
        CableTvChannelMap.update(
            listOf(TvChannel("ch-sitcoms", "5", "Sitcoms", null, "v1", 10, liveTvId = cableChannelItem.toString().replace("-", ""))),
        )

    @After
    fun clear() = CableTvChannelMap.update(emptyList())

    @Test
    fun aCableTvChannelOpensTheTvMode() {
        register()

        assertEquals(Destination.CableTv("ch-sitcoms"), BaseItem(channelDto(cableChannelItem), false).destination())
    }

    @Test
    fun aProgramOnACableTvChannelOpensTheTvMode() {
        register()

        assertEquals(Destination.CableTv("ch-sitcoms"), BaseItem(programDto(cableChannelItem), false).destination())
    }

    @Test
    fun otherChannelsStillPlayInLiveTv() {
        register()

        assertEquals(Destination.Playback(otherChannelItem, 0L), BaseItem(channelDto(otherChannelItem), false).destination())
        assertEquals(Destination.Playback(otherChannelItem, 0L), BaseItem(programDto(otherChannelItem), false).destination())
    }
}
