package dev.marzahn.sankaku

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DefaultWindowExceptionHandlerFactory
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.marzahn.sankaku.data.FirstLaunch
import dev.marzahn.sankaku.di.startSankakuKoin
import dev.marzahn.sankaku.log.CrashReports
import dev.marzahn.sankaku.log.installUncaughtHandler
import dev.marzahn.sankaku.shell.GridRefresh
import dev.marzahn.sankaku.tabs.ClearViewed
import dev.marzahn.sankaku.tabs.NewTab
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.isCommand
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.system.exitProcess

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val mainThread = Thread.currentThread()
  val crashReports = desktopCrashReports().also {
    it.installUncaughtHandler(closesApp = { Thread.currentThread() === mainThread })
  }
  LaunchFocus.remember()
  migrateAppData()
  val instance = SingleInstance.acquire(APP_DATA_DIR) ?: exitProcess(0)
  val focusRequests = Channel<Unit>(Channel.CONFLATED)
  instance.listen { focusRequests.trySend(Unit) }

  val koin = startSankakuKoin(desktopPlatformModule(crashReports)).koin
  val tabs = koin.get<TabsController>()
  val navigator = koin.get<Navigator>()
  val refresh = koin.get<GridRefresh>()
  val clearViewed = koin.get<ClearViewed>()
  val newTab = koin.get<NewTab>()
  val firstLaunch = koin.get<FirstLaunch>()
  val controlPort = ControlPort.fromProperty()?.also { it.requestLog = koin.get() }

  keepAccessibleFocusInBackground()
  application {
    val windowState = rememberWindowState(width = 1280.dp, height = 860.dp)
    CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides recordingWindowCrashes(crashReports)) {
      Window(
        onCloseRequest = ::exitApplication,
        title = "Tessera",
        icon = painterResource("icon.png"),
        state = windowState,
        onPreviewKeyEvent = { firstLaunch.accepted.value && tabShortcuts(it, tabs, newTab, refresh, clearViewed) },
      ) {
        val accepted by firstLaunch.accepted.collectAsState()
        WindowBackHandler(enabled = accepted, onBack = navigator::goBack)
        LaunchedEffect(Unit) {
          delay(300)
          LaunchFocus.yieldFocus()
        }
        LaunchedEffect(Unit) { controlPort?.attach(window) }
        LaunchedEffect(Unit) {
          focusRequests.receiveAsFlow().collect {
            windowState.isMinimized = false
            window.toFront()
          }
        }
        App(onQuit = ::exitApplication)
      }
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun recordingWindowCrashes(reports: CrashReports) = WindowExceptionHandlerFactory { window ->
  val default = DefaultWindowExceptionHandlerFactory.exceptionHandler(window)
  WindowExceptionHandler { error ->
    reports.record(error)
    default.onException(error)
  }
}

private fun tabShortcuts(
  event: KeyEvent,
  tabs: TabsController,
  newTab: NewTab,
  refresh: GridRefresh,
  clearViewed: ClearViewed,
): Boolean {
  if (event.type != KeyEventType.KeyDown) return false
  val command = event.isCommand
  return when {
    event.key == Shortcuts.NextTabCommand.key && event.isCtrlPressed -> true.also { tabs.cycle(forward = !event.isShiftPressed) }
    command && event.isShiftPressed && event.key == Shortcuts.ReopenTabCommand.key -> true.also { tabs.reopenClosed() }
    command && event.isShiftPressed && Shortcuts.ClearViewed.matches(event.key) -> true.also { clearViewed() }
    command && event.key == Shortcuts.NewTab.key -> true.also { newTab.open() }
    command && event.key == Shortcuts.CloseTabCommand.key -> true.also { tabs.close(tabs.active.value) }
    (command && event.key == Shortcuts.Refresh.key) || event.key == Shortcuts.Refresh.alias -> true.also { refresh.request() }
    else -> false
  }
}
