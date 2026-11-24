package dev.marzahn.sankaku.features.post

import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.NoOriginalFileException
import dev.marzahn.sankaku.data.OriginalFiles
import dev.marzahn.sankaku.data.originalFileName
import dev.marzahn.sankaku.data.originalMimeType
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.RawSink

fun interface SavedFile {
  fun open()
}

interface SaveFolder {
  suspend fun find(name: String): SavedFile?

  suspend fun save(name: String, mimeType: String?, write: suspend (RawSink) -> Unit): SavedFile
}

sealed interface SaveOutcome {
  data class Saved(val file: SavedFile) : SaveOutcome

  data class Failed(val noFile: Boolean) : SaveOutcome
}

class PostSaver(
  private val files: OriginalFiles,
  private val folder: SaveFolder,
  private val messages: Messages,
  private val scope: CoroutineScope,
) {
  private val _saving = MutableStateFlow(emptySet<String>())

  val saving: StateFlow<Set<String>> = _saving.asStateFlow()

  private val _saved = MutableStateFlow(emptyMap<String, SavedFile>())

  val saved: StateFlow<Map<String, SavedFile>> = _saved.asStateFlow()

  private val _fresh = MutableStateFlow(emptyMap<String, SaveOutcome>())

  val fresh: StateFlow<Map<String, SaveOutcome>> = _fresh.asStateFlow()

  private val claims = MutableStateFlow(emptyMap<String, Int>())

  fun claim(postId: String): () -> Unit {
    claims.update { it + (postId to (it[postId] ?: 0) + 1) }
    var released = false
    return {
      if (!released) {
        released = true
        claims.update { val left = (it[postId] ?: 1) - 1; if (left > 0) it + (postId to left) else it - postId }
        if (!claimed(postId)) {
          val unshown = _fresh.value[postId]
          settle(postId)
          if (unshown is SaveOutcome.Saved) messages.show(savedMessage(unshown.file))
        }
      }
    }
  }

  private fun savedMessage(file: SavedFile) = Message("Saved", actionLabel = "Open", onAction = file::open)

  fun settle(postId: String) {
    _fresh.update { it - postId }
  }

  private fun claimed(postId: String) = postId in claims.value

  fun save(post: PostDto) {
    var started = false
    _saving.update {
      started = post.id !in it
      it + post.id
    }
    if (!started) return
    settle(post.id)
    scope.launch {
      val outcome = try {
        val name = originalFileName(post)
        val saved = folder.find(name) ?: run {
          if (!claimed(post.id)) messages.show("Saving…")
          folder.save(name, originalMimeType(post)) { sink -> files.download(post, sink) }
        }
        SaveOutcome.Saved(saved)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        SaveOutcome.Failed(noFile = e is NoOriginalFileException)
      } finally {
        _saving.update { it - post.id }
      }
      if (outcome is SaveOutcome.Saved) _saved.update { it + (post.id to outcome.file) }
      val onControl = claimed(post.id)
      if (onControl) _fresh.update { it + (post.id to outcome) }
      when (outcome) {
        is SaveOutcome.Saved -> if (!onControl) messages.show(savedMessage(outcome.file))
        is SaveOutcome.Failed -> messages.showError(if (outcome.noFile) "Couldn't save: this post has no file" else "Couldn't save the file")
      }
    }
  }
}

fun postPageUrl(post: PostDto): String = "https://www.sankakucomplex.com/posts/${post.id}"
