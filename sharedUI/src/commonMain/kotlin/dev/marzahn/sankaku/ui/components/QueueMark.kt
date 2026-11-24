package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.view_agenda

@Composable
fun QueueMark(position: Int, modifier: Modifier = Modifier, size: Dp = 13.dp, tint: Color = LocalContentColor.current) {
  HoverHint("In the queue · item $position") {
    Icon(view_agenda, contentDescription = "in the queue, item $position", tint = tint, modifier = modifier.size(size))
  }
}
