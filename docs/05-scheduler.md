# 5. Schedule

[All steps](README.md) · [Switch languages](index.html)

Choose one mode. `exactSchedule` and `exactScheduleDate` clear a periodic interval. `periodicSchedule` clears an exact time.

Exact schedules use WorkManager unless `schedulerUseAlarmManager(true)`. Alarm exact times require the exact-alarm permission on newer Android versions. If that permission is missing, the scheduler falls back to an inexact alarm. The library does not declare `SCHEDULE_EXACT_ALARM`; add it in the host only if you opt into AlarmManager.

A periodic interval below 15 minutes is raised to 15 and a warning is logged. WorkManager periodic work also waits for a network connection.

`scheduleDownload` stores the request, including checksum and headers, and arms the trigger. It does not download immediately.

## Step 13. Weekday

`weekday = null` means the next matching clock time, every day. Hour is 0–23.

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    exactSchedule(
        hour = 2,
        minute = 15,
        weekday = Weekday.TUESDAY,
        allowWhileIdle = true
    )
    schedulerUseAlarmManager(false)
}

val request = DownloadRequest(
    url = "https://downloads.example.com/tms/app-release.apk",
    fileName = "app-release.apk",
    id = "tms-tuesday"
)

DownloadForegroundService.scheduleDownload(
    this,
    request,
    ScheduleTime(hour = 2, minute = 15, weekday = Weekday.TUESDAY)
)
```

**Java**

```java
DownloadForegroundService.configureService(this, builder -> {
    builder.exactSchedule(2, 15, Weekday.TUESDAY, true);
    builder.schedulerUseAlarmManager(false);
    return kotlin.Unit.INSTANCE;
});

DownloadRequest request = new DownloadRequest(
        "https://downloads.example.com/tms/app-release.apk",
        "app-release.apk",
        DownloadDestination.Auto.INSTANCE,
        "tms-tuesday",
        Collections.emptyMap(),
        null,
        ChecksumAlgorithm.SHA256
);

DownloadForegroundService.scheduleDownload(
        this,
        request,
        new ScheduleTime(2, 15, Weekday.TUESDAY, null, null, null)
);
```

## Step 14. Calendar date

Month is 1–12. This is a one-shot date, not a weekday.

**Kotlin**

```kotlin
val whenToRun = ScheduleTime(
    hour = 12,
    minute = 30,
    weekday = null,
    year = 2026,
    month = 10,
    dayOfMonth = 9
)

DownloadForegroundService.configureService(this) {
    exactScheduleDate(
        year = 2026,
        month = 10,
        dayOfMonth = 9,
        hour = 12,
        minute = 30,
        allowWhileIdle = true
    )
}

DownloadForegroundService.scheduleDownload(this, request, whenToRun)
```

**Java**

```java
DownloadForegroundService.configureService(this, builder -> {
    builder.exactScheduleDate(2026, 10, 9, 12, 30, true);
    return kotlin.Unit.INSTANCE;
});

ScheduleTime whenToRun = new ScheduleTime(12, 30, null, 2026, 10, 9);
DownloadForegroundService.scheduleDownload(this, request, whenToRun);
```

## Step 15. Periodic interval

`scheduleDownload` always takes a `ScheduleTime`, so it is the exact-time API. A repeating job is `MobileDownloadManager.schedule(request, null)` after `periodicSchedule` is set and no exact time is set. An interval below 15 minutes is stored as requested and raised to 15 when the work is enqueued.

**Kotlin**

```kotlin
val manager = MobileDownloadManager.create(this) {
    periodicSchedule(intervalMinutes = 60)
    schedulerUseAlarmManager(false)
}

manager.schedule(request)
```

**Java**

```java
MobileDownloadManager manager = MobileDownloadManager.builder(this)
        .periodicSchedule(60)
        .schedulerUseAlarmManager(false)
        .build();

manager.schedule(request, null);
```

## Step 16. This section, filled in

AlarmManager variant of the Tuesday schedule. `useAlarmManager` does not apply to periodic WorkManager jobs.

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    exactSchedule(hour = 2, minute = 15, weekday = Weekday.TUESDAY, allowWhileIdle = true)
    schedulerUseAlarmManager(true)
}

val request = DownloadRequest(
    url = "https://downloads.example.com/tms/app-release.apk",
    fileName = "app-release.apk",
    destination = DownloadDestination.Auto,
    id = "tms-tuesday",
    headers = emptyMap(),
    expectedChecksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    checksumAlgorithm = ChecksumAlgorithm.SHA256
)

DownloadForegroundService.scheduleDownload(
    this,
    request,
    ScheduleTime(
        hour = 2,
        minute = 15,
        weekday = Weekday.TUESDAY,
        year = null,
        month = null,
        dayOfMonth = null
    )
)
```

**Java**

```java
DownloadForegroundService.configureService(this, builder -> {
    builder.exactSchedule(2, 15, Weekday.TUESDAY, true);
    builder.schedulerUseAlarmManager(true);
    return kotlin.Unit.INSTANCE;
});

Map<String, String> headers = Collections.emptyMap();
DownloadRequest request = new DownloadRequest(
        "https://downloads.example.com/tms/app-release.apk",
        "app-release.apk",
        DownloadDestination.Auto.INSTANCE,
        "tms-tuesday",
        headers,
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        ChecksumAlgorithm.SHA256
);

DownloadForegroundService.scheduleDownload(
        this,
        request,
        new ScheduleTime(2, 15, Weekday.TUESDAY, null, null, null)
);
```

Next: [pause, resume, and stop](06-pause-resume.md).
