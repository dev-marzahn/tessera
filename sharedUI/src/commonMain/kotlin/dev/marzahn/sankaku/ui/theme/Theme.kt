package dev.marzahn.sankaku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme

val Amber = Color(0xFFF59E0B)

private val Graphite = Color(0xFF7B7673)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SankakuTheme(isDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialExpressiveTheme(
    colorScheme = remember(isDark) { sankakuColorScheme(isDark) },
    motionScheme = MotionScheme.expressive(),
    typography = robotoTypography(),
    content = content,
  )
}

fun sankakuColorScheme(isDark: Boolean): ColorScheme =
  dynamicColorScheme(seedColor = Amber, isDark = isDark, neutral = Graphite, neutralVariant = Graphite)
