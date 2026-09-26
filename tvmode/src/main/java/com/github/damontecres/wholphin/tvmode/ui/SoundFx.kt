package com.github.damontecres.wholphin.tvmode.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Small interface sounds, generated rather than bundled: a burst of static on a channel change and a blip when the
 * guide opens. Quiet, and silent if the device refuses an audio track.
 */
class SoundFx {
    private val staticTrack: AudioTrack? = track(staticBurst())
    private val blipTrack: AudioTrack? = track(blip())

    fun channelChange() = play(staticTrack)

    fun guideOpen() = play(blipTrack)

    fun release() {
        staticTrack?.release()
        blipTrack?.release()
    }

    private fun play(track: AudioTrack?) {
        track ?: return
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    private companion object {
        const val RATE = 22_050

        fun staticBurst(): ShortArray {
            val random = Random(7)
            val n = RATE * 30 / 100
            return ShortArray(n) { i ->
                val envelope = 1f - i.toFloat() / n
                ((random.nextFloat() * 2 - 1) * 5_000 * envelope).toInt().toShort()
            }
        }

        fun blip(): ShortArray {
            val n = RATE * 6 / 100
            return ShortArray(n) { i ->
                val envelope = 1f - i.toFloat() / n
                (sin(2 * PI * 880 * i / RATE) * 4_000 * envelope).toInt().toShort()
            }
        }

        fun track(samples: ShortArray): AudioTrack? =
            runCatching {
                AudioTrack
                    .Builder()
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    ).setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    ).setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
                    .also { it.write(samples, 0, samples.size) }
            }.getOrNull()
    }
}
