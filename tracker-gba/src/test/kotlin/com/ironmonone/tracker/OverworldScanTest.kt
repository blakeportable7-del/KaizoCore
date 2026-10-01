package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OverworldScan reads "Play as your Pokemon"'s addresses out of a game's own code, which is how Nat. Dex is supported
 * (it is in no table, and its RAM moves between versions). Three kinds of proof:
 *
 *  - on the six retail dumps the scan returns EXACTLY the tables in [Overworld], which were read from the pret symbol
 *    files (independent of the scan) and are checked against the same dumps a different way by OverworldAddressTest;
 *  - on both Nat. Dex 1.2.1 dumps it finds every function once, and every function that names gPlayerAvatar agrees;
 *  - on a made-up ROM each rule refuses with its reason named, so a test that goes red is red for that rule.
 *
 * The ROM tests need IRONMON_ROMS (a folder of dumps, never copied anywhere) and skip cleanly, saying so, without it.
 */
class OverworldScanTest {
    private val dir: File? = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory }

    private fun rom(name: String): ByteArray? {
        if (dir == null) { println("OverworldScanTest skipped: set IRONMON_ROMS"); return null }
        val f = File(dir, name)
        if (!f.isFile) { println("OverworldScanTest: $name missing, that game is skipped"); return null }
        return f.readBytes()
    }

    /** Every field but the name. */
    private fun sameAddresses(want: OverworldAddresses, got: OverworldAddresses?, what: String) {
        assertNotNull(got, "$what: the scan found nothing")
        assertEquals(want.copy(name = "x"), got.copy(name = "x"), what)
    }

    // ------------------------------------------------------------------ the six retail dumps

    @Test
    fun `on each retail dump the scan returns exactly the table`() {
        var checked = 0
        for ((file, table) in listOf(
            "firered-u-v10.gba" to Overworld.FIRERED_U_V10, "firered-u-v11.gba" to Overworld.FIRERED_U_V11,
            "leafgreen-u.gba" to Overworld.LEAFGREEN_U, "emerald-u.gba" to Overworld.EMERALD_U,
            "ruby-u.gba" to Overworld.RUBY_U, "sapphire-u.gba" to Overworld.SAPPHIRE_U,
        )) {
            val bytes = rom(file) ?: continue
            val out = OverworldScan.scan(bytes)
            assertEquals("found", out.why, "$file: ${out.why}")
            sameAddresses(table, out.addresses, file)
            assertTrue(out.gMainConfirmed, "$file: SetMainCallback2 was not found to confirm gMain")
            checked++
        }
        if (dir != null) assertEquals(6, checked, "a retail dump is missing from IRONMON_ROMS, so the scan was not checked against it")
        if (dir != null) println("OverworldScanTest: the scan matched the table on $checked retail dumps")
    }

    @Test
    fun `reading the ROM through the memory reader gives what reading the bytes gives`() {
        val bytes = rom("firered-u-v11.gba") ?: return
        var reads = 0
        val reader = MemoryReader { a, n ->
            reads++
            val off = (a - 0x08000000L).toInt()
            if (off < 0 || off + n > bytes.size) ByteArray(0) else bytes.copyOfRange(off, off + n)
        }
        sameAddresses(Overworld.FIRERED_U_V11, OverworldScan.find(reader), "through the reader")
        assertEquals(1, reads, "the window is read once")
    }

    // ------------------------------------------------------------------ Nat. Dex

    private class Golden(val file: String, val main: Long, val cb2: Long, val avatar: Long, val sprites: Long, val coordX: Long,
                         val plttUnfaded: Long, val objectEvents: Long)

    @Test
    fun `both Nat Dex 1_2_1 dumps are read, and what is read is consistent with itself and with the retail games' unmoved variables`() {
        // What the scan reads out of each dump. These numbers come from the dumps themselves (there is no symbol file for
        // Nat. Dex), so they are a record of what was found, and the relations after them are what makes them believable.
        val dumps = listOf(
            Golden("firered-natdex-121.gba", main = 0x03002CC0, cb2 = 0x0805DB55, avatar = 0x02037024, sprites = 0x0202063C,
                coordX = 0x02021BC8, plttUnfaded = 0x020371A4, objectEvents = 0x02036DE4),
            Golden("emerald-natdex-121.gba", main = 0x03002CC0, cb2 = 0x08072379, avatar = 0x0203734C, sprites = 0x02020630,
                coordX = 0x02021BBC, plttUnfaded = 0x020374D0, objectEvents = 0x0203710C),
        )
        var checked = 0
        for (d in dumps) {
            val bytes = rom(d.file) ?: continue
            val out = OverworldScan.scan(bytes)
            val a = assertNotNull(out.addresses, "${d.file}: ${out.why}")
            assertTrue(out.gMainConfirmed, "${d.file}: SetMainCallback2 does not confirm gMain")
            assertEquals(d.main, a.main, "${d.file}: gMain")
            assertEquals(0x38, a.oamBufferOffset, "${d.file}: gMain.oamBuffer")
            assertEquals(d.cb2, a.cb2Overworld, "${d.file}: CB2_Overworld")
            assertEquals(d.cb2 - 0xC, a.cb2OverworldBasic, "${d.file}: CB2_OverworldBasic")
            assertEquals(d.avatar, a.playerAvatar, "${d.file}: gPlayerAvatar")
            assertEquals(d.sprites, a.sprites, "${d.file}: gSprites")
            assertEquals(d.coordX, a.coordOffsetX, "${d.file}: gSpriteCoordOffsetX")
            assertEquals(d.coordX + 2, a.coordOffsetY, "${d.file}: gSpriteCoordOffsetY")
            assertEquals(d.plttUnfaded, a.plttUnfaded, "${d.file}: gPlttBufferUnfaded")
            assertEquals(d.plttUnfaded + 0x400, a.plttFaded, "${d.file}: gPlttBufferFaded")
            assertEquals(d.objectEvents, a.objectEvents, "${d.file}: gObjectEvents")

            // The relations the game's own layout imposes: 16 object events of 0x24 bytes end where gPlayerAvatar starts
            // (0x240, in every retail game too), and a Thumb pointer has its low bit set.
            assertEquals(0x240L, a.playerAvatar - a.objectEvents, "${d.file}: gObjectEvents is 16 x 0x24 before gPlayerAvatar")
            assertTrue(a.cb2Overworld and 1L == 1L && a.cb2OverworldBasic and 1L == 1L)
            assertTrue(a.cb2Overworld in 0x08000001L..0x08200000L, "${d.file}: CB2_Overworld is in the ROM's first 2 MiB")

            // Variables that sit before the part of RAM that moved are where the retail game has them: gSprites and the camera
            // offsets. The scan found those from the Nat. Dex code alone, so that is an independent agreement.
            val retail = if (d.file.startsWith("firered")) Overworld.FIRERED_U_V11 else Overworld.EMERALD_U
            assertEquals(retail.sprites, a.sprites, "${d.file}: gSprites is where the retail game has it")
            assertEquals(retail.coordOffsetX, a.coordOffsetX)
            assertEquals(retail.coordOffsetY, a.coordOffsetY)
            checked++
        }
        if (dir != null) assertEquals(2, checked, "a Nat. Dex dump is missing from IRONMON_ROMS")
        if (dir != null) println("OverworldScanTest: the scan read $checked Nat. Dex dumps")
    }

    @Test
    fun `resolve reads the ROM for Nat Dex only, and never for a retail game`() {
        val natdexMap = GameMap.FIRERED_U_V10.copy(name = "Nat. Dex", expandedSpeciesIds = true)
        var reads = 0
        val nothing = MemoryReader { _, _ -> reads++; ByteArray(0) }
        // A retail game: the table, without one read.
        assertEquals(Overworld.FIRERED_U_V11, Overworld.resolve(GameMap.FIRERED_U_V11, nothing))
        assertEquals(Overworld.EMERALD_U, Overworld.resolve(GameMap.EMERALD_U, nothing))
        assertEquals(0, reads, "a retail game's ROM is not read")
        // Nat. Dex with a ROM that cannot be read: nothing, after trying.
        assertNull(Overworld.resolve(natdexMap, nothing))
        assertTrue(reads > 0)
        // A game the tracker cannot name is not searched.
        reads = 0
        assertNull(Overworld.resolve(GameMap.FIRERED_U_V10.copy(name = "Some hack"), nothing))
        assertEquals(0, reads)
        assertTrue(Overworld.isNatDex(natdexMap) && !Overworld.isNatDex(GameMap.FIRERED_U_V10))
        // Nat. Dex with its ROM: the scan.
        val bytes = rom("firered-natdex-121.gba") ?: return
        val reader = MemoryReader { a, n ->
            val off = (a - 0x08000000L).toInt()
            if (off < 0 || off + n > bytes.size) ByteArray(0) else bytes.copyOfRange(off, off + n)
        }
        val found = assertNotNull(Overworld.resolve(natdexMap, reader))
        assertEquals(OverworldScan.NAME, found.name)
        assertEquals(0x03002CC0L, found.main)
        // The line a player reads when it cannot be found.
        assertTrue("Nat. Dex" in Overworld.whyNot(natdexMap))
    }

    // ------------------------------------------------------------------ a made-up ROM: every rule, and why it refuses

    /** A ROM with the eight functions at known places and their pools filled from FireRed's numbers, to break one thing at a time. */
    private class Fake(size: Int = 0x40000) {
        val rom = ByteArray(size)
        fun place(p: OverworldScan.Pattern, at: Int) { for (i in p.bytes.indices) rom[at + i] = if (p.bytes[i] >= 0) p.bytes[i].toByte() else 0 }
        fun u32(at: Int, v: Long) { for (i in 0 until 4) rom[at + i] = (v shr (8 * i)).toByte() }
        fun u8(at: Int, v: Int) { rom[at] = v.toByte() }
        fun scan() = OverworldScan.scan(rom)
        /** gMain as LoadOam and SetMainCallback2 both name it. */
        fun gMain(v: Long) { u32(LOAD + 0x24, v); u32(SETMAIN + 0x10, v) }

        companion object {
            const val LOAD = 0x1000; const val UPDATE = 0x2000; const val FACING = 0x3000; const val AVATAR = 0x4000
            const val CHECK = 0x5000; const val TRANSITION = 0x6000; const val CB2 = 0x7010; const val BLEND = 0x8000; const val SETMAIN = 0xA000

            /** The functions [shift] bytes further in than their usual places (the tests poke the usual ones, so 0 unless timing). */
            fun fireRedLike(shift: Int = 0, size: Int = 0x40000): Fake = Fake(size).apply {
                place(OverworldScan.LOAD_OAM, LOAD + shift); u8(LOAD + shift + 0x14, 0x38); u32(LOAD + shift + 0x24, 0x030030F0); u32(LOAD + shift + 0x28, 0x439)
                place(OverworldScan.UPDATE_OAM_COORDS, UPDATE + shift)
                u32(UPDATE + shift + 0x6C, 0x0202063C); u32(UPDATE + shift + 0x78, 0x02021BC8); u32(UPDATE + shift + 0x7C, 0x02021BCA)
                place(OverworldScan.PLAYER_FACING, FACING + shift); u32(FACING + shift + 0x18, 0x02036E38); u32(FACING + shift + 0x1C, 0x02037078)
                place(OverworldScan.AVATAR_SPRITE_ID, AVATAR + shift); u32(AVATAR + shift + 8, 0x02037078)
                place(OverworldScan.CHECK_MOVEMENT_INPUT, CHECK + shift); for (o in listOf(0x14, 0x30, 0x40)) u32(CHECK + shift + o, 0x02037078)
                place(OverworldScan.TRANSITION_STATE, TRANSITION + shift); u32(TRANSITION + shift + 0x2C, 0x02037078)
                place(OverworldScan.CB2_OVERWORLD_BASIC, CB2 + shift - 0xC); place(OverworldScan.CB2_OVERWORLD, CB2 + shift)
                place(OverworldScan.BLEND_PALETTE, BLEND + shift); u32(BLEND + shift + 0x98, 0x020371F8); u32(BLEND + shift + 0x9C, 0x020375F8)
                place(OverworldScan.SET_MAIN_CALLBACK2[0], SETMAIN + shift); u32(SETMAIN + shift + 0x10, 0x030030F0)
            }
        }
    }

    private fun refused(f: Fake, reason: String) {
        val out = f.scan()
        assertNull(out.addresses, "it should have refused: $reason")
        assertTrue(reason in out.why, "refused, but for '${out.why}', not for '$reason'")
    }

    @Test
    fun `a made-up ROM with all eight functions is read, and every number comes from its own function`() {
        val out = Fake.fireRedLike().scan()
        assertEquals("found", out.why)
        val a = assertNotNull(out.addresses)
        assertEquals(
            OverworldAddresses(
                name = OverworldScan.NAME, main = 0x030030F0, oamBufferOffset = 0x38,
                cb2Overworld = 0x08000000L + Fake.CB2 + 1, cb2OverworldBasic = 0x08000000L + Fake.CB2 - 0xC + 1,
                playerAvatar = 0x02037078, sprites = 0x0202063C, coordOffsetX = 0x02021BC8, coordOffsetY = 0x02021BCA,
                plttUnfaded = 0x020371F8, plttFaded = 0x020375F8, objectEvents = 0x02036E38,
            ), a)
        // Ruby's layout (oamBuffer 0x3C, camera offsets and object events in IWRAM) is accepted as well.
        val ruby = Fake.fireRedLike().apply {
            u8(Fake.LOAD + 0x14, 0x3C); gMain(0x03001770); u32(Fake.LOAD + 0x28, 0x43D)
            u32(Fake.UPDATE + 0x78, 0x030024D0); u32(Fake.UPDATE + 0x7C, 0x030027E0); u32(Fake.FACING + 0x18, 0x030048A0)
        }.scan()
        assertEquals("found", ruby.why)
        assertEquals(0x3C, ruby.addresses!!.oamBufferOffset)
        assertEquals(0x030024D0L, ruby.addresses!!.coordOffsetX)
    }

    @Test
    fun `a function that is missing, in two places, or past the window is a refusal`() {
        refused(Fake.fireRedLike().apply { for (i in 0 until OverworldScan.BLEND_PALETTE.size) rom[Fake.BLEND + i] = 0 }, "BlendPalette is not in")
        refused(Fake.fireRedLike().apply { place(OverworldScan.LOAD_OAM, 0x9000) }, "LoadOam is in more than one place")
        refused(Fake.fireRedLike().apply { place(OverworldScan.CB2_OVERWORLD, 0x9010) }, "CB2_Overworld is in more than one place")
        // Past the window: a complete ROM whose last function is real but too far in to be searched for. The same ROM with that
        // function inside the window is read, so it is the distance and nothing else that refuses it.
        val whole = Fake.fireRedLike(size = OverworldScan.WINDOW + 0x10000)
        assertEquals("found", whole.scan().why)
        val far = Fake.fireRedLike(size = OverworldScan.WINDOW + 0x10000).apply {
            for (i in 0 until OverworldScan.BLEND_PALETTE.size) rom[Fake.BLEND + i] = 0
            val at = OverworldScan.WINDOW + 0x100
            place(OverworldScan.BLEND_PALETTE, at); u32(at + 0x98, 0x020371F8); u32(at + 0x9C, 0x020375F8)
        }
        refused(far, "BlendPalette is not in the first")
        assertNull(OverworldScan.scan(ByteArray(0)).addresses)
        assertNull(OverworldScan.scan(ByteArray(0x1000)).addresses)
    }

    @Test
    fun `the oamBuffer layout, and the words beside it, have to be the ones a game has`() {
        refused(Fake.fireRedLike().apply { u8(Fake.LOAD + 0x14, 0x40) }, "neither of the two layouts")
        refused(Fake.fireRedLike().apply { u32(Fake.LOAD + 0x28, 0x1234) }, "the flag after the buffer")
        refused(Fake.fireRedLike().apply { u32(Fake.UPDATE + 0x70, 0x0FF) }, "UpdateOamCoords")   // a byte of the fixed mask: the code no longer matches
    }

    @Test
    fun `gMain has to be the same in LoadOam and in SetMainCallback2, which is optional`() {
        val plain = Fake.fireRedLike().scan()
        assertTrue(plain.gMainConfirmed, "the made-up ROM has SetMainCallback2, and it agrees")
        refused(Fake.fireRedLike().apply { u32(Fake.SETMAIN + 0x10, 0x030030F4) }, "SetMainCallback2 does not agree with LoadOam")
        refused(Fake.fireRedLike().apply { place(OverworldScan.SET_MAIN_CALLBACK2[0], 0xB000); u32(0xB010, 0x030030F0) }, "SetMainCallback2 is in more than one place")
        // Ruby and Sapphire compile it another way: that shape is read as well, and has to agree as well.
        val ruby = Fake.fireRedLike().apply {
            for (i in 0 until OverworldScan.SET_MAIN_CALLBACK2[0].size) rom[Fake.SETMAIN + i] = 0
            place(OverworldScan.SET_MAIN_CALLBACK2[1], Fake.SETMAIN); u32(Fake.SETMAIN + 0x10, 0x030030F0)
        }
        assertTrue(ruby.scan().gMainConfirmed)
        refused(ruby.apply { u32(Fake.SETMAIN + 0x10, 0x030030F8) }, "SetMainCallback2 does not agree with LoadOam")
        // A build without either shape (a hack that rewrote it) is read on LoadOam alone, and says it was not confirmed.
        val bare = Fake.fireRedLike().apply { for (i in 0 until OverworldScan.SET_MAIN_CALLBACK2[0].size) rom[Fake.SETMAIN + i] = 0 }.scan()
        assertEquals("found", bare.why)
        assertTrue(!bare.gMainConfirmed)
    }

    @Test
    fun `every function that names gPlayerAvatar must agree on it`() {
        val wrong = 0x02037080L
        refused(Fake.fireRedLike().apply { u32(Fake.AVATAR + 8, wrong) }, "GetPlayerAvatarObjectId does not agree")
        for (o in listOf(0x14, 0x30, 0x40)) refused(Fake.fireRedLike().apply { u32(Fake.CHECK + o, wrong) }, "CheckMovementInputNotOnBike does not agree")
        refused(Fake.fireRedLike().apply { u32(Fake.TRANSITION + 0x2C, wrong) }, "UpdatePlayerAvatarTransitionState does not agree")
    }

    @Test
    fun `CB2_OverworldBasic has to be where CB2_Overworld says, and the palette buffers have to be a pair`() {
        refused(Fake.fireRedLike().apply { for (i in 0 until 10) rom[Fake.CB2 - 0xC + i] = 0 }, "CB2_OverworldBasic is not where")
        refused(Fake.fireRedLike().apply { u32(Fake.BLEND + 0x9C, 0x020373F8) }, "0x400 bytes apart")
        refused(Fake.fireRedLike().apply { u32(Fake.BLEND + 0x9C, 0x020371F8) }, "0x400 bytes apart")
    }

    @Test
    fun `every address has to be somewhere in RAM, whole and aligned`() {
        refused(Fake.fireRedLike().apply { gMain(0x02000000) }, "gMain is not in IWRAM")
        refused(Fake.fireRedLike().apply { gMain(0x08000000) }, "gMain is not in IWRAM")
        refused(Fake.fireRedLike().apply { gMain(0x03007F00) }, "gMain is not in IWRAM")   // the buffer would run past the end of IWRAM
        refused(Fake.fireRedLike().apply { gMain(0x030030F2) }, "gMain is not in IWRAM")   // not word aligned
        refused(Fake.fireRedLike().apply { u32(Fake.UPDATE + 0x6C, 0x08000000) }, "gSprites is not in RAM")
        refused(Fake.fireRedLike().apply { u32(Fake.UPDATE + 0x6C, 0x0203F000) }, "gSprites is not in RAM")   // 64 sprites run past the end of EWRAM
        refused(Fake.fireRedLike().apply { u32(Fake.UPDATE + 0x78, 0x02021BC9) }, "the camera offsets")
        refused(Fake.fireRedLike().apply { u32(Fake.UPDATE + 0x7C, 0x0) }, "the camera offsets")
        refused(Fake.fireRedLike().apply { u32(Fake.FACING + 0x18, 0x0) }, "gObjectEvents is not in RAM")
        // gPlayerAvatar: all six places have to agree first, so change every one of them.
        refused(Fake.fireRedLike().apply {
            val bad = 0x0800000CL
            u32(Fake.FACING + 0x1C, bad); u32(Fake.AVATAR + 8, bad); for (o in listOf(0x14, 0x30, 0x40)) u32(Fake.CHECK + o, bad); u32(Fake.TRANSITION + 0x2C, bad)
        }, "gPlayerAvatar is not in RAM")
        refused(Fake.fireRedLike().apply { u32(Fake.BLEND + 0x98, 0x0203FC00); u32(Fake.BLEND + 0x9C, 0x02040000) }, "the palette buffers are not in RAM")
    }

    @Test
    fun `the scan is fast enough to run when a game starts, with every function at the far end of the window`() {
        // All eight sit in the last 64 KiB of the window, so each of the eight searches walks about 2 MiB.
        val f = Fake.fireRedLike(shift = OverworldScan.WINDOW - 0x10000, size = OverworldScan.WINDOW)
        assertEquals("found", f.scan().why)
        val t0 = System.nanoTime()
        repeat(5) { assertNotNull(f.scan().addresses) }
        val ms = (System.nanoTime() - t0) / 1_000_000 / 5
        println("OverworldScanTest: a scan with everything at the far end of the window took $ms ms")
        assertTrue(ms < 2000, "one scan took $ms ms")
    }
}
