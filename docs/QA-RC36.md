# rc36 QA (2026-10-05, not shipped)

rc36 is Heart & Soul Kaizo. On rel/rc36 from master 50d10a33: feat/hns-kaizo (8bb65d07), feat/hns-parity (c2e08584),
fix/seen-and-log (9cc99436). Not in it: feat/stream, feat/gb-core-mgba, the new tracker UI prototype.

Blake's ruling: ship Heart & Soul as built. KaizoCore never bundles the team's official .ups; the player gets it from
GitHub or Hackdex with one tap. Our comfort BPS (F0C6236C) is bundled.

## Merges

- feat/hns-parity: tools/hns/layout.py (both scalar sets kept), tools/hns/gen_tracker.py (parity's GachaMon prize
  trainers, FINAL_TRAINERS = Red, the lab's starter balls), FavoriteBall.kt (parity's hnsLines). layout-kaizo.json,
  HnsLayout.kt and the hns tsvs regenerated from the F0C6236C build in WSL; layout_check.py 71 checks, 0 failed.
- fix/seen-and-log: LogPokemon.kt (gymTms and infoPanel both kept), LogViewer.kt (Heart & Soul's ROM pictures over
  LogPictureIds' pack lookup; palDex passes Heart & Soul's numbering; info panels on Heart & Soul's pages).
- The win: Kaizo at Red (FINAL_TRAINERS 464, 650; HnsTrackerTest), a Nuzlocke at Lance (the caps table's "champion" is
  TRAINER_LANCE_1_HNS; NuzlockeCapsTest), the rules books' "defeat Red at the top of Mt. Silver" and HNS-KAIZO.md now
  agree. A Kaizo win shows the game-over popup's YOU WON; a Nuzlocke shows "Finished. The Champion is beaten."

## Tests

Full suite on 9cc99436, as release.sh runs it (uncached after cleanTest and the app's two cleanTest variants, the real
dumps from the main checkout, IRONMON_REQUIRE_DUMPS=1): 5,429 tests, 0 failures, 0 errors, 0 skipped
(release_checks.py tests). Play's method: 35,270 bytes (javap of the debug compile), the same as rc35.2.

**The Heart & Soul ROM the tests read must be the F0C6236C build.** The main checkout's `.vendor/hns/hns-kaizo.gba` is
still E08DD128, and HnsEngineTest looks next to IRONMON_ROMS first, so the first runs failed 30 Heart & Soul tests
("not a Heart & Soul build KaizoCore knows (CRC E08DD128)") and 19 in tracker-gba. This worktree's `.vendor/hns` now
holds F0C6236C (hns-kaizo.gba and .bps, copied from IronMonOne-wt-hns), and the green run set
`IRONMON_HNS_ROM=C:/Users/bepor/IronMonOne-wt-rc36/.vendor/hns/hns-kaizo.gba`. release.sh needs the same: run it with
that variable, or put the F0C6236C build in the main checkout's `.vendor/hns` first.

## Device checklist (emulator, cold boot, -memory 4096, debug APK installed with adb install -r over the build in place)

1. Play opens on a Game Boy, a GBA and a DS game, portrait and landscape; logcat has no FATAL, ANR or VerifyError.
2. Home > Pokémon Heart & Soul: the flow runs from the Emerald (USA) dump and the official 2.0.6 patch to both games in
   the Library. A stale extracted comfort patch (an older BPS in files/prep/patches) is copied out again.
3. A Kaizo run on Nat. Dex: at Elm's table the ball picker (die and favorites) and the favorite-in-a-ball line.
4. The starter's held item shows on the tracker.
5. The lab trash can gives one item that is not a TM, then is empty.
6. A hidden item sparkles; the Pokémon Center heals at once.
7. A Vanilla run starts.
8. A trainer battle with a held item: the trainer's name, the party balls, the foe card, the last attack line against
   memory.
9. Lose on purpose: the game-over popup, the log opens quickly (timed), NEW RUN makes a new seed.
10. Seen (Trainer) stays the same across an app restart in the middle of a battle.
11. A FireRed or Emerald run is unaffected.

## Device results

(filled in after the run)

## For Blake's go

The versionCode bump, release.sh (the release APK), the tag, the GitHub release, the public snapshot, the wiki
(docs/wiki/Release-notes.md and the download lines), site_bump.py, the site build and the deploy.
