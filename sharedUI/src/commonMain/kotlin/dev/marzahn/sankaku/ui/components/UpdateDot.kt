package dev.marzahn.sankaku.ui.components

import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

const val UPDATE_AVAILABLE_DOT = "Update available"

@Composable
fun UpdateDot(modifier: Modifier = Modifier) {
  Badge(modifier, containerColor = MaterialTheme.colorScheme.primary)
}
