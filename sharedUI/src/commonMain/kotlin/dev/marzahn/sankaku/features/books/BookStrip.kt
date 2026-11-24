package dev.marzahn.sankaku.features.books

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import dev.marzahn.sankaku.api.FILE_HOST
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.BookPlace
import dev.marzahn.sankaku.data.SavedReading
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.data.url
import dev.marzahn.sankaku.features.grid.Rating
import dev.marzahn.sankaku.features.post.aspectRatio
import dev.marzahn.sankaku.features.post.isAnimated
import dev.marzahn.sankaku.features.post.isFlash
import dev.marzahn.sankaku.features.post.isVideo
import dev.marzahn.sankaku.features.post.previewRequest
import dev.marzahn.sankaku.features.post.sampleRequest
import dev.marzahn.sankaku.features.post.sampleVariant
import io.ktor.http.Url
import kotlin.math.roundToInt

internal const val PRELOAD_PAGES = 3

internal fun preloadedPages(current: Int, count: Int): IntRange = (current + 1)..minOf(current + PRELOAD_PAGES, count - 1)

internal data class PageSpan(val index: Int, val top: Int, val height: Int)

internal fun mostShownPage(pages: List<PageSpan>, viewportStart: Int, viewportEnd: Int): Int {
  var best = 0
  var bestShown = -1
  for ((index, top, height) in pages) {
    val shown = minOf(top + height, viewportEnd) - maxOf(top, viewportStart)
    if (shown > bestShown) {
      best = index
      bestShown = shown
    }
  }
  return best
}

internal fun hiddenByGOnly(page: PostDto, gOnly: Boolean): Boolean = gOnly && Rating.ofCode(page.rating) != Rating.G

internal fun savedPageIndex(pages: List<PostDto>, sequence: Int?): Int {
  if (sequence == null) return 0
  var found = 0
  pages.forEachIndexed { index, page -> if ((page.sequence ?: index) <= sequence) found = index }
  return found
}

internal fun openingPageIndex(pages: List<PostDto>, saved: SavedReading?): Int =
  if (saved == null || saved.finished) 0 else savedPageIndex(pages, saved.sequence)

internal fun bookPlace(pages: List<PostDto>, index: Int): BookPlace =
  BookPlace(sequence = pages.getOrNull(index)?.sequence ?: index, page = index + 1, pages = pages.size)

internal fun resumeLabel(page: Int): String = "Page $page"

internal const val START_OVER = "Start over"

internal fun pageLabel(current: Int, count: Int): String = "page ${current + 1} / $count"

internal fun needsFullFile(page: PostDto, shownWidth: Float): Boolean {
  if (isVideo(page) || isAnimated(page) || isFlash(page) || sampleVariant(page) == Variant.File) return false
  val fileWidth = page.fileWidth ?: return false
  val sampleWidth = page.sampleWidth ?: return false
  return fileWidth > sampleWidth && shownWidth > sampleWidth
}

internal fun pageSize(page: PostDto, width: Int): Size? {
  if (width <= 0) return null
  return Size(width, (width / aspectRatio(page)).roundToInt().coerceAtLeast(1))
}

internal fun pageRequest(context: PlatformContext, page: PostDto, width: Int, gOnly: Boolean): ImageRequest? {
  if (page.redirectToSignup || hiddenByGOnly(page, gOnly)) return null
  val size = pageSize(page, width)
  return if (showsPreviewFirst(page)) previewRequest(context, page, size) else sampleRequest(context, page, size)
}

private fun showsPreviewFirst(page: PostDto) = isVideo(page) || isAnimated(page) || isFlash(page)

internal fun pagePreviewRequest(context: PlatformContext, page: PostDto, width: Int): ImageRequest? {
  if (showsPreviewFirst(page)) return null
  val sample = page.url(sampleVariant(page)) ?: return null
  if (runCatching { Url(sample).host }.getOrNull() != FILE_HOST) return null
  return previewRequest(context, page, pageSize(page, width))
}
