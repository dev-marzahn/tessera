package dev.marzahn.sankaku.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class DefaultNetworkCallback private constructor(
  private val watcher: NetworkWatcher,
) : ConnectivityManager.NetworkCallback() {
  override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
    watcher.onDefaultNetwork(network, capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
  }

  override fun onLost(network: Network) {
    watcher.onLost(network)
  }

  companion object {
    fun register(context: Context, connectivity: ConnectivityState): DefaultNetworkCallback {
      val callback = DefaultNetworkCallback(NetworkWatcher(connectivity))
      context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callback)
      return callback
    }
  }
}
