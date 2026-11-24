package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.ImageRefusedException
import dev.marzahn.sankaku.api.dto.PostDto
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.readBuffer
import kotlinx.io.RawSink
import kotlin.time.Clock

class NoOriginalFileException : Exception("No original file")

class DownloadFailedException(val status: Int) : Exception("Download failed ($status)")

class OriginalFiles(
  private val client: HttpClient,
  private val repository: PostsRepository,
  private val store: PostStateStore,
  private val clock: Clock = Clock.System,
) {
  suspend fun download(post: PostDto, sink: RawSink) {
    val known = store.overrides.value[post.id]?.urls?.file ?: post.fileUrl
    if (known == null || !isSignedUrlUsable(known, clock.now())) return fetch(freshUrl(post.id), sink)
    try {
      fetch(known, sink)
    } catch (e: Exception) {
      if (!e.isRefusal()) throw e
      fetch(freshUrl(post.id), sink)
    }
  }

  private suspend fun freshUrl(postId: String): String = repository.refetch(postId).fileUrl ?: throw NoOriginalFileException()

  private suspend fun fetch(url: String, sink: RawSink) {
    client.prepareGet(url).execute { response ->
      val status = response.status.value
      if (status !in 200..299) throw DownloadFailedException(status)
      val body = response.bodyAsChannel()
      while (!body.exhausted()) body.readBuffer(CHUNK_BYTES).transferTo(sink)
      sink.flush()
    }
  }

  private fun Throwable.isRefusal(): Boolean = generateSequence(this) { it.cause }.any {
    it is ImageRefusedException || (it is DownloadFailedException && it.status in REFUSED_STATUSES)
  }

  private companion object {
    const val CHUNK_BYTES = 256L * 1024
    val REFUSED_STATUSES = setOf(401, 403, 404, 410)
  }
}

fun originalFileName(post: PostDto): String {
  fun String.safe(max: Int) = filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.take(max).lowercase()
  val ext = post.fileExt?.safe(MAX_EXT)?.ifEmpty { null }
    ?: post.fileType?.substringAfter('/', "")?.substringBefore('+')?.substringBefore(';')?.safe(MAX_EXT)?.ifEmpty { null }
    ?: "bin"
  val base = post.md5.safe(MAX_BASE).ifEmpty { "post" }
  return "$base.$ext"
}

private const val MAX_EXT = 8
private const val MAX_BASE = 64

fun originalMimeType(post: PostDto): String? =
  post.fileType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { '/' in it } ?: when (post.fileExt?.lowercase()) {
  "jpg", "jpeg" -> "image/jpeg"
  "png" -> "image/png"
  "gif" -> "image/gif"
  "webp" -> "image/webp"
  "avif" -> "image/avif"
  "mp4" -> "video/mp4"
  "webm" -> "video/webm"
  else -> null
}
