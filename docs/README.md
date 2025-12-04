# Documentation Index

## Core Features

| # | Topic | Summary |
|---|-------|---------|
| 1 | [Configuration & Builder](01-configuration.md) | How to customize chunking, retries, notifications, scheduler, storage, and listeners using the fluent DSL. |
| 2 | [Storage Resolver](02-storage.md) | Directory selection, overwrite policy, free-space validation, and sample-app previews. |
| 3 | [Chunked Download Engine](03-chunk-engine.md) | Range-aware HTTP downloads, retry/backoff, listener callbacks, and sample UI controls. |
| 4 | [Foreground Service & Notifications](04-foreground.md) | Persistent service, per-download notifications, and sample status wiring. |
| 5 | [Scheduler Integration](05-scheduler.md) | WorkManager periodic jobs, AlarmManager exact triggers, config persistence, and sample scheduling UI. |
| 6 | [Pause/Resume](06-pause-resume.md) | Session tracking, resumable ranges, and sample pause/resume controls. |
| 7 | [Notifications, Parallelism & Installer](07-foreground-notify-installer.md) | Unified foreground notification, parallel chunking, storage permissions, and optional post-download installer prompt. |

## File Integrity & Validation

| Topic | Summary |
|-------|---------|
| [APK Integrity Guide](APK_INTEGRITY_GUIDE.md) | Complete guide for ensuring APK download integrity with checksum verification, file size validation, and APK structure validation. |
| [APK Structure Validation](APK_STRUCTURE_VALIDATION.md) | How `verifyApkStructure` works: Magic Number check and ZIP structure validation mechanism. |
| [APK Signature Validation](APK_SIGNATURE_VALIDATION.md) | How `verifyApkSignature` works: PackageManager-based signature verification, why it's expensive, and when to use it. |
| [Checksum Retry Best Practices](CHECKSUM_RETRY_BEST_PRACTICES.md) | Best practices for handling checksum mismatch: IDM behavior, file deletion, error differentiation, and retry strategies. |
| [Retry Resume Behavior](RETRY_RESUME_BEHAVIOR.md) | Retry and resume behavior on checksum mismatch: why we can't detect corrupted sections, and why complete deletion is the best approach. |
| [Current Retry Status](CURRENT_RETRY_STATUS.md) | Current retry implementation status: what's supported, what's not, and comparison between network errors and integrity errors. |

> Each development stage adds a new numbered document here. After review, the same content is linked from the root `README.md`.

