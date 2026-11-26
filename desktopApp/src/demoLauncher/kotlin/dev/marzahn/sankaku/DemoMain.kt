package dev.marzahn.sankaku

import dev.marzahn.sankaku.demo.demoCrashReports
import dev.marzahn.sankaku.demo.desktopDemoModules
import org.koin.core.module.Module

class DemoLaunch(override val overrides: List<Module>) : DesktopLaunch {
  override val crashReports = demoCrashReports()

  override fun claim(onFocus: () -> Unit) = true
}

fun main() = runApp(DemoLaunch(desktopDemoModules()))
