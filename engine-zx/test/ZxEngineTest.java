import com.dabomstew.pkrandomzx.Settings;
import com.dabomstew.pkrandomzx.Version;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the vendored ZX engine actually runs on the JVM we build with, using Blake's
 * real settings files rather than fixtures.
 *
 * These tests live in `test/` because `src/` is vendored source and must stay pristine.
 */
public class ZxEngineTest {

    private static final String VANILLA =
        "C:\\PokemonIronmon\\EmeraldNatDex\\Tracker\\Ironmon-Tracker\\ironmon_tracker" +
        "\\RandomizerSettings\\RSE Kaizo.rnqs";

    private static final String NATDEX =
        "C:\\PokemonIronmon\\EmeraldNatDex\\Tracker\\Ironmon-Tracker\\extensions\\natdex" +
        "\\rnqs_files\\RSE NatDex v1.2 Kaizo.rnqs";

    @Test
    void engineIsTheVersionWePinned() {
        assertEquals("4.6.1", Version.VERSION_STRING);
        assertEquals(322, Version.VERSION);
    }

    @Test
    void loadsARealVanillaKaizoSettingsFile() throws Exception {
        File f = new File(VANILLA);
        Assumptions.assumeTrue(f.exists(), "vanilla RSE Kaizo.rnqs not on this machine");

        Settings s;
        try (FileInputStream in = new FileInputStream(f)) {
            s = Settings.read(in);
        }
        assertNotNull(s);
        // A settings string round-trips, which is what the editor will rely on.
        String asString = s.toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertNotNull(Settings.fromString(asString));
        System.out.println("vanilla RSE Kaizo loaded; updatedFromOldVersion="
            + s.isUpdatedFromOldVersion());
    }

    /**
     * The brief's known-bad combination: the vanilla ZX engine against a Nat. Dex
     * settings file. On PC this surfaces as
     * "The settings file is too old to update and cannot be loaded."
     *
     * This test records what actually happens rather than asserting a guess, because
     * the answer decides whether the profile guard is a convenience or a necessity.
     */
    @Test
    void recordsWhatHappensWithANatDexSettingsFile() throws Exception {
        File f = new File(NATDEX);
        Assumptions.assumeTrue(f.exists(), "NatDex rnqs not on this machine");

        String outcome;
        try (FileInputStream in = new FileInputStream(f)) {
            Settings s = Settings.read(in);
            outcome = "LOADED WITHOUT ERROR (updatedFromOldVersion="
                + s.isUpdatedFromOldVersion() + ")";
        } catch (UnsupportedOperationException e) {
            outcome = "REFUSED: UnsupportedOperationException: " + e.getMessage();
        } catch (Exception e) {
            outcome = "REFUSED: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.println("ZX 4.6.1 + NatDex rnqs -> " + outcome);

        // Either way this is informative, not a pass/fail. What matters is that the app
        // never reaches this point: Profile.validate() refuses the pairing first.
        assertNotNull(outcome);
    }
}
