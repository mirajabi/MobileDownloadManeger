# 13. Retry and resume

[All steps](README.md) · [Switch languages](index.html)

## Step 30. Two different retries

A dropped connection keeps the partial file. The state written for that wait is `retry_wait`, not pause. After reboot, that download continues from the last flushed offset. The delay starts at `initialDelayMillis` and multiplies by `backoffMultiplier` until `maxAttempts` is spent.

An integrity failure does not keep the partial file. The library cannot point at the corrupted range, so it deletes the file and the chunk list and starts at byte zero. That restart uses the same attempt counter.

Pause is neither of those. It is stored as paused, `onPaused` runs once, and WorkManager is not asked to retry it.

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    retryPolicy(
        maxAttempts = 5,
        initialDelayMillis = 2_000L,
        backoffMultiplier = 2f
    )
    integrityValidation(
        verifyFileSize = true,
        verifyChecksum = true,
        verifyApkStructure = true,
        verifyContentType = false,
        verifyApkSignature = false
    )
}
```

**Java**

```java
DownloadForegroundService.configureService(this, builder -> {
    builder.retryPolicy(5, 2_000L, 2f);
    builder.integrityValidation(true, true, true, false, false);
    return kotlin.Unit.INSTANCE;
});
```

`onRetry` receives the next attempt number. After the last attempt, `onFailed` runs and the recovery record is `failed`, which lets a later request for that path start clean.

## This section, filled in

| Cause | Partial file | Record | Callback |
|-------|--------------|--------|----------|
| Network error, attempts left | Kept | `retry_wait` | `onRetry` |
| Network error, attempts spent | Kept until you replace it | `failed` | `onFailed` |
| Checksum, size, or structure | Deleted | `running`, then `failed` if attempts are spent | `onRetry`, then `onFailed` |
| User pause | Kept | `paused` | `onPaused` |
| User stop | Checkpoint removed | gone | `onCancelled` |

Next: [what the host observes](CURRENT_RETRY_STATUS.md).
