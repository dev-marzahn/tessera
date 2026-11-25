package dev.marzahn.sankaku.tabs

class RecordingTabStateStore(var saved: SavedTabs? = null) : TabStateStore {
  val saves = mutableListOf<SavedTabs>()

  override fun load() = saved

  override fun save(tabs: SavedTabs) {
    saved = tabs
    saves += tabs
  }

  override fun clear() {
    saved = null
  }
}
