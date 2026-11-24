# Contributing

Bug reports and feature requests are welcome as [issues](../../issues/new/choose), through the bug and feature forms.
Every pull request needs the `build` check green before it merges.

The repository, modules and Kotlin packages keep the old working name `sankaku`; the app is Tessera.

## Prerequisites

- A JDK. Gradle downloads JDK 25 for its daemon if needed (`gradle/gradle-daemon-jvm.properties`).
- The Android SDK, with its path in `local.properties` (`sdk.dir=…`). The Android targets compile in every build.

## Running

```sh
scripts/gradlew :desktopApp:run
```

- `-Dsankaku.sample=true` opens the compatibility sample for the Material 3 Expressive components.
- `-Dsankaku.debug.expireAccessToken=true` replaces the stored access token with an invalid one on launch, so the next
  request gets a 401 and refreshes.
- `-Dsankaku.debug.safeOnly=true` holds G only on for the run: `rating:s` replaces any rating tag in every search,
  also when signed in. Use it for test runs and screenshots.
- `-Dsankaku.debug.crash=ui` crashes the window 3 s after the app shows; the next launch shows "Tessera closed
  unexpectedly". Use it with `-Dsankaku.debug.dataDir`. `thread` fails a background thread instead, which leaves the
  desktop app running and so isn't recorded.

## Smoke test

`:cli` runs the login flow and every API discovery step against the live API, prints a table, and exits non-zero on
any failure. Run it before and after changing any endpoint, and first when the app breaks.

1. Copy `.env.example` to `.env.local` and fill in a dedicated test account.
2. `scripts/gradlew :cli:run`

Discovery steps compare the response with `docs/api-recorded.json`; a missing entry is recorded on the first run.
`--args=--rerecord` overwrites the entries after an intended change, `--args=--slow` adds the one-hour expired-URL
check, and `--args=--android` sends the Android app's User-Agent and client hints instead of the desktop's. The table
and the JSON key shapes of each response go to `cli/out/`. Output never contains tokens or user fields.

Steps 16 and 17 keep a ledger between runs outside the repository, in `~/.sankaku-cli/feed-ledger.json` (or
`SANKAKU_FEED_LEDGER`): keyset cursors that later runs replay to see how long a cursor stays valid.

## Module boundaries

- `:sharedLogic` has no Compose dependency, so `:cli` runs the exact code the app uses. It compiles with warnings as
  errors.
- `:sharedUI` holds all UI. Only `ui/theme` and `ui/components` may opt in to `ExperimentalMaterial3ExpressiveApi`;
  screens use the components from there, so a pre-release signature change breaks few files.
- `:desktopApp` and `:androidApp` only start the app.

## API rules

- Endpoint-specific headers are set only by `apiRequest` in `sharedLogic/.../api/Request.kt`. The one exception is
  `probe`, which only `:cli` uses.
- Never send the V2 header set on post requests: it strips `file_url` and `sample_url`.
- Bearer tokens go only to `sankakuapi.com`. Auth calls (login, exchange, refresh) use the auth client, which has no
  `Auth` plugin.
- Never print or log tokens, passwords, user fields, ids, md5s or query strings.
- No load on Sankaku beyond what the user asked for: every request goes through the rate limiter, nothing polls, a
  background tab doesn't load, and API calls aren't retried automatically. The exceptions: when the network comes
  back, the shown tab retries once the loads that failed because Sankaku couldn't be reached; the outbox's
  favourites, votes and follows are sent at launch and whenever the connection may be back, one round at a time that
  stops at the first that must wait; and a grid page that ran past Sankaku's search-time limit (408) is asked once
  more before it shows its error.
- A network error, timeout, 403, 429 or 5xx never signs the user out, and a token refresh happens only inside
  `AuthSession`'s mutex.

## Versions

- Exact versions live only in `gradle/libs.versions.toml`.
- Every library uses its newest stable release, except `org.jetbrains.compose.material3:material3`: the newest
  pre-release with the same minor version as Compose Multiplatform.
- Update with `scripts/gradlew versionCatalogUpdate --interactive`, then `scripts/gradlew versionCatalogApplyUpdates`.

## Checks

`./gradlew build` compiles every module for every target and runs all unit tests. CI's `build` check runs it without
the release APK (`-x :androidApp:assembleRelease`).

Locally, run the narrowest tasks that cover your change: the touched tests
(`scripts/gradlew :sharedUI:jvmTest --tests '*QueuePageBehaviourTest'`) or the touched module's compile
(`scripts/gradlew :sharedLogic:compileKotlinJvm`). `scripts/gradlew` waits for one of `GRADLE_SLOTS` (default 3) build
slots shared by every checkout on the machine, so parallel builds don't run it out of memory. A build that fails, hangs
or loses its daemon is simply run again; don't stop the shared daemons.

UI tests assert state, semantics and layout bounds. How a change looks and animates is checked in the running app,
with screenshots or a recording in the PR. Pixel-capturing tests are slow on CI and break with any design change.

## Releases

The version is `tessera` in `gradle/libs.versions.toml`, used by both apps. It follows epoch semantic versioning: the
first number is EPOCH × 100 + MAJOR, then MINOR and PATCH. The APK's `versionCode` is first × 10000 + MINOR × 100 +
PATCH, so MINOR and PATCH stay below 100.

1. Bump `CHROME_MAJOR_VERSION` in `sharedLogic/.../api/BrowserFingerprint.kt` to the current stable Chrome's major
   version ([Chromium Dash](https://chromiumdash.appspot.com/schedule)).
2. Run the dependency update check.
3. Set the `tessera` version.
4. Draft the version's `CHANGELOG.md` section with [git-cliff](https://git-cliff.org):
   `git cliff --unreleased --tag v<version> --prepend CHANGELOG.md`, then edit it into what a user wants to read. The
   section becomes the release's notes.
5. Merge that to `main`, then tag it: `git tag -a v<version> -m "Tessera <version>" && git push origin v<version>`.
6. The tag starts `release.yml`. It checks the tag against the catalog and the changelog, builds the DMG, and builds
   the signed APK once the `release` environment is approved. Check the release and its attestations:

   ```sh
   gh release download v<version> --repo dev-marzahn/tessera --pattern 'Tessera-*' --dir /tmp/tessera-<version>
   gh attestation verify /tmp/tessera-<version>/Tessera-<version>.apk --repo dev-marzahn/tessera
   gh attestation verify /tmp/tessera-<version>/Tessera-<version>.dmg --repo dev-marzahn/tessera
   ```

`./gradlew release` builds the same files locally (`releaseApk` and `releaseMac`), for testing before a tag and as the
fallback. It reads the signing settings from `~/.gradle/gradle.properties` (`tessera.signing.storeFile`,
`storePassword`, `keyAlias`, `keyPassword`; for notarizing, `tessera.mac.signingIdentity` and
`tessera.mac.notaryProfile`) and names any that are missing.
