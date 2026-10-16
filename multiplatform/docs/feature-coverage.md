# Feature coverage

Status words: Existing, Partial, Missing, Blocked, Planned. Verification is Pass, Fail, Not run, or Pending.

The engine in this column is `multiplatform/engine` plus `desktopApp`. It is not the Android `:downloader` module.

| ID | Status | Module | Gap | Stage | Check | Result |
|---|---|---|---|---|---|---|
| DR-01 | Partial | OkHttpSpanDownloader | Parts write into one temp file at their offsets. Finish renames it. | 3 | JvmSpanDownloaderTest partsLandInPlace | Pass |
| DR-02 | Partial | OkHttpSpanDownloader | Temp file is in the destination folder and renamed. Cross-volume fallback is the error, not a silent second copy. | 3 | span-data is gone after success | Pass |
| DR-03 | Partial | BufferLoan | Four 64 KB buffers are shared. A part waits instead of allocating another. | 3 | diskFullBeforePartsKeepsThePreviousFile | Pass for the space check |
| DR-04 | Partial | BufferLoan | The reader uses one borrowed buffer and waits when the four are in use. A slow disk was not timed. | 3 | Borrow waits instead of growing | Not run |
| DR-05 | Partial | DiskPolicy | Auto flushes local disks every 1 MB and removable or network disks every 256 KB. The setting can force one of those. SSD and HDD are not guessed. | 3 | writePolicyFollowsTheDiskUnlessOverridden | Pass |
| DR-06 | Partial | DeskLibrary | Job list is saved on state changes, not per packet. | 3 | Metadata writes independent of packet count | Not run |
| DR-07 | Partial | span-plan | The plan is written only after the channel is forced. A crash between those two lines is not tested. | 3 | Crash cannot resume past the last plan | Not run |
| DR-08 | Partial | downloadPart | Each part forces and records its offset about once per megabyte and when the part ends. The interval is not a setting. | 3 | Configurable flush | Not run |
| DR-09 | Partial | SpanFileLog | Failures append to ~/Span/fetch.log. The token, bearer value, and URL password are replaced, and the file rotates. | 3 | failureLogDropsTheTokenAndRotates | Pass |
| DR-10 | Partial | fits | A full disk is refused before parts start and before an unknown-size body is written. Mid-download fill is not forced. | 3 | diskFullBeforePartsKeepsThePreviousFile, unknownSizeRefusesWhenTheDiskIsFull | Pass |
| DR-11 | Partial | SpanProgress.reservedBytes | The percent uses the checkpoint. Reserved bytes are labeled separately and are not the finished count. | 3 | reservedBytesStayAheadOfTheCheckpoint | Pass |
| DR-12 | Partial | span-plan | Bytes written past the plan are fetched again from the saved offset. A reboot of the desktop process is not part of this test. | 3 | resumeIgnoresBytesWrittenAfterThePlan | Pass |
| DR-13 | Partial | disk stamp | The volume id is read at the start and again before the plan is updated. A different id keeps the previous file. A real USB eject was not used. | 3 | aDifferentDiskDoesNotReplaceTheFile, aMissingDiskStopsBeforeWriting | Pass |
| DR-14 | Partial | publish | A bad checksum does not replace the previous file. Cancel during parts was not tested. | 3 | badChecksumKeepsThePreviousFile | Pass |
| DR-15 | Partial | DiskHealth | The status line says Health N/A. A thrown or empty probe is not turned into a number. | 3 | missingDiskHealthIsNotANumber | Pass |
| RT-01 | Partial | Add dialog | URL, name, folder, and probe exist. | 2 | One URL creates one id | Not run |
| RT-02 | Partial | BatchIntake | Each line is reviewed before Add. A refused line does not stop later http lines. | 4 | aBadBatchLineDoesNotStopTheNext | Pass |
| RT-03 | Partial | DropIntake | A dropped text file yields http links. A script is not opened. | 4 | droppedScriptIsNotOpened | Pass |
| RT-04 | Partial | listenForBrowser | Local bridge exists. No browser extension is shipped. | 4 | Unsupported browser explains itself | Not run |
| RT-05 | Partial | clipboard watcher | Optional. Non-links are not enqueued. | 4 | Duplicate offers are quiet | Not run |
| RT-06 | Partial | OkHttpSpanDownloader | A 200 body is not appended onto an existing part. | 3 | Range mismatch fails | Not run |
| RT-07 | Partial | stop and remove | Stop keeps the partial. Delete file is a separate checkbox. | 2 | Stop does not erase | Not run |
| RT-08 | Partial | SpanRules | Part bounds and a minimum piece size exist. | 3 | Ranges do not overlap | Not run |
| RT-09 | Partial | DeskSettings | maxConcurrent is stored. Enforcement is not audited here. | 4 | Split count cannot raise the file cap | Not run |
| RT-10 | Partial | saveQueues | Queue order is written in list order and read back in that order. | 4 | placeQueue | Pass for the order function |
| RT-11 | Partial | DeskQueue | Each queue has a cap and an hour window. | 4 | One queue edit leaves the other | Not run |
| RT-12 | Partial | FairShare | A low-priority download that has waited takes the open slot. Priority is a session value. | 4 | aWaitingLowPriorityDownloadStillRuns | Pass |
| RT-13 | Partial | settings speed | The global cap is copied onto a new job. | 4 | Aggregate near the cap | Not run |
| RT-14 | Partial | DeskJob.speedLimit | Per-job limit is sent on that request. | 4 | Other jobs stay unchanged | Not run |
| RT-15 | Partial | Admission | A host that already has its cap does not start another download. A per-host rule replaces the global cap. | 4 | advancedNotesStayInsideTheEvidence | Pass |
| RT-16 | Partial | queueOpen | The computer's local hour opens or closes the queue, including an overnight window. A missed hour stays closed. Daylight saving is the hour the clock already reports. | 4 | aClosedHourStaysClosed | Pass |
| RT-17 | Partial | RetryAfter | A 429 with Retry-After waits at least that long. An unreadable value falls back to the normal backoff. | 4 | retryAfterIsReportedOnce | Pass |
| RT-18 | Partial | StallWatch | Frozen bytes are stalled. Bytes still landing on disk are a slow disk, not a stall. The socket is not cancelled. | 4 | slowDiskIsNotAStall | Pass |
| RT-19 | Partial | ReconnectPlan | Startup resumes only queued or running work. A paused or failed download stays as it was. | 4 | reconnectLeavesAPausedDownloadPaused | Pass |
| RT-20 | Partial | WakePlan | A running download is not started again. Sleep and wake of the machine were not observed. | 4 | reconnectLeavesAPausedDownloadPaused | Pass for the guard |
| RT-21 | Partial | BatteryPlan | Optional. On battery, work pauses with that label. A manual pause stays paused. If the power source cannot be read, nothing is paused. | 4 | batteryPolicyDoesNotResumeAManualPause | Pass |
| RT-22 | Partial | settings proxy | Host and port exist. Credentials are not in a secret store. | 4 | Proxy errors are distinct | Not run |
| RT-23 | Partial | LinkProbe | Basic and bearer are kept in memory for that job. | 4 | Bad credentials are explained | Not run |
| RT-24 | Partial | enqueue headers | Authorization is not written to downloads.txt. | 4 | Redirect does not leak the header | Not run |
| RT-25 | Partial | Redirects | Eight hops are followed. A repeated address ends the download. | 4 | redirectLoopEnds | Pass |
| RT-26 | Partial | SpanRules | FTP, SFTP, and magnet links are refused. An http file whose name ends in metalink is still a normal download, not a metalink client. | 4 | otherProtocolsAreRefused | Pass |
| RT-27 | Partial | categories | Extension folders exist. | 4 | User can override the folder | Not run |
| RT-28 | Partial | SpanRules.safeFileName | Unsafe names are stripped. | 4 | Name cannot leave the folder | Not run |
| RT-29 | Partial | overwrite | Overwrite or a unique name. | 4 | No silent race | Not run |
| RT-30 | Partial | enqueue | Same URL is refused while a job is still open. Name similarity is not treated as identity. | 4 | Similar name is not dropped | Not run |
| RT-31 | Partial | search and nav | Search is separate from the library filter. | 2 | Empty search is not an empty library | Not run |
| RT-32 | Partial | FetchHistory | Removing a row records it. Clear history deletes that list and leaves the file. | 4 | clearingHistoryLeavesTheFile | Pass |
| RT-33 | Partial | SettingsDocument | Export and import use schema 1. A bad file leaves the current settings. | 4 | badSettingsImportKeepsTheCurrentFile | Pass |
| RT-34 | Partial | finish and inspector | A blank checksum is labeled "No checksum provided". | 2 | Saved without a hash is not called verified | Not run |
| RT-35 | Partial | ScanLabel | The inspector says Not scanned. Nothing is called clean. | 4 | anUnscannedFileIsNotCalledClean | Pass |
| RT-36 | Partial | safeFileName | Path characters are stripped. Symlinks are not fully checked. | 4 | Server name stays in the folder | Not run |
| RT-37 | Partial | SettingsDocument | Export drops password, token, and authorization keys. There is no platform keychain. | 4 | badSettingsImportKeepsTheCurrentFile | Pass |
| RT-38 | Partial | tray | Open and Quit use the same process. | 2 | Tray does not create a second engine | Not run |
| RT-39 | Partial | CompletionPlan | Cancel, or a download that is still active, stops the sleep or shutdown. Other systems only notify. | 4 | shutdownCanBeCancelled | Pass |
| RT-40 | Partial | UpdatePlan | There is no update server. A signed update would still wait until downloads finish, and a bad signature is refused. | 4 | anUpdateWaitsForActiveDownloads | Pass |
| AD-01 | Partial | BottleneckJudge | The note is Network, Disk, or Unknown. Unknown stays Low confidence. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-02 | Partial | AdaptiveConnections | Off by default. When on, a stall can lower the count and cannot raise the cap. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-03 | Partial | LatencyLimit | A slow reply cuts this download's existing speed cap in half. Other programs are untouched. Unlimited downloads are not given a new cap. | 5 | aSlowReplyCutsOnlyThisDownloadsLimit | Pass |
| AD-04 | Partial | TemporaryPriority | Boost for 10 minutes, then the old priority returns. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-05 | Partial | DeadlineGuess | The finish line says the pace is not a promise. No pace means not enough evidence. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-06 | Partial | QuotaUse | Optional. A full useful-byte quota stops a new start. Retries are not added to that count. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-07 | Partial | NetworkProfile | Unmetered or Metered is chosen by the user. Metered limits a new download to two connections. The note says other programs are unchanged. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-08 | Partial | InterfaceBind | Binding to one network is refused because this build cannot do it. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-09 | Partial | LinkIdentity | A different tag or size is refused. A missing tag is not treated as the same file. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-10 | Partial | ProviderRefresh | Only the YouTube provider may refresh. Other names are refused. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-11 | Partial | Mirrors | The first reachable mirror is kept. A failed probe is skipped. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-12 | Partial | SameFile | Sources are combined only when every identity matches. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-13 | Partial | PieceRepair | A bad range is eligible only when both hashes exist and differ. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-14 | Partial | PartialImport | Only a v2 plan is a known layout. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-15 | Partial | HandoffBundle | Cookies and passwords are left out of the bundle. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-16 | Partial | ZipUnpack | The picker lists only entries that stay in the folder. A path that climbs out is not extracted. | 5 | aZipPathThatLeavesTheFolderIsNotExtracted | Pass |
| AD-17 | Partial | FetchPlayer | VLC or mpv plays a resolved URL. It is not a second download engine. | 5 | Unsupported media shows an error | Not run |
| AD-18 | Partial | WatchPreview | Only formats that actually return a URL are listed. | 5 | Missing audio-plus-video is not invented | Not run |
| AD-19 | Partial | DeltaUpdate | An invalid manifest patches nothing. A valid manifest is not applied by this build. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-20 | Partial | RuleBook | A cycle stops the preview. Saved rules are the extension-to-queue list. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-21 | Partial | PostStep | Scripts stay off. Turning the idea on still does not run a script. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-22 | Partial | ProjectPath | A relative path that climbs out of the root is refused. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-23 | Partial | DeviceAccess | A revoked token loses access. There is no remote transport. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-24 | Partial | LocalGate | The browser port accepts only this machine and only with the saved token. A blank token refuses everyone. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| AD-25 | Partial | TransferCounters | Useful, received, retries, and checkpoints are labeled separately. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| UX-01 | Partial | LinkOfferForm | Advanced fields stay behind Options. | 2 | Start does not require them | Not run |
| UX-02 | Partial | SegmentMap | Each part splits flushed bytes, bytes ahead of the checkpoint, and bytes waiting on a checksum. The counts come from those ranges. | 2 | aFullReceiveIsNotCalledReady | Pass |
| UX-03 | Partial | SpeedCause | A network stall can restart the download with one fewer connection. A slow disk is left running. One connection is not reduced further. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| UX-04 | Partial | PaceChart | The inspector draws bytes per second from samples newer than 60 seconds. Fewer than two samples stay blank. | 5 | oldPaceSamplesAreDropped | Pass |
| UX-05 | Partial | TransferStage | A full receive says Finalizing or Verifying. Ready is only after the file is saved. A checksum is not reported before the bytes stop. | 3 | aFullReceiveIsNotCalledReady, verifyStartsOnlyAfterTheBytesArrive | Pass |
| UX-06 | Partial | StatusLine | Free bytes of the destination folder. | 2 | Failure shows unavailable, not zero health | Not run |
| UX-07 | Partial | ActivityLog | The inspector lists state changes. A repeated receiving update is not appended. | 2 | aBadBatchLineDoesNotStopTheNext | Pass |
| UX-08 | Partial | placeQueue | Up and Down move a queue. A vertical drag uses the same order. Alt+Up and Alt+Down move the selected queue. | 5 | movingAQueueUpChangesTheOrder | Pass |
| UX-09 | Partial | MonthBoard | The queue calendar marks today and repeats the local hour window. It does not add events. | 5 | theCalendarMarksTodayAndDoesNotInventEvents | Pass |
| UX-10 | Partial | RuleBook | Tools, Rules shows the step chain before save. A loop is refused. A saved rule can send one extension to a named queue. Scripts stay off. | 5 | advancedNotesStayInsideTheEvidence | Pass |
| UX-11 | Partial | SettingPreview | The settings dialog lists what Save would change for disk flush, battery, quota, metered connections, speed cap, hour window, and the local token. The token text is not shown. Unchanged rows stay out of the list. | 2 | aSettingPreviewShowsOnlyWhatWouldChange | Pass |
| UX-12 | Partial | columnWidth | The name column can be dragged and the width is saved. Other columns stay fixed. | 2 | aDraggedNameColumnStaysReadable | Pass |
| UX-13 | Partial | DownloadCard | Name, state, progress, and pause are visible without hover. | 2 | Pause click does not also select away | Not run |
| UX-14 | Partial | command palette | Command-K runs Add, Pause, Resume, Needs attention, Settings, and Widget. It does not search downloads. | 2 | Focus returns after close | Not run |
| UX-15 | Partial | Inspector | One side panel. Selecting it does not call the engine. | 2 | Tab stays when another file is selected | Not run |
| UX-16 | Partial | SelectionBar | Pause and Resume show how many of the selection qualify. A partial failure is not listed per file. | 2 | Per-file failure report | Not run |
| UX-17 | Partial | Needs attention | Failed jobs are grouped by the message cause. Replace-link is not a real action yet. | 2 | Replace link keeps the partial | Not run |
| UX-18 | Partial | UndoBar | Removing a row can be undone while the file stays. Deleting the file has no undo. | 5 | undoPutsTheRowBackAndADeletedFileDoesNot | Pass |
| UX-19 | Partial | ShellMatrixTest | Add, Pause, and Search have names in the semantics tree. VoiceOver was not driven. | 6 | primaryControlsAreNamedForAScreenReader | Pass for the semantics tree |
| UX-20 | Partial | libraryRows | Ten thousand names filter in 24 ms. Row 9000 of the lazy card list is reachable in 35 ms. | 6 | tenThousandRowsFilterAndScroll | Pass |
| RZ-01 | Partial | windowState.size | The mode uses dp from the Compose window. | 2 | Not the monitor pixel size | Not run |
| RZ-02 | Partial | ShellMatrixTest | Wide, standard, compact, and widget are captured in docs/shots. | 2 | clientSizesKeepTheAddControlInside | Pass for the test scene |
| RZ-03 | Partial | resolveShell | Width or height can enter the widget. | 2 | 599 and height 359 | Pass for the function |
| RZ-04 | Partial | resolveShell | Exit needs 680 and 420. | 2 | 620 by 480 stays widget | Pass for the function |
| RZ-05 | Partial | ShellMatrixTest | One live download kept a single request while the test scene changed from wide to widget. The packaged window was not resized. | 2 | aLiveDownloadSurvivesAResize | Pass for the test scene |
| RZ-06 | Partial | rememberLazyListState | Filter, sort, inspector tab, and list scroll live above the layout branch. Dialog text across a resize was not tested. | 2 | Dialog text survives a resize | Not run |
| RZ-07 | Partial | clampSize | Expand and the saved size stay inside the work area. The function is tested. A detached monitor was not. | 2 | DeskFrameTest restoredSizeStaysInsideTheWorkArea | Pass for the function |
| RZ-08 | Partial | minimumSize | AWT minimum is 360 by 280 because the title bar is outside the client. | 2 | 320 by 200 client measured | Not run |
| RZ-09 | Partial | pinnedId | Pin lasts for this session. Unknown size has no fake percent. | 2 | Completed pin is not replaced | Not run |
| RZ-10 | Partial | manualWidget | Manual mode ignores size until Expand. | 2 | Growing the window does not leave manual mode | Pass for the function |
| RZ-11 | Partial | ShellMatrixTest | Density, 200 percent text, and RTL keep Add inside the client. A screen reader was not used. | 6 | clientSizesKeepTheAddControlInside | Pass for the test scene |
| RZ-12 | Partial | tray | Close hides the window and explains once. Quit exits. Always-on-top is still the separate floating window. | 2 | Close and Quit are different | Not run |
