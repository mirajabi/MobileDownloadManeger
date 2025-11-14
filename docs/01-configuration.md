# 1. Configuration & Builder

## Overview
- Establishes a single `DownloadConfig` that aggregates chunking, retries, notifications, scheduling, storage, and listener hooks.
- Introduces `DownloadManagerBuilder`, a fluent DSL used by host apps (and the sample module) to assemble complex setups without verbose boilerplate.
- Defines callback contracts (`DownloadListener`) and status models so downstream consumers can react to queue changes consistently.

## Builder Quick Start
```kotlin
val manager = MobileDownloadManager.create(context) {
    chunkCount(4)
    chunkParallel(true)
    retryPolicy(maxAttempts = 5, initialDelayMillis = 3_000L, backoffMultiplier = 1.5f)
    notificationChannel("sample_downloads", "Sample Downloads", "Foreground sample channel")
    periodicSchedule(intervalMinutes = 60)
    storageDestinations(listOf(DownloadDestination.Auto))
    addListener(object : DownloadListener {
        override fun onQueued(handle: DownloadHandle) { /* update UI */ }
    })
}
```

## Key Structures
- `DownloadConfig`: immutable snapshot holding every knob.
- `ChunkingConfig`: chunk count, minimum size, and whether parallel execution is preferred.
- `RetryPolicy`: attempts, initial delay, and multiplier (currently exponential-style).
- `NotificationConfig`: channel metadata, icon, and progress/ongoing preferences.
- `SchedulerConfig` & `ScheduleTime`: describe either periodic WorkManager jobs or exact AlarmManager triggers.
- `StorageConfig`: controls download destinations, overwrite behavior, and free-space validation.
- `DownloadListener`: lifecycle callbacks (`onQueued`, `onStarted`, `onProgress`, `onCompleted`, `onFailed`, `onRetry`, `onCancelled`).

## Sample Module Hook
File `app/src/main/java/com/miaadrajabi/mobiledownloadmaneger/MainActivity.kt` uses the builder to:
1. Create a demo listener that logs when requests enter the queue.
2. Configure chunking, retries, notifications, scheduling, and storage using the DSL.
3. Display the resulting configuration snapshot inside the activity UI for quick verification.

As future stages (storage, networking, scheduling, etc.) are implemented, additional documents will extend this folder and reference the relevant code paths.

