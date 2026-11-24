package dev.marzahn.sankaku.features.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.menu_book
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.data.BooksRepository
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.features.grid.ratingLocked
import dev.marzahn.sankaku.ui.openOnClick
import kotlinx.coroutines.flow.first
import dev.marzahn.sankaku.Navigator
import org.koin.compose.koinInject

@Composable
fun FromTheBook(
  post: PostDto,
  modifier: Modifier = Modifier,
  onScreen: () -> Boolean = { true },
  onOpen: () -> Unit = {},
  books: BooksRepository = koinInject(),
  opener: BookOpener = koinInject(),
  navigator: Navigator = koinInject(),
  session: AuthSession = koinInject(),
  privacy: PrivacySettings = koinInject(),
) {
  val signedIn by session.isSignedIn.collectAsState()
  val gOnly by privacy.gOnly.collectAsState()
  val locked = ratingLocked(signedIn, gOnly)
  val asked = Asked(post.id, signedIn, locked)
  val kept = remember(asked) { books.cachedBookOf(post, signedIn, locked) }
  val visible by rememberUpdatedState(onScreen)
  val book by produceState(kept?.let { asked to it }, asked, post.inVisiblePool) {
    snapshotFlow { visible() }.first { it }
    value = try {
      books.bookOf(post, signedIn, locked)?.let { asked to it }
    } catch (_: ApiError) {
      null
    }
  }
  val shown = book?.takeIf { it.first == asked }?.second ?: kept ?: return
  FromTheBookRow(
    shown,
    modifier,
    open = {
      onOpen()
      opener.open(shown)
    },
    keep = {
      onOpen()
      navigator.openInNewTab(shown.keptRoute())
    },
  )
}

@Composable
private fun FromTheBookRow(book: BookDto, modifier: Modifier, open: () -> Unit, keep: () -> Unit) {
  val label = "From the book ${book.title}"
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = ROW_SHAPE,
    modifier = modifier
      .fillMaxWidth()
      .clip(ROW_SHAPE)
      .openOnClick(onOpen = open, onOpenInNewTab = keep)
      .clearAndSetSemantics {
        contentDescription = label
        role = Role.Button
      },
  ) {
    Row(
      Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(menu_book, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
      Column {
        Text("From the book", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          book.title,
          style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private data class Asked(val postId: String, val signedIn: Boolean, val ratingLocked: Boolean)

private val ROW_SHAPE = RoundedCornerShape(12.dp)
