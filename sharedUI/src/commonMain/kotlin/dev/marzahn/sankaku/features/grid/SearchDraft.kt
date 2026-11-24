package dev.marzahn.sankaku.features.grid

import dev.marzahn.sankaku.data.SearchTags
import dev.marzahn.sankaku.api.Feed as ApiFeed

enum class Rating(val code: String, val badge: String, val label: String) {
  G("s", "G", "General"),
  R15("q", "R15", "Questionable"),
  R18("e", "R18", "Explicit"),
  ;

  val tag: String get() = "rating:$code"

  companion object {
    private val all = entries.toSet()

    fun ofCode(code: String?): Rating? = code?.let { of("rating:$it")?.singleOrNull() }

    fun of(tag: String): Set<Rating>? {
      val excluded = tag.startsWith("-")
      val body = tag.removePrefix("-").lowercase()
      if (!body.startsWith("rating:")) return null
      val rating = when (body.removePrefix("rating:")) {
        "s", "safe" -> G
        "q", "questionable" -> R15
        "e", "explicit" -> R18
        else -> return null
      }
      return if (excluded) all - rating else setOf(rating)
    }

    fun tag(ratings: Set<Rating>): String? = when (ratings.size) {
      1 -> ratings.single().tag
      2 -> "-" + (all - ratings).single().tag
      else -> null
    }

    fun inTags(tags: List<String>): Set<Rating> {
      val allowed = tags.mapNotNull(::of).fold(all) { left, next -> left intersect next }
      return if (allowed == all) emptySet() else entries.filter { it in allowed }.toSet()
    }

    fun isRatingTag(tag: String): Boolean = tag.removePrefix("-").startsWith("rating:", ignoreCase = true)
  }
}

data class FilterOption(val tag: String, val label: String) {
  fun matches(word: String): Boolean = word.equals(tag, ignoreCase = true)
}

enum class Filter(val prefix: String, val label: String, val anyLabel: String, val options: List<FilterOption>) {
  Feed("feed:", "Feed", "All posts", ApiFeed.entries.map { FilterOption(it.tag, it.label) }),
  Order(
    "order:", "Order", "Newest",
    listOf(
      FilterOption("order:popularity", "Popularity"),
      FilterOption("order:quality", "Quality"),
      FilterOption("order:score", "Score"),
      FilterOption("order:favcount", "Favourites"),
      FilterOption("order:random", "Random"),
    ),
  ),
  Threshold(
    "threshold:", "Minimum score", "Any score",
    (1..5).map { FilterOption("threshold:$it", "Score $it+") },
  ),
  ;

  fun optionFor(word: String): FilterOption? = options.firstOrNull { it.matches(word) }

  companion object {
    fun optionFor(word: String): Pair<Filter, FilterOption>? =
      entries.firstNotNullOfOrNull { filter -> filter.optionFor(word)?.let { filter to it } }
  }
}

enum class Favourites(val label: String) {
  Only("Only my favourites"),
  Hide("Hide my favourites"),
  ;

  fun tag(name: String): String = (if (this == Hide) "-" else "") + "fav:$name"

  companion object {
    const val LABEL = "Favourites"
    const val ANY_LABEL = "All posts"

    fun of(tag: String, name: String?): Favourites? = when {
      name == null -> null
      tag.equals(Only.tag(name), ignoreCase = true) -> Only
      tag.equals(Hide.tag(name), ignoreCase = true) -> Hide
      else -> null
    }

    fun shortLabel(tag: String): String? = when {
      tag.startsWith("fav:", ignoreCase = true) -> LABEL
      tag.startsWith("-fav:", ignoreCase = true) -> "Not favourites"
      else -> null
    }
  }
}

data class SearchDraft(
  val chips: List<String> = emptyList(),
  val ratings: Set<Rating> = emptySet(),
  val filters: Map<Filter, FilterOption> = emptyMap(),
  val favourites: Favourites? = null,
  val me: String? = null,
) {
  val tags: List<String>
    get() = SearchTags.filtersFirst(
      listOfNotNull(me?.let { favourites?.tag(it) }, Rating.tag(ratings)) + Filter.entries.mapNotNull { filters[it]?.tag } + chips,
    )

  fun add(text: String): SearchDraft = text.split(WHITESPACE).filter(String::isNotEmpty).fold(this) { draft, word ->
    val ratings = Rating.of(word)
    val choice = Filter.optionFor(word)
    val favourites = Favourites.of(word, me)
    when {
      ratings != null -> draft.withRatings(ratings)
      choice != null -> draft.with(choice.first, choice.second)
      favourites != null -> draft.withFavourites(favourites)
      word in draft.chips -> draft
      else -> draft.copy(chips = SearchTags.filtersFirst(draft.chips + word))
    }
  }

  fun withMe(me: String?): SearchDraft = of(tags, me)

  fun withFavourites(favourites: Favourites?): SearchDraft = copy(
    chips = chips.filterNot { Favourites.of(it, me) != null },
    favourites = favourites,
  )

  fun remove(chip: String): SearchDraft = copy(chips = chips - chip)

  fun withRatings(ratings: Set<Rating>): SearchDraft = copy(
    chips = chips.filterNot(Rating::isRatingTag),
    ratings = if (ratings.size == Rating.entries.size) emptySet() else ratings,
  )

  fun with(filter: Filter, option: FilterOption?): SearchDraft = copy(
    chips = chips.filterNot { it.startsWith(filter.prefix, ignoreCase = true) },
    filters = if (option == null) filters - filter else filters + (filter to option),
  )

  companion object {
    private val WHITESPACE = Regex("\\s+")

    fun of(tags: List<String>, me: String? = null): SearchDraft = SearchDraft(me = me).add(tags.joinToString(" "))
  }
}
