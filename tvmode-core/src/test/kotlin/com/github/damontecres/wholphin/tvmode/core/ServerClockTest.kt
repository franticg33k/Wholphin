package com.github.damontecres.wholphin.tvmode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerClockTest {
    @Test
    fun usesTheServerTimeAtTheRequestMidpoint() {
        var elapsed = 1_000L
        val clock = ServerClock(elapsedMs = { elapsed }, wallMs = { 0L })

        assertFalse(clock.isSynced)
        assertEquals(0L, clock.nowMs())

        // Sent at 1000, answered at 1200: the server said T0, so at 1100 it was T0.
        clock.record(serverTimeMs = T0, sentAtElapsedMs = 1_000, receivedAtElapsedMs = 1_200)
        elapsed = 1_100
        assertTrue(clock.isSynced)
        assertEquals(T0, clock.nowMs())

        elapsed = 61_100
        assertEquals(T0 + 60_000, clock.nowMs())
    }

    @Test
    fun prefersSamplesWithShorterRoundTrips() {
        var elapsed = 0L
        val clock = ServerClock(elapsedMs = { elapsed }, wallMs = { 0L })

        clock.record(serverTimeMs = T0, sentAtElapsedMs = 0, receivedAtElapsedMs = 100)
        clock.record(serverTimeMs = T0 + 5_000, sentAtElapsedMs = 1_000, receivedAtElapsedMs = 3_000)

        elapsed = 50
        assertEquals(T0, clock.nowMs())
    }
}
