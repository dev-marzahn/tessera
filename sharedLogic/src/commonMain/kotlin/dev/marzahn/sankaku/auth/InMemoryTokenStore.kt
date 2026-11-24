package dev.marzahn.sankaku.auth

import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryTokenStore(initial: StoredTokens? = null) : TokenStore {
  private val tokens = MutableStateFlow(initial)

  override suspend fun load(): StoredTokens? = tokens.value

  override suspend fun save(tokens: StoredTokens) {
    this.tokens.value = tokens
  }

  override suspend fun clear() {
    tokens.value = null
  }
}
