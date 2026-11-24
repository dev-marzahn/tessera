package dev.marzahn.sankaku.features.post

import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.couldNot
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.data.OutboxSender
import dev.marzahn.sankaku.data.PendingChange
import dev.marzahn.sankaku.data.PostOverride
import dev.marzahn.sankaku.data.PostStateStore
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.merge
import dev.marzahn.sankaku.data.TagFollows
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.components.UNDO
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

sealed interface Rejection {
  data class Favourite(val postId: String, val viaDoubleTap: Boolean = false) : Rejection

  data class Vote(val postId: String) : Rejection

  data class Follow(val tagName: String) : Rejection
}

enum class FollowOutcome { Followed, AlreadyFollowed, Refused }

data class FavouriteHeart(val postId: String, val favourited: Boolean)

fun Flow<FavouriteHeart>.forShown(shownId: () -> String?): Flow<Boolean> = filter { it.postId == shownId() }.map { it.favourited }

class PostActions(
  private val repository: PostsRepository,
  private val session: AuthSession,
  private val signIn: SignInController,
  private val messages: Messages,
  private val scope: CoroutineScope,
  val follows: TagFollows,
  private val onActed: (PostDto) -> Unit = {},
  refusals: Flow<OutboxSender.Refusal> = emptyFlow(),
  private val store: PostStateStore,
  private val nextAction: suspend () -> Unit = { awaitCancellation() },
) {

  fun actedOn(post: PostDto) = onActed(post)

  private val shown = MutableStateFlow<String?>(null)

  fun showing(postId: String) {
    shown.value = postId
  }

  private val _rejections = MutableSharedFlow<Rejection>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val rejections: SharedFlow<Rejection> = _rejections.asSharedFlow()

  private val _hearts = MutableSharedFlow<FavouriteHeart>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val hearts: SharedFlow<FavouriteHeart> = _hearts.asSharedFlow()

  private fun reject(rejection: Rejection, reason: String) {
    _rejections.tryEmit(rejection)
    messages.showError(reason)
  }

  init {
    scope.launch { session.isSignedIn.drop(1).collect { follows.clear() } }
    scope.launch { refusals.collect(::refused) }
  }

  private fun refused(refusal: OutboxSender.Refusal) {
    val error = refusal.error
    when (val change = refusal.change) {
      is PendingChange.Favourite -> {
        _hearts.tryEmit(FavouriteHeart(change.postId, change.wasFavourited))
        reject(Rejection.Favourite(change.postId), error.couldNot(if (change.favourited) "favourite" else "unfavourite"))
      }
      is PendingChange.Vote -> reject(
        Rejection.Vote(change.postId),
        error.couldNot(if (change.vote.userVote == null) "take the vote back" else "vote"),
      )
      is PendingChange.Follow -> reject(
        Rejection.Follow(change.name),
        error.couldNot("${if (change.following) "follow" else "unfollow"} ${change.tag.displayName}"),
      )
    }
  }

  val isSignedIn: StateFlow<Boolean> get() = session.isSignedIn

  fun openSignIn() = signIn.open()

  private fun signInThen(reason: String, action: suspend () -> Unit) {
    signIn.open(reason) {
      scope.launch {
        repository.awaitCurrentAccount()
        if (session.isSignedIn.value) action()
      }
    }
  }

  fun toggleFavourite(post: PostDto, viaDoubleTap: Boolean = false, fromGrid: Boolean = false): Boolean {
    if (!fromGrid) onActed(post)
    if (!session.isSignedIn.value) {
      signInThen("to favourite") {
        val current = try {
          repository.fullPost(post.id)
        } catch (_: ApiError) {
          post
        }
        if (!current.isFavorited) favourite(current, viaDoubleTap, playsHeart = false)
      }
      return false
    }
    val playsHeart = !fromGrid
    if (playsHeart) _hearts.tryEmit(FavouriteHeart(post.id, !post.isFavorited))
    scope.launch { favourite(post, viaDoubleTap, playsHeart) }
    if (!fromGrid && post.isFavorited) messages.show(unfavouritedMessage(post))
    return true
  }

  private fun unfavouritedMessage(post: PostDto): Message {
    fun favourited(overrides: Map<String, PostOverride>) = post.merge(overrides[post.id]).isFavorited
    val favouritedAgain = store.overrides.dropWhile(::favourited).filter(::favourited)
    val movedOn = shown.drop(1).filter { it != post.id }
    val until: suspend () -> Unit = { merge(flow { emit(nextAction()) }, favouritedAgain.map {}, movedOn.map {}).first() }
    return Message("Unfavourited", actionLabel = UNDO, until = until) {
      val current = post.merge(store.overrides.value[post.id])
      if (current.isFavorited) return@Message
      _hearts.tryEmit(FavouriteHeart(post.id, favourited = true))
      scope.launch { favourite(current, viaDoubleTap = false, playsHeart = true) }
    }
  }

  fun addFavourite(post: PostDto): Boolean {
    if (!post.isFavorited || !session.isSignedIn.value) return toggleFavourite(post)
    onActed(post)
    _hearts.tryEmit(FavouriteHeart(post.id, favourited = true))
    return true
  }

  private suspend fun favourite(post: PostDto, viaDoubleTap: Boolean, playsHeart: Boolean) {
    repository.toggleFavourite(post).onFailure {
      if (playsHeart) _hearts.tryEmit(FavouriteHeart(post.id, post.isFavorited))
      reject(
        Rejection.Favourite(post.id, viaDoubleTap),
        it.couldNot(if (post.isFavorited) "unfavourite" else "favourite"),
      )
    }
  }

  fun vote(post: PostDto, score: Int?) {
    onActed(post)
    if (!session.isSignedIn.value) {
      signInThen("to vote") { castVote(post, score) }
      return
    }
    scope.launch { castVote(post, score) }
  }

  private suspend fun castVote(post: PostDto, score: Int?) {
    repository.vote(post, score).onFailure {
      reject(Rejection.Vote(post.id), it.couldNot(if (score == null) "take the vote back" else "vote"))
    }
  }

  fun toggleFollow(tag: TagDto) {
    if (!session.isSignedIn.value) {
      signIn.open()
      return
    }
    val following = follows.isFollowing(tag)
    scope.launch {
      follows.toggle(tag).onFailure {
        reject(Rejection.Follow(tag.name), it.couldNot("${if (following) "unfollow" else "follow"} ${tag.displayName}"))
      }
    }
  }

  suspend fun follow(tag: TagDto): FollowOutcome {
    if (!session.isSignedIn.value) return FollowOutcome.Refused
    if (follows.isFollowing(tag)) return FollowOutcome.AlreadyFollowed
    return scope.async {
      if (follows.isFollowing(tag)) return@async FollowOutcome.AlreadyFollowed
      val result = follows.toggle(tag).onFailure { reject(Rejection.Follow(tag.name), it.couldNot("follow ${tag.displayName}")) }
      if (result.isSuccess) FollowOutcome.Followed else FollowOutcome.Refused
    }.await()
  }

  fun unfollow(tag: TagDto) {
    if (follows.isFollowing(tag)) toggleFollow(tag)
  }
}

@Composable
fun rememberRejections(actions: PostActions, rejection: Rejection): Int {
  var count by remember(rejection) { mutableIntStateOf(0) }
  LaunchedEffect(actions, rejection) { actions.rejections.collect { if (it == rejection) count++ } }
  return count
}
