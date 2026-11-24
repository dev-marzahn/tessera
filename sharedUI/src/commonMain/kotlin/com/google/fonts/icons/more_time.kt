package com.google.fonts.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

public val more_time: ImageVector
  get() {
    if (_more_time != null) {
      return _more_time!!
    }
    _more_time =
      ImageVector.Builder(
        name = "more_time",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
      )
        .apply {
          group(translationY = 960f) {
            addPath(
              pathData = addPathNodes("M299.5-148Q234-176 185-225t-77-114.5Q80-405 80-480t28-140.5Q136-686 185-735t114.5-77Q365-840 440-840q21 0 40.5 2.5T520-830v82q-20-6-39.5-9t-40.5-3q-118 0-199 81t-81 199q0 118 81 199t199 81q118 0 199-81t81-199q0-11-1-20t-3-20h82q2 11 2 20v20q0 75-28 140.5T695-225q-49 49-114.5 77T440-120q-75 0-140.5-28ZM552-312 400-464v-216h80v184l128 128-56 56Zm168-288v-120H600v-80h120v-120h80v120h120v80H800v120h-80Z"),
              fill = SolidColor(Color.Black),
            )
          }
        }
        .build()
    return _more_time!!
  }

private var _more_time: ImageVector? = null
