package com.github.damontecres.wholphin.tvmode

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.hilt.work.HiltWorker
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.damontecres.wholphin.tvmode.core.CableTvClient
import com.github.damontecres.wholphin.tvmode.core.CableTvLinks
import com.github.damontecres.wholphin.tvmode.core.Guide
import com.github.damontecres.wholphin.tvmode.core.HomeRow
import com.github.damontecres.wholphin.tvmode.core.OnNowEntry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "On now on Cable TV" row on the Android TV home screen: one tile per channel showing what it is airing, which
 * tunes straight to that channel when picked.
 *
 * This uses the same Android TV provider (preview channels) as the app's own "Recently added" row. Android asks the
 * user to approve a new row once; it can be hidden from the home screen's own settings, or turned off in the TV mode
 * settings here.
 */
@Singleton
class HomeRowPublisher
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val host: TvModeHost,
    ) {
        private val prefs = context.getSharedPreferences("cable_tv_home_row", Context.MODE_PRIVATE)

        /** Preview channels need Android 8 and a TV launcher. */
        val supported: Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        /** Replaces the row's tiles with [entries]; [title] names the row ("On now on <service name>"). */
        fun publish(
            entries: List<OnNowEntry>,
            title: String = context.getString(R.string.cable_tv_on_now),
        ) {
            if (!supported) return
            if (entries.isEmpty()) {
                remove()
                return
            }
            val channelId = ensureChannel(title) ?: return
            val existing = programIds(channelId)
            val programs = entries.mapIndexed { index, entry -> program(channelId, entry, entries.size - index).toContentValues() }
            context.contentResolver.bulkInsert(TvContractCompat.PreviewPrograms.CONTENT_URI, programs.toTypedArray())
            // Remove the old tiles after the new ones are in, so the row never flashes empty.
            existing.forEach { context.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
        }

        /** Takes the row off the home screen. */
        fun remove() {
            if (!supported) return
            val id = prefs.getLong(KEY_CHANNEL, -1L)
            if (id >= 0) {
                runCatching { context.contentResolver.delete(TvContractCompat.buildChannelUri(id), null, null) }
                prefs.edit().remove(KEY_CHANNEL).apply()
            }
        }

        private fun ensureChannel(title: String): Long? {
            val stored = prefs.getLong(KEY_CHANNEL, -1L)
            if (stored >= 0 && channelExists(stored)) {
                // The service name can change in the plugin's settings.
                if (prefs.getString(KEY_TITLE, null) != title) {
                    val values = ContentValues().apply { put(TvContractCompat.Channels.COLUMN_DISPLAY_NAME, title) }
                    runCatching { context.contentResolver.update(TvContractCompat.buildChannelUri(stored), values, null, null) }
                    prefs.edit().putString(KEY_TITLE, title).apply()
                }
                return stored
            }
            val channel =
                Channel
                    .Builder()
                    .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                    .setDisplayName(title)
                    .setAppLinkIntent(linkIntent(null))
                    .build()
            val uri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues()) ?: return null
            val id = ContentUris.parseId(uri)
            prefs
                .edit()
                .putLong(KEY_CHANNEL, id)
                .putString(KEY_TITLE, title)
                .apply()
            // Asks the user to let the row show on the home screen (Android asks once).
            runCatching { TvContractCompat.requestChannelBrowsable(context, id) }
            return id
        }

        private fun channelExists(id: Long): Boolean =
            runCatching {
                context.contentResolver
                    .query(
                        TvContractCompat.buildChannelUri(id),
                        arrayOf(TvContractCompat.Channels._ID),
                        null,
                        null,
                        null,
                    )?.use {
                        it.moveToFirst()
                    } == true
            }.getOrDefault(false)

        private fun programIds(channelId: Long): List<Long> =
            runCatching {
                context.contentResolver
                    .query(
                        TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                        arrayOf(TvContractCompat.PreviewPrograms._ID),
                        null,
                        null,
                        null,
                    )?.use { cursor -> generateSequence { if (cursor.moveToNext()) cursor.getLong(0) else null }.toList() }
                    .orEmpty()
            }.getOrDefault(emptyList())

        private fun program(
            channelId: Long,
            entry: OnNowEntry,
            weight: Int,
        ): PreviewProgram {
            val time = DateFormat.getTimeFormat(context)
            val times = "${time.format(Date(entry.startMs))} – ${time.format(Date(entry.endMs))}"
            val description =
                listOfNotNull(
                    "CH ${entry.number} · ${entry.channelName}",
                    times,
                    entry.subtitle,
                    entry.nextTitle?.let { "Next: $it" },
                ).joinToString("\n")
            val picture =
                entry.itemId
                    ?.let { host.imageUrl(it, if (entry.movie) "Backdrop" else "Primary", PICTURE_HEIGHT) }
                    ?: entry.logoUrl
            return PreviewProgram
                .Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                .setTitle(entry.title)
                .setDescription(description)
                .setInternalProviderId(entry.channelId)
                .setLive(true)
                .setWeight(weight)
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .apply { picture?.let { setPosterArtUri(Uri.parse(it)) } }
                .setIntent(linkIntent(entry.channelId))
                .build()
        }

        private fun linkIntent(channelId: String?): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(CableTvLinks.uri(channelId))).setPackage(context.packageName)

        private companion object {
            const val KEY_CHANNEL = "channel"
            const val KEY_TITLE = "title"
            const val PICTURE_HEIGHT = 360
        }
    }

/** Refreshes the home screen row from the server. */
@HiltWorker
class HomeRowWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val host: TvModeHost,
        private val settings: TvModeSettingsStore,
        private val publisher: HomeRowPublisher,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!publisher.supported) return Result.success()
            if (!settings.settings.value.homeRow) {
                publisher.remove()
                return Result.success()
            }
            return try {
                if (!host.ensureSignedIn()) return Result.success()
                val client = CableTvClient(host.transport)
                val channels = client.channels()
                if (channels.isEmpty()) {
                    publisher.remove()
                    return Result.success()
                }
                val now = client.clock.nowMs()
                val ids = channels.map { it.id }
                val window = 4 * 60 * 60 * 1000L
                val guides =
                    client.guide(ids, now - 60_000L, now + window)
                        ?: client.schedule(ids, now - 60_000L, now + window).map { s ->
                            com.github.damontecres.wholphin.tvmode.core
                                .ChannelGuide(s.channelId, s.scheduleVersion, Guide.entries(s.slots))
                        }
                // "On now on <the service name set in the plugin>", the plugin's default being "Cable TV".
                val service = runCatching { client.presentation().serviceName }.getOrNull()?.takeIf { it.isNotBlank() }
                publisher.publish(
                    HomeRow.onNow(channels, guides, now),
                    service?.let { applicationContext.getString(R.string.cable_tv_on_now_service, it) }
                        ?: applicationContext.getString(R.string.cable_tv_on_now),
                )
                Result.success()
            } catch (e: IOException) {
                Result.retry()
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }

/** Keeps the home screen row fresh while the server has Cable TV channels. */
@Singleton
class HomeRowScheduler
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val publisher: HomeRowPublisher,
    ) {
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Starts the periodic refresh (every 15 minutes, the shortest WorkManager allows) and refreshes now. */
        fun ensureScheduled() {
            if (!publisher.supported) return
            workManager.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<HomeRowWorker>(15, TimeUnit.MINUTES).setConstraints(network).build(),
            )
            refreshNow()
        }

        /** Refreshes the row once, now. */
        fun refreshNow() {
            if (!publisher.supported) return
            workManager.enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<HomeRowWorker>().setConstraints(network).build(),
            )
        }

        /** Stops refreshing and takes the row away (the server has no Cable TV, or the user turned it off). */
        fun cancel() {
            workManager.cancelUniqueWork(PERIODIC)
            workManager.cancelUniqueWork(NOW)
            publisher.remove()
        }

        private companion object {
            const val PERIODIC = "cable_tv_home_row"
            const val NOW = "cable_tv_home_row_now"
        }
    }
