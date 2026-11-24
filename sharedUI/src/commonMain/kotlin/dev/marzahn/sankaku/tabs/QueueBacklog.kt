package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.fonts.icons.check
import androidx.compose.ui.draw.rotate
import com.google.fonts.icons.expand_more
import com.google.fonts.icons.visibility_off
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.images.cachedPostImageRequest
import dev.marzahn.sankaku.images.postImageRequest
import dev.marzahn.sankaku.queue.TodayCounts

@Composable
internal fun TodayStrip(counts: TodayCounts, goneThrough: Int, seen: Int, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  FlowRow(
    modifier
      .padding(horizontal = 12.dp, vertical = 4.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(colors.surfaceContainer)
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .testTag(TODAY_TAG),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      "TODAY",
      style = MaterialTheme.typography.labelMedium,
      color = colors.onSurfaceVariant,
      modifier = Modifier.clearAndSetSemantics {},
    )
    TodayCount("+${grouped(counts.found)}", "found", spoken = "${counts.found} found today")
    TodayCount(
      grouped(goneThrough),
      "gone through",
      spoken = if (goneThrough == 1) "1 kept post gone through today" else "$goneThrough kept posts gone through today",
    )
    TodayCount(grouped(seen), "seen", spoken = if (seen == 1) "1 post seen today" else "$seen posts seen today")
    TodayCount(grouped(counts.liked), "liked", spoken = "${counts.liked} liked today")
  }
}

@Composable
private fun TodayCount(number: String, word: String, spoken: String) {
  val colors = MaterialTheme.colorScheme
  Column(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
    Text(number, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1)
    Text(word, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
  }
}

@Composable
internal fun QueueChips(
  filter: QueueFilter,
  kept: Int,
  new: Int,
  started: Int,
  onFilter: (QueueFilter) -> Unit,
  modifier: Modifier = Modifier,
  later: Int = 0,
) {
  FlowRow(
    modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    QueueChip("All", filter == QueueFilter.All) { onFilter(QueueFilter.All) }
    QueueChip("Kept · ${grouped(kept)}", filter == QueueFilter.Kept) { onFilter(QueueFilter.Kept) }
    if (later > 0 || filter == QueueFilter.Later) QueueChip("Later · ${grouped(later)}", filter == QueueFilter.Later) { onFilter(QueueFilter.Later) }
    QueueChip("New · ${grouped(new)}", filter == QueueFilter.New) { onFilter(QueueFilter.New) }
    QueueChip("Started · ${grouped(started)}", filter == QueueFilter.Started) { onFilter(QueueFilter.Started) }
  }
}

@Composable
private fun QueueChip(label: String, selected: Boolean, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(8.dp)
  Row(
    Modifier
      .heightIn(min = 32.dp)
      .clip(shape)
      .then(if (selected) Modifier.background(colors.secondaryContainer) else Modifier.border(1.dp, colors.outlineVariant, shape))
      .clickable(role = Role.Tab, onClick = onClick)
      .semantics { this.selected = selected }
      .padding(horizontal = 12.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (selected) Icon(check, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onSecondaryContainer)
    Text(
      label,
      style = MaterialTheme.typography.labelLarge,
      color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
    )
  }
}

@Composable
internal fun NewLabel() {
  val colors = MaterialTheme.colorScheme
  Text(
    "NEW",
    style = MaterialTheme.typography.labelSmall,
    color = colors.onTertiaryContainer,
    maxLines = 1,
    modifier = Modifier
      .clip(RoundedCornerShape(9.dp))
      .background(colors.tertiaryContainer)
      .padding(horizontal = 6.dp, vertical = 1.dp)
      .testTag(NEW_LABEL_TAG),
  )
}

@Composable
internal fun TagDot(color: Color) {
  Box(Modifier.size(9.dp).clip(CircleShape).background(color).testTag(TAG_DOT_TAG))
}

@Composable
internal fun PreviewStrip(
  thumbs: List<QueueThumb>?,
  picture: (QueueThumb) -> PostImage,
  blurred: (QueueThumb) -> Boolean,
  modifier: Modifier = Modifier,
  count: Int = STRIP_SIZE,
) {
  val context = LocalPlatformContext.current
  Row(modifier.testTag(STRIP_TAG), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    repeat(count) { at ->
      val thumb = thumbs?.getOrNull(at)
      Box(
        Modifier.size(STRIP_TILE).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        if (thumb != null) {
          val blur = blurred(thumb)
          val image = picture(thumb)
          AsyncImage(
            model = if (image.url != null) postImageRequest(context, image) else cachedPostImageRequest(context, image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(STRIP_TILE).then(if (blur) Modifier.blur(10.dp).testTag(BLURRED_TAG) else Modifier),
          )
          if (blur) BlurredMark()
        }
      }
    }
  }
}

@Composable
internal fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier
      .fillMaxWidth()
      .height(4.dp)
      .clip(RoundedCornerShape(2.dp))
      .background(colors.surfaceContainerHighest)
      .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
      .testTag(PROGRESS_TAG),
  ) {
    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(2.dp)).background(colors.primary))
  }
}

@Composable
internal fun FoldLine(fold: Fold, blurAdult: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier
      .heightIn(min = 34.dp)
      .clip(RoundedCornerShape(17.dp))
      .background(colors.surfaceContainer)
      .clickable(onClick = onToggle)
      .clearAndSetSemantics {
        contentDescription = fold.label
        role = Role.Button
        if (fold.open) collapse { onToggle(); true } else expand { onToggle(); true }
      }
      .padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
      .testTag(FOLD_TAG),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row {
      fold.pictures.forEachIndexed { at, route ->
        val post = route as? Route.Post
        val blur = blurAdult && isAdult(route) { null }
        Box(
          Modifier
            .offset(x = (-6 * at).dp)
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(colors.surfaceVariant),
        ) {
          val md5 = post?.md5
          if (md5 != null) {
            PreviewImage(
              PostImage(post.id, md5, Variant.Preview, url = null),
              Modifier.size(20.dp).then(if (blur) Modifier.blur(6.dp).testTag(BLURRED_TAG) else Modifier),
            )
          }
        }
      }
    }
    Spacer(Modifier.width(if (fold.pictures.size > 1) 0.dp else 8.dp))
    val primary = colors.primary
    Text(
      buildAnnotatedString {
        fold.parts.forEachIndexed { at, part ->
          if (at > 0) append(" · ")
          if (at == 0 && fold.later > 0) withStyle(SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
        }
      },
      style = MaterialTheme.typography.labelLarge,
      color = colors.onSurfaceVariant,
      maxLines = rowTextLines(),
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Icon(expand_more, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp).rotate(if (fold.open) 180f else 0f))
  }
}

@Composable
private fun BlurredMark() {
  Box(
    Modifier.size(20.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(visibility_off, contentDescription = "Blurred", tint = Color.White, modifier = Modifier.size(14.dp))
  }
}

internal const val STRIP_SIZE = 5
private val STRIP_TILE: Dp = 52.dp

internal val BACKLOG_INDENT: Dp = 36.dp

internal const val TODAY_TAG = "today"
internal const val STRIP_TAG = "preview strip"
internal const val PROGRESS_TAG = "progress"
internal const val FOLD_TAG = "fold"
internal const val NEW_LABEL_TAG = "new source"
internal const val TAG_DOT_TAG = "tag dot"
