# 12. Checksum

[All steps](README.md) · [Switch languages](index.html)

## Step 29. Pass a real digest, or pass none

| Algorithm | Hex length |
|-----------|------------|
| `ChecksumAlgorithm.MD5` | 32 |
| `ChecksumAlgorithm.SHA256` | 64 |
| `ChecksumAlgorithm.SHA512` | 128 |

Lower case and upper case are both accepted. A wrong length, or an unknown algorithm name together with a checksum, fails the request in `onFailed` before download. The library does not assume SHA-256 for a broken value.

`null` or `""` means there is no checksum. Older pause files that have no checksum field still load.

The digest is stored with the WorkManager input, the alarm intent, and the pause record. A scheduled run checks the same bytes as the original request.

On mismatch the file and the chunk list are deleted. The same attempt budget retries from byte zero. It does not try to repair a slice of the file.

**Kotlin**

```kotlin
val sha256 = DownloadRequest(
    url = "https://downloads.example.com/tms/app-release.apk",
    fileName = "app-release.apk",
    id = "tms-sha256",
    expectedChecksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    checksumAlgorithm = ChecksumAlgorithm.SHA256
)

val sha512 = sha256.copy(
    id = "tms-sha512",
    expectedChecksum = "0123456789abcdef".repeat(8),
    checksumAlgorithm = ChecksumAlgorithm.SHA512
)

val noChecksum = sha256.copy(
    id = "tms-plain",
    expectedChecksum = null
)
```

**Java**

```java
DownloadRequest sha256 = new DownloadRequest(
        "https://downloads.example.com/tms/app-release.apk",
        "app-release.apk",
        DownloadDestination.Auto.INSTANCE,
        "tms-sha256",
        Collections.emptyMap(),
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        ChecksumAlgorithm.SHA256
);

String sha512Hex = new String(new char[8]).replace("\0", "0123456789abcdef");
DownloadRequest sha512 = new DownloadRequest(
        "https://downloads.example.com/tms/app-release.apk",
        "app-release.apk",
        DownloadDestination.Auto.INSTANCE,
        "tms-sha512",
        Collections.emptyMap(),
        sha512Hex,
        ChecksumAlgorithm.SHA512
);

DownloadRequest noChecksum = new DownloadRequest(
        "https://downloads.example.com/tms/app-release.apk",
        "app-release.apk",
        DownloadDestination.Auto.INSTANCE,
        "tms-plain",
        Collections.emptyMap(),
        null,
        ChecksumAlgorithm.SHA256
);
```

`"0123456789abcdef".repeat(8)` is 128 hex characters. Replace it with the digest of the real file. Two ids for the same path are rejected while the first is healthy; the three requests above are three shapes, not three concurrent downloads.

## This section, filled in

Use SHA-256 unless the publisher gives you another algorithm.

**Kotlin**

```kotlin
DownloadRequest(
    url = "https://downloads.example.com/tms/app-release.apk",
    fileName = "app-release.apk",
    id = "tms-app-release",
    expectedChecksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    checksumAlgorithm = ChecksumAlgorithm.SHA256
)
```

**Java**

```java
new DownloadRequest(
        "https://downloads.example.com/tms/app-release.apk",
        "app-release.apk",
        DownloadDestination.Auto.INSTANCE,
        "tms-app-release",
        Collections.emptyMap(),
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        ChecksumAlgorithm.SHA256
);
```

Next: [retry versus restart](RETRY_RESUME_BEHAVIOR.md).
