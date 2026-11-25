package dev.marzahn.sankaku.images

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Canvas
import coil3.Image
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageMemoryAndroidTest {
  private class RecordingApplication : Application() {
    val memoryCallbacks = mutableListOf<ComponentCallbacks>()
    val lifecycleCallbacks = mutableListOf<ActivityLifecycleCallbacks>()

    override fun getApplicationContext(): Context = this

    override fun registerComponentCallbacks(callback: ComponentCallbacks) {
      memoryCallbacks += callback
    }

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
      lifecycleCallbacks += callback
    }

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
      lifecycleCallbacks -= callback
    }

    fun hideUi() {
      val listeners = memoryCallbacks.filterIsInstance<ComponentCallbacks2>()
      check(listeners.isNotEmpty()) { "nothing listens for memory trims yet: decode an image first" }
      listeners.forEach { it.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) }
    }

    fun showUi() {
      check(lifecycleCallbacks.isNotEmpty()) { "nothing waits for the app to come back" }
      val activity = object : Activity() {
        override fun getApplicationContext(): Context = this@RecordingApplication
      }
      lifecycleCallbacks.toList().forEach { it.onActivityStarted(activity) }
    }
  }

  private class DecodedImage(override val size: Long) : Image {
    override val width = 1
    override val height = 1
    override val shareable = true
    override fun draw(canvas: Canvas) = Unit
  }

  private val app = RecordingApplication()
  private val loader = ImageLoader.Builder(app).imageMemoryPolicy(app).build()
  private val cache = requireNotNull(loader.memoryCache)

  private fun decode(key: String) = runBlocking {
    val request = ImageRequest.Builder(app)
      .data(DecodedImage(size = MEGABYTE))
      .memoryCacheKey(key)
      .fetcherFactory<DecodedImage> { image, _, _ ->
        Fetcher { ImageFetchResult(image, isSampled = false, dataSource = DataSource.MEMORY) }
      }
      .build()
    loader.execute(request)
  }

  @Test
  fun hidingTheAppEmptiesTheImageCache() {
    decode("a")
    decode("b")
    assertEquals(2 * MEGABYTE, cache.size)

    app.hideUi()

    assertEquals(0, cache.size)
  }

  @Test
  fun comingBackKeepsDecodedImagesAgain() {
    decode("a")
    app.hideUi()
    assertEquals(0, cache.size)

    app.showUi()
    assertEquals(cache.initialMaxSize, cache.maxSize)

    decode("b")
    assertEquals(MEGABYTE, cache.size)
  }
}

private const val MEGABYTE = 1024L * 1024
