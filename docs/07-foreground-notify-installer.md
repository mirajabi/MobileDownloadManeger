# Foreground Notification & Auto-Installer

## Goals
- Merge the foreground service notification and download progress into a single card that always stays up-to-date.
- Support pause/resume/stop actions from the notification itself.
- Allow multi-chunk downloads to run truly in parallel for improved throughput.
- Offer an optional “prompt to install” flow once an APK/APKS file finishes downloading.

## Runtime Flow
1. `DownloadNotificationHelper` listens to all `DownloadListener` events and rebuilds the same notification with the proper title, progress bar, and action buttons.
2. `DownloadForegroundService` now calls `startForeground(...)` only once; subsequent updates use `NotificationManager.notify(...)`, so text and progress change instantly without collapsing the card.
3. `ChunkedDownloader` launches multiple coroutines (bounded by `chunkCount`) and writes directly into the target file via `FileChannel.write(offset, buffer)`. When the server only discloses `Content-Length` in the GET response, the total size is inferred from `Content-Range` and rebroadcast for accurate percentages.
4. After a successful download, `DownloadInstaller` optionally fires an install intent (using the library’s own `FileProvider`) so the user can install immediately.

## Permissions & Storage
- The sample app now requests `READ/WRITE_EXTERNAL_STORAGE` at runtime (for API 23‑29) and sets `requestLegacyExternalStorage=true`. This allows writing directly into `/storage/emulated/0/Download/...` when the sample chooses a `DownloadDestination.Custom`.
- On Android 11+, downloads still land in the app-specific directory; a future enhancement can mirror the file to `MediaStore.Downloads` if public visibility is required.

## Key Files
- `downloader/src/main/java/com/miaadrajabi/downloader/DownloadForegroundService.kt` – runs all downloads, relays listener events to UI, and updates the single foreground notification.
- `downloader/src/main/java/com/miaadrajabi/downloader/DownloadNotificationHelper.kt` – builds the card, toggles Pause/Resume action text, and pushes updates via the service.
- `downloader/src/main/java/com/miaadrajabi/downloader/ChunkedDownloader.kt` – handles parallel chunk execution, infers total bytes from GET responses, and smooths speed measurements.
- `downloader/src/main/java/com/miaadrajabi/downloader/DownloadInstaller.kt` – wraps the `FileProvider` logic and fires the installer intent.
- `app/src/main/java/com/miaadrajabi/mobiledownloadmaneger/MainActivity.kt` & `JavaSampleActivity.java` – request storage permissions, enqueue downloads through the service, and react to pause/resume callbacks for the UI buttons.

## Usage Checklist
1. Configure the builder with `storageDestinations(listOf(DownloadDestination.Custom(...)))` when you want a specific path.
2. Call `installerPromptOnCompletion(true)` if you want the system’s installer prompt immediately after completion.
3. Ensure your host app defines a `FileProvider` (the library does this internally for the downloader module).
4. For JitPack consumers, publish a new Git tag (`git tag v1.0.0 && git push origin v1.0.0`) so JitPack can build the exact version.

