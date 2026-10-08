package com.miaadrajabi.downloader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {

    @Test
    fun matchingPartialResponseIsWritable() {
        assertEquals(
            RangeOutcome.WriteAtOffset,
            classifyRangeResponse(
                requestedStart = 10L,
                requestedEndInclusive = 19L,
                statusCode = 206,
                contentRangeHeader = "bytes 10-19/100",
                knownTotal = 100L,
                localFileLength = 10L
            )
        )
    }

    @Test
    fun fullResponseAtNonZeroOffsetRestarts() {
        assertEquals(
            RangeOutcome.RestartFromZero,
            classifyRangeResponse(
                requestedStart = 10L,
                requestedEndInclusive = 19L,
                statusCode = 200,
                contentRangeHeader = null,
                knownTotal = 100L,
                localFileLength = 10L
            )
        )
    }

    @Test
    fun wrongContentRangeRestarts() {
        assertEquals(
            RangeOutcome.RestartFromZero,
            classifyRangeResponse(
                requestedStart = 0L,
                requestedEndInclusive = 9L,
                statusCode = 206,
                contentRangeHeader = "bytes 5-14/100",
                knownTotal = 100L,
                localFileLength = 0L
            )
        )
    }

    @Test
    fun malformedContentRangeRestarts() {
        assertNull(parseContentRange("bytes garbage"))
        assertEquals(
            RangeOutcome.RestartFromZero,
            classifyRangeResponse(
                requestedStart = 0L,
                requestedEndInclusive = 9L,
                statusCode = 206,
                contentRangeHeader = "not-a-range",
                knownTotal = null,
                localFileLength = 0L
            )
        )
    }

    @Test
    fun rangeNotSatisfiableCompletesOnlyWhenTheLocalFileMatches() {
        assertEquals(
            RangeOutcome.AlreadyComplete,
            classifyRangeResponse(50L, 99L, 416, null, 50L, 50L)
        )
        assertEquals(
            RangeOutcome.RestartFromZero,
            classifyRangeResponse(50L, 99L, 416, null, 50L, 20L)
        )
    }

    @Test
    fun weakEtagDoesNotCountAsIdentity() {
        assertNull(strongEtag("W/\"abc\""))
        assertEquals("\"abc\"", strongEtag("\"abc\""))
    }

    @Test
    fun changedStrongValidatorRestartsAndMissingValidatorContinues() {
        val saved = ArtifactValidators(strongEtag = "\"one\"", totalBytes = 4L)
        assertEquals(
            ValidatorDecision.RestartFromZero,
            validatorDecision(saved, "\"two\"", 4L)
        )
        assertEquals(
            ValidatorDecision.ContinuePartial,
            validatorDecision(ArtifactValidators(totalBytes = 4L), null, 4L)
        )
        assertEquals(
            ValidatorDecision.RestartFromZero,
            validatorDecision(saved, "\"one\"", 8L)
        )
    }

    @Test
    fun checksumFormatRejectsTheWrongShapeAndKeepsAnOmittedChecksum() {
        assertEquals(
            "ab".repeat(32),
            validatedChecksum("AB".repeat(32), ChecksumAlgorithm.SHA256)
        )
        assertNull(validatedChecksum(null, ChecksumAlgorithm.SHA256))
        assertNull(validatedChecksum("  ", ChecksumAlgorithm.SHA256))
        try {
            validatedChecksum("abc", ChecksumAlgorithm.SHA256)
            throw AssertionError("malformed checksum was accepted")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("SHA256"))
        }
        try {
            parseChecksumAlgorithm("SHA-256", true)
            throw AssertionError("unknown algorithm was accepted")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("SHA-256"))
        }
    }

    @Test
    fun coverageRejectsHolesAndDoesNotTrustAFullLengthAlone() {
        val complete = listOf(
            ChunkStateData(0, 0L, 4L, 5L),
            ChunkStateData(1, 5L, 9L, 10L)
        )
        assertTrue(coverageIsComplete(complete, 10L))
        val hole = listOf(
            ChunkStateData(0, 0L, 3L, 4L),
            ChunkStateData(1, 6L, 9L, 10L)
        )
        assertFalse(coverageIsComplete(hole, 10L))
        assertFalse(chunkBytesAreComplete(ChunkStateData(0, 0L, 9L, 4L)))
        assertTrue(chunkBytesAreComplete(ChunkStateData(0, 0L, 9L, 10L)))
    }

    @Test
    fun partialWriteAdvancesByTheReturnedCount() {
        assertEquals(15L, advanceWritePosition(10L, 5))
        try {
            advanceWritePosition(10L, 0)
            throw AssertionError("zero-length write was accepted")
        } catch (error: java.io.IOException) {
            assertTrue(error.message!!.contains("no progress"))
        }
    }

    @Test
    fun healthyOwnerRejectsASecondIdAndAbandonedOwnerStartsOver() {
        assertEquals(
            Admission.RejectBusy,
            admitDownload(
                sameIdState = null,
                sameIdSameArtifact = true,
                pathOwnerState = DownloadRecovery.RUNNING,
                pathOwnerIsSameId = false
            )
        )
        assertEquals(
            Admission.ReplaceFromZero,
            admitDownload(
                sameIdState = null,
                sameIdSameArtifact = true,
                pathOwnerState = DownloadRecovery.FAILED,
                pathOwnerIsSameId = false
            )
        )
        assertEquals(
            Admission.ReuseActive,
            admitDownload(
                sameIdState = DownloadRecovery.RUNNING,
                sameIdSameArtifact = true,
                pathOwnerState = null,
                pathOwnerIsSameId = false
            )
        )
        assertEquals(
            Admission.RejectBusy,
            admitDownload(
                sameIdState = DownloadRecovery.PAUSED,
                sameIdSameArtifact = false,
                pathOwnerState = null,
                pathOwnerIsSameId = false
            )
        )
    }

    @Test
    fun canonicalPathUsesTheSameFileIdentity() {
        val file = File("download.bin")
        assertTrue(canonicalPath(file).endsWith("download.bin"))
    }
}
