# 4. Notification

[All steps](README.md) · [Switch languages](index.html)

The service calls `startForeground` before it reads the saved configuration or opens a socket. That keeps `startForegroundService` from crashing the host when the first progress event has not arrived yet.

One notification, id `7001`, shows the active transfer: bytes, speed, and Pause, Resume, and Stop. Those buttons call the running manager first. If the process was recreated, they start the service with the same action. Completion stays visible; stopping the service detaches the foreground state and leaves the result notification.

## Step 11. Channel, icon, progress, and the ongoing flag

Use a white status-bar icon in `res/drawable`. `notificationPersistent(true)` keeps the notification ongoing while bytes are moving.

**Kotlin**

```kotlin
DownloadForegroundService.setNotificationIcon(R.drawable.ic_stat_download)
DownloadForegroundService.configureService(this) {
    notificationChannel(
        id = "tms_downloads",
        name = "TMS downloads",
        description = "Package downloads"
    )
    notificationIcon(R.drawable.ic_stat_download)
    notificationShowProgress(true)
    notificationPersistent(true)
    enforceForeground(true)
}
```

**Java**

```java
DownloadForegroundService.setNotificationIcon(R.drawable.ic_stat_download);
DownloadForegroundService.configureService(this, builder -> {
    builder.notificationChannel("tms_downloads", "TMS downloads", "Package downloads");
    builder.notificationIcon(R.drawable.ic_stat_download);
    builder.notificationShowProgress(true);
    builder.notificationPersistent(true);
    builder.enforceForeground(true);
    return kotlin.Unit.INSTANCE;
});
```

If Android blocks a background start (`ForegroundServiceStartNotAllowedException` on newer OS versions), the scheduled worker finishes that one download itself and then drops the temporary manager. The previous manager is restored.

## Step 12. This section, filled in

Step 11 is the filled notification setup. Pair it with a request from [step 10](03-chunk-engine.md). The host does not build its own `Notification`.

Next: [scheduling](05-scheduler.md).
