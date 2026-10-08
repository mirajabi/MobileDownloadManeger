# 2. Storage

[All steps](README.md) · [Switch languages](index.html)

`StorageResolver` picks the first writable directory, then creates `fileName` inside it. `DownloadDestination.Auto` uses app-specific external downloads, then documents, then `filesDir/downloads`. `preferExternalPublic` puts the shared `Download/` directory first.

A real resolve deletes an existing file when overwrite is on. Admission checks the path before that delete, so a rejected second request does not remove the first file.

## Step 5. Choose a destination

**Kotlin**

```kotlin
val auto = DownloadDestination.Auto

val custom = DownloadDestination.Custom(
    getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.absolutePath
)

val scoped = DownloadDestination.Scoped("tms/packages")
```

**Java**

```java
DownloadDestination auto = DownloadDestination.Auto.INSTANCE;

File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
DownloadDestination custom = new DownloadDestination.Custom(dir.getAbsolutePath());

DownloadDestination scoped = new DownloadDestination.Scoped("tms/packages");
```

`Scoped` is a path under `getExternalFilesDir(null)`, or under `filesDir` when external storage is unavailable. It is not a Storage Access Framework tree URI.

## Step 6. Overwrite, free space, and the public Download folder

`minFreeSpaceBytes` is checked only when `validate` is true. The default floor is 10 MB. Public `Download/` on API 28 and below needs `WRITE_EXTERNAL_STORAGE` at runtime. On API 29 and above, writing that directory also depends on the host's storage policy. App-specific directories do not need that permission.

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    storageDestinations(listOf(DownloadDestination.Scoped("tms/packages")))
    storageOverwrite(false)
    storageValidateFreeSpace(validate = true, minBytes = 64L * 1024 * 1024)
    storageUsePublicDownloads(false)
}
```

**Java**

```java
DownloadForegroundService.configureService(this, builder -> {
    builder.storageDestinations(Collections.singletonList(
            new DownloadDestination.Scoped("tms/packages")
    ));
    builder.storageOverwrite(false);
    builder.storageValidateFreeSpace(true, 64L * 1024 * 1024);
    builder.storageUsePublicDownloads(false);
    return kotlin.Unit.INSTANCE;
});
```

With `storageOverwrite(false)`, an existing file fails the request with `StorageResolutionException`. The partial file from an interrupted download of the same id is a checkpoint, not a second file, and resume does not take this path.

## Step 7. This section, filled in

**Kotlin**

```kotlin
val packages = File(
    getExternalFilesDir(null) ?: filesDir,
    "tms/packages"
)

DownloadForegroundService.configureService(this) {
    storageDestinations(listOf(DownloadDestination.Custom(packages.absolutePath)))
    storageOverwrite(true)
    storageValidateFreeSpace(validate = true, minBytes = 64L * 1024 * 1024)
    storageUsePublicDownloads(false)
}
```

**Java**

```java
File root = getExternalFilesDir(null);
if (root == null) {
    root = getFilesDir();
}
File packages = new File(root, "tms/packages");

DownloadForegroundService.configureService(this, builder -> {
    builder.storageDestinations(Collections.singletonList(
            new DownloadDestination.Custom(packages.getAbsolutePath())
    ));
    builder.storageOverwrite(true);
    builder.storageValidateFreeSpace(true, 64L * 1024 * 1024);
    builder.storageUsePublicDownloads(false);
    return kotlin.Unit.INSTANCE;
});
```

Next: [the request and enqueue](03-chunk-engine.md).
