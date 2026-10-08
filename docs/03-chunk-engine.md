# 3. Download

[All steps](README.md) · [Switch languages](index.html)

`chunkCount` is how many ranges are planned when the server returns a size and accepts `Range`. The planner keeps each piece at least `chunkMinSize`. A file smaller than that size, or a response that cannot be ranged, uses one stream. `chunkParallel(false)` still plans the pieces, then transfers them one at a time.

Bytes are checkpointed every 256 KB. A finite range is complete only when the bytes received equal `end - start + 1`. A short body retries that range. It is not marked complete.

## Step 8. Build the request, including optional fields

`url` and `fileName` are required. The rest have defaults.

| Field | Required | Default | Rule |
|-------|----------|---------|------|
| `url` | yes | — | HTTP or HTTPS |
| `fileName` | yes | — | File name only. The directory comes from storage config or `destination` |
| `destination` | no | `Auto` | `Auto`, `Custom`, or `Scoped` |
| `id` | no | random UUID | Same id and same artifact resumes. Same id and a different URL, file name, or checksum is rejected while the first is healthy |
| `headers` | no | empty | Sent on every request. Put `Cookie` here. The library owns `Range` |
| `expectedChecksum` | no | null | Hex. SHA-256 is 64 chars, SHA-512 is 128, MD5 is 32. Blank means omitted. A byte window checksums the saved window |
| `checksumAlgorithm` | no | `SHA256` | An unknown name fails the request when a checksum is present |
| `rangeStart` | no | null | First remote byte to keep, inclusive. Null starts at byte zero |
| `rangeEndInclusive` | no | null | Last remote byte to keep, inclusive. Null reads through the end of the file |

`@JvmOverloads` keeps the older Java constructor. Pass `null` for a side of the window you want left open. The saved file contains only that window, written from local byte zero. Parallel connections are `chunkCount` on the service config, not a field on the request.

**Kotlin**

```kotlin
val headers = mapOf(
    "Authorization" to "Bearer token",
    "Accept" to "application/vnd.android.package-archive"
)

val request = DownloadRequest(
    url = "https://downloads.example.com/tms/app-release.apk",
    fileName = "app-release.apk",
    destination = DownloadDestination.Auto,
    id = "tms-app-release",
    headers = headers,
    expectedChecksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    checksumAlgorithm = ChecksumAlgorithm.SHA256
)
```

**Java**

```java
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Bearer token");
headers.put("Accept", "application/vnd.android.package-archive");

DownloadRequest request = new DownloadRequest(
        "https://downloads.example.com/tms/app-release.apk",
        "app-release.apk",
        DownloadDestination.Auto.INSTANCE,
        "tms-app-release",
        headers,
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        ChecksumAlgorithm.SHA256
);
```

Omit the checksum by passing `expectedChecksum = null` in Kotlin, or `null` as the sixth Java argument. Do not invent a hash.

## Step 9. Enqueue and listen

`enqueueDownload` starts the foreground service and returns immediately. Keep the `id` you put on the request; pause, resume, and stop use that id.

Register the listener on the service. A listener added only inside `configureService` is stored on a temporary manager and is not the one the running service calls. `registerListener` is the process-local callback.

**Kotlin**

```kotlin
DownloadForegroundService.registerListener(object : DownloadListener {
    override fun onQueued(handle: DownloadHandle) {}
    override fun onStarted(handle: DownloadHandle) {}
    override fun onProgress(handle: DownloadHandle, progress: DownloadProgress) {
        val percent = progress.percent ?: 0
        Log.i("MDM", "$percent% ${progress.bytesPerSecond ?: 0} B/s")
    }
    override fun onPaused(handle: DownloadHandle) {}
    override fun onResumed(handle: DownloadHandle) {}
    override fun onCompleted(handle: DownloadHandle) {}
    override fun onFailed(handle: DownloadHandle, error: Throwable?) {
        Log.e("MDM", error?.message ?: "failed")
    }
    override fun onRetry(handle: DownloadHandle, attempt: Int) {}
    override fun onCancelled(handle: DownloadHandle) {}
})

DownloadForegroundService.enqueueDownload(this, request)
```

**Java**

```java
DownloadForegroundService.registerListener(new DownloadListener() {
    @Override public void onQueued(DownloadHandle handle) { }
    @Override public void onStarted(DownloadHandle handle) { }
    @Override public void onProgress(DownloadHandle handle, DownloadProgress progress) {
        int percent = progress.getPercent() == null ? 0 : progress.getPercent();
        long speed = progress.getBytesPerSecond() == null ? 0L : progress.getBytesPerSecond();
        Log.i("MDM", percent + "% " + speed + " B/s");
    }
    @Override public void onPaused(DownloadHandle handle) { }
    @Override public void onResumed(DownloadHandle handle) { }
    @Override public void onCompleted(DownloadHandle handle) { }
    @Override public void onFailed(DownloadHandle handle, Throwable error) {
        Log.e("MDM", error == null ? "failed" : String.valueOf(error.getMessage()));
    }
    @Override public void onRetry(DownloadHandle handle, int attempt) { }
    @Override public void onCancelled(DownloadHandle handle) { }
});

DownloadForegroundService.enqueueDownload(this, request);
```

Kotlin may leave a method out. The interface defaults cover it. Java implements every method; an empty body is enough for the ones you ignore.

Progress notifications are coalesced to about one update per second. `onProgress` follows that cadence. Completion, failure, pause, and cancel are not delayed.

## Step 10. This section, filled in

**Kotlin**

```kotlin
val request = DownloadRequest(
    url = "https://downloads.example.com/tms/app-release.apk",
    fileName = "app-release.apk",
    destination = DownloadDestination.Scoped("tms/packages"),
    id = "tms-app-release",
    headers = mapOf("Authorization" to "Bearer token"),
    expectedChecksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    checksumAlgorithm = ChecksumAlgorithm.SHA256
)

DownloadForegroundService.registerListener(object : DownloadListener {
    override fun onCompleted(handle: DownloadHandle) {
        Log.i("MDM", "ready ${handle.id}")
    }
    override fun onFailed(handle: DownloadHandle, error: Throwable?) {
        Log.e("MDM", "failed ${handle.id}", error)
    }
})

DownloadForegroundService.enqueueDownload(this, request)
```

**Java**

```java
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Bearer token");

DownloadRequest request = new DownloadRequest(
        "https://downloads.example.com/tms/app-release.apk",
        "app-release.apk",
        new DownloadDestination.Scoped("tms/packages"),
        "tms-app-release",
        headers,
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        ChecksumAlgorithm.SHA256
);

DownloadForegroundService.registerListener(new DownloadListener() {
    @Override public void onQueued(DownloadHandle handle) { }
    @Override public void onStarted(DownloadHandle handle) { }
    @Override public void onProgress(DownloadHandle handle, DownloadProgress progress) { }
    @Override public void onPaused(DownloadHandle handle) { }
    @Override public void onResumed(DownloadHandle handle) { }
    @Override public void onCompleted(DownloadHandle handle) {
        Log.i("MDM", "ready " + handle.getId());
    }
    @Override public void onFailed(DownloadHandle handle, Throwable error) {
        Log.e("MDM", "failed " + handle.getId(), error);
    }
    @Override public void onRetry(DownloadHandle handle, int attempt) { }
    @Override public void onCancelled(DownloadHandle handle) { }
});

DownloadForegroundService.enqueueDownload(this, request);
```

Next: [the foreground notification](04-foreground.md).
