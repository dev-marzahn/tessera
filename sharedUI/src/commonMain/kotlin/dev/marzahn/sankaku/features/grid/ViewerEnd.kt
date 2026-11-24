package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import dev.marzahn.sankaku.ui.components.AppLoadingIndicator
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.shell.LocalOpenSearch
import dev.marzahn.sankaku.ui.components.ApiErrorState
import org.koin.compose.koinInject

sealed interface ViewerEnd {
  data object Loading : ViewerEnd

  data class Failed(val error: Throwable) : ViewerEnd

  data object NoMore : ViewerEnd
}

fun viewerEnd(append: LoadState): ViewerEnd = when {
  append is LoadState.Error -> ViewerEnd.Failed(append.error)
  append is LoadState.NotLoading && append.endOfPaginationReached -> ViewerEnd.NoMore
  else -> ViewerEnd.Loading
}

internal const val LoadingMorePosts = "Loading more posts"
internal const val NoMorePosts = "No more posts"
internal const val ViewerEndTag = "viewer-end"

@Composable
fun ViewerEndPage(end: ViewerEnd, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { EndPageContent(end, onRetry, modifier) }
}

@Composable
private fun EndPageContent(end: ViewerEnd, onRetry: () -> Unit, modifier: Modifier, signIn: SignInController = koinInject()) {
  Box(modifier.fillMaxSize().testTag(ViewerEndTag).semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
    when (end) {
      ViewerEnd.Loading -> Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        AppLoadingIndicator()
        Text(LoadingMorePosts, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
      }
      is ViewerEnd.Failed -> ApiErrorState(end.error, onRetry = onRetry, onEditSearch = LocalOpenSearch.current, onSignIn = signIn::open)
      ViewerEnd.NoMore -> Text(NoMorePosts, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
  }
}
