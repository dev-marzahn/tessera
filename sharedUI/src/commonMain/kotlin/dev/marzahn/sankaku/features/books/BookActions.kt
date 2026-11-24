package dev.marzahn.sankaku.features.books

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.marzahn.sankaku.api.couldNot
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.data.BookFavourites
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class BookActions(
  val favourites: BookFavourites,
  private val session: AuthSession,
  private val signIn: SignInController,
  private val messages: Messages,
  private val scope: CoroutineScope,
) {
  private val _rejections = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val rejections: SharedFlow<String> = _rejections.asSharedFlow()

  private val _hearts = MutableSharedFlow<BookHeart>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val hearts: SharedFlow<BookHeart> = _hearts.asSharedFlow()

  fun toggleFavourite(book: BookDto) {
    val emitHeart: (Boolean) -> Unit = { on -> _hearts.tryEmit(BookHeart(book.id, on)) }
    if (!session.isSignedIn.value) {
      signIn.open("to favourite") {
        scope.launch {
          if (session.isSignedIn.value) report(book, wasOn = false, favourites.favouriteUnlessAlready(book, emitHeart))
        }
      }
      return
    }
    scope.launch {
      val wasOn = favourites.current(book).on
      report(book, wasOn, favourites.toggle(book, emitHeart))
    }
  }

  private fun report(book: BookDto, wasOn: Boolean, result: Result<*>) {
    result.onFailure {
      _rejections.tryEmit(book.id)
      messages.showError(it.couldNot(if (wasOn) "unfavourite" else "favourite"))
    }
  }
}

data class BookHeart(val bookId: String, val favourited: Boolean)

@Composable
internal fun rememberBookRejections(actions: BookActions, bookId: String): Int {
  var count by remember(bookId) { mutableIntStateOf(0) }
  LaunchedEffect(actions, bookId) { actions.rejections.collect { if (it == bookId) count++ } }
  return count
}
