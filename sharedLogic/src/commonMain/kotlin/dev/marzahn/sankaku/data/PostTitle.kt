package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.api.dto.TagType

object PostTitle {
  const val UNTITLED = "Untitled post"

  private val named = listOf(TagType.Character, TagType.Copyright, TagType.Artist)

  private val notGeneral = named + listOf(TagType.Studio, TagType.Meta, TagType.Language, TagType.Automatic)

  fun of(post: PostDto): String? = of(post.tags)

  fun of(tags: List<TagDto>): String? {
    fun names(picked: List<TagDto>, count: Int) =
      picked.map { it.displayName }.filter { it.isNotBlank() }.distinct().take(count).takeIf { it.isNotEmpty() }
    val (originals, series) = tags.filter { it.category == TagType.Copyright }.partition(::isOriginal)
    for (type in named) {
      val picked = if (type == TagType.Copyright) series else tags.filter { it.category == type }
      names(picked, 2)?.let { return it.joinToString(", ") }
    }
    names(tags.filter { it.category !in notGeneral }, 3)?.let { return it.joinToString(" · ") }
    return names(originals, 2)?.joinToString(", ")
  }

  private fun isOriginal(tag: TagDto): Boolean =
    (tag.tagName ?: tag.name).trim().lowercase().replace(' ', '_') in ORIGINAL_SERIES

  private val ORIGINAL_SERIES = setOf("original", "original_character")
}
