package dev.marzahn.sankaku.demo

import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.log.CrashReports

fun demoCrashReports(): CrashReports = CrashReports(InMemoryTextFile())
