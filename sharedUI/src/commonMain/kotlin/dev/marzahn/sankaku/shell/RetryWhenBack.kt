package dev.marzahn.sankaku.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import dev.marzahn.sankaku.net.ConnectivityState
import org.koin.compose.koinInject

@Composable
fun RetryWhenBack(connectivity: ConnectivityState = koinInject(), retry: () -> Unit) {
  var handled by rememberSaveable { mutableIntStateOf(connectivity.retryRequests.value) }
  val latest by rememberUpdatedState(retry)
  LaunchedEffect(connectivity) {
    connectivity.retryRequests.collect {
      if (it != handled) {
        handled = it
        latest()
      }
    }
  }
}

@Composable
fun RetryOnWindowFocus(connectivity: ConnectivityState = koinInject()) {
  val focused = LocalWindowInfo.current.isWindowFocused
  LaunchedEffect(focused) {
    if (focused && connectivity.connectionFailedSinceRetry) connectivity.requestRetry()
  }
}
