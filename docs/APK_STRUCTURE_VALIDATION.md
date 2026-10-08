# 10. APK structure

[All steps](README.md) · [Switch languages](index.html)

## Step 27. Turn structure checks on

The check runs only for file names ending in `.apk` or `.apks`. Any other file passes this flag without being read.

For an APK it requires:

1. The first two bytes are `PK`.
2. The file opens as a ZIP and has at least one entry.

A missing `AndroidManifest.xml` is logged and does not fail the download. A bad magic number or a ZIP that cannot be opened deletes the file and retries from byte zero.

**Kotlin**

```kotlin
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
    builder.integrityValidation(true, true, true, false, false);
    return kotlin.Unit.INSTANCE;
});
```

## This section, filled in

Leave `verifyApkStructure` true for package downloads and false only when the file is not a package and the name still ends in `.apk`.

**Kotlin**

```kotlin
integrityValidation(verifyApkStructure = true)
```

**Java**

```java
builder.integrityValidation(null, null, true, null, null);
```

Passing `null` from Java keeps the current value of the other four flags.

Next: [signature](APK_SIGNATURE_VALIDATION.md).
