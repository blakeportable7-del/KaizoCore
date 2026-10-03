package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Play as your Pokemon" trusts eleven addresses per game and a handful of struct offsets
 * (Overworld.kt, libretrodroid's sprite_core.h). A wrong one does not crash: it draws a
 * Pokemon in the wrong place, or hides the wrong sprite. So each is proven against the
 * game itself.
 *
 * The compiled code of eight small functions is byte for byte the same in FireRed, LeafGreen,
 * Ruby, Sapphire and Emerald apart from the words in its literal pool (and the operands of
 * its calls). This finds each function in a ROM by that code, exactly once, and reads its
 * pool: gMain and the oamBuffer offset out of LoadOam; gSprites, both camera offsets and
 * the Sprite layout out of UpdateOamCoords; gObjectEvents, gPlayerAvatar, the
 * ObjectEvent size and its facing nibble out of GetPlayerFacingDirection; and so on. None
 * of it comes from a symbol file, which is what makes it a check on the table (that was
 * read from the pret .sym files) and not a copy of it.
 *
 * Needs IRONMON_ROMS (a folder holding firered-u-v10.gba, firered-u-v11.gba, leafgreen-u.gba,
 * emerald-u.gba, ruby-u.gba, sapphire-u.gba); a missing one is skipped and says so. Every one
 * of the six is asserted, and the run that says "6 ROMs checked" is the proof none was skipped
 * (see `every ROM of the six is there when IRONMON_ROMS is set`).
 */
class OverworldAddressTest {
    private class Rom(val name: String, val bytes: ByteArray, val table: OverworldAddresses)

    private val roms: List<Rom> by lazy {
        val dir = Dumps.romsDir()
        if (dir == null) {
            println("OverworldAddressTest skipped: set IRONMON_ROMS")
            emptyList()
        } else listOf(
            "firered-u-v10.gba" to Overworld.FIRERED_U_V10, "firered-u-v11.gba" to Overworld.FIRERED_U_V11,
            "leafgreen-u.gba" to Overworld.LEAFGREEN_U,
            "emerald-u.gba" to Overworld.EMERALD_U, "ruby-u.gba" to Overworld.RUBY_U, "sapphire-u.gba" to Overworld.SAPPHIRE_U,
        ).mapNotNull { (file, table) ->
            val f = Dumps.file(dir, file)
            if (f == null) { println("OverworldAddressTest: $file missing, that game is skipped"); null }
            else Rom(file, f.readBytes(), table)
        }
    }

    /** Hex with ".." for a byte that may be anything. */
    private fun sig(hex: String): List<Int> {
        val clean = hex.replace(" ", "")
        return (0 until clean.length step 2).map { i ->
            val p = clean.substring(i, i + 2)
            if (p == "..") -1 else p.toInt(16)
        }
    }

    private fun Rom.findAll(sig: List<Int>): List<Int> {
        val out = ArrayList<Int>()
        val limit = bytes.size - sig.size
        var i = 0
        while (i <= limit) {
            var ok = true
            for (j in sig.indices) {
                val s = sig[j]
                if (s >= 0 && (bytes[i + j].toInt() and 255) != s) { ok = false; break }
            }
            if (ok) out += i
            i += 2   // Thumb code is halfword aligned
        }
        return out
    }

    private fun Rom.u32(at: Int): Long =
        (bytes[at].toLong() and 255) or ((bytes[at + 1].toLong() and 255) shl 8) or
            ((bytes[at + 2].toLong() and 255) shl 16) or ((bytes[at + 3].toLong() and 255) shl 24)

    /** Where the function with this code is, in the ROM; exactly one place, or the test says which. */
    private fun Rom.locate(name: String, hex: String): Int {
        val s = sig(hex)
        val hits = findAll(s)
        assertEquals(1, hits.size, "$name in ${this.name}: ${hits.size} places match its code")
        return hits.single()
    }

    private fun each(check: (Rom) -> Unit) {
        // No ROM is a skip, and says so; the assertTrue(roms.size >= 1) that followed could not fail (rc32 audit P3 #84).
        if (roms.isEmpty()) { println("SKIP: OverworldAddressTest has no ROM of the six to check"); return }
        roms.forEach(check)
    }

    // ---- the code that is the same in every game (pool words and call operands are "..")
    private val LOAD_OAM = "00b5084a08495018017801200840002806d1101c..30e021c904044a........01bc0047................00010004"
    private val UPDATE_OAM_COORDS =
        "f0b50024194f1a48051c1a4e200100198000c319181c3e30017805200840012847d10220084000282ad02022995e2422" +
        "985e0918181c283000780006001609180d480022805e091829405a88301c104008435880d98c588c0918181c29300078" +
        "064a40181278801822e00000........ff01000000feffff................2022995e2422985e0918181c28300078" +
        "00060016091829405a88301c104008435880d98c588c0918181c2930007840181870601c0006040e3f2ca7d9f0bc01bc" +
        "0047"
    private val PLAYER_FACING = "054a06484179c800401880008018007e0007000f70470000................"
    private val AVATAR_SPRITE_ID = "0148007970470000........"
    private val CHECK_MOVEMENT_INPUT =
        "10b50006040e002c06d102488470002013e00000........00f0....0006000e844207d002498878022803d0012003e0" +
        "........02490220887010bc02bc0847........"
    // The calls (bl) are wild too: they differ in every game. Ruby and Sapphire call this one sub_8059204.
    private val TRANSITION_STATE =
        "10b50a4c0020e070........0006002815d0........0006002809d1........000600280bd1012008e00000" +
        "................0006002801d10220e07010bc01bc0047"
    private val CB2_OVERWORLD = "10b50948c079c009041c002c02d00020..f7....fff7..ff002c01d000f0..fa10bc01bc00470000........"
    private val BLEND_PALETTE =
        "f0b557464e464546e0b481b00004000c80460904090c8c461206170e68460380002666452fd21c4882461c4989464046" +
        "35182d04ed0b514668180368dc069a055b044d44e40e0099c806c00e001b784300112418d20e8805c00e801a78430011" +
        "121852011443db0e4904c90ec91a081c784300111b189b021c432c80701c0004060c6645d3d301b038bc9846a146aa46" +
        "f0bc01bc00470000................"
    private val TRANSFER_PLTT =
        "30b5114c217a8025281c08400006030e002b16d10d49a022d2040d48016042600c49816080680c480360617a03200840" +
        "022806d1e179281c0840002801d000f0b3ff30bc01bc0047................d400000400020080........"

    // SetMainCallback2: gMain.callback2 = callback; gMain.state = 0. Ruby and Sapphire compile it another way.
    private val SET_MAIN_CALLBACK2_FRE = "034948608720c0000918002008707047........"
    private val SET_MAIN_CALLBACK2_RS = "03494860034809180020087070470000........3c040000"

    // ---------------------------------------------------------------------------------

    /** The No-Intro CRC32 of each retail dump the tables are checked against, and its header's game code and version. */
    private val known = mapOf(
        "firered-u-v10.gba" to Triple(0xDD88761CL, "BPRE", 0),
        "firered-u-v11.gba" to Triple(0x84EE4776L, "BPRE", 1),
        "leafgreen-u.gba" to Triple(0xD69C96CCL, "BPGE", 0),
        "emerald-u.gba" to Triple(0x1F1C08FBL, "BPEE", 0),
        "ruby-u.gba" to Triple(0xF0815EE7L, "AXVE", 0),
        "sapphire-u.gba" to Triple(0x554DEDC4L, "AXPE", 0),
    )

    @Test
    fun `every ROM of the six is there when IRONMON_ROMS is set, and each is the retail game its table is named for`() {
        if (roms.isEmpty()) return
        assertEquals(known.keys.sorted(), roms.map { it.name }.sorted(),
            "a dump is missing from IRONMON_ROMS, so its addresses were not checked")
        for (r in roms) {
            val (crc, code, version) = known.getValue(r.name)
            val c = java.util.zip.CRC32().also { it.update(r.bytes) }.value
            assertEquals(crc, c, "${r.name}: not the retail dump (CRC32 %08x)".format(c))
            assertEquals(code, String(r.bytes, 0xAC, 4, Charsets.US_ASCII), "${r.name}: game code")
            assertEquals(version, r.bytes[0xBC].toInt() and 255, "${r.name}: version byte")
            assertEquals(16 * 1024 * 1024, r.bytes.size, "${r.name}: 16 MiB")
        }
        println("OverworldAddressTest: ${roms.size} ROMs checked: " + roms.joinToString { it.name })
    }

    @Test
    fun `gMain and the oamBuffer offset are what LoadOam of each game says`() = each { r ->
        val at = r.locate("LoadOam", LOAD_OAM)
        // adds r0, #imm is the offset of gMain.oamBuffer; the pool holds gMain, and the offset of the byte just past the buffer.
        val imm = r.bytes[at + 0x14].toInt() and 255
        assertEquals(r.table.oamBufferOffset, imm, "${r.name}: gMain.oamBuffer offset")
        assertEquals(r.table.main, r.u32(at + 0x24), "${r.name}: gMain")
        // oamLoadDisabled is the byte 0x401 past the buffer's start (the flags byte after state), in FireRed and Ruby's layouts alike.
        assertEquals((r.table.oamBufferOffset + 0x401).toLong(), r.u32(at + 0x28), "${r.name}: oamLoadDisabled offset")
        // CpuCopy32 of 0x100 words: the buffer is 128 entries of 8 bytes, copied to OAM at 0x07000000.
        assertEquals(0x04000100L, r.u32(at + 0x2C), "${r.name}: the copy is 0x100 words")
        assertEquals(0xE0, r.bytes[at + 0x16].toInt() and 255)
    }

    @Test
    fun `gMain callback2 is at plus 4 and gMain is what SetMainCallback2 of each game says`() = each { r ->
        val places = r.findAll(sig(SET_MAIN_CALLBACK2_FRE)) + r.findAll(sig(SET_MAIN_CALLBACK2_RS))
        assertEquals(1, places.size, "SetMainCallback2 in ${r.name}: ${places.size} places match its code")
        val at = places.single()
        // ldr r1, [pc, #12] (= gMain) then str r0, [r1, #4]: the callback goes to gMain + 4, which is where sprite_core.h reads it.
        assertEquals(0x4903, (r.bytes[at].toInt() and 255) or ((r.bytes[at + 1].toInt() and 255) shl 8), "ldr r1, [pc, #12]")
        assertEquals(0x6048, (r.bytes[at + 2].toInt() and 255) or ((r.bytes[at + 3].toInt() and 255) shl 8), "str r0, [r1, #4]: callback2 is at gMain + 4")
        assertEquals(r.table.main, r.u32(at + 0x10), "${r.name}: gMain by a second function")
    }

    @Test
    fun `gSprites and the camera offsets, and the whole Sprite layout, are what UpdateOamCoords of each game says`() = each { r ->
        val at = r.locate("UpdateOamCoords", UPDATE_OAM_COORDS)
        assertEquals(r.table.sprites, r.u32(at + 0x6C), "${r.name}: gSprites")
        assertEquals(r.table.coordOffsetX, r.u32(at + 0x78), "${r.name}: gSpriteCoordOffsetX")
        assertEquals(r.table.coordOffsetY, r.u32(at + 0x7C), "${r.name}: gSpriteCoordOffsetY")
        // The code reads x at +0x20, y at +0x22, x2 at +0x24, y2 at +0x26 (ldrsh/ldrh with those immediates), the two
        // centre-to-corner bytes at +0x28 and +0x29, the flags at +0x3E (inUse 1, coordOffsetEnabled 2, invisible 4),
        // and sizeof(Sprite) is 0x44 (index * 17 * 4). The signature holding those bytes IS that proof; spell the ones
        // the native code relies on out anyway, so this fails with words if the signature is ever loosened.
        fun halfword(o: Int) = (r.bytes[at + o].toInt() and 255) or ((r.bytes[at + o + 1].toInt() and 255) shl 8)
        assertEquals(0x0120, halfword(0x0C), "lsls r0, r4, #4 (index * 16)")
        assertEquals(0x1900, halfword(0x0E), "adds r0, r0, r4 (* 17)")
        assertEquals(0x0080, halfword(0x10), "lsls r0, r0, #2 (* 4 = 0x44 a sprite)")
        assertEquals(0x303E, halfword(0x16), "adds r0, #0x3e: the flags byte")
        assertEquals(0x2005, halfword(0x1A), "movs r0, #5: inUse and not invisible")
        assertEquals(0x2801, halfword(0x1E), "cmp r0, #1")
        assertEquals(0x2002, halfword(0x22), "movs r0, #2: coordOffsetEnabled")
        assertEquals(0x2220, halfword(0x2A), "movs r2, #0x20: x")
        assertEquals(0x2224, halfword(0x2E), "movs r2, #0x24: x2")
        assertEquals(0x3028, halfword(0x36), "adds r0, #0x28: centerToCornerVecX")
        assertEquals(0x8CD9, halfword(0x54), "ldrh r1, [r3, #0x26]: y2")
        assertEquals(0x8C58, halfword(0x56), "ldrh r0, [r3, #0x22]: y")
        assertEquals(0x3029, halfword(0x5C), "adds r0, #0x29: centerToCornerVecY")
        assertEquals(0x2C3F, halfword(0xB8), "cmp r4, #0x3f: 64 sprites")
        // The pool: the 9-bit mask the game puts x in, and the mask that keeps the rest of the attribute.
        assertEquals(0x1FFL, r.u32(at + 0x70))
        assertEquals(0xFFFFFE00L, r.u32(at + 0x74))
    }

    @Test
    fun `gObjectEvents, gPlayerAvatar and the ObjectEvent layout are what GetPlayerFacingDirection of each game says`() = each { r ->
        val at = r.locate("GetPlayerFacingDirection", PLAYER_FACING)
        assertEquals(r.table.objectEvents, r.u32(at + 0x18), "${r.name}: gObjectEvents")
        assertEquals(r.table.playerAvatar, r.u32(at + 0x1C), "${r.name}: gPlayerAvatar")
        // ldrb r1, [r0, #5] (objectEventId), * 9 * 4 (0x24 bytes an object event), ldrb [.., #0x18], low nibble (lsl 28, lsr 28).
        fun halfword(o: Int) = (r.bytes[at + o].toInt() and 255) or ((r.bytes[at + o + 1].toInt() and 255) shl 8)
        assertEquals(0x7941, halfword(4), "ldrb r1, [r0, #5]: gPlayerAvatar.objectEventId")
        assertEquals(0x00C8, halfword(6), "lsls r0, r1, #3")
        assertEquals(0x1840, halfword(8), "adds r0, r0, r1 (* 9)")
        assertEquals(0x0080, halfword(10), "lsls r0, r0, #2 (* 4)")
        assertEquals(0x7E00, halfword(14), "ldrb r0, [r0, #0x18]: ObjectEvent.facingDirection")
        assertEquals(0x0700, halfword(16), "lsls r0, r0, #0x1c")
        assertEquals(0x0F00, halfword(18), "lsrs r0, r0, #0x1c")
    }

    @Test
    fun `gPlayerAvatar spriteId is at +4, runningState at +2, in every game`() = each { r ->
        val at = r.locate("GetPlayerAvatarObjectId", AVATAR_SPRITE_ID)
        assertEquals(r.table.playerAvatar, r.u32(at + 8), "${r.name}: gPlayerAvatar")
        val ldrb = (r.bytes[at + 2].toInt() and 255) or ((r.bytes[at + 3].toInt() and 255) shl 8)
        assertEquals(0x7900, ldrb, "ldrb r0, [r0, #4]: gPlayerAvatar.spriteId")
        // CheckMovementInputNotOnBike stores runningState (strb [.., #2]): 0 for no input, then compares it with 2 (MOVING)
        // and stores 1 (TURN_DIRECTION) or 2. tileTransitionState (+3) is UpdatePlayerAvatarTransitionState's, checked below.
        val c = r.locate("CheckMovementInputNotOnBike", CHECK_MOVEMENT_INPUT)
        assertEquals(r.table.playerAvatar, r.u32(c + 0x14), "${r.name}: gPlayerAvatar in CheckMovementInputNotOnBike")
        assertEquals(r.table.playerAvatar, r.u32(c + 0x30))
        assertEquals(r.table.playerAvatar, r.u32(c + 0x40))
        fun halfword(o: Int) = (r.bytes[c + o].toInt() and 255) or ((r.bytes[c + o + 1].toInt() and 255) shl 8)
        assertEquals(0x7084, halfword(0x0C), "strb r4, [r0, #2]: runningState = NOT_MOVING when nothing is pressed")
        assertEquals(0x7888, halfword(0x26), "ldrb r0, [r1, #2]: runningState")
        assertEquals(0x2802, halfword(0x28), "cmp r0, #2: MOVING")
        assertEquals(0x2001, halfword(0x2C), "movs r0, #1: TURN_DIRECTION")
        assertEquals(0x2002, halfword(0x36), "movs r0, #2: MOVING")
        assertEquals(0x7088, halfword(0x38), "strb r0, [r1, #2]: runningState stored")
        // UpdatePlayerAvatarTransitionState writes tileTransitionState (strb [r4, #3]): 0, then 1 (T_TILE_TRANSITION) or 2 (T_TILE_CENTER).
        val u = r.locate("UpdatePlayerAvatarTransitionState", TRANSITION_STATE)
        assertEquals(r.table.playerAvatar, r.u32(u + 0x2C), "${r.name}: gPlayerAvatar in UpdatePlayerAvatarTransitionState")
        fun uh(o: Int) = (r.bytes[u + o].toInt() and 255) or ((r.bytes[u + o + 1].toInt() and 255) shl 8)
        assertEquals(0x70E0, uh(0x06), "strb r0, [r4, #3]: tileTransitionState = T_NOT_MOVING first")
        assertEquals(0x2001, uh(0x26), "movs r0, #1: T_TILE_TRANSITION")
        assertEquals(0x2002, uh(0x3A), "movs r0, #2: T_TILE_CENTER")
        assertEquals(0x70E0, uh(0x3C), "strb r0, [r4, #3]: tileTransitionState stored")
    }

    @Test
    fun `the callback pointers are the Thumb addresses of CB2_Overworld and CB2_OverworldBasic in each game`() = each { r ->
        val at = r.locate("CB2_Overworld", CB2_OVERWORLD)
        assertEquals(r.table.cb2Overworld, 0x08000000L + at + 1, "${r.name}: CB2_Overworld | 1")
        // CB2_OverworldBasic sits 0xC bytes before it: push {lr}; bl OverworldBasic; pop {r0}; bx r0.
        val basic = at - 0xC
        assertEquals(r.table.cb2OverworldBasic, 0x08000000L + basic + 1, "${r.name}: CB2_OverworldBasic | 1")
        val code = sig("00b5 fff7 ..ff 01bc 0047")
        code.forEachIndexed { i, s -> if (s >= 0) assertEquals(s, r.bytes[basic + i].toInt() and 255, "${r.name}: CB2_OverworldBasic byte $i") }
        // A Thumb function pointer has its low bit set, and so do the table's.
        assertTrue(r.table.cb2Overworld and 1L == 1L && r.table.cb2OverworldBasic and 1L == 1L)
    }

    @Test
    fun `the palette buffers are what BlendPalette and TransferPlttBuffer of each game say`() = each { r ->
        val b = r.locate("BlendPalette", BLEND_PALETTE)
        assertEquals(r.table.plttUnfaded, r.u32(b + 0x98), "${r.name}: gPlttBufferUnfaded")
        assertEquals(r.table.plttFaded, r.u32(b + 0x9C), "${r.name}: gPlttBufferFaded")
        val t = r.locate("TransferPlttBuffer", TRANSFER_PLTT)
        assertEquals(r.table.plttFaded, r.u32(t + 0x4C), "${r.name}: gPlttBufferFaded is what goes to palette RAM")
        // Both are 0x400 bytes (0x200 colours) and sit 0x400 apart in every game; the OBJ palettes start
        // 0x200 bytes in, which is where sprite_core.h reads (kObjPlttBufferOffset).
        assertEquals(0x400L, r.table.plttFaded - r.table.plttUnfaded, "${r.name}: the two buffers are adjacent")
    }

    // ---------------------------------------------------------------------------------
    // No ROM needed below.

    @Test
    fun `every supported game has a table and Nat Dex has none`() {
        val names = listOf(GameMap.FIRERED_U_V10, GameMap.FIRERED_U_V11, GameMap.LEAFGREEN_U, GameMap.EMERALD_U, GameMap.RUBY_U, GameMap.SAPPHIRE_U).map { it.name }
        assertEquals(names.sorted(), Overworld.ALL.map { it.name }.sorted())
        for (m in listOf(GameMap.FIRERED_U_V10, GameMap.FIRERED_U_V11, GameMap.LEAFGREEN_U, GameMap.EMERALD_U, GameMap.RUBY_U, GameMap.SAPPHIRE_U)) {
            assertNotNull(Overworld.forMap(m), m.name)
        }
        val natdex = GameMap.FIRERED_U_V10.copy(name = "Nat. Dex", expandedSpeciesIds = true)
        assertNull(Overworld.forMap(natdex))
        assertTrue("Nat. Dex" in Overworld.whyNot(natdex))
    }

    @Test
    fun `the tables agree with the tracker's own gMain-adjacent addresses and with pret's rev1 for FireRed 1_1`() {
        // Every RAM address of v1.1 is v1.0's; only the callbacks move (pokefirered_rev1.sym).
        val a = Overworld.FIRERED_U_V10
        val b = Overworld.FIRERED_U_V11
        assertEquals(a.copy(name = b.name, cb2Overworld = b.cb2Overworld, cb2OverworldBasic = b.cb2OverworldBasic), b)
        assertEquals(0x080565C8L + 1, b.cb2Overworld)
        assertEquals(0x080565BCL + 1, b.cb2OverworldBasic)
        // Sapphire is Ruby with its own callbacks (pokesapphire.sym: 080543A8 and 0805439C).
        assertEquals(0x080543A8L + 1, Overworld.SAPPHIRE_U.cb2Overworld)
        assertEquals(0x0805439CL + 1, Overworld.SAPPHIRE_U.cb2OverworldBasic)
        // The tracker's own battle-side addresses sit in the same memory: gPlayerParty and the save blocks are
        // clear of everything the overworld reads (a wrong table that overlapped them would show here).
        for (m in listOf(GameMap.FIRERED_U_V10, GameMap.EMERALD_U, GameMap.RUBY_U)) {
            val o = Overworld.forMap(m)!!
            val party = m.party
            val spritesEnd = o.sprites + 65 * 0x44
            assertTrue(party !in o.sprites until spritesEnd, "${m.name}: gPlayerParty inside gSprites")
        }
    }

    @Test
    fun `the config words are in the order the native side reads them, with a Thumb bit on both callbacks`() {
        for (o in Overworld.ALL) {
            val c = o.toConfig()
            assertEquals(11, c.size)
            assertEquals(o.main, c[0]); assertEquals(o.oamBufferOffset.toLong(), c[1])
            assertEquals(o.cb2Overworld, c[2]); assertEquals(o.cb2OverworldBasic, c[3])
            assertEquals(o.playerAvatar, c[4]); assertEquals(o.sprites, c[5])
            assertEquals(o.coordOffsetX, c[6]); assertEquals(o.coordOffsetY, c[7])
            assertEquals(o.plttUnfaded, c[8]); assertEquals(o.plttFaded, c[9]); assertEquals(o.objectEvents, c[10])
            assertTrue(c[2] and 1L == 1L && c[3] and 1L == 1L, o.name)
            assertTrue(c[1] == 0x38L || c[1] == 0x3CL, o.name)
        }
    }
}
