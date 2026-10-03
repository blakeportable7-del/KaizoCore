import com.dabomstew.pkrandommd.FileFunctions;
import com.dabomstew.pkrandommd.RandomSource;
import com.dabomstew.pkrandommd.Randomizer;
import com.dabomstew.pkrandommd.Settings;
import com.dabomstew.pkrandommd.romhandlers.Gen3RomHandler;
import com.dabomstew.pkrandommd.romhandlers.RomHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The whole randomize, on the JVM, with the call sequence MaxDexEngine.kt uses: the patched
 * test ROM (MaxDex 1.0, CRC 28C12926), Trip's Kaizo preset and three seeds. The output CRCs
 * are pinned in PINNED.txt; a change to the port that changes a run fails here. Skipped
 * without the ROM, as MaxDexEngineTest is.
 */
public class MaxDexRandomizeTest {

    /** Golden runs of this port, 2026-10-02 (PINNED.txt). */
    static final long SEED_A = 12345L, CRC_A = 0xFC3D321BL;
    static final long SEED_B = 20261002L, CRC_B = 0xB9880C08L;

    static byte[] run(File rom, long seed, StringBuilder logOut) throws Exception {
        Settings settings;
        try (FileInputStream in = new FileInputStream(MaxDexEngineTest.presetFile())) {
            settings = Settings.read(in);
        }
        settings.setCustomNames(FileFunctions.getCustomNames());
        Gen3RomHandler.Factory factory = new Gen3RomHandler.Factory();
        RomHandler handler = factory.create(RandomSource.instance());
        handler.loadRom(rom.getAbsolutePath());
        settings.tweakForRom(handler);
        File dest = Files.createTempFile("maxdex-run", ".gba").toFile();
        dest.deleteOnExit();
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(log, false, "UTF-8")) {
            new Randomizer(settings, handler, ResourceBundle.getBundle("com/dabomstew/pkrandommd/newgui/Bundle"), false)
                .randomize(dest.getAbsolutePath(), ps, seed);
        }
        if (logOut != null) logOut.append(new String(log.toByteArray(), StandardCharsets.UTF_8));
        byte[] out = Files.readAllBytes(dest.toPath());
        dest.delete();
        return out;
    }

    static int u32(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | (b[o + 3] & 0xFF) << 24;
    }

    @Test
    void randomizesTheMaxDexRomDeterministically() throws Exception {
        File rom = MaxDexEngineTest.rom();
        Assumptions.assumeTrue(rom != null, "firered-maxdex.gba not to hand");
        byte[] base = Files.readAllBytes(rom.toPath());

        StringBuilder log = new StringBuilder();
        byte[] a = run(rom, SEED_A, log);
        byte[] a2 = run(rom, SEED_A, null);
        byte[] b = run(rom, SEED_B, null);
        System.out.printf("seed %d -> %08x, seed %d -> %08x%n", SEED_A, MaxDexEngineTest.crc(a), SEED_B, MaxDexEngineTest.crc(b));

        assertEquals(base.length, a.length, "a full 32 MB ROM");
        assertArrayEquals(a, a2, "the same seed must make the same ROM (New Run's contract)");
        assertEquals(CRC_A, MaxDexEngineTest.crc(a), "seed " + SEED_A + " no longer makes the pinned ROM");
        assertEquals(CRC_B, MaxDexEngineTest.crc(b), "seed " + SEED_B + " no longer makes the pinned ROM");

        // The log says what made it, as Trip's jar does.
        assertTrue(log.toString().startsWith("Randomizer Version: 4.6.0-END112"), log.substring(0, 60));
        assertTrue(log.toString().contains("Settings String: 904" + MaxDexEngineTest.SETTINGS_904));

        // What the tracker recognises a MaxDex game by stays put: the species count and the
        // Game Freak header pointers (GameMap.resolve).
        for (int at : new int[]{0x170, 0x1BC, 0x1CC, 0x144}) {
            assertEquals(u32(base, at), u32(a, at), String.format("header word at %x moved", at));
        }
        assertEquals(1255, u32(a, 0x170));

        // The physical/special byte of every move (+4 of 12) is the patch's own: the engine never writes it.
        int moves = 0x268000;
        for (int m = 1; m <= 841; m++) {
            assertEquals(base[moves + m * 12 + 4], a[moves + m * 12 + 4], "category byte of move " + m);
        }
    }
}
