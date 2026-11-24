package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.features.post.FollowOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FollowInsteadTest {
  private val tabs = TabsController()
  private val undo = ClosedTabUndo(tabs)
  private val deck = SortDeck(tabs, undo)

  private var account: String? = "a1"
  private var followSucceeds = true

  private var followedMeanwhile = false
  private val server = mapOf(
    "artist_y" to TagDto(name = "artist_y", tagName = "artist_y", type = TagType.Artist.id, id = "t1"),
    "artist_p" to TagDto(name = "artist_p", tagName = "artist_p", type = TagType.Artist.id, id = "t4"),
    "artist_f" to TagDto(name = "artist_f", tagName = "artist_f", type = TagType.Artist.id, id = "t2", isFollowing = true),
    "scenery" to TagDto(name = "scenery", tagName = "scenery", type = TagType.General.id, id = "t3"),
  )
  private val known = mutableMapOf<String, TagType>()
  private val lookedUp = mutableListOf<String>()
  private val followed = mutableSetOf<String>()
  private val unfollowed = mutableListOf<String>()

  private val follow = FollowInstead(
    tabs,
    CoroutineScope(Dispatchers.Unconfined),
    account = { account },
    lookUp = { key -> lookedUp += key; server[key] },
    isFollowing = { it.id in followed || it.isFollowing },
    follow = { tag ->
      when {
        followedMeanwhile -> FollowOutcome.AlreadyFollowed.also { followed += tag.id!! }
        followSucceeds -> FollowOutcome.Followed.also { followed += tag.id!! }
        else -> FollowOutcome.Refused
      }
    },
    unfollow = { tag -> followed -= tag.id!!; unfollowed += tag.name },
    knownType = { known[it] },
  )

  private fun source(vararg tags: String): TabState {
    val route = Route.Grid(tags.toList())
    tabs.open(route, background = true)
    return tabs.tabs.value.single { it.top == route }
  }

  private fun loaded(tab: TabState): TabState = tab.also { runBlocking { follow.load(it) } }

  private fun isOpen(tab: TabState) = tabs.tabs.value.any { it.id == tab.id }

  @Test
  fun offeredForAnUnfollowedArtistThatIsTheSearchsOneTag() {
    assertNotNull(follow.offer(loaded(source("artist_y"))))
    assertNotNull(follow.offer(loaded(source("artist_p", "order:popularity"))))
  }

  @Test
  fun notOfferedForAFollowedTagANonArtistAMultiTagSearchOrSignedOut() {
    assertNull(follow.offer(loaded(source("artist_f"))))
    assertNull(follow.offer(loaded(source("scenery"))))
    val twoTags = loaded(source("artist_y", "scenery"))
    assertNull(follow.offer(twoTags))
    assertNull(follow.offer(loaded(source("artist_z", "rating:g"))))
    assertEquals(listOf("artist_f", "scenery"), lookedUp)

    val artist = loaded(source("artist_y"))
    assertNotNull(follow.offer(artist))
    account = null
    assertNull(follow.offer(artist))
  }

  @Test
  fun aTagLookedUpForAnotherAccountIsAskedAgain() {
    val artist = loaded(source("artist_y"))
    account = "a2"

    assertNull(follow.offer(artist))
    loaded(artist)
    assertNotNull(follow.offer(artist))
    assertEquals(listOf("artist_y", "artist_y"), lookedUp)
  }

  @Test
  fun aTagTheAppKnowsIsNoArtistIsNotLookedUp() {
    known["scenery"] = TagType.General
    assertNull(follow.offer(loaded(source("scenery"))))
    assertTrue(lookedUp.isEmpty())
  }

  @Test
  fun signedOutNothingIsLookedUp() {
    account = null
    loaded(source("artist_y"))
    assertTrue(lookedUp.isEmpty())
  }

  @Test
  fun aTagFollowedSinceIsNoLongerOffered() {
    val artist = loaded(source("artist_y"))
    followed += "t1"
    assertNull(follow.offer(artist))
  }

  @Test
  fun followAndCloseFollowsThenClosesAndOneUndoReversesBoth() {
    val artist = loaded(source("artist_y"))

    follow.followAndClose(artist) { unfollow -> undo.close(artist.id, unfollow) }

    assertTrue("t1" in followed)
    assertTrue(!isOpen(artist))
    undo.undo()
    assertEquals(listOf("artist_y"), unfollowed)
    assertTrue(tabs.tabs.value.any { it.top == Route.Grid(listOf("artist_y")) })
    assertTrue("t1" !in followed)
  }

  @Test
  fun undoLeavesAFollowThisDidNotMake() {
    val artist = loaded(source("artist_y"))
    followedMeanwhile = true

    follow.followAndClose(artist) { alsoUndo -> undo.close(artist.id, alsoUndo) }

    assertTrue(!isOpen(artist))
    undo.undo()
    assertTrue(tabs.tabs.value.any { it.top == Route.Grid(listOf("artist_y")) })
    assertTrue(unfollowed.isEmpty())
    assertTrue("t1" in followed)
  }

  @Test
  fun aRefusedFollowLeavesTheSourceOpen() {
    val artist = loaded(source("artist_y"))
    followSucceeds = false

    follow.followAndClose(artist) { unfollow -> undo.close(artist.id, unfollow) }

    assertTrue(isOpen(artist))
    assertNull(undo.pending.value)
    assertNotNull(follow.offer(artist))
  }

  @Test
  fun onASortCardItIsFollowedInsteadAndTheDecksUndoReversesBoth() {
    val artist = loaded(source("artist_y"))
    val next = source("scenery")
    deck.start()

    follow.followAndClose(artist) { unfollow -> deck.followedInstead(artist.id, unfollow) }

    assertTrue(!isOpen(artist))
    assertEquals(next, deck.card)
    assertEquals(SortDeck.Tally(followed = 1), deck.tally)
    assertTrue(deck.canUndoDone)

    deck.undoDone()

    assertEquals(listOf("artist_y"), unfollowed)
    assertEquals(Route.Grid(listOf("artist_y")), deck.card?.top)
    assertEquals(SortDeck.Tally(undone = listOf(SortDeck.Undone(SortDeck.Answer.Kind.Followed, artist.title))), deck.tally)
  }

  @Test
  fun followAndCloseOnTheSourceGoneIntoShowsTheNextCard() {
    val artist = loaded(source("artist_y"))
    val next = source("scenery")
    deck.start()
    deck.goIn()

    follow.followAndClose(artist) { unfollow -> deck.followedInstead(artist.id, unfollow) }

    assertTrue(deck.shown)
    assertNull(deck.goneIn)
    assertEquals(next, deck.card)
    assertEquals(SortDeck.Tally(followed = 1, followedWentIn = 1), deck.tally)
  }

  @Test
  fun aRefusedFollowOnASortCardLeavesTheCardUnanswered() {
    val artist = loaded(source("artist_y"))
    source("scenery")
    deck.start()
    followSucceeds = false

    follow.followAndClose(artist) { unfollow -> deck.followedInstead(artist.id, unfollow) }

    assertEquals(artist, deck.card)
    assertEquals(SortDeck.Tally(), deck.tally)
  }
}
