package dev.marzahn.sankaku.api

enum class ErrorAction { Retry, EditSearch, SignIn }

data class ErrorAdvice(val sentence: String, val actions: List<ErrorAction>, val retryAfterSeconds: Long? = null)

fun Throwable.advice(signedIn: Boolean = true): ErrorAdvice {
  val retry = listOf(ErrorAction.Retry)
  return when (this) {
    is ApiError.Http if status == 404 && !signedIn ->
      ErrorAdvice("Sankaku shows this only to signed-in accounts. Sign in to view it.", listOf(ErrorAction.SignIn))
    is ApiError.Network ->
      ErrorAdvice(if (timedOut) "Sankaku took too long to answer. Try again." else "Can't reach Sankaku. Check your connection and try again.", retry)
    is ApiError.Unauthorized -> ErrorAdvice("Your session has ended. Sign in again.", listOf(ErrorAction.SignIn, ErrorAction.Retry))
    is ApiError.RateLimited -> ErrorAdvice("Too many requests at once. Try again in a moment.", retry, retryAfterSeconds)
    is ApiError.Rejected -> ErrorAdvice("Sankaku is turning this app away right now. Try again later.", retry)
    is ApiError.SearchTimeout ->
      ErrorAdvice("This search took Sankaku too long. Try fewer or narrower tags.", listOf(ErrorAction.EditSearch, ErrorAction.Retry))
    is ApiError.TagLimit -> ErrorAdvice(
      limit?.let { "Your search limit is $it tags. Remove a tag first." }
        ?: "Your search has more tags than Sankaku allows. Remove a tag first.",
      listOf(ErrorAction.EditSearch),
    )
    is ApiError.Http -> ErrorAdvice(
      when {
        status >= 500 -> "Sankaku is having trouble. Try again in a moment."
        status == 404 -> "Sankaku couldn't find it. It may have been deleted."
        status in 400..499 -> "Sankaku didn't accept that. Try again later."
        else -> UNREADABLE
      },
      retry,
    )
    is ApiError.Decoding -> ErrorAdvice(UNREADABLE, retry)
    else -> ErrorAdvice("Something went wrong. Try again.", retry)
  }
}

fun Throwable.couldNot(action: String): String = "Couldn't $action. ${advice().sentence}"

private const val UNREADABLE = "Sankaku sent an answer the app can't read. Try again; if it keeps happening, report a bug."
