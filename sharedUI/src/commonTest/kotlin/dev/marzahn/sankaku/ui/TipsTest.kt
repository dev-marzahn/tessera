package dev.marzahn.sankaku.ui

import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TipsTest {
  @Test
  fun aTipShowsOnceAndNotAfterARestart() {
    val file = InMemoryTextFile()
    val tips = Tips(file, touch = true)
    assertTrue(tips.offer(Tip.GridGestures))
    assertEquals(Tip.GridGestures, tips.current)
    tips.appeared(Tip.GridGestures)
    tips.dismissed(Tip.GridGestures)
    assertNull(tips.current)
    assertFalse(tips.offer(Tip.GridGestures))

    val restarted = Tips(file, touch = true)
    assertFalse(restarted.offer(Tip.GridGestures))
    assertTrue(restarted.offer(Tip.TabList))
  }

  @Test
  fun theReopenTipShowsOnceAndNotAfterARestart() {
    val file = InMemoryTextFile()
    val tips = Tips(file, touch = true)
    assertTrue(tips.offer(Tip.ReopenHold))
    tips.appeared(Tip.ReopenHold)
    tips.dismissed(Tip.ReopenHold)
    assertNull(tips.current)
    assertFalse(tips.offer(Tip.ReopenHold))
    assertFalse(Tips(file, touch = true).offer(Tip.ReopenHold))
  }

  @Test
  fun aTipCutShortWhileOnScreenCountsAsSeen() {
    val file = InMemoryTextFile()
    val tips = Tips(file, touch = true)
    tips.offer(Tip.TabList)
    tips.appeared(Tip.TabList)
    assertFalse(Tips(file, touch = true).pending(Tip.TabList))
  }

  @Test
  fun aTipWhoseAnchorWentBeforeItShowedComesLaterAndFreesItsPlace() {
    val file = InMemoryTextFile()
    val tips = Tips(file, touch = true)
    assertTrue(tips.offer(Tip.GridGestures))
    tips.dismissed(Tip.GridGestures)
    assertNull(tips.current)
    assertTrue(tips.pending(Tip.GridGestures))
    assertTrue(Tips(file, touch = true).pending(Tip.GridGestures))
    assertTrue(tips.offer(Tip.TabList))
  }

  @Test
  fun aTipWhoseActionWasUsedNeverShows() {
    val file = InMemoryTextFile()
    val tips = Tips(file, touch = true)
    tips.used(Tip.GridGestures)
    tips.used(Tip.TabList)
    assertFalse(tips.offer(Tip.GridGestures))
    assertFalse(tips.offer(Tip.TabList))
    assertNull(tips.current)
    assertFalse(Tips(file, touch = true).pending(Tip.GridGestures))
  }

  @Test
  fun usingOneTipsActionLeavesTheOther() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    tips.used(Tip.GridGestures)
    assertTrue(tips.offer(Tip.TabList))
  }

  @Test
  fun oneTipAtATime() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    assertTrue(tips.offer(Tip.GridGestures))
    assertFalse(tips.offer(Tip.TabList))
    assertTrue(tips.pending(Tip.TabList))
    tips.dismissed(Tip.TabList)
    assertEquals(Tip.GridGestures, tips.current)
    tips.dismissed(Tip.GridGestures)
    assertTrue(tips.offer(Tip.TabList))
  }

  @Test
  fun aTipThatLeftTheScreenHoldsTheNextUntilTheNextGestureEnds() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    assertTrue(tips.offer(Tip.QueueSwipe))
    tips.appeared(Tip.QueueSwipe)
    tips.dismissed(Tip.QueueSwipe)
    assertTrue(tips.resting)
    tips.gestureEnded()
    assertFalse(tips.resting)
  }

  @Test
  fun aTipThatNeverShowedHoldsNothingUp() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    assertTrue(tips.offer(Tip.QueueSwipe))
    tips.dismissed(Tip.QueueSwipe)
    assertFalse(tips.resting)
  }

  @Test
  fun aTipOfferedOnAnActionDoesntWaitForTheRest() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    tips.offer(Tip.TabList)
    tips.appeared(Tip.TabList)
    tips.dismissed(Tip.TabList)
    tips.visiting("next")
    assertTrue(tips.offer(Tip.GridGestures))
  }

  @Test
  fun aScreenVisitShowsOneTipAtMost() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    tips.visiting("grid")
    assertTrue(tips.offer(Tip.OpenedInFront))
    assertTrue(tips.appeared(Tip.OpenedInFront))
    tips.dismissed(Tip.OpenedInFront)
    tips.gestureEnded()
    assertFalse(tips.offer(Tip.QueueSwipe), "not on the same visit")
    tips.visiting("grid")
    assertFalse(tips.offer(Tip.QueueSwipe))
    tips.visiting("next")
    assertTrue(tips.offer(Tip.QueueSwipe))
  }

  @Test
  fun aTipStillOnScreenGoesWithItsVisit() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    tips.visiting("grid")
    tips.offer(Tip.QueueSwipe)
    tips.appeared(Tip.QueueSwipe)
    tips.visiting("opener")
    assertNull(tips.bubble)
    assertNull(tips.current)
    assertFalse(tips.visitSpent)
    assertFalse(tips.resting)
  }

  @Test
  fun aTipOfferedBeforeAnotherCameInTheVisitDoesntAppear() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    tips.visiting("grid")
    assertTrue(tips.offer(Tip.QueueSwipe))
    assertTrue(tips.tell(Tip.WayBack))
    assertFalse(tips.appeared(Tip.QueueSwipe))
    assertNull(tips.current)
    assertTrue(tips.pending(Tip.QueueSwipe), "it can come on a later visit")
  }

  @Test
  fun aTipSaidOnTheTopLineIsTheVisitsTip() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    tips.visiting("grid")
    assertTrue(tips.tell(Tip.WayBack))
    assertFalse(tips.pending(Tip.WayBack))
    assertFalse(tips.offer(Tip.KeptWaits))
    tips.visiting("next")
    assertFalse(tips.tell(Tip.WayBack), "once")
    assertTrue(tips.offer(Tip.KeptWaits))
  }

  @Test
  fun aGestureThatBeganOverATipTakesItAwayAsItEnds() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    tips.offer(Tip.GridGestures)
    tips.gestureStarted()
    tips.appeared(Tip.GridGestures)
    tips.gestureEnded()
    assertEquals(Tip.GridGestures, tips.bubble?.tip)
    tips.gestureStarted()
    tips.gestureEnded()
    assertNull(tips.bubble)
    assertNull(tips.current)
    assertFalse(tips.resting, "the gesture that ended it went on to what's under it")
  }

  @Test
  fun aSecondTabAppearsOnlyOnARiseFromOne() {
    val tips = Tips(InMemoryTextFile(), touch = true)
    tips.counted(2)
    assertFalse(tips.secondTabAppeared)
    tips.counted(1)
    assertFalse(tips.secondTabAppeared)
    tips.counted(2)
    assertTrue(tips.secondTabAppeared)
  }

  @Test
  fun eachPlatformShowsOnlyItsOwnTips() {
    val desktop = Tips(InMemoryTextFile(), touch = false)
    assertFalse(desktop.offer(Tip.GridGestures))
    assertFalse(desktop.offer(Tip.TabList))
    assertFalse(desktop.offer(Tip.ReopenHold))
    assertTrue(desktop.offer(Tip.KeepLoop))

    val phone = Tips(InMemoryTextFile(), touch = true)
    assertFalse(phone.offer(Tip.KeepLoop))
    assertTrue(phone.offer(Tip.GridGestures))
  }

  @Test
  fun keepingBeforeTheKeepLoopTipCameMeansItNeverShows() {
    val file = InMemoryTextFile()
    Tips(file, touch = false).used(Tip.KeepLoop)
    assertFalse(Tips(file, touch = false).offer(Tip.KeepLoop))
  }

  @Test
  fun theLongPressTipComesOnceAfterTheFirstHeartThatFavourites() {
    val file = InMemoryTextFile()
    val tips = Tips(file, touch = true)
    assertFalse(tips.offerLongPressTip(wasFavourited = true, ran = true))
    assertFalse(tips.offerLongPressTip(wasFavourited = false, ran = false))
    assertNull(tips.current)

    assertTrue(tips.offerLongPressTip(wasFavourited = false, ran = true))
    assertNull(tips.current)

    tips.visiting("next")
    assertFalse(tips.offerLongPressTip(wasFavourited = false, ran = true))
    assertFalse(Tips(file, touch = true).offerLongPressTip(wasFavourited = false, ran = true))
  }

  @Test
  fun theLongPressTipNeverComesOnceTheUserHasLongPressed() {
    val file = InMemoryTextFile()
    Tips(file, touch = true).used(Tip.LongPressFavourite)
    val tips = Tips(file, touch = true)
    assertFalse(tips.offerLongPressTip(wasFavourited = false, ran = true))
    assertNull(tips.current)
  }

  @Test
  fun theLongPressTipIsThePhonesOnly() {
    assertFalse(Tips(InMemoryTextFile(), touch = false).offerLongPressTip(wasFavourited = false, ran = true))
  }

  @Test
  fun anUnreadableFileStartsFresh() {
    assertTrue(Tips(InMemoryTextFile("not json"), touch = true).offer(Tip.GridGestures))
  }
}
