package com.github.damontecres.wholphin.tvmode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneInPlannerTest {
    private val day =
        listOf(
            slot("a", 0.0, 22.0, group = "g1"),
            slot("ad1", 22.0, 22.5, SlotKind.COMMERCIAL, group = "g1"),
            slot("ad2", 22.5, 23.0, SlotKind.COMMERCIAL, group = "g1"),
            slot("f", 23.0, 30.0, SlotKind.FILLER, group = "g1"),
            slot("b", 30.0, 52.0, group = "g2"),
            slot("c", 52.0, 60.0, group = "g3"),
        )

    @Test
    fun joinsTheAiringItemAtTheRightOffset_AndQueuesWhatFollows() {
        val plan = TuneInPlanner.plan(day, T0 + 10 * MIN) as TunePlan.Play

        assertEquals(listOf("a", "ad1", "ad2"), plan.items.map { it.slot.id })
        assertEquals(10 * MIN, plan.startPositionMs)
        assertEquals(T0 + 23 * MIN, plan.queuedUntilMs)
    }

    @Test
    fun stopsQueueingAtFiller() {
        val plan = TuneInPlanner.plan(day, T0 + 22 * MIN + 10_000) as TunePlan.Play

        assertEquals(listOf("ad1", "ad2"), plan.items.map { it.slot.id })
        assertEquals(10_000L, plan.startPositionMs)
    }

    @Test
    fun fillerMeansStaticUntilItEnds() {
        val plan = TuneInPlanner.plan(day, T0 + 25 * MIN)

        assertEquals(TunePlan.Static(day[3], T0 + 30 * MIN), plan)
    }

    @Test
    fun joiningInTheLastSecondSkipsToTheNextSlot() {
        val plan = TuneInPlanner.plan(day, T0 + 52 * MIN - 500) as TunePlan.Play

        assertEquals(
            "c",
            plan.items
                .first()
                .slot.id,
        )
        assertEquals(0L, plan.startPositionMs)
    }

    @Test
    fun clipsFollowInAndOutPoints() {
        val secondHalf = slot("a2", 0.0, 11.0, inPointMs = 11 * MIN)
        val plan = TuneInPlanner.plan(listOf(secondHalf), T0 + 5 * MIN) as TunePlan.Play

        assertEquals(11 * MIN, plan.items.single().clipStartMs)
        assertEquals(22 * MIN, plan.items.single().clipEndMs)
        assertEquals(5 * MIN, plan.startPositionMs)
    }

    @Test
    fun withoutAScheduleForNowItAsksForOne() {
        assertEquals(TunePlan.NeedsSchedule, TuneInPlanner.plan(emptyList(), T0))
        assertEquals(TunePlan.NeedsSchedule, TuneInPlanner.plan(day, T0 + 61 * MIN))
        assertEquals(TunePlan.NeedsSchedule, TuneInPlanner.plan(day.drop(1), T0 + 5 * MIN))
    }

    @Test
    fun extendKeepsTheQueueAheadOfPlayback() {
        val long = (0 until 10).map { slot("s$it", it * 10.0, it * 10.0 + 10) }

        val more = TuneInPlanner.extend(long, playing = long[1], lastQueued = long[2], queueAhead = 3)

        assertEquals(listOf("s3", "s4"), more.map { it.slot.id })
        assertTrue(TuneInPlanner.extend(long, playing = long[1], lastQueued = long[4], queueAhead = 3).isEmpty())
    }

    @Test
    fun driftIsPositiveWhenBehind() {
        val playing = day[0]
        assertEquals(2_000L, TuneInPlanner.drift(playing, positionMs = 8_000, nowMs = T0 + 10_000))
    }
}
