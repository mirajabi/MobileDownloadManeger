# 14. What the host observes

[All steps](README.md) · [Switch languages](index.html)

## Step 31. Handle the callbacks

The service does not throw these outcomes back to `enqueueDownload`. That call returns after the service is asked to start. Read the listener.

**Kotlin**

```kotlin
DownloadForegroundService.registerListener(object : DownloadListener {
    override fun onQueued(handle: DownloadHandle) {}
    override fun onStarted(handle: DownloadHandle) {}
    override fun onProgress(handle: DownloadHandle, progress: DownloadProgress) {}
    override fun onPaused(handle: DownloadHandle) {}
    override fun onResumed(handle: DownloadHandle) {}
    override fun onCompleted(handle: DownloadHandle) {
        // File is complete. Install it in the host if that is your job.
    }
    override fun onRetry(handle: DownloadHandle, attempt: Int) {}
    override fun onFailed(handle: DownloadHandle, error: Throwable?) {
        when (error) {
            is IllegalStateException -> { /* second writer rejected */ }
            is IllegalArgumentException -> { /* checksum shape rejected */ }
            else -> { /* network or integrity exhausted */ }
        }
    }
    override fun onCancelled(handle: DownloadHandle) {}
})
```

**Java**

```java
DownloadForegroundService.registerListener(new DownloadListener() {
    @Override public void onQueued(DownloadHandle handle) { }
    @Override public void onStarted(DownloadHandle handle) { }
    @Override public void onProgress(DownloadHandle handle, DownloadProgress progress) { }
    @Override public void onPaused(DownloadHandle handle) { }
    @Override public void onResumed(DownloadHandle handle) { }
    @Override public void onCompleted(DownloadHandle handle) { }
    @Override public void onRetry(DownloadHandle handle, int attempt) { }
    @Override public void onFailed(DownloadHandle handle, Throwable error) {
        if (error instanceof IllegalStateException) {
            // Second writer rejected.
        } else if (error instanceof IllegalArgumentException) {
            // Checksum shape rejected.
        }
    }
    @Override public void onCancelled(DownloadHandle handle) { }
});
```

## This section, filled in

A finished guide does four things, in this order:

1. [Step 4](01-configuration.md) saves the full config.
2. [Step 10](03-chunk-engine.md) enqueues one request with a checksum.
3. [Step 19](06-pause-resume.md) wires pause, resume, and stop.
4. This listener treats `onCompleted` as the only signal that the file is ready to extract or install.

`onCompleted` is not raised for a pause, a stop, or a rejected second writer.
