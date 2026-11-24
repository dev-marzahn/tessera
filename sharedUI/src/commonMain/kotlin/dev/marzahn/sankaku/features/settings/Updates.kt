package dev.marzahn.sankaku.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.sankaku.platformOpenUrl
import dev.marzahn.sankaku.ui.components.UPDATE_AVAILABLE_DOT
import dev.marzahn.sankaku.update.AvailableUpdate
import dev.marzahn.sankaku.update.UpdateCheck
import org.koin.compose.koinInject

internal const val CHECK_FOR_UPDATES = "Check for updates"

internal const val CheckForUpdatesHelp = "Once a day, asks GitHub whether a newer version exists. Nothing is downloaded."

internal fun updateAvailableLabel(update: AvailableUpdate): String = "$UPDATE_AVAILABLE_DOT · ${update.version}"

@Composable
internal fun UpdatesRows(updates: UpdateCheck = koinInject(), open: (String) -> Unit = ::platformOpenUrl) {
  val enabled by updates.enabled.collectAsStateWithLifecycle()
  val available by updates.available.collectAsStateWithLifecycle()
  SwitchRow(CHECK_FOR_UPDATES, CheckForUpdatesHelp, enabled, updates::setEnabled)
  available?.let { update ->
    ActionRow(updateAvailableLabel(update), help = "Opens the release page", onClick = { open(update.url) })
  }
}
