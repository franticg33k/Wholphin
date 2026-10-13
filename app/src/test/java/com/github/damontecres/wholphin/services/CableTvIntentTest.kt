package com.github.damontecres.wholphin.services

import android.content.Intent
import android.net.Uri
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.RestoredSession
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.update
import com.github.damontecres.wholphin.test.currentUser
import com.github.damontecres.wholphin.tvmode.core.CableTvLaunch
import com.github.damontecres.wholphin.ui.nav.Destination
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Cable TV link the Android TV home screen row fires opens the TV mode on the channel it names. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class CableTvIntentTest {
    private val api: ApiClient = mockk()
    private val serverRepository: ServerRepository = mockk()
    private val userPreferencesService: UserPreferencesService = mockk()
    private val currentUser: CurrentUser = currentUser(UUID.randomUUID(), UUID.randomUUID())

    private lateinit var intentService: IntentService

    @Before
    fun setup() {
        CableTvLaunch.take()
        intentService = IntentService(api, serverRepository, userPreferencesService)
        every { userPreferencesService.flow } returns
            flow {
                emit(UserPreferences(AppPreferences.getDefaultInstance().update { signInAutomatically = true }, null))
            }
        every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(currentUser)
        coEvery { serverRepository.restoreLastSession() } returns RestoredSession.Success(currentUser)
    }

    @Test
    fun `a Cable TV link opens the TV mode and asks for its channel`() =
        runTest {
            val result = intentService.parseIntent(Intent(Intent.ACTION_VIEW, Uri.parse("wholphin://cabletv?channel=ch-sitcoms")))

            assertTrue(result is IntentResult.Target)
            result as IntentResult.Target
            assertEquals(listOf<Destination>(Destination.CableTv), result.destinations)
            assertTrue(result.addHomeToBackStack)
            assertEquals("ch-sitcoms", CableTvLaunch.take())
        }

    @Test
    fun `a Cable TV link without a channel just opens the TV mode`() =
        runTest {
            val result = intentService.parseIntent(Intent(Intent.ACTION_VIEW, Uri.parse("wholphin://cabletv")))

            assertTrue(result is IntentResult.Target)
            assertEquals(listOf<Destination>(Destination.CableTv), (result as IntentResult.Target).destinations)
            assertNull(CableTvLaunch.take())
        }
}
