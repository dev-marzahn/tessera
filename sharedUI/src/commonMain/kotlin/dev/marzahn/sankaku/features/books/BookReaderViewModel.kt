package dev.marzahn.sankaku.features.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.BooksApi
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.data.BookFavourites
import dev.marzahn.sankaku.api.dto.PostDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BookUiState {
  data object Loading : BookUiState

  data class Failed(val error: ApiError) : BookUiState {
    val unreachable: Boolean get() = error is ApiError.Network
  }

  data class Loaded(val book: BookDto, val pages: List<PostDto>) : BookUiState
}

class BookReaderViewModel(
  private val bookId: String,
  private val books: BooksApi,
  private val favourites: BookFavourites? = null,
) : ViewModel() {
  private val _state = MutableStateFlow<BookUiState>(BookUiState.Loading)
  val state: StateFlow<BookUiState> = _state.asStateFlow()

  init {
    load()
  }

  private var loading: Job? = null

  fun load() {
    loading?.cancel()
    _state.value = BookUiState.Loading
    loading = viewModelScope.launch {
      _state.value = try {
        val book = if (favourites == null) books.pages(bookId) else favourites.fresh({ books.pages(bookId) }) { listOf(it) }
        BookUiState.Loaded(book, readingOrder(book.posts))
      } catch (e: ApiError) {
        BookUiState.Failed(e)
      }
    }
  }
}

internal fun readingOrder(posts: List<PostDto>): List<PostDto> = posts.sortedBy { it.sequence ?: Int.MAX_VALUE }
