# 6. Pause, resume, and stop

[All steps](README.md) · [Switch languages](index.html)

Pause writes the checkpoint first, then cancels the call. The pause record stays on disk until a later resume actually finishes or the user stops the download. Stop deletes the recovery record. Neither action is retried as a network failure.

The id is the `DownloadRequest.id` you chose, or the generated UUID if you left it empty. The notification buttons send the same three actions.

## Step 17. Pause

**Kotlin**

```kotlin
DownloadForegroundService.pauseDownload(this, "tms-app-release")
```

**Java**

```java
DownloadForegroundService.pauseDownload(this, "tms-app-release");
```

`onPaused` runs when this pause is still the current attempt. A resume that already replaced it does not get a late pause callback.

## Step 18. Resume and stop

Resume continues from the saved chunk offsets. If the strong ETag or the total size changed, resume discards the partial file and starts at byte zero. Stop removes the checkpoint. The bytes already written are not a resumable download after stop.

**Kotlin**

```kotlin
DownloadForegroundService.resumeDownload(this, "tms-app-release")
DownloadForegroundService.stopDownload(this, "tms-app-release")
```

**Java**

```java
DownloadForegroundService.resumeDownload(this, "tms-app-release");
DownloadForegroundService.stopDownload(this, "tms-app-release");
```

Calling resume for an id that is already running does not start a second writer. Calling enqueue again with the same id and the same artifact returns the active download, or resumes it when it is paused.

Resume also continues a download that already failed. The failed recovery record keeps the flushed bytes, and `resume` starts from that record. A brand-new enqueue of an abandoned id still replaces it and starts at byte zero. After a reboot, a failed download stays failed until something calls resume. Queued, running, and retry-waiting work still continues from the last checkpoint, and a manual pause stays paused.

## Step 19. This section, filled in

**Kotlin**

```kotlin
val id = "tms-app-release"

DownloadForegroundService.registerListener(object : DownloadListener {
    override fun onPaused(handle: DownloadHandle) {
        if (handle.id == id) pauseButton.isEnabled = false
    }
    override fun onResumed(handle: DownloadHandle) {
        if (handle.id == id) pauseButton.isEnabled = true
    }
    override fun onCancelled(handle: DownloadHandle) {
        if (handle.id == id) pauseButton.isEnabled = false
    }
})

pauseButton.setOnClickListener {
    DownloadForegroundService.pauseDownload(this, id)
}
resumeButton.setOnClickListener {
    DownloadForegroundService.resumeDownload(this, id)
}
stopButton.setOnClickListener {
    DownloadForegroundService.stopDownload(this, id)
}
```

**Java**

```java
final String id = "tms-app-release";

DownloadForegroundService.registerListener(new DownloadListener() {
    @Override public void onQueued(DownloadHandle handle) { }
    @Override public void onStarted(DownloadHandle handle) { }
    @Override public void onProgress(DownloadHandle handle, DownloadProgress progress) { }
    @Override public void onPaused(DownloadHandle handle) {
        if (id.equals(handle.getId())) pauseButton.setEnabled(false);
    }
    @Override public void onResumed(DownloadHandle handle) {
        if (id.equals(handle.getId())) pauseButton.setEnabled(true);
    }
    @Override public void onCompleted(DownloadHandle handle) { }
    @Override public void onFailed(DownloadHandle handle, Throwable error) { }
    @Override public void onRetry(DownloadHandle handle, int attempt) { }
    @Override public void onCancelled(DownloadHandle handle) {
        if (id.equals(handle.getId())) pauseButton.setEnabled(false);
    }
});

pauseButton.setOnClickListener(v -> DownloadForegroundService.pauseDownload(this, id));
resumeButton.setOnClickListener(v -> DownloadForegroundService.resumeDownload(this, id));
stopButton.setOnClickListener(v -> DownloadForegroundService.stopDownload(this, id));
```

Next: [the optional installer prompt](07-foreground-notify-installer.md).
