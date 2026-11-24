package dev.marzahn.sankaku.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import dev.marzahn.sankaku.api.dto.UserSummary
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.fonts.icons.auto_awesome
import com.google.fonts.icons.favorite
import com.google.fonts.icons.group
import com.google.fonts.icons.local_fire_department
import com.google.fonts.icons.menu_book
import com.google.fonts.icons.search
import com.google.fonts.icons.settings
import com.google.fonts.icons.shield
import com.google.fonts.icons.trophy
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.data.GridPresets
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.features.auth.AccountSection
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.features.settings.SettingsController
import dev.marzahn.sankaku.features.settings.SwitchRow
import dev.marzahn.sankaku.ui.components.UPDATE_AVAILABLE_DOT
import dev.marzahn.sankaku.ui.components.UpdateDot
import dev.marzahn.sankaku.update.UpdateCheck
import dev.marzahn.sankaku.tabs.SETTINGS_TITLE
import dev.marzahn.sankaku.tabs.BOOKS_TITLE
import dev.marzahn.sankaku.isBooks
import dev.marzahn.sankaku.processMemoryBytes
import dev.marzahn.sankaku.ui.components.KeyHint
import dev.marzahn.sankaku.ui.components.OncePerPress
import dev.marzahn.sankaku.ui.components.ModalNavigationRail
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.isPlain
import dev.marzahn.sankaku.ui.components.keyRing
import dev.marzahn.sankaku.ui.openOnClick
import org.koin.compose.koinInject

@Composable
fun Sidebar(
  open: Boolean,
  onClose: () -> Unit,
  current: Route? = null,
  byKey: Boolean = false,
  keyHeld: Boolean = false,
  onClosedByKey: () -> Unit = onClose,
  onCloseKeyUp: () -> Unit = {},
  navigator: Navigator = koinInject(),
  session: AuthSession = koinInject(),
  signIn: SignInController = koinInject(),
  privacy: PrivacySettings = koinInject(),
  settingsController: SettingsController = koinInject(),
  updates: UpdateCheck = koinInject(),
  readMemory: () -> Long? = ::processMemoryBytes,
) {
  val user = (session.state.collectAsStateWithLifecycle().value as? AuthState.SignedIn)?.user
  val gOnly by privacy.gOnly.collectAsStateWithLifecycle()
  val update by updates.available.collectAsStateWithLifecycle()
  val settingsOpen by settingsController.isOpen.collectAsStateWithLifecycle()
  val selectedRoute = current.takeUnless { settingsOpen }
  val anyCurrent = settingsOpen || hasEntry(selectedRoute, user?.name)
  ModalNavigationRail(
    open,
    onClose,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    closeKey = Shortcuts.Sidebar.key,
    focusColumn = !byKey,
    closeKeyHeld = keyHeld,
    onClosedByKey = onClosedByKey,
    onCloseKeyUp = onCloseKeyUp,
  ) {
    CompositionLocalProvider(LocalOpenedByKey provides (open && byKey)) {
      BoxWithConstraints(Modifier.weight(1f)) {
        val room = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = room)) {
          SidebarEntries(byKey, user, selectedRoute, anyCurrent, gOnly, navigator, onClose, session, signIn, privacy)
        }
      }
      val openSettings = {
        onClose()
        settingsController.open()
      }
      Entry(
        SETTINGS_TITLE,
        settings,
        onOpen = openSettings,
        onOpenInNewTab = openSettings,
        selected = settingsOpen,
        dot = if (update != null) UPDATE_AVAILABLE_DOT else null,
      )
      HorizontalDivider(Modifier.padding(horizontal = 24.dp))
      AccountSection(open, onClose, Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp).keyRing(CircleShape))
      MemoryUsage(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp), readMemory)
    }
  }
}

@Composable
private fun ColumnScope.SidebarEntries(
  byKey: Boolean,
  user: UserSummary?,
  selectedRoute: Route?,
  anyCurrent: Boolean,
  gOnly: Boolean,
  navigator: Navigator,
  onClose: () -> Unit,
  session: AuthSession,
  signIn: SignInController,
  privacy: PrivacySettings,
) {
  Row(
    Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = if (byKey) 8.dp else 24.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Image(TesseraAppIcon, contentDescription = null, modifier = Modifier.size(32.dp))
    Text("Tessera", style = MaterialTheme.typography.titleLarge)
  }
  if (byKey) SidebarKeyHints(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp))
  Entry(
    "Browse",
    search,
    Route.Grid(emptyList()),
    navigator,
    onClose,
    selected = isCurrent(selectedRoute,emptyList()),
    startHere = !anyCurrent,
  )
  Entry(
    "For you",
    auto_awesome,
    Route.Grid(GridPresets.forYou),
    navigator,
    onClose,
    selected = isCurrent(selectedRoute,GridPresets.forYou),
  )
  if (user != null) {
    Entry(
      "Following",
      group,
      Route.Grid(GridPresets.following),
      navigator,
      onClose,
      selected = isCurrent(selectedRoute,GridPresets.following),
    )
    val favourites = GridPresets.favourites(user.name)
    Entry(
      "Favourites",
      favorite,
      Route.Grid(favourites),
      navigator,
      onClose,
      selected = isCurrent(selectedRoute,favourites),
    )
  } else {
    SignInEntry(
      "Following",
      group,
      "to see Following",
      signIn,
      navigator,
      onClose,
      selected = isCurrent(selectedRoute,GridPresets.following),
    ) {
      Route.Grid(GridPresets.following)
    }
    SignInEntry("Favourites", favorite, "to see your favourites", signIn, navigator, onClose) {
      (session.state.value as? AuthState.SignedIn)?.let { Route.Grid(GridPresets.favourites(it.user.name)) }
    }
  }
  Entry(
    "Popular",
    local_fire_department,
    Route.Grid(GridPresets.popular),
    navigator,
    onClose,
    selected = isCurrent(selectedRoute,GridPresets.popular),
  )
  Entry(
    "Top",
    trophy,
    Route.Grid(GridPresets.top),
    navigator,
    onClose,
    selected = isCurrent(selectedRoute,GridPresets.top),
  )
  Entry(BOOKS_TITLE, menu_book, Route.Books(), navigator, onClose, selected = selectedRoute?.isBooks == true)
  Spacer(Modifier.weight(1f))
  if (user != null) {
    SwitchRow(
      label = "G only",
      help = "Show only G-rated posts",
      checked = gOnly,
      onCheckedChange = privacy::setGOnly,
      modifier = Modifier.padding(horizontal = 12.dp).keyRing(CircleShape).padding(horizontal = 16.dp),
      enabled = !privacy.gOnlyForced,
      icon = shield,
    )
  }
}

@Composable
private fun SidebarKeyHints(modifier: Modifier = Modifier) {
  FlowRow(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    KeyHint("W S", "move")
    KeyHint(Shortcuts.OpenHere.label, "open")
    KeyHint(Shortcuts.OpenInTab.label, "new tab")
    KeyHint(Shortcuts.Back.label, "close")
  }
}

@Composable
private fun SignInEntry(
  label: String,
  icon: ImageVector,
  reason: String,
  signIn: SignInController,
  navigator: Navigator,
  onClose: () -> Unit,
  selected: Boolean = false,
  route: () -> Route?,
) {
  fun open(inNewTab: Boolean) {
    onClose()
    signInThenOpen(signIn, navigator, reason, inNewTab, route)
  }
  Entry(
    label,
    icon,
    onOpen = { open(inNewTab = false) },
    onOpenInNewTab = { open(inNewTab = true) },
    selected = selected,
  )
}

internal fun isCurrent(current: Route?, tags: List<String>): Boolean =
  current is Route.Grid && current.tags.toSet() == tags.toSet()

internal fun hasEntry(current: Route?, userName: String?): Boolean {
  val presets = listOfNotNull(
    emptyList(),
    GridPresets.forYou,
    GridPresets.following,
    userName?.let { GridPresets.favourites(it) },
    GridPresets.popular,
    GridPresets.top,
  )
  return current?.isBooks == true || presets.any { isCurrent(current, it) }
}

internal enum class SidebarKey {
  None,

  Repeat,

  Open,
}

internal fun shellSidebarKey(key: Key, plain: Boolean, held: Boolean, searchOpen: Boolean, touch: Boolean): SidebarKey =
  when {
    key != Shortcuts.Sidebar.key -> SidebarKey.None
    held -> SidebarKey.Repeat
    plain && !searchOpen && !touch -> SidebarKey.Open
    else -> SidebarKey.None
  }

internal fun signInThenOpen(
  signIn: SignInController,
  navigator: Navigator,
  reason: String,
  inNewTab: Boolean,
  route: () -> Route?,
) {
  signIn.open(reason) {
    val target = route() ?: return@open
    if (inNewTab) navigator.openInNewTab(target) else navigator.goTo(target)
  }
}

@Composable
private fun Entry(
  label: String,
  icon: ImageVector,
  route: Route,
  navigator: Navigator,
  onClose: () -> Unit,
  selected: Boolean = false,
  startHere: Boolean = false,
) {
  Entry(
    label,
    icon,
    onOpen = {
      onClose()
      navigator.goTo(route)
    },
    onOpenInNewTab = { navigator.openInNewTab(route) },
    selected = selected,
    startHere = startHere,
  )
}

internal val LocalOpenedByKey = compositionLocalOf { false }

@Composable
internal fun Entry(
  label: String,
  icon: ImageVector,
  onOpen: () -> Unit,
  onOpenInNewTab: () -> Unit,
  selected: Boolean = false,
  dot: String? = null,
  startHere: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  val focus = remember { FocusRequester() }
  val takesFocus = LocalOpenedByKey.current && (selected || startHere)
  val newTabPresses = remember { OncePerPress() }
  LaunchedEffect(takesFocus) { if (takesFocus) focus.requestFocus() }
  Row(
    Modifier
      .padding(horizontal = 12.dp)
      .fillMaxWidth()
      .height(56.dp)
      .keyRing(CircleShape)
      .clip(CircleShape)
      .background(if (selected) colors.secondaryContainer else Color.Transparent)
      .onFocusChanged { if (!it.hasFocus) newTabPresses.letGo() }
      .onKeyEvent { event ->
        if (event.key != Shortcuts.OpenInTab.key || !event.isPlain) return@onKeyEvent false
        if (newTabPresses.isNewPress(event)) onOpenInNewTab()
        event.type == KeyEventType.KeyDown
      }
      .focusRequester(focus)
      .openOnClick(onOpen, onOpenInNewTab)
      .semantics {
        role = Role.Tab
        this.selected = selected
        if (dot != null) stateDescription = dot
      }
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val content = if (selected) colors.onSecondaryContainer else LocalContentColor.current
    Icon(icon, contentDescription = null, tint = content)
    Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    if (dot != null) UpdateDot()
  }
}
