import com.dabomstew.pkrandommd.FileFunctions;
import com.dabomstew.pkrandommd.RandomSource;
import com.dabomstew.pkrandommd.Settings;
import com.dabomstew.pkrandommd.Version;
import com.dabomstew.pkrandommd.pokemon.Move;
import com.dabomstew.pkrandommd.pokemon.MoveCategory;
import com.dabomstew.pkrandommd.pokemon.MoveLearnt;
import com.dabomstew.pkrandommd.pokemon.Pokemon;
import com.dabomstew.pkrandommd.pokemon.Type;
import com.dabomstew.pkrandommd.romhandlers.Gen3RomHandler;
import com.dabomstew.pkrandommd.romhandlers.RomHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The MaxDex engine: CyanSixFour's Nat. Dex randomizer 1.1.3 with Trip's changes ported from
 * MaxDex-Randomizer.jar (engine-maxdex/PINNED.txt). What the ported code reads off the MaxDex
 * ROM is checked on the patched test ROM when it is to hand (IRONMON_ROMS holding
 * firered-maxdex.gba, else Blake's vendor folder), and skipped otherwise, as FasterFireRedTest is.
 */
public class MaxDexEngineTest {

    /** "FRLG MaxDex Kaizo.rnqs" from Tripc423/Maxdex, all 112 bytes (settings version 902). */
    static final String PRESET_BASE64 =
        "AAADhgAAAGhXUklrRWpMOEFQOEFBZ0dSQUFLZUJoc0VDUUVBRkFBeUNRQXVFZ0FBQy84QUJSQXc1QVRrQW9aSUNUSUdCQUl5QUFV" +
        "WUVFWnBjbVVnVW1Wa0lDaFZLU0F4TGpINTlIbmVJNnp2VGc9PQ==";
    static final String PRESET_SHA256 = "af51837c44942be36cb1b407be1fe4bebd6c5d08f0cc95f9325aa1fa7491ec9e";
    /** The preset as the 904 engine writes it back (the research report's section 2.2). */
    static final String SETTINGS_904 =
        "WRIkEjL8AP8AAgGRAAKeBhsECQEAFAAyCQAuEgAAG/8ABRAw5ATkAoZICTIGBAIyAAUYEEZpcmUgUmVkIChVKSAxLjFMRoqz48M4ig==";
    /** SHA-256 of the gen3_offsets.ini inside Trip's jar, which config/gen3_offsets.ini carries below its notice. */
    static final String TRIP_INI_SHA256 = "bf8a35f4f49c068de0f91f887b12d2094a20612e0c35b0d9476a2ebe1f1a6b3f";

    static File rom() {
        String dir = System.getenv("IRONMON_ROMS");
        File f = new File(dir != null ? dir : "C:/Users/bepor/IronMonOne/.vendor/roms", "firered-maxdex.gba");
        return f.isFile() ? f : null;
    }

    static File presetFile() throws Exception {
        File f = Files.createTempFile("FRLG MaxDex Kaizo", ".rnqs").toFile();
        f.deleteOnExit();
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(Base64.getDecoder().decode(PRESET_BASE64));
        }
        return f;
    }

    static String sha256(byte[] b) throws Exception {
        StringBuilder s = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) s.append(String.format("%02x", x));
        return s.toString();
    }

    static long crc(byte[] b) {
        CRC32 c = new CRC32();
        c.update(b);
        return c.getValue();
    }

    @Test
    void reportsTheVersionTripsJarReports() {
        assertEquals(904, Version.VERSION);
        assertEquals("4.6.0-END112", Version.VERSION_STRING);
        assertEquals("4.6.0-END112", Version.oldVersions.get(904));
    }

    @Test
    void readsTheMaxDexKaizoPreset() throws Exception {
        byte[] bytes = Base64.getDecoder().decode(PRESET_BASE64);
        assertEquals(112, bytes.length);
        assertEquals(PRESET_SHA256, sha256(bytes));
        Settings s;
        try (FileInputStream in = new FileInputStream(presetFile())) {
            s = Settings.read(in);
        }
        // 902 is read and brought up to 904 by the engine's own SettingsUpdater.
        assertTrue(s.isUpdatedFromOldVersion());
        assertEquals(SETTINGS_904, s.toString());
        assertEquals("Fire Red (U) 1.1", s.getRomName());
    }

    /** The ini is Trip's, byte for byte, under the four-line notice the port added. */
    @Test
    void theOffsetsAreTrips() throws Exception {
        byte[] all;
        try (InputStream in = FileFunctions.openConfig("gen3_offsets.ini")) {
            all = in.readAllBytes();
        }
        String text = new String(all, "UTF-8").replace("\r\n", "\n");
        String[] lines = text.split("\n", 5);
        for (int i = 0; i < 4; i++) assertTrue(lines[i].startsWith("// "), "notice line " + i);
        assertTrue(lines[0].contains("LOCAL MODIFICATION (KaizoCore, MaxDex)"));
        assertEquals(TRIP_INI_SHA256, sha256(lines[4].getBytes("UTF-8")));
    }

    private static RomHandler load(File rom) throws Exception {
        Gen3RomHandler.Factory f = new Gen3RomHandler.Factory();
        assertTrue(f.isLoadable(rom.getAbsolutePath()));
        RomHandler h = f.create(RandomSource.instance());
        h.loadRom(rom.getAbsolutePath());
        return h;
    }

    /** What the ported readers take off the unrandomized MaxDex ROM (research report, section 2.1). */
    @Test
    void readsTheMaxDexRom() throws Exception {
        File rom = rom();
        Assumptions.assumeTrue(rom != null, "firered-maxdex.gba not to hand");
        assertEquals(0x28C12926L, crc(Files.readAllBytes(rom.toPath())), "the test ROM must be MaxDex 1.0");
        RomHandler h = load(rom);

        List<Pokemon> pokes = h.getPokemon();
        assertEquals(1255 + 1, pokes.size(), "1255 species, the 45 Z-A megas included");
        Pokemon bulbasaur = pokes.get(1);
        // u16 abilities at +22/+24 of the 32-byte entry: Overgrow and Chlorophyll.
        assertEquals(65, bulbasaur.ability1);
        assertEquals(34, bulbasaur.ability2);
        assertEquals("Overgrow", h.abilityName(65));
        assertEquals(289, h.highestAbilityIndex());
        assertEquals("POISON PUPPETEER", h.abilityName(289).toUpperCase());
        // Stride 32: the last species reads real stats, not a neighbour's bytes.
        Pokemon last = pokes.get(1255);
        assertTrue(last.hp > 0 && last.attack > 0, last.name);

        List<Move> moves = h.getMoves();
        assertEquals(841 + 1, moves.size());
        assertEquals("Malignant Chain", moves.get(841).name);
        Move firePunch = moves.get(7), roost = moves.get(361), freezeDry = moves.get(578);
        assertEquals("Fire Punch", firePunch.name);
        assertEquals(75, firePunch.power);
        assertEquals(Type.FIRE, firePunch.type);
        assertEquals(15, firePunch.pp);
        assertEquals(100.0, firePunch.hitratio);
        assertEquals("Roost", roost.name);
        assertEquals(MoveCategory.STATUS, roost.category);
        assertEquals(5, roost.pp);
        assertEquals("Freeze-Dry", freezeDry.name);
        assertEquals(70, freezeDry.power);
        assertEquals(Type.ICE, freezeDry.type);
        // A u16 effect past 255: Worry Seed's 293.
        assertEquals(293, moves.get(394).effectIndex);

        // Jambo's 3-byte learnsets, move-0 slots included: Bulbasaur's level-18 slot is empty on the base ROM.
        List<MoveLearnt> bl = h.getMovesLearnt().get(1);
        assertEquals(33, bl.get(0).move);
        assertEquals(1, bl.get(0).level);
        assertTrue(bl.stream().anyMatch(m -> m.move == 0 && m.level == 18), "the level-18 slot");
    }

    /** Unown and every species of 600 or more base stat total stay out of the wild, whatever the settings. */
    @Test
    void neverPutsA600SpeciesInTheWild() throws Exception {
        File rom = rom();
        Assumptions.assumeTrue(rom != null, "firered-maxdex.gba not to hand");
        RomHandler h = load(rom);
        List<Pokemon> banned = h.bannedForWildEncounters();
        Pokemon unown = h.getPokemon().get(201);
        assertEquals("Unown", unown.name);
        assertTrue(banned.contains(unown), "Unown");
        int strong = 0;
        for (Pokemon p : h.getPokemon()) {
            if (p == null) continue;
            int bst = p.hp + p.attack + p.defense + p.spatk + p.spdef + p.speed;
            if (bst >= 600) {
                strong++;
                assertTrue(banned.contains(p), p.name + " " + bst);
            }
        }
        assertTrue(strong > 100, "MaxDex has well over a hundred 600+ species, megas included: " + strong);
        // Nothing under 600 but Unown. (The list also holds the 25 unused "?" slots, which read 600 and are
        // never offered anyway.)
        for (Pokemon p : banned) {
            int bst = p.hp + p.attack + p.defense + p.spatk + p.spdef + p.speed;
            assertTrue(p == unown || bst >= 600, p.name + " " + bst);
        }
    }
}
