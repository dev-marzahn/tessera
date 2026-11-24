package dev.marzahn.sankaku.api

const val LOGIN_BASE = "https://login.sankakucomplex.com"
const val API_BASE = "https://sankakuapi.com"
const val API_HOST = "sankakuapi.com"

const val FILE_HOST = "s.sankakucomplex.com"

val IMAGE_HOSTS = setOf(FILE_HOST, "v.sankakucomplex.com")

const val CLIENT_ID = "sankaku-web-app"
const val CLIENT_URL = "https://www.sankakucomplex.com"

internal const val API_REQUEST_TIMEOUT_MS = 30_000L
