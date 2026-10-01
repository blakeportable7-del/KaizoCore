# The randomizer engines are driven by reflection (Settings option discovery,
# RomHandler factories, bundled resource lookups by class) and the editor
# reflects over Settings setters. Keep both engine trees whole - they are the
# payload, not the fat.
-keep class com.dabomstew.** { *; }
-keep class compressors.** { *; }
-keep class cuecompressors.** { *; }
-keep class pptxt.** { *; }
-keep class thenewpoketext.** { *; }
-keep class launcher.** { *; }
-keep class zxcompressors.** { *; }
-keep class zxcuecompressors.** { *; }
-keep class zxpptxt.** { *; }
-keep class zxthenewpoketext.** { *; }
-keep class zxlauncher.** { *; }

# Native code resolves these by JNI name; renaming breaks the emulator host.
-keep class com.swordfish.libretrodroid.** { *; }

# Crash reports (2026-09-29) carry Java stack traces to Blake. Renamed classes and
# no line numbers make a trace unreadable without the one mapping.txt of that exact
# build, which is easy to lose; readable names cost a little dex in a 120 MB APK.
# Shrinking and optimizing still run.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# The engines reference desktop-only bits (AWT dialogs in dead paths).
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn com.dabomstew.**
