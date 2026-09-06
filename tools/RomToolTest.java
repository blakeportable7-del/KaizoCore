import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;

/**
 * Tests for RomTool's patch appliers.
 *
 * The apply paths could not be tested against real files: applying a BPS needs the
 * clean source ROM, and no clean Emerald or FireRed dump exists on this machine. So
 * this builds synthetic fixtures instead, including a minimal BPS ENCODER whose only
 * job is to emit a patch that exercises all four BPS opcodes.
 *
 *     javac -d out RomTool.java RomToolTest.java && java -cp out RomToolTest
 */
public class RomToolTest {

    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        crc32MatchesKnownVector();
        bpsRoundTripsAllFourOpcodes();
        bpsRejectsWrongSourceRom();
        bpsRejectsWrongSourceSize();
        bpsDetectsCorruptOutput();
        ipsAppliesPlainRecords();
        ipsAppliesRleRun();
        ipsTruncates();

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------- the tests

    /** Guards the gate itself: a wrong CRC function silently passes bad dumps. */
    static void crc32MatchesKnownVector() {
        // zlib crc32("123456789") == 0xCBF43926, the standard CRC-32/ISO-HDLC vector.
        long got = RomTool.crc32("123456789".getBytes(), 0, 9);
        check("crc32 matches the standard check vector", got == 0xCBF43926L,
              String.format("expected cbf43926, got %08x", got));
    }

    static void bpsRoundTripsAllFourOpcodes() {
        byte[] src = pattern(256, 7, 13);
        byte[] want = expectedTarget(src);
        byte[] patch = encodeBps(src, want);

        try {
            byte[] got = RomTool.applyBps(patch, src);
            boolean same = got.length == want.length;
            if (same) for (int i = 0; i < want.length; i++) if (got[i] != want[i]) { same = false; break; }
            check("BPS applies SourceRead + TargetRead + SourceCopy + TargetCopy", same,
                  "output differs from the expected target");
        } catch (Exception e) {
            check("BPS applies SourceRead + TargetRead + SourceCopy + TargetCopy", false,
                  "threw " + e.getMessage());
        }
    }

    /** The CRC gate's real job: refuse the wrong dump with a reason, not a corrupt ROM. */
    static void bpsRejectsWrongSourceRom() {
        byte[] src = pattern(256, 7, 13);
        byte[] patch = encodeBps(src, expectedTarget(src));
        byte[] wrong = pattern(256, 7, 13);
        wrong[100] ^= 0xFF;                       // same size, different contents

        try {
            RomTool.applyBps(patch, wrong);
            check("BPS refuses a source ROM with the wrong CRC", false, "it accepted a bad ROM");
        } catch (IllegalStateException e) {
            check("BPS refuses a source ROM with the wrong CRC",
                  e.getMessage().contains("wrong source ROM"), "wrong message: " + e.getMessage());
        } catch (Exception e) {
            check("BPS refuses a source ROM with the wrong CRC", false, "threw " + e);
        }
    }

    static void bpsRejectsWrongSourceSize() {
        byte[] src = pattern(256, 7, 13);
        byte[] patch = encodeBps(src, expectedTarget(src));

        try {
            RomTool.applyBps(patch, pattern(128, 7, 13));
            check("BPS refuses a source ROM of the wrong size", false, "it accepted a short ROM");
        } catch (IllegalStateException e) {
            check("BPS refuses a source ROM of the wrong size",
                  e.getMessage().contains("wrong source size"), "wrong message: " + e.getMessage());
        } catch (Exception e) {
            check("BPS refuses a source ROM of the wrong size", false, "threw " + e);
        }
    }

    /** A patch whose actions are fine but whose declared output CRC is not. */
    static void bpsDetectsCorruptOutput() {
        byte[] src = pattern(256, 7, 13);
        byte[] patch = encodeBps(src, expectedTarget(src));
        patch[patch.length - 8] ^= 0x01;                       // corrupt target CRC
        writeU32le(patch, patch.length - 4, RomTool.crc32(patch, 0, patch.length - 4));

        try {
            RomTool.applyBps(patch, src);
            check("BPS detects an output that does not match its declared CRC", false,
                  "it accepted a mismatched output");
        } catch (IllegalStateException e) {
            check("BPS detects an output that does not match its declared CRC",
                  e.getMessage().contains("output is wrong"), "wrong message: " + e.getMessage());
        } catch (Exception e) {
            check("BPS detects an output that does not match its declared CRC", false, "threw " + e);
        }
    }

    static void ipsAppliesPlainRecords() {
        byte[] base = pattern(64, 3, 1);
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        writeAscii(p, "PATCH");
        ipsRecord(p, 10, new byte[]{1, 2, 3, 4, 5});
        writeAscii(p, "EOF");

        byte[] got = RomTool.applyIps(p.toByteArray(), base);
        boolean ok = got.length == 64;
        for (int i = 0; i < 5 && ok; i++) ok = got[10 + i] == (byte) (i + 1);
        if (ok) ok = got[9] == base[9] && got[15] == base[15];   // neighbours untouched
        check("IPS applies a plain record without disturbing neighbours", ok, "record misapplied");
    }

    static void ipsAppliesRleRun() {
        byte[] base = pattern(64, 3, 1);
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        writeAscii(p, "PATCH");
        p.write(0); p.write(0); p.write(20);                    // offset 20
        p.write(0); p.write(0);                                 // size 0 = RLE
        p.write(0); p.write(10);                                // run length 10
        p.write(0xAB);                                          // value
        writeAscii(p, "EOF");

        byte[] got = RomTool.applyIps(p.toByteArray(), base);
        boolean ok = true;
        for (int i = 20; i < 30 && ok; i++) ok = got[i] == (byte) 0xAB;
        if (ok) ok = got[19] == base[19] && got[30] == base[30];
        check("IPS applies an RLE run of the right length", ok, "RLE run misapplied");
    }

    static void ipsTruncates() {
        byte[] base = pattern(64, 3, 1);
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        writeAscii(p, "PATCH");
        writeAscii(p, "EOF");
        p.write(0); p.write(0); p.write(32);                    // truncate to 32

        byte[] got = RomTool.applyIps(p.toByteArray(), base);
        check("IPS honours the optional truncation field", got.length == 32,
              "expected 32 bytes, got " + got.length);
    }

    // -------------------------------------------------------------- fixtures

    /**
     * Target laid out so every BPS opcode is required:
     *   [0,64)    identical to source        -> SourceRead
     *   [64,96)   novel bytes                -> TargetRead
     *   [96,128)  copied from source at 200  -> SourceCopy
     *   [128,160) copied from target at 0    -> TargetCopy
     */
    static byte[] expectedTarget(byte[] src) {
        byte[] t = new byte[160];
        System.arraycopy(src, 0, t, 0, 64);
        for (int i = 0; i < 32; i++) t[64 + i] = (byte) ((i * 31 + 5) & 0xFF);
        System.arraycopy(src, 200, t, 96, 32);
        System.arraycopy(t, 0, t, 128, 32);
        return t;
    }

    /** Minimal BPS encoder. Emits exactly the four actions above, in order. */
    static byte[] encodeBps(byte[] src, byte[] target) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeAscii(b, "BPS1");
        varint(b, src.length);
        varint(b, target.length);
        varint(b, 0);                                   // no metadata

        varint(b, ((64 - 1) << 2) | 0);                 // SourceRead 64
        varint(b, ((32 - 1) << 2) | 1);                 // TargetRead 32
        for (int i = 0; i < 32; i++) b.write(target[64 + i]);
        varint(b, ((32 - 1) << 2) | 2);                 // SourceCopy 32
        varint(b, signedVarint(200));                   //   srcRel 0 -> 200
        varint(b, ((32 - 1) << 2) | 3);                 // TargetCopy 32
        varint(b, signedVarint(0));                     //   dstRel 0 -> 0

        byte[] head = b.toByteArray();
        byte[] out = new byte[head.length + 12];
        System.arraycopy(head, 0, out, 0, head.length);
        writeU32le(out, head.length,     crc(src));
        writeU32le(out, head.length + 4, crc(target));
        writeU32le(out, out.length - 4,  RomTool.crc32(out, 0, out.length - 4));
        return out;
    }

    /** Inverse of RomTool.Cursor.varint. */
    static void varint(ByteArrayOutputStream b, long n) {
        while (true) {
            long x = n & 0x7f;
            n >>= 7;
            if (n == 0) { b.write((int) (0x80 | x)); return; }
            b.write((int) x);
            n--;
        }
    }

    static long signedVarint(int offset) {
        return ((long) Math.abs(offset) << 1) | (offset < 0 ? 1 : 0);
    }

    // --------------------------------------------------------------- helpers

    static byte[] pattern(int len, int mul, int add) {
        byte[] d = new byte[len];
        for (int i = 0; i < len; i++) d[i] = (byte) ((i * mul + add) & 0xFF);
        return d;
    }

    static long crc(byte[] d) { CRC32 c = new CRC32(); c.update(d); return c.getValue(); }

    static void writeU32le(byte[] d, int o, long v) {
        d[o]     = (byte) (v         & 0xFF);
        d[o + 1] = (byte) ((v >>> 8)  & 0xFF);
        d[o + 2] = (byte) ((v >>> 16) & 0xFF);
        d[o + 3] = (byte) ((v >>> 24) & 0xFF);
    }

    static void writeAscii(ByteArrayOutputStream b, String s) {
        for (int i = 0; i < s.length(); i++) b.write(s.charAt(i));
    }

    static void ipsRecord(ByteArrayOutputStream b, int offset, byte[] data) {
        b.write((offset >> 16) & 0xFF); b.write((offset >> 8) & 0xFF); b.write(offset & 0xFF);
        b.write((data.length >> 8) & 0xFF); b.write(data.length & 0xFF);
        for (byte x : data) b.write(x);
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("  PASS  " + name); }
        else    { failed++; System.out.println("  FAIL  " + name + "  --  " + detail); }
    }
}
