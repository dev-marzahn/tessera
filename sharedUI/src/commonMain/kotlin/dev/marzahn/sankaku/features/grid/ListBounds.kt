package dev.marzahn.sankaku.features.grid

import androidx.paging.compose.LazyPagingItems
import dev.marzahn.sankaku.api.dto.PostDto

internal fun <T : Any> LazyPagingItems<T>.peekOrNull(index: Int): T? = if (index in 0 until itemCount) peek(index) else null

internal fun <T : Any> LazyPagingItems<T>.getOrNull(index: Int): T? = if (index in 0 until itemCount) get(index) else null

internal fun LazyPagingItems<PostDto>.viewerPageKey(page: Int, order: ReadingOrder): Any = peekOrNull(order.indexAt(page))?.id ?: page
