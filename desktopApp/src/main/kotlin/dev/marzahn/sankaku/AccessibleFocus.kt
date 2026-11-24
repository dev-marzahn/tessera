package dev.marzahn.sankaku

import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.lang.ref.WeakReference

internal fun keepAccessibleFocusInBackground() {
  if (System.getProperty("os.name").startsWith("Mac")) {
    KeyboardFocusManager.setCurrentKeyboardFocusManager(BackgroundAccessibleFocusManager())
  }
}

private class BackgroundAccessibleFocusManager : DefaultKeyboardFocusManager() {
  private var lastWindow = WeakReference<Window>(null)
  private val stack = StackWalker.getInstance()

  init {
    addPropertyChangeListener("focusedWindow") { event ->
      (event.newValue as? Window)?.let { lastWindow = WeakReference(it) }
    }
  }

  override fun getFocusOwner(): Component? =
    super.getFocusOwner() ?: lastWindow.get()?.takeIf { it.isShowing && calledByMacAccessibility() }?.mostRecentFocusOwner

  private fun calledByMacAccessibility(): Boolean =
    stack.walk { frames -> frames.anyMatch { it.className.startsWith("sun.lwawt.macosx.CAccessibility") } }
}
