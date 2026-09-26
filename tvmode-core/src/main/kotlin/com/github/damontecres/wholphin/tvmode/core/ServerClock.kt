package com.github.damontecres.wholphin.tvmode.core

/**
 * The server's clock, as seen from the device.
 *
 * Every TV shows the same thing at the same moment only if they agree on the time, and device clocks drift. Each API
 * response carries `serverTime`; the offset between it and a monotonic device clock is kept from the sample with the
 * shortest round trip (the least uncertain), and [nowMs] is the monotonic clock plus that offset.
 */
class ServerClock(
    private val elapsedMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallMs: () -> Long = System::currentTimeMillis,
) {
    private data class Sample(
        val offsetMs: Long,
        val roundTripMs: Long,
        val takenAtMs: Long,
    )

    @Volatile
    private var best: Sample? = null

    /** Whether at least one server time sample has been taken. */
    val isSynced: Boolean get() = best != null

    /**
     * Records a server time.
     *
     * @param serverTimeMs the response's `serverTime`
     * @param sentAtElapsedMs monotonic time the request was sent
     * @param receivedAtElapsedMs monotonic time the response arrived
     */
    fun record(
        serverTimeMs: Long,
        sentAtElapsedMs: Long,
        receivedAtElapsedMs: Long,
    ) {
        val roundTrip = (receivedAtElapsedMs - sentAtElapsedMs).coerceAtLeast(0)
        val midpoint = sentAtElapsedMs + roundTrip / 2
        val sample = Sample(serverTimeMs - midpoint, roundTrip, receivedAtElapsedMs)
        val current = best
        // Prefer tighter samples, but let an old one age out so a slowly drifting clock is followed.
        if (current == null ||
            sample.roundTripMs <= current.roundTripMs ||
            receivedAtElapsedMs - current.takenAtMs > MAX_SAMPLE_AGE_MS
        ) {
            best = sample
        }
    }

    /** The current server time in epoch milliseconds; the device clock until the first sample. */
    fun nowMs(): Long = best?.let { elapsedMs() + it.offsetMs } ?: wallMs()

    /** Monotonic device time, for timing requests. */
    fun elapsed(): Long = elapsedMs()

    private companion object {
        const val MAX_SAMPLE_AGE_MS = 10 * 60 * 1000L
    }
}
