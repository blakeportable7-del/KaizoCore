# rc33 fixes: progress (branch fix/rc33-p0)

The rc32 audit's confirmed P0s (C:/Users/bepor/KaizoCore-audit-rc32/audit-2026-10-01/P0-VERIFIED.md, also on the
private repo's handoff branch under docs/private/audit-rc32/), fixed in the order 1, 3, 2, 4, 5, 9, 12, 6, 8, 7, 11, 10.
Blake, 2026-10-01: "fix the bugs" and "keep going until usage credits expire". Each fix gets a test that fails without
it and, where it is visible on a phone, a check on the emulator. Nothing is released without Blake's go.

| # | Problem | Status | Proof |
|---|---|---|---|
| 1 | Native leak on every save state and SRAM copy | **Fixed** | Emulator, Crystal at 1x: native heap +14.9 MB in 90 s before, +0.1 MB after. NativeSerializeTest pins it. |
| 3 | Time Machine restore never resets `viewing` | **Fixed** | The flag lives in the dialog's DisposableEffect: Close, a restore or leaving Play all clear it and trim. TimeMachineTest. |
| 2 | Two quick saves to one slot can delete it | **Fixed** | One writer per file and an atomic replace that never deletes the slot first (StateSlots.replace). StateWriteTest: 8 threads racing one slot, and a failed replace now reports failure; both red on the old code. |
| 4 | NEW RUN has no in-flight guard | **Fixed** (emulator: Play opens, NEW RUN works; with a run made ahead it lands in about 1 s, too fast to double-tap by hand) | NewRunGuard: newRun claims it right before the launch; the job's completion gives it back (also for a job cancelled before it started). NewRunGuardTest. |
| 5 | A run installed while Play is open writes into the wrong game | **Fixed** (emulator: Start attempt 4, then Play at once: the wait screen, then the new run booted by itself) | Play does not start while a run is being made (RunJob.installing): the shell shows the progress (PlayRunBeingMade) and Play boots fresh on the new run. Covers DS too, where the install overwrote the ROM melonDS held open. PlayWaitsForNewRunTest. |
| 9 | Cloud link saved before the restore is confirmed | **Fixed** | The link carries the restore as pending, on disk, in one write (CloudSync.linkForRestore); sync writes nothing while it is set (Result.RestorePending); only a restore that went through clears it; Cancel and a failed restore read the mark on disk. CloudSyncTest. |
| 12 | Turning off "Get the next run ready" deletes the claimed run | **Fixed** | clear() leaves a claimed run to the install that holds it; a leftover from a killed install still goes. NextRunTest. |
| 6 | A randomized Nuzlocke ends another game's run without asking | **Fixed** | Any run in play is asked about (store.currentRun, the Kaizo screen's test), naming the other game (NuzlockeStarts.replaceQuestion). NuzlockeStartQuestionTest. |
| 8 | Full storage: in-game save, auto-save and ledger fail silently | **Fixed** | SaveTrouble logs every failed background save and toasts the first of each kind a minute. The battery save goes through StateSlots.writeAtomic; SafeWrite replaces atomically, never deletes first and removes its .tmp on failure. SaveTroubleTest. |
| 7 | Starter-ball info as a Dialog on the second screen crashes | **Fixed** (device check owed: needs a second display) | The second screen marks itself (LocalOnSecondScreen); the starter info, the one dialog the tracker opens by itself, is not opened there. SecondScreenDialogTest. |
| 11 | A big DS game loads on the GL thread (ANR) | **Fixed for the tabs** (emulator: Black 2 from the library, Home tapped 3.5 s in: held with the toast; after the load Home switched; no ANR) | The tabs hold Play while its game loads, at most 20 s (PlayLoading, noted where Play already holds its size while loading). Home, power and a display move still pause the view mid-load; moving the load off the GL thread is the full fix and needs melonDS testing. PlayLoadingTest. |
| 10 | Stream snapshot reads StatMarks lists off the main thread | **Fixed** | The snapshot is built on the main thread, where StatMarks is written; only the JSON is written off it; movesSeenFor returns a copy. StreamSnapshotThreadTest. |

Then: verify the 82 P1s in findings-raw.json at their file:line before fixing any.

## Notes for whoever picks this up

- Measure the leak at 1x: the rewind recorder skips while fast-forward is on, so at 8x the heap stays flat either way.
  `adb shell dumpsys meminfo com.ironmonone.app`, the Native Heap PSS column, a library game, 60 to 90 s.
- Gradle: take C:/Users/bepor/rogue-gradle.lock (mkdir) before any Gradle run and rmdir it after, only if you took it.
