# 6. Pause / Resume

## Overview
- Downloads now support pausing and resuming without restarting from byte 0. The manager tracks active sessions, cancels their coroutines on pause, snapshots the completed byte count, and restarts the pipeline with Range headers when resume is requested.
- `ChunkedDownloader` accepts a `startOffset` so it can skip already-written bytes and emit progress that continues from the saved offset.
- The sample app exposes Pause/Resume buttons that operate on the most recent handle returned by `enqueue`, making it easy to observe the lifecycle callbacks and notifications.

## Key Pieces
1. **Session Tracking**
   - `MobileDownloadManager` keeps `DownloadSession` (request + storage + job) in memory.
   - Calling `pause(handleId)` stores a `PausedState` (request, resolution, completed bytes) and cancels the job. Listeners receive `onCancelled`, and the foreground notification updates accordingly.
   - `resume(handleId)` reuses the stored state, restarts the foreground service if needed, and calls `runDownloadWithRetry(... startOffset = completedBytes)`.
2. **Notification Controls**
   - The expanded notification now shows Pause, Resume, and Stop actions. Pressing them triggers a broadcast handled by `DownloadNotificationActionReceiver`, which proxies the call to the active manager.
   - The notification text displays downloaded vs total bytes, percentage, instantaneous speed, and remaining size.
3. **Chunk Planner Awareness**
   - When resuming, chunk ranges are filtered so they start at `max(originalStart, startOffset)`. If the total size is unknown, a Range request `bytes=startOffset-` is issued.
   - Progress counters begin from `startOffset`, so UI and notifications show the true cumulative amount.
4. **Cancellation Handling**
   - `runDownloadWithRetry` now treats `CancellationException` differently: if it was triggered by pause, the paused state stays on disk and no `onFailed` is fired; otherwise listeners receive `onCancelled`.

## Sample App
- Buttons `Pause` / `Resume` call the new APIs. When paused, the UI text changes to “Paused: <handleId>”; tapping Resume resumes the same handle.
- Start button stores the `DownloadHandle.id` gathered from `enqueue(...)`, so there is always a known target for Pause/Resume while testing.

## Notes
- Paused states are currently kept in-memory; future work can persist them so resumes also work after process death.
- Parallel chunk execution (next stage) will leverage the same state model but needs per-chunk checkpoints instead of a single offset.

