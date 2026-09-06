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

# The engines reference desktop-only bits (AWT dialogs in dead paths).
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn com.dabomstew.**
