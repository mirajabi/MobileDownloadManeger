# 2. Storage Resolver

## Overview
- Introduces `StorageResolver`, the component responsible for translating `StorageConfig` into concrete directories/files.
- Supports `DownloadDestination.Auto`, `Custom`, and `Scoped` targets with fallbacks to app-specific external files directories.
- Handles housekeeping before any network traffic begins: directory creation, overwrite policy, and free-space validation.

## Key Behaviors
1. **Directory Resolution**
   - Auto → `context.getExternalFilesDir(DIRECTORY_DOWNLOADS/DIRECTORY_DOCUMENTS)` + internal `files/downloads`.
   - Custom → absolute path provided by the host app.
   - Scoped → relative path rooted under the app-specific external directory (safe for API 23+).
2. **Overwrite Policy**
   - If `StorageConfig.overwriteExisting` is true, existing files are deleted just before download.
   - Otherwise, the resolver throws `StorageResolutionException` so the caller can rename or skip.
3. **Free-Space Validation**
   - When `validateFreeSpace` is enabled, `StatFs` ensures at least `minFreeSpaceBytes` remain (default 10 MB).
   - This is a coarse check; later stages can refine it using content-length metadata from the server.
4. **Dry-Run Support**
   - `MobileDownloadManager.previewDestination(request)` runs the resolver without deleting files, perfect for diagnostics or UI previews.

## Public Types
- `StorageConfig`: extended with `minFreeSpaceBytes`.
- `StorageResolution`: exposes `directory`, `file`, and whether an overwrite would occur.
- `StorageResolutionException`: thrown when directories are unwritable, a file already exists, or space is insufficient.

## Sample App Integration
`MainActivity` now:
1. Builds a `MobileDownloadManager` instance.
2. Creates a demo `DownloadRequest` (`sample_config.bin`).
3. Calls `previewDestination(...)` and prints the directory/file paths inside the UI.
4. Surfaces any resolver errors to help test permissions or misconfigured paths quickly.

## Next Steps
- Wire up the resolver output to the upcoming chunked downloading engine.
- Extend the sample UI with user-provided URLs and destination pickers once networking is live.

