package dev.marzahn.sankaku.images

import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options

object ImmutableCacheStrategy : CacheStrategy {
  override suspend fun read(
    cacheResponse: NetworkResponse,
    networkRequest: NetworkRequest,
    options: Options,
  ): CacheStrategy.ReadResult = CacheStrategy.ReadResult(cacheResponse)

  override suspend fun write(
    cacheResponse: NetworkResponse?,
    networkRequest: NetworkRequest,
    networkResponse: NetworkResponse,
    options: Options,
  ): CacheStrategy.WriteResult =
    if (networkResponse.code in 200..299) CacheStrategy.WriteResult(networkResponse) else CacheStrategy.WriteResult.DISABLED
}
