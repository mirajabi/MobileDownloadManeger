# 5. Scheduler Integration

## Overview
- `DownloadScheduler` now bridges WorkManager (periodic or delayed jobs) and AlarmManager (exact wall-clock triggers). It respects `SchedulerConfig` and even accepts per-request overrides via `MobileDownloadManager.schedule(...)`, supporting weekly (weekday-based) as well as absolute date/time targets.
- `DownloadConfigStore` persists the active configuration in SharedPreferences so scheduled jobs can recreate the manager after process death.
- `DownloadRequestAdapter`, `ScheduledDownloadWorker`, and `DownloadAlarmReceiver` serialize each `DownloadRequest` and enqueue it when the OS fires the job.
- The sample app demonstrates scheduling a download for every Tuesday at 00:30, updating the UI with the human-readable target time.

## Flow
1. `MobileDownloadManager.schedule(request, scheduleTime)`
   - Delegates to `DownloadScheduler`, which decides between AlarmManager (`useAlarmManager = true`) or WorkManager.
   - Periodic schedules use `PeriodicWorkRequest`; exact schedules compute the next occurrence (optionally weekday-specific) and either enqueue a `OneTimeWorkRequest` or register an alarm.
2. When the job fires:
   - WorkManager runs `ScheduledDownloadWorker`, which loads the persisted config and calls `MobileDownloadManager.enqueue(...)`.
   - AlarmManager broadcasts to `DownloadAlarmReceiver`, which does the same via a lightweight coroutine scope.
3. All scheduled and immediate downloads share the same notification/foreground infrastructure added earlier.

## Config Persistence
- `DownloadConfigStore.save(...)` runs whenever a manager is created, storing chunking/retry/notification/scheduler/storage knobs as JSON.
- Scheduled components load via `DownloadConfigStore.load(...)`. If unavailable, they fall back to defaults, ensuring safety even if the store is cleared.

## Sample App Additions
- New button “Schedule Tuesday 00:30 Download” calls `downloadManager.schedule(...)` with `ScheduleTime(hour = 0, minute = 30, weekday = Weekday.TUESDAY)`.
- Another button “Schedule Exact Date” schedules a one-off run for tomorrow at 12:30 by passing year/month/day/hour/minute.
- The UI shows the computed schedule text so testers immediately see when the next run will occur.
- Existing status + storage preview remain intact, so the sample now covers immediate, scheduled, and diagnostic flows.

## Next Steps
- Extend the builder to accept per-request overrides (custom weekdays or one-off delays) via convenience helpers.
- Add UI elements for choosing day/time dynamically and listing scheduled jobs (backed by WorkManager queries).
- Combine schedules with grouping/constraint settings (Wi-Fi only, charging, etc.) for even finer control.

