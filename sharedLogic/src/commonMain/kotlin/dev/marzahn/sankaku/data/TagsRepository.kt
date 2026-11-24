package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.TagsApi
import dev.marzahn.sankaku.api.dto.TagAndWiki
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.api.dto.TagSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class TagsRepository(private val api: TagsApi, private val file: TextFile = InMemoryTextFile()) {
  private val lock = Mutex()

  private val suggestions = LinkedHashMap<String, List<TagSuggestion>>()

  private val missing = mutableSetOf<String>()

  private val _known = MutableStateFlow(load())

  val known: StateFlow<Map<String, TagSuggestion>> = _known.asStateFlow()

  private val _unknown = MutableStateFlow<Set<String>>(emptySet())

  val unknown: StateFlow<Set<String>> = _unknown.asStateFlow()

  suspend fun page(tagName: String): TagAndWiki = api.tagAndWiki(tagName)

  suspend fun suggest(prefix: String): List<TagSuggestion> {
    val key = prefix.trim().lowercase()
    if (key.removePrefix("-").length < MIN_PREFIX) return emptyList()
    lock.withLock { suggestions[key]?.also { suggestions.putBounded(key, it, CACHE_SIZE) } }?.let { return it }
    val fetched = api.autocomplete(key)
    lock.withLock { suggestions.putBounded(key, fetched, CACHE_SIZE) }
    learn(fetched)
    return fetched
  }

  suspend fun forget() {
    lock.withLock {
      suggestions.clear()
      missing.clear()
      _unknown.value = emptySet()
      _known.value = emptyMap()
      withContext(Dispatchers.Default) { runCatching { file.write(json.encodeToString(emptyList<TagSuggestion>())) } }
    }
  }

  suspend fun learnFromPosts(tags: List<TagDto>) = learn(
    tags.map { TagSuggestion(tagName = it.searchName, name = it.name, type = it.type, postCount = it.postCount) },
  )

  suspend fun lookUp(keys: List<String>) {
    for (key in keys.map { it.removePrefix("-").lowercase() }.distinct()) {
      if (':' in key || key.length < MIN_PREFIX || key in _known.value) continue
      if (!lock.withLock { missing.add(key) }) continue
      val found = runCatching { api.autocomplete(key) }.getOrNull() ?: continue
      val match = found.firstOrNull { it.tagName.equals(key, ignoreCase = true) }
      if (match != null) learn(listOf(match)) else lock.withLock { if (key !in _known.value) _unknown.value += key }
    }
  }

  private suspend fun learn(found: List<TagSuggestion>) {
    if (found.isEmpty()) return
    lock.withLock {
      val known = _known.value
      val fresh = found.map { if (it.tagName.startsWith("-")) it.copy(tagName = it.tagName.removePrefix("-")) else it }
      val added = known + fresh.associate { it.tagName.lowercase().let { key -> key to it.over(known[key]) } }
      _unknown.value -= fresh.map { it.tagName.lowercase() }.toSet()
      if (added == known) return
      val bounded = if (added.size <= KNOWN_SIZE) added else added.entries.drop(added.size - KNOWN_SIZE).associate { it.toPair() }
      _known.value = bounded
      withContext(Dispatchers.Default) { runCatching { file.write(json.encodeToString(bounded.values.toList())) } }
    }
  }

  private fun load(): Map<String, TagSuggestion> = runCatching {
    file.read()?.let { json.decodeFromString<List<TagSuggestion>>(it) }.orEmpty().associateBy { it.tagName.lowercase() }
  }.getOrDefault(emptyMap())

  companion object {
    const val MIN_PREFIX = 2
    private const val CACHE_SIZE = 100
    private const val KNOWN_SIZE = 5000
    private val json = Json { ignoreUnknownKeys = true }
  }
}
