package dev.marzahn.sankaku

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.marzahn.sankaku.shell.LocalChromePadding
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.shell.LocalTabBarSlide
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.navigation3.scene.Scene
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.createChildTransition
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import dev.marzahn.sankaku.shell.LocalQueuePageVisibility
import dev.marzahn.sankaku.shell.SidebarKey
import dev.marzahn.sankaku.shell.ShortcutSheet
import dev.marzahn.sankaku.shell.ShortcutSheetController
import dev.marzahn.sankaku.shell.shellSidebarKey
import dev.marzahn.sankaku.shell.LocalShellTransitions
import dev.marzahn.sankaku.shell.LocalUnderQueuePage
import dev.marzahn.sankaku.shell.LocalQueuePageOpening
import androidx.compose.runtime.withFrameNanos
import dev.marzahn.sankaku.shell.aboveSharedTransitions
import dev.marzahn.sankaku.shell.LocalPostHandoff
import dev.marzahn.sankaku.shell.PostHandoff
import dev.marzahn.sankaku.shell.PostHandoffHost
import dev.marzahn.sankaku.shell.MATCH_FRAMES
import dev.marzahn.sankaku.shell.animationsOff
import dev.marzahn.sankaku.ui.components.spatialFadeSpec
import dev.marzahn.sankaku.ui.components.effectsSpec
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.QueueSettings
import dev.marzahn.sankaku.data.FirstLaunch
import dev.marzahn.sankaku.features.firstlaunch.FirstLaunchScreen
import dev.marzahn.sankaku.features.firstlaunch.QueueIntro
import dev.marzahn.sankaku.features.firstlaunch.QueueIntroScreen
import dev.marzahn.sankaku.features.crash.CrashNotice
import dev.marzahn.sankaku.log.CrashReports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import dev.marzahn.sankaku.features.auth.SignInDialog
import dev.marzahn.sankaku.shell.SidebarNavigator
import dev.marzahn.sankaku.features.settings.SettingsController
import dev.marzahn.sankaku.features.settings.SettingsPage
import dev.marzahn.sankaku.features.settings.SettingsSheet
import dev.marzahn.sankaku.features.books.BookReader
import dev.marzahn.sankaku.features.books.BookReaderPage
import androidx.compose.runtime.SideEffect
import dev.marzahn.sankaku.features.tag.TagPageHost
import dev.marzahn.sankaku.features.tag.TagPages
import dev.marzahn.sankaku.features.tag.rememberTagPageShown
import dev.marzahn.sankaku.features.tag.underTagPage
import dev.marzahn.sankaku.net.ConnectivityState
import dev.marzahn.sankaku.shell.BackOverrides
import dev.marzahn.sankaku.shell.RetryOnWindowFocus
import dev.marzahn.sankaku.shell.LocalOpenSearch
import dev.marzahn.sankaku.shell.SearchOverlay
import dev.marzahn.sankaku.shell.Sidebar
import dev.marzahn.sankaku.shell.TopBar
import dev.marzahn.sankaku.shell.rememberMenuBadge
import dev.marzahn.sankaku.tabs.LocalTabEntry
import dev.marzahn.sankaku.tabs.rememberTabStates
import dev.marzahn.sankaku.tabs.QueueBar
import dev.marzahn.sankaku.tabs.LocalOpenTabList
import dev.marzahn.sankaku.ui.Tip
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.ui.wayBackText
import dev.marzahn.sankaku.tabs.QueuePage
import dev.marzahn.sankaku.tabs.SortDeck
import dev.marzahn.sankaku.tabs.BlockFinds
import dev.marzahn.sankaku.tabs.BlockFindsCard
import dev.marzahn.sankaku.tabs.SortDeckPage
import dev.marzahn.sankaku.tabs.QueueFeeds
import dev.marzahn.sankaku.tabs.QueueStart
import dev.marzahn.sankaku.tabs.backsLikeALonePost
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.marzahn.sankaku.tabs.QueueSwipeHost
import dev.marzahn.sankaku.tabs.UpNextState
import dev.marzahn.sankaku.tabs.shownSearch
import dev.marzahn.sankaku.tabs.TabEntry
import dev.marzahn.sankaku.tabs.TabState
import dev.marzahn.sankaku.tabs.TabStrip
import dev.marzahn.sankaku.tabs.TabSwitcher
import dev.marzahn.sankaku.tabs.BlockChip
import dev.marzahn.sankaku.tabs.BlockOffer
import dev.marzahn.sankaku.tabs.LeavingBlockMessages
import dev.marzahn.sankaku.tabs.LeavingBlockOffers
import dev.marzahn.sankaku.tabs.NewTab
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.UserGestures
import dev.marzahn.sankaku.ui.observeGestures
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import dev.marzahn.sankaku.tabs.ClosedTabUndo
import dev.marzahn.sankaku.tabs.EndUndoAtNextAction
import dev.marzahn.sankaku.tabs.UndoHiddenWhile
import dev.marzahn.sankaku.ui.components.AppSnackbarHost
import dev.marzahn.sankaku.ui.components.AppSnackbarVisuals
import dev.marzahn.sankaku.ui.components.KeyFocus
import dev.marzahn.sankaku.ui.components.LocalKeyFocus
import dev.marzahn.sankaku.ui.components.ConnectivityBanner
import dev.marzahn.sankaku.ui.components.connectivityBannerState
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.TipLayer
import dev.marzahn.sankaku.ui.components.tipGestures
import dev.marzahn.sankaku.ui.components.isCommand
import dev.marzahn.sankaku.ui.components.isPlain
import dev.marzahn.sankaku.ui.components.tabStep
import dev.marzahn.sankaku.ui.components.rememberFullScreenSearchState
import dev.marzahn.sankaku.ui.components.SampleScreen
import dev.marzahn.sankaku.ui.components.rememberHideOnScroll
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

@Composable
fun App(onQuit: () -> Unit) {
  val flags = koinInject<DebugFlags>()
  val loader = koinInject<ImageLoader>()
  setSingletonImageLoaderFactory { loader }
  PlatformHideInRecents(koinInject<PrivacySettings>().hideInRecents.collectAsStateWithLifecycle().value)
  val firstLaunch = koinInject<FirstLaunch>()
  val accepted by firstLaunch.accepted.collectAsStateWithLifecycle()
  val crash = koinInject<CrashReports>().pending.collectAsStateWithLifecycle().value
  val queueIntro = koinInject<QueueIntro>()
  val introShown by queueIntro.shown.collectAsStateWithLifecycle()
  SankakuTheme {
    when {
      !accepted -> FirstLaunchScreen(
        onContinue = {
          firstLaunch.accept()
          if (platformIsTouch) queueIntro.show()
        },
        onQuit = onQuit,
      )
      crash != null -> CrashNotice(crash)
      flags.sample -> SampleScreen()
      else -> {
        CrashOnPurpose(flags.crash)
        val tips = koinInject<Tips>()
        Box(Modifier.fillMaxSize().tipGestures(tips)) {
          Box(if (introShown) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) { Shell() }
          if (introShown) {
            PlatformBackHandler(enabled = true, onBack = queueIntro::close)
            QueueIntroScreen(onStart = queueIntro::close)
          }
          TipLayer(tips)
        }
      }
    }
  }
}

@Composable
private fun CrashOnPurpose(where: DebugCrash?) {
  if (where == null) return
  var due by remember { mutableStateOf(false) }
  LaunchedEffect(where) {
    delay(3_000)
    when (where) {
      DebugCrash.Ui -> due = true
      DebugCrash.Thread -> CoroutineScope(Dispatchers.Default).launch { throw DebugCrashException() }
    }
  }
  if (due) throw DebugCrashException()
}

private class DebugCrashException : IllegalStateException("Debug crash at https://sankakuapi.com/posts/123?tags=secret")

@Composable
private fun Shell(
  tabs: TabsController = koinInject(),
  navigator: Navigator = koinInject(),
  connectivity: ConnectivityState = koinInject(),
  overrides: BackOverrides = koinInject(),
  undo: ClosedTabUndo = koinInject(),
  start: QueueStart = koinInject(),
  upNext: UpNextState = koinInject(),
  settings: SettingsController = koinInject(),
  reader: BookReader = koinInject(),
  queueSettings: QueueSettings = koinInject(),
  newTab: NewTab = koinInject(),
  sortDeck: SortDeck = koinInject(),
  blockFinds: BlockFinds = koinInject(),
) {
  val sortShown = platformIsTouch && sortDeck.running && sortDeck.shown
  val findsShown = platformIsTouch && blockFinds.offer != null
  if (platformIsTouch) remember(start) { start.launched() }
  QueueFeeds(tabs)
  val tabList by tabs.tabs.collectAsStateWithLifecycle()
  val activeId by tabs.active.collectAsStateWithLifecycle()
  val active = tabList.firstOrNull { it.id == activeId } ?: return
  val offline by connectivity.offline.collectAsStateWithLifecycle()
  val rejected by connectivity.rejected.collectAsStateWithLifecycle()
  val unreachable by connectivity.unreachable.collectAsStateWithLifecycle()
  if (!platformIsTouch) RetryOnWindowFocus(connectivity)
  var sidebarOpen by rememberSaveable { mutableStateOf(false) }
  var sidebarByKey by remember { mutableStateOf(false) }
  var sidebarKeyHeld by remember { mutableStateOf(false) }
  var searchOpen by rememberSaveable { mutableStateOf(false) }
  var switcherOpen by remember { mutableStateOf(false) }
  var switcherKeyHeld by remember { mutableStateOf(false) }
  val switcherList by queueSettings.switcherList.collectAsStateWithLifecycle()
  val shortcutSheet = koinInject<ShortcutSheetController>()
  val openQueueList = {
    queueSettings.setSwitcherList(true)
    switcherKeyHeld = false
    switcherOpen = true
  }
  var closeHeld by remember { mutableStateOf(false) }
  var searchBounds by remember { mutableStateOf(Rect.Zero) }
  val fullScreenSearch = rememberFullScreenSearchState()
  val snackbar = remember { SnackbarHostState() }
  val gestures = koinInject<UserGestures>()
  val blockOffer = koinInject<BlockOffer>()
  val messages = koinInject<Messages>()
  if (!platformIsTouch) LeavingBlockMessages(blockOffer)
  val settingsOpen by settings.isOpen.collectAsStateWithLifecycle()
  val settingsPageOpen = platformIsTouch && settingsOpen
  val sidebarNavigator = remember(navigator, settings, start) { SidebarNavigator(navigator, settings, start::closePage) }
  val menuBadge = rememberMenuBadge()
  val toggleSidebar = {
    sidebarByKey = false
    sidebarOpen = !sidebarOpen
  }
  val readerOpen = reader.shown.collectAsStateWithLifecycle().value != null
  val tagPages = koinInject<TagPages>()
  val tagPageShown = rememberTagPageShown(tagPages)
  val coveredTips = koinInject<Tips>()
  val introShown by koinInject<QueueIntro>().shown.collectAsStateWithLifecycle()
  SideEffect { coveredTips.covered = tagPageShown || introShown || readerOpen }
  var searchTabId by remember { mutableStateOf(active.id) }
  LaunchedEffect(active.id, newTab.searchFor) {
    if (active.id != searchTabId) {
      searchTabId = active.id
      searchOpen = false
    }
    if (newTab.take(active.id)) searchOpen = true
  }
  val upNextCard = upNext.card
  LaunchedEffect(active.id, active.top) { upNext.itemShown(active.id, active.top) }
  val shownRoute = if (platformIsTouch) shownSearch(upNextCard, active.top) else active.top
  val rootFocus = remember { FocusRequester() }
  val keyFocus = remember { KeyFocus() }
  MessagesEffect(snackbar, gestures) {
    if (!platformIsTouch) {
      rootFocus.requestFocus()
      keyFocus.returnToContent()
    }
  }
  var searchWasOpen by remember { mutableStateOf(false) }
  LaunchedEffect(searchOpen) {
    if (!searchOpen && searchWasOpen) {
      rootFocus.requestFocus()
      keyFocus.returnToContent()
    }
    searchWasOpen = searchOpen
  }
  var sidebarWasOpen by remember { mutableStateOf(false) }
  LaunchedEffect(sidebarOpen) {
    if (!sidebarOpen && sidebarWasOpen) {
      rootFocus.requestFocus()
      keyFocus.returnToContent()
    }
    sidebarWasOpen = sidebarOpen
  }
  var switcherWasOpen by remember { mutableStateOf(false) }
  LaunchedEffect(switcherOpen) {
    if (!switcherOpen && switcherWasOpen) {
      rootFocus.requestFocus()
      keyFocus.returnToContent()
    }
    switcherWasOpen = switcherOpen
  }
  val sidebarKeys = { event: KeyEvent, searchOpen: Boolean ->
    if (event.key != Shortcuts.Sidebar.key || event.type == KeyEventType.KeyUp) sidebarKeyHeld = false
    val key = shellSidebarKey(event.key, event.isPlain, sidebarKeyHeld, searchOpen, platformIsTouch)
    if (event.type == KeyEventType.KeyDown && key == SidebarKey.Open) {
      sidebarKeyHeld = true
      sidebarByKey = true
      sidebarOpen = true
    }
    event.type == KeyEventType.KeyDown && key != SidebarKey.None
  }
  var settingsWasOpen by remember { mutableStateOf(false) }
  LaunchedEffect(settingsOpen) {
    if (!platformIsTouch && !settingsOpen && settingsWasOpen) {
      rootFocus.requestFocus()
      keyFocus.returnToContent()
    }
    settingsWasOpen = settingsOpen
  }

  val onGrid = active.top is Route.Grid
  val hideTabBar = rememberHideOnScroll()
  LaunchedEffect(active.id, active.top, tabList.size) { hideTabBar.show() }
  val undoPending = undo.pending.collectAsStateWithLifecycle().value != null
  LaunchedEffect(undoPending) { if (undoPending) hideTabBar.show() }
  val covered = platformIsTouch && upNextCard == null && active.chromeCovered
  LaunchedEffect(covered) { if (covered) hideTabBar.showAtOnce() }
  val tabBarShown = platformIsTouch && !covered
  val queuePageOpen = platformIsTouch && start.pageOpen
  val pageTransitions = rememberQueuePageTransitions(queuePageOpen)
  if (platformIsTouch) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { start.left() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { start.returned() }
    LaunchedEffect(activeId) { start.shown(activeId) }
    LaunchedEffect(activeId, tabList) { sortDeck.itemShown(activeId) }
    LaunchedEffect(activeId) { blockFinds.itemShown(activeId) }
    LaunchedEffect(upNextCard != null) { if (upNextCard != null) start.interact() }
  }
  val tips = koinInject<Tips>()
  val openQueuePage = {
    tips.used(Tip.TabList)
    start.interact()
    start.openPage()
  }
  LaunchedEffect(tabList.size) { tips.counted(tabList.size) }
  LaunchedEffect(activeId) { tips.visiting(activeId) }
  LaunchedEffect(tabs, tips, messages) {
    tabs.wayBacks.collect { name ->
      tips.visiting(tabs.active.value)
      if (tips.tell(Tip.WayBack)) messages.show(wayBackText(name))
    }
  }
  var chromeHeight by remember { mutableStateOf(56.dp) }
  val overGrid by rememberUpdatedState(tabBarShown && onGrid)
  val tabBarSlide: () -> Float = remember(hideTabBar) { { if (overGrid) hideTabBar.hiddenBy else 0f } }
  val density = LocalDensity.current
  val postHandoff = remember { if (platformIsTouch) PostHandoff() else null }
  LaunchedEffect(queuePageOpen) { if (queuePageOpen) postHandoff?.end() }

  if (platformIsTouch) {
    EndUndoAtNextAction()
    UndoHiddenWhile((queuePageOpen && !sortShown) || settingsPageOpen)
    LeavingBlockOffers(blockOffer, quiet = queuePageOpen || sortDeck.running)
  }

  Scaffold(
    snackbarHost = { if (!platformIsTouch) AppSnackbarHost(snackbar, swipeable = false) },
    modifier = (if (settingsPageOpen || readerOpen) Modifier.clearAndSetSemantics {} else Modifier).underTagPage(tagPageShown, tagPages)
      .observeGestures(gestures),
  ) { padding ->
    val content = @Composable { chromePadding: PaddingValues, tabStates: SaveableStateHolder ->
      CompositionLocalProvider(
        LocalKeyFocus provides keyFocus,
        LocalChromePadding provides chromePadding,
        LocalTabBarSlide provides tabBarSlide,
        LocalOpenTabList provides openQueuePage,
        LocalOpenSearch provides { searchOpen = true },
        LocalPostHandoff provides postHandoff,
      ) {
        if (platformIsTouch) {
          QueueSwipeHost(active, undo = undo, onMoved = { tips.used(Tip.QueueSwipe) }, content = {
            PostHandoffHost(active, tabList, postHandoff) { item -> ActiveTab(item, tabs, navigator, tabStates) }
          })
        } else {
          ActiveTab(active, tabs, navigator, tabStates)
        }
      }
    }
    ShellBox(
      shared = platformIsTouch,
      Modifier
        .fillMaxSize()
        .then(if (tabBarShown && onGrid && !undoPending) Modifier.nestedScroll(hideTabBar.connection) else Modifier)
        .platformBackKeys { if (!overrides.back()) navigator.goBack() }
        .onKeyEvent { event ->
          if (event.key != Shortcuts.CloseTab.key || event.type == KeyEventType.KeyUp) closeHeld = false
          if (event.key == Shortcuts.TabSwitcher.key && event.type == KeyEventType.KeyUp) switcherKeyHeld = false
          if (sidebarKeys(event, searchOpen)) return@onKeyEvent true
          if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
          when {
            event.key == Shortcuts.Help.key && event.isShiftPressed && !event.isCommand && !searchOpen && !platformIsTouch ->
              true.also { shortcutSheet.open() }
            event.key == Shortcuts.Search.key && !searchOpen -> true.also { searchOpen = true }
            event.key == Shortcuts.Find.key && event.isCommand && !searchOpen -> true.also { searchOpen = true }
            event.key == Shortcuts.ReopenTab.key && !searchOpen && !event.isCommand -> true.also { tabs.reopenClosed() }
            event.key == Shortcuts.TabSwitcher.key && event.isPlain && !searchOpen && !platformIsTouch ->
              true.also {
                switcherKeyHeld = true
                switcherOpen = true
              }
            event.tabStep != null && !searchOpen -> true.also { tabs.cycle(forward = event.tabStep == true) }
            event.key == Shortcuts.CloseTab.key && !searchOpen && event.isPlain ->
              true.also {
                if (!closeHeld) tabs.close(tabs.active.value)
                closeHeld = true
              }
            event.key == Shortcuts.CloseBlock.key && event.isShiftPressed && !event.isCommand && !event.isAltPressed &&
              !searchOpen && !platformIsTouch && blockOffer.pending != null ->
              true.also {
                if (!closeHeld) blockOffer.take(tabs, messages)
                closeHeld = true
              }
            event.key == Shortcuts.Back.key && searchOpen -> true.also { searchOpen = false }
            else -> false
          }
        }
        .focusRequester(rootFocus)
        .focusable(),
    ) {
      val barOnTop = platformIsTouch && !covered && !queuePageOpen && !searchOpen && !sortShown
      Column(Modifier.fillMaxSize().padding(padding).zIndex(if (barOnTop) 1f else 0f).aboveSharedTransitions(enabled = barOnTop)) {
        if (platformHasTabs && !platformIsTouch) {
          TabStrip(seen = koinInject(), onNewTab = newTab::open, onShowAll = openQueueList, onShortcuts = shortcutSheet::open)
        }
        Box(Modifier.fillMaxSize()) {
          Column {
            Column(
              Modifier
                .onSizeChanged { chromeHeight = with(density) { it.height.toDp() } }
                .onGloballyPositioned { tips.topBarBottom = it.boundsInRoot().bottom }
                .then(if (covered || queuePageOpen || sortShown) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
              TopBar(
                current = shownRoute,
                canGoBack = active.backStack.size > 1 || (active.backsLikeALonePost && tabList.indexOf(active) > 0),
                menuOpen = sidebarOpen,
                menuBadge = menuBadge,
                onMenu = toggleSidebar,
                onOpenSearch = { searchOpen = true },
                onSearchPlaced = { searchBounds = it },
                fullScreenSearch = fullScreenSearch,
                beforeSearch = {
                  if (!platformIsTouch) {
                    BlockChip(active.id, onOpen = openQueueList)
                  }
                },
              )
              ConnectivityBanner(connectivityBannerState(offline, unreachable, rejected))
            }
            if (!platformIsTouch) TabContent(shown = true, tabList) { content(PaddingValues(), it) }
          }
          if (!platformIsTouch) {
            SearchOverlay(searchOpen, active.top, searchBounds, fullScreen = fullScreenSearch, onClose = { searchOpen = false })
          }
        }
      }
      if (platformIsTouch) {
        TabContent(shown = true, tabList) { holder ->
          UnderQueuePage(pageTransitions) {
            Box(Modifier.fillMaxSize().then(if (sortShown || findsShown) Modifier.clearAndSetSemantics {} else Modifier)) {
              content(
                PaddingValues(
                  top = padding.calculateTopPadding() + chromeHeight,
                  bottom = padding.calculateBottomPadding(),
                ),
                holder,
              )
            }
          }
        }
        SearchOverlay(searchOpen, shownRoute, searchBounds, fullScreen = fullScreenSearch, onClose = { searchOpen = false })
        if (findsShown && !queuePageOpen) {
          BlockFindsCard(
            blockFinds,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + chromeHeight, bottom = padding.calculateBottomPadding()),
          )
        }
      }
      if (tabBarShown && !queuePageOpen && !sortShown) {
        QueueBar(
          hideOnScroll = hideTabBar.takeIf { onGrid },
          onOpenQueue = openQueuePage,
          bottom = BottomRowLayout.WindowRoom,
          modifier = Modifier.align(Alignment.BottomCenter).aboveSharedTransitions(),
        )
      }
      if (platformIsTouch) {
        QueuePageAndTopLine(snackbar, pageTransitions) { onListTop ->
          QueuePage(
            onDismiss = start::closePage,
            undo = undo,
            onMenu = toggleSidebar,
            menuBadge = menuBadge,
            onListTop = onListTop,
            leaving = !queuePageOpen,
            previews = koinInject(),
            today = koinInject(),
            onSort = { sortDeck.start() },
            chipMemory = start,
            following = koinInject(),
            catchUp = koinInject(),
          )
        }
        SortDeckPage(onGoIn = start::closePage, onToQueue = start::openPage, today = koinInject())
      }
    }
  }
  Sidebar(
    open = sidebarOpen,
    onClose = { sidebarOpen = false },
    current = active.top,
    byKey = sidebarByKey,
    keyHeld = sidebarKeyHeld,
    onClosedByKey = {
      sidebarKeyHeld = true
      sidebarOpen = false
    },
    onCloseKeyUp = { sidebarKeyHeld = false },
    navigator = sidebarNavigator,
  )
  if (!platformIsTouch) {
    TabSwitcher(
      open = switcherOpen,
      onClose = { switcherOpen = false },
      keyHeld = switcherKeyHeld,
      list = switcherList,
      onList = queueSettings::setSwitcherList,
      seen = koinInject(),
      clearViewed = koinInject(),
    )
  }
  if (platformIsTouch) {
    SettingsPage(snackbar)
  } else {
    CompositionLocalProvider(LocalKeyFocus provides keyFocus) {
      SettingsSheet(onSidebarKey = { sidebarKeys(it, false) })
    }
    ShortcutSheet(shortcutSheet)
  }
  Box(Modifier.underTagPage(tagPageShown, tagPages)) {
    CompositionLocalProvider(LocalOpenTabList provides openQueuePage) { BookReaderPage(reader) }
  }
  TagPageHost(
    tagPages,
    snackbar = snackbar,
    onKeysBack = {
      if (reader.shown.value == null) {
        rootFocus.requestFocus()
        keyFocus.returnToContent()
      }
    },
  )
  SignInDialog()
}

@OptIn(ExperimentalTransitionApi::class)
@Composable
internal fun BoxScope.QueuePageAndTopLine(
  snackbar: SnackbarHostState,
  transitions: QueuePageTransitions,
  page: @Composable (onListTop: (Dp) -> Unit) -> Unit,
) {
  val pageOpen = transitions.page.targetState
  var listTop by remember { mutableStateOf(Dp.Unspecified) }
  val fade = spatialFadeSpec<Float>()
  val rowTransition = transitions.under.createChildTransition(label = "queue row picture") { postGone ->
    if (postGone) EnterExitState.Visible else EnterExitState.PostExit
  }
  val rowVisibility = remember(rowTransition) { RowVisibility(rowTransition) }
  transitions.page.AnimatedVisibility(visible = { it }, enter = fadeIn(fade), exit = fadeOut(fade)) {
    CompositionLocalProvider(LocalQueuePageVisibility provides rowVisibility) { page { listTop = it } }
  }
  LaunchedEffect(pageOpen) { if (!pageOpen) listTop = Dp.Unspecified }
  val place = if (pageOpen && listTop.isSpecified) {
    Modifier.padding(top = listTop)
  } else {
    Modifier.windowInsetsPadding(WindowInsets.statusBars)
  }
  AppSnackbarHost(snackbar, swipeable = true, Modifier.align(Alignment.TopCenter).then(place).zIndex(2f).aboveSharedTransitions())
}

private class RowVisibility(override val transition: Transition<EnterExitState>) : AnimatedVisibilityScope

@Composable
internal fun BoxScope.QueuePageAndTopLine(
  snackbar: SnackbarHostState,
  pageOpen: Boolean,
  page: @Composable (onListTop: (Dp) -> Unit) -> Unit,
) = QueuePageAndTopLine(snackbar, rememberQueuePageTransitions(pageOpen), page)

internal class QueuePageTransitions(val page: Transition<Boolean>, val under: Transition<Boolean>, val opening: Boolean)

@Composable
internal fun rememberQueuePageTransitions(pageOpen: Boolean): QueuePageTransitions {
  var stage by remember { mutableStateOf(if (pageOpen) OpeningStage.Open else OpeningStage.Shut) }
  LaunchedEffect(pageOpen) {
    if (!pageOpen) {
      stage = OpeningStage.Shut
    } else if (stage == OpeningStage.Shut && animationsOff()) {
      stage = OpeningStage.Open
    } else if (stage == OpeningStage.Shut) {
      stage = OpeningStage.Attaching
      repeat(MATCH_FRAMES) { withFrameNanos {} }
      stage = OpeningStage.PageIn
      repeat(MATCH_FRAMES) { withFrameNanos {} }
      stage = OpeningStage.Open
    }
  }
  val shownStage = if (pageOpen) stage else OpeningStage.Shut
  return QueuePageTransitions(
    page = updateTransition(shownStage >= OpeningStage.PageIn, label = "queue page"),
    under = updateTransition(shownStage == OpeningStage.Open, label = "under the queue page"),
    opening = shownStage == OpeningStage.Attaching || shownStage == OpeningStage.PageIn,
  )
}

private enum class OpeningStage { Shut, Attaching, PageIn, Open }

@Composable
internal fun UnderQueuePage(transitions: QueuePageTransitions, content: @Composable () -> Unit) {
  transitions.under.AnimatedVisibility(visible = { hidden -> !hidden }, enter = EnterTransition.None, exit = ExitTransition.None) {
    CompositionLocalProvider(LocalUnderQueuePage provides this, LocalQueuePageOpening provides transitions.opening) { content() }
  }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ShellBox(shared: Boolean, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
  if (!shared) return Box(modifier, content = content)
  SharedTransitionScope { overlay ->
    Box(modifier.then(overlay)) {
      CompositionLocalProvider(LocalShellTransitions provides this@SharedTransitionScope) { content() }
    }
  }
}

@Composable
internal fun TabContent(shown: Boolean, open: List<TabState>, content: @Composable (SaveableStateHolder) -> Unit) {
  val tabStates = rememberTabStates(open)
  if (shown) content(tabStates)
}

@OptIn(KoinExperimentalAPI::class)
@Composable
internal fun ActiveTab(
  tab: TabState,
  tabs: TabsController,
  navigator: Navigator,
  holder: SaveableStateHolder,
  entryProvider: (NavKey) -> NavEntry<NavKey> = koinEntryProvider(),
) {
  key(tab.id) {
    holder.SaveableStateProvider(tab.id.value) {
      CompositionLocalProvider(LocalViewModelStoreOwner provides tab) {
        val transitions = inTabTransitions()
        NavDisplay(
          backStack = tab.backStack,
          onBack = navigator::goBack,
          entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberTabViewModelDecorator(tab) { tabs.active.value == tab.id },
          ),
          transitionSpec = transitions.push,
          popTransitionSpec = transitions.pop,
          predictivePopTransitionSpec = transitions.predictivePop,
          entryProvider = entryProvider,
        )
      }
    }
  }
}

internal class InTabTransitions(fade: FiniteAnimationSpec<Float>) {
  val push: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
    { EnterTransition.None togetherWith ExitTransition.None }
  val pop: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = push
  val predictivePop: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform =
    { EnterTransition.None togetherWith fadeOut(fade) }
}

@Composable
internal fun inTabTransitions(): InTabTransitions {
  val fade = effectsSpec<Float>()
  return remember(fade) { InTabTransitions(fade) }
}

@Composable
private fun rememberTabViewModelDecorator(tab: TabState, isActive: () -> Boolean): NavEntryDecorator<NavKey> =
  remember(tab) {
    NavEntryDecorator(
      onPop = { key -> if (isActive()) tab.clearEntry(key) },
      decorate = { entry ->
        val owner = remember(entry.contentKey) { tab.entryOwner(entry.contentKey) }
        val tabEntry = remember(entry.contentKey) { TabEntry(tab, entry.contentKey) }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalTabEntry provides tabEntry) { entry.Content() }
      },
    )
  }

@Composable
private fun MessagesEffect(
  snackbar: SnackbarHostState,
  gestures: UserGestures,
  messages: Messages = koinInject(),
  session: AuthSession = koinInject(),
  signIn: SignInController = koinInject(),
  afterAction: () -> Unit,
) {
  LaunchedEffect(Unit) {
    launch {
      session.state.collect {
        if (it == AuthState.SignedOut(AuthState.SignedOut.Reason.Expired)) {
          messages.show(Message("Session expired", actionLabel = "Sign in", isError = true, onAction = signIn::open))
        }
      }
    }
    messages.messages.collect { message ->
      snackbar.currentSnackbarData?.dismiss()
      launch {
        val visuals = when {
          message.until != null -> AppSnackbarVisuals(message.text, message.actionLabel, SnackbarDuration.Indefinite, message.isError)
          message.short -> AppSnackbarVisuals(message.text, message.actionLabel, SnackbarDuration.Short, message.isError)
          else -> AppSnackbarVisuals(message.text, message.actionLabel, isError = message.isError)
        }
        val ends = message.until?.let { until ->
          launch {
            merge(flow { emit(until()) }, flow { emit(gestures.awaitNext()) }).first()
            snapshotFlow { snackbar.currentSnackbarData }.first { it?.visuals === visuals }?.dismiss()
          }
        }
        val result = snackbar.showSnackbar(visuals)
        ends?.cancel()
        if (result == SnackbarResult.ActionPerformed) {
          message.onAction()
          if (!signIn.isOpen.value) afterAction()
        }
      }
    }
  }
}
