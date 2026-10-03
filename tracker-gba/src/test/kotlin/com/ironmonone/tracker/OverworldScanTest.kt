package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OverworldScan reads "Play as your Pokemon"'s addresses out of a game's own code, which is how Nat. Dex is supported
 * (it is in no table, and its RAM moves between versions). Three kinds of proof:
 *
 *  - on the six retail dumps the scan returns EXACTLY the tables in [Overworld], which were read from the pret symbol
 *    files (independent of the scan) and are checked against the same dumps a different way by OverworldAddressTest;
 *  - on both Nat. Dex 1.2.1 dumps and the MaxDex 1.0 build it finds every function once, and every function that names
 *    gPlayerAvatar agrees (on MaxDex, so does the RAM its tracker extension hardcodes);
 *  - on a made-up ROM each rule refuses with its reason named, so a test that goes red is red for that rule.
 *
 * The ROM tests need IRONMON_ROMS (a folder of dumps, never copied anywhere) and skip cleanly, saying so, without it.
 */
class OverworldScanTest {
    private val dir: File? = Dumps.romsDir()

    private fun rom(name: String): ByteArray? {
        if (dir == null) { println("OverworldScanTest skipped: set IRONMON_ROMS"); return null }
        val f = Dumps.file(dir, name)
        if (f == null) { println("OverworldScanTest: $name missing, that game is skipped"); return null }
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

    /** The emulator side's reader: nothing at all for a read over 1 MiB (libretrodroidjni.cpp readMemory). */
    private fun phoneReader(bytes: ByteArray, count: () -> Unit = {}) = MemoryReader { a, n ->
        count()
        val off = (a - 0x08000000L).toInt()
        if (n > OverworldScan.READ_MAX || off < 0 || off + n > bytes.size) ByteArray(0) else bytes.copyOfRange(off, off + n)
    }

    @Test
    fun `reading the ROM through the memory reader gives what reading the bytes gives`() {
        val bytes = rom("firered-u-v11.gba") ?: return
        var reads = 0
        sameAddresses(Overworld.FIRERED_U_V11, OverworldScan.find(phoneReader(bytes) { reads++ }), "through the reader")
        assertEquals(2, reads, "the 2 MiB window in two reads, the most the phone hands back at once")
    }

    @Test
    fun `on the phone the whole window is searched, in pieces the emulator side hands back`() {
        // rc32 audit P3 #114: one 2 MiB read always came back empty on the phone, so only the first 1 MiB was searched,
        // and a build that moved a function past it was refused. BlendPalette here sits across the 1 MiB join.
        val f = Fake.fireRedLike(size = OverworldScan.WINDOW).apply {
            for (i in 0 until OverworldScan.BLEND_PALETTE.size) rom[Fake.BLEND + i] = 0
            val at = OverworldScan.READ_MAX - 0x40
            place(OverworldScan.BLEND_PALETTE, at); u32(at + 0x98, 0x020371F8); u32(at + 0x9C, 0x020375F8)
        }
        assertEquals("found", f.scan().why)
        val found = assertNotNull(OverworldScan.find(phoneReader(f.rom)), "through the phone's reader")
        assertEquals(0x020371F8L, found.plttUnfaded)
        // A ROM that ends inside the window is searched as far as its last whole piece.
        assertNotNull(OverworldScan.find(phoneReader(Fake.fireRedLike(size = 0x180000).rom)))
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

    // ------------------------------------------------------------------ MaxDex 1.0

    /**
     * MaxDex 1.0 (Trip's FireRed 1.1 build on Nat. Dex 1.1.3) is in no table either and publishes no address table at all,
     * so its overworld is read out of its own code as Nat. Dex's is (Blake, 2026-10-03, wanted a MaxDex run played as a
     * Gen 9 Pokemon). Checked on Blake's own dump with Trip's patch applied (firered-maxdex.gba, skipped when it is not in
     * IRONMON_ROMS). The numbers are the scan's, a record of what was found; what makes them believable is that every
     * block sits where MaxDex's tracker extension, a source that never saw this code, puts the RAM around it. Its map
     * header, special var and trainer opponent (GameMap.MAXDEX_FR_10, copied from the extension) are 0x9E0 below
     * FireRed's, and so is each overworld block the scan read; its battle function and save block pointer are 0x3D0 below
     * FireRed's, and so is gMain.
     */
    @Test
    fun `MaxDex 1_0's overworld is read out of its own code, and sits where its tracker extension's RAM says`() {
        val bytes = rom("firered-maxdex.gba") ?: return
        val out = OverworldScan.scan(bytes)
        val a = assertNotNull(out.addresses, "firered-maxdex.gba: ${out.why}")
        assertTrue(out.gMainConfirmed, "SetMainCallback2 confirms gMain")
        assertEquals(
            OverworldAddresses(
                name = OverworldScan.NAME, main = 0x03002D20, oamBufferOffset = 0x38,
                cb2Overworld = 0x0805CDE9, cb2OverworldBasic = 0x0805CDDD,
                playerAvatar = 0x02036698, sprites = 0x0202063C, coordOffsetX = 0x02021BC8, coordOffsetY = 0x02021BCA,
                plttUnfaded = 0x02036818, plttFaded = 0x02036C18, objectEvents = 0x02036458,
            ), a)
        // The game's own layout: 16 object events of 0x24 bytes end where gPlayerAvatar starts.
        assertEquals(0x240L, a.playerAvatar - a.objectEvents)

        // The extension's RAM against FireRed's, and the scan's against FireRed's: the same two moves.
        val fr = GameMap.FIRERED_U_V11
        val md = GameMap.MAXDEX_FR_10
        val retail = Overworld.FIRERED_U_V11
        val ewram = md.mapHeader - fr.mapHeader
        assertEquals(-0x9E0L, ewram)
        assertEquals(ewram, md.specialVarResult - fr.specialVarResult)
        assertEquals(ewram, md.trainerOpponent - fr.trainerOpponent)
        for ((what, found, theirs) in listOf(
            Triple("gObjectEvents", a.objectEvents, retail.objectEvents), Triple("gPlayerAvatar", a.playerAvatar, retail.playerAvatar),
            Triple("gPlttBufferUnfaded", a.plttUnfaded, retail.plttUnfaded), Triple("gPlttBufferFaded", a.plttFaded, retail.plttFaded),
        )) assertEquals(ewram, found - theirs, "$what moved with the extension's EWRAM")
        val iwram = md.battleMainFunc - fr.battleMainFunc
        assertEquals(-0x3D0L, iwram)
        assertEquals(iwram, md.saveBlock1Ptr - fr.saveBlock1Ptr)
        assertEquals(iwram, a.main - retail.main, "gMain moved with the extension's IWRAM")
        // What sits before the part of RAM that moved is where FireRed has it, as on Nat. Dex.
        assertEquals(retail.sprites, a.sprites)
        assertEquals(retail.coordOffsetX, a.coordOffsetX)
        assertEquals(retail.coordOffsetY, a.coordOffsetY)

        // As the phone gets there: the header names MaxDex, and resolve reads the window through the emulator's reader.
        var reads = 0
        val reader = phoneReader(bytes) { reads++ }
        val map = GameMap.resolve(reader)
        assertEquals(GameMap.MAXDEX_FR_10, map)
        reads = 0
        assertEquals(a.copy(name = "MaxDex 1.0 (read from the game)"), Overworld.resolve(map, reader))
        assertEquals(2, reads, "the 2 MiB window in two reads")
        println("OverworldScanTest: the scan read MaxDex 1.0's overworld")
    }

    /**
     * Without the dump: the MaxDex map is read out of the game's code as Nat. Dex is, under its own name. Its first version
     * returned nothing here, so the player stayed the trainer on every MaxDex game.
     */
    @Test
    fun `resolve reads a MaxDex game out of its own code, and says so in MaxDex's words when it cannot`() {
        val found = assertNotNull(Overworld.resolve(GameMap.MAXDEX_FR_10, RomReader(Fake.fireRedLike(size = OverworldScan.WINDOW).rom)))
        assertEquals("MaxDex 1.0 (read from the game)", found.name)
        assertEquals(0x030030F0L, found.main)
        assertEquals(0x08000000L + Fake.CB2 + 1, found.cb2Overworld)
        // One whose code cannot be read is refused, and the line names MaxDex, not Nat. Dex.
        assertNull(Overworld.resolve(GameMap.MAXDEX_FR_10, RomReader(Fake(OverworldScan.WINDOW).rom)))
        assertEquals("Play as your Pokemon could not find its way around this MaxDex build.", Overworld.whyNot(GameMap.MAXDEX_FR_10))
        // Nat. Dex keeps its own name and line.
        val natdexMap = GameMap.FIRERED_U_V10.copy(name = "Nat. Dex", expandedSpeciesIds = true)
        assertEquals(OverworldScan.NAME, assertNotNull(Overworld.resolve(natdexMap, RomReader(Fake.fireRedLike(size = OverworldScan.WINDOW).rom))).name)
        assertTrue("Nat. Dex" in Overworld.whyNot(natdexMap))
    }

    /** [rom] as the running game's memory: the ROM at 0x08000000, nothing anywhere else. Each read counted, with its length. */
    private class RomReader(private val rom: ByteArray) : MemoryReader {
        val reads = ArrayList<Int>()
        override fun read(address: Long, length: Int): ByteArray {
            reads += length
            val off = address - 0x08000000L
            return if (off < 0 || off + length > rom.size) ByteArray(0) else rom.copyOfRange(off.toInt(), off.toInt() + length)
        }
    }

    @Test
    fun `resolve reads a retail game's two callbacks, and the whole window only for Nat Dex or a build that moved them`() {
        val natdexMap = GameMap.FIRERED_U_V10.copy(name = "Nat. Dex", expandedSpeciesIds = true)
        // A retail game whose code is where its table says: the table, after one small read of its two callbacks.
        val retail = Fake(0x60000).apply {
            place(OverworldScan.CB2_OVERWORLD_BASIC, (Overworld.FIRERED_U_V11.cb2OverworldBasic - 1 - 0x08000000L).toInt())
            place(OverworldScan.CB2_OVERWORLD, (Overworld.FIRERED_U_V11.cb2Overworld - 1 - 0x08000000L).toInt())
        }
        val r = RomReader(retail.rom)
        assertEquals(Overworld.FIRERED_U_V11, Overworld.resolve(GameMap.FIRERED_U_V11, r))
        assertEquals(1, r.reads.size, "one read")
        assertTrue(r.reads.single() < 0x100, "of the two callbacks, not the window")
        var reads = 0
        val nothing = MemoryReader { _, _ -> reads++; ByteArray(0) }
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
        refused(Fake.fireRedLike().apply { u32(Fake.UPDATE + 0x6C, 0x08000000) }, "gSprites is not in EWRAM")
        refused(Fake.fireRedLike().apply { u32(Fake.UPDATE + 0x6C, 0x0203F000) }, "gSprites is not in EWRAM")   // 64 sprites run past the end of EWRAM
        refused(Fake.fireRedLike().apply { u32(Fake.UPDATE + 0x78, 0x02021BC9) }, "the camera offsets")
        refused(Fake.fireRedLike().apply { u32(Fake.UPDATE + 0x7C, 0x0) }, "the camera offsets")
        refused(Fake.fireRedLike().apply { u32(Fake.FACING + 0x18, 0x0) }, "gObjectEvents is not in RAM")
        // gPlayerAvatar: all six places have to agree first, so change every one of them.
        refused(Fake.fireRedLike().apply {
            val bad = 0x0800000CL
            u32(Fake.FACING + 0x1C, bad); u32(Fake.AVATAR + 8, bad); for (o in listOf(0x14, 0x30, 0x40)) u32(Fake.CHECK + o, bad); u32(Fake.TRANSITION + 0x2C, bad)
        }, "gPlayerAvatar is not in EWRAM")
        refused(Fake.fireRedLike().apply { u32(Fake.BLEND + 0x98, 0x0203FC00); u32(Fake.BLEND + 0x9C, 0x02040000) }, "the palette buffers are not in EWRAM")
    }

    @Test
    fun `each block takes only the RAM the emulator side takes it in`() {
        // configPlausible (sprite_core.h:130-143) takes gPlayerAvatar, gSprites and both palette buffers in EWRAM only,
        // the camera offsets and gObjectEvents in either. The scan took IWRAM for every block, so a build with gSprites
        // in IWRAM passed it and was refused there (rc32 audit P3 #114).
        refused(Fake.fireRedLike().apply { u32(Fake.UPDATE + 0x6C, 0x03001000) }, "gSprites is not in EWRAM")
        refused(Fake.fireRedLike().apply {
            val iw = 0x03004000L
            u32(Fake.FACING + 0x1C, iw); u32(Fake.AVATAR + 8, iw); for (o in listOf(0x14, 0x30, 0x40)) u32(Fake.CHECK + o, iw); u32(Fake.TRANSITION + 0x2C, iw)
        }, "gPlayerAvatar is not in EWRAM")
        refused(Fake.fireRedLike().apply { u32(Fake.BLEND + 0x98, 0x03001000); u32(Fake.BLEND + 0x9C, 0x03001400) }, "the palette buffers are not in EWRAM")
        // Its exact top ends, as configPlausible's: one word further is refused.
        refused(Fake.fireRedLike().apply { u32(Fake.FACING + 0x18, 0x0203FDC0) }, "gObjectEvents is not in RAM")
        assertEquals("found", Fake.fireRedLike().apply { u32(Fake.FACING + 0x18, 0x0203FDBC) }.scan().why)
        // The camera offsets and gObjectEvents may sit in IWRAM, as Ruby's do.
        assertEquals("found", Fake.fireRedLike().apply { u32(Fake.FACING + 0x18, 0x030048A0) }.scan().why)
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
        // Loose, for a busy machine (rc32 audit P3 #83): a scan that went quadratic would still be far past it.
        assertTrue(ms < 8000, "one scan took $ms ms")
    }

    // ------------------------------------------------------------------ a hack that kept the header (rc32 audit P2 #87)

    /**
     * A decomp-built hack keeps its base game's header (BPEE, BPRE), so GameMap names it as the retail game, and its
     * code moves. Given the retail table anyway, the native side never saw its overworld callback and drew nothing, with
     * no word anywhere. Its own code is read now, and a build that cannot be read is refused, so the player is told.
     */
    @Test
    fun `a build that kept a retail header but moved its code is read out of its own code`() {
        // FireRed's eight functions, at places of their own: nowhere near the retail table's callbacks.
        val hack = Fake.fireRedLike(size = OverworldScan.WINDOW)
        assertFalse(OverworldScan.callbacksAt(RomReader(hack.rom), Overworld.FIRERED_U_V11), "the table's callback is not this build's")
        val found = assertNotNull(Overworld.resolve(GameMap.FIRERED_U_V11, RomReader(hack.rom)))
        assertEquals(0x08000000L + Fake.CB2 + 1, found.cb2Overworld, "the callback the native side checks is the one in this build")
        assertEquals(0x08000000L + Fake.CB2 - 0xC + 1, found.cb2OverworldBasic)
        assertEquals(0x030030F0L, found.main)
        assertTrue("FireRed" in found.name, found.name)
        // The same build with the retail callbacks' code where the table has them is the table, as a retail dump is.
        hack.place(OverworldScan.CB2_OVERWORLD_BASIC, (Overworld.FIRERED_U_V11.cb2OverworldBasic - 1 - 0x08000000L).toInt())
        hack.place(OverworldScan.CB2_OVERWORLD, (Overworld.FIRERED_U_V11.cb2Overworld - 1 - 0x08000000L).toInt())
        assertEquals(Overworld.FIRERED_U_V11, Overworld.resolve(GameMap.FIRERED_U_V11, RomReader(hack.rom)))
    }

    @Test
    fun `a build that moved its code and cannot be read is refused, and says so in its own words`() {
        val moved = Fake(OverworldScan.WINDOW).apply { place(OverworldScan.LOAD_OAM, 0x1000) }   // one function, not eight
        assertNull(Overworld.resolve(GameMap.EMERALD_U, RomReader(moved.rom)))
        assertTrue(Overworld.hasTable(GameMap.EMERALD_U) && !Overworld.hasTable(GameMap.EMERALD_U.copy(name = "Some hack")))
        assertEquals("Play as your Pokemon could not find its way around this game, so you stay the trainer.", Overworld.whyNot(GameMap.EMERALD_U))
        assertEquals("This game is not one Play as your Pokemon knows.", Overworld.whyNot(GameMap.EMERALD_U.copy(name = "Some hack")))
        // Only the callbacks' code counts: one byte off and the table is not taken.
        val near = Fake(0x90000).apply {
            place(OverworldScan.CB2_OVERWORLD_BASIC, (Overworld.EMERALD_U.cb2OverworldBasic - 1 - 0x08000000L).toInt())
            place(OverworldScan.CB2_OVERWORLD, (Overworld.EMERALD_U.cb2Overworld - 1 - 0x08000000L).toInt())
        }
        assertTrue(OverworldScan.callbacksAt(RomReader(near.rom), Overworld.EMERALD_U))
        near.rom[(Overworld.EMERALD_U.cb2Overworld - 1 - 0x08000000L).toInt()] = 0x11
        assertFalse(OverworldScan.callbacksAt(RomReader(near.rom), Overworld.EMERALD_U))
    }

    @Test
    fun `every retail dump's callbacks are where its table says`() {
        var checked = 0
        for ((file, table) in listOf(
            "firered-u-v10.gba" to Overworld.FIRERED_U_V10, "firered-u-v11.gba" to Overworld.FIRERED_U_V11,
            "leafgreen-u.gba" to Overworld.LEAFGREEN_U, "emerald-u.gba" to Overworld.EMERALD_U,
            "ruby-u.gba" to Overworld.RUBY_U, "sapphire-u.gba" to Overworld.SAPPHIRE_U,
        )) {
            val bytes = rom(file) ?: continue
            val r = RomReader(bytes)
            assertTrue(OverworldScan.callbacksAt(r, table), "$file: the table's callbacks are this dump's")
            val map = GameMap.resolve(r)
            assertEquals(table, Overworld.resolve(map, r), "$file: resolve keeps the table")
            checked++
        }
        if (dir != null) assertEquals(6, checked, "a retail dump is missing from IRONMON_ROMS")
    }
}
