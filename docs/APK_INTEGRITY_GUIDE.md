# 9. Integrity flags

[All steps](README.md) · [Switch languages](index.html)

All five flags are optional. Defaults are already the recommended APK set: size, checksum, and structure on; content type and signature off. `integrityValidationForApk()` writes that same set.

Checksum bytes live on the request, not on this config. `verifyChecksum` does nothing when `expectedChecksum` is null or blank.

## Step 26. Set every flag

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    integrityValidationForApk()
}

DownloadForegroundService.configureService(this) {
    integrityValidation(
        verifyFileSize = true,
        verifyChecksum = true,
        verifyApkStructure = true,
        verifyContentType = false,
        verifyApkSignature = false
    )
}
```

**Java**

```java
DownloadForegroundService.configureService(this, builder -> {
    builder.integrityValidationForApk();
    return kotlin.Unit.INSTANCE;
});

DownloadForegroundService.configureService(this, builder -> {
    builder.integrityValidation(true, true, true, false, false);
    return kotlin.Unit.INSTANCE;
});
```

The second call replaces the first. Use one.

| Flag | Default | When it fails |
|------|---------|----------------|
| `verifyFileSize` | true | Local length does not match the known total |
| `verifyChecksum` | true | Hex digest does not match `expectedChecksum` |
| `verifyApkStructure` | true | `.apk` / `.apks` is not a ZIP that starts with `PK` |
| `verifyContentType` | false | Response type does not match the expected MIME type. Many servers send a wrong type, so this stays off unless you control the server |
| `verifyApkSignature` | false | `PackageManager` cannot read a signature. Unsigned packages fail. This is slow |

A failed check deletes the file, clears the checkpoint, and retries from byte zero while `maxAttempts` remains. The installer prompt does not run.

## This section, filled in

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    retryPolicy(maxAttempts = 5, initialDelayMillis = 2_000L, backoffMultiplier = 2f)
    integrityValidation(
        verifyFileSize = true,
        verifyChecksum = true,
        verifyApkStructure = true,
        verifyContentType = false,
        verifyApkSignature = false
    )
}

val request = DownloadRequest(
    url = "https://downloads.example.com/tms/app-release.apk",
    fileName = "app-release.apk",
    id = "tms-app-release",
    expectedChecksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    checksumAlgorithm = ChecksumAlgorithm.SHA256
)
```

**Java**

```java
DownloadForegroundService.configureService(this, builder -> {
    builder.retryPolicy(5, 2_000L, 2f);
    builder.integrityValidation(true, true, true, false, false);
    return kotlin.Unit.INSTANCE;
});

DownloadRequest request = new DownloadRequest(
        "https://downloads.example.com/tms/app-release.apk",
        "app-release.apk",
        DownloadDestination.Auto.INSTANCE,
        "tms-app-release",
        Collections.emptyMap(),
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        ChecksumAlgorithm.SHA256
);
```

Next: [what the structure check looks at](APK_STRUCTURE_VALIDATION.md).
