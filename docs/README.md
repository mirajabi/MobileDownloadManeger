# Guide

Each step shows Kotlin and Java. Each page ends with that page filled in, including every optional value.

Open the [guide](https://mirajabi.github.io/MobileDownloadManeger/) for the Fetch course. It walks the sample source from the manifest to the home-screen widget, then the library reference below it switches between Kotlin and Java with one control.

| Step | Page | What you fill in |
|------|------|------------------|
| 1 | [Setup](01-configuration.md) | JitPack coordinate |
| 2 | [Setup](01-configuration.md) | One-time `configureService` call |
| 3 | [Setup](01-configuration.md) | Every optional builder method |
| 4 | [Setup](01-configuration.md) | Complete configuration |
| 5 | [Storage](02-storage.md) | `Auto`, `Custom`, and `Scoped` |
| 6 | [Storage](02-storage.md) | Overwrite, free space, public `Download/` |
| 7 | [Storage](02-storage.md) | Complete storage setup |
| 8 | [Download](03-chunk-engine.md) | `DownloadRequest`, including optional fields |
| 9 | [Download](03-chunk-engine.md) | Enqueue and the listener |
| 10 | [Download](03-chunk-engine.md) | Complete enqueue |
| 11 | [Notification](04-foreground.md) | Channel, icon, progress, persistent flag |
| 12 | [Notification](04-foreground.md) | Complete notification setup |
| 13 | [Schedule](05-scheduler.md) | Weekday |
| 14 | [Schedule](05-scheduler.md) | Calendar date |
| 15 | [Schedule](05-scheduler.md) | Periodic interval |
| 16 | [Schedule](05-scheduler.md) | AlarmManager or WorkManager, then the complete call |
| 17 | [Pause and resume](06-pause-resume.md) | Pause |
| 18 | [Pause and resume](06-pause-resume.md) | Resume and stop |
| 19 | [Pause and resume](06-pause-resume.md) | Complete controls |
| 20 | [Installer](07-foreground-notify-installer.md) | Prompt and MIME type |
| 21 | [Installer](07-foreground-notify-installer.md) | Complete installer setup |
| 22 | [Service behavior](08-service-configuration.md) | Saved configuration and defaults |
| 23 | [Service behavior](08-service-configuration.md) | Reboot |
| 24 | [Service behavior](08-service-configuration.md) | Range, ETag, and checksum |
| 25 | [Service behavior](08-service-configuration.md) | Two requests, one file |
| 26 | [Integrity](APK_INTEGRITY_GUIDE.md) | All five integrity flags |
| 27 | [APK structure](APK_STRUCTURE_VALIDATION.md) | `verifyApkStructure` |
| 28 | [APK signature](APK_SIGNATURE_VALIDATION.md) | `verifyApkSignature` |
| 29 | [Checksum](CHECKSUM_RETRY_BEST_PRACTICES.md) | Algorithm, hex length, mismatch |
| 30 | [Retry](RETRY_RESUME_BEHAVIOR.md) | Network retry versus integrity restart |
| 31 | [Outcomes](CURRENT_RETRY_STATUS.md) | What the host observes |
