package dev.marzahn.sankaku.features.post

import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerPreferencesTest {
  @Test
  fun aFreshInstallPlaysUnmutedAtFullVolumeWithStartMutedOff() {
    val preferences = PlayerPreferences(InMemoryTextFile())

    assertFalse(preferences.muted)
    assertEquals(1f, preferences.playedVolume)
    assertFalse(preferences.startMuted)
  }

  @Test
  fun startMutedMutesEachRunWhateverTheLastRunLeft() {
    val file = InMemoryTextFile()
    PlayerPreferences(file).apply {
      changeStartMuted(true)
      toggleMute()
      toggleMute()
      changeVolume(0.6f)
      assertFalse(muted)
    }

    val next = PlayerPreferences(file)
    assertTrue(next.startMuted)
    assertTrue(next.muted)
    assertEquals(0f, next.playedVolume)
    next.toggleMute()
    assertEquals(0.6f, next.playedVolume)
  }

  @Test
  fun withStartMutedOffARunKeepsTheLastRunsMute() {
    val file = InMemoryTextFile()
    PlayerPreferences(file).toggleMute()
    assertTrue(PlayerPreferences(file).muted)

    PlayerPreferences(file).toggleMute()
    assertFalse(PlayerPreferences(file).muted)
  }

  @Test
  fun aStartsMuteIsntSavedAsTheUsersOwn() {
    val file = InMemoryTextFile()
    PlayerPreferences(file).changeStartMuted(true)
    PlayerPreferences(file).apply {
      assertTrue(muted)
      changeStartMuted(false)
    }

    assertFalse(PlayerPreferences(file).muted, "the next run plays as the user left it: unmuted")
  }

  @Test
  fun aMuteTheUserChoseDuringAStartMutedRunIsKept() {
    val file = InMemoryTextFile()
    PlayerPreferences(file).changeStartMuted(true)
    PlayerPreferences(file).apply {
      toggleMute()
      toggleMute()
      changeStartMuted(false)
    }

    assertTrue(PlayerPreferences(file).muted)
  }

  @Test
  fun turningStartMutedOnDoesntMuteTheVideoPlaying() {
    val preferences = PlayerPreferences(InMemoryTextFile())
    preferences.changeStartMuted(true)

    assertFalse(preferences.muted)
  }
}
