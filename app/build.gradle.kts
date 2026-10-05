plugins {
    id("com.android.application")
    kotlin("android")
}

// The release key (rc32 audit P2 #7, #8; docs/RELEASING.md, "The release key"). Until Blake's release certificate is
// pinned in tools/release-certs.txt, a release build is signed with the debug key, the one every installed copy trusts.
// Once it is, the release build comes out unsigned (app-release-unsigned.apk) and tools/sign_release.py signs it with both
// keys, so that a phone holding the debug key's build still takes the update. The same file pins it for release.sh and
// site_bump.py. -Pironmon.debugSigned=true signs one with the debug key anyway, for an emulator that never had a release:
// such an APK cannot update a phone that took one signed with the release key.
val releaseKeyPinned: Boolean = rootProject.file("tools/release-certs.txt").readLines()
    .map { it.substringBefore('#').trim().split(Regex("\\s+")) }
    .filter { it[0] == "release" }
    .also { require(it.size == 1) { "tools/release-certs.txt: expected one release line, found ${it.size}" } }
    .single().getOrNull(1)
    ?.also { require(Regex("[0-9a-f]{64}").matches(it)) { "tools/release-certs.txt: the release line is not a SHA-256" } } != null

android {
    namespace = "com.ironmonone.app"
    compileSdk = 34
    // The NDK libretrodroid builds with. Without it here AGP cannot find the strip tool and ships
    // liblibretrodroid.so with its debug info, about 9 MB a library (rc32 audit P2 #4, P3 #10).
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.ironmonone.app"
        // Brief section 0 asks for Android 8.0+. Note that vendoring UPR-Android's app
        // module later would raise this to 29, which is a decision, not an accident.
        minSdk = 26
        targetSdk = 34
        versionCode = 49
        // The build id rides on the version so INFO and the bug report say WHICH rc15:
        // three same-named builds went to Blake's phone in one evening (2026-09-08).
        val baseVersion = "1.0.0-rc36"
        versionName = baseVersion + "+" + (project.findProperty("buildId")?.toString()?.takeIf { it.isNotBlank() } ?: "local")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Only the ABIs the emulator cores ship for (app/src/main/jniLibs): with libretrodroid built for four, a
        // 32-bit phone installed the app and then every game said "Emulator core missing from this build." Now
        // Android itself refuses the install on such a phone (rc33 audit P1).
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }   // pairs with Kotlin 1.9.24

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release {
            // R8: Blake's efficiency ask. The engines and LibretroDroid live on
            // reflection and JNI name lookups, so proguard-rules.pro keeps them
            // whole; everything else (Compose, our code) shrinks and optimizes.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The debug key until the release key is pinned (releaseKeyPinned, above); unsigned after, for sign_release.py.
            signingConfig = if (releaseKeyPinned && !project.hasProperty("ironmon.debugSigned")) null else signingConfigs.getByName("debug")
        }
    }

    // A "lite" build for sending over links with a size cap: identical app,
    // minus the ~37MB of bundled Nat. Dex patches, which are then imported once
    // and kept forever. Build with -Pironmon.lite=true. Measured first: the two
    // patches cannot fit under a 30MB cap even xz'd, so this is the only way to
    // hand over a working APK without a file copy.
    if (project.hasProperty("ironmon.lite")) {
        sourceSets["main"].assets.setSrcDirs(
            listOf("src/main/assets").map { file(it) }
        )
        androidResources { ignoreAssetsPatterns += "patches" }
    }

    // An UPDATE build for the phone: same app, ~11MB instead of ~57MB.
    //
    // Two thirds of the APK is the Nat. Dex patches (37MB) and three ABIs the
    // phone never loads. A phone that has already prepared its ROMs needs
    // neither, so -Pironmon.phone=true drops both and the result is small
    // enough to send over a chat link instead of a file copy.
    //
    // It is a genuine update, not a variant: same package, same signing key, so
    // it installs OVER the existing app and keeps prepared ROMs, runs, saves
    // and stat notes. Use the full build when a ROM still has to be prepared,
    // since that is when the patches are needed.
    if (project.hasProperty("ironmon.phone")) {
        androidResources { ignoreAssetsPatterns += "patches" }
        defaultConfig {
            ndk { abiFilters.clear(); abiFilters.add("arm64-v8a") }
        }
    }

    packaging {
        jniLibs {
            // The libretro core is dlopen()ed by absolute path from nativeLibraryDir,
            // so it must be extracted to disk, and never stripped (symbols matter to
            // the core's own loader).
            useLegacyPackaging = true
            keepDebugSymbols += "*/*/*_libretro_android.so"
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
    // Screenshot harness: renders the tracker panel with a fixed fixture so its
    // layout can be LOOKED AT, rather than argued about from source. The panel
    // only appears after a real party exists in a real run, which is far too
    // slow a loop to iterate a layout against.
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation(project(":core-api"))
    implementation(project(":core-patch"))
    implementation(project(":core-recipe"))
    // Both randomizer engines. engine-zx is package-renamed to com.dabomstew.pkrandomzx
    // so it can coexist with the Nat. Dex fork's com.dabomstew.pkrandom in one APK.
    implementation(project(":engine-natdex"))
    implementation(project(":engine-zx"))
    // MaxDex 1.0's randomizer: Nat. Dex 1.1.3 with Trip's changes, renamed com.dabomstew.pkrandommd.
    implementation(project(":engine-maxdex"))
    implementation(project(":editor"))
    implementation(project(":tracker-gba"))
    implementation(project(":tracker-nds"))

    // Emulator host (E1/E2): vendored LibretroDroid 0.13.2 with our memory-read patch.
    implementation(project(":libretrodroid"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

    // Facecam bubble for streaming: CameraX preview only, bound to the activity
    // lifecycle. No capture, no storage - the streaming app records the screen.
    // 1.4: its JNI library is 16 KB aligned, which a 16 KB page phone needs to load it; 1.3.3's was 4 KB
    // (rc32 audit P3 #11).
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
}
