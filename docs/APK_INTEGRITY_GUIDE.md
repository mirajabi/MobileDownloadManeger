# APK Download Integrity Guide

## Priority-ordered steps to ensure downloaded file integrity

### Stage 1: Pre-Download Validation
- ✅ **URL and network connectivity check**: Ensure server accessibility
- ✅ **Content-Length retrieval**: Check file size before download
- ✅ **Free space check**: Ensure sufficient storage (currently implemented)
- ⚠️ **Checksum retrieval from server**: If server provides `ETag` or `Content-MD5` header, store it

### Stage 2: During Download Validation
- ✅ **Range Headers check**: Ensure chunk ranges are correct
- ✅ **Content-Range check**: Match with Content-Length
- ⚠️ **Incremental Hash calculation**: Calculate hash during download (for large files)
- ⚠️ **Network Errors check**: Retry mechanism (currently implemented)

### Stage 3: Post-Download Validation

#### 3.1 File Size Validation
```
- Compare downloaded file size with Content-Length
- If different → download incomplete → retry
```

#### 3.2 Checksum/Hash Verification (Integrity Check)
```
- If MD5/SHA-256 received from server → compare
- If provided in DownloadRequest → compare
- If different → file corrupted → retry
```

#### 3.3 Content-Type Validation
```
- Check MIME type from response header
- For APK should be: application/vnd.android.package-archive
- If different → warning or reject

⚠️ Important note: This check is disabled by default because:
  - Many servers don't send correct Content-Type headers
  - CDNs or Proxies may modify Content-Type headers
  - File may be valid but Content-Type incorrect
  - APK structure validation (Magic Number + ZIP) is more reliable

✅ When to enable:
  - When you're sure the server sends correct Content-Type
  - For higher security in sensitive environments
  - When you want to prevent downloading wrong files (e.g., HTML error page)
```

#### 3.4 APK Structure Validation
```
- Check Magic Number: APK must start with "PK" (ZIP format)
- Check ZIP structure: Ensure ZIP structure integrity
- Check AndroidManifest.xml: Verify manifest existence and validity
```

#### 3.5 APK Signature Verification
```
- Check signature presence in APK
- Verify signature validity (optional - requires PackageManager)
- If signature invalid → reject
```

### Stage 4: Final Validation
- ✅ **File existence check**: Ensure file exists in final path
- ⚠️ **Read access check**: Ensure file is readable
- ⚠️ **Install access check**: For APK, check installation capability

## Suggested Implementation

### 1. Add IntegrityConfig to DownloadConfig
```kotlin
data class IntegrityConfig(
    val verifyFileSize: Boolean = true,
    val verifyChecksum: Boolean = true,
    val checksumAlgorithm: ChecksumAlgorithm = ChecksumAlgorithm.SHA256,
    val expectedChecksum: String? = null, // from DownloadRequest
    val verifyApkStructure: Boolean = true,
    val verifyApkSignature: Boolean = false // optional
)

enum class ChecksumAlgorithm {
    MD5, SHA256, SHA512
}
```

### 2. Add checksum to DownloadRequest
```kotlin
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val destination: DownloadDestination = DownloadDestination.Auto,
    val id: String = UUID.randomUUID().toString(),
    val headers: Map<String, String> = emptyMap(),
    val expectedChecksum: String? = null, // new
    val checksumAlgorithm: ChecksumAlgorithm = ChecksumAlgorithm.SHA256 // new
)
```

### 3. Create FileIntegrityVerifier
```kotlin
internal object FileIntegrityVerifier {
    fun verifyFileSize(file: File, expectedSize: Long?): Boolean
    fun calculateChecksum(file: File, algorithm: ChecksumAlgorithm): String
    fun verifyChecksum(file: File, expected: String, algorithm: ChecksumAlgorithm): Boolean
    fun verifyApkStructure(file: File): Boolean
    fun verifyApkSignature(context: Context, file: File): Boolean
}
```

### 4. Integration in MobileDownloadManager
```kotlin
// After successful download:
if (config.integrity.verifyFileSize) {
    verifyFileSize(resolution.file, totalBytes)
}
if (config.integrity.verifyChecksum && request.expectedChecksum != null) {
    verifyChecksum(resolution.file, request.expectedChecksum, request.checksumAlgorithm)
}
if (config.integrity.verifyApkStructure && isApkFile(resolution.file)) {
    verifyApkStructure(resolution.file)
}
```

## Implementation Priority

1. **High Priority (Critical)**:
   - ✅ File Size Validation
   - ✅ Checksum Verification (MD5/SHA256)
   - ✅ APK Structure Validation (Magic Number + ZIP)

2. **Medium Priority (Important)**:
   - ⚠️ Content-Type Validation
   - ⚠️ Incremental Hash (for large files)

3. **Low Priority (Optional)**:
   - ⚠️ APK Signature Verification
   - ⚠️ ETag/Content-MD5 from Response Headers

## Usage Example

```kotlin
val request = DownloadRequest(
    url = "https://example.com/app.apk",
    fileName = "app.apk",
    expectedChecksum = "a1b2c3d4e5f6...", // SHA256 hash
    checksumAlgorithm = ChecksumAlgorithm.SHA256
)

val config = DownloadConfig(
    integrity = IntegrityConfig(
        verifyFileSize = true,
        verifyChecksum = true,
        verifyApkStructure = true,
        verifyApkSignature = false
    )
)
```
