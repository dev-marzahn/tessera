package dev.marzahn.sankaku.features.post

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.components.spatialSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.favorite
import com.google.fonts.icons.favorite_filled
import com.google.fonts.icons.star
import com.google.fonts.icons.star_filled
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.PostTitle
import dev.marzahn.sankaku.ui.components.ConnectedGroup
import dev.marzahn.sankaku.ui.components.ConnectedLeadingPane
import dev.marzahn.sankaku.ui.components.ConnectedTrailingToggle
import dev.marzahn.sankaku.ui.components.KeyHint
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.effectsSpec
import kotlin.math.roundToInt

val POST_TITLE_HEIGHT: Dp = 64.dp

@Composable
fun PostTitle(post: PostDto, endInset: Dp, modifier: Modifier = Modifier, scrolled: Boolean = false) {
  val container by animateColorAsState(
    if (scrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f),
    effectsSpec(),
  )
  Column(
    modifier.fillMaxWidth().height(POST_TITLE_HEIGHT).background(container).padding(start = 16.dp, end = endInset),
    verticalArrangement = Arrangement.Center,
  ) {
    val title = PostTitle.of(post)
    Text(
      title ?: PostTitle.UNTITLED,
      color = if (title == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    val byline = postByline(post)
    if (byline.isNotEmpty()) {
      Text(
        byline,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun PostStats(
  post: PostDto,
  onToggleFavourite: () -> Unit,
  onVote: (Int?) -> Unit,
  modifier: Modifier = Modifier,
  voteRejected: Int = 0,
  favouriteRejected: Int = 0,
  onSignIn: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  mirrored: Boolean = false,
  countsApart: Boolean = false,
  leading: (@Composable () -> Unit)? = null,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      if (mirrored && trailing != null) {
        trailing()
        Spacer(Modifier.width(4.dp))
      }
      if (!mirrored && leading != null) {
        leading()
        Spacer(Modifier.width(8.dp))
      }
      ConnectedGroup(Modifier.weight(1f)) {
        val average = averageVote(post)
        val favourite: @Composable RowScope.() -> Unit = {
          ConnectedTrailingToggle(
            checked = post.isFavorited,
            onCheckedChange = { onToggleFavourite() },
            rejected = favouriteRejected,
            mirrored = mirrored,
            iconOnly = countsApart,
            modifier = Modifier.semantics {
              contentDescription =
                favouriteDescription(compactCount(post.favCount), Shortcuts.Favourite.label.takeUnless { platformIsTouch })
            },
          ) {
            Icon(
              if (post.isFavorited) favorite_filled else favorite,
              contentDescription = null,
              tint = if (post.isFavorited) MaterialTheme.colorScheme.primary else LocalContentColor.current,
              modifier = Modifier.size(20.dp),
            )
            if (!countsApart) {
              Spacer(Modifier.width(8.dp))
              Text(
                compactCount(post.favCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clearAndSetSemantics {},
              )
            }
          }
        }
        if (mirrored) favourite()
        ConnectedLeadingPane(
          checked = post.userVote != null,
          rejected = voteRejected,
          mirrored = mirrored,
          modifier = Modifier.weight(1f).clearAndSetSemantics { voteSemantics(post, onVote, onSignIn) },
        ) {
          if (countsApart) {
            VoteStars(average, post.userVote, onVote, cell = TouchStarCell, narrows = leading != null)
          } else {
            StarsAndCounts(
            stars = { VoteStars(average, post.userVote, onVote) },
            counts = {
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  averageLabel(average),
                  style = MaterialTheme.typography.labelLarge,
                  color = if (average == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                  maxLines = 1,
                  softWrap = false,
                )
                votesLabel(post.voteCount)?.let {
                  Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }
            },
            modifier = Modifier.weight(1f),
            )
          }
        }
        if (!mirrored) favourite()
      }
      if (mirrored && leading != null) {
        Spacer(Modifier.width(8.dp))
        leading()
      }
      if (!mirrored && trailing != null) {
        Spacer(Modifier.width(4.dp))
        trailing()
      }
    }
    if (!platformIsTouch) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        KeyHint(Shortcuts.Vote.label, "vote")
        KeyHint(Shortcuts.Favourite.label, "favourite")
      }
    }
  }
}

@Composable
internal fun StarsAndCounts(stars: @Composable () -> Unit, counts: @Composable () -> Unit, modifier: Modifier = Modifier) {
  Layout(listOf(stars, counts), modifier) { (starsMeasurables, countsMeasurables), constraints ->
    val countsPlaceable = countsMeasurables.single().measure(Constraints())
    val room = (constraints.maxWidth - countsPlaceable.width).coerceAtLeast(0)
    val star = StarSize.toPx()
    val cell = minOf(StarCell.toPx(), (room + star / 2) / 5.5f)
    val inset = ((cell - star) / 2).coerceAtLeast(0f).roundToInt()
    val starsPlaceable = starsMeasurables.single().measure(Constraints(maxWidth = (room - inset).coerceAtLeast(0)))
    val width = constraints.constrainWidth(room + countsPlaceable.width)
    val height = constraints.constrainHeight(maxOf(starsPlaceable.height, countsPlaceable.height))
    layout(width, height) {
      starsPlaceable.place(0, (height - starsPlaceable.height) / 2)
      countsPlaceable.place(width - inset - countsPlaceable.width, (height - countsPlaceable.height) / 2)
    }
  }
}

@Composable
private fun VoteStars(
  average: Double?,
  userVote: Int?,
  onVote: (Int?) -> Unit,
  modifier: Modifier = Modifier,
  cell: Dp = StarCell,
  narrows: Boolean = true,
) {
  var hovered by remember { mutableIntStateOf(0) }
  val shown = when {
    hovered > 0 -> hovered
    userVote != null -> userVote
    else -> average?.roundToInt() ?: 0
  }
  val own = hovered > 0 || userVote != null
  val pop = remember { Animatable(1f) }
  var landed by remember { mutableStateOf(userVote) }
  val popSpec = spatialSpec<Float>()
  LaunchedEffect(userVote) {
    if (userVote != null && userVote != landed) {
      pop.snapTo(1.3f)
      pop.animateTo(1f, popSpec)
    }
    landed = userVote
  }
  Row(
    modifier.pointerInput(Unit) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent()
          val change = event.changes.firstOrNull()?.takeIf { it.type == PointerType.Mouse } ?: continue
          val width = minOf(cell.toPx(), size.width / 5f)
          hovered = if (event.type == PointerEventType.Exit) 0 else (change.position.x / width).toInt().coerceIn(0, 4) + 1
        }
      }
    },
  ) {
    for (n in 1..5) {
      val filled = n <= shown
      val popping = userVote != null && n <= userVote
      Box(
        Modifier
          .then(if (narrows) Modifier.weight(1f, fill = false) else Modifier)
          .size(cell)
          .clip(CircleShape)
          .clickable { onVote(if (n == userVote) null else n) },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          if (filled) star_filled else star,
          contentDescription = null,
          tint = when {
            !filled -> MaterialTheme.colorScheme.outline
            own -> MaterialTheme.colorScheme.primary
            else -> StarColor
          },
          modifier = Modifier.size(StarSize).graphicsLayer {
            val scale = if (popping) pop.value else 1f
            scaleX = scale
            scaleY = scale
          },
        )
      }
    }
  }
}

private val TouchStarCell = 40.dp

private val StarCell = if (platformIsTouch) TouchStarCell else 24.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostCounts(post: PostDto, modifier: Modifier = Modifier) {
  val average = averageVote(post)
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  FlowRow(
    modifier.clearAndSetSemantics {},
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    Text(averageLabel(average), style = MaterialTheme.typography.labelLarge, color = if (average == null) muted else Color.Unspecified)
    votesLabel(post.voteCount)?.let {
      Text("·", style = MaterialTheme.typography.labelLarge, color = muted)
      Text(it, style = MaterialTheme.typography.labelLarge, color = muted)
    }
    Text("·", style = MaterialTheme.typography.labelLarge, color = muted)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      Icon(favorite, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
      Text(compactCount(post.favCount), style = MaterialTheme.typography.labelLarge, color = muted)
    }
  }
}

private val StarSize = 20.dp

private val StarColor = Color(0xFFE8A200)

internal fun postByline(post: PostDto): String =
  listOfNotNull(post.author?.name, post.createdAt?.toString()?.take(10)).joinToString(" · ")

internal fun averageVote(post: PostDto): Double? =
  if (post.voteCount > 0) (post.totalScore.toDouble() / post.voteCount * 10).roundToInt() / 10.0 else null

internal fun averageLabel(average: Double?): String = average?.toString() ?: "No votes"

internal fun votesLabel(count: Int): String? = when (count) {
  0 -> null
  1 -> "1 vote"
  else -> "${compactCount(count)} votes"
}
