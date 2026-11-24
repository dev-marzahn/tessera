package dev.marzahn.sankaku.cli

import dev.marzahn.sankaku.api.AuthApi
import dev.marzahn.sankaku.api.PostsApi
import dev.marzahn.sankaku.api.RateLimiter
import dev.marzahn.sankaku.api.createAuthClient
import dev.marzahn.sankaku.api.createEngine
import dev.marzahn.sankaku.api.createMainClient
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.SignInResult
import dev.marzahn.sankaku.data.HashedPost
import dev.marzahn.sankaku.data.PreviewHash
import dev.marzahn.sankaku.data.oneBatch
import dev.marzahn.sankaku.data.oneFamily
import dev.marzahn.sankaku.log.RequestLog
import dev.marzahn.sankaku.net.ConnectivityState
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class PreviewHashDiscovery(private val env: Map<String, String>) {
  private val engine = createEngine()
  private val log = RequestLog()
  private val limiter = RateLimiter()
  private val connectivity = ConnectivityState()
  private val session = AuthSession(AuthApi(createAuthClient(engine, log, limiter, connectivity)), InMemoryTokenStore())
  private val client = createMainClient(engine, log, limiter, connectivity, session)
  private val posts = PostsApi(client)
  private val work = Files.createTempDirectory("phash").toFile()
  private val lookDir = env["SANKAKU_PHASH_DIR"]?.let(::File)
  private val gOnly = env["SANKAKU_PHASH_G"] == "true"

  private val failures = ConcurrentHashMap<String, Int>()

  private class Hashed(val post: PostDto, val hashed: HashedPost, val image: BufferedImage?)

  private enum class Kind { Family, OneBatch, Apart }

  private val table = Kind.entries.associateWith { IntArray(DISTANCES.size) }
  private val apartOneUploader = IntArray(DISTANCES.size)
  private val apartOneSize = IntArray(DISTANCES.size)
  private var pairCount = 0L
  private var looked = 0

  suspend fun run() {
    val login = env["SANKAKU_LOGIN"].orEmpty()
    val password = env["SANKAKU_PASSWORD"].orEmpty()
    if (login.isNotBlank() && password.isNotBlank()) {
      val signIn = session.signIn(login, password)
      println("Sign-in: ${if (signIn == SignInResult.Ok) "ok" else signIn::class.simpleName}")
    }
    val artists = env["SANKAKU_PHASH_ARTISTS"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: busyArtists()
    println("Artists: ${artists.size}${if (gOnly) ", G only" else ""}")
    lookDir?.mkdirs()
    artists.forEachIndexed { n, artist ->
      val grid = grid(artist)
      val hashed = hashAll(grid)
      pairs(hashed, n + 1)
      println("Artist ${n + 1}: ${grid.size} posts, ${hashed.size} hashed")
    }
    println("$pairCount pairs. Distance ≤ d: family, one batch, apart (of those: one uploader, one size)")
    DISTANCES.forEachIndexed { i, d ->
      println(
        "  ≤$d: ${table.getValue(Kind.Family)[i]}, ${table.getValue(Kind.OneBatch)[i]}, ${table.getValue(Kind.Apart)[i]} " +
          "(${apartOneUploader[i]}, ${apartOneSize[i]})",
      )
    }
    println("G pairs written: $looked")
  }

  private suspend fun busyArtists(): List<String> {
    val search = listOfNotNull("order:popular", "rating:s".takeIf { gOnly })
    val first = posts.keyset(search, limit = 40)
    val second = first.meta.next?.let { posts.keyset(search, next = it, page = 2, limit = 40) }
    val popular = (first.data + second?.data.orEmpty()).filterNot { it.redirectToSignup }
    val found = mutableMapOf<String, Int>()
    for (post in popular) {
      val full = runCatching { posts.full(post.id) }.getOrNull() ?: continue
      full.tags.filter { it.category == TagType.Artist }.forEach { tag ->
        val count = tag.postCount ?: return@forEach
        if (count in MIN_POSTS..MAX_POSTS) found[tag.searchName] = count
      }
      if (found.size >= ARTISTS) break
    }
    return found.entries.sortedByDescending { it.value }.take(ARTISTS).map { it.key }
  }

  private suspend fun grid(artist: String): List<PostDto> {
    val all = mutableListOf<PostDto>()
    var next: String? = null
    var page = 1
    val search = listOfNotNull(artist, "rating:s".takeIf { gOnly })
    while (all.size < POSTS_PER_ARTIST) {
      val result = posts.keyset(search, next = next, page = page)
      all += result.data.filterNot { it.redirectToSignup }
      next = result.meta.next ?: break
      page++
    }
    return all.distinctBy { it.id }.take(POSTS_PER_ARTIST)
  }

  private suspend fun hashAll(grid: List<PostDto>): List<Hashed> = coroutineScope {
    val gate = Semaphore(6)
    grid.filter { it.previewUrl != null }.map { post ->
      async {
        gate.withPermit {
          runCatching { hash(post) }.onFailure { failures.merge(it::class.simpleName ?: "?", 1, Int::plus) }.getOrNull()
        }
      }
    }.awaitAll().filterNotNull().also {
      if (failures.isNotEmpty()) println("Failed: $failures")
      failures.clear()
    }
  }

  private suspend fun hash(post: PostDto): Hashed? {
    val bytes = client.get(post.previewUrl!!).bodyAsBytes()
    val image = withContext(Dispatchers.IO) { decode(bytes) } ?: return null
    val argb = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    val hash = PreviewHash.of(argb, image.width, image.height) ?: return null
    val hashed = HashedPost(post.id, hash, post.parentId, post.createdAt)
    return Hashed(post, hashed, image.takeIf { post.rating == "s" && lookDir != null })
  }

  private fun decode(bytes: ByteArray): BufferedImage? {
    val input = File.createTempFile("preview", ".avif", work)
    val output = File(input.path.removeSuffix(".avif") + ".png")
    try {
      input.writeBytes(bytes)
      val process = ProcessBuilder("sips", "-s", "format", "png", input.path, "--out", output.path)
        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
      if (!process.waitFor(30, TimeUnit.SECONDS)) process.destroyForcibly()
      return if (output.exists()) ImageIO.read(output) else ImageIO.read(input)
    } finally {
      input.delete()
      output.delete()
    }
  }

  private fun pairs(hashed: List<Hashed>, artist: Int) {
    for (i in hashed.indices) {
      for (j in i + 1 until hashed.size) {
        pairCount++
        val a = hashed[i]
        val b = hashed[j]
        val distance = a.hashed.hash.distance(b.hashed.hash)
        if (distance > DISTANCES.last()) continue
        val kind = when {
          oneFamily(a.hashed, b.hashed) -> Kind.Family
          oneBatch(a.hashed, b.hashed) -> Kind.OneBatch
          else -> Kind.Apart
        }
        val oneUploader = a.post.author?.name != null && a.post.author?.name == b.post.author?.name
        val oneSize = a.post.fileWidth == b.post.fileWidth && a.post.fileHeight == b.post.fileHeight
        DISTANCES.forEachIndexed { i, d ->
          if (distance > d) return@forEachIndexed
          table.getValue(kind)[i]++
          if (kind == Kind.Apart && oneUploader) apartOneUploader[i]++
          if (kind == Kind.Apart && oneSize) apartOneSize[i]++
        }
        val dir = lookDir ?: continue
        if (a.image != null && b.image != null) {
          looked++
          val name = "a$artist-d${distance.toString().padStart(2, '0')}-${kind.name.lowercase()}-$looked.png"
          ImageIO.write(sideBySide(a.image, b.image), "png", File(dir, name))
        }
      }
    }
  }

  private fun sideBySide(a: BufferedImage, b: BufferedImage): BufferedImage {
    val out = BufferedImage(a.width + b.width + 8, maxOf(a.height, b.height), BufferedImage.TYPE_INT_RGB)
    val g = out.createGraphics()
    g.drawImage(a, 0, 0, null)
    g.drawImage(b, a.width + 8, 0, null)
    g.dispose()
    return out
  }

  fun close() {
    client.close()
    work.deleteRecursively()
  }

  companion object {
    val DISTANCES = intArrayOf(0, 2, 4, 5, 6, 7, 8, 10, 12, 16)
    const val POSTS_PER_ARTIST = 400
    const val ARTISTS = 4
    const val MIN_POSTS = 400
    const val MAX_POSTS = 20_000
  }
}
