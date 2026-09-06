pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")   // LibretroDroid, RadialGamePad
    }
}

rootProject.name = "IronMonOne"

// v1 modules. Uncomment as each is stood up; see README for status.
include(":core-api")
include(":core-patch")
include(":core-recipe")
include(":app")
// include(":emu-mgba")
// include(":tracker-gba")
include(":tracker-nds")
include(":engine-zx")
include(":engine-natdex")
include(":editor")
include(":libretrodroid")   // vendored, GPL-3.0, carries the memory-read patch
include(":tracker-gba")
// include(":app")

// v2. Stays stubbed in v1 — brief section 15.5.
// include(":nds")
