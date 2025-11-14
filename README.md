# Mobile Download Manager

Modular Android download manager targeting API 23+ with support for chunked transfers, background scheduling, notification progress, and configurable storage policies. The project is built incrementally—each stage introduces new capabilities alongside matching documentation and sample-app demos.

## Modules
- `downloader`: reusable library that exposes `MobileDownloadManager`, configuration DSL, and (later) the execution engine.
- `app`: sample client that exercises every stage of the library. The UI evolves in lockstep with the feature set so manual tests stay straightforward.

## Documentation
- See `docs/README.md` for the current table of contents.  
- Stage 1 (`docs/01-configuration.md`) explains the configuration DSL and how the sample activity uses it to preview chunking, retry, scheduler, notification, and storage settings.  
- Stage 2 (`docs/02-storage.md`) covers the storage resolver, overwrite policy, free-space validation, and the sample dry-run preview.
- Stage 3 (`docs/03-chunk-engine.md`) describes the chunked downloader, retry/backoff flow, and the sample UI’s live status updates.
- Stage 4 (`docs/04-foreground.md`) details the foreground service, persistent notifications, and how the sample button now mirrors those events.
- Stage 5 (`docs/05-scheduler.md`) explains WorkManager/AlarmManager scheduling, persisted config, and the sample’s Tuesday 00:30 scheduling demo.
- Stage 6 (`docs/06-pause-resume.md`) introduces resumable downloads, session tracking, and the sample’s Pause/Resume controls.
- Stage 7 (`docs/07-foreground-notify-installer.md`) unifies the notification, enables true parallel downloads, requests storage permissions, and adds the optional post-download installer prompt.

## Development Workflow
1. Implement a feature in the library.
2. Mirror the change in the sample module with the simplest possible UI or instrumentation hook.
3. Capture the behavior and usage notes inside the `docs/` folder and link it here.

> Gradle Wrapper 6.7.1 + Android Gradle Plugin 4.1.2 are enforced because the target environment requires them.

## Continuous Integration
GitHub Actions (`.github/workflows/ci.yml`) runs `./gradlew assemble` on every push/PR with JDK 11 (Temurin). This mirrors the JitPack environment and guarantees the sample + library remain compatible with the requested Gradle/AGP versions.

## JitPack Consumption
Add the JitPack repository once:

```kotlin
repositories {
    maven("https://jitpack.io")
}
```

Then pull whichever tag you want (example: `v1.0.0`):

```kotlin
dependencies {
    implementation("com.github.mirajabi:MobileDownloadManeger:v1.0.0")
}
```

The same coordinate works for Groovy Gradle scripts:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.mirajabi:MobileDownloadManeger:v1.0.0'
}
```

## Kotlin Usage Example
```kotlin
private fun enqueueSampleDownload() {
    val request = DownloadRequest(
        url = SAMPLE_URL,
        fileName = "sample-${System.currentTimeMillis()}.apk",
        destination = DownloadDestination.Custom(defaultDownloadPath())
    )

    DownloadForegroundService.setNotificationIcon(R.mipmap.ic_launcher)
    DownloadForegroundService.enqueueDownload(this, request)
}

private fun defaultDownloadPath(): String {
    val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return (publicDir ?: File(filesDir, "Download")).apply { mkdirs() }.absolutePath
}
```

## Java Usage Example
```java
private void enqueueSampleDownload() {
    DownloadRequest request = new DownloadRequest(
            SAMPLE_URL,
            "sample-" + System.currentTimeMillis() + ".apk",
            new DownloadDestination.Custom(defaultDownloadPath()),
            UUID.randomUUID().toString(),
            Collections.emptyMap()
    );

    DownloadForegroundService.setNotificationIcon(R.mipmap.ic_launcher);
    DownloadForegroundService.enqueueDownload(this, request);
}

private String defaultDownloadPath() {
    File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    File dir = publicDir != null ? publicDir : new File(getFilesDir(), "Download");
    if (!dir.exists()) dir.mkdirs();
    return dir.getAbsolutePath();
}
```

