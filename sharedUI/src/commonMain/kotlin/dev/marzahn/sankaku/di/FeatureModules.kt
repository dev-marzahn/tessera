package dev.marzahn.sankaku.di

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.PLAYER_FILE
import dev.marzahn.sankaku.REOPEN_HINT_FILE
import dev.marzahn.sankaku.TIPS_FILE
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.data.OriginalFiles
import dev.marzahn.sankaku.data.BookFavourites
import dev.marzahn.sankaku.data.BooksRepository
import dev.marzahn.sankaku.features.books.BookActions
import dev.marzahn.sankaku.data.ReadingProgress
import dev.marzahn.sankaku.features.books.BookListScreen
import dev.marzahn.sankaku.features.books.BookListViewModel
import dev.marzahn.sankaku.features.books.BookOpener
import dev.marzahn.sankaku.features.books.BookSeriesScreen
import dev.marzahn.sankaku.features.books.BookSeriesViewModel
import dev.marzahn.sankaku.features.books.ContinueReadingViewModel
import dev.marzahn.sankaku.features.books.BookReader
import dev.marzahn.sankaku.features.books.BookReaderViewModel
import dev.marzahn.sankaku.features.books.KeptBookScreen
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.features.auth.SignInViewModel
import dev.marzahn.sankaku.features.settings.CacheLimits
import dev.marzahn.sankaku.features.settings.ClearCache
import dev.marzahn.sankaku.features.settings.ClearEverything
import dev.marzahn.sankaku.features.settings.ClearSavedTabs
import dev.marzahn.sankaku.features.post.actedOn
import dev.marzahn.sankaku.features.settings.SettingsController
import dev.marzahn.sankaku.shell.ShortcutSheetController
import dev.marzahn.sankaku.features.grid.GridCursorRequest
import dev.marzahn.sankaku.features.grid.GridScreen
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.features.grid.GridViewModel
import dev.marzahn.sankaku.features.grid.PreviewHashes
import dev.marzahn.sankaku.features.grid.ViewerPreferences
import dev.marzahn.sankaku.features.post.PlayerPreferences
import dev.marzahn.sankaku.features.post.PostActions
import dev.marzahn.sankaku.data.FollowedTagList
import dev.marzahn.sankaku.data.OutboxSender
import dev.marzahn.sankaku.data.TagsRepository
import dev.marzahn.sankaku.features.post.PostSaver
import dev.marzahn.sankaku.features.post.PostScreen
import dev.marzahn.sankaku.features.post.PostViewModel
import dev.marzahn.sankaku.features.post.TagBlacklisting
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.tabs.NewTab
import dev.marzahn.sankaku.tabs.ClearViewed
import dev.marzahn.sankaku.tabs.ClearedUndo
import dev.marzahn.sankaku.tabs.ClearedUndoOnTopLine
import dev.marzahn.sankaku.tabs.ClosedTabUndo
import dev.marzahn.sankaku.tabs.Closing
import dev.marzahn.sankaku.tabs.SortDeck
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.tabs.BlockFinds
import dev.marzahn.sankaku.tabs.BlockOffer
import dev.marzahn.sankaku.tabs.FollowInstead
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.TagType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.tabs.CachedTabPosts
import dev.marzahn.sankaku.tabs.migrateReopenHint
import dev.marzahn.sankaku.tabs.TabPosts
import dev.marzahn.sankaku.tabs.QueueStart
import dev.marzahn.sankaku.tabs.UpNextState
import dev.marzahn.sankaku.tabs.pinnedFeeds
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.UserGestures
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

@OptIn(KoinExperimentalAPI::class)
val shellModule = module {
  single(createdAtStart = true) {
    TabsController(get(), get(), knownTags = getOrNull<TagsRepository>()?.known, today = getOrNull())
      .also { it.pinFeeds(pinnedFeeds(getOrNull(), getOrNull())) }
  }
  single<Navigator> { TabsNavigator(get(), get()) }
  single { NewTab(get()) }
  single { Messages() }
  single { Tips(get(TIPS_FILE), touch = platformIsTouch).also { migrateReopenHint(get(REOPEN_HINT_FILE), it) } }
  single { UserGestures() }
  single { ClosedTabUndo(get(), get(), get(), get()) }
  single { BlockOffer() }
  single {
    val privacy = get<PrivacySettings>()
    val today = getOrNull<TodayStore>()
    SortDeck(get(), get(), gOnly = { privacy.gOnly.value }, liked = { today?.likedPosts().orEmpty() })
  }
  single {
    val privacy = get<PrivacySettings>()
    val scope = this
    BlockFinds(get(), get(), gOnly = { privacy.gOnly.value }, quiet = { !platformIsTouch || scope.get<QueueStart>().pageOpen })
  }
  single {
    val actions = get<PostActions>()
    val session = get<AuthSession>()
    val tags = get<TagsRepository>()
    FollowInstead(
      get(),
      get<CoroutineScope>() + Dispatchers.Main,
      account = { (session.state.value as? AuthState.SignedIn)?.user?.id },
      lookUp = { key ->
        try {
          tags.page(key).tag
        } catch (_: ApiError) {
          null
        }
      },
      isFollowing = { actions.follows.isFollowing(it) },
      follow = actions::follow,
      unfollow = actions::unfollow,
      knownType = { key -> tags.known.value[key]?.let { TagType.fromId(it.type) } },
    )
  }
  single<ClearedUndo> {
    val undo = get<ClosedTabUndo>()
    if (platformIsTouch) ClearedUndo { undo.opened(it, Closing.Cleared) } else ClearedUndoOnTopLine(undo, get())
  }
  single { ClearViewed(get(), get(), get()) }
  single<TabPosts> { CachedTabPosts(get(), get()) }
  single { UpNextState(get(), get()) }
  single { QueueStart(get(), get(), get(), get()) }
  single { SettingsController() }
  single { ShortcutSheetController() }
  single { ClearEverything(get(), get(), get(), get(), get(), get(), get(), get(), get(), today = getOrNull()) }
  single { ClearCache(get()) }
  single { CacheLimits(get(), get()) }
  single { ClearSavedTabs(get(), get()) }
}

val authModule = module {
  single { SignInController() }
  viewModel { SignInViewModel(get(), get()) }
}

@OptIn(KoinExperimentalAPI::class)
val gridModule = module {
  single { ViewerPreferences() }
  single { GridCursorRequest() }
  single { PreviewHashes(get(), get(), get()) }
  viewModel { p ->
    val followed = get<FollowedTagList>()
    GridViewModel(p[0], p.values.getOrNull(1) as? GridPlace, get(), get(), get(), get(), get(), get(), get(), get(), get(), followedTagIds = followed::idsOrNull)
  }
  navigation<Route.Grid> { route -> GridScreen(route) }
}

@OptIn(KoinExperimentalAPI::class)
val postModule = module {
  single { FollowedTagList(get()) }
  single { TagBlacklisting(get(), get(), get()) }
  single { PlayerPreferences(get(PLAYER_FILE)) }
  single {
    val tabs = get<TabsController>()
    PostActions(get(), get(), get(), get(), get(), get(), actedOn(get(), tabs), get<OutboxSender>().refusals, get(), tabs::awaitNextAction)
  }
  single { OriginalFiles(get(MAIN_CLIENT), get(), get(), get()) }
  single { PostSaver(get(), get(), get(), get()) }
  viewModel { p -> PostViewModel(p.get(), get(), get(), get()) }
  navigation<Route.Post>(metadata = NavDisplay.popTransitionSpec { EnterTransition.None togetherWith ExitTransition.None }) { route ->
    PostScreen(route)
  }
}

@OptIn(KoinExperimentalAPI::class)
val booksModule = module {
  single { BooksRepository(get(), get(), get(), signedIn = get<AuthSession>().isSignedIn, favourites = get()) }
  single { BookFavourites(get(), get(), get()) }
  single { BookActions(get(), get(), get(), get(), get()) }
  single { BookReader(get<ReadingProgress>()::save) }
  single<BookOpener> { BookOpener { book -> get<BookReader>().open(book.id, book.title) } }
  viewModel { p -> BookReaderViewModel(p.get(), get(), get()) }
  viewModel { p -> BookListViewModel(p.get(), get(), get(), get()) }
  viewModel { p -> BookSeriesViewModel(p.get(), get(), get(), get()) }
  viewModel { ContinueReadingViewModel(get(), get(), get(), get()) }
  navigation<Route.Books> { route -> BookListScreen(route) }
  navigation<Route.BookSeries> { route -> BookSeriesScreen(route) }
  navigation<Route.Book> { route -> KeptBookScreen(route) }
}
