# nds — Platinum adapter, NOT IMPLEMENTED

Deliberately empty in v1. This module exists so the DS slot is visible in the tree and
nobody "temporarily" reaches for a GBA type from shared UI.

When v2 starts (only after the GBA loop ships, brief section 15.6):

- `MelonDsCore.kt` implements `EmulatorCore` with `ScreenLayout.DualScreen`
- `NdsIronmonTracker.kt` implements `Tracker`, ported against `readMemory`
- Randomizer is **ZX 4.6.1**, the same engine as vanilla GBA, accepting `.nds`
- QoL is IronMonPlat `.xdelta`. **Never** apply a GBA NatDex `.bps` to Platinum.
- Platinum needs its own CRC/revision detect (1.0 vs 1.1)

Nothing here is a copy of `gba/`. If a DS class starts looking like a renamed GBA
class, the abstraction in `core-api` is wrong and that is the thing to fix.
