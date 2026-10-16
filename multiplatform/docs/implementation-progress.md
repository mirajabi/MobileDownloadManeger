# Desktop Fetch progress

This file tracks the desktop app in `multiplatform/`. It does not describe the Android library in `:downloader`.

## Stage

Stage 1 baseline is recorded. Stage 2 shell behaviors in this checkpoint compile and the window-mode tests pass. Screenshots and the later UX tools are still open, so stage 2 is not visually closed. Stage 3 has the part-file write path. The rest of stage 3 and stages 4–6 are not done.

## Done in this checkpoint

- Window mode resolver with entry at under 600×360 and exit only at 680×420 or larger.
- Manual widget stays a widget until Expand.
- Wide navigation, standard icon rail, compact menu, and a widget face share the same job list.
- Command bar, download cards, inspector, status line, and needs-attention filter.
- Closing the window hides it. Quit is on the menu-bar icon.
- `DeskFrameTest` covers the mode boundaries.

## Stage 2 closed in this checkpoint

- Window size, card or table, density, and always-on-top are saved.
- Expand puts the window back inside the usable screen.
- Command-F focuses search. Command-K opens a command list that runs real actions.
- A multi-selection says how many of the selection can pause or resume.
- Needs attention is grouped by storage, authentication, integrity, expired link, file changed, or network.
- The list keeps its scroll state when the widget is shown and hidden.

## Still open inside stage 2

- No screenshots of the size matrix.
- Table columns are not user-resizable.
- No sparkline, queue drag, schedule calendar, rule builder, settings preview, or undo.
- DPI, RTL, and 200 percent text were not exercised.

## Stage 3 done in this checkpoint

- Multi-part bytes are written at their own offset in `name.span-data`, then renamed over the destination.
- A bad checksum deletes the temp file and leaves the previous file.
- Resume reads `name.span-plan` and continues at that offset.
- A full 200 response on a part is rejected.
- Free space is checked before the parts start and before an unknown-size body is saved. Both refusals are tested. The previous file stays.
- Four shared 64 KB buffers. The plan file is updated only after the channel is forced, including about once per megabyte.

- A failed download is appended to `~/Span/fetch.log`. Tokens and URL passwords are redacted, and the file rotates.
- Progress percent follows the checkpoint. Bytes written ahead of that checkpoint are labeled reserved.
- A data file that is longer than its plan resumes at the plan offset.

- Write policy is Auto, Local, Removable, or Network. Auto uses the volume type. It does not guess SSD versus hard disk.
- A destination whose identity changes, or that disappears, fails before the previous file is replaced.
- Disk health that cannot be read is shown as N/A.

## Stage 4 started

- Dropped text can add links. A script file is not opened.
- A waiting low-priority download can take the next slot.
- An idle connection is labeled stalled. A slow disk is not.
- Startup does not resume a paused or failed download, and a running one is not started twice.
- Redirect loops stop.
- History is separate from the files. Settings import rejects a bad file and omits secrets.
- An unscanned file is not called clean.

- Battery pause is optional and labeled. A manual pause is left alone. An unknown power source does not pause anything.
- Retry-After on HTTP 429 is a wait, not an immediate retry.
- Sleep or shutdown can be cancelled, and it does not run while a download is active.
- There is still no update server. The rule is that an update waits for active downloads and needs a valid signature.
- FTP, SFTP, and magnet links are refused. They are not implemented.

## Stage 5 started

- A bottleneck note stays Low when the evidence is missing.
- Adaptive connections are off unless enabled, and they cannot raise the cap.
- A ten-minute boost expires. A finish estimate is not a promise.
- A replacement link needs the same identity. Mirrors, piece repair, and multi-source refuse an unproven file.
- The local port requires a token and stays on this machine. Scripts are not run.

- A metered profile limits a new download to two connections and says it does not change other programs.
- A slow reply cuts only this download's existing speed cap.
- A useful-byte quota can stop a new start. Retries are outside that count.
- An unknown part-plan layout is not treated as a finished plan.
- History rows drop authorization.

## Stage 6

- The test scene keeps Add inside the measured client sizes, including 200 percent text, density 125/150/200, and RTL. Shots are in `docs/shots/`.
- Resizing that scene does not replace the running job's cancel token.
- Ten thousand rows filter in 24 ms and the lazy list reaches row 9000 in 35 ms.

- Removing a row can be undone. Deleting the file cannot.
- The name column width is saved.
- A queue says when it next opens, using the local hour.
- One live transfer stayed on the same request while the test scene became a widget.

- Queue order is saved. Up, Down, drag, and Alt+arrow use the same move.
- A rule previews its chain and is not saved when that chain loops. It can choose a queue for one extension. It does not run a script.
- The speed chart uses only measured samples from the last 60 seconds.

- A network stall can restart with fewer connections. A disk stall does not.
- The queue calendar marks today in the local month. The hour window is the same every day.

- A download that has every byte still says Finalizing or Verifying. Ready is the saved file. The part bar separates stored, pending, and needs-check bytes.
- Settings shows the effect of unsaved disk, battery, quota, metered, speed, hour-window, and token changes before Save. The token itself is not written in that preview.
- A batch paste reviews each line. A refused line does not stop the rest.
- Activity lists state changes, not one row per packet.
- A saved ZIP can pick entries. A path that leaves the folder is not extracted.

## Next

VoiceOver and a resize of the packaged window are still open. Stage 4 still has no FTP, SFTP, BitTorrent, or Metalink client and no antivirus scanner. Stage 3 still lacks a live USB eject and a power-loss test.

## Blockers

None for the shell. FTP, SFTP, BitTorrent, and Metalink have no adapter in this tree yet.
