# 4. Foreground Service & Notifications

## Overview
- `MobileDownloadManager` now keeps downloads alive via `DownloadForegroundService`. The service is started when the first job is enqueued (if `enforceForegroundService = true`) and stopped automatically when the queue drains.
- `DownloadNotificationHelper` mirrors every lifecycle event: queued, started, progress, completed, failed, and cancelled. Each download gets its own notification plus a persistent foreground notification summarizing the active count.
- The helper shares a single notification channel (configurable via `NotificationConfig`) and reuses the same instance across the manager and service via `DownloadNotificationRegistry`.

## Key Components
1. **DownloadNotificationHelper**
   - Ensures the notification channel exists.
   - Implements `DownloadListener`, so it hooks into all events transparently.
   - Provides a foreground notification builder and dispatcher used by the service.
2. **DownloadForegroundService**
   - Minimal `Service` that calls `startForeground(...)` with the helper’s notification.
   - Exposes static `start(status)`, `update(status)`, and `stop(context)` helpers.
   - Declared in the library manifest with `foregroundServiceType="dataSync"`.
3. **MobileDownloadManager**
   - Tracks active download count; starts the service on the first job, updates the status text whenever progress events arrive, and stops the service when the count reaches zero.
   - Pipes listener events through `DownloadNotificationHelper` in addition to user-supplied listeners.

## Sample App Integration
- Added `android.permission.FOREGROUND_SERVICE` to the app manifest.
- When the “Start Sample Download” button is pressed, the app now:
  1. Starts the foreground service (visible notification “Downloads running”).
  2. Displays per-download notifications with progress bars.
  3. Updates the on-screen status text in sync with the notification helper callbacks.

## Notes & Next Steps
- Notification appearance (icon/text) comes from `NotificationConfig`; apps can supply their own icons and wording via the builder DSL.
- Future work can add notification actions (pause/resume/cancel) and richer grouping per download batch.
- The same infrastructure will be reused once WorkManager/AlarmManager scheduling and installer prompts arrive in later stages.

