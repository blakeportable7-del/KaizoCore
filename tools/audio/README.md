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

Not measured: content above 24 kHz in the core's output folds back below it on the way
down to 48 kHz with either resampler, since neither filters before decimating.
