package dev.marzahn.sankaku.net

class NetworkWatcher(private val connectivity: ConnectivityState) {
  private var announced: Any? = null

  fun onDefaultNetwork(network: Any, validated: Boolean) {
    when {
      !validated -> if (network == announced) announced = null
      network != announced -> {
        announced = network
        connectivity.networkAvailable()
      }
    }
  }

  fun onLost(network: Any) {
    if (network == announced) announced = null
  }
}
