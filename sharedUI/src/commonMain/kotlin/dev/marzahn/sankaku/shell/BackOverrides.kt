package dev.marzahn.sankaku.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.marzahn.sankaku.PlatformBackHandler
import dev.marzahn.sankaku.ui.components.Shortcut
import org.koin.compose.koinInject

class BackOverrides {
  internal class Entry(val close: () -> Unit) {
    var alsoKey by mutableStateOf<Shortcut?>(null)
  }

  private val stack = mutableStateListOf<Entry>()

  val active: Boolean get() = stack.isNotEmpty()

  val alsoKey: Shortcut? get() = stack.lastOrNull()?.alsoKey

  fun back(): Boolean {
    val entry = stack.lastOrNull() ?: return false
    entry.close()
    return true
  }

  internal fun add(entry: Entry) {
    stack.add(entry)
  }

  internal fun remove(entry: Entry) {
    stack.remove(entry)
  }
}

@Composable
fun BackOverride(enabled: Boolean, onBack: () -> Unit, alsoKey: Shortcut? = null, overrides: BackOverrides = koinInject()) {
  val latest by rememberUpdatedState(onBack)
  val entry = remember { BackOverrides.Entry { latest() } }
  SideEffect { entry.alsoKey = alsoKey }
  DisposableEffect(enabled, overrides) {
    if (enabled) overrides.add(entry)
    onDispose { overrides.remove(entry) }
  }
  PlatformBackHandler(enabled = enabled) { latest() }
}
