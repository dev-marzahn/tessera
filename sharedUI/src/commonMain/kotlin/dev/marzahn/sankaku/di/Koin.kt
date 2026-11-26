package dev.marzahn.sankaku.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

fun appModules(platform: Module, overrides: List<Module> = emptyList()): List<Module> =
  listOf(sharedModule, platform, shellModule, authModule, gridModule, postModule, booksModule) + overrides

fun startSankakuKoin(platform: Module, overrides: List<Module> = emptyList(), extra: KoinAppDeclaration = {}): KoinApplication =
  startKoin {
    extra()
    modules(appModules(platform, overrides))
  }
