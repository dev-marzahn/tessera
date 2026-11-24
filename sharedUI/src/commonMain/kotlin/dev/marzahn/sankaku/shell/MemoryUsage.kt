package dev.marzahn.sankaku.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import dev.marzahn.sankaku.processMemoryBytes
import dev.marzahn.sankaku.processMemoryTargetBytes
import dev.marzahn.sankaku.tabs.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun MemoryUsage(modifier: Modifier = Modifier, read: () -> Long? = ::processMemoryBytes) {
  val bytes by produceState<Long?>(null) {
    while (true) {
      value = withContext(Dispatchers.Default) { read() }
      delay(MEMORY_POLL_MS)
    }
  }
  val shown = bytes ?: return
  Text(
    "Memory usage ${formatBytes(shown)}",
    style = MaterialTheme.typography.labelSmall,
    color = if (shown > processMemoryTargetBytes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier,
  )
}

private const val MEMORY_POLL_MS = 2_000L
