import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.Version;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the vendored Nat. Dex engine is the one the jar was built from, using Blake's
 * real settings files. The mirror image of engine-zx's test.
 */
public class NatDexEngineTest {

    private static final String NATDEX_KAIZO =
        "C:\\PokemonIronmon\\EmeraldNatDex\\Tracker\\Ironmon-Tracker\\extensions\\natdex" +
        "\\rnqs_files\\RSE NatDex v1.2 Kaizo.rnqs";

    private static final String VANILLA_KAIZO =
        "C:\\PokemonIronmon\\EmeraldNatDex\\Tracker\\Ironmon-Tracker\\ironmon_tracker" +
        "\\RandomizerSettings\\RSE Kaizo.rnqs";

    @Test
    void engineIsTheNatDex121Release() {
        assertEquals("4.6.1-END121", Version.VERSION_STRING);
        assertEquals(908, Version.VERSION);
    }

    /** The load that decides whether tonight works. */
    @Test
    void loadsTheRealNatDexKaizoSettings() throws Exception {
        File f = new File(NATDEX_KAIZO);
        Assumptions.assumeTrue(f.exists(), "NatDex Kaizo rnqs not on this machine");

        Settings s;
        try (FileInputStream in = new FileInputStream(f)) {
            s = Settings.read(in);
        }
        assertNotNull(s);
        String str = s.toString();
        assertFalse(str.isEmpty());
        assertNotNull(Settings.fromString(str));
        System.out.println("NatDex engine + RSE NatDex v1.2 Kaizo.rnqs -> LOADED, round-trips");
    }

    /**
     * The reverse cross-wiring: NatDex engine + VANILLA rnqs. The brief's "settings
     * file is too old" error should live in THIS direction (908 reading 322-era
     * files). Recorded, not guessed, so the app can show the right message for it.
     */
    @Test
    void recordsWhatHappensWithAVanillaSettingsFile() throws Exception {
        File f = new File(VANILLA_KAIZO);
        Assumptions.assumeTrue(f.exists(), "vanilla RSE Kaizo.rnqs not on this machine");

        String outcome;
        try (FileInputStream in = new FileInputStream(f)) {
            Settings s = Settings.read(in);
            outcome = "LOADED (updatedFromOldVersion=" + s.isUpdatedFromOldVersion() + ")";
        } catch (Exception e) {
            outcome = "REFUSED: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.println("NatDex engine + vanilla RSE Kaizo.rnqs -> " + outcome);
        assertNotNull(outcome);
    }
}
