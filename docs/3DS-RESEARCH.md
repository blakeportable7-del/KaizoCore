# 3DS IronMON: research and plan (2026-09-28)

Blake asked for 3DS support research (Citra-Tracker-v2 by kcblack42, and UTDZac's Gen 6 setup gist).
Nothing here is built yet. Findings come from source, git history and web pages; no emulator was run.

## What exists on PC

- **Citra-Tracker-v2** (kcblack42, v1.6.1, 2025-04): a Python tracker for X/Y, OR/AS, S/M, US/UM. It reads
  memory through Citra's scripting RPC (UDP 45987; ReadMemory in 1 KB chunks), polling every 4 s. It finds the
  game by trying four party addresses, decrypts Gen 6/7 party data (LCRNG seeded by the encryption constant,
  block unshuffle), no checksum. Gen 7 support is thin. No licence file.
- **UTDZac's gist**: the community's PC setup for Gen 6: your own decrypted ROM (XY 1.0 preferred), an archived
  Citra nightly (Azahar may work), ZX or Smart's randomizer, seeds as LayeredFS mod folders (romfs + code.bin),
  in-game saves over save states. The official settings gist has Gen 6/7 settings strings and rules (Kaizo BST
  cap 600 in XY/ORAS, 655 in SM/USUM).

- **kaizo-ironmon-3ds** (SJTButler, v0.5.0, 2026-09-22; checked 2026-09-29) is not a 3DS-game tracker. It is
  an mGBA fork that runs Pokemon FireRed (USA 1.1) on New 3DS hardware, with an on-device Kaizo randomizer and a
  FireRed tracker. No Gen 6/7 support, no Citra/Azahar path, so nothing here changes. Its 53 FireRed addresses
  shared with besteon's GameAddresses all agree. Notes: C:/Users/bepor/ironmon-ref/kaizo-ironmon-3ds-NOTES.md.

**PermaLocke** (Grenin430, GPL-3.0, v1.0.7.9, 2026-09-28; found 2026-09-29, docs/research/repos-to-add.md section 10):
a working Azahar RPC client with PK6/PK7 decoding, checked on a real Ultra Moon. From its docs/ARCHITECTURE.md: call
SetGetProcess first (ReadMemory answers without it, with bytes that are not the game's); the packet header is
`<u32 version=1, u32 requestId, u32 type, u32 dataSize>` with at most 1024 data bytes; ops 1 Read, 2 Write, 3 ProcessList,
4 SetGetProcess; the RPC server can only be switched on with emulation stopped; WriteMemory silently drops writes to
0x30000000-0x40000000 while replying OK. It found the Ultra Moon party at 0x330128E4 (six slots 0x104 apart), not the
0x33F7FA44 / 484 below: treat every fixed address as per build, and reconcile in M0. It scans for the party instead; a
96 MB heap sweep over RPC took about 6 s. pokebot-3ds (MIT) and PKHeX-Plugins RamOffsets.cs (MIT, box 1 slot 1:
X/Y 1.5 0x8C861C8, OR/AS 1.4 0x8C9E134, S/M 1.2 0x330D9838, US/UM 1.2 0x33015AB0) are second sources.

Party addresses (game process space): XY 0x08CE1CE8, ORAS 0x08CF727C, SM 0x34195E10, USUM 0x33F7FA44; slot
stride 484; battle records 580 (Gen 6) / 816 (Gen 7) apart. Full table in the research report.

## Android emulators

- **Azahar** (Citra fork + Lime3DS merge, GPLv2+): the live project (2126.1.2, 2026-09-20), on Play. Standalone
  app has the same RPC server (Configure > Debug > Enable RPC Server, off by default). Its libretro core exists
  but publishes its memory map once at load (no game heap), returns nothing for SYSTEM_RAM, and prefers Vulkan
  (LibretroDroid is GLES only; set `citra_graphics_api=OpenGL`, needs GLES 3.2, so not testable on the AVD).
  Builds embed an obfuscated key blob by default (ENABLE_BUILTIN_KEYBLOB): KaizoCore must never ship a stock
  build; a fork must build with it off and delete src/core/hw/default_keys.h.
- libretro/citra (2023 code), Panda3DS (experimental, no save states): not recommended.
- RetroAchievements has no 3DS support yet.

## Randomizer and patching

The vendored ZX 4.6.1 supports all eight games (decrypted .3ds/.cci/.cxi), can output LayeredFS, and its 3DS path
is plain Java. Azahar loads mods from `load/mods/<TitleID>/` (romfs/, code.bin, exefs/code.ips|bps), so no
KaizoCore patcher work is needed. ZxEngine must pass saveAsDirectory=true for 3DS and give ZX a writable temp dir.

## Plan

1. **M0, 2-4 days (works on the AVD against Azahar desktop on the PC at 10.0.2.2):** a debug "3DS probe": Kotlin
   UDP client for the RPC protocol, select X/Y by title id, read six party slots, decode and checksum them, read
   the wild opponent; save RAM-only dumps for replay tests. Needs Blake's own decrypted X or Y 1.0 and a save
   past the starter.
2. **Companion mode** (9-13 weeks with the tracker): KaizoCore drives Azahar over RPC, writes the LayeredFS seed
   into Azahar's folder via a granted folder permission, tracker shown split-screen, as an overlay or on a second
   device. Best performance (Vulkan), lowest legal risk (no 3DS code shipped).
3. **Embedded mode** (14-20 weeks total): a pinned Azahar core fork (key blob off; export a virtual-memory read or
   republish the memory map once the heap exists), Platform.N3DS gated to Android 10+ and GLES 3.2, title-id
   identification, and a LibretroDroid fix: its SYSTEM_RAM shortcut assumes base 0x02000000 and must be limited
   to GBA/DS. Performance gate on a real phone before committing Gen 7 to it.

Open questions: which game revisions the addresses match, whether randomized seeds move the heap, performance of
the OpenGL path vs Vulkan, RPC while KaizoCore is in the background.
