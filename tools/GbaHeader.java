/**
 * GBA cartridge header. Nothing on the JVM does this, and it is ~30 lines from GBATEK.
 *
 * Used for ROM identification (game code + version) alongside the CRC-32 gate. The
 * header checksum at 0xBD is a cheap second validation: it catches a truncated or
 * garbage file before CRC-32 has to be computed over 32MB.
 */
public class GbaHeader {

    public static final int TITLE   = 0xA0;   // 12 bytes, ASCII
    public static final int CODE    = 0xAC;   // 4 bytes: BPEE Emerald, BPRE FireRed
    public static final int MAKER   = 0xB0;   // 2 bytes, "01" = Nintendo
    public static final int VERSION = 0xBC;   // 1 byte: 0, 1, 2
    public static final int CHECK   = 0xBD;   // 1 byte, complement check

    public final String title, gameCode, makerCode;
    public final int version;
    public final boolean checksumValid;

    public GbaHeader(byte[] rom) {
        if (rom.length < 0xC0) throw new IllegalArgumentException("too small to be a GBA ROM");
        title     = ascii(rom, TITLE, 12);
        gameCode  = ascii(rom, CODE, 4);
        makerCode = ascii(rom, MAKER, 2);
        version   = rom[VERSION] & 0xFF;
        checksumValid = (rom[CHECK] & 0xFF) == computeChecksum(rom);
    }

    /** GBATEK: chk = -(0x19 + sum of bytes 0xA0..0xBC), truncated to 8 bits. */
    public static int computeChecksum(byte[] rom) {
        int sum = 0;
        for (int i = TITLE; i <= VERSION; i++) sum += rom[i] & 0xFF;
        return (-(0x19 + sum)) & 0xFF;
    }

    /** "Emerald", "FireRed", "LeafGreen", "Ruby", "Sapphire", or null. */
    public String game() {
        return switch (gameCode.length() >= 3 ? gameCode.substring(0, 3) : "") {
            case "BPE" -> "Emerald";
            case "BPR" -> "FireRed";
            case "BPG" -> "LeafGreen";
            case "AXV" -> "Ruby";
            case "AXP" -> "Sapphire";
            default    -> null;
        };
    }

    private static String ascii(byte[] d, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int c = d[off + i] & 0xFF;
            if (c == 0) break;
            sb.append(c >= 32 && c < 127 ? (char) c : '.');
        }
        return sb.toString().trim();
    }

    public String toString() {
        return String.format("%-12s  code=%s  maker=%s  v1.%d  game=%s  checksum=%s",
            title, gameCode, makerCode, version,
            game() == null ? "unknown" : game(), checksumValid ? "OK" : "BAD");
    }
}
