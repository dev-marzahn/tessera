package dev.marzahn.sankaku.api.dto

enum class TagType(val id: Int) {
  General(0),
  Artist(1),
  Studio(2),
  Copyright(3),
  Character(4),
  Genre(5),
  Medium(8),
  Meta(9),
  Fashion(10),
  Anatomy(11),
  Pose(12),
  Activity(13),
  Role(14),
  Flora(15),
  Fauna(16),
  Entity(17),
  Object(18),
  Substance(19),
  Setting(20),
  Language(21),
  Automatic(22),
  Other(23),
  ;

  companion object {
    private val byId = entries.associateBy { it.id }

    fun fromId(id: Int): TagType = byId[id] ?: Other
  }
}
