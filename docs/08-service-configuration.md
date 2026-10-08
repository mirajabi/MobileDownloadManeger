# 8. Service behavior

[All steps](README.md) · [Switch languages](index.html)

`configureService` replaces the saved config and restores the previous in-memory manager. It does not cancel a download that is already running. The next process start reads the new file.

## Step 22. Saved configuration and defaults

These fields are written: chunking, retry, foreground flag, notification channel and icon, one schedule mode, storage directories, overwrite, free space, public `Download/`, installer prompt and MIME type, and all five integrity flags.

If the file is missing or the JSON cannot be parsed, the service logs a warning and uses `DownloadConfig()` defaults. It does not throw from `onCreate`.

You do not call a load method. `configureService` is the write. The service is the read.

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    chunkCount(4)
    retryPolicy(maxAttempts = 5, initialDelayMillis = 2_000L, backoffMultiplier = 2f)
    notificationChannel("tms_downloads", "TMS downloads", "Package downloads")
    notificationIcon(R.drawable.ic_stat_download)
    storageOverwrite(true)
    storageValidateFreeSpace(true, 32L * 1024 * 1024)
    storageUsePublicDownloads(false)
    installerPromptOnCompletion(false, "application/vnd.android.package-archive")
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
    builder.chunkCount(4);
    builder.retryPolicy(5, 2_000L, 2f);
    builder.notificationChannel("tms_downloads", "TMS downloads", "Package downloads");
    builder.notificationIcon(R.drawable.ic_stat_download);
    builder.storageOverwrite(true);
    builder.storageValidateFreeSpace(true, 32L * 1024 * 1024);
    builder.storageUsePublicDownloads(false);
    builder.installerPromptOnCompletion(false, "application/vnd.android.package-archive");
    builder.integrityValidation(true, true, true, false, false);
    return kotlin.Unit.INSTANCE;
});
```

## Step 23. Reboot

| State when the process died | After the app may run again |
|-----------------------------|-----------------------------|
| Queued, running, or waiting to retry | Continues from the last flushed checkpoint |
| Paused by the user | Stays paused until `resumeDownload` |
| Failed or cancelled | Stays stopped. A new request may replace it |
| Completed | The recovery record is already gone |

WorkManager unique work `mdm-active-recovery` starts `DownloadForegroundService` when a network is connected. There is no `BOOT_COMPLETED` receiver. A force-stop keeps the records on disk and does not run them until Android allows the app to run again. Opening the app, or WorkManager's next allowed run, is enough. You can also call `recoverDownloads` once the process is allowed to start a foreground service.

**Kotlin**

```kotlin
DownloadForegroundService.recoverDownloads(this)
```

**Java**

```java
DownloadForegroundService.recoverDownloads(this);
```

Do not call `recoverDownloads` to unpause a manual pause. That state is not in the auto-resume set.

## Step 24. Range, validators, and checksum

| Response | What is written |
|----------|-----------------|
| 206 and `Content-Range` matches the requested start, end, and known total | Append at that offset |
| 200, or a 206 whose range does not match, at a non-zero offset | Partial file deleted, one full GET from byte zero |
| 416 and the local length already equals the known total | No body is written. The checksum still runs |
| Strong ETag changed, or total size changed | Restart from byte zero |
| No validator, or only a weak ETag (`W/...`) | Keep the partial bytes |
| Checksum or integrity failure | File deleted, retry from byte zero while attempts remain |
| Checksum hex length does not match the algorithm | `onFailed`. Nothing is downloaded |
| Checksum omitted or blank | No hash is invented. The other integrity checks still run |

Ranged requests send `Accept-Encoding: identity` and `If-Range` (strong ETag, otherwise `Last-Modified`).

## Step 25. Two requests, one file

Compared fields are URL, file name, checksum, and algorithm. The canonical path is the resolved file.

| First download | Second request |
|----------------|----------------|
| Queued, running, waiting to retry, or paused, same artifact | Existing download continues. `onFailed` is not used for a same-id replay; the same handle is reused or the pause is resumed |
| Healthy, different id or a different artifact on that path | `onFailed` with `IllegalStateException`. The file is not touched |
| Failed or cancelled | The new request starts at byte zero and may overwrite |
| Same id, conflicting artifact, still healthy | Rejected |

Enqueue does not throw that rejection out of the service. Handle it in `onFailed`.

## This section, filled in

Steps 22 through 25 are the service contract. The host code is step 22 plus the request from [step 10](03-chunk-engine.md). No extra API turns those rules on. They are `v1.3.3`.

Next: [integrity flags](APK_INTEGRITY_GUIDE.md).
