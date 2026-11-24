package dev.marzahn.sankaku.features.auth

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

@Composable
internal expect fun SignInPresentation(
  reason: String?,
  onDismiss: () -> Unit,
  body: @Composable ColumnScope.() -> Unit,
  confirm: @Composable () -> Unit,
  dismiss: @Composable () -> Unit,
)
