package dev.marzahn.sankaku.features.post

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.add as addIcon
import com.google.fonts.icons.close as closeIcon
import com.google.fonts.icons.info as infoIcon
import com.google.fonts.icons.search as searchIcon
import com.google.fonts.icons.visibility_off as visibilityOffIcon
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.data.SearchTags
import dev.marzahn.sankaku.features.post.TagMenuEntry.SearchKind
import dev.marzahn.sankaku.plainTextClip
import dev.marzahn.sankaku.ui.components.GroupedMenu
import dev.marzahn.sankaku.ui.components.LaterButton
import dev.marzahn.sankaku.ui.components.MenuAction
import dev.marzahn.sankaku.ui.components.MenuGroup
import dev.marzahn.sankaku.ui.openOnClick
import dev.marzahn.sankaku.ui.theme.color
import kotlinx.coroutines.launch

internal sealed interface TagMenuEntry {
  val label: String

  data class Search(val kind: SearchKind, val search: List<String>) : TagMenuEntry {
    override val label get() = kind.label
  }

  enum class SearchKind(val label: String) {
    Add("Add to search"),
    Exclude("Exclude from search"),
    Remove("Remove from search"),
    Only("Search only this tag"),
  }

  data object About : TagMenuEntry {
    override val label = "About this tag"
  }

  data object CopyName : TagMenuEntry {
    override val label = "Copy name"
  }

  data class Follow(val following: Boolean) : TagMenuEntry {
    override val label = if (following) "Unfollow" else "Follow"
  }

  data object Blacklist : TagMenuEntry {
    override val label = "Blacklist tag…"
  }
}

internal fun tagMenuEntries(
  tag: String,
  search: List<String>,
  canFollow: Boolean,
  following: Boolean,
  canBlacklist: Boolean = false,
  canAbout: Boolean = false,
): List<TagMenuEntry> {
  val state = inSearch(tag, search)
  return buildList {
    if (state != InSearch.Included) add(TagMenuEntry.Search(SearchKind.Add, withTag(search, tag, InSearch.Included)))
    if (state != InSearch.Excluded) add(TagMenuEntry.Search(SearchKind.Exclude, withTag(search, tag, InSearch.Excluded)))
    if (state != InSearch.No) add(TagMenuEntry.Search(SearchKind.Remove, withTag(search, tag, InSearch.No)))
    add(TagMenuEntry.Search(SearchKind.Only, SearchTags.searchForOpenedTag(tag)))
    if (canAbout) add(TagMenuEntry.About)
    add(TagMenuEntry.CopyName)
    if (canFollow) add(TagMenuEntry.Follow(following))
    if (canBlacklist) add(TagMenuEntry.Blacklist)
  }
}

internal fun tagSheetByline(type: TagType?, postCount: Int?, following: Boolean): String = listOfNotNull(
  type?.name,
  postCount?.let { if (it == 1) "1 post" else "${compactCount(it)} posts" },
  "following".takeIf { following },
).joinToString(" · ")

@Composable
internal fun TagMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  tag: TagDto,
  type: TagType?,
  following: Boolean,
  actions: TagActions,
) {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  val toggleFollow = actions.onToggleFollow
  val blacklist = actions.onBlacklist
  var confirming by remember { mutableStateOf(false) }
  val entries = tagMenuEntries(
    tag.searchName, actions.search, canFollow = toggleFollow != null && tag.id != null, following, canBlacklist = blacklist != null,
    canAbout = actions.onAbout != null,
  )
  fun choose(entry: TagMenuEntry, newTab: Boolean) {
    when (entry) {
      is TagMenuEntry.Search -> Route.Grid(entry.search).let { if (newTab) actions.onOpenInNewTab(it) else actions.onOpen(it) }
      TagMenuEntry.About -> actions.onAbout?.invoke(tag)
      TagMenuEntry.CopyName -> scope.launch { clipboard.setClipEntry(plainTextClip(tag.searchName)) }
      is TagMenuEntry.Follow -> toggleFollow?.invoke(tag)
      TagMenuEntry.Blacklist -> confirming = true
    }
  }
  if (confirming && blacklist != null) {
    BlacklistConfirmation(tag, actions.usedBlacklistRules, onDismiss = { confirming = false }, onConfirm = { blacklist(tag) })
  }
  if (actions.touch) {
    if (expanded) TagSheet(tag, type, following, entries, onDismiss, ::choose)
    return
  }
  GroupedMenu(expanded = expanded, onDismissRequest = onDismiss) {
    val account = entries.filter { it is TagMenuEntry.Follow || it == TagMenuEntry.Blacklist }
    val count = if (account.isNotEmpty()) 2 else 1
    MenuGroup(0, count) {
      entries.forEach { entry ->
        when (entry) {
          is TagMenuEntry.Search -> OpenMenuItem(
            entry.label,
            onOpen = { onDismiss(); choose(entry, newTab = false) },
            onOpenInNewTab = { onDismiss(); choose(entry, newTab = true) },
          )
          TagMenuEntry.About, TagMenuEntry.CopyName -> MenuAction(text = { Text(entry.label) }, onClick = { onDismiss(); choose(entry, newTab = false) })
          is TagMenuEntry.Follow, TagMenuEntry.Blacklist -> Unit
        }
      }
    }
    if (account.isNotEmpty()) {
      MenuGroup(1, count) {
        account.forEach { entry ->
          MenuAction(
            text = { Text(entry.label) },
            leadingIcon = { Icon(entry.icon, contentDescription = null) },
            onClick = { onDismiss(); choose(entry, newTab = false) },
          )
        }
      }
    }
  }
}

@Composable
private fun OpenMenuItem(label: String, onOpen: () -> Unit, onOpenInNewTab: () -> Unit) {
  var newTab by remember { mutableStateOf(false) }
  var middle by remember { mutableStateOf(false) }
  val inNewTab by rememberUpdatedState(onOpenInNewTab)
  MenuAction(
    text = { Text(label) },
    onClick = { if (newTab) onOpenInNewTab() else onOpen() },
    modifier = Modifier.pointerInput(Unit) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          when (event.type) {
            PointerEventType.Press -> {
              newTab = event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed
              middle = event.buttons.isTertiaryPressed
            }
            PointerEventType.Release -> if (middle) {
              middle = false
              inNewTab()
            }
          }
        }
      }
    },
  )
}

internal fun forLaterLabel(label: String): String = "$label for later"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSheet(
  tag: TagDto,
  type: TagType?,
  following: Boolean,
  entries: List<TagMenuEntry>,
  onDismiss: () -> Unit,
  onChoose: (TagMenuEntry, newTab: Boolean) -> Unit,
) {
  val sheet = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
  val scope = rememberCoroutineScope()
  val haptics = LocalHapticFeedback.current
  var chosen by remember { mutableStateOf(false) }
  fun choose(entry: TagMenuEntry, newTab: Boolean) {
    if (chosen) return
    chosen = true
    onChoose(entry, newTab)
    scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
  }
  val colors = MaterialTheme.colorScheme
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.surfaceContainerLow) {
    Row(
      Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(Modifier.size(10.dp).background(type?.color ?: colors.outline, CircleShape))
      Column(Modifier.weight(1f)) {
        Text(tag.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val byline = tagSheetByline(type, tag.postCount, following)
        if (byline.isNotEmpty()) Text(byline, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
      }
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.outlineVariant)
    entries.forEach { entry ->
      val search = entry is TagMenuEntry.Search
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = 56.dp)
          .combinedClickable(
            hapticFeedbackEnabled = false,
            role = Role.Button,
            onLongClickLabel = if (search) forLaterLabel(entry.label) else null,
            onLongClick = if (search) {
              {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                choose(entry, newTab = true)
              }
            } else {
              null
            },
            onClick = { choose(entry, newTab = false) },
          )
          .padding(start = 24.dp, end = if (search) 12.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Icon(entry.icon, contentDescription = null, tint = colors.onSurfaceVariant)
        Text(entry.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (search) LaterButton(onClick = { choose(entry, newTab = true) }, description = forLaterLabel(entry.label))
      }
    }
  }
}

private val TagMenuEntry.icon: ImageVector
  get() = when (this) {
    is TagMenuEntry.Search -> when (kind) {
      SearchKind.Add -> addIcon
      SearchKind.Exclude -> TagExclude
      SearchKind.Remove -> closeIcon
      SearchKind.Only -> searchIcon
    }
    TagMenuEntry.About -> infoIcon
    TagMenuEntry.CopyName -> TagCopy
    is TagMenuEntry.Follow -> if (following) TagFollowed else TagFollow
    TagMenuEntry.Blacklist -> visibilityOffIcon
  }
