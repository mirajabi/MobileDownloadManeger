# 7. Installer prompt

[All steps](README.md) · [Switch languages](index.html)

`installerPromptOnCompletion` defaults to false. Leave it false when the host extracts the file and installs it. Set it true only when the system package installer should open after a successful download.

The prompt uses `FileProvider` with authority `${applicationId}.downloader.provider`. The library manifest already adds `REQUEST_INSTALL_PACKAGES`. On API 26 and higher the user still has to allow this app to install unknown packages. The library does not open that settings screen for you.

`autoDetectMimeType` stays on unless you need the fallback. The fallback is used when the file name has no usable type. The default fallback is the APK MIME type.

## Step 20. Turn the prompt on and set the MIME type

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    installerPromptOnCompletion(
        enabled = true,
        fallbackMimeType = "application/vnd.android.package-archive"
    )
}
```

**Java**

```java
DownloadForegroundService.configureService(this, builder -> {
    builder.installerPromptOnCompletion(
            true,
            "application/vnd.android.package-archive"
    );
    return kotlin.Unit.INSTANCE;
});
```

A failed checksum does not open the installer. The partial file is deleted and the download retries from byte zero while attempts remain.

## Step 21. This section, filled in

Prompt off, which is the right setup when another component installs the package. The MIME type is still stored so a later `enabled = true` has a fallback.

**Kotlin**

```kotlin
DownloadForegroundService.configureService(this) {
    storageUsePublicDownloads(false)
    installerPromptOnCompletion(
        enabled = false,
        fallbackMimeType = "application/vnd.android.package-archive"
    )
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
    builder.storageUsePublicDownloads(false);
    builder.installerPromptOnCompletion(false, "application/vnd.android.package-archive");
    builder.integrityValidation(true, true, true, false, false);
    return kotlin.Unit.INSTANCE;
});
```

Next: [what the service does after a restart](08-service-configuration.md).
