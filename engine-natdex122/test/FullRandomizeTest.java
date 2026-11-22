import com.dabomstew.pkrandom122.FileFunctions;
import com.dabomstew.pkrandom122.RandomSource;
import com.dabomstew.pkrandom122.Randomizer;
import com.dabomstew.pkrandom122.Settings;
import com.dabomstew.pkrandom122.romhandlers.Gen3RomHandler;
import com.dabomstew.pkrandom122.romhandlers.RomHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ResourceBundle;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The whole point of the app, executed end to end on the JVM: Blake's real Nat. Dex
 * Emerald and FireRed (his dumps patched to Nat. Dex 1.2.2, .vendor/roms) + the bundled
 * Kaizo settings + the real engine, twice with the same seed and once with another.
 *
 * Passing here means the phone build's only remaining unknown is ART itself. This is
 * the same call sequence NatDexEngine.kt uses. The ROMs are local files, never in the
 * repository: without them this skips.
 */
public class FullRandomizeTest {

    private static final String ROMS = "C:/Users/bepor/IronMonOne/.vendor/roms/";
    private static final String PRESETS = "../app/src/main/assets/presets/";

    @Test
    void randomizesBlakesEmeraldDeterministically() throws Exception {
        randomizesDeterministically(ROMS + "emerald-natdex-122.gba", 0x7B4291C4L, PRESETS + "RSE NatDex v1.2 Kaizo.rnqs");
    }

    @Test
    void randomizesBlakesFireRedDeterministically() throws Exception {
        randomizesDeterministically(ROMS + "firered-natdex-122.gba", 0x34AAFED7L, PRESETS + "FRLG NatDex v1.2 Kaizo.rnqs");
    }

    private static void randomizesDeterministically(String rom, long expectedCrc, String rnqs) throws Exception {
        Assumptions.assumeTrue(new File(rom).exists(), "prepared Nat. Dex 1.2.2 game not present");
        assertTrue(new File(rnqs).exists(), "bundled Nat. Dex Kaizo preset missing: " + rnqs);

        long sourceCrc = crc(new File(rom));
        assertEquals(expectedCrc, sourceCrc, "source must be the verified Nat. Dex 1.2.2 ROM");

        File outA = Files.createTempFile("imone_a", ".gba").toFile();
        File outB = Files.createTempFile("imone_b", ".gba").toFile();
        File outC = Files.createTempFile("imone_c", ".gba").toFile();
        outA.deleteOnExit(); outB.deleteOnExit(); outC.deleteOnExit();

        long t0 = System.currentTimeMillis();
        runOnce(rnqs, rom, outA, 12345L);
        long elapsed = System.currentTimeMillis() - t0;
        runOnce(rnqs, rom, outB, 12345L);
        runOnce(rnqs, rom, outC, 99999L);

        long a = crc(outA), b = crc(outB), c = crc(outC);
        System.out.printf("%s: seed 12345 -> %08x (twice: %08x), seed 99999 -> %08x, first run %d ms%n",
            new File(rom).getName(), a, b, c, elapsed);

        assertTrue(outA.length() > 16 * 1024 * 1024, "output is a full ROM");
        assertNotEquals(sourceCrc, a, "randomization must change the ROM");
        assertEquals(a, b, "same seed must be byte-identical (New Run's contract)");
        assertNotEquals(a, c, "a different seed must change the ROM");
    }

    /** Mirrors CliRandomizer.performDirectRandomization and NatDexEngine.kt. */
    private static void runOnce(String rnqs, String rom, File dest, long seed) throws Exception {
        Settings settings;
        try (FileInputStream in = new FileInputStream(rnqs)) {
            settings = Settings.read(in);
        }
        settings.setCustomNames(FileFunctions.getCustomNames());

        Gen3RomHandler.Factory factory = new Gen3RomHandler.Factory();
        assertTrue(factory.isLoadable(rom), "Gen3 handler must accept the ROM");
        RomHandler handler = factory.create(RandomSource.instance());
        handler.loadRom(rom);
        settings.tweakForRom(handler);

        ResourceBundle bundle =
            ResourceBundle.getBundle("com/dabomstew/pkrandom122/newgui/Bundle");
        try (PrintStream log = new PrintStream(new ByteArrayOutputStream(), false, "UTF-8")) {
            new Randomizer(settings, handler, bundle, false)
                .randomize(dest.getAbsolutePath(), log, seed);
        }
    }

    private static long crc(File f) throws Exception {
        CRC32 c = new CRC32();
        c.update(Files.readAllBytes(f.toPath()));
        return c.getValue();
    }
}
