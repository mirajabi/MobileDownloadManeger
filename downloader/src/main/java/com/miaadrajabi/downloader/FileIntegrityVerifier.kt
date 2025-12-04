package com.miaadrajabi.downloader

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipException

/**
 * Exception thrown when file integrity validation fails.
 * This is distinct from network errors and should trigger file deletion and retry from start.
 */
internal class IntegrityValidationException(
    message: String,
    val errors: List<String>,
    val file: File
) : IOException(message)

/**
 * File integrity verification utilities.
 * Provides configurable validation checks for downloaded files, especially APKs.
 */
internal object FileIntegrityVerifier {
    
    private const val TAG = "FileIntegrityVerifier"
    private const val APK_MAGIC_NUMBER = "PK" // ZIP files start with "PK" (0x504B)
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    
    /**
     * Validates file size against expected size.
     * @return true if sizes match or expectedSize is null, false otherwise
     */
    fun verifyFileSize(file: File, expectedSize: Long?): Boolean {
        if (expectedSize == null) {
            Log.d(TAG, "No expected size provided, skipping size validation")
            return true
        }
        
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: ${file.absolutePath}")
            return false
        }
        
        val actualSize = file.length()
        val matches = actualSize == expectedSize
        
        if (!matches) {
            Log.w(TAG, "File size mismatch: expected=$expectedSize, actual=$actualSize")
        } else {
            Log.d(TAG, "File size verified: $actualSize bytes")
        }
        
        return matches
    }
    
    /**
     * Calculates checksum/hash of a file using the specified algorithm.
     * @return hex string representation of the hash, or null on error
     */
    fun calculateChecksum(file: File, algorithm: ChecksumAlgorithm): String? {
        if (!file.exists() || !file.isFile) {
            Log.e(TAG, "File does not exist or is not a file: ${file.absolutePath}")
            return null
        }
        
        return try {
            val digest = MessageDigest.getInstance(algorithm.name)
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hashBytes = digest.digest()
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating checksum: ${algorithm.name}", e)
            null
        }
    }
    
    /**
     * Verifies file checksum against expected value.
     * @return true if checksums match, false otherwise
     */
    fun verifyChecksum(
        file: File,
        expectedChecksum: String,
        algorithm: ChecksumAlgorithm
    ): Boolean {
        val actualChecksum = calculateChecksum(file, algorithm)
        
        if (actualChecksum == null) {
            Log.e(TAG, "Failed to calculate checksum for verification")
            return false
        }
        
        val expectedNormalized = expectedChecksum.toLowerCase(Locale.US).trim()
        val actualNormalized = actualChecksum.toLowerCase(Locale.US)
        
        val matches = actualNormalized == expectedNormalized
        
        if (!matches) {
            Log.w(TAG, "Checksum mismatch (${algorithm.name}): expected=$expectedNormalized, actual=$actualNormalized")
        } else {
            Log.d(TAG, "Checksum verified (${algorithm.name}): $actualNormalized")
        }
        
        return matches
    }
    
    /**
     * Validates Content-Type header against expected MIME type.
     * @return true if types match or contentType is null, false otherwise
     */
    fun verifyContentType(contentType: String?, expectedMimeType: String?): Boolean {
        if (contentType == null || expectedMimeType == null) {
            Log.d(TAG, "Content-Type validation skipped (null values)")
            return true
        }
        
        val normalizedContentType = contentType.toLowerCase(Locale.US).split(';')[0].trim()
        val normalizedExpected = expectedMimeType.toLowerCase(Locale.US).trim()
        
        val matches = normalizedContentType == normalizedExpected
        
        if (!matches) {
            Log.w(TAG, "Content-Type mismatch: expected=$normalizedExpected, actual=$normalizedContentType")
        } else {
            Log.d(TAG, "Content-Type verified: $normalizedContentType")
        }
        
        return matches
    }
    
    /**
     * Checks if file is an APK based on extension.
     */
    fun isApkFile(file: File): Boolean {
        val name = file.name.toLowerCase(Locale.US)
        return name.endsWith(".apk") || name.endsWith(".apks")
    }
    
    /**
     * Validates APK file structure (magic number and ZIP format).
     * @return true if APK structure is valid, false otherwise
     */
    fun verifyApkStructure(file: File): Boolean {
        if (!isApkFile(file)) {
            Log.d(TAG, "File is not an APK, skipping structure validation")
            return true // Not an APK, so validation passes
        }
        
        if (!file.exists() || !file.isFile) {
            Log.e(TAG, "APK file does not exist: ${file.absolutePath}")
            return false
        }
        
        // Check magic number (ZIP files start with "PK")
        try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(2)
                val read = input.read(magic)
                if (read != 2) {
                    Log.e(TAG, "APK file too short to read magic number")
                    return false
                }
                
                val magicString = String(magic)
                if (magicString != APK_MAGIC_NUMBER) {
                    Log.e(TAG, "APK magic number mismatch: expected='PK', actual='$magicString'")
                    return false
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading APK magic number", e)
            return false
        }
        
        // Validate ZIP structure
        try {
            ZipFile(file).use { zip ->
                // Try to read entries to verify ZIP integrity
                val entries = zip.entries()
                var hasManifest = false
                var entryCount = 0
                
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    if (entry.name == "AndroidManifest.xml") {
                        hasManifest = true
                    }
                }
                
                if (entryCount == 0) {
                    Log.e(TAG, "APK ZIP file has no entries")
                    return false
                }
                
                if (!hasManifest) {
                    Log.w(TAG, "APK does not contain AndroidManifest.xml (may be invalid)")
                    // Don't fail here, some APKs might have manifest in different location
                }
                
                Log.d(TAG, "APK structure verified: $entryCount entries, manifest=${hasManifest}")
                return true
            }
        } catch (e: ZipException) {
            Log.e(TAG, "APK ZIP structure invalid", e)
            return false
        } catch (e: IOException) {
            Log.e(TAG, "Error validating APK ZIP structure", e)
            return false
        }
    }
    
    /**
     * Verifies APK signature using PackageManager.
     * This is expensive and may fail for unsigned APKs.
     * @return true if signature is valid or verification is skipped, false on error
     */
    fun verifyApkSignature(context: Context, file: File): Boolean {
        if (!isApkFile(file)) {
            Log.d(TAG, "File is not an APK, skipping signature validation")
            return true
        }
        
        if (!file.exists()) {
            Log.e(TAG, "APK file does not exist: ${file.absolutePath}")
            return false
        }
        
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_SIGNATURES or PackageManager.GET_SIGNING_CERTIFICATES
            )
            
            if (packageInfo == null) {
                Log.w(TAG, "Could not read package info from APK (may be unsigned or corrupted)")
                return false
            }
            
            val signatures = packageInfo.signatures
            val signingInfo = packageInfo.signingInfo
            
            val hasSignatures = (signatures != null && signatures.isNotEmpty()) ||
                    (signingInfo != null && signingInfo.hasMultipleSigners())
            
            if (!hasSignatures) {
                Log.w(TAG, "APK has no signatures (unsigned APK)")
                return false
            }
            
            Log.d(TAG, "APK signature verified: package=${packageInfo.packageName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying APK signature", e)
            false
        }
    }
    
    /**
     * Performs all configured integrity checks on a downloaded file.
     * @return IntegrityResult containing validation status and any errors
     */
    fun verifyFile(
        file: File,
        config: IntegrityConfig,
        request: DownloadRequest,
        expectedSize: Long?,
        contentType: String?,
        context: Context? = null
    ): IntegrityResult {
        val errors = mutableListOf<String>()
        
        // 1. File size validation
        if (config.verifyFileSize) {
            if (!verifyFileSize(file, expectedSize)) {
                errors.add("File size mismatch: expected=$expectedSize, actual=${file.length()}")
            }
        }
        
        // 2. Checksum validation
        if (config.verifyChecksum && request.expectedChecksum != null) {
            if (!verifyChecksum(file, request.expectedChecksum, request.checksumAlgorithm)) {
                errors.add("Checksum mismatch (${request.checksumAlgorithm.name})")
            }
        }
        
        // 3. Content-Type validation
        if (config.verifyContentType) {
            val expectedMimeType = if (isApkFile(file)) APK_MIME_TYPE else null
            if (expectedMimeType != null && !verifyContentType(contentType, expectedMimeType)) {
                errors.add("Content-Type mismatch: expected=$expectedMimeType, actual=$contentType")
            }
        }
        
        // 4. APK structure validation
        if (config.verifyApkStructure) {
            if (!verifyApkStructure(file)) {
                errors.add("APK structure validation failed")
            }
        }
        
        // 5. APK signature validation (requires context)
        if (config.verifyApkSignature && context != null) {
            if (!verifyApkSignature(context, file)) {
                errors.add("APK signature validation failed")
            }
        }
        
        return IntegrityResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}

/**
 * Result of file integrity verification.
 */
data class IntegrityResult(
    /**
     * true if all enabled validations passed, false otherwise
     */
    val isValid: Boolean,
    
    /**
     * List of error messages describing validation failures
     */
    val errors: List<String>
)

