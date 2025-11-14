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

## Development Workflow
1. Implement a feature in the library.
2. Mirror the change in the sample module with the simplest possible UI or instrumentation hook.
3. Capture the behavior and usage notes inside the `docs/` folder and link it here.

> Gradle Wrapper 6.7.1 + Android Gradle Plugin 4.1.2 are enforced because the target environment requires them.

