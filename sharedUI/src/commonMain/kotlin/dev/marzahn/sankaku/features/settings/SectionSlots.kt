package dev.marzahn.sankaku.features.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

internal const val UPDATES_FILLED = true

@Composable
internal fun ColumnScope.UpdatesSettings() = UpdatesRows()

internal const val DIAGNOSTICS_FILLED = true

@Composable
internal fun ColumnScope.DiagnosticsSettings() = DiagnosticsSection()

internal const val ABOUT_FILLED = true

@Composable
internal fun ColumnScope.AboutSettings() = AboutRows()
