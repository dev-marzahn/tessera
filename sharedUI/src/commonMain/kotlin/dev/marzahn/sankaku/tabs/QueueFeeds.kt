package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.data.QueueSettings
import dev.marzahn.sankaku.queue.FeedItems
import org.koin.compose.koinInject

internal fun pinnedFeeds(session: AuthSession?, settings: QueueSettings?): List<Feed> =
  FeedItems.pinned(session?.isSignedIn?.value == true, settings?.forYouInQueue?.value == true)

@Composable
internal fun QueueFeeds(
  tabs: TabsController = koinInject(),
  session: AuthSession = koinInject(),
  settings: QueueSettings = koinInject(),
) {
  val signedIn by session.isSignedIn.collectAsStateWithLifecycle()
  val forYou by settings.forYouInQueue.collectAsStateWithLifecycle()
  LaunchedEffect(signedIn, forYou) { tabs.pinFeeds(FeedItems.pinned(signedIn, forYou)) }
}
