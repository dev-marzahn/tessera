package dev.marzahn.sankaku.features.settings

import androidx.compose.foundation.clickable
import dev.marzahn.sankaku.shell.ShortcutSheetController
import dev.marzahn.sankaku.ui.components.ButtonHint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Slider
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.fonts.icons.arrow_back
import com.google.fonts.icons.close
import com.google.fonts.icons.error
import dev.marzahn.sankaku.PlatformBackHandler
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.data.CacheSizes
import dev.marzahn.sankaku.features.post.platformKeepsVideos
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.auth.TokenStorageStatus
import dev.marzahn.sankaku.data.AccessibilitySettings
import dev.marzahn.sankaku.features.post.PlayerPreferences
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.QueueSettings
import dev.marzahn.sankaku.data.GridSettings
import dev.marzahn.sankaku.features.grid.gridColumns
import dev.marzahn.sankaku.gridScrollbarRoom
import dev.marzahn.sankaku.platformCellGap
import dev.marzahn.sankaku.platformGridEdge
import dev.marzahn.sankaku.data.StartOn
import dev.marzahn.sankaku.features.auth.AccountRow
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.features.firstlaunch.QueueIntro
import dev.marzahn.sankaku.features.firstlaunch.QueueIntroSettingsLabel
import dev.marzahn.sankaku.tabs.SETTINGS_TITLE
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.components.AppSnackbarHost
import dev.marzahn.sankaku.ui.components.AppTopBar
import dev.marzahn.sankaku.ui.components.KEY_SCROLL_SCREEN
import dev.marzahn.sankaku.ui.components.KeyFocusHolder
import dev.marzahn.sankaku.ui.components.KeyHint
import dev.marzahn.sankaku.ui.components.KeyScroll
import dev.marzahn.sankaku.ui.components.KeyScrollLine
import dev.marzahn.sankaku.ui.components.LocalKeyFocus
import dev.marzahn.sankaku.ui.components.ModalSideSheet
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.isCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal const val HideInRecentsHelp =
  "Shows a blank preview in the app switcher. On Android 12 this also blocks screenshots."

internal val ClearEverythingConfirmText = clearEverythingConfirmText(platformIsTouch)

internal fun clearEverythingConfirmText(touch: Boolean): String =
  (if (touch) "Empties the queue and Recently closed" else "Closes all tabs and the recently closed ones") +
    ", and clears the ${if (touch) "image and video caches" else "image cache"} " +
    "and the tags and searches the app remembers. You stay signed in. Posts you saved to Download/Tessera stay."

internal const val AlwaysStartGOnlyHelp = "Turns G only on each time the app starts. You can still turn it off."

internal const val ForYouInQueueHelp = "Signed in, For you stays at the end of the queue, after Following."

internal fun postSizeHelp(columns: Int, touch: Boolean = false): String =
  "Larger posts, fewer columns. ${columnsLabel(columns)} " +
    if (touch) "on this screen." else "in this window."

private fun columnsLabel(columns: Int): String = if (columns == 1) "1 column" else "$columns columns"

internal const val StartVideosMutedHelp = "Mutes videos each time the app starts. Unmuting lasts until you close it."

internal const val ShowQueueButtonsHelp = "Shows ✕, ‹ and › beside the queue count, to close and move without swiping."

internal const val LeftHandedHelp = "Puts ♡ and ⋮ at the left end of a post's vote row, under your left thumb."

internal const val InMemoryTokensWarning = "Can't store tokens; you'll need to sign in each launch"

internal enum class SettingsSection(val title: String) {
  Account("Account"),
  Privacy("Privacy"),
  Queue("Queue"),
  Grid("Grid"),
  Videos("Videos"),
  Keyboard("Keyboard"),
  Accessibility("Accessibility"),
  Storage("Storage"),
  Updates("Updates"),
  Diagnostics("Diagnostics"),
  About("About"),
}

internal fun shownSections(
  touch: Boolean,
  updates: Boolean = UPDATES_FILLED,
  diagnostics: Boolean = DIAGNOSTICS_FILLED,
  about: Boolean = ABOUT_FILLED,
): List<SettingsSection> = SettingsSection.entries.filter {
  when (it) {
    SettingsSection.Accessibility -> touch
    SettingsSection.Keyboard -> !touch
    SettingsSection.Updates -> updates
    SettingsSection.Diagnostics -> diagnostics
    SettingsSection.About -> about
    else -> true
  }
}

internal fun clearSavedTabsLabel(touch: Boolean): String = if (touch) "Clear the queue" else "Clear saved tabs"

internal fun clearSavedTabsConfirmText(touch: Boolean): String =
  (if (touch) "Empties the queue and Recently closed." else "Closes all tabs and the recently closed ones.") +
    " The cache, the posts you've seen, your searches and settings stay."

internal fun clearCacheConfirmText(touch: Boolean): String =
  "Deletes the ${if (touch) "images and videos" else "images"} the app has downloaded; they load again when shown. " +
    "${if (touch) "The queue" else "Your tabs"}, your searches and settings stay."

@Composable
fun SettingsSheet(onSidebarKey: (KeyEvent) -> Boolean, controller: SettingsController = koinInject()) {
  val isOpen by controller.isOpen.collectAsStateWithLifecycle()
  ModalSideSheet(isOpen, onClose = controller::close, title = SETTINGS_TITLE, modifier = Modifier.onKeyEvent(onSidebarKey)) {
    Row(
      Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        SETTINGS_TITLE,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.weight(1f).semantics { heading() },
      )
      ButtonHint("Close settings (${Shortcuts.Back.label})") {
        IconButton(onClick = controller::close) { Icon(close, contentDescription = "Close settings") }
      }
    }
    FlowRow(
      Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      KeyHint("W S", "scroll")
      KeyHint("Tab", "next setting")
      KeyHint(Shortcuts.Back.label, "close")
    }
    SettingsColumn(onClearedEverything = controller::close, Modifier.weight(1f))
  }
}

@Composable
internal fun SettingsColumn(onClearedEverything: () -> Unit, modifier: Modifier = Modifier) {
  val scroll = rememberScrollState()
  val keyScroll = remember(scroll) { KeyScroll(scroll) }
  val scope = rememberCoroutineScope()
  val line = with(LocalDensity.current) { KeyScrollLine.toPx() }
  val firstSetting = remember { FocusRequester() }
  var skipAccount by remember { mutableStateOf(true) }
  var holderFocused by remember { mutableStateOf(false) }
  val returns = LocalKeyFocus.current.returns
  LaunchedEffect(returns) { skipAccount = true }
  Box(
    modifier.fillMaxWidth().onKeyEvent { event ->
      val step = settingsKeyStep(event, scroll.viewportSize, line)
      if (step != null) keyScroll.by(step, scope)
      step != null
    },
  ) {
    KeyFocusHolder(
      Modifier
        .onFocusChanged { state ->
          if (holderFocused && !state.isFocused) skipAccount = false
          holderFocused = state.isFocused
        }
        .focusProperties { if (skipAccount) next = firstSetting },
    )
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(scroll)
        .testTag(SettingsScrollTag)
        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
      SettingsSections(touch = false, onClearedEverything, firstSetting = Modifier.focusRequester(firstSetting))
    }
  }
}

internal const val SettingsScrollTag = "settings-scroll"

internal fun settingsKeyStep(event: KeyEvent, viewport: Int, line: Float): Float? {
  if (event.type != KeyEventType.KeyDown || event.isCommand || event.isAltPressed) return null
  val step = if (event.isShiftPressed) viewport * KEY_SCROLL_SCREEN else line
  return when {
    Shortcuts.Down.matches(event.key) -> step
    Shortcuts.Up.matches(event.key) -> -step
    else -> null
  }
}

@Composable
fun SettingsPage(snackbar: SnackbarHostState, controller: SettingsController = koinInject()) {
  val isOpen by controller.isOpen.collectAsStateWithLifecycle()
  if (!isOpen) return
  PlatformBackHandler(enabled = true, onBack = controller::close)
  Box(Modifier.fillMaxSize()) {
    Scaffold(
      modifier = Modifier.semantics { paneTitle = SETTINGS_TITLE },
      topBar = {
        AppTopBar(
          title = SETTINGS_TITLE,
          navigationIcon = {
            IconButton(onClick = controller::close) { Icon(arrow_back, contentDescription = "Back") }
          },
        )
      },
    ) { padding ->
      Column(
        Modifier
          .fillMaxSize()
          .padding(padding)
          .consumeWindowInsets(padding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
      ) {
        SettingsSections(touch = true, onClearedEverything = controller::close)
      }
    }
    AppSnackbarHost(
      snackbar,
      swipeable = true,
      Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).zIndex(1f),
    )
  }
}

@Composable
internal fun ColumnScope.SettingsSections(
  touch: Boolean,
  onClearedEverything: () -> Unit = {},
  firstSetting: Modifier = Modifier,
) {
  shownSections(touch).forEachIndexed { index, section ->
    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
    SectionHeading(section.title)
    when (section) {
      SettingsSection.Account -> AccountSettings(touch)
      SettingsSection.Privacy -> PrivacySettings(touch, onClearedEverything, firstSetting)
      SettingsSection.Queue -> QueueSettings()
      SettingsSection.Grid -> GridSettings(touch)
      SettingsSection.Videos -> VideoSettings()
      SettingsSection.Keyboard -> KeyboardSettings()
      SettingsSection.Accessibility -> AccessibilitySettings()
      SettingsSection.Storage -> StorageSettings(touch)
      SettingsSection.Updates -> UpdatesSettings()
      SettingsSection.Diagnostics -> DiagnosticsSettings()
      SettingsSection.About -> AboutSettings()
    }
  }
}

@Composable
private fun SectionHeading(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
  )
}

@Composable
private fun AccountSettings(
  touch: Boolean,
  session: AuthSession = koinInject(),
  signIn: SignInController = koinInject(),
  storage: TokenStorageStatus = koinInject(),
  settings: SettingsController = koinInject(),
) {
  val state by session.state.collectAsStateWithLifecycle()
  val user = (state as? AuthState.SignedIn)?.user
  val scope = rememberCoroutineScope()
  Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
    if (user != null) {
      AccountRow(user.name, user.avatarUrl, open = true, onSignOut = { scope.launch { session.signOut() } })
    } else {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Not signed in", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = { signIn.open() }) { Text("Sign in") }
      }
    }
  }
  if (user != null) {
    var showingFollowed by rememberSaveable { mutableStateOf(false) }
    ActionRow(FollowedTagsTitle, help = FollowedTagsHelp, onClick = { showingFollowed = true })
    if (showingFollowed) {
      FollowedTagsPage(
        onDismiss = { showingFollowed = false },
        onOpenTag = {
          showingFollowed = false
          settings.close()
        },
      )
    }
  }
  if (!touch && storage == TokenStorageStatus.InMemory) {
    Row(
      Modifier.fillMaxWidth().padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
      Text(InMemoryTokensWarning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
  }
}

@Composable
private fun PrivacySettings(
  touch: Boolean,
  onClearedEverything: () -> Unit,
  firstRow: Modifier,
  privacy: PrivacySettings = koinInject(),
) {
  val hideInRecents by privacy.hideInRecents.collectAsStateWithLifecycle()
  val startOn by privacy.startOn.collectAsStateWithLifecycle()
  val alwaysStartGOnly by privacy.alwaysStartGOnly.collectAsStateWithLifecycle()
  var confirming by remember { mutableStateOf(false) }
  val scope = koinInject<CoroutineScope>()
  if (touch) {
    SwitchRow("Hide in Recents", HideInRecentsHelp, hideInRecents, privacy::setHideInRecents, firstRow)
    StartOnChoice(startOn, privacy::setStartOn)
  }
  SwitchRow(
    "Always start in G only",
    AlwaysStartGOnlyHelp,
    alwaysStartGOnly,
    privacy::setAlwaysStartGOnly,
    if (touch) Modifier else firstRow,
  )
  ActionRow("Clear everything…", help = null, onClick = { confirming = true }, destructive = true)
  if (confirming) {
    ClearEverythingConfirm(scope = scope, onDismiss = { confirming = false }, onConfirm = onClearedEverything)
  }
}

@Composable
private fun KeyboardSettings(shortcuts: ShortcutSheetController = koinInject()) {
  ActionRow("Keyboard shortcuts", help = KeyboardShortcutsHelp, onClick = shortcuts::open)
}

internal val KeyboardShortcutsHelp = "Every key and where it works. ${Shortcuts.Help.label} opens it from anywhere."

@Composable
private fun QueueSettings(queue: QueueSettings = koinInject(), intro: QueueIntro = koinInject()) {
  val forYouInQueue by queue.forYouInQueue.collectAsStateWithLifecycle()
  SwitchRow("Show For you in the queue", ForYouInQueueHelp, forYouInQueue, queue::setForYouInQueue)
  if (platformIsTouch) ActionRow(QueueIntroSettingsLabel, help = null, onClick = intro::show)
}

@Composable
private fun GridSettings(touch: Boolean, grid: GridSettings = koinInject()) {
  val saved by grid.cellWidth.collectAsStateWithLifecycle()
  val sizes = grid.sizes
  val cellWidth = saved ?: sizes.default
  val windowWidth = LocalWindowInfo.current.containerSize.width
  val columns = with(LocalDensity.current) {
    val gridWidth = windowWidth - (platformGridEdge * 2 + gridScrollbarRoom).roundToPx()
    gridColumns(gridWidth, cellWidth.dp.roundToPx(), platformCellGap.roundToPx())
  }
  val help = postSizeHelp(columns, touch)
  Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
    Text("Post size", style = MaterialTheme.typography.bodyLarge)
    Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(
      value = cellWidth.toFloat(),
      onValueChange = { grid.setCellWidth(it.roundToInt()) },
      valueRange = sizes.min.toFloat()..sizes.max.toFloat(),
      steps = (sizes.max - sizes.min) / sizes.step - 1,
      modifier = Modifier.fillMaxWidth().testTag(PostSizeSliderTag).semantics {
        contentDescription = "Post size"
        stateDescription = columnsLabel(columns)
      },
    )
  }
}

internal const val PostSizeSliderTag = "post-size-slider"

@Composable
private fun VideoSettings(player: PlayerPreferences = koinInject()) {
  SwitchRow("Start videos muted", StartVideosMutedHelp, player.startMuted, player::changeStartMuted)
}

@Composable
private fun AccessibilitySettings(accessibility: AccessibilitySettings = koinInject()) {
  val showQueueButtons by accessibility.showQueueButtons.collectAsStateWithLifecycle()
  val leftHanded by accessibility.leftHanded.collectAsStateWithLifecycle()
  SwitchRow("Show queue buttons", ShowQueueButtonsHelp, showQueueButtons, accessibility::setShowQueueButtons)
  SwitchRow("Left-handed", LeftHandedHelp, leftHanded, accessibility::setLeftHanded)
}

@Composable
private fun StorageSettings(
  touch: Boolean,
  cache: ClearCache = koinInject(),
  limits: CacheLimits = koinInject(),
  savedTabs: ClearSavedTabs = koinInject(),
  messages: Messages = koinInject(),
) {
  var cleared by remember { mutableIntStateOf(0) }
  var inUse by remember { mutableStateOf<Long?>(null) }
  var imagesInUse by remember { mutableStateOf<Long?>(null) }
  var videosInUse by remember { mutableStateOf<Long?>(null) }
  val imageBytes by limits.imageBytes.collectAsStateWithLifecycle()
  val videoBytes by limits.videoBytes.collectAsStateWithLifecycle()
  LaunchedEffect(cleared) {
    inUse = runCatching { cache.bytesInUse() }.getOrNull()
    imagesInUse = runCatching { limits.imagesInUse() }.getOrNull()
    videosInUse = runCatching { limits.videosInUse() }.getOrNull()
  }
  var confirming by remember { mutableStateOf<StorageWipe?>(null) }
  var choosing by remember { mutableStateOf<CacheKind?>(null) }
  val scope = koinInject<CoroutineScope>()
  ActionRow(
    "Image cache",
    help = cacheSizeHelp(imageBytes, imagesInUse),
    onClick = { choosing = CacheKind.Images },
    modifier = Modifier.testTag(ImageCacheRowTag),
  )
  if (platformKeepsVideos) {
    ActionRow(
      "Video cache",
      help = cacheSizeHelp(videoBytes, videosInUse),
      onClick = { choosing = CacheKind.Videos },
      modifier = Modifier.testTag(VideoCacheRowTag),
    )
  }
  choosing?.let { kind ->
    CacheSizeDialog(
      title = kind.title,
      sizes = kind.sizes,
      selected = if (kind == CacheKind.Images) imageBytes else videoBytes,
      onDismiss = { choosing = null },
      onSelect = { bytes ->
        choosing = null
        scope.launch {
          if (kind == CacheKind.Images) limits.setImageBytes(bytes) else limits.setVideoBytes(bytes)
          cleared++
        }
      },
    )
  }
  ActionRow(
    "Clear cache…",
    help = "Cache in use: ${inUse?.let(::storageSize) ?: "…"}",
    onClick = { confirming = StorageWipe.Cache },
    modifier = Modifier.testTag(CacheRowTag),
  )
  ActionRow("${clearSavedTabsLabel(touch)}…", help = null, onClick = { confirming = StorageWipe.SavedTabs })
  when (confirming) {
    StorageWipe.Cache -> ConfirmDialog(
      title = "Clear cache?",
      text = clearCacheConfirmText(touch),
      confirm = "Clear cache",
      onDismiss = { confirming = null },
      onConfirm = {
        scope.launch {
          cache.run()
          cleared++
          messages.show("Cache cleared")
        }
      },
    )
    StorageWipe.SavedTabs -> ConfirmDialog(
      title = "${clearSavedTabsLabel(touch)}?",
      text = clearSavedTabsConfirmText(touch),
      confirm = clearSavedTabsLabel(touch),
      onDismiss = { confirming = null },
      onConfirm = {
        savedTabs.run()
        messages.show(if (touch) "Queue cleared" else "Tabs cleared")
      },
    )
    null -> Unit
  }
}

internal const val CacheRowTag = "settings-cache"
internal const val ImageCacheRowTag = "settings-image-cache"
internal const val VideoCacheRowTag = "settings-video-cache"

private enum class StorageWipe { Cache, SavedTabs }

private enum class CacheKind(val title: String, val sizes: CacheSizes) {
  Images("Image cache", CacheSizes.Images),
  Videos("Video cache", CacheSizes.Videos),
}

internal fun cacheSizeHelp(maxBytes: Long, inUse: Long?): String =
  "Up to ${storageSize(maxBytes)} · ${inUse?.let { if (it <= 0) "empty" else "${storageSize(it)} in use" } ?: "…"}"

internal fun cacheSizeLabel(bytes: Long, sizes: CacheSizes): String =
  if (bytes == sizes.default) "${storageSize(bytes)} (default)" else storageSize(bytes)

@Composable
private fun CacheSizeDialog(
  title: String,
  sizes: CacheSizes,
  selected: Long,
  onDismiss: () -> Unit,
  onSelect: (Long) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(Modifier.selectableGroup()) {
        Text(
          "A smaller size deletes the least recently used files to fit.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp),
        )
        sizes.presets.forEach { bytes ->
          RadioRow(cacheSizeLabel(bytes, sizes), selected = bytes == selected, onSelect = { onSelect(bytes) })
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
      TextButton(
        onClick = {
          onDismiss()
          onConfirm()
        },
      ) { Text(confirm, color = MaterialTheme.colorScheme.error) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
internal fun ActionRow(
  label: String,
  help: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  destructive: Boolean = false,
) {
  Column(
    modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = 8.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      label,
      style = MaterialTheme.typography.bodyLarge,
      color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
    if (help != null) {
      Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun StartOnChoice(selected: StartOn, onSelect: (StartOn) -> Unit) {
  Column(Modifier.selectableGroup()) {
    Text("Start on", style = MaterialTheme.typography.bodyLarge)
    Text(
      "After a launch, or coming back after more than 5 minutes",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StartOn.entries.forEach { choice ->
      val label = when (choice) {
        StartOn.Queue -> "Queue"
        StartOn.Browse -> "Browse"
      }
      RadioRow(label, selected = choice == selected, onSelect = { onSelect(choice) })
    }
  }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    RadioButton(selected = selected, onClick = null)
    Text(label, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
internal fun ClearEverythingConfirm(
  scope: CoroutineScope,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit = {},
  clear: ClearEverything = koinInject(),
  messages: Messages = koinInject(),
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Clear everything?") },
    text = { Text(ClearEverythingConfirmText) },
    confirmButton = {
      TextButton(
        onClick = {
          onDismiss()
          onConfirm()
          scope.launch(Dispatchers.Main) {
            clear.run()
            messages.show("Everything cleared")
          }
        },
      ) { Text("Clear everything", color = MaterialTheme.colorScheme.error) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
internal fun SwitchRow(
  label: String,
  help: String?,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  Row(
    modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
      Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (icon != null) Icon(icon, contentDescription = null)
      Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (help != null) {
          Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
  }
}
