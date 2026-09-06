import java.nio.file.*;
import java.util.*;

/**
 * Tests for PokemonDecoder and GbaHeader.
 *
 * WHAT THESE PROVE, AND WHAT THEY DO NOT. The round-trip cases encode with a helper in
 * this file and decode with PokemonDecoder, so on their own they would only prove the
 * two agree — a check that cannot fail if both share a wrong assumption. So the
 * substructure table, the shiny rule, the IV packing and the header checksum are each
 * asserted against INDEPENDENT facts instead: the canonical permutation order, hand-
 * computed vectors, a directly-written bit pattern, and a real retail ROM.
 *
 * Final validation is still a differential test against a live save once the emulator
 * exists, exactly as docs/TRACKER_SPEC.md says.
 */
public class PokemonDecoderTest {

    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        substructureTableIsAValidPermutationSet();
        substructureTableMatchesCanonicalOrder();
        shinyRuleMatchesHandComputedVectors();
        natureIsPidMod25();
        ivFieldsUnpackFromAKnownBitPattern();
        genderSplitsOnTheRatio();
        roundTripsAcrossFourDifferentOrderings();
        realRomHeaderParsesAndChecksums();

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // -------------------------------------------- independent structural checks

    static void substructureTableIsAValidPermutationSet() {
        Set<String> seen = new HashSet<>();
        boolean ok = PokemonDecoder.SLOT_OF.length == 24;
        for (int i = 0; ok && i < 24; i++) {
            int[] row = PokemonDecoder.SLOT_OF[i];
            ok = row.length == 4 && Arrays.stream(row).sorted().toArray()[0] == 0
                 && new HashSet<>(List.of(row[0], row[1], row[2], row[3])).size() == 4;
            seen.add(Arrays.toString(row));
        }
        check("all 24 substructure orderings are distinct valid permutations",
              ok && seen.size() == 24, "table is malformed: " + seen.size() + " distinct");
    }

    /** Against the published order: index 0 is GAEM, 1 is GAME, 23 is MEAG. */
    static void substructureTableMatchesCanonicalOrder() {
        boolean ok = Arrays.equals(PokemonDecoder.SLOT_OF[0],  new int[]{0, 1, 2, 3})   // GAEM
                  && Arrays.equals(PokemonDecoder.SLOT_OF[1],  new int[]{0, 1, 3, 2})   // GAME
                  && Arrays.equals(PokemonDecoder.SLOT_OF[23], new int[]{3, 2, 1, 0});  // MEAG
        check("substructure table matches the canonical GAEM/GAME/.../MEAG order", ok,
              "row0=" + Arrays.toString(PokemonDecoder.SLOT_OF[0])
              + " row1=" + Arrays.toString(PokemonDecoder.SLOT_OF[1])
              + " row23=" + Arrays.toString(PokemonDecoder.SLOT_OF[23]));
    }

    /** Hand-computed: pid^otId^(pid>>16)^(otId>>16) < 8. */
    static void shinyRuleMatchesHandComputedVectors() {
        boolean ok = PokemonDecoder.isShiny(0x00000000L, 0x00000000L)      // 0  -> shiny
                  && PokemonDecoder.isShiny(0x00000007L, 0x00000000L)      // 7  -> shiny
                  && !PokemonDecoder.isShiny(0x00000008L, 0x00000000L)     // 8  -> not
                  && PokemonDecoder.isShiny(0x00010001L, 0x00000000L)      // 1^1 = 0 -> shiny
                  && !PokemonDecoder.isShiny(0xABCD1234L, 0x0000FFFFL);
        check("shiny rule matches hand-computed vectors", ok, "shiny arithmetic is wrong");
    }

    static void natureIsPidMod25() {
        byte[] mon = mon(/*pid*/ 27, /*otId*/ 5, /*species*/ 1, new int[]{1,2,3,4}, 0, 50);
        check("nature is PID mod 25", PokemonDecoder.decode(mon).nature() == 27 % 25,
              "nature mismatch");
    }

    /** IV word written directly, so this does not depend on the encoder helper. */
    static void ivFieldsUnpackFromAKnownBitPattern() {
        // HP=31 Atk=0 Def=31 Spe=0 SpA=31 SpD=0, ability slot = 1.
        long word = 31L | (0L << 5) | (31L << 10) | (0L << 15) | (31L << 20) | (0L << 25)
                  | (1L << 31);
        long pid = 0, otId = 0;                                  // key 0, so plain == cipher
        byte[] mon = new byte[100];
        PokemonDecoder.putU32(mon, PokemonDecoder.PID, pid);
        PokemonDecoder.putU32(mon, PokemonDecoder.OTID, otId);
        int m = PokemonDecoder.SLOT_OF[0][3] * 12;               // Misc slot for pid%24==0
        PokemonDecoder.putU32(mon, PokemonDecoder.ENCRYPTED + m + 4, word);

        PokemonDecoder.Mon d = PokemonDecoder.decode(mon);
        boolean ok = Arrays.equals(d.ivs(), new int[]{31, 0, 31, 0, 31, 0}) && d.abilitySlot() == 1;
        check("IV 5-bit fields and the ability-slot bit unpack correctly", ok,
              "ivs=" + Arrays.toString(d.ivs()) + " abilitySlot=" + d.abilitySlot());
    }

    static void genderSplitsOnTheRatio() {
        boolean ok = "F".equals(PokemonDecoder.gender(0x10, 0x1F))       // 16 < 31
                  && "M".equals(PokemonDecoder.gender(0x40, 0x1F))       // 64 >= 31
                  && "M".equals(PokemonDecoder.gender(0x00, 0x00))       // always male
                  && PokemonDecoder.gender(0x40, 0xFF) == null;          // genderless
        check("gender splits on the species ratio", ok, "gender rule is wrong");
    }

    // ------------------------------------------------------ round-trip coverage

    /** PIDs chosen so pid%24 hits four different substructure orderings. */
    static void roundTripsAcrossFourDifferentOrderings() {
        int[] pids = {24, 25, 31, 47};            // %24 -> 0, 1, 7, 23
        boolean ok = true;
        StringBuilder why = new StringBuilder();

        for (int pid : pids) {
            int[] moves = {33, 45, 52, 98};
            byte[] raw = mon(pid, 0xDEAD, /*species*/ 260, moves, /*item*/ 13, /*level*/ 42);
            PokemonDecoder.Mon d = PokemonDecoder.decode(raw);
            if (d.species() != 260 || !Arrays.equals(d.moves(), moves)
                || d.heldItem() != 13 || d.level() != 42) {
                ok = false;
                why.append(String.format(" pid%%24=%d gave species=%d moves=%s;",
                        pid % 24, d.species(), Arrays.toString(d.moves())));
            }
        }
        check("decodes species, moves, item and level across 4 substructure orderings",
              ok, why.toString());
    }

    // ----------------------------------------- external validation on a real ROM

    static void realRomHeaderParsesAndChecksums() throws Exception {
        Path rom = Path.of("C:\\PokemonIronmon\\EmeraldNatDex\\ROMs\\Clean",
                           "Pokemon - Emerald Version (USA, Europe) (patched).gba");
        if (!Files.exists(rom)) {
            System.out.println("  SKIP  real-ROM header check (ROM not found)");
            return;
        }
        byte[] d = Files.readAllBytes(rom);
        GbaHeader h = new GbaHeader(d);
        System.out.println("        " + h);
        check("real ROM header parses and its GBATEK checksum validates",
              h.checksumValid && "BPEE".equals(h.gameCode) && "Emerald".equals(h.game()),
              "header: " + h);
    }

    // ------------------------------------------------------------------ helpers

    /** Encodes a party structure the way the game does. Round-trip only; see the note above. */
    static byte[] mon(long pid, long otId, int species, int[] moves, int item, int level) {
        byte[] plain = new byte[PokemonDecoder.ENC_LEN];
        int[] slot = PokemonDecoder.SLOT_OF[(int) (pid % 24)];
        int g = slot[0] * 12, a = slot[1] * 12;

        putU16(plain, g, species);
        putU16(plain, g + 2, item);
        PokemonDecoder.putU32(plain, g + 4, 125000);
        plain[g + 9] = 70;                                       // friendship
        for (int i = 0; i < 4; i++) { putU16(plain, a + i * 2, moves[i]); plain[a + 8 + i] = 15; }

        byte[] mon = new byte[100];
        PokemonDecoder.putU32(mon, PokemonDecoder.PID, pid);
        PokemonDecoder.putU32(mon, PokemonDecoder.OTID, otId);
        long key = pid ^ otId;
        for (int w = 0; w < PokemonDecoder.ENC_LEN / 4; w++)
            PokemonDecoder.putU32(mon, PokemonDecoder.ENCRYPTED + w * 4,
                                  PokemonDecoder.u32(plain, w * 4) ^ key);
        mon[PokemonDecoder.LEVEL] = (byte) level;
        putU16(mon, PokemonDecoder.CUR_HP, 100);
        putU16(mon, PokemonDecoder.MAX_HP, 120);
        return mon;
    }

    static void putU16(byte[] d, int o, int v) { d[o] = (byte) v; d[o + 1] = (byte) (v >>> 8); }

    static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("  PASS  " + name); }
        else    { failed++; System.out.println("  FAIL  " + name + "  --  " + detail); }
    }
}
