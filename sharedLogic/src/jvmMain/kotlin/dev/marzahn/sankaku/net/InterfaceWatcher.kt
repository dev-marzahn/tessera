package dev.marzahn.sankaku.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

class InterfaceWatcher(
  private val connectivity: ConnectivityState,
  private val scope: CoroutineScope,
  private val connected: suspend () -> Set<String>? = ::connectedInterfaces,
) {
  fun start(): Job = scope.launch {
    connectivity.unreachable.collectLatest { unreachable ->
      if (!unreachable) return@collectLatest
      var last = connected()
      while (true) {
        delay(POLL_MS)
        val now = connected() ?: continue
        val gained = last?.let { now - it }.orEmpty().isNotEmpty()
        last = now
        if (gained) {
          delay(SETTLE_MS)
          connectivity.networkAvailable()
        }
      }
    }
  }

  companion object {
    const val POLL_MS = 2_000L

    const val SETTLE_MS = 3_000L

    suspend fun connectedInterfaces(): Set<String>? = withContext(Dispatchers.IO) {
      runCatching {
        NetworkInterface.networkInterfaces()
          .filter { it.isUp && !it.isLoopback }
          .filter { nif -> nif.inetAddresses().anyMatch { !it.isLinkLocalAddress && !it.isLoopbackAddress } }
          .map { it.name }
          .toList()
          .toSet()
      }.getOrNull()
    }
  }
}
