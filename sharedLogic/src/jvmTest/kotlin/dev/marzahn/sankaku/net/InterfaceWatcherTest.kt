package dev.marzahn.sankaku.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InterfaceWatcherTest {
  private val connectivity = ConnectivityState()
  private var interfaces: Set<String>? = setOf("en0")
  private var reads = 0

  private fun TestScope.watch() = InterfaceWatcher(connectivity, backgroundScope, connected = { reads++; interfaces }).start()

  private fun TestScope.offline() {
    connectivity.setOffline(true)
    runCurrent()
  }

  private fun TestScope.nextChange() = advanceTimeBy(InterfaceWatcher.POLL_MS + InterfaceWatcher.SETTLE_MS + 1)

  @Test
  fun theNetworkComingBackClearsOfflineAndAsksForARetry() = runTest {
    watch()
    offline()
    interfaces = emptySet()
    advanceTimeBy(InterfaceWatcher.POLL_MS + 1)
    assertTrue(connectivity.offline.value)

    interfaces = setOf("en0")
    nextChange()

    assertFalse(connectivity.offline.value)
    assertEquals(1, connectivity.retryRequests.value)
  }

  @Test
  fun anotherNetworkClearsOfflineToo() = runTest {
    watch()
    offline()
    interfaces = setOf("en0", "en7")
    nextChange()

    assertFalse(connectivity.offline.value)
  }

  @Test
  fun nothingChangedSaysNothing() = runTest {
    watch()
    offline()
    advanceTimeBy(10 * InterfaceWatcher.POLL_MS)

    assertTrue(connectivity.offline.value)
    assertEquals(0, connectivity.retryRequests.value)
  }

  @Test
  fun oneOfTwoNetworksGoingAwaySaysNothing() = runTest {
    interfaces = setOf("en0", "en7")
    watch()
    offline()
    interfaces = setOf("en0")
    advanceTimeBy(10 * InterfaceWatcher.POLL_MS)

    assertTrue(connectivity.offline.value)
  }

  @Test
  fun aFailedReadSaysNothing() = runTest {
    watch()
    offline()
    interfaces = null
    advanceTimeBy(InterfaceWatcher.POLL_MS + 1)
    interfaces = setOf("en0")
    advanceTimeBy(10 * InterfaceWatcher.POLL_MS)

    assertTrue(connectivity.offline.value)
  }

  @Test
  fun onlineItReadsNothing() = runTest {
    watch()
    advanceTimeBy(10 * InterfaceWatcher.POLL_MS)

    assertEquals(0, reads)
  }

  @Test
  fun aTryThatFailsIsNotRepeated() = runTest {
    watch()
    offline()
    interfaces = setOf("en0", "en7")
    nextChange()
    assertEquals(1, connectivity.retryRequests.value)
    offline()

    advanceTimeBy(60_000)

    assertTrue(connectivity.offline.value)
    assertEquals(1, connectivity.retryRequests.value)
  }

  @Test
  fun aLaterOutageStartsFromItsOwnInterfaces() = runTest {
    watch()
    offline()
    interfaces = setOf("en0", "en7")
    nextChange()
    assertFalse(connectivity.offline.value)
    interfaces = setOf("en0", "en7", "utun4")
    advanceTimeBy(60_000)
    offline()
    advanceTimeBy(10 * InterfaceWatcher.POLL_MS)

    assertTrue(connectivity.offline.value)
    assertEquals(1, connectivity.retryRequests.value)
  }
}
