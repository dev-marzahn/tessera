package dev.marzahn.sankaku.features.post

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import kotlin.math.min

actual val platformAnimatesGifs: Boolean = true

@Composable
actual fun AnimatedImage(file: Path, modifier: Modifier, onShown: () -> Unit) {
  val shown by rememberUpdatedState(onShown)
  var current by remember(file) { mutableStateOf<Image?>(null) }

  LaunchedEffect(file) {
    val codec = withContext(Dispatchers.Default) {
      runCatching { Codec.makeFromData(Data.makeFromBytes(FileSystem.SYSTEM.read(file) { readByteArray() })) }.getOrNull()
    } ?: return@LaunchedEffect
    val working = Bitmap()
    var stale: Image? = null
    var frame = 0
    try {
      working.allocPixels(ImageInfo.makeN32Premul(codec.width, codec.height))
      val durations = codec.framesInfo.map { it.duration.toLong().coerceAtLeast(MIN_FRAME_MILLIS) }
      val count = durations.size.coerceAtLeast(1)
      while (true) {
        val prior = if (frame == 0) -1 else frame - 1
        val image = withContext(Dispatchers.Default) {
          codec.readPixels(working, frame, prior)
          Image.makeFromBitmap(working)
        }
        stale?.close()
        stale = current
        current = image
        if (frame == 0 && stale == null) shown()
        if (count == 1) break
        delay(durations[frame])
        frame = (frame + 1) % count
      }
    } finally {
      working.close()
      codec.close()
    }
  }

  Canvas(modifier) {
    val image = current ?: return@Canvas
    val scale = min(size.width / image.width, size.height / image.height)
    val width = image.width * scale
    val height = image.height * scale
    val left = (size.width - width) / 2
    val top = (size.height - height) / 2
    drawIntoCanvas {
      it.nativeCanvas.drawImageRect(
        image,
        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        Rect.makeXYWH(left, top, width, height),
        SamplingMode.LINEAR,
        null,
        true,
      )
    }
  }
}

private const val MIN_FRAME_MILLIS = 20L
