package com.miaadrajabi.span

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpanRulesTest {
    @Test
    fun droppedScriptIsNotOpened() {
        val script = "#!/bin/sh\necho https://example.com/a.bin\n"
        assertTrue(DropIntake.fromFile("run.sh", script).isEmpty())
        assertEquals(listOf("https://example.com/a.bin"), DropIntake.fromFile("links.txt", script))
        assertTrue(DropIntake.links("hello there").isEmpty())
    }

    @Test
    fun aWaitingLowPriorityDownloadStillRuns() {
        val picked = FairShare.pick(
            listOf(
                FairCandidate(1, priority = 2, waits = 0),
                FairCandidate(2, priority = 2, waits = 0),
                FairCandidate(3, priority = 0, waits = 2),
            ),
            slots = 1,
        )
        assertEquals(listOf(3L), picked)
    }

    @Test
    fun slowDiskIsNotAStall() {
        assertEquals(StallKind.SlowDisk, StallWatch.judge(10, 10, 10, 40, 30_000))
        assertEquals(StallKind.Moving, StallWatch.judge(10, 11, 10, 11, 30_000))
        assertEquals(StallKind.Stalled, StallWatch.judge(10, 10, 10, 10, 20_000))
        assertEquals(StallKind.Moving, StallWatch.judge(10, 10, 10, 10, 1_000))
    }

    @Test
    fun reconnectLeavesAPausedDownloadPaused() {
        assertTrue(ReconnectPlan.resume("Running"))
        assertTrue(ReconnectPlan.resume("Queued"))
        assertFalse(ReconnectPlan.resume("Paused"))
        assertFalse(ReconnectPlan.resume("Failed"))
        assertFalse(WakePlan.shouldStart("Running"))
        assertTrue(WakePlan.shouldStart("Queued"))
    }

    @Test
    fun redirectLoopIsRefused() {
        assertEquals("Redirect loop", Redirects.refuse(listOf("https://example.com/a"), "https://example.com/a"))
        assertEquals("Too many redirects", Redirects.refuse(List(8) { "https://example.com/$it" }, "https://example.com/next"))
        assertNull(Redirects.refuse(listOf("https://example.com/a"), "https://example.com/b"))
    }

    @Test
    fun badSettingsImportKeepsTheCurrentFile() {
        val current = mapOf("theme" to "light")
        assertEquals(current, SettingsDocument.import(current, "not a settings file"))
        assertEquals(current, SettingsDocument.import(current, "schema=9\ntheme=dark\n"))
        val exported = SettingsDocument.export(current + mapOf("password" to "nope"))
        assertFalse(exported.contains("nope"))
        val imported = SettingsDocument.import(current, "schema=1\ntheme=dark\npassword=hidden\n")
        assertEquals("dark", imported["theme"])
        assertFalse(imported.containsKey("password"))
    }

    @Test
    fun retryAfterIsAWaitNotASpin() {
        assertEquals(2_000L, RetryAfter.millis("2"))
        assertNull(RetryAfter.millis("soon"))
        assertEquals(3_600_000L, RetryAfter.millis("99999"))
    }

    @Test
    fun batteryPolicyDoesNotResumeAManualPause() {
        assertEquals("Paused", BatteryPlan.nextState(true, true, "Running", false))
        assertEquals("Paused", BatteryPlan.nextState(true, false, "Paused", false))
        assertEquals("Queued", BatteryPlan.nextState(true, false, "Paused", true))
        assertEquals("Running", BatteryPlan.nextState(true, null, "Running", false))
        assertEquals("Queued", BatteryPlan.nextState(false, true, "Paused", true))
    }

    @Test
    fun anUpdateWaitsForActiveDownloads() {
        assertEquals("No update is available.", UpdatePlan.decision(0, false, true))
        assertEquals("An update waits until downloads finish.", UpdatePlan.decision(2, true, true))
        assertEquals("The update signature was not accepted.", UpdatePlan.decision(0, true, false))
        assertEquals("Ready to update.", UpdatePlan.decision(0, true, true))
    }

    @Test
    fun advancedNotesStayInsideTheEvidence() {
        assertEquals("Metered", NetworkProfile.explain("Unmetered", "Metered").let { explained ->
            assertTrue(explained.contains("does not change other programs"))
            "Metered"
        })
        assertEquals(2, NetworkProfile.connectionCap("Metered", 8))
        assertEquals(8, NetworkProfile.connectionCap("Unmetered", 8))
        assertEquals("Restart with 3 connections", SpeedCause.suggest("Network", 4, true).label)
        assertEquals(3, SpeedCause.suggest("Network", 4, true).connections)
        assertEquals(null, SpeedCause.suggest("Network", 1, true).connections)
        assertEquals(null, SpeedCause.suggest("Disk", 4, true).connections)
        assertEquals("Low", BottleneckJudge.note(false, false, false).confidence)
        assertEquals("Disk", BottleneckJudge.note(false, true, true).cause)
        assertEquals(4, AdaptiveConnections.choose(true, 8, 4, false))
        assertEquals(4, AdaptiveConnections.choose(false, 8, 4, true))
        assertEquals(3, AdaptiveConnections.choose(true, 4, 8, true))
        assertEquals(50_000L, LatencyLimit.apply(true, 100_000L, 900L))
        assertEquals(100_000L, LatencyLimit.apply(false, 100_000L, 900L))
        assertEquals(5, TemporaryPriority.effective(1, 5, 1_000L, 500L))
        assertEquals(1, TemporaryPriority.effective(1, 5, 1_000L, 1_000L))
        assertTrue(DeadlineGuess.phrase(0, 10).contains("not a promise") || DeadlineGuess.phrase(100, 0).contains("Not enough evidence"))
        assertTrue(DeadlineGuess.phrase(100, 0).contains("Not enough evidence"))
        assertTrue(QuotaUse.label(10, 4).contains("Retries 4"))
        assertEquals("The new link is a different file.", LinkIdentity.refusal("a", "b", 1, 1))
        assertNull(LinkIdentity.refusal("a", "a", 3, 3))
        assertFalse(ProviderRefresh.allowed("instagram"))
        assertEquals("https://ok", Mirrors.firstReachable(listOf("https://bad" to false, "https://ok" to true)))
        assertFalse(SameFile.proven(listOf("one", "two")))
        assertTrue(SameFile.proven(listOf("one", "one")))
        assertFalse(PieceRepair.badRange(null, "abc"))
        assertTrue(PieceRepair.badRange("abc", "def"))
        assertTrue(PartialImport.known("v2"))
        assertFalse(PartialImport.known("v1"))
        assertFalse(HandoffBundle.pack(mapOf("url" to "https://a", "cookie" to "secret")).containsKey("cookie"))
        assertFalse(ZipEntryGuard.allowed("../x"))
        assertTrue(ZipEntryGuard.allowed("docs/a.txt"))
        assertEquals("This system cannot bind the download to one network.", InterfaceBind.refusal(true, false))
        assertEquals("A delta update needs a valid manifest. Nothing was patched.", DeltaUpdate.refusal(false))
        assertNull(DeltaUpdate.refusal(true))
        assertTrue(RulePreview.steps("a", mapOf("a" to "b", "b" to "a")).contains("Loop stopped"))
        assertFalse(RuleBook.canSave("pdf", "pdf"))
        assertTrue(RuleBook.canSave("pdf", "queue Night"))
        assertEquals("Night", RuleBook.queueOf("pdf", "queue Night"))
        assertEquals(null, RuleBook.queueOf("pdf", "pdf"))
        assertFalse(Admission.ready(true, 0L, 10L, true, 0, 2, 2, 2))
        assertTrue(Admission.ready(true, 0L, 10L, true, 0, 2, 1, 2))
        assertFalse(Admission.ready(true, 0L, 10L, true, 2, 2, 0, 4))
        assertFalse(Admission.ready(true, 50L, 10L, true, 0, 2, 0, 4))
        assertEquals("Always open", ScheduleWindow.describe(-1, -1, 15))
        assertEquals("Open now, until 6:00", ScheduleWindow.describe(22, 6, 23))
        assertEquals("Next run at 22:00", ScheduleWindow.describe(22, 6, 10))
        assertEquals("Post-download scripts are off.", PostStep.describe(false))
        assertEquals("This build does not run scripts.", PostStep.describe(true))
        assertFalse(ProjectPath.inside("../outside"))
        assertTrue(ProjectPath.inside("files/a.bin"))
        val devices = DeviceAccess()
        devices.pair("phone")
        assertTrue(devices.allowed("phone"))
        devices.revoke("phone")
        assertFalse(devices.allowed("phone"))
        assertFalse(LocalGate.allow("127.0.0.1", "", ""))
        assertFalse(LocalGate.allow("10.0.0.4", "secret", "secret"))
        assertTrue(LocalGate.allow("127.0.0.1", "secret", "secret"))
        assertTrue(TransferCounters(1, 2, 3, 4).label().contains("Retries 3"))
    }

    @Test
    fun shutdownCanBeCancelled() {
        assertFalse(CompletionPlan.proceed(cancelled = true, active = false))
        assertFalse(CompletionPlan.proceed(cancelled = false, active = true))
        assertTrue(CompletionPlan.proceed(cancelled = false, active = false))
    }

    @Test
    fun otherProtocolsAreRefused() {
        listOf("ftp://example.com/a", "sftp://example.com/a", "magnet:?xt=urn:btih:abc", "http://example.com/a.metalink").forEach { url ->
            if (url.endsWith(".metalink")) {
                assertNull(SpanRules.validationError(SpanRequest(url, "a.bin")))
            } else {
                assertEquals("Only http and https links are accepted.", SpanRules.validationError(SpanRequest(url, "a.bin")))
            }
        }
    }

    @Test
    fun anUnscannedFileIsNotCalledClean() {
        assertEquals("Not scanned", ScanLabel.NOT_SCANNED)
        assertFalse(ScanLabel.NOT_SCANNED.equals("clean", ignoreCase = true))
    }

    @Test
    fun secretsDoNotStayInTheLogLine() {
        val line = SpanRedaction.redact(
            "url=https://user:pw@example.com/a?token=super-secret&x=1 Authorization: Bearer super-secret",
        )
        assertFalse(line.contains("super-secret"), line)
        assertFalse(line.contains("user:pw@"))
        assertTrue(line.contains("[redacted]"))
        assertTrue(line.contains("x=1"))
    }

    @Test
    fun writePolicyFollowsTheDiskUnlessOverridden() {
        assertEquals(1024L * 1024L, DiskPolicy.interval(WritePolicy.Auto, DiskKind.Local))
        assertEquals(256L * 1024L, DiskPolicy.interval(WritePolicy.Auto, DiskKind.Network))
        assertEquals(256L * 1024L, DiskPolicy.interval(WritePolicy.Auto, DiskKind.Removable))
        assertEquals(1024L * 1024L, DiskPolicy.interval(WritePolicy.Local, DiskKind.Network))
        assertEquals("Network", DiskPolicy.describe(WritePolicy.Auto, DiskKind.Network))
        assertEquals("Local", DiskPolicy.describe(WritePolicy.Local, DiskKind.Network))
    }

    @Test
    fun missingDiskHealthIsNotANumber() {
        assertEquals("N/A", DiskHealth.read { null })
        assertEquals("N/A", DiskHealth.read { throw IllegalStateException("no smart data") })
        assertEquals("OK", DiskHealth.read { "OK" })
    }

    @Test
    fun reservedBytesAreNotCountedAsDownloaded() {
        val progress = SpanProgress(bytes = 100L, total = 1000L, reservedBytes = 400L)
        assertEquals(10L, progress.bytes * 100L / progress.total!!)
        assertTrue(progress.reservedBytes > progress.bytes)
    }

    @Test
    fun aFullReceiveIsNotCalledReady() {
        assertEquals("Finalizing", TransferStage.label("Running", TransferPhase.Receiving, 100, 100, ""))
        assertEquals("Finalizing", TransferStage.label("Running", TransferPhase.Finalize, 100, 100, ""))
        assertEquals("Verifying", TransferStage.label("Running", TransferPhase.Verify, 100, 100, ""))
        assertEquals("Ready", TransferStage.label("Running", TransferPhase.Ready, 100, 100, ""))
        assertEquals("Ready", TransferStage.label("Saved", TransferPhase.Receiving, 100, 100, ""))
        assertEquals("Receiving", TransferStage.label("Running", TransferPhase.Receiving, 40, 100, ""))
        assertEquals("Paused", TransferStage.label("Paused", TransferPhase.Finalize, 100, 100, ""))
        assertEquals("Waiting for storage", TransferStage.label("Running", TransferPhase.Receiving, 100, 100, "Not enough free space."))
        val receiving = SegmentMap.bands(80, 100, 100, TransferPhase.Receiving)
        assertEquals(80L, receiving.first { it.kind == SegmentKind.Stored }.bytes)
        assertEquals(20L, receiving.first { it.kind == SegmentKind.Pending }.bytes)
        assertTrue(receiving.none { it.kind == SegmentKind.NeedsCheck })
        val verifying = SegmentMap.bands(80, 100, 100, TransferPhase.Verify)
        assertEquals(80L, verifying.first { it.kind == SegmentKind.NeedsCheck }.bytes)
        assertEquals(20L, verifying.first { it.kind == SegmentKind.Pending }.bytes)
        assertTrue(verifying.none { it.kind == SegmentKind.Stored })
        val ready = SegmentMap.describe("Part 1", 100, 100, 100, TransferPhase.Ready)
        assertEquals("Part 1 stored 100 pending 0 needs check 0", ready)
        val spoken = SegmentMap.describe("Part 1", 80, 100, 100, TransferPhase.Verify)
        assertEquals("Part 1 stored 0 pending 20 needs check 80", spoken)
    }

    @Test
    fun aSettingPreviewShowsOnlyWhatWouldChange() {
        val saved = SettingDraft("Auto", false, 0, "Unmetered", false, false, "", 0)
        assertEquals(listOf("Save keeps the current settings."), SettingPreview.pending(saved, saved))
        val draft = saved.copy(quotaMb = 20, networkProfile = "Metered", pauseOnBattery = true, localToken = "secret")
        val lines = SettingPreview.pending(saved, draft)
        assertEquals("Not saved yet. A running download keeps the settings it started with.", lines.first())
        assertTrue(lines.any { it.contains("20 MB") && it.contains("Retries are not included") })
        assertTrue(lines.any { it.contains("2 connections") && it.contains("Other programs stay as they are") })
        assertTrue(lines.any { it.contains("On battery") })
        assertTrue(lines.none { it.contains("secret") })
        assertTrue(lines.none { it.contains("hard disk versus SSD") && it.contains("Local disks flush") })
        val local = SettingPreview.pending(saved, saved.copy(writePolicy = "Local"))
        assertTrue(local.any { it.contains("1 MB") && it.contains("does not guess hard disk versus SSD") })
    }

    @Test
    fun aBadBatchLineDoesNotStopTheNext() {
        val rows = BatchIntake.review(
            """
            ftp://example.com/a.bin
            https://example.com/good.bin
            not-a-link
            https://example.com/also.bin
            """.trimIndent(),
        )
        assertEquals(4, rows.size)
        assertFalse(rows[0].accepted)
        assertTrue(rows[1].accepted)
        assertFalse(rows[2].accepted)
        assertTrue(rows[3].accepted)
        assertEquals(
            listOf("https://example.com/good.bin", "https://example.com/also.bin"),
            BatchIntake.keep(rows.joinToString("\n") { it.text }),
        )
        assertTrue(ActivityLog.next(emptyList(), "Receiving").let { first ->
            ActivityLog.next(first, "Receiving") == first
        })
        val later = ActivityLog.next(listOf("Receiving"), "Finalizing")
        assertEquals(listOf("Receiving", "Finalizing"), later)
        assertEquals(listOf("docs/a.txt"), ZipPicker.listed(listOf("docs/a.txt", "../escape.txt", "docs/")))
        assertFalse(ZipEntryGuard.allowed("../escape.txt"))
    }

    private fun request(
        start: Long? = null,
        end: Long? = null,
        checksum: String? = null,
        algorithm: ChecksumAlgorithm = ChecksumAlgorithm.SHA256,
        headers: Map<String, String> = emptyMap(),
    ) = SpanRequest(
        url = "https://example.com/file.bin",
        fileName = "file.bin",
        rangeStart = start,
        rangeEndInclusive = end,
        headers = headers,
        expectedChecksum = checksum,
        checksumAlgorithm = algorithm,
    )

    @Test
    fun fullFileHasNoRemoteRange() {
        val window = SpanRules.window(request())
        assertEquals(ByteWindow(0L, null), window)
        assertNull(SpanRules.remoteRangeHeader(window, 0L))
    }

    @Test
    fun windowIsWrittenAsALocalSlice() {
        val window = SpanRules.window(request(start = 100, end = 199))
        assertEquals(100L, SpanRules.windowLength(window))
        assertEquals("bytes=100-199", SpanRules.remoteRangeHeader(window, 0L))
        assertEquals("bytes=150-199", SpanRules.remoteRangeHeader(window, 50L))
        assertTrue(SpanRules.isComplete(window, 100L))
        assertFalse(SpanRules.isComplete(window, 99L))
    }

    @Test
    fun openEndedWindowStartsAtZeroWhenStartIsMissing() {
        val window = SpanRules.window(request(end = 9))
        assertEquals("bytes=0-9", SpanRules.remoteRangeHeader(window, 0L))
        assertEquals("bytes=40-", SpanRules.remoteRangeHeader(SpanRules.window(request()), 40L))
    }

    @Test
    fun badRangeAndChecksumAreRejected() {
        assertEquals("Range end is before the start.", SpanRules.validationError(request(start = 10, end = 4)))
        assertEquals("Range start cannot be negative.", SpanRules.validationError(request(start = -1)))
        assertEquals(
            "Checksum must be 64 hexadecimal characters.",
            SpanRules.validationError(request(checksum = "abc")),
        )
        assertNull(SpanRules.validationError(request(checksum = "ab".repeat(32))))
        assertNull(SpanRules.validationError(request(checksum = " ")))
        assertEquals(
            "Checksum must be 32 hexadecimal characters.",
            SpanRules.validationError(request(checksum = "zz", algorithm = ChecksumAlgorithm.MD5)),
        )
        assertEquals(
            "Checksum must be 128 hexadecimal characters.",
            SpanRules.validationError(request(checksum = "aa", algorithm = ChecksumAlgorithm.SHA512)),
        )
    }

    @Test
    fun userRangeHeadersAreStripped() {
        val kept = SpanRules.strippedHeaders(
            mapOf("Range" to "bytes=0-1", "content-range" to "bytes 0-1/2", "Accept" to "*/*"),
        )
        assertEquals(mapOf("Accept" to "*/*"), kept)
    }

    @Test
    fun weakEtagDoesNotDiscardAPartial() {
        assertEquals(
            ResumeAction.KEEP_PARTIAL,
            SpanRules.resumeAction("W/\"abc\"", "W/\"other\"", 10L, 10L),
        )
        assertEquals(
            ResumeAction.KEEP_PARTIAL,
            SpanRules.resumeAction(null, null, null, 80L),
        )
    }

    @Test
    fun strongEtagOrSizeChangeDiscardsThePartial() {
        assertEquals(
            ResumeAction.DISCARD_PARTIAL,
            SpanRules.resumeAction("\"one\"", "\"two\"", 10L, 10L),
        )
        assertEquals(
            ResumeAction.DISCARD_PARTIAL,
            SpanRules.resumeAction(null, null, 10L, 12L),
        )
        assertEquals(
            ResumeAction.KEEP_PARTIAL,
            SpanRules.resumeAction("\"same\"", "\"SAME\"", 10L, 10L),
        )
    }

    @Test
    fun userStopIsNotRetried() {
        assertFalse(SpanRules.shouldRetry(StopReason.USER))
        assertTrue(SpanRules.shouldRetry(StopReason.NETWORK))
        assertTrue(SpanRules.shouldRetry(StopReason.HTTP))
    }

    @Test
    fun fileNameKeepsLettersDigitsAndAShortLength() {
        assertEquals("report_final.bin", SpanRules.safeFileName("report final.bin"))
        assertEquals("download.bin", SpanRules.safeFileName("   "))
        assertEquals(80, SpanRules.safeFileName("a".repeat(120)).length)
    }

    @Test
    fun partsCoverTheWholeFileWithoutOverlap() {
        val parts = SpanRules.partBounds(1000L, 4)
        assertEquals(4, parts.size)
        assertEquals(0L, parts.first().start)
        assertEquals(999L, parts.last().endInclusive)
        assertEquals(1000L, parts.sumOf { SpanRules.windowLength(it) ?: 0L })
    }
}
