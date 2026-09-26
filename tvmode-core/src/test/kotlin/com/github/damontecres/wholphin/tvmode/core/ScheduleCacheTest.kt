package com.github.damontecres.wholphin.tvmode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCacheTest {
    private fun schedule(
        version: String,
        vararg slots: TvSlot,
    ) = ChannelSchedule("ch", version, slots.toList())

    @Test
    fun findsTheSlotAiringAtATime() {
        val cache = ScheduleCache()
        cache.merge(schedule("v1", slot("a", 0.0, 30.0), slot("b", 30.0, 60.0)), T0, T0 + 60 * MIN)

        assertEquals("a", cache.slotAt("ch", T0 + 29 * MIN)?.id)
        assertEquals("b", cache.slotAt("ch", T0 + 30 * MIN)?.id)
        assertNull(cache.slotAt("ch", T0 + 60 * MIN))
        assertEquals(listOf("a", "b"), cache.slotsFrom("ch", T0 + 5 * MIN).map { it.id })
    }

    @Test
    fun mergesAdjacentWindows_AndExtendsCoverage() {
        val cache = ScheduleCache()
        cache.merge(schedule("v1", slot("a", 0.0, 30.0)), T0, T0 + 30 * MIN)
        cache.merge(schedule("v1", slot("b", 30.0, 60.0)), T0 + 30 * MIN, T0 + 60 * MIN)

        assertEquals(listOf("a", "b"), cache.slotsFrom("ch", T0).map { it.id })
        assertTrue(cache.covers("ch", T0 + 10 * MIN, 40 * MIN))
        assertFalse(cache.covers("ch", T0 + 10 * MIN, 60 * MIN))
    }

    @Test
    fun aNewVersionReplacesTheChannel() {
        val cache = ScheduleCache()
        cache.merge(schedule("v1", slot("a", 0.0, 30.0), slot("b", 30.0, 60.0)), T0, T0 + 60 * MIN)
        cache.merge(schedule("v2", slot("x", 0.0, 45.0)), T0, T0 + 45 * MIN)

        assertEquals("v2", cache.version("ch"))
        assertEquals(listOf("x"), cache.slotsFrom("ch", T0).map { it.id })
    }

    @Test
    fun guideQueryReturnsOverlappingSlots() {
        val cache = ScheduleCache()
        cache.merge(schedule("v1", slot("a", 0.0, 30.0), slot("b", 30.0, 60.0), slot("c", 60.0, 90.0)), T0, T0 + 90 * MIN)

        assertEquals(listOf("a", "b"), cache.slotsBetween("ch", T0 + 10 * MIN, T0 + 40 * MIN).map { it.id })
    }
}
