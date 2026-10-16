# Fetch desktop and Span engine

For the desktop product, screenshots, installers and setup, read the dedicated **[Fetch Desktop README](desktopApp/README.md)** or visit the **[desktop website](https://mirajabi.github.io/MobileDownloadManeger/desktop/)**.

This independent Kotlin Multiplatform build lives alongside the older Android project. Run its own Gradle wrapper from **this directory**, using JDK 17.

```sh
./gradlew :engine:jvmTest :desktopApp:jvmTest
python3 tools/media/prepare.py
./gradlew :desktopApp:run
```

## Desktop interface

The initial window is 1040 × 680 logical pixels and later sizes are remembered. **View → Reset window size** restores it. Navigation collapses into an animated icon rail and can be dragged between 184 and 280 dp using its edge grip. It scrolls without a visible scrollbar. The inspector also scrolls independently; narrow windows use a dismissible inspector.

**Settings → Appearance** offers Cobalt, Tidal, Amber, Forest, Orchid and Rose. These use generated Material HCT palettes and distinct tonal surfaces. Translucent panels and a diffuse backdrop create the frosted appearance inside the app. The header icon is SVG; native app icons include Retina and Windows multi-size artwork. See `tools/design/README.md` for regeneration.

Download cards and rows expand inline. Right-click anywhere on an item for a rounded context menu at the pointer. The add dialog checks the exact link and destination before enabling download, and changing the input invalidates old checks. The tray includes add/paste/batch, pause all, resume, retry, destination, recent files, clipboard monitoring and window controls.

Pause cancels HTTP calls without interrupting shared file-channel writers. Workers finish their checkpoints before a resume can start. Cancellation listeners are atomic, late progress is ignored, and a transfer failure is contained rather than closing the application.

## Media Studio

YouTube and Instagram buttons open a resizable studio with thumbnail/title, quality selection, inline player, seeking, volume, destination and queue selection. Extraction uses the bundled **yt-dlp + Deno** tools. Requests have a timeout and cancel when the dialog/link changes. It does not read browser cookies or user yt-dlp configuration/plugins.

Direct files are verified before download. When YouTube exposes separate MP4 video and M4A audio, Fetch offers complete video-with-audio choices, previews synchronized tracks and downloads both through the resumable engine. Bundled FFmpeg joins them without re-encoding. Paused tracks stay in a `.span-media` work directory; completed merges remove it. Source page and format IDs persist so expired addresses can be refreshed after restarting.

Private, audience-restricted, deleted and sign-in-required videos can be rejected by the source. Their error appears in the studio. DRM and segmented-only formats are not offered as ordinary downloadable files. Inline preview supports H.264 MP4 and supported audio codecs; other direct formats can still be downloaded. An Instagram reel can be restricted even if its link is syntactically valid.

Helpers are downloaded only by `tools/media/prepare.py`, from pinned releases with SHA-256 verification. They and their notices are bundled with installers; no global helper installation is required. See `tools/media/versions.json` and each packaged `media-tools/*LICENSE*` / `SOURCES.txt` file. Primary references: [yt-dlp](https://github.com/yt-dlp/yt-dlp), [JavaScript support](https://github.com/yt-dlp/yt-dlp/wiki/EJS), [FFmpeg binary sources](https://github.com/eugeneware/ffmpeg-static).

## Installers

```sh
python3 tools/media/prepare.py
./gradlew :desktopApp:packageDistributionForCurrentOS
```

Outputs are in `desktopApp/build/compose/binaries/main/`: DMG on macOS, MSI/EXE on Windows and DEB on Linux. Each includes its Java runtime, platform-specific native UI libraries and media helpers. The native package must be built on its target OS and architecture; see [Compose packaging](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html). `.github/workflows/desktop-installers.yml` at the repository root provides a manually triggered three-OS build. It uploads artifacts without publishing a release. Certificates/notarization are not configured.

## Mobile

```sh
./gradlew :androidApp:assembleDebug
./gradlew :mobile:linkDebugFrameworkIosSimulatorArm64
```

`:androidApp` and `:mobile` already contain an Android app and a separate mobile screen. The desktop UI uses JVM/AWT/JavaFX and does not automatically become Android UI. A polished Android release needs mobile layouts, native media playback, foreground download service, notifications/storage handling and release signing. The engine can be shared. The iOS shell is under `iosApp`.

Desktop downloads default to `~/Fetch`; preferences/history remain in `~/Span`. Mobile files use the app's private Span directory.

## Verification

Tests cover pause/resume byte integrity, concurrent cancellation, palette contrast, resizing/collapse, long scrollable navigation, inline expansion, right-click menus, stale link checks, media parsing and loopback player transport. UI snapshots are written to `desktopApp/build/ui-review/`.

Optional `PlaybackSmokeTest` cases accept JVM properties `fetch.player.sample` (local MP4) and `fetch.youtube.sample` (a fresh yt-dlp JSON result) to verify native playback and live media access. They skip without those fixtures, so normal CI needs no external video service.
