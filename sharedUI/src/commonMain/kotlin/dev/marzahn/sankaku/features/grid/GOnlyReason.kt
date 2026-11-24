package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.api.dto.TagSuggestion
import dev.marzahn.sankaku.data.GridPresets

internal enum class GOnlyReason(val text: String, val action: String?, val droppedRating: String, val hint: String) {
  SignedOut(
    "Signed out: G only",
    "Sign in",
    "Ratings stay G while signed out",
    "Signed out, searches show G only. Sign in to choose other ratings.",
  ),

  Switch("G only is on", "Turn off", "Ratings stay G while G only is on", "G only is on. Turn it off to choose other ratings."),

  Held("G only is on", null, "Ratings stay G while G only is on", "G only is on for this run."),
}

internal fun gOnlyReason(signedIn: Boolean, gOnly: Boolean, canTurnOff: Boolean): GOnlyReason? = when {
  !signedIn -> GOnlyReason.SignedOut
  !gOnly -> null
  canTurnOff -> GOnlyReason.Switch
  else -> GOnlyReason.Held
}

@Composable
internal fun GOnlyReasonLine(
  reason: GOnlyReason,
  onAction: () -> Unit,
  modifier: Modifier = Modifier,
  badge: Boolean = true,
) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    if (badge) RatingBadge(Rating.G, Modifier.clearAndSetSemantics {})
    Text(reason.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    reason.action?.let { TextButton(onClick = onAction) { Text(it) } }
  }
}

internal enum class EmptyFeed {
  Following,
  Favourites,
  ;

  companion object {
    fun of(searched: List<String>, me: String?, ratingLocked: Boolean): EmptyFeed? {
      val listed = (if (ratingLocked) withoutRating(searched) else searched).map(String::lowercase)
      return when {
        listed == GridPresets.following -> Following
        me != null && listed == GridPresets.favourites(me).map(String::lowercase) -> Favourites
        else -> null
      }
    }
  }
}

internal fun shownRecentSearches(
  recent: List<List<String>>,
  known: Map<String, TagSuggestion>,
  gOnly: Boolean,
): List<List<String>> = if (!gOnly) recent else recent.filterNot { search -> search.any { namesNotG(it, known) } }

private fun namesNotG(tag: String, known: Map<String, TagSuggestion>): Boolean {
  Rating.of(tag)?.let { ratings -> return ratings.any { it != Rating.G } }
  return Rating.ofCode(known[tag.removePrefix("-").lowercase()]?.rating) in setOf(Rating.R15, Rating.R18)
}
