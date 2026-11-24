package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.ui.components.TagBadgeHeight
import dev.marzahn.sankaku.ui.components.BadgeLabel
import dev.marzahn.sankaku.ui.theme.Tinted
import dev.marzahn.sankaku.ui.theme.tinted

val Rating.color: Color
  get() = when (this) {
    Rating.G -> Color(0xFF2E9D4F)
    Rating.R15 -> Color(0xFFD99A00)
    Rating.R18 -> Color(0xFFD93636)
  }

fun Rating.tinted(panel: Color): Tinted = tinted(color, panel, strength = 0.4f)

enum class RatingBadgeSize(val height: Dp) { Small(20.dp), Tag(TagBadgeHeight) }

@Composable
fun RatingBadge(rating: Rating, modifier: Modifier = Modifier, size: RatingBadgeSize = RatingBadgeSize.Small) {
  val tag = size == RatingBadgeSize.Tag
  val tint = rating.tinted(MaterialTheme.colorScheme.surfaceContainerLowest)
  BadgeLabel(
    rating.badge,
    color = tint.text,
    style = if (tag) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    modifier = modifier
      .defaultMinSize(minWidth = size.height, minHeight = size.height)
      .background(tint.fill, CircleShape)
      .padding(horizontal = if (tag) 8.dp else 4.dp, vertical = 1.dp),
  )
}

@Composable
fun RatingStack(ratings: Set<Rating>, modifier: Modifier = Modifier, size: RatingBadgeSize = RatingBadgeSize.Small) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy((-6).dp), verticalAlignment = Alignment.CenterVertically) {
    Rating.entries.filter { it in ratings }.forEach { RatingBadge(it, size = size) }
  }
}
