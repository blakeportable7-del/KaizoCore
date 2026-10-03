package com.ironmonone.app

import java.io.File

/**
 * Where the ROM tests find the real game dumps (IRONMON_ROMS) and the DS RAM dumps (IRONMON_DUMPS), read where they lie,
 * never copied. Without them those tests return early, so a machine with no dumps still runs the suite. tools/release.sh
 * points both at the main checkout's .vendor folders and sets IRONMON_REQUIRE_DUMPS=1, and then a missing folder or file
 * fails the test instead: the release gate used to count about 25 classes that had returned early as passes (rc32 audit
 * P3 #105). The same helper is in tracker-gba and tracker-nds; DumpsTest proves this one fails.
 */
open class DumpFolders(private val env: (String) -> String?) {
    /** Under the release gate a dump a test needs is a failure when it is missing, not a skip. */
    val required: Boolean get() = env("IRONMON_REQUIRE_DUMPS") == "1"

    /** IRONMON_ROMS, or null: the test returns. */
    fun romsDir(): File? = dir("IRONMON_ROMS")

    /** IRONMON_DUMPS, or null: the test returns. */
    fun dumpsDir(): File? = dir("IRONMON_DUMPS")

    /** The game dump [name] in IRONMON_ROMS, or null. */
    fun rom(name: String): File? = file(romsDir(), name)

    /** The RAM dump [name] in IRONMON_DUMPS, or null. */
    fun dump(name: String): File? = file(dumpsDir(), name)

    /** [name] in [dir], a test's own folder (which may fall back to the main checkout's), or null. */
    fun file(dir: File?, name: String): File? {
        val f = dir?.let { File(it, name) }?.takeIf { it.isFile }
        if (f == null && required && name !in NOT_YET) {
            throw AssertionError("IRONMON_REQUIRE_DUMPS: $name is not in ${dir ?: "a dumps folder"}, and the release gate needs it")
        }
        return f
    }

    private fun dir(variable: String): File? {
        val d = env(variable)?.let(::File)?.takeIf { it.isDirectory }
        if (d == null && required) throw AssertionError("IRONMON_REQUIRE_DUMPS: $variable is not a folder (${env(variable)}), and the release gate needs it")
        return d
    }

    companion object {
        /**
         * Games the release PC has no dump of: a test of one still skips it under the gate. Put the dump in .vendor/roms,
         * then take it off this list (and off the other two).
         */
        val NOT_YET = setOf("gold-u.gbc", "silver-u.gbc", "diamond-u.nds", "pearl-u.nds", "soulsilver-u.nds",
            "black-u.nds", "white-u.nds")
    }
}

/** The folders this test JVM was given. */
object Dumps : DumpFolders({ System.getenv(it) })
