package dev.marzahn.sankaku

import java.awt.HeadlessException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenUrlJvmTest {
  @Test
  fun noBrowseSupportFails() {
    assertEquals(OpenUrlResult.Failed, launchUrl("https://example.com/") { throw UnsupportedOperationException() })
  }

  @Test
  fun aHeadlessDesktopFails() {
    assertEquals(OpenUrlResult.Failed, launchUrl("https://example.com/") { throw HeadlessException() })
  }

  @Test
  fun aFailedLaunchFails() {
    assertEquals(OpenUrlResult.Failed, launchUrl("https://example.com/") { throw IOException() })
  }

  @Test
  fun aLaunchedLinkOpens() {
    var browsed = 0
    assertEquals(OpenUrlResult.Opened, launchUrl("https://example.com/") { browsed++ })
    assertEquals(1, browsed)
  }
}
