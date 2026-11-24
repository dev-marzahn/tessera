package dev.marzahn.sankaku.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

internal val TesseraAppIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "TesseraAppIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = BODY,
    viewportHeight = BODY,
  ).apply {
    addPath(
      pathData = addPathNodes(
        "M185,0H639A185,185 0,0 1,824 185V639A185,185 0,0 1,639 824H185A185,185 0,0 1,0 639V185A185,185 0,0 1,185 0Z",
      ),
      fill = Brush.radialGradient(
        0f to Color(0xFF3A3430),
        1f to Color(0xFF1A1715),
        center = Offset(BODY * 0.5f, BODY * 0.3f),
        radius = BODY * 0.8f,
      ),
    )
    group(
      scaleX = SCALE,
      scaleY = SCALE,
      translationX = (BODY - FOREGROUND * SCALE) / 2,
      translationY = (BODY - FOREGROUND * SCALE) / 2,
    ) {
      for (tile in TILES) addPath(addPathNodes(tile), fill = SolidColor(Color(0xFFF3EDE8)))
      addPath(
        addPathNodes("M75.5,38V46A3.5,3.5 0,0 1,72 49.5H62A3.5,3.5 0,0 1,58.5 46V36A3.5,3.5 0,0 1,62 32.5H70"),
        stroke = SolidColor(Color(0xFFF59E0B)),
        strokeLineWidth = 4f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      )
    }
  }.build()
}

private const val BODY = 824f
private const val SCALE = 10.5f

private const val FOREGROUND = 108f

private val TILES = listOf(
  "M36,30H46A6,6 0,0 1,52 36V46A6,6 0,0 1,46 52H36A6,6 0,0 1,30 46V36A6,6 0,0 1,36 30Z",
  "M36,56H46A6,6 0,0 1,52 62V72A6,6 0,0 1,46 78H36A6,6 0,0 1,30 72V62A6,6 0,0 1,36 56Z",
  "M62,56H72A6,6 0,0 1,78 62V72A6,6 0,0 1,72 78H62A6,6 0,0 1,56 72V62A6,6 0,0 1,62 56Z",
)
