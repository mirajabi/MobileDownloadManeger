# 1. Setup

[All steps](README.md) · [Switch languages](index.html)

Call `configureService` once, from `Application` or the first screen, before `enqueueDownload`. That call writes `DownloadConfig` to disk. The foreground service reads it on the next start. Listeners are not written to disk; register them again each process.

## Step 1. Add the library

**Kotlin**

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.mirajabi:MobileDownloadManeger:v1.3.7")
}
```

**Java**

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.mirajabi:MobileDownloadManeger:v1.3.7'
}
```

## Step 2. Configure the service once

The library manifest merges the service, both receivers, and the `FileProvider` (`${applicationId}.downloader.provider`). A host that targets API 34 or higher still needs `FOREGROUND_SERVICE_DATA_SYNC` in its own manifest if the merger does not already satisfy the target. The library declares it.

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    chunkCount(4)
}
```

**Java**

```java
DownloadForegroundService.configureService(this, builder -> {
    builder.chunkCount(4);
    return kotlin.Unit.INSTANCE;
});
```

## Step 3. Fill every optional setting

Defaults are in the comment on the right. Java has no default arguments on these methods, so pass every parameter. `periodicSchedule` and `exactSchedule` clear each other. Pick one. The block below uses a weekday schedule and leaves the periodic call commented.

`notificationIcon` is stored with the config and used for the download notification. `setNotificationIcon` is the icon for the short startup notification that runs before the saved config is applied. Set both to the same drawable.

`addListener` on the builder belongs to a `MobileDownloadManager` you keep yourself. `configureService` does not keep that object. Callbacks from the foreground service come from `registerListener`.

**Kotlin**

```kotlin
val listener = object : DownloadListener {
    override fun onCompleted(handle: DownloadHandle) {
        Log.i("MDM", "completed ${handle.id}")
    }
    override fun onFailed(handle: DownloadHandle, error: Throwable?) {
        Log.e("MDM", "failed ${handle.id}", error)
    }
}

val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    ?: filesDir

DownloadForegroundService.setNotificationIcon(R.drawable.ic_stat_download)
DownloadForegroundService.configureService(this) {
    chunkCount(4)                         // default 3, minimum 1
    chunkParallel(true)                   // default true
    chunkMinSize(256 * 1024L)             // default 512 KB, minimum 64 KB

    retryPolicy(
        maxAttempts = 5,                  // default 3
        initialDelayMillis = 3_000L,     // default 2_000
        backoffMultiplier = 1.5f          // default 2
    )

    enforceForeground(true)               // default true

    notificationChannel(
        id = "tms_downloads",             // default "mobile_downloader"
        name = "TMS downloads",           // default "Mobile Downloader"
        description = "Package downloads" // default "Background downloads"
    )
    notificationIcon(R.drawable.ic_stat_download) // default null, then the system download icon
    notificationShowProgress(true)        // default true
    notificationPersistent(true)          // default true

    exactSchedule(
        hour = 2,
        minute = 15,
        weekday = Weekday.TUESDAY,        // null means every day
        allowWhileIdle = true             // default true
    )
    // periodicSchedule(60)               // alternative; minimum applied interval is 15
    schedulerUseAlarmManager(false)       // default false, WorkManager

    storageDestinations(listOf(DownloadDestination.Custom(downloadDir.absolutePath)))
    storageOverwrite(true)                // default true
    storageValidateFreeSpace(
        validate = true,                  // default true
        minBytes = 32L * 1024 * 1024      // default 10 MB
    )
    storageUsePublicDownloads(false)      // default false

    installerPromptOnCompletion(
        enabled = false,                  // default false
        fallbackMimeType = "application/vnd.android.package-archive"
    )

    integrityValidation(
        verifyFileSize = true,            // default true
        verifyChecksum = true,            // default true, runs only when a checksum is set
        verifyApkStructure = true,        // default true, only .apk and .apks
        verifyContentType = false,        // default false
        verifyApkSignature = false        // default false
    )

}

DownloadForegroundService.registerListener(listener)
```

**Java**

```java
DownloadListener listener = new DownloadListener() {
    @Override public void onQueued(DownloadHandle handle) { }
    @Override public void onStarted(DownloadHandle handle) { }
    @Override public void onProgress(DownloadHandle handle, DownloadProgress progress) { }
    @Override public void onPaused(DownloadHandle handle) { }
    @Override public void onResumed(DownloadHandle handle) { }
    @Override public void onCompleted(DownloadHandle handle) {
        Log.i("MDM", "completed " + handle.getId());
    }
    @Override public void onFailed(DownloadHandle handle, Throwable error) {
        Log.e("MDM", "failed " + handle.getId(), error);
    }
    @Override public void onRetry(DownloadHandle handle, int attempt) { }
    @Override public void onCancelled(DownloadHandle handle) { }
};

File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
if (downloadDir == null) {
    downloadDir = getFilesDir();
}

DownloadForegroundService.setNotificationIcon(R.drawable.ic_stat_download);
File finalDownloadDir = downloadDir;
DownloadForegroundService.configureService(this, builder -> {
    builder.chunkCount(4);
    builder.chunkParallel(true);
    builder.chunkMinSize(256 * 1024L);
    builder.retryPolicy(5, 3_000L, 1.5f);
    builder.enforceForeground(true);
    builder.notificationChannel("tms_downloads", "TMS downloads", "Package downloads");
    builder.notificationIcon(R.drawable.ic_stat_download);
    builder.notificationShowProgress(true);
    builder.notificationPersistent(true);
    builder.exactSchedule(2, 15, Weekday.TUESDAY, true);
    builder.schedulerUseAlarmManager(false);
    builder.storageDestinations(Collections.singletonList(
            new DownloadDestination.Custom(finalDownloadDir.getAbsolutePath())
    ));
    builder.storageOverwrite(true);
    builder.storageValidateFreeSpace(true, 32L * 1024 * 1024);
    builder.storageUsePublicDownloads(false);
    builder.installerPromptOnCompletion(false, "application/vnd.android.package-archive");
    builder.integrityValidation(true, true, true, false, false);
    return kotlin.Unit.INSTANCE;
});
DownloadForegroundService.registerListener(listener);
```

## Step 4. This section, filled in

Step 3 is the filled configuration. Nothing else on this page is required. A host that skips `configureService` still downloads: the service loads defaults and logs that the saved file was missing.

Next: [where the file is written](02-storage.md).
