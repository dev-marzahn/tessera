package dev.marzahn.sankaku.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AndroidFingerprintTest {
  @Test
  fun androidClaimsChromesDesktopSite() {
    val current = BrowserFingerprint.Current
    val desktopSite = BrowserFingerprint(BrowserPlatform.AndroidDesktopSite)
    assertEquals(desktopSite.userAgent, current.userAgent)
    assertEquals(desktopSite.clientHints, current.clientHints)
    assertFalse("Android" in current.userAgent)
  }
}
