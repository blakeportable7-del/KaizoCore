# rc31 QA (2026-09-29)

Shipped 2026-09-29: GitHub pre-release v1.0.0-rc31 (tag on build commit 5c0d540), site deploy
6abc3bdbb03f758db35c6a2a (rollback: 6abc27dd08f22961b738cce8). APK 120,545,227 bytes,
SHA-256 ddb75f24648bd820f978906f510e099d27c7a77cb046dab9a5a0a56d7c795758.

## What rc31 adds

- In-app update check: once a day (20 h), willowcreek.group/kaizocore/latest.json; prompt never over Play or the crash dialog; INFO, Updates card with a switch and Check now.
- One-tap update: download, size and SHA-256 check, PackageInstaller session. Android asks once for Install unknown apps and confirms the first in-app update; after that, with UPDATE_PACKAGES_WITHOUT_USER_ACTION, the tap on Update is the install.
- Crash reports to Blake (opt-in): Send report, Always send, Not now; INFO card and switch; scrub of paths and file names on every way out; three a day, never twice; Java stacks captured by an uncaught-exception handler; release builds keep class names and line numbers.

## Automated

- Full suite, uncached, before the one-tap work: 1,574 tests, 0 failures (362 files). After it, app module rerun by tools/release.sh: 1,598 tests on disk, 0 failures.
- New classes: UpdateCheckTest 40, UpdateInstallTest 12, CrashReportTest 65 (each has guards proven to fail when broken).
- Site: kaizocore-crash.test.js 40 checks; the release build's 18 gates, including the new kaizocore-latest gate (12 ways of breaking latest.json each caught).

## On the emulator (AVD ironmon, Android 14, release builds)

| Check | Result |
|---|---|
| rc31 installs over rc30, data kept | pass |
| Play screen opens and runs a game | pass |
| INFO: Updates card, Crash reports card render; switches persist and read as check boxes | pass |
| Check now against the live file (rc30 then rc31 on the site) | "You have the newest build." both times |
| Update prompt (demo): waits for leaving Play; wording; Later writes no snooze in demo | pass |
| One tap (demo reinstall): Allow step, Android settings, auto-continue on return, Android's confirm, app replaced | pass |
| Declining Android's confirm | "Update canceled.", buttons back |
| Silent second update with UPDATE_PACKAGES_WITHOUT_USER_ACTION | no confirm, except within Android's 30 s silent-update throttle |
| Crash dialog (demo): Send report, Sent. Thank you., Close; demo writes nothing | pass |
| Real Java crash (am crash): stack saved, handed on; next launch dialog; report has the java section | pass |
| Real send to the live endpoint | delivered (the first test report emailed Blake, 5:37 pm) |

## Found and fixed during QA

- The Updates and Crash reports switches used the tracker's GearToggle (PC palette, 7dp): ShellSwitchRow.
- The crash email's subject named the build that sent the report, not the one that crashed.
- USER_ACTION_NOT_REQUIRED needs UPDATE_PACKAGES_WITHOUT_USER_ACTION declared, or Android asks every time.
- Another session's production deploys (6abc264c, 6abc27dd) shipped the staged KaizoCore site pieces early; rc31 made the page true.

## Not tested

- A real rc31 to rc32 update through the app over the network on a device (the demo reinstall covers the install; the download is covered against a local server). The first real one will be rc32.
- Crash reports on Android 26 to 29 (no exit records there; the app reports nothing, by design).
