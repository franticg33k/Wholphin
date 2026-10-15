package com.github.damontecres.wholphin.tvmode

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
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
import com.github.damontecres.wholphin.tvmode.core.HomeRowPolicy
import com.github.damontecres.wholphin.tvmode.core.OnNowEntry
import com.github.damontecres.wholphin.tvmode.core.RowAction
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
            val channelId = ensureChannel(title)
            if (channelId == null) {
                setStatus("Couldn't create the row on the home screen")
                return
            }
            val existing = programIds(channelId)
            // One insert per tile, so a tile Android rejects is named in the log rather than silently dropped.
            var inserted = 0
            var failure: String? = null
            entries.forEachIndexed { index, entry ->
                try {
                    val uri =
                        context.contentResolver.insert(
                            TvContractCompat.PreviewPrograms.CONTENT_URI,
                            program(channelId, entry, entries.size - index).toContentValues(),
                        )
                    if (uri != null) inserted++ else failure = failure ?: "Android refused a tile"
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't add the tile for channel ${entry.number}", e)
                    failure = failure ?: "${e.javaClass.simpleName}: ${e.message}"
                }
            }
            if (inserted == 0) {
                // Keep the old tiles: removing them now would leave an empty row.
                setStatus("Couldn't add tiles (${failure ?: "unknown"})")
                return
            }
            // Remove the old tiles only once the new ones are in, so the row never flashes or goes empty.
            existing.forEach { context.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
            val shown = isBrowsable(channelId)
            Log.i(TAG, "Published $inserted of ${entries.size} tiles, removed ${existing.size}, browsable=$shown")
            setStatus(
                when {
                    !shown -> "$inserted tiles ready, but the row isn't on your home screen yet: select this to add it"
                    failure != null -> "$inserted of ${entries.size} tiles ($failure)"
                    else -> "$inserted tiles"
                },
                tiles = inserted,
            )
        }

        /** The row's tiles and when they were last refreshed, for the settings screen. */
        fun status(): String {
            val text = prefs.getString(KEY_STATUS, null) ?: return "Not set up yet"
            val at = prefs.getLong(KEY_STATUS_AT, 0L)
            return if (at > 0) "$text, ${DateFormat.getTimeFormat(context).format(Date(at))}" else text
        }

        /** True when the row exists but Android hasn't been allowed to show it on the home screen. */
        fun needsApproval(): Boolean {
            val id = prefs.getLong(KEY_CHANNEL, -1L)
            return supported && id >= 0 && channelExists(id) && !isBrowsable(id)
        }

        /** Asks Android to show the row on the home screen. Needs an Activity: it opens the system's approval prompt. */
        fun requestApproval(activity: android.app.Activity) {
            val id = prefs.getLong(KEY_CHANNEL, -1L)
            if (id >= 0) runCatching { TvContractCompat.requestChannelBrowsable(activity, id) }
        }

        private fun setStatus(
            text: String,
            tiles: Int = 0,
        ) {
            Log.i(TAG, "Row status: $text")
            prefs
                .edit()
                .putString(KEY_STATUS, text)
                .putLong(KEY_STATUS_AT, System.currentTimeMillis())
                .putInt(KEY_TILES, tiles)
                .apply()
        }

        private fun isBrowsable(channelId: Long): Boolean =
            runCatching {
                context.contentResolver
                    .query(
                        TvContractCompat.buildChannelUri(channelId),
                        arrayOf(TvContractCompat.Channels.COLUMN_BROWSABLE),
                        null,
                        null,
                        null,
                    )?.use { it.moveToFirst() && it.getInt(0) == 1 }
            }.getOrNull() ?: true

        /** Takes the row off the home screen. */
        fun remove() {
            if (!supported) return
            val id = prefs.getLong(KEY_CHANNEL, -1L)
            if (id >= 0) {
                runCatching { context.contentResolver.delete(TvContractCompat.buildChannelUri(id), null, null) }
                prefs.edit().remove(KEY_CHANNEL).apply()
                setStatus("Off the home screen")
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
            // Android only shows the row once the user approves it; that prompt needs an Activity (see requestApproval),
            // so this may do nothing from the background.
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
            const val KEY_STATUS = "status"
            const val KEY_STATUS_AT = "statusAt"
            const val KEY_TILES = "tiles"
            const val PICTURE_HEIGHT = 360
        }
    }

private const val TAG = "CableTvHomeRow"

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
                val now = client.clock.nowMs()
                val ids = channels.map { it.id }
                val window = 4 * 60 * 60 * 1000L
                val guides =
                    if (channels.isEmpty()) {
                        emptyList()
                    } else {
                        client.guide(ids, now - 60_000L, now + window)
                            ?: client.schedule(ids, now - 60_000L, now + window).map { s ->
                                com.github.damontecres.wholphin.tvmode.core
                                    .ChannelGuide(s.channelId, s.scheduleVersion, Guide.entries(s.slots))
                            }
                    }
                val tiles = HomeRow.onNow(channels, guides, now)
                Log.i(TAG, "Refreshed: ${channels.size} channels, ${tiles.size} on now")
                when (HomeRowPolicy.afterRefresh(channels.size, tiles.size)) {
                    RowAction.REMOVE -> {
                        publisher.remove()
                    }

                    RowAction.KEEP -> {
                        Unit
                    }

                    RowAction.PUBLISH -> {
                        // "On now on <the service name set in the plugin>", the plugin's default being "Cable TV".
                        val service = runCatching { client.presentation().serviceName }.getOrNull()?.takeIf { it.isNotBlank() }
                        publisher.publish(
                            tiles,
                            service?.let { applicationContext.getString(R.string.cable_tv_on_now_service, it) }
                                ?: applicationContext.getString(R.string.cable_tv_on_now),
                        )
                    }
                }
                Result.success()
            } catch (e: IOException) {
                Log.w(TAG, "Refresh failed, will retry: ${e.message}")
                Result.retry()
            } catch (e: Exception) {
                // A periodic worker keeps running after a failure; the row stays as it was.
                Log.w(TAG, "Refresh failed", e)
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
