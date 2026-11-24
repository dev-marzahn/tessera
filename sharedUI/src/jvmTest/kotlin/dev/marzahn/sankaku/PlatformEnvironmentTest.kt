package dev.marzahn.sankaku

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformEnvironmentTest {
  private val onMac = System.getProperty("os.name").startsWith("Mac")

  @Test
  fun namesTheVersionPlatformOsAndDevice() {
    val environment = platformEnvironment()

    assertEquals(APP_VERSION, environment.version)
    assertTrue(environment.os.startsWith(environment.platform), environment.os)
    assertTrue(environment.device.isNotBlank())
    if (onMac) {
      assertEquals("macOS", environment.platform)
      assertTrue(Regex("""[A-Za-z]+\d+,\d+( \(.+\))?""").matches(environment.device), environment.device)
    }
  }
}
