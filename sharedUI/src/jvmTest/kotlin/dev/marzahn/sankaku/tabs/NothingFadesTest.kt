package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class NothingFadesTest {
  private val start = Instant.parse("2026-11-01T12:00:00Z")
  private var now = start
  private val clock = object : Clock {
    override fun now() = now
  }
  private val file = InMemoryTextFile()

  private fun grid(vararg tags: String) = Route.Grid(tags.toList())

  private fun post(id: String) = Route.Post(id, "md5$id", listOf("a"), label = "Post $id")

  private fun launch() = TabsController(FileTabStateStore(file), clock = clock)

  private fun saveUntouched() {
    val first = launch()
    first.open(grid("a"), background = false)
    first.openInBlock(listOf(post("p1")))
    first.open(grid("b"), background = true)
    first.activate(first.tabs.value.first().id)
    FileTabStateStore(file).save(first.saved())
  }

  @Test
  fun aLaunchAfterFourDaysFadesNoUntouchedSourceAndKeepsItsKeptPosts() {
    saveUntouched()

    now = start + 4.days
    val second = launch()

    assertEquals(listOf("Browse", "A", "Post p1", "B"), second.tabs.value.map { it.title })
    assertEquals(second.tabs.value[1].id, second.tabs.value[2].block)
    assertTrue(second.saved().faded.isEmpty())
  }

  @Test
  fun nothingIsDeletedHoweverLongTheAppWasClosed() {
    saveUntouched()

    now = start + 400.days
    val second = launch()

    assertEquals(listOf("Browse", "A", "Post p1", "B"), second.tabs.value.map { it.title })
  }

  private fun saved(id: String, vararg routes: Route, block: String? = null, keptAt: Long? = null, place: GridPlace? = null) =
    SavedTab(id, routes.toList(), block = block, place = place, keptAt = keptAt)

  @Test
  fun aSavedFadedListComesBackAtTheEndTheOneThatFadedFirstFirstEachWithItsKeptPostsAsABlock() {
    val browse = saved("browse", grid())
    val open = saved("open", grid("open"))
    val late = SavedFaded(
      at = (start - 1.days).epochSeconds,
      tabs = listOf(saved("late", grid("late")), saved("late-kept", post("p2"), block = "late", keptAt = start.epochSeconds)),
    )
    val early = SavedFaded(
      at = (start - 10.days).epochSeconds,
      tabs = listOf(
        saved("early", grid("early"), place = GridPlace("p9", time = null)),
        saved("early-kept-1", post("p3"), block = "early", keptAt = start.epochSeconds),
        saved("early-kept-2", post("p4"), block = "early", keptAt = start.epochSeconds),
      ),
    )
    FileTabStateStore(file).save(SavedTabs(activeIndex = 0, tabs = listOf(browse, open), faded = listOf(late, early)))

    val tabs = launch()

    val all = tabs.tabs.value
    assertEquals(listOf("Browse", "Open", "Early", "Post p3", "Post p4", "Late", "Post p2"), all.map { it.title })
    val earlySource = all[2]
    assertEquals(GridPlace("p9", time = null), earlySource.place)
    assertEquals(listOf(earlySource.id, earlySource.id), all.subList(3, 5).map { it.block })
    assertEquals(all[5].id, all[6].block)
    assertNull(all[5].block)
    assertEquals("Browse", tabs.activeTab.title)
    assertTrue(tabs.saved().faded.isEmpty())
    assertEquals(7, tabs.saved().tabs.size)
  }

  @Test
  fun aFadedSourceWhoseSearchIsOpenAgainBringsOnlyItsKeptPostsIntoThatSourcesBlock() {
    val browse = saved("browse", grid())
    val open = saved("open", grid("a"))
    val other = saved("other", grid("z"))
    val faded = SavedFaded(
      at = start.epochSeconds,
      tabs = listOf(saved("old-a", grid("a")), saved("old-kept", post("p5"), block = "old-a", keptAt = start.epochSeconds)),
    )
    FileTabStateStore(file).save(SavedTabs(activeIndex = 0, tabs = listOf(browse, open, other), faded = listOf(faded)))

    val tabs = launch()

    assertEquals(listOf("Browse", "A", "Post p5", "Z"), tabs.tabs.value.map { it.title })
    assertEquals(tabs.tabs.value[1].id, tabs.tabs.value[2].block)
  }
}
