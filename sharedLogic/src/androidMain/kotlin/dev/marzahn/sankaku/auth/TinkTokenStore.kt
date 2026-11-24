package dev.marzahn.sankaku.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dev.marzahn.sankaku.api.dto.UserSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import kotlin.io.encoding.Base64
import kotlin.time.Instant

interface TokenKey {
  fun aead(): Aead

  fun delete()
}

class TinkTokenStore(private val dataStore: DataStore<Preferences>, private val key: TokenKey) : TokenStore {
  @Serializable
  private data class Payload(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
    val user: UserSummary,
  )

  private val mutex = Mutex()
  private var loaded = false
  private var cached: StoredTokens? = null
  private var aead: Aead? = null

  override suspend fun load(): StoredTokens? = mutex.withLock {
    if (!loaded) {
      cached = withContext(Dispatchers.IO) { read() }
      loaded = true
    }
    cached
  }

  override suspend fun save(tokens: StoredTokens): Unit = mutex.withLock {
    cached = tokens
    loaded = true
    withContext(Dispatchers.IO) {
      val payload = Payload(
        tokens.accessToken,
        tokens.refreshToken,
        tokens.accessExpiresAt.toEpochMilliseconds(),
        tokens.refreshExpiresAt.toEpochMilliseconds(),
        tokens.user,
      )
      val sealed = orNullOnKeyFailure { aead().encrypt(Json.encodeToString(payload).encodeToByteArray(), ASSOCIATED_DATA) }
      if (sealed == null) {
        dropKeyAndValue()
      } else {
        try {
          dataStore.edit { it[TOKENS] = Base64.encode(sealed) }
        } catch (_: IOException) {
        }
      }
    }
  }

  override suspend fun clear(): Unit = mutex.withLock {
    cached = null
    loaded = true
    withContext(Dispatchers.IO) {
      try {
        dataStore.edit { it.remove(TOKENS) }
      } catch (_: IOException) {
        dropKeyAndValue()
      }
    }
  }

  private suspend fun read(): StoredTokens? {
    val stored = try {
      dataStore.data.first()[TOKENS]
    } catch (_: IOException) {
      null
    } ?: return null
    val sealed = runCatching { Base64.decode(stored) }.getOrNull()
    val plain = sealed?.let { orNullOnKeyFailure { aead().decrypt(it, ASSOCIATED_DATA) } }
    val payload = plain?.let { runCatching { Json.decodeFromString<Payload>(it.decodeToString()) }.getOrNull() }
    if (payload == null) {
      dropKeyAndValue()
      return null
    }
    return StoredTokens(
      accessToken = payload.accessToken,
      refreshToken = payload.refreshToken,
      accessExpiresAt = Instant.fromEpochMilliseconds(payload.accessExpiresAt),
      refreshExpiresAt = Instant.fromEpochMilliseconds(payload.refreshExpiresAt),
      user = payload.user,
    )
  }

  private fun aead(): Aead = aead ?: key.aead().also { aead = it }

  private suspend fun dropKeyAndValue() {
    aead = null
    runCatching { key.delete() }
    try {
      dataStore.edit { it.remove(TOKENS) }
    } catch (_: IOException) {
    }
  }

  private inline fun <T> orNullOnKeyFailure(block: () -> T): T? = try {
    block()
  } catch (_: GeneralSecurityException) {
    null
  } catch (_: IOException) {
    null
  }

  companion object {
    private val TOKENS = stringPreferencesKey("tokens")

    private val ASSOCIATED_DATA = "dev.marzahn.tessera/tokens".encodeToByteArray()

    fun create(context: Context): TinkTokenStore = TinkTokenStore(
      PreferenceDataStoreFactory.create(ReplaceFileCorruptionHandler { emptyPreferences() }) {
        context.preferencesDataStoreFile("tokens")
      },
      KeystoreTokenKey(context.applicationContext),
    )
  }
}

class KeystoreTokenKey(private val context: Context) : TokenKey {
  override fun aead(): Aead = try {
    AeadConfig.register()
    AndroidKeysetManager.Builder()
      .withSharedPref(context, KEYSET_NAME, PREFS_FILE)
      .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
      .withMasterKeyUri("android-keystore://$MASTER_KEY_ALIAS")
      .build()
      .keysetHandle
      .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
  } catch (e: ProviderException) {
    throw GeneralSecurityException(e)
  }

  override fun delete() {
    context.deleteSharedPreferences(PREFS_FILE)
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
  }

  private companion object {
    const val PREFS_FILE = "tessera_token_keyset"
    const val KEYSET_NAME = "keyset"
    const val MASTER_KEY_ALIAS = "tessera_token_key"
  }
}
