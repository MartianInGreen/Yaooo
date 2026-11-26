<p align="center">
  <img src="docs/logo.svg" width="128" alt="Yaooo logo">
</p>

<h1 align="center">Yaooo</h1>
<p align="center">An unofficial, offline-capable reader for <a href="https://archiveofourown.org">Archive of Our Own</a>.<br>
Search, bookmark, download, and get told when your fics update.</p>

> Yaooo is not affiliated with or endorsed by AO3 or the Organization for Transformative Works.

## Features

- **Search** by keyword, title, author and tags (fandom, character, relationship, additional tags) with AO3's tag
  autocomplete and tag exclusion. Filters: rating, warnings, category, complete / in progress, crossovers, one-shots,
  word count, minimum kudos, update date, language, and sort order. Tapping a tag on a fic searches for it.
- **Library** of bookmarked and downloaded fics, with filters for new chapters, downloaded, and in progress.
  **Add from URL** adds any AO3 work link (optionally downloading it at the same time).
- **Offline reading**: download a whole fic and read it with no network.
- **Reading progress**: your exact paragraph is remembered per fic, and the library shows a progress bar and
  percentage on every fic you've started.
- **New-chapter notifications**: a background job periodically checks bookmarked, unfinished fics and posts a
  notification per updated fic (tap to open it). Downloaded fics refresh automatically. Interval is configurable
  (1–24 h), and there is a "Check now" button in Settings.
- **Reader**: text size, line spacing, serif / sans, chapter picker, tap to hide the bars, light / dark / system theme.

## How it works

AO3 has no public API, so Yaooo reads AO3's own pages and files:

| Need | Source |
|------|--------|
| Search results, tag autocomplete | `/works/search`, `/autocomplete/<type>` |
| Fic metadata + all chapters | AO3's **HTML download** (`/downloads/<id>/work.html`) – the same file as the site's Download button; one request, simple stable markup |
| "Any new chapters?" check | `/works/<id>/navigate` (one light request per fic) |

Requests are serialised, spaced at least 1.5 s apart, honour `Retry-After`, and AO3's bot-filter page is detected and
reported instead of being retried. Please keep it that way and respect AO3's [Terms of Service](https://archiveofourown.org/tos).

## Tech

Kotlin Multiplatform + **Compose Multiplatform** (Material 3), so the UI and almost all logic are shared and an iOS
port is mostly a host app.

| Concern | Library |
|---------|---------|
| UI | Compose Multiplatform |
| HTTP | Ktor (OkHttp on Android, Darwin on iOS) |
| HTML parsing | Ksoup (multiplatform Jsoup port) |
| Database | SQLDelight (SQLite), with migrations |
| Background work | WorkManager (Android) behind the `UpdateScheduler` interface |

```
shared/                     Kotlin Multiplatform module
  commonMain/
    data/                   Ao3Client, Ao3Parser, ContentParser, Repository, UpdateChecker, models
    ui/                     Search, Library, Work, Reader, Settings screens
    sqldelight/             schema + migrations
    Platform.kt             expect/actual seams: DB driver, scheduler, notifier, back handler
  androidMain/              WorkManager worker, notifications, SQLite driver
  iosMain/                  iOS actuals + MainViewController (not yet compiled, see below)
androidApp/                 thin Android host: Application, MainActivity, manifest, icons
docs/logo.svg               logo source
```

The reader renders one lazy-list item per paragraph, so long chapters stay smooth.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```sh
./gradlew :androidApp:assembleDebug    # APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew :shared:testDebugUnitTest    # parser, query, URL and progress tests
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## Status and known limitations

- No AO3 login: bookmarks are local, and works restricted to registered users can't be opened.
- Scraping is fragile by nature. Parsers are covered by tests built from real AO3 download markup, but search-result
  and `/navigate` markup have only been checked against fixtures modelled on AO3's HTML; if either changes, update
  `Ao3Parser`.
- The AO3 filter IDs for warnings / ratings / categories are AO3's form values written from memory; verify against
  the live search form if a filter misbehaves.
- Very long fics download as one large file, so the first open can take a moment.
- **iOS**: `iosMain` is written but has never been compiled (needs macOS). Still to do: an Xcode host app calling
  `MainViewController()`, a `BGAppRefreshTask` in `IosUpdateScheduler`, and `UNUserNotificationCenter` in `IosNotifier`.

## License

See [LICENSE](LICENSE).
