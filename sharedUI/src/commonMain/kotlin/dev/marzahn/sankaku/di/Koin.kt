package dev.marzahn.sankaku.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

fun appModules(platform: Module): List<Module> =
  listOf(sharedModule, platform, shellModule, authModule, gridModule, postModule, booksModule)

fun startSankakuKoin(platform: Module, extra: KoinAppDeclaration = {}): KoinApplication = startKoin {
  extra()
  modules(appModules(platform))
}
