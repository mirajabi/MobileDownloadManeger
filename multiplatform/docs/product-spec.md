# Fetch desktop product spec

Scope: `multiplatform/` desktop app. Package name Fetch. Kotlin package `com.miaadrajabi.span`. The Android library in `:downloader` is a separate product and is not changed by this spec.

Priority order: correct files, engine recovery, a clear interface, real window modes, resource limits, then routine and advanced features.

Window modes, card density, and download state are separate. The widget is another view of the same jobs.

Identifiers below are fixed. Status lives in `feature-coverage.md`.

## Resize

| ID | Requirement |
|---|---|
| RZ-01 | Measure the Compose client size, not the monitor resolution. |
| RZ-02 | Wide, Standard, Compact, and Widget layouts. |
| RZ-03 | Enter the widget from width or height. |
| RZ-04 | Hysteresis so the mode does not flicker. |
| RZ-05 | A layout change must not restart a download. |
| RZ-06 | Keep selection, filter, sort, and dialog text. |
| RZ-07 | Expand restores the last full bounds inside the work area. |
| RZ-08 | Usable controls at a small client size. |
| RZ-09 | A pin survives completion and does not invent a percent. |
| RZ-10 | Manual widget and automatic widget differ. |
| RZ-11 | DPI, text scale, RTL, and keyboard do not clip controls. |
| RZ-12 | Optional always-on-top, and distinct Close, tray, and Quit. |

## Disk

| ID | Requirement |
|---|---|
| DR-01 | Part bytes land at the right offset without a second full copy. |
| DR-02 | Temp file on the destination volume, finished by rename when possible. |
| DR-03 | Bounded write buffer. |
| DR-04 | Backpressure when the writer falls behind. |
| DR-05 | Selectable write policy per destination kind. |
| DR-06 | Checkpoint metadata in batches, not per packet. |
| DR-07 | Durable progress never claims bytes that were not flushed. |
| DR-08 | Flush on an interval and at sensitive points. |
| DR-09 | Rotating logs with redaction. |
| DR-10 | Free-space checks, including unknown sizes. |
| DR-11 | Allocation without confusing reserved size and downloaded size. |
| DR-12 | Recovery after crash or reboot. |
| DR-13 | Removable and network destinations can disconnect. |
| DR-14 | A failed replace keeps the previous good file. |
| DR-15 | Optional disk health, with N/A when unavailable. |

## Routine

| ID | Requirement |
|---|---|
| RT-01 | Add one URL with name, type, and folder. |
| RT-02 | Several links, each with its own result. |
| RT-03 | Drop a link or a list file. Do not execute a dropped file. |
| RT-04 | Browser handoff with a real error when unsupported. |
| RT-05 | Optional clipboard watch that ignores non-links. |
| RT-06 | Pause and resume only when the representation still matches. |
| RT-07 | Stop keeps partial data unless the user deletes it. |
| RT-08 | Segment sizes and a connection cap, with a one-connection fallback. |
| RT-09 | Active-file cap separate from connections per file. |
| RT-10 | Queue order survives restart. |
| RT-11 | Several queues with their own limits. |
| RT-12 | Priority with fairness. |
| RT-13 | Global speed cap. |
| RT-14 | Per-download speed cap and the effective limit. |
| RT-15 | Per-host connection cap. |
| RT-16 | Schedule with time zone and DST. |
| RT-17 | Retry with backoff and Retry-After. |
| RT-18 | Stall detection that is not confused with a slow disk. |
| RT-19 | Reconnect resumes only eligible downloads. |
| RT-20 | Sleep and wake do not duplicate connections. |
| RT-21 | Optional battery policy, shown in the UI. |
| RT-22 | Proxy, with credentials stored safely. |
| RT-23 | Authentication failure is explained. |
| RT-24 | Cookies and headers stay in scope and out of logs. |
| RT-25 | Redirect limit and loop detection. |
| RT-26 | HTTP and HTTPS, plus FTP, SFTP, BitTorrent, and Metalink adapters. |
| RT-27 | Categories with an override. |
| RT-28 | Safe rename before start. |
| RT-29 | Explicit collision policy. |
| RT-30 | Duplicate detection that does not treat a similar name as proof. |
| RT-31 | Search and filters that do not delete data. |
| RT-32 | History that can be cleared without deleting files. |
| RT-33 | Import and export with a schema version and no secrets by default. |
| RT-34 | Checksum result distinct from "no checksum". |
| RT-35 | Antivirus result distinct from "not scanned". |
| RT-36 | Server file names cannot escape the destination. |
| RT-37 | Platform secret storage or a documented fallback. |
| RT-38 | Tray actions use the same commands as the window. |
| RT-39 | Completion actions, with a cancelable shutdown. |
| RT-40 | Signed update that does not break an active file. |

## Advanced

| ID | Requirement |
|---|---|
| AD-01 | Bottleneck note with evidence and confidence. |
| AD-02 | Adaptive connections with a cap and a way to turn it off. |
| AD-03 | Optional latency-aware rate cut. It does not claim to shape all system traffic. |
| AD-04 | Temporary priority that expires. |
| AD-05 | Deadline estimate with uncertainty. |
| AD-06 | Quota that counts retries separately from useful bytes. |
| AD-07 | Network profiles. |
| AD-08 | Bind a download to one interface where the OS allows it. |
| AD-09 | Replace an expired URL only after the file identity matches. |
| AD-10 | Refresh a link only for a named provider. |
| AD-11 | User or manifest mirrors, with a cheap probe. |
| AD-12 | Multi-source only when byte identity is proven. |
| AD-13 | Repair a bad range only with a piece hash. |
| AD-14 | Import a partial file only for a known layout. |
| AD-15 | Handoff bundle without cookies or passwords. |
| AD-16 | ZIP entry selection with traversal protection. |
| AD-17 | Media preview from the same engine, not a second downloader. |
| AD-18 | Only real quality choices. |
| AD-19 | Delta update only with a valid manifest. |
| AD-20 | Rules with a preview and a loop guard. |
| AD-21 | Post-download steps. Scripts stay off by default. |
| AD-22 | Project bundle with relative paths inside a root. |
| AD-23 | Remote control with pairing and revoke. |
| AD-24 | Local authenticated CLI or API on the same engine. |
| AD-25 | Separate useful bytes, received bytes, retries, and logical writes. |

## Interface

| ID | Requirement |
|---|---|
| UX-01 | A normal download does not require advanced fields. |
| UX-02 | Segment map from real ranges. |
| UX-03 | Speed-drop panel with a real action. |
| UX-04 | Network and write charts with units. |
| UX-05 | Receiving, finalize, verify, and ready are different. |
| UX-06 | Free space, or an explicit unavailable state. |
| UX-07 | Short event timeline, not one row per packet. |
| UX-08 | Queue reorder by drag and by keyboard. |
| UX-09 | Schedule calendar with the next run. |
| UX-10 | Visual rule builder. |
| UX-11 | Preview of an important setting before it is applied. |
| UX-12 | Professional table with saved columns and density. |
| UX-13 | Cards whose pause control is always visible. |
| UX-14 | Command palette. |
| UX-15 | One inspector. Opening it does not touch the engine. |
| UX-16 | Group actions that say how many items qualify. |
| UX-17 | Needs-attention groups with a real next step. |
| UX-18 | Undo only where the action is actually reversible. |
| UX-19 | Keyboard, contrast, RTL, and large text. |
| UX-20 | The list stays usable with a large history. |
