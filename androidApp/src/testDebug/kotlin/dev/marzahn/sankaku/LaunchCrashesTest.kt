package dev.marzahn.sankaku

import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.log.CrashReports
import dev.marzahn.sankaku.log.installUncaughtHandler
import org.koin.core.module.Module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaunchCrashesTest {
  private val handler = Thread.getDefaultUncaughtExceptionHandler()
  private val crashFile = InMemoryTextFile()
  private val crashes = LaunchCrashes(CrashReports(crashFile))

  @AfterTest
  fun restore() = Thread.setDefaultUncaughtExceptionHandler(handler)

  private fun crash() {
    installUncaughtHandler { crashes.current }
    Thread { throw IllegalStateException("crashed") }.apply { start() }.join()
  }

  @Test
  fun aCrashBeforeTheLaunchIsChosenIsKept() {
    crash()
    assertContains(crashFile.read().orEmpty(), "IllegalStateException")
  }

  @Test
  fun aCrashAfterTheLaunchIsChosenGoesToThatLaunch() {
    val launchFile = InMemoryTextFile()
    crashes.follow(AndroidLaunch.Installed(CrashReports(launchFile)))
    crash()
    assertNull(crashFile.read())
    assertContains(launchFile.read().orEmpty(), "IllegalStateException")
  }

  @Test
  fun aDemoLaunchBuildsItsModulesOnlyWhenAsked() {
    var built = false
    val demo = DemoLaunch {
      built = true
      emptyList<Module>()
    }
    crashes.follow(demo)
    assertFalse(built)
    demo.overrides
    assertTrue(built)
  }
}
