package dev.marzahn.sankaku.shell

import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.features.settings.SettingsController

internal class SidebarNavigator(
  private val navigator: Navigator,
  private val settings: SettingsController,
  private val closeQueuePage: () -> Unit,
) : Navigator by navigator {
  override fun goTo(route: Route) {
    closeCovers()
    navigator.goTo(route)
  }

  override fun openInNewTab(route: Route): Int? {
    closeCovers()
    return navigator.openInNewTab(route)
  }

  private fun closeCovers() {
    settings.close()
    closeQueuePage()
  }
}
