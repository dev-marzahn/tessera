package dev.marzahn.sankaku.features.books

import dev.marzahn.sankaku.api.dto.BookDto

fun interface BookOpener {
  fun open(book: BookDto)
}
