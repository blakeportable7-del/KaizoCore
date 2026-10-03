# Resampler harness

Measures the emulator's audio resampling the way `Audio::onAudioReady` drives it:
fixed-size output callbacks from the core's 65,238 Hz to the phone's 48,000 Hz, a pure
tone in, signal-to-noise out. It compiles the real `libretrodroid` resampler sources.

Build with the NDK's clang and run it on the x86_64 emulator (or a phone, with the
matching `--target`):

```bash
NDK=/c/Users/bepor/Android/Sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/windows-x86_64/bin
R=libretrodroid/src/main/cpp/resamplers
"$NDK/clang++.exe" --target=x86_64-linux-android26 -O2 -std=c++17 -static-libstdc++ -I"$R" \
  tools/audio/resampler_harness.cpp "$R/linearresampler.cpp" "$R/cubicresampler.cpp" -o harness
MSYS2_ARG_CONV_EXCL='/data' adb push harness /data/local/tmp/harness
MSYS2_ARG_CONV_EXCL='/data' adb shell "chmod 755 /data/local/tmp/harness && /data/local/tmp/harness"
```

2026-09-27, 96-frame callbacks: old path 41 / 29 / 20 / 15 / 10 dB at 440 Hz / 1.76 / 5 / 9 /
15 kHz; `CubicResampler` 87 / 83 / 57 / 41 / 26 dB. The old figures do not improve with a
better interpolator; they come from rounding each callback's input to whole frames.

That last gap is closed. `WindowedSincResampler` (2026-10-01, Audio's resampler since) filters
what 48 kHz cannot hold before it decimates. Blake heard it as static: "it sounds like it has some
static to it or a crackling sound".

2026-10-01, the core sending 65,536 Hz (mGBA's real rate, from `pcm_dump`), 96-frame callbacks:

| | 440 Hz | 1.76 kHz | 5 kHz | 9 kHz | 15 kHz |
|---|---|---|---|---|---|
| `CubicResampler` | 87 | 83 | 57 | 41 | 26 dB |
| `WindowedSincResampler` | 87 | 87 | 87 | 87 | 87 dB |

A tone above 24 kHz, which must come out as nothing, comes out of `CubicResampler` at -2 to -3 dB,
folded to 17 to 23 kHz; `WindowedSincResampler` leaves -89 to -93 dB of it (-27 dB at 25 kHz, inside
its rolloff). From 32,768 Hz (melonDS, Gambatte) at 12 kHz: cubic 11 dB, sinc 90 dB.

## The real thing: `pcm_dump`

`pcm_dump.cpp` runs a core headless with no input and writes its sound, untouched, to a file: what
the resampler is given. Then `harness <file> <rate>` writes `<file>.cubic.raw` and `<file>.sinc.raw`
beside it. The ROM is the player's own dump; nothing here ships.

```bash
"$NDK/clang++.exe" --target=x86_64-linux-android26 -O2 -std=c++17 -static-libstdc++   -I libretrodroid/src/main/cpp/libretro/libretro-common/include tools/audio/pcm_dump.cpp -ldl -o pcm_dump
# push pcm_dump, app/src/main/jniLibs/x86_64/libmgba_libretro_android.so and a ROM to /data/local/tmp/pcm, then:
./pcm_dump ./libmgba_libretro_android.so ./lg.gba 2700 lg-raw.pcm        # 45 s of LeafGreen's intro
./harness lg-raw.pcm 65536
```

LeafGreen's intro, 2026-10-01: the core's output holds energy between 24 and 32.8 kHz only 17 dB
below the whole signal (mGBA's own low-pass at 60% takes it to 29 dB below). Scored against a long
band-limited resample of the same 10 s of music:

| | SNR | error under 12 kHz | 12 to 20 kHz |
|---|---|---|---|
| `CubicResampler` | 17.4 dB | -25.5 dB | -20.0 dB |
| `WindowedSincResampler` | 29.8 dB | -84.7 dB | -45.6 dB |

The sinc's remaining difference is its rolloff between 20 and 24 kHz (its cutoff is 22 kHz).

## DS games: the core's own sound (2026-10-02)

Blake heard the same crackle on Black 2. The resampler was not it (its cutoff sits at 92% of the lower Nyquist,
15 kHz from 32,768 Hz). melonDS's own defaults were: "Automatic" bit depth is 10-bit on a DS and the interpolation
is None, and KaizoCore set neither. Recorded straight off the stream tap with `ws_record.py` (STREAM on in the game
menu, `adb forward tcp:8642 tcp:8642`, the token from the menu), scored with `pcm_stats.py`, Black 2's title music:

| | distinct values | samples on multiples of 32 | 8 to 12 kHz | 12 to 16.4 kHz |
|---|---|---|---|---|
| core defaults (10-bit, None) | 961 | 100% | -25.8 dB | -28.4 dB |
| 16-bit, Cubic (KaizoCore's defaults since) | 25,150 | 3.1% | -33.2 dB | -36.5 dB |

The interpolation applies mid-game; the bit depth only when the game boots (switched live the samples stayed 16-bit,
after a restart they were 10-bit), so its row says so.

`pcm_dump` takes core options as `PCM_OPTS="key=value,..."` and gives melonDS its log and rumble interfaces, but
melonDS still cannot run headless from adb's shell ("Failed to allocate memory using ftruncate!") and plays silence:
record DS games through the stream instead.
