package dev.marzahn.sankaku.auth

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.marzahn.sankaku.api.dto.UserSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class TinkTokenStoreKeystoreTest {
  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val file = File(context.filesDir, "keystore-test.preferences_pb")
  private val scopes = mutableListOf<CoroutineScope>()

  private val tokens = StoredTokens(
    accessToken = "access-secret",
    refreshToken = "refresh-secret",
    accessExpiresAt = Instant.fromEpochMilliseconds(1_000),
    refreshExpiresAt = Instant.fromEpochMilliseconds(2_000),
    user = UserSummary(id = "42", name = "tester"),
  )

  private fun restart(): Pair<TinkTokenStore, () -> String?> {
    closeAll()
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scopes += scope
    val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
    return TinkTokenStore(dataStore, KeystoreTokenKey(context)) to { runBlocking { dataStore.data.first()[TOKENS] } }
  }

  @Before
  @After
  fun cleanUp() {
    closeAll()
    file.delete()
    KeystoreTokenKey(context).delete()
  }

  private fun closeAll() = runBlocking {
    scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
  }

  @Test
  fun tokensSurviveARestart() = runBlocking {
    restart().first.save(tokens)

    assertEquals(tokens, restart().first.load())
  }

  @Test
  fun aLostKeystoreKeySignsOutAndTheNextSignInWorks() = runBlocking {
    restart().first.save(tokens)
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("tessera_token_key")

    val (store, stored) = restart()
    assertNull(store.load())
    assertNull(stored())

    store.save(tokens)
    assertEquals(tokens, restart().first.load())
  }

  private companion object {
    val TOKENS = stringPreferencesKey("tokens")
  }
}
