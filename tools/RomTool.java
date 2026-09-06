import java.nio.file.*;
import java.util.zip.CRC32;

/**
 * IronMON One - ROM verification and patching, reference implementation.
 *
 * Pure JDK, no dependencies. Run without compiling:
 *     java RomTool.java info    <rom>
 *     java RomTool.java patchinfo <patch.bps>
 *     java RomTool.java apply   <patch.bps|.ips> <source> <dest>
 *
 * This is the algorithm core of the app's `core-patch` module, written in Java so it
 * can be verified today against real files. Ports to Kotlin unchanged.
 *
 * CRC-32 is zlib crc32, the same function RomPatcher.js uses, so results match what
 * the user sees on PC.
 */
public class RomTool {

    record Known(String name, long crc, long size) {}

    static final Known[] KNOWN = {
        new Known("Pokemon FireRed (U) v1.1  [brief-required]", 0x84ee4776L, 16 * 1024 * 1024),
        new Known("Pokemon Emerald (U)       [brief-required]", 0x1f1c08fbL, 16 * 1024 * 1024),
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 2) { usage(); return; }
        switch (args[0]) {
            case "info"      -> info(args[1]);
            case "patchinfo" -> patchInfo(args[1]);
            case "apply"     -> { if (args.length < 4) usage(); else apply(args[1], args[2], args[3]); }
            default          -> usage();
        }
    }

    static void usage() {
        System.out.println("java RomTool.java info <rom>");
        System.out.println("java RomTool.java patchinfo <patch.bps>");
        System.out.println("java RomTool.java apply <patch.bps|.ips> <source> <dest>");
    }

    // ---------------------------------------------------------------- info

    static void info(String path) throws Exception {
        byte[] d = Files.readAllBytes(Path.of(path));
        long crc = crc32(d, 0, d.length);

        System.out.println("file      : " + path);
        System.out.printf ("size      : %,d bytes (%.2f MB)%n", d.length, d.length / 1048576.0);
        System.out.printf ("crc32     : %08x%n", crc);

        if (d.length >= 0xB0) {
            System.out.println("hdr title : " + ascii(d, 0xA0, 12));
            System.out.println("hdr code  : " + ascii(d, 0xAC, 4));
        }

        String match = null;
        for (Known k : KNOWN) if (k.crc == crc) match = k.name;

        if (match != null) {
            System.out.println("VERDICT   : MATCH - " + match);
        } else {
            System.out.println("VERDICT   : no known-CRC match");
            for (Known k : KNOWN) {
                if (d.length != k.size) {
                    System.out.printf("            not %s (size %,d, expected %,d)%n",
                        k.name.split("\\s{2,}")[0], d.length, k.size);
                }
            }
            if (d.length > 16 * 1024 * 1024) {
                System.out.println("            NOTE: larger than a retail GBA cart dump.");
                System.out.println("            An expanded ROM is already a hack build, not a clean dump.");
            }
            // Scan for a NatDex signature in the ROM text.
            if (indexOf(d, "Nat. Dex".getBytes("US-ASCII")) >= 0
                || indexOf(d, "NatDex".getBytes("US-ASCII")) >= 0) {
                System.out.println("            NatDex signature FOUND in ROM body.");
            }
        }
    }

    // ----------------------------------------------------------- patchinfo

    static void patchInfo(String path) throws Exception {
        byte[] p = Files.readAllBytes(Path.of(path));
        if (!(p[0] == 'B' && p[1] == 'P' && p[2] == 'S' && p[3] == '1'))
            throw new IllegalArgumentException("not a BPS patch");

        Cursor c = new Cursor(p, 4);
        long srcSize = c.varint(), dstSize = c.varint(), metaSize = c.varint();

        long srcCrc  = u32le(p, p.length - 12);
        long dstCrc  = u32le(p, p.length - 8);
        long selfCrc = u32le(p, p.length - 4);
        long calcSelf = crc32(p, 0, p.length - 4);

        System.out.println("patch      : " + path);
        System.out.printf ("patch size : %,d bytes%n", p.length);
        System.out.printf ("expects src: %,d bytes, crc32 %08x%n", srcSize, srcCrc);
        System.out.printf ("produces   : %,d bytes, crc32 %08x%n", dstSize, dstCrc);
        System.out.printf ("patch crc  : %08x %s%n", selfCrc,
            calcSelf == selfCrc ? "(patch intact)" : "(PATCH CORRUPT)");
        if (metaSize > 0) System.out.println("metadata   : " + metaSize + " bytes");

        for (Known k : KNOWN)
            if (k.crc == srcCrc)
                System.out.println("SOURCE     : " + k.name);
    }

    // --------------------------------------------------------------- apply

    static void apply(String patchPath, String srcPath, String dstPath) throws Exception {
        byte[] p = Files.readAllBytes(Path.of(patchPath));
        byte[] s = Files.readAllBytes(Path.of(srcPath));
        byte[] out;

        if (p.length > 4 && p[0]=='B' && p[1]=='P' && p[2]=='S' && p[3]=='1') out = applyBps(p, s);
        else if (p.length > 5 && p[0]=='P' && p[1]=='A' && p[2]=='T' && p[3]=='C' && p[4]=='H') out = applyIps(p, s);
        else throw new IllegalArgumentException("unrecognised patch format");

        Files.write(Path.of(dstPath), out);
        System.out.printf("wrote %s  (%,d bytes, crc32 %08x)%n",
            dstPath, out.length, crc32(out, 0, out.length));
    }

    /**
     * BPS carries CRC-32 of source, target and itself. Applying it IS the verification
     * step: a wrong dump fails here with a precise reason, which is what the CRC gate
     * surfaces to the user.
     */
    static byte[] applyBps(byte[] p, byte[] src) {
        Cursor c = new Cursor(p, 4);
        long srcSize = c.varint(), dstSize = c.varint(), metaSize = c.varint();
        c.i += metaSize;

        long wantSrcCrc = u32le(p, p.length - 12);
        long gotSrcCrc  = crc32(src, 0, src.length);
        if (src.length != srcSize)
            throw new IllegalStateException(String.format(
                "wrong source size: patch needs %,d bytes, yours is %,d", srcSize, src.length));
        if (gotSrcCrc != wantSrcCrc)
            throw new IllegalStateException(String.format(
                "wrong source ROM: patch needs crc32 %08x, yours is %08x", wantSrcCrc, gotSrcCrc));

        byte[] dst = new byte[(int) dstSize];
        int outPos = 0, srcRel = 0, dstRel = 0;
        int end = p.length - 12;

        while (c.i < end) {
            long action = c.varint();
            int cmd = (int) (action & 3);
            int len = (int) ((action >> 2) + 1);
            switch (cmd) {
                case 0 -> { for (int n = 0; n < len; n++) { dst[outPos] = src[outPos]; outPos++; } }
                case 1 -> { for (int n = 0; n < len; n++) dst[outPos++] = p[c.i++]; }
                case 2 -> { srcRel += signed(c.varint());
                            for (int n = 0; n < len; n++) dst[outPos++] = src[srcRel++]; }
                case 3 -> { dstRel += signed(c.varint());
                            for (int n = 0; n < len; n++) dst[outPos++] = dst[dstRel++]; }
            }
        }

        long wantDstCrc = u32le(p, p.length - 8);
        long gotDstCrc  = crc32(dst, 0, dst.length);
        if (gotDstCrc != wantDstCrc)
            throw new IllegalStateException(String.format(
                "patch applied but output is wrong: expected crc32 %08x, got %08x",
                wantDstCrc, gotDstCrc));
        System.out.printf("source verified %08x -> output verified %08x%n", gotSrcCrc, gotDstCrc);
        return dst;
    }

    static byte[] applyIps(byte[] p, byte[] src) {
        byte[] dst = src.clone();
        int i = 5;
        while (i + 3 <= p.length) {
            if (p[i]=='E' && p[i+1]=='O' && p[i+2]=='F') {
                i += 3;
                if (i + 3 <= p.length) {                       // optional truncate
                    int cut = ((p[i]&255)<<16) | ((p[i+1]&255)<<8) | (p[i+2]&255);
                    if (cut < dst.length) { byte[] t = new byte[cut]; System.arraycopy(dst,0,t,0,cut); dst = t; }
                }
                return dst;
            }
            int off = ((p[i]&255)<<16) | ((p[i+1]&255)<<8) | (p[i+2]&255); i += 3;
            int len = ((p[i]&255)<<8)  |  (p[i+1]&255);             i += 2;
            if (off + Math.max(len, 1) > dst.length) dst = grow(dst, off + Math.max(len, 1));
            if (len == 0) {                                          // RLE run
                int run = ((p[i]&255)<<8) | (p[i+1]&255); i += 2;
                byte v = p[i++];
                if (off + run > dst.length) dst = grow(dst, off + run);
                for (int n = 0; n < run; n++) dst[off + n] = v;
            } else {
                for (int n = 0; n < len; n++) dst[off + n] = p[i + n];
                i += len;
            }
        }
        return dst;
    }

    // ------------------------------------------------------------- helpers

    static class Cursor {
        final byte[] d; int i;
        Cursor(byte[] d, int i) { this.d = d; this.i = i; }
        /** BPS variable-width integer. */
        long varint() {
            long data = 0, shift = 1;
            while (true) {
                int x = d[i++] & 255;
                data += (x & 0x7f) * shift;
                if ((x & 0x80) != 0) break;
                shift <<= 7;
                data += shift;
            }
            return data;
        }
    }

    static int  signed(long v) { return (int) (((v & 1) != 0 ? -1 : 1) * (v >> 1)); }
    static long u32le(byte[] d, int o) {
        return (d[o]&255L) | ((d[o+1]&255L)<<8) | ((d[o+2]&255L)<<16) | ((d[o+3]&255L)<<24);
    }
    static long crc32(byte[] d, int off, int len) {
        CRC32 c = new CRC32(); c.update(d, off, len); return c.getValue();
    }
    static byte[] grow(byte[] a, int n) { byte[] b = new byte[n]; System.arraycopy(a,0,b,0,a.length); return b; }
    static String ascii(byte[] d, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < len; n++) { int ch = d[off+n]&255; sb.append(ch >= 32 && ch < 127 ? (char) ch : '.'); }
        return sb.toString().trim();
    }
    static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i+j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
