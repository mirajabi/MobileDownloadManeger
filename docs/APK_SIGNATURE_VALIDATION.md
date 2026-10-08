# 11. APK signature

[All steps](README.md) · [Switch languages](index.html)

## Step 28. Leave signature off unless you need it

`verifyApkSignature` asks `PackageManager` to read the archive. It is off by default. Turn it on only when an unsigned or unreadable package must fail the download. The check is slower than the ZIP magic test, and a package the host still intends to inspect itself should not be rejected here.

The library does not compare the signer to a known certificate. It only checks that `PackageManager` can see a signature. Identity of the signer stays with the host.

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    integrityValidation(
        verifyFileSize = true,
        verifyChecksum = true,
        verifyApkStructure = true,
        verifyContentType = false,
        verifyApkSignature = true
    )
}
```

**Java**

```java
DownloadForegroundService.configureService(this, builder -> {
    builder.integrityValidation(true, true, true, false, true);
    return kotlin.Unit.INSTANCE;
});
```

## This section, filled in

Recommended production value is `false`.

**Kotlin**

```kotlin
integrityValidation(verifyApkSignature = false)
```

**Java**

```java
builder.integrityValidation(null, null, null, null, false);
```

Next: [checksum shape and mismatch](CHECKSUM_RETRY_BEST_PRACTICES.md).
