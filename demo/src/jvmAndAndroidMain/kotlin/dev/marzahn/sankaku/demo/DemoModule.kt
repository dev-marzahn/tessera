package dev.marzahn.sankaku.demo

import dev.marzahn.sankaku.CACHE_DIR
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.TokenStorageStatus
import dev.marzahn.sankaku.auth.TokenStore
import dev.marzahn.sankaku.data.FirstLaunch
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.TextFiles
import dev.marzahn.sankaku.features.post.SaveFolder
import io.ktor.client.engine.HttpClientEngine
import okio.Path
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun demoModule(scratch: Path): Module = module {
  single { DemoMedia(scratch / "media") }
  single { DemoSankaku(get()) }
  single<HttpClientEngine> { get<DemoSankaku>().engine }
  single<TokenStore> { InMemoryTokenStore() }
  single { TokenStorageStatus.InMemory }
  single<TextFiles> { MemoryTextFiles() }
  single { FirstLaunch(InMemoryTextFile()).apply { accept() } }
  single<SaveFolder> { MemorySaveFolder() }
  single(CACHE_DIR) { scratch / "cache" }
}
