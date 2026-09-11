# PixivReader

<p align="center">
  <em>An unofficial Pixiv client for Android</em>
</p>

**English | [简体中文](./README.md)**

> 🌐 **Release site**: <https://nichijoux.github.io/Pixiv-Reader-MD3/> — APK downloads, setup guide and phone/tablet screenshots.

> ⚠️ This is an **unofficial** third-party client, not affiliated with Pixiv Inc. All illustrations, manga and novel works remain copyrighted by their respective creators or Pixiv. Use at your own risk regarding account security (e.g. Pixiv rate-limit/risk-control on third-party clients, OAuth login restrictions). Please comply with Pixiv's Terms of Service.

## Features

- **Illustrations / Manga**: waterfall feed, artwork detail, pinch-to-zoom, ugoira (motion) playback & export (**MP4 video / ZIP frame pack**), rankings (day / week / month / male / female / rookie / R-18 sections with slide-to-switch + infinite pagination).
- **Novels**: online reading (reading-progress memory), novel rankings, local **TXT / EPUB / Markdown** import & reading, export (PDF / TXT).
- **Discover / Ecosystem**: illustration & novel search with trending tags, pixivision features, illustrator / AI / era / wallpaper rankings; FANBOX / COMIC / DMs open in the system browser.
- **Watchlist**: follow novel / manga series, series detail pages, watchlist management (novel / manga sections + inline unfollow).
- **Comments**: text comments + text emoji + pixiv stamps; delete your own comments.
- **History / Read Later / Blocking**: browsing & search history; long-press cards to add to read later (manager with artwork / novel sections and clear-all); long-press to block works (blurred card overlay + block manager).
- **Downloads**: illustration / page downloads via WorkManager background tasks, progress tracking, completion notifications, offline queuing and retry.
- **Bookmarks / Favorites / Follows**: bookmark editor supports public / private and tag editing.
- **Personalization**: dark mode + dynamic color (Material 3), in-app **简体中文 / 繁體中文 / English** language switch, font scale.
- **Misc**: home feed snapshot (instant cold start, offline browsing), in-app update check (GitHub Releases + rendered changelog), `pixiv://` deep links.

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose (Material 3) |
| Architecture | Single Activity + Compose Navigation, MVVM |
| DI | Hilt |
| Data | Room, DataStore, MMKV (session tokens) |
| Network | Retrofit / OkHttp / Gson (`lib:pixivapi` is a vendored pixiv API wrapper) |
| Background | WorkManager (downloads, export) |
| Parsing | jsoup; PDFBox (PDF export); Android-OpenCC (simplified/traditional Chinese) |
| Images | Coil (automatic Referer) |

Module dependency (hard constraint): `app → feature/* → core/ui → core/network → core/database · datastore · model → core/common`. Features must not depend on each other; shared logic lives in `core`.

## Build

- **Requirements**: Android 8.0 (API 26)+; JDK 21; Gradle 9.7.1 (wrapper).
- Command-line build (Windows, no Android Studio):

```powershell
# Set JDK (adjust to your local path)
$env:JAVA_HOME = "C:\Users\<user>\.jdks\jbr-21.0.11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Quick compile (recommended sanity check)
.\gradlew.bat :app:compileDebugKotlin --console=plain

# Build Debug APK
.\gradlew.bat :app:assembleDebug --console=plain

# Unit tests (for touched modules)
.\gradlew.bat :core:novel:testDebugUnitTest :core:network:testDebugUnitTest --console=plain
```

## Releases

Pushing a `v*` tag (e.g. `v1.2.3`) triggers GitHub Actions to build Release APKs and split them **by ABI** into two slim packages — `arm64-v8a` (ARMv8) and `armeabi-v7a` (other ARM) — which are uploaded to GitHub Releases. Release signing requires repository Secrets: `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` (unsigned APKs are produced if not configured).

## Attribution

- Inspired by **[Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft) (GPL-2.0)** and the pixiv client ecosystem; parts of the API wrapper / login flow derive from or reference it.
- Third-party dependencies retain their own licenses: `lib:pixivapi` (vendored pixiv API wrapper, upstream source), `pixiv-login`, Room, MMKV, Coil, PDFBox, etc.

## License

This project is licensed under **GPL-2.0** (see [LICENSE](./LICENSE)).
Third-party dependencies retain their own licenses; please verify the terms of `lib:pixivapi` (vendored pixiv API wrapper) and `pixiv-login` before publishing.

---

*For learning purposes only. Please respect the creators and Pixiv's rights; do not use for commercial purposes.*
