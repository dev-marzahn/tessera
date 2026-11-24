package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.Viewing
import dev.marzahn.sankaku.tabs.TabsController
import org.koin.compose.koinInject

fun actedOn(seen: SeenStore, tabs: TabsController): (PostDto) -> Unit = { post ->
  seen.record(post.id, post.md5)
  tabs.touchActive()
}

fun Viewing.keepingFrom(keep: (PostDto) -> Unit): (PostDto) -> Unit = { post ->
  keeping(post.id)
  keep(post)
}

@Composable
fun rememberViewing(current: PostDto?, seen: SeenStore = koinInject()): Viewing {
  val viewing = remember(seen) { Viewing(seen) }
  SideEffect { viewing.current(current?.id, current?.md5) }
  DisposableEffect(viewing) { onDispose { viewing.leave() } }
  return viewing
}
