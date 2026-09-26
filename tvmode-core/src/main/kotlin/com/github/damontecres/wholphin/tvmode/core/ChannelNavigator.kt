package com.github.damontecres.wholphin.tvmode.core

/**
 * Channel up/down, number-pad entry and the last-channel toggle.
 */
class ChannelNavigator(
    channels: List<TvChannel>,
) {
    var channels: List<TvChannel> = channels
        private set

    var current: TvChannel? = channels.firstOrNull()
        private set

    private var previous: TvChannel? = null
    private val digits = StringBuilder()

    /** Digits typed so far, for the on-screen entry. */
    val pendingDigits: String get() = digits.toString()

    /** Replaces the channel list (after a refresh), keeping the current channel when it still exists. */
    fun update(newChannels: List<TvChannel>) {
        channels = newChannels
        current = newChannels.firstOrNull { it.id == current?.id } ?: newChannels.firstOrNull()
        previous = newChannels.firstOrNull { it.id == previous?.id }
    }

    fun tune(channel: TvChannel): TvChannel {
        if (channel.id != current?.id) {
            previous = current
            current = channel
        }
        digits.clear()
        return channel
    }

    fun tuneTo(channelId: String): TvChannel? = channels.firstOrNull { it.id == channelId }?.let(::tune)

    fun up(): TvChannel? = step(1)

    fun down(): TvChannel? = step(-1)

    /** Switches to the channel watched before this one. */
    fun last(): TvChannel? = previous?.let(::tune)

    /**
     * Adds a typed digit. Returns the channel to tune to once the entry is complete (as many digits as the longest
     * channel number), otherwise null; call [commitDigits] when the entry times out.
     */
    fun typeDigit(digit: Int): TvChannel? {
        require(digit in 0..9)
        digits.append(digit)
        val longest = channels.maxOfOrNull { it.number.length } ?: 4
        return if (digits.length >= longest) commitDigits() else null
    }

    /** Tunes to the typed number, if a channel has it. Clears the entry either way. */
    fun commitDigits(): TvChannel? {
        val typed = digits.toString()
        digits.clear()
        if (typed.isEmpty()) return null
        val match =
            channels.firstOrNull { it.number == typed }
                ?: typed.toIntOrNull()?.let { n -> channels.firstOrNull { it.number.toIntOrNull() == n } }
        return match?.let(::tune)
    }

    private fun step(delta: Int): TvChannel? {
        if (channels.isEmpty()) return null
        val index = channels.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        val next = channels[Math.floorMod(index + delta, channels.size)]
        return tune(next)
    }
}
