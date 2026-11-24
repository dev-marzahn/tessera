package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.dto.PostDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

enum class Variant { Preview, Sample, File }

data class PostUrls(val preview: String?, val sample: String?, val file: String?) {
  fun forVariant(variant: Variant): String? = when (variant) {
    Variant.Preview -> preview
    Variant.Sample -> sample
    Variant.File -> file
  }
}

@Serializable
data class VoteState(val userVote: Int?, val voteCount: Int, val totalScore: Int)

data class PostOverride(
  val isFavourited: Boolean? = null,
  val favCount: Int? = null,
  val urls: PostUrls? = null,
  val inFlight: Boolean = false,
  val generation: Long = 0,
  val vote: VoteState? = null,
  val voteInFlight: Boolean = false,
  val voteGeneration: Long = 0,
)

fun PostDto.url(variant: Variant): String? = when (variant) {
  Variant.Preview -> previewUrl
  Variant.Sample -> sampleUrl
  Variant.File -> fileUrl
}

fun PostDto.merge(override: PostOverride?): PostDto {
  override ?: return this
  return copy(
    isFavorited = override.isFavourited ?: isFavorited,
    favCount = override.favCount ?: favCount,
    previewUrl = override.urls?.preview ?: previewUrl,
    sampleUrl = override.urls?.sample ?: sampleUrl,
    fileUrl = override.urls?.file ?: fileUrl,
    userVote = if (override.vote != null) override.vote.userVote else userVote,
    voteCount = override.vote?.voteCount ?: voteCount,
    totalScore = override.vote?.totalScore ?: totalScore,
  )
}

fun PostDto.voteState(): VoteState = VoteState(userVote, voteCount, totalScore)

fun PostDto.urls(): PostUrls = PostUrls(previewUrl, sampleUrl, fileUrl)

class PostStateStore {
  private val _overrides = MutableStateFlow<Map<String, PostOverride>>(emptyMap())
  val overrides: StateFlow<Map<String, PostOverride>> = _overrides.asStateFlow()

  fun beginToggle(id: String, value: Boolean, favCount: Int): Long {
    var generation = 0L
    _overrides.update { all ->
      val current = all[id] ?: PostOverride()
      generation = current.generation + 1
      all + (id to current.copy(isFavourited = value, favCount = favCount, inFlight = true, generation = generation))
    }
    return generation
  }

  fun setFavourite(id: String, value: Boolean, favCount: Int, inFlight: Boolean, generation: Long): Boolean {
    var written = false
    _overrides.update { all ->
      val current = all[id] ?: return@update all
      if (current.generation != generation) return@update all
      written = true
      all + (id to current.copy(isFavourited = value, favCount = favCount, inFlight = inFlight))
    }
    return written
  }

  fun beginVote(id: String, vote: VoteState): Long {
    var generation = 0L
    _overrides.update { all ->
      val current = all[id] ?: PostOverride()
      generation = current.voteGeneration + 1
      all + (id to current.copy(vote = vote, voteInFlight = true, voteGeneration = generation))
    }
    return generation
  }

  fun setVote(id: String, vote: VoteState, generation: Long, inFlight: Boolean = false) {
    _overrides.update { all ->
      val current = all[id] ?: return@update all
      if (current.voteGeneration != generation) return@update all
      all + (id to current.copy(vote = vote, voteInFlight = inFlight))
    }
  }

  fun setServerVote(post: PostDto) {
    _overrides.update { all ->
      val current = all[post.id] ?: PostOverride()
      if (current.voteInFlight) return@update all
      all + (post.id to current.copy(vote = post.voteState()))
    }
  }

  fun restorePending(changes: List<PendingChange>) {
    _overrides.update { all ->
      changes.fold(all) { acc, change ->
        val current = acc[change.id] ?: PostOverride()
        when (change) {
          is PendingChange.Favourite -> acc + (
            change.postId to current.copy(
              isFavourited = change.favourited,
              favCount = change.favCount,
              inFlight = true,
              generation = current.generation + 1,
            )
          )
          is PendingChange.Vote -> acc + (
            change.postId to current.copy(vote = change.vote, voteInFlight = true, voteGeneration = current.voteGeneration + 1)
          )
          is PendingChange.Follow -> acc
        }
      }
    }
  }

  fun setUrls(post: PostDto) {
    _overrides.update { all -> all + (post.id to (all[post.id] ?: PostOverride()).copy(urls = post.urls())) }
  }

  fun takeListed(posts: List<PostDto>, whenAsked: Map<String, PostOverride>) {
    _overrides.update { all ->
      posts.fold(all) { acc, post ->
        val current = acc[post.id] ?: return@fold acc
        val before = whenAsked[post.id] ?: PostOverride()
        val favouriteSettled = current.isFavourited != null && !before.inFlight && !current.inFlight &&
          before.generation == current.generation
        val voteSettled = !before.voteInFlight && !current.voteInFlight && before.voteGeneration == current.voteGeneration &&
          before.vote == current.vote
        val listed = post.urls()
        acc + (
          post.id to current.copy(
            isFavourited = if (favouriteSettled) post.isFavorited else current.isFavourited,
            favCount = if (favouriteSettled) post.favCount else current.favCount,
            urls = if (current.urls != null && before.urls == current.urls) {
              PostUrls(listed.preview ?: current.urls.preview, listed.sample ?: current.urls.sample, listed.file ?: current.urls.file)
            } else {
              current.urls
            },
            vote = if (voteSettled) current.vote?.copy(voteCount = post.voteCount, totalScore = post.totalScore) else current.vote,
          )
        )
      }
    }
  }

  fun clear() {
    _overrides.value = emptyMap()
  }
}
