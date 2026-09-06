package com.ironmonone.editor

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The editor's SAVE AS, minus the UI.
 *
 * The signature test proves the reflective lookups resolve. This proves the
 * whole operation the user performs: load a preset, change something, write it,
 * read it back through the engine and confirm every option survived.
 *
 * It runs against the FIVE PRESETS THE APP SHIPS rather than a fresh
 * `new Settings()`. That is not incidental. A bare Settings leaves
 * `selectedEXPCurve` null and NPEs inside the engine's own toString(), because
 * the desktop GUI populates it from a combo box before ever writing. The app
 * never constructs one - it always starts from a preset file - so testing a
 * bare instance would have failed for a reason no user can hit while missing
 * the reason they can.
 *
 * Written after "Save failed: Settings.read [class java.io.InputStream]"
 * reached a real phone. No test had pressed the button.
 */
class SettingsRoundTripTest {

    private val presets = File("../app/src/main/assets/presets")

    private val engines = listOf(
        "com.dabomstew.pkrandom.Settings",
        "com.dabomstew.pkrandomzx.Settings",
    )

    /** Reads any option's value, the way the editor's diff does. */
    private fun valueOf(o: Option, on: Any): Any = when (o) {
        is Option.Bool -> o.get(on)
        is Option.IntValue -> o.get(on)
        is Option.Choice -> o.get(on)
    }

    private fun read(cls: Class<*>, f: File): Any? = runCatching {
        FileInputStream(f).use { input ->
            cls.getMethod("read", FileInputStream::class.java).invoke(null, input)
        }
    }.getOrNull()

    @Test
    fun `every shipped preset survives a save`() {
        assertTrue(presets.isDirectory, "presets not found at ${presets.absolutePath}")
        val files = presets.listFiles { f: File -> f.name.endsWith(".rnqs") }
            ?.sortedBy { it.name } ?: emptyList()
        assertTrue(files.size >= 5, "expected the 5 shipped presets, found ${files.size}")

        for (f in files) {
            // Whichever engine can read it is the engine that owns it; the two
            // are never mixed, so exactly one should accept each file.
            val cls = engines.map { Class.forName(it) }.firstOrNull { read(it, f) != null }
                ?: fail("neither engine could read ${f.name}")
            val settings = read(cls, f)!!
            val options = SettingsReflector.options(cls)

            // Move something off its loaded value, so a save that writes a
            // blank or unchanged file cannot pass.
            val flag = options.filterIsInstance<Option.Bool>().first()
            val before = flag.get(settings)
            flag.set(settings, !before)

            val tmp = File.createTempFile("roundtrip", ".rnqs")
            try {
                FileOutputStream(tmp).use { out ->
                    cls.getMethod("write", FileOutputStream::class.java).invoke(settings, out)
                }
                assertTrue(tmp.length() > 0, "${f.name}: wrote an empty preset")

                val reread = read(cls, tmp)
                    ?: fail("${f.name}: the engine could not read back its own save")

                val lost = options.filter { valueOf(it, reread) != valueOf(it, settings) }
                assertTrue(lost.isEmpty(), "${f.name}: lost across the round trip: " +
                    lost.take(5).joinToString { it.label })
                assertTrue(flag.get(reread) == !before, "${f.name}: the change was lost")
            } finally {
                tmp.delete()
            }
        }
    }
}
