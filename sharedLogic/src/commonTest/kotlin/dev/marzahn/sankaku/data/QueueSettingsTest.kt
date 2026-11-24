package dev.marzahn.sankaku.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueSettingsTest {
  @Test
  fun forYouIsOutOfTheQueueByDefault() {
    assertFalse(QueueSettings(InMemoryTextFile()).forYouInQueue.value)
  }

  @Test
  fun forYouInTheQueueSurvivesARestart() {
    val file = InMemoryTextFile()
    QueueSettings(file).setForYouInQueue(true)

    assertTrue(QueueSettings(file).forYouInQueue.value)
  }

  @Test
  fun theSwitcherOpensOnItsCardsByDefault() {
    assertFalse(QueueSettings(InMemoryTextFile()).switcherList.value)
  }

  @Test
  fun theSwitcherModeSurvivesARestart() {
    val file = InMemoryTextFile()
    QueueSettings(file).setSwitcherList(true)

    assertTrue(QueueSettings(file).switcherList.value)
  }

  @Test
  fun eachSettingKeepsTheOther() {
    val file = InMemoryTextFile()
    QueueSettings(file).setForYouInQueue(true)
    QueueSettings(file).setSwitcherList(true)
    QueueSettings(file).setForYouInQueue(false)

    val settings = QueueSettings(file)
    assertFalse(settings.forYouInQueue.value)
    assertTrue(settings.switcherList.value)
  }

  @Test
  fun anUnreadableFileIsTheDefaults() {
    val file = InMemoryTextFile().apply { write("not json") }

    assertFalse(QueueSettings(file).forYouInQueue.value)
  }
}
