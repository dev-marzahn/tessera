package com.google.fonts.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

public val subdirectory_arrow_right: ImageVector
  get() {
    if (_subdirectory_arrow_right != null) {
      return _subdirectory_arrow_right!!
    }
    _subdirectory_arrow_right =
      ImageVector.Builder(
        name = "subdirectory_arrow_right",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
      )
        .apply {
          group(translationY = 960f) {
            addPath(
              pathData = addPathNodes("m560-120-57-57 144-143H200v-480h80v400h367L503-544l56-57 241 241-240 240Z"),
              fill = SolidColor(Color.Black),
            )
          }
        }
        .build()
    return _subdirectory_arrow_right!!
  }

private var _subdirectory_arrow_right: ImageVector? = null
