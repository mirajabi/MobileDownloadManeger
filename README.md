# Mobile Download Manager

[![JitPack](https://www.jitpack.io/v/mirajabi/MobileDownloadManeger.svg)](https://www.jitpack.io/#mirajabi/MobileDownloadManeger)

Android library for large file downloads: parallel ranges, pause and resume, a foreground notification, and a schedule that survives process death. The current release is `v1.3.4`. Minimum SDK is 23.

The host app keeps ownership of extraction, package identity, and installation. The library downloads a file and can optionally open the system installer. It does not install a package by itself.

## Install

Kotlin (`settings.gradle.kts` or the root `build.gradle.kts`):

```kotlin
repositories {
    maven("https://jitpack.io")
}
```

```kotlin
dependencies {
    implementation("com.github.mirajabi:MobileDownloadManeger:v1.3.4")
}
```

Java / Groovy:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

```groovy
dependencies {
    implementation 'com.github.mirajabi:MobileDownloadManeger:v1.3.4'
}
```

The library manifest already merges `INTERNET`, the foreground-service permissions, the download service, the alarm and notification receivers, and a `FileProvider`. Call `configureService` once before the first download so those components load your settings instead of the defaults.

## Read the guide

The guide is numbered. Every step has a Kotlin sample and a Java sample, and every section ends with that section filled in, including the optional fields.

- [Step-by-step index](docs/README.md)
- [Guide with a Kotlin / Java switch](https://mirajabi.github.io/MobileDownloadManeger/)

The switch runs on GitHub Pages. One control at the top shows either Kotlin or Java for every step. GitHub's file view only shows the HTML source, so the page above is the one to open.

## What a download does

- A request can keep a byte window. `rangeStart` and `rangeEndInclusive` are both inclusive. Leave them unset to take the whole file. The saved file contains only that window, and the checksum covers those bytes.
- Several connections run together when `chunkCount` is greater than one. Headers, including `Cookie`, travel on `DownloadRequest.headers`.
- A changed strong ETag or a changed total size starts again from byte zero. A missing or weak ETag keeps the partial file. The checksum is the final check.
- A manual pause stays paused across reboot. A download that was queued, running, or waiting to retry continues from the last flushed checkpoint. There is no boot receiver. After a force-stop, Android allows that work again when the app may run.
- A second request for the same file is rejected while the first one is queued, running, waiting to retry, or paused. If the first one already failed or was cancelled, the new request starts from byte zero.
- Pause and Stop are not network errors, so they are not started again by the scheduler.
- A missing or corrupt saved configuration uses `DownloadConfig` defaults. The service does not crash in `onCreate`.

## Changelog

### v1.3.4

A download can keep a chosen byte window. Set `rangeStart` and `rangeEndInclusive` on `DownloadRequest`; both ends are inclusive, and either one can be left open. The file on disk contains only that window. Parallel connections stay on `chunkCount`, and cookies stay in `headers` under the name `Cookie`.

### v1.3.3

Interrupted downloads continue from the last checkpoint. A ranged response is kept only when it is the same artifact, and a second request cannot write into a file that is still healthy. The usage guide in `docs/` walks through every setting in Kotlin and Java. JitPack's current image points `JAVA_HOME` at a JDK 11 directory it no longer ships, so the tag installs Temurin 11 there before Gradle starts.

## Requirements

| | |
|---|---|
| minSdk | 23 |
| Artifact | `com.github.mirajabi:MobileDownloadManeger:v1.3.4` |
| Foreground service type | `dataSync` |
| Periodic schedule floor | 15 minutes |
