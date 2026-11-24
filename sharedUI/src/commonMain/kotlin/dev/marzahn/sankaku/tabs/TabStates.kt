package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

internal class TabStates(private val holder: SaveableStateHolder, private val given: MutableSet<String>) : SaveableStateHolder {
  private var open: Set<String> = emptySet()

  @Composable
  override fun SaveableStateProvider(key: Any, content: @Composable () -> Unit) {
    holder.SaveableStateProvider(key, content)
    SideEffect { if (key in open) given += key as String }
  }

  override fun removeState(key: Any) {
    given.remove(key)
    holder.removeState(key)
  }

  fun keepOnly(open: Set<String>) {
    this.open = open
    given.filter { it !in open }.forEach(::removeState)
  }
}

@Composable
internal fun rememberTabStates(open: List<TabState>): SaveableStateHolder {
  val holder = rememberSaveableStateHolder()
  val given = rememberSaveable(saver = listSaver<MutableSet<String>, String>({ it.toList() }, { it.toMutableSet() })) {
    mutableSetOf()
  }
  val states = remember(holder, given) { TabStates(holder, given) }
  val ids = open.mapTo(mutableSetOf()) { it.id.value }
  SideEffect { states.keepOnly(ids) }
  return states
}
