# Service Configuration Guide

## Overview

`DownloadForegroundService` now loads its configuration from persistent storage instead of using hardcoded defaults. This allows you to configure the download behavior once and have it persist across app restarts and process deaths.

## Configuration Method

### Basic Setup

Before using the download service, you must configure it by calling `DownloadForegroundService.configureService()`:

```kotlin
DownloadForegroundService.configureService(context) {
    // Chunking configuration
    chunkCount(4)
    chunkParallel(true)
    chunkMinSize(256 * 1024L)  // 256 KB

    // Retry policy
    retryPolicy(
        maxAttempts = 5,
        initialDelayMillis = 3_000L,
        backoffMultiplier = 1.5f
    )

    // Notification configuration
    notificationChannel(
        id = "downloads",
        name = "Downloads",
        description = "Download notifications"
    )
    notificationShowProgress(true)
    notificationPersistent(true)

    // Storage configuration
    storageDestinations(listOf(DownloadDestination.Downloads))
    storageOverwrite(false)
    storageValidateFreeSpace(true)

    // Installer configuration
    installerPromptOnCompletion(true)
}
```

### When to Configure

You should call `configureService()` once during app initialization, typically:

1. **In Application.onCreate()** - Recommended for most cases
2. **In MainActivity.onCreate()** - Works for single-activity apps
3. **Before first download** - Minimum requirement

### Configuration Persistence

The configuration is automatically saved to `DownloadConfigStore` and will be:
- ✅ Used by the foreground service when it starts
- ✅ Restored after app restarts
- ✅ Available to scheduled downloads
- ✅ Persisted across process deaths

### Advanced Example

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        configureDownloadService()
    }

    private fun configureDownloadService() {
        val downloadPath = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: filesDir.absolutePath

        DownloadForegroundService.configureService(this) {
            // High-performance chunking
            chunkCount(8)
            chunkParallel(true)
            chunkMinSize(512 * 1024L)

            // Aggressive retry
            retryPolicy(
                maxAttempts = 10,
                initialDelayMillis = 2_000L,
                backoffMultiplier = 2.0f
            )

            // Custom notification
            notificationChannel(
                id = "app_downloads",
                name = "App Downloads",
                description = "Download progress and status"
            )
            notificationShowProgress(true)
            notificationPersistent(false)

            // Scheduled downloads
            periodicSchedule(intervalMinutes = 120)

            // Custom storage
            storageDestinations(listOf(
                DownloadDestination.Custom(downloadPath)
            ))
            storageOverwrite(true)
            storageValidateFreeSpace(true)

            // Auto-install APKs
            installerPromptOnCompletion(true)
        }
    }
}
```

### Error Handling

If the service starts without configuration, it will throw:

```
IllegalStateException: DownloadForegroundService requires configuration.
Call DownloadForegroundService.configureService() before starting the service.
```

### Updating Configuration

To update the configuration, simply call `configureService()` again with new settings. The new configuration will be used the next time the service starts.

```kotlin
// Update to use different storage location
DownloadForegroundService.configureService(context) {
    storageDestinations(listOf(DownloadDestination.Downloads))
    // ... other settings
}
```

## Migration from Hardcoded Configuration

If you were previously relying on the service's default configuration, you must now:

1. Call `configureService()` before starting any downloads
2. Provide all necessary configuration options
3. Remove any assumptions about default values

### Before (Old Way)

```kotlin
// Service had hardcoded defaults
DownloadForegroundService.enqueueDownload(context, request)
```

### After (New Way)

```kotlin
// Configure service first
DownloadForegroundService.configureService(context) {
    chunkCount(4)
    // ... other settings
}

// Then use it
DownloadForegroundService.enqueueDownload(context, request)
```

## Best Practices

1. **Configure Once**: Call `configureService()` once during app initialization
2. **Use Application Class**: For multi-activity apps, configure in `Application.onCreate()`
3. **Validate Paths**: Ensure storage paths exist and are writable
4. **Test Configuration**: Verify downloads work with your settings
5. **Document Settings**: Keep track of why you chose specific values

## Configuration Options Reference

### Chunking
- `chunkCount(n)` - Number of parallel chunks (1-16)
- `chunkParallel(boolean)` - Enable parallel downloading
- `chunkMinSize(bytes)` - Minimum chunk size (recommended: 256 KB - 1 MB)

### Retry Policy
- `retryPolicy(maxAttempts, initialDelayMillis, backoffMultiplier)`
- Typical values: 3-10 attempts, 1-5 second delay, 1.5-2.0 backoff

### Notification
- `notificationChannel(id, name, description)` - Required for Android 8+
- `notificationShowProgress(boolean)` - Show progress bar
- `notificationPersistent(boolean)` - Keep notification after download

### Storage
- `storageDestinations(list)` - Where to save files
- `storageOverwrite(boolean)` - Overwrite existing files
- `storageValidateFreeSpace(boolean)` - Check disk space before download

### Scheduler
- `periodicSchedule(intervalMinutes)` - Background sync interval
- For one-time scheduled downloads, use `DownloadForegroundService.scheduleDownload()`

### Installer
- `installerPromptOnCompletion(boolean)` - Auto-prompt to install APKs

## See Also

- [Configuration Documentation](01-configuration.md)
- [Storage Configuration](02-storage.md)
- [Scheduler Documentation](05-scheduler.md)
