import com.dabomstew.pkrandom.exceptions.RandomizerIOException;
import cuecompressors.BLZCoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Nat. Dex engine's copy of the BLZ decoder (RC35-NOTICED N #38), the same change engine-zx's copy had for rc32
 * audit P3 #91: a DS code block with a bad BLZ footer ended the whole process with System.exit(0). It throws now, and
 * every caller already handles a thrown randomizer error. Before the change this test ended the test JVM.
 */
public class BLZCoderTest {

    @Test
    void aBadHeaderLengthThrowsInsteadOfEndingTheProcess() {
        // The last word says 1 byte was added (so the file is coded), and the header length byte before it is 0.
        byte[] bad = {0, 0, 0, 0, 1, 0, 0, 0};
        RandomizerIOException e = assertThrows(RandomizerIOException.class, () -> new BLZCoder(null).BLZ_DecodePub(bad, "arm9.bin"));
        assertEquals("Bad header length", e.getMessage());
    }

    @Test
    void aCodedFileTooShortForItsHeaderThrows() {
        byte[] bad = {0, 0, 0, 1, 0, 0, 0};
        assertThrows(RandomizerIOException.class, () -> new BLZCoder(null).BLZ_DecodePub(bad, "overlay.bin"));
    }

    @Test
    void anUncodedFileStillComesBackAsItIs() {
        // An increment of 0 is "not coded": the bytes are handed back unchanged, as before.
        byte[] plain = {9, 8, 7, 6, 0, 0, 0, 0};
        assertArrayEquals(plain, new BLZCoder(null).BLZ_DecodePub(plain, "arm9.bin"));
    }
}
