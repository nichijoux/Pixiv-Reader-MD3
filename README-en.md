# PixivReader

<p align="center">
  <em>An unofficial Pixiv client for Android</em>
</p>

**English | [简体中文](./README.md)**

> ⚠️ This is an **unofficial** third-party client, not affiliated with Pixiv Inc. All illustrations, manga and novel works remain copyrighted by their respective creators or Pixiv. Use at your own risk regarding account security (e.g. Pixiv rate-limit/risk-control on third-party clients, OAuth login restrictions). Please comply with Pixiv's Terms of Service.

## Features

- **Illustrations / Manga**: waterfall feed, artwork detail, pinch-to-zoom, ugoira (motion) playback, rankings (slide-to-switch sections + infinite pagination).
- **Novels**: online reading, novel rankings, local **TXT / EPUB / Markdown** import & reading, export (PDF / TXT).
- **Comments**: text comments + text emoji + pixiv stamps.
- **Search**: illustration / novel search (shared query across tabs).
- **History**: browsing history & search history.
- **Downloads**: illustration / page downloads via WorkManager background tasks, progress tracking, completion notifications.
- **Bookmarks / Favorites / Watchlist / Follows**.
- **Personalization**: dark mode + dynamic color (Material 3), in-app **中文 / English** language switch.

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.1 |
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

- **Requirements**: Android 8.0 (API 26)+; JDK 21; Gradle 8.14.3 (wrapper).
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

- Inspired by **[Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft) (MIT)** and the pixiv client ecosystem; parts of the API wrapper / login flow derive from or reference it.
- Third-party dependencies retain their own licenses: `lib:pixivapi` (vendored pixiv API wrapper, upstream source), `pixiv-login`, Room, MMKV, Coil, PDFBox, etc.

## License

This project is licensed under **GPL-2.0** (see [LICENSE](./LICENSE)).
Third-party dependencies retain their own licenses; please verify the terms of `lib:pixivapi` (vendored pixiv API wrapper) and `pixiv-login` before publishing.

---

*For learning purposes only. Please respect the creators and Pixiv's rights; do not use for commercial purposes.*
