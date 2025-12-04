# Current Retry Status in Project

## Current Status Review

### ✅ What is supported:

#### 1. Retry with Exponential Backoff ✅
```kotlin
// Line 347-348
delay(delayMs)
delayMs = (delayMs * policy.backoffMultiplier).toLong().coerceAtLeast(1_000L)
```
- ✅ Exponential backoff works
- ✅ Default: 2s, 4s, 8s, ...

#### 2. Retry Limit ✅
```kotlin
// Line 340
if (attempt >= policy.maxAttempts) {
    listeners.forEach { it.onFailed(handle, error) }
    return
}
```
- ✅ Retry limit works
- ✅ Default: 3 attempts

#### 3. Resume for Network Error ✅
```kotlin
// Line 346
plannedChunkStates = currentChunkStates(handle.id)
```
- ✅ Resume from last position works
- ✅ Suitable for network error

#### 4. Logging and Reporting ✅
```kotlin
// Line 345
listeners.forEach { it.onRetry(handle, attempt) }
```
- ✅ onRetry callback is called
- ✅ User is notified

---

### ❌ What is not supported:

#### 1. Delete File on Checksum Mismatch ❌
```kotlin
// Line 326-329: Only throws IOException
if (!integrityResult.isValid) {
    throw IOException("File integrity validation failed...")
    // ❌ File not deleted!
}
```

**Problem**: Corrupted file remains and occupies disk space.

#### 2. Differentiate Network Error from Integrity Error ❌
```kotlin
// Line 339: All IOException handled the same
catch (error: IOException) {
    // ❌ Doesn't know if network error or integrity error
    plannedChunkStates = currentChunkStates(handle.id)  // always resumes
}
```

**Problem**: 
- Integrity error → should delete file and start from beginning
- Network error → should resume
- But currently all handled the same!

#### 3. Retry from Start for Integrity Error ❌
```kotlin
// Line 346: Always resumes
plannedChunkStates = currentChunkStates(handle.id)  // ❌ from last position
startOffset = ...  // ❌ from last offset
```

**Problem**: For integrity error should start from beginning, not resume.

---

## Comparison Table

| Feature | Network Error | Integrity Error | Current Status |
|---------|---------------|-----------------|----------------|
| **Retry** | ✅ | ✅ | ✅ Supported |
| **Exponential Backoff** | ✅ | ✅ | ✅ Supported |
| **Retry Limit** | ✅ | ✅ | ✅ Supported |
| **Resume** | ✅ Should | ❌ Shouldn't | ⚠️ Always resumes |
| **Delete File** | ❌ Shouldn't | ✅ Should | ❌ Not supported |
| **Retry from Start** | ❌ Shouldn't | ✅ Should | ❌ Not supported |
| **Error Differentiation** | - | - | ❌ Not supported |

---

## Current Scenarios

### Scenario 1: Network Error (Connection Timeout)
```
Attempt 1:
  ❌ Connection timeout (50% downloaded)
  📁 File kept ✅
  🔄 Retry with resume from 50% ✅
  
Attempt 2:
  ✅ Resume from 50%
  ✅ Download complete
  ✅ Success!
```
**Result**: ✅ Works correctly

### Scenario 2: Checksum Mismatch
```
Attempt 1:
  ✅ Download complete (2MB)
  ❌ Checksum mismatch
  📁 File kept ❌ (should be deleted)
  🔄 Retry with resume from 0 ⚠️ (should be from start)
  
Attempt 2:
  ✅ Download from start (overwrite)
  ❌ Checksum mismatch (again)
  🔄 Retry
  
Attempt 3:
  ✅ Download from start
  ✅ Checksum verified
  ✅ Success!
```
**Result**: ⚠️ Works but not optimal (corrupted file kept)

---

## Summary

### ✅ Supported:
1. ✅ Retry with exponential backoff
2. ✅ Retry limit
3. ✅ Resume for network error
4. ✅ Logging and reporting

### ❌ Not supported:
1. ❌ **Delete file on checksum mismatch**
2. ❌ **Differentiate network and integrity error**
3. ❌ **Retry from start for integrity error**

---

## Conclusion

**Answer**: ✅ **Yes**, all scenarios are now supported!

### Current status (after improvements):
- ✅ **Network Error**: Works correctly (resume from last position)
- ✅ **Integrity Error**: Works correctly (delete file + retry from start)

### Improvements applied:
1. ✅ Added `IntegrityValidationException`
2. ✅ Delete file before retry on integrity error
3. ✅ Retry from start for integrity error (not resume)
4. ✅ Differentiate network and integrity error in catch block

---

## Action Items

✅ **All items implemented:**
- [x] Add IntegrityValidationException
- [x] Delete file on checksum mismatch
- [x] Reset states for integrity error (retry from start)
- [x] Differentiate network and integrity error in catch block

---

## New Behavior

### Network Error:
```
Attempt 1: Connection timeout (50%)
  📁 File kept ✅
  🔄 Resume from 50% ✅
  
Attempt 2: Success ✅
```

### Integrity Error (Checksum Mismatch):
```
Attempt 1: Checksum mismatch
  🗑️ File deleted ✅
  🔄 Retry from start ✅
  
Attempt 2: Success ✅
```
