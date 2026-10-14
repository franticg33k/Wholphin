package com.github.damontecres.wholphin.tvmode.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowPolicyTest {
    @Test
    fun aRefreshPublishesWhenThereIsSomethingOn() {
        assertEquals(RowAction.PUBLISH, HomeRowPolicy.afterRefresh(channels = 5, tiles = 3))
    }

    @Test
    fun aRefreshKeepsTheLastTilesWhenNothingIsOnRightNow() {
        assertEquals(RowAction.KEEP, HomeRowPolicy.afterRefresh(channels = 5, tiles = 0))
    }

    @Test
    fun aServerWithNoChannelsRemovesTheRow() {
        assertEquals(RowAction.REMOVE, HomeRowPolicy.afterRefresh(channels = 0, tiles = 0))
    }

    @Test
    fun onlyAMissingEndpointOrAnEmptyChannelListRemovesTheRow() {
        assertEquals(RowAction.REMOVE, HomeRowPolicy.afterAvailabilityCheck(status = 404, channels = null))
        assertEquals(RowAction.REMOVE, HomeRowPolicy.afterAvailabilityCheck(status = null, channels = 0))
        assertEquals(RowAction.PUBLISH, HomeRowPolicy.afterAvailabilityCheck(status = null, channels = 4))
    }

    @Test
    fun refusedLoginsServerErrorsAndOutagesLeaveTheRowAlone() {
        listOf(401, 403, 408, 429, 500, 502, 503).forEach {
            assertEquals("status $it", RowAction.KEEP, HomeRowPolicy.afterAvailabilityCheck(status = it, channels = null))
        }
        assertEquals(RowAction.KEEP, HomeRowPolicy.afterAvailabilityCheck(status = null, channels = null))
    }
}
