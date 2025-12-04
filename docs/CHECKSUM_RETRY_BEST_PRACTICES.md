# Best Practices for Checksum Mismatch and Retry

## Behavior of IDM and Professional Download Managers

### Internet Download Manager (IDM)
1. **Delete corrupted file**: Deletes incomplete/corrupted file before retry
2. **Retry with exponential backoff**: 2s, 4s, 8s, 16s, ...
3. **Retry limit**: Usually 5-10 attempts
4. **User notification**: Tells user why it's retrying
5. **Error differentiation**: Distinguishes between network error and integrity error

### Other Download Managers (wget, curl, aria2)
- **wget**: Deletes file and downloads from start
- **curl**: Keeps file (manual cleanup)
- **aria2**: Deletes file and retries

---

## Best Practices (Recommended)

### 1. ✅ Delete Corrupted File Before Retry
```
Why?
- Corrupted file may occupy disk space
- User may think file is valid
- Retry should start from clean file
```

### 2. ✅ Differentiate Between Error Types
```
Network Error:
  - Connection timeout
  - DNS failure
  - Server error (5xx)
  → Retry is logical

Integrity Error:
  - Checksum mismatch
  - File size mismatch
  - APK structure invalid
  → Retry is logical (may be network corruption)
  
Permanent Error:
  - File not found (404)
  - Permission denied (403)
  → Retry is useless
```

### 3. ✅ Exponential Backoff
```
Attempt 1: 2s delay
Attempt 2: 4s delay
Attempt 3: 8s delay
Attempt 4: 16s delay
...
→ Prevents server overload
```

### 4. ✅ Retry Limit
```
Default: 3-5 attempts
Maximum: 10 attempts
→ Prevents infinite loop
```

### 5. ✅ Logging and Reporting
```
- Log retry reason
- Report to user
- Store error history
```

### 6. ✅ State Management
```
- Clear chunk states on checksum mismatch
- Reset progress tracking
- Clean temporary files
```

---

## Current Code Status

### ✅ What we have:
1. ✅ Retry with exponential backoff
2. ✅ Retry limit (maxAttempts)
3. ✅ Logging
4. ✅ User notification (onRetry, onFailed)

### ❌ What we don't have:
1. ❌ **Delete file before retry** (on checksum mismatch)
2. ❌ **Error differentiation** (network vs integrity)
3. ❌ **Option to keep/delete file**

---

## Suggested Improvements

### 1. Add IntegrityError Exception
```kotlin
class IntegrityValidationException(
    message: String,
    val errors: List<String>,
    val file: File
) : IOException(message)
```

### 2. Delete File Before Retry
```kotlin
catch (error: IntegrityValidationException) {
    // Delete corrupted file
    if (error.file.exists()) {
        error.file.delete()
        Log.d(TAG, "Deleted corrupted file: ${error.file.absolutePath}")
    }
    
    // Retry
    if (attempt < maxAttempts) {
        listeners.forEach { it.onRetry(handle, attempt) }
        delay(delayMs)
        attempt++
    }
}
```

### 3. Differentiate Between Errors
```kotlin
when (error) {
    is IntegrityValidationException -> {
        // Integrity error: delete file and retry
        deleteFileAndRetry()
    }
    is NetworkException -> {
        // Network error: retry without deletion (resume possible)
        retryWithResume()
    }
    is PermanentException -> {
        // Permanent error: fail immediately
        failImmediately()
    }
}
```

### 4. Configurable File Deletion
```kotlin
data class IntegrityConfig(
    // ...
    val deleteFileOnValidationFailure: Boolean = true,  // new
    val deleteFileOnRetry: Boolean = true  // new
)
```

---

## Practical Example: IDM Behavior

### Scenario: Checksum Mismatch

```
Attempt 1:
  ✅ Download complete (2MB)
  ❌ Checksum mismatch
  🗑️ File deleted
  ⏱️ Wait 2s
  🔄 Retry

Attempt 2:
  ✅ Download complete (2MB)
  ❌ Checksum mismatch
  🗑️ File deleted
  ⏱️ Wait 4s
  🔄 Retry

Attempt 3:
  ✅ Download complete (2MB)
  ✅ Checksum verified
  ✅ Success!
```

### Scenario: Network Error

```
Attempt 1:
  ❌ Connection timeout (50% downloaded)
  📁 File kept (for resume)
  ⏱️ Wait 2s
  🔄 Retry (resume from 50%)

Attempt 2:
  ✅ Resume from 50%
  ✅ Download complete
  ✅ Success!
```

---

## Implementation Recommendations

### Priority 1 (Critical):
1. ✅ **Delete file on checksum mismatch**
   - Prevents using corrupted file
   - Saves disk space

2. ✅ **Differentiate IntegrityError from NetworkError**
   - Different behavior for each error type
   - Resume only for network errors

### Priority 2 (Important):
3. ⚠️ **Configurable deletion**
   - Option to keep file (debugging)
   - Option for automatic deletion

4. ⚠️ **Better error reporting**
   - More details in onFailed
   - Retry attempt history

### Priority 3 (Nice to have):
5. ⚠️ **Incremental checksum**
   - Calculate checksum during download
   - Early corruption detection

6. ⚠️ **Partial file recovery**
   - Use valid parts of file
   - Resume from last valid byte

---

## Conclusion

### Best Practice Summary:
1. ✅ **Delete corrupted file** before retry
2. ✅ **Differentiate errors** (network vs integrity)
3. ✅ **Exponential backoff** (we have)
4. ✅ **Retry limit** (we have)
5. ✅ **Logging and reporting** (we have)

### Action Items:
- [x] Add IntegrityValidationException
- [x] Delete file before retry on checksum mismatch
- [x] Differentiate between network and integrity errors
- [ ] Add option for configurable deletion
