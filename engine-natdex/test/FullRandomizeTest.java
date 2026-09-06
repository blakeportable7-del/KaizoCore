import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.RandomSource;
import com.dabomstew.pkrandom.Randomizer;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.romhandlers.Gen3RomHandler;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
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
 * Emerald + the real RSE NatDex Kaizo settings + the real engine, twice with the same
 * seed and once with another.
 *
 * Passing here means the phone build's only remaining unknown is ART itself. This is
 * the same call sequence NatDexEngine.kt uses.
 */
public class FullRandomizeTest {

    private static final String ROM =
        "C:\\PokemonIronmon\\EmeraldNatDex\\ROMs\\Clean" +
        "\\Pokemon - Emerald Version (USA, Europe) (patched).gba";

    private static final String RNQS =
        "C:\\PokemonIronmon\\EmeraldNatDex\\Tracker\\Ironmon-Tracker\\extensions\\natdex" +
        "\\rnqs_files\\RSE NatDex v1.2 Kaizo.rnqs";

    @Test
    void randomizesBlakesRealRomDeterministically() throws Exception {
        Assumptions.assumeTrue(new File(ROM).exists(), "prepared NatDex Emerald not present");
        Assumptions.assumeTrue(new File(RNQS).exists(), "NatDex Kaizo rnqs not present");

        long sourceCrc = crc(new File(ROM));
        assertEquals(0xebfdce4bL, sourceCrc, "source must be the verified NatDex 1.2.1 ROM");

        File outA = Files.createTempFile("imone_a", ".gba").toFile();
        File outB = Files.createTempFile("imone_b", ".gba").toFile();
        File outC = Files.createTempFile("imone_c", ".gba").toFile();
        outA.deleteOnExit(); outB.deleteOnExit(); outC.deleteOnExit();

        long t0 = System.currentTimeMillis();
        runOnce(RNQS, ROM, outA, 12345L);
        long elapsed = System.currentTimeMillis() - t0;
        runOnce(RNQS, ROM, outB, 12345L);
        runOnce(RNQS, ROM, outC, 99999L);

        long a = crc(outA), b = crc(outB), c = crc(outC);
        System.out.printf("seed 12345 -> %08x (twice: %08x), seed 99999 -> %08x, first run %d ms%n",
            a, b, c, elapsed);

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
            ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle");
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
