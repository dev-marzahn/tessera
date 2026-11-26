package dev.marzahn.sankaku

import dev.marzahn.sankaku.log.CrashReports
import org.koin.core.module.Module

interface DesktopLaunch {
  val crashReports: CrashReports
  val overrides: List<Module>

  fun claim(onFocus: () -> Unit): Boolean

  class Installed : DesktopLaunch {
    override val crashReports = desktopCrashReports()
    override val overrides = emptyList<Module>()

    override fun claim(onFocus: () -> Unit): Boolean {
      migrateAppData()
      val instance = SingleInstance.acquire(APP_DATA_DIR) ?: return false
      instance.listen(onFocus)
      return true
    }
  }
}
