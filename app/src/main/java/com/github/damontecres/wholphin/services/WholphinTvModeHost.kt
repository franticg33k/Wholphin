package com.github.damontecres.wholphin.services

import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.tvmode.TvModeHost
import com.github.damontecres.wholphin.tvmode.core.CableTvHttpException
import com.github.damontecres.wholphin.tvmode.core.CableTvTransport
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videosApi
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the Cable TV mode (the :tvmode module) to Wholphin: the signed-in server, the player factory and navigation.
 */
@Singleton
class WholphinTvModeHost
    @Inject
    constructor(
        private val api: ApiClient,
        @param:AuthOkHttpClient private val okHttpClient: OkHttpClient,
        private val playerFactory: PlayerFactory,
        private val userPreferencesService: UserPreferencesService,
        private val navigationManager: NavigationManager,
    ) : TvModeHost {
        override val transport =
            CableTvTransport { path, query ->
                val base = api.baseUrl ?: throw IOException("Not signed in to a server")
                val url =
                    base
                        .trimEnd('/')
                        .plus("/")
                        .plus(path)
                        .toHttpUrl()
                        .newBuilder()
                        .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
                        .build()
                withContext(WholphinDispatchers.IO) {
                    okHttpClient
                        .newCall(
                            Request
                                .Builder()
                                .url(url)
                                .get()
                                .build(),
                        ).execute()
                        .use { response ->
                            if (!response.isSuccessful) throw CableTvHttpException(response.code, "HTTP ${response.code} for $path")
                            response.body.string()
                        }
                }
            }

        override fun streamUrl(
            itemId: String,
            mediaSourceId: String?,
        ): String =
            api.videosApi.getVideoStreamUrl(
                itemId = UUID.fromString(itemId.toDashedUuid()),
                mediaSourceId = mediaSourceId,
                static = true,
            )

        override fun imageUrl(
            itemId: String,
            type: String,
            maxHeight: Int,
        ): String = "${api.baseUrl.orEmpty().trimEnd('/')}/Items/$itemId/Images/$type?maxHeight=$maxHeight&quality=90"

        override suspend fun createPlayer(): Player {
            // TV mode clips items to their scheduled in/out points, which ExoPlayer supports; use it whatever the
            // default backend is.
            val prefs = userPreferencesService.getCurrent().appPreferences
            // Start after one second of buffer rather than ExoPlayer's 2.5 s: every tune-in is a seek into a file.
            val loadControl =
                DefaultLoadControl
                    .Builder()
                    .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000)
                    .build()
            return playerFactory.createVideoPlayer(PlayerBackend.EXO_PLAYER, prefs, loadControl).player
        }

        override fun releasePlayer(player: Player) {
            player.release()
        }

        override fun exit() {
            navigationManager.goBack()
        }

        private fun String.toDashedUuid(): String =
            if (length == 32) {
                "${substring(0, 8)}-${substring(8, 12)}-${substring(12, 16)}-${substring(16, 20)}-${substring(20)}"
            } else {
                this
            }
    }
