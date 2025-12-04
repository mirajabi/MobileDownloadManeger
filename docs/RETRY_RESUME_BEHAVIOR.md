# Retry and Resume Behavior on Checksum Mismatch

## Current Code Status

### When Checksum Mismatch occurs:

```kotlin
// Line 326-330: Integrity validation fail
if (!integrityResult.isValid) {
    throw IOException("File integrity validation failed...")
}

// Line 339-350: Catch IOException
catch (error: IOException) {
    if (attempt < maxAttempts) {
        plannedChunkStates = currentChunkStates(handle.id)  // ⚠️ Keeps it
        delay(delayMs)
        attempt++
        // 🔄 Retries
    }
}
```

### Problems:
1. ❌ **File not deleted** - Corrupted file remains
2. ❌ **Resumes** - Continues from last position
3. ❌ **Overwrites corrupted file** - But corruption may be in previous sections

---

## How it works (Current)

### Scenario: Checksum Mismatch

```
Attempt 1:
  ✅ Download complete (2MB)
  ✅ Chunk 0: 0-666KB ✅
  ✅ Chunk 1: 666KB-1.3MB ✅
  ✅ Chunk 2: 1.3MB-2MB ✅
  ❌ Checksum mismatch (entire file corrupted)
  📁 File kept (2MB)
  🔄 Retry

Attempt 2:
  📍 Resume from 0 (because plannedChunkStates is empty or starts from 0)
  ✅ Download starts from beginning
  ✅ Overwrites previous file
  ❌ Checksum mismatch (again)
  🔄 Retry
```

### Problems with this approach:
- If corruption is in first section, retry may download same corruption again
- Corrupted file occupies disk space
- Resuming from corrupted file is meaningless

---

## Detecting Corrupted Section (Advanced)

### Method 1: Incremental Checksum (Complex)
```
Idea:
  - Checksum each chunk separately
  - If chunk 0 is valid, keep it
  - Only re-download corrupted chunks

Example:
  Chunk 0 (0-666KB): Checksum ✅ → Keep
  Chunk 1 (666KB-1.3MB): Checksum ❌ → Re-download
  Chunk 2 (1.3MB-2MB): Checksum ✅ → Keep
```

**Problem**: 
- Complex
- Requires checksum for each chunk
- Server must provide chunk-level checksum

### Method 2: Binary Search (Very Complex)
```
Idea:
  - Split file in half
  - Checksum each half
  - Find corrupted section
  - Only re-download that section

Example:
  First half (0-1MB): Checksum ✅
  Second half (1MB-2MB): Checksum ❌
  → Only re-download second half
```

**Problem**:
- Very complex
- Requires multiple checksum calculations
- Performance overhead

### Method 3: Complete Deletion and Retry (Simple and Effective) ⭐
```
Idea:
  - If checksum mismatch → delete file
  - Download from start
  - Simple and reliable

Example:
  Attempt 1: Checksum mismatch
  🗑️ File deleted
  Attempt 2: Download from start
```

**Advantages**:
- ✅ Simple
- ✅ Reliable
- ✅ Prevents corruption
- ✅ Best practice (IDM behavior)

---

## Best Practice: IDM Behavior

### Checksum Mismatch:
```
Attempt 1:
  ✅ Download complete (2MB)
  ❌ Checksum mismatch
  🗑️ File deleted
  ⏱️ Wait 2s
  🔄 Retry from start

Attempt 2:
  ✅ Download complete (2MB) - from start
  ✅ Checksum verified
  ✅ Success!
```

### Network Error:
```
Attempt 1:
  ❌ Connection timeout (50% downloaded)
  📁 File kept
  ⏱️ Wait 2s
  🔄 Retry (resume from 50%)

Attempt 2:
  ✅ Resume from 50%
  ✅ Download complete
  ✅ Success!
```

---

## Why can't we detect which section is corrupted?

### Main problem:
```
Checksum is for entire file, not each chunk!

Example:
  Expected: "abc123..." (SHA256 of entire file)
  Actual:   "def456..." (SHA256 of downloaded file)
  
  → We know file is corrupted
  → But we don't know which section is corrupted!
```

### Possible solutions:

#### 1. Chunk-level Checksum (Requires server)
```
If server provides checksum for each chunk:
  Chunk 0: checksum="xxx"
  Chunk 1: checksum="yyy"
  Chunk 2: checksum="zzz"
  
  → We can validate each chunk separately
  → Only re-download corrupted chunk
```

**Problem**: Most servers don't provide this

#### 2. Incremental Hash (During download)
```
During download:
  - Hash each chunk
  - If hash differs from expected → stop immediately
  - Restart from that chunk
```

**Problem**: Requires checksum for each chunk (server must provide)

#### 3. Complete deletion (Simple and effective) ⭐
```
If checksum mismatch:
  → Delete file
  → Download from start
```

**Advantages**: Simple, reliable, best practice

---

## Suggested Improvements

### 1. Differentiate Between Errors
```kotlin
// Network Error → Resume possible
catch (error: NetworkException) {
    // File kept
    // Resume from last position
    retryWithResume()
}

// Integrity Error → Must download from start
catch (error: IntegrityValidationException) {
    // File deleted
    // Retry from start
    deleteFileAndRetryFromStart()
}
```

### 2. Delete File Before Retry
```kotlin
if (!integrityResult.isValid) {
    // Delete corrupted file
    if (resolution.file.exists()) {
        resolution.file.delete()
        Log.d(TAG, "Deleted corrupted file for retry")
    }
    
    // Reset chunk states
    chunkStateSnapshots.remove(handle.id)
    lastProgress.remove(handle.id)
    
    throw IntegrityValidationException(...)
}
```

### 3. Retry from Start (Not Resume)
```kotlin
catch (error: IntegrityValidationException) {
    if (attempt < maxAttempts) {
        // Reset states
        plannedChunkStates = emptyList()  // from start
        startOffset = 0L  // from start
        
        listeners.forEach { it.onRetry(handle, attempt) }
        delay(delayMs)
        attempt++
    }
}
```

---

## Method Comparison

| Method | Complexity | Accuracy | Performance | Best Practice |
|--------|------------|----------|-------------|---------------|
| **Complete deletion** | ⭐ Simple | ⭐⭐⭐ Excellent | ⭐⭐ Good | ✅ IDM |
| **Incremental Hash** | ⭐⭐⭐ Complex | ⭐⭐⭐ Excellent | ⭐ Medium | ⚠️ Requires server |
| **Binary Search** | ⭐⭐⭐⭐ Very complex | ⭐⭐⭐ Excellent | ⭐ Weak | ❌ Overhead |
| **Resume from corrupted file** | ⭐ Simple | ❌ Invalid | ⭐⭐⭐ Excellent | ❌ Wrong |

---

## Conclusion

### Current status:
- ❌ Corrupted file kept
- ❌ Resume from corrupted file (meaningless)
- ❌ May download corruption again

### Best Practice:
1. ✅ **Checksum Mismatch** → Delete file + Retry from start
2. ✅ **Network Error** → Keep file + Resume
3. ✅ **Error differentiation** → Different behavior

### Recommendation:
- For **Checksum Mismatch**: Complete deletion and retry from start (simple and effective)
- For **Network Error**: Resume from last position (saves bandwidth)
- **Incremental Hash**: Only if server provides chunk-level checksum

**Summary**: We can't precisely determine which section is corrupted (because checksum is for entire file), so it's better to delete file and download from start.
