package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.TagsApi
import dev.marzahn.sankaku.api.dto.TagDto

class FollowedTagList(private val api: TagsApi, private val pageSize: Int = TagsApi.FOLLOWED_PAGE_SIZE) {
  suspend fun all(): List<TagDto> {
    val tags = LinkedHashMap<String, TagDto>()
    for (page in 1..MAX_PAGES) {
      val got = api.followedTags(page, pageSize)
      val before = tags.size
      got.forEach { tags.putIfAbsent(it.id ?: it.searchName, it) }
      if (got.size < pageSize || tags.size == before) break
    }
    return tags.values.toList()
  }

  suspend fun idsOrNull(): List<String>? = try {
    all().mapNotNull { it.id }
  } catch (e: ApiError) {
    null
  }

  private companion object {
    const val MAX_PAGES = 50
  }
}
