package dev.marzahn.sankaku

import dev.marzahn.sankaku.demo.androidDemoModules
import dev.marzahn.sankaku.demo.demoCrashReports
import org.koin.core.module.Module

class DemoLaunch(buildOverrides: () -> List<Module>) : AndroidLaunch {
  override val crashReports = demoCrashReports()
  override val overrides by lazy(buildOverrides)
}

internal val launchChoice = LaunchChoice { context, intent, installed ->
  if (intent.getBooleanExtra("demo", false)) DemoLaunch { androidDemoModules(context) } else installed
}
