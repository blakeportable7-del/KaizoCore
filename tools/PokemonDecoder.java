/**
 * Gen III Pokémon structure decoder — the heart of the tracker.
 *
 * 100-byte party structure. Bytes 0x20..0x4F are encrypted with key = PID xor OTID
 * applied over twelve 32-bit words, and the four 12-byte substructures (Growth,
 * Attacks, EVs, Misc) are permuted by PID % 24.
 *
 * Constants follow besteon/Ironmon-Tracker (MIT), which docs/TRACKER_SPEC.md names as
 * the source of truth. The offsets below still want confirming against a live save;
 * see the note in RUN_ORDER about what the tests do and do not prove.
 */
public class PokemonDecoder {

    // Party structure offsets.
    static final int PID = 0x00, OTID = 0x04, ENCRYPTED = 0x20, ENC_LEN = 48;
    static final int STATUS = 0x50, LEVEL = 0x54, CUR_HP = 0x56, MAX_HP = 0x58;
    static final int ATK = 0x5A, DEF = 0x5C, SPE = 0x5E, SPATK = 0x60, SPDEF = 0x62;

    /**
     * Slot index of each substructure, indexed by PID % 24, in order G, A, E, M.
     *
     * GENERATED, not transcribed. The game orders the four substructures by the
     * lexicographic permutations of (G,A,E,M); hardcoding 24 rows of four digits is
     * exactly the sort of table that acquires a silent typo, so it is derived instead.
     */
    static final int[][] SLOT_OF = buildSlotTable();

    static int[][] buildSlotTable() {
        int[][] table = new int[24][4];
        int[] letters = {0, 1, 2, 3};
        int row = 0;
        for (int a = 0; a < 4; a++)
            for (int b = 0; b < 4; b++) {
                if (b == a) continue;
                for (int c = 0; c < 4; c++) {
                    if (c == a || c == b) continue;
                    int d = 6 - a - b - c;            // the remaining index
                    int[] bySlot = {letters[a], letters[b], letters[c], letters[d]};
                    for (int slot = 0; slot < 4; slot++) table[row][bySlot[slot]] = slot;
                    row++;
                }
            }
        return table;
    }

    public record Mon(
        long pid, long otId, int level,
        int species, int heldItem, long experience, int friendship,
        int[] moves, int[] pp, int[] evs, int[] ivs,
        int abilitySlot, int nature, boolean shiny,
        int curHp, int maxHp, int atk, int def, int spe, int spAtk, int spDef
    ) {}

    /** Empty party slots are identified by personality == 0, not by a checksum. */
    public static boolean isEmpty(byte[] mon) { return u32(mon, PID) == 0; }

    public static Mon decode(byte[] mon) {
        long pid = u32(mon, PID), otId = u32(mon, OTID);
        long key = pid ^ otId;

        byte[] plain = new byte[ENC_LEN];
        for (int w = 0; w < ENC_LEN / 4; w++) {
            long word = u32(mon, ENCRYPTED + w * 4) ^ key;
            putU32(plain, w * 4, word);
        }

        int[] slot = SLOT_OF[(int) (pid % 24)];
        int g = slot[0] * 12, a = slot[1] * 12, e = slot[2] * 12, m = slot[3] * 12;

        int[] moves = new int[4], pp = new int[4];
        for (int i = 0; i < 4; i++) { moves[i] = u16(plain, a + i * 2); pp[i] = plain[a + 8 + i] & 0xFF; }

        int[] evs = new int[6];
        for (int i = 0; i < 6; i++) evs[i] = plain[e + i] & 0xFF;

        long ivWord = u32(plain, m + 4);
        int[] ivs = new int[6];
        for (int i = 0; i < 6; i++) ivs[i] = (int) ((ivWord >> (i * 5)) & 0x1F);
        int abilitySlot = (int) ((ivWord >> 31) & 1);

        return new Mon(
            pid, otId, mon[LEVEL] & 0xFF,
            u16(plain, g), u16(plain, g + 2), u32(plain, g + 4), plain[g + 9] & 0xFF,
            moves, pp, evs, ivs,
            abilitySlot, (int) (pid % 25), isShiny(pid, otId),
            u16(mon, CUR_HP), u16(mon, MAX_HP), u16(mon, ATK),
            u16(mon, DEF), u16(mon, SPE), u16(mon, SPATK), u16(mon, SPDEF)
        );
    }

    public static boolean isShiny(long pid, long otId) {
        long v = (otId ^ pid ^ (pid >>> 16) ^ (otId >>> 16)) & 0xFFFF;
        return v < 8;
    }

    /** Female if (PID and 0xFF) < genderRatio. 0xFF = genderless, 0x00 = always male. */
    public static String gender(long pid, int genderRatio) {
        if (genderRatio == 0xFF) return null;
        if (genderRatio == 0x00) return "M";
        if (genderRatio == 0xFE) return "F";
        return (pid & 0xFF) < genderRatio ? "F" : "M";
    }

    static int  u16(byte[] d, int o) { return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8); }
    static long u32(byte[] d, int o) {
        return (d[o] & 0xFFL) | ((d[o+1] & 0xFFL) << 8) | ((d[o+2] & 0xFFL) << 16) | ((d[o+3] & 0xFFL) << 24);
    }
    static void putU32(byte[] d, int o, long v) {
        d[o] = (byte) v; d[o+1] = (byte) (v >>> 8); d[o+2] = (byte) (v >>> 16); d[o+3] = (byte) (v >>> 24);
    }
}
