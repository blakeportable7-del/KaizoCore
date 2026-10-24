package com.ironmonone.pc

import com.ironmonone.app.engine.HnsEngine
import com.ironmonone.app.engine.hns.HnsOptions
import com.ironmonone.patch.Patcher
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.CRC32

/**
 * The Heart & Soul side of KaizoCore PC: the player's build, the settings, the pool, one randomize. Everything the
 * randomizer reads comes from inside the jar (/assets, copied from app/src/main/assets when the jar is built), so a seed
 * makes the same game here as on a phone of the same release (KaizoCorePcPinTest).
 */
object PcHns {
    /** The official Heart & Soul 2.0.6 (Emerald + the team's UPS). KaizoCore's comfort patch goes on top of it. */
    const val OFFICIAL_206 = 0x01713508L
    const val DEFAULT_PRESET = "RSE NatDex v1.2 Kaizo"
    const val COMFORT_PATCH = "patches/hns-kaizo-heartsoul-206.bps"

    /** Assets bundled in the jar under /assets, laid out as the app's app/src/main/assets. */
    val assets: (String) -> String = { path -> resource(path).toString(Charsets.UTF_8) }

    fun resource(path: String): ByteArray =
        PcHns::class.java.getResourceAsStream("/assets/$path")?.use { it.readBytes() }
            ?: error("missing from the jar: assets/$path")

    fun crc(b: ByteArray): Long = CRC32().also { it.update(b) }.value
    fun sha1(b: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02X".format(it) }

    /**
     * The KaizoCore Heart & Soul build from what the player has: the official 2.0.6 (the comfort patch is applied here,
     * in memory; the player's file is never changed) or the comfort build itself. Anything else is refused with the reason.
     */
    fun build(rom: ByteArray): ByteArray {
        val c = crc(rom)
        return when {
            c == OFFICIAL_206 -> Patcher.apply(resource(COMFORT_PATCH), rom, "Heart & Soul 2.0.6")
            HnsEngine.refusal(rom) == null -> rom
            else -> throw IllegalArgumentException(
                "This is not Heart & Soul 2.0.6 or a KaizoCore Heart & Soul build this version knows (CRC %08X).".format(c))
        }
    }

    /** A .rnqs file by path, or one of the bundled presets by name ("RSE NatDex v1.2 Kaizo"). */
    fun settingsFile(spec: String): File {
        val f = File(spec)
        if (f.isFile) return f
        val name = spec.removeSuffix(".rnqs")
        val bytes = PcHns::class.java.getResourceAsStream("/assets/presets/$name.rnqs")?.use { it.readBytes() }
            ?: throw IllegalArgumentException("No settings file \"$spec\" (a path to a .rnqs, or a preset name such as \"$DEFAULT_PRESET\").")
        // readSettings takes the mode from the file's name, so the copy keeps it.
        val dir = File(System.getProperty("java.io.tmpdir"), "kaizocore-pc-presets").apply { mkdirs() }
        return File(dir, "$name.rnqs").apply { writeBytes(bytes) }
    }

    fun options(settings: File): HnsOptions = HnsEngine.readSettings(settings)

    /** A pool flag, else the settings file's name, else Nat. Dex (the app's default). */
    fun pool(flag: String?, settings: File): HnsEngine.Pool = when {
        flag.equals("vanilla", ignoreCase = true) -> HnsEngine.Pool.VANILLA
        flag.equals("natdex", ignoreCase = true) -> HnsEngine.Pool.NATDEX
        "vanilla" in settings.name.lowercase() -> HnsEngine.Pool.VANILLA
        else -> HnsEngine.Pool.NATDEX
    }

    fun randomize(build: ByteArray, options: HnsOptions, seed: Long, pool: HnsEngine.Pool): HnsEngine.Result =
        HnsEngine.randomize(build, options, seed, pool, assets)
}

/** The release this jar belongs to: the app's version name and code, and the commit both were built from. */
object PcVersion {
    private val props: Properties = Properties().apply {
        PcVersion::class.java.getResourceAsStream("/kaizocore-pc.properties")?.use { load(it) }
    }
    val version: String get() = props.getProperty("version", "dev")
    val versionCode: Int get() = props.getProperty("versionCode", "0").toIntOrNull() ?: 0
    val commit: String get() = props.getProperty("commit", "unknown")
    val line: String get() = "KaizoCore PC $version ($versionCode, $commit)"
}
