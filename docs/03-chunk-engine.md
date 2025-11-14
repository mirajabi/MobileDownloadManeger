# 3. Chunked Download Engine

## Overview
- `MobileDownloadManager.enqueue()` now performs the full download pipeline: storage resolution, HTTP transfer (optionally chunked), retry with exponential backoff, and listener callbacks for every state.
- `ChunkedDownloader` handles range planning, OkHttp requests, progress dispatch, and writes data via `RandomAccessFile`.
- The sample app exposes a “Start Sample Download” button that queues a real 1 MB file and mirrors queue/start/progress/complete/fail events in the UI.

## Flow
1. `enqueue(request)`
   - Resolves the target file via `StorageResolver`.
   - Emits `onQueued`.
   - Launches a coroutine to call `runDownloadWithRetry`.
2. `runDownloadWithRetry`
   - Emits `onStarted`.
   - Invokes `ChunkedDownloader.download`.
   - On success → `onCompleted`, removes cached destination.
   - On `IOException` → retries up to `RetryPolicy.maxAttempts`, emitting `onRetry` before each attempt.
   - On unrecoverable errors → `onFailed`.
3. `ChunkedDownloader.download`
   - Optional HEAD request to determine content length.
   - `ChunkPlanner` splits the file into ranges (respecting `ChunkingConfig` min sizes and counts).
   - Executes GET calls (with Range headers when possible) and streams bytes into the file while `ProgressDispatcher` sends `onProgress`.

## Configurable Pieces
- `ChunkingConfig`: chunk count, minimum chunk size, parallel preference (currently serialized downloads until concurrency control is added).
- `RetryPolicy`: attempts, initial delay, multiplier (default exponential growth).
- `NotificationConfig`: already wired for future Foreground Service work; not yet visualized in sample.

## Sample App Hooks
- Button `Start Sample Download` calls `downloadManager.enqueue(...)` with a timestamped filename.
- `DownloadListener` implementation logs events and updates `TextView` showing the latest status (queued, started, bytes downloaded, completed, failed).
- Storage preview (from Stage 2) still runs so testers can see the next directory/file before clicking.

## Next Ideas
- Parallel chunk execution + pause/resume controls.
- Foreground service + persistent notifications tied to the progress callbacks.
- Hash verification and post-download install flow.

