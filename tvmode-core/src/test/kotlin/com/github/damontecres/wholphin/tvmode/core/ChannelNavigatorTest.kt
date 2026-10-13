package com.github.damontecres.wholphin.tvmode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelNavigatorTest {
    private val channels = listOf(channel("a", "0002"), channel("b", "0010"), channel("c", "0412"))

    @Test
    fun upAndDownWrapAround() {
        val nav = ChannelNavigator(channels)

        assertEquals("c", nav.down()?.id)
        assertEquals("a", nav.up()?.id)
        assertEquals("b", nav.up()?.id)
    }

    @Test
    fun numberEntryCompletesAtFullLength_OrOnCommit() {
        val nav = ChannelNavigator(channels)

        listOf(0, 4, 1).forEach { assertNull(nav.typeDigit(it)) }
        assertEquals("041", nav.pendingDigits)
        assertEquals("c", nav.typeDigit(2)?.id)

        nav.typeDigit(1)
        nav.typeDigit(0)
        assertEquals("b", nav.commitDigits()?.id)
        assertNull(nav.commitDigits())
    }

    @Test
    fun unknownNumbersDoNothing() {
        val nav = ChannelNavigator(channels)

        listOf(9, 9, 9, 9).forEach { nav.typeDigit(it) }

        assertEquals("a", nav.current?.id)
    }

    @Test
    fun lastSwitchesBack() {
        val nav = ChannelNavigator(channels)
        nav.tuneTo("c")

        assertEquals("a", nav.last()?.id)
        assertEquals("c", nav.last()?.id)
    }

    @Test
    fun updateKeepsTheCurrentChannel() {
        val nav = ChannelNavigator(channels)
        nav.tuneTo("b")

        nav.update(listOf(channel("b", "0010"), channel("d", "0020")))

        assertEquals("b", nav.current?.id)
    }

    @Test
    fun upAndDownVisitOnlyFavourites() {
        val nav = ChannelNavigator(channels)
        nav.only = setOf("a", "c")

        assertEquals("c", nav.down()?.id)
        assertEquals("a", nav.down()?.id)
        assertEquals("c", nav.up()?.id)
    }

    @Test
    fun steppingFromANonFavouriteJoinsTheFavourites() {
        val nav = ChannelNavigator(channels)
        nav.tuneTo("b")
        nav.only = setOf("a", "c")

        assertEquals("c", nav.up()?.id)

        nav.tuneTo("b")
        assertEquals("a", nav.down()?.id)
    }

    @Test
    fun favouritesThatNoLongerExistDontStrandTheViewer() {
        val nav = ChannelNavigator(channels)
        nav.only = setOf("gone")

        assertEquals("b", nav.up()?.id)
    }

    @Test
    fun numberEntryStillReachesNonFavourites() {
        val nav = ChannelNavigator(channels)
        nav.only = setOf("a")

        nav.typeDigit(0)
        nav.typeDigit(0)
        nav.typeDigit(1)
        assertEquals("b", nav.typeDigit(0)?.id)
    }
}
