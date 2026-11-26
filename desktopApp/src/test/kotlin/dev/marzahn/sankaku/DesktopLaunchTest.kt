package dev.marzahn.sankaku

import dev.marzahn.sankaku.log.installUncaughtHandler
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopLaunchTest {
  private val handler = Thread.getDefaultUncaughtExceptionHandler()
  private val crashFile = File(APP_DATA_DIR, "crash.txt")

  @AfterTest
  fun restore() {
    Thread.setDefaultUncaughtExceptionHandler(handler)
    crashFile.delete()
  }

  private fun crashIn(launch: DesktopLaunch) {
    launch.crashReports.installUncaughtHandler(closesApp = { true })
    Thread { throw IllegalStateException("crashed") }.apply { start() }.join()
  }

  @Test
  fun aDemoCrashLeavesTheCrashFileAlone() {
    val demo = DemoLaunch(emptyList())
    crashIn(demo)
    assertFalse(crashFile.exists())
    assertNull(demo.crashReports.pending.value)
  }

  @Test
  fun anInstalledCrashIsKeptForTheNextLaunch() {
    crashIn(DesktopLaunch.Installed())
    assertTrue(crashFile.readText().contains("IllegalStateException"))
  }
}
