package com.ironmonone.tracker

/**
 * Finds "Play as your Pokemon"'s addresses in a game's own code, for a build that is in no table: Nat. Dex, and MaxDex
 * 1.0, which is Nat. Dex 1.1.3 grown and is read the same way.
 *
 * Nat. Dex publishes an address table in its ROM (0x08000150 on) but not the eleven this needs, and its RAM moves
 * between versions, which is why nothing about it may be hardcoded (GbaTracker). It is compiled from the same
 * source as the retail games, though, and eight small functions of the overworld come out byte for byte the same
 * apart from the words in their literal pools and the operands of their calls: LoadOam, UpdateOamCoords,
 * GetPlayerFacingDirection, GetPlayerAvatarObjectId, CheckMovementInputNotOnBike,
 * UpdatePlayerAvatarTransitionState, CB2_Overworld and BlendPalette. Finding each by that code, exactly once, and reading
 * its pool gives gMain, the oamBuffer offset, gSprites, both camera offsets, gObjectEvents, gPlayerAvatar, the two
 * palette buffers and both callbacks from the game itself: the addresses the game's own code uses. gMain, the one
 * address whose writes matter most, is read a second time from SetMainCallback2, which has nothing to do with the
 * overworld, and the two have to agree.
 *
 * It is proven three ways (OverworldScanTest): on the six retail dumps it returns exactly the tables in [Overworld],
 * which were read from the pret symbol files and are checked against those same dumps separately
 * (OverworldAddressTest); on both Nat. Dex dumps and the MaxDex 1.0 build it finds every function once and every
 * function that names gPlayerAvatar agrees on it (on MaxDex, also with the RAM its tracker extension hardcodes); and
 * each rule below refuses, on a made-up ROM, with the reason named.
 *
 * It is a refusal when anything is off, never a guess: a function missing or in two places, functions that disagree
 * about an address, a pool word that is not in RAM. A wrong table is not harmless (it would hide the wrong sprite), and
 * the emulator side checks gMain.callback2 against the callback found here, so a table that is not the running game's
 * writes nothing, but the scan does not lean on that.
 */
object OverworldScan {
    /**
     * How much of the ROM is searched. The functions are in the first 0x97000 bytes of every dump seen (the
     * highest, Emerald Nat. Dex's GetPlayerAvatarObjectId, is at 0x96D84); 2 MiB leaves room for a build that moves them.
     */
    const val WINDOW = 0x200000
    /**
     * The most the emulator side hands back for one read: libretrodroidjni.cpp's readMemory returns nothing for a
     * length over 0x100000. The window was asked for in one read, which on the phone always came back empty, so only
     * the first 1 MiB was ever searched (rc32 audit P3 #114); it is read in pieces this size.
     */
    const val READ_MAX = 0x100000
    const val ROM_BASE = 0x08000000L
    const val NAME = "Nat. Dex (read from the game)"

    /**
     * What a scan found, or why it did not: the reason names the rule, so a test can tell the rule it means.
     * [gMainConfirmed] is true when a second, unrelated function (SetMainCallback2) was found and named the same gMain.
     */
    class Outcome(val addresses: OverworldAddresses?, val why: String, val gMainConfirmed: Boolean = false)

    /** One function's code, with ".." for a byte that is a pool word or a call operand and so differs. */
    internal class Pattern(val name: String, hex: String) {
        val bytes: IntArray
        private val fixed: IntArray

        init {
            val clean = hex.replace(" ", "")
            require(clean.length % 2 == 0) { "$name: odd number of hex digits" }
            bytes = IntArray(clean.length / 2) { i ->
                val p = clean.substring(i * 2, i * 2 + 2)
                if (p == "..") -1 else p.toInt(16)
            }
            fixed = bytes.indices.filter { bytes[it] >= 0 }.toIntArray()
        }

        val size: Int get() = bytes.size

        fun matchesAt(rom: ByteArray, at: Int): Boolean {
            if (at < 0 || at + bytes.size > rom.size) return false
            for (j in fixed) if ((rom[at + j].toInt() and 255) != bytes[j]) return false
            return true
        }
    }

    internal val LOAD_OAM = Pattern("LoadOam",
        "00b5084a08495018017801200840002806d1101c..30e021c904044a........01bc0047................00010004")
    internal val UPDATE_OAM_COORDS = Pattern("UpdateOamCoords",
        "f0b50024194f1a48051c1a4e200100198000c319181c3e30017805200840012847d10220084000282ad02022995e2422" +
            "985e0918181c283000780006001609180d480022805e091829405a88301c104008435880d98c588c0918181c29300078" +
            "064a40181278801822e00000........ff01000000feffff................2022995e2422985e0918181c28300078" +
            "00060016091829405a88301c104008435880d98c588c0918181c2930007840181870601c0006040e3f2ca7d9f0bc01bc" +
            "0047")
    internal val PLAYER_FACING = Pattern("GetPlayerFacingDirection", "054a06484179c800401880008018007e0007000f70470000................")
    internal val AVATAR_SPRITE_ID = Pattern("GetPlayerAvatarObjectId", "0148007970470000........")
    internal val CHECK_MOVEMENT_INPUT = Pattern("CheckMovementInputNotOnBike",
        "10b50006040e002c06d102488470002013e00000........00f0....0006000e844207d002498878022803d0012003e0" +
            "........02490220887010bc02bc0847........")
    internal val TRANSITION_STATE = Pattern("UpdatePlayerAvatarTransitionState",
        "10b50a4c0020e070........0006002815d0........0006002809d1........000600280bd1012008e00000" +
            "................0006002801d10220e07010bc01bc0047")
    internal val CB2_OVERWORLD = Pattern("CB2_Overworld",
        "10b50948c079c009041c002c02d00020..f7....fff7..ff002c01d000f0..fa10bc01bc00470000........")
    /** CB2_OverworldBasic, which sits 0xC bytes before CB2_Overworld. */
    internal val CB2_OVERWORLD_BASIC = Pattern("CB2_OverworldBasic", "00b5 fff7 ..ff 01bc 0047")
    internal val BLEND_PALETTE = Pattern("BlendPalette",
        "f0b557464e464546e0b481b00004000c80460904090c8c461206170e68460380002666452fd21c4882461c4989464046" +
            "35182d04ed0b514668180368dc069a055b044d44e40e0099c806c00e001b784300112418d20e8805c00e801a78430011" +
            "121852011443db0e4904c90ec91a081c784300111b189b021c432c80701c0004060c6645d3d301b038bc9846a146aa46" +
            "f0bc01bc00470000................")

    /**
     * SetMainCallback2, which has nothing to do with the overworld: gMain.callback2 = callback (str r0, [r1, #4], which is where
     * the emulator side reads it) and gMain.state = 0. FireRed, LeafGreen and Emerald compile it one way, Ruby and Sapphire
     * another. Used only to confirm gMain a second time, so a build that has neither is read without it.
     */
    internal val SET_MAIN_CALLBACK2 = listOf(
        Pattern("SetMainCallback2", "034948608720c0000918002008707047........"),
        Pattern("SetMainCallback2", "03494860034809180020087070470000........3c040000"),
    )

    private val FUNCTIONS = listOf(
        LOAD_OAM, UPDATE_OAM_COORDS, PLAYER_FACING, AVATAR_SPRITE_ID, CHECK_MOVEMENT_INPUT, TRANSITION_STATE, CB2_OVERWORLD, BLEND_PALETTE,
    )

    /** Every place in the first [limit] bytes that [p] matches, halfword aligned (Thumb code is), stopping at two. */
    private fun places(rom: ByteArray, limit: Int, p: Pattern): List<Int> {
        val out = ArrayList<Int>(2)
        val last = minOf(limit, rom.size) - p.size
        var i = 0
        while (i <= last && out.size < 2) {
            if (p.matchesAt(rom, i)) out += i
            i += 2
        }
        return out
    }

    /**
     * Whether the running game has CB2_OverworldBasic's and CB2_Overworld's code at [table]'s two callbacks: that a game
     * named by its header has that game's overworld where the table says (rc32 audit P2 #87). One read through [read],
     * of the few bytes from the one callback to the end of the other. False for anything it cannot read.
     */
    fun callbacksAt(read: MemoryReader, table: OverworldAddresses): Boolean {
        val basic = table.cb2OverworldBasic - 1
        val gap = (table.cb2Overworld - 1 - basic).toInt()
        if (gap < 0 || gap > 0x100) return false
        val len = gap + CB2_OVERWORLD.size
        val bytes = runCatching { read.read(basic, len) }.getOrNull() ?: return false
        return bytes.size == len && CB2_OVERWORLD_BASIC.matchesAt(bytes, 0) && CB2_OVERWORLD.matchesAt(bytes, gap)
    }

    /** The addresses in the ROM whose first bytes are [rom], or null (see [scan] for why not). */
    fun find(rom: ByteArray, name: String = NAME): OverworldAddresses? = scan(rom, name).addresses

    /**
     * The addresses in the running game's ROM, read through [reader]: the first [WINDOW] bytes, [READ_MAX] at a time and
     * joined before the search, so a function across the join is found as well. A ROM that ends inside the window (a read
     * past it comes back short) is searched as far as its last whole piece, as the 1 MiB read before this was. Null when
     * the ROM cannot be read or is not one this understands.
     */
    fun find(reader: MemoryReader, name: String = NAME): OverworldAddresses? {
        val out = ByteArray(WINDOW)
        var got = 0
        while (got < WINDOW) {
            val n = minOf(READ_MAX, WINDOW - got)
            val part = runCatching { reader.read(ROM_BASE + got, n) }.getOrNull()
            if (part == null || part.size != n) break
            part.copyInto(out, got)
            got += n
        }
        if (got == 0) return null
        return find(if (got == WINDOW) out else out.copyOf(got), name)
    }

    fun scan(rom: ByteArray, name: String = NAME): Outcome {
        fun refuse(why: String) = Outcome(null, why)
        val limit = minOf(rom.size, WINDOW)

        val at = LinkedHashMap<String, Int>()
        for (p in FUNCTIONS) {
            val found = places(rom, limit, p)
            when {
                found.isEmpty() -> return refuse("${p.name} is not in the first $limit bytes of the ROM")
                found.size > 1 -> return refuse("${p.name} is in more than one place")
                else -> at[p.name] = found.single()
            }
        }
        fun u32(o: Int): Long =
            if (o < 0 || o + 4 > rom.size) -1L
            else (rom[o].toLong() and 255) or ((rom[o + 1].toLong() and 255) shl 8) or
                ((rom[o + 2].toLong() and 255) shl 16) or ((rom[o + 3].toLong() and 255) shl 24)

        // LoadOam: adds r0, #oamBuffer offset, then gMain and the two offsets its copy uses.
        val oam = at.getValue(LOAD_OAM.name)
        val oamOffset = rom[oam + 0x14].toInt() and 255
        if (oamOffset != 0x38 && oamOffset != 0x3C) return refuse("LoadOam: gMain.oamBuffer at $oamOffset is neither of the two layouts (0x38, 0x3C)")
        val main = u32(oam + 0x24)
        if (u32(oam + 0x28) != (oamOffset + 0x401).toLong()) return refuse("LoadOam: the flag after the buffer is not where the buffer says")
        if (u32(oam + 0x2C) != 0x04000100L) return refuse("LoadOam: it does not copy 0x100 words to OAM")

        // gMain a second time, from a function that has nothing to do with the overworld.
        val setMain = SET_MAIN_CALLBACK2.flatMap { places(rom, limit, it) }
        if (setMain.size > 1) return refuse("SetMainCallback2 is in more than one place")
        val gMainConfirmed = setMain.size == 1
        if (gMainConfirmed && u32(setMain.single() + 0x10) != main) return refuse("gMain: SetMainCallback2 does not agree with LoadOam")

        // UpdateOamCoords: gSprites and the two camera offsets, and the masks that mean x is nine bits.
        val upd = at.getValue(UPDATE_OAM_COORDS.name)
        val sprites = u32(upd + 0x6C)
        val coordX = u32(upd + 0x78)
        val coordY = u32(upd + 0x7C)
        if (u32(upd + 0x70) != 0x1FFL || u32(upd + 0x74) != 0xFFFFFE00L) return refuse("UpdateOamCoords: its masks are not the OAM's")

        // gObjectEvents and gPlayerAvatar from GetPlayerFacingDirection; four other functions name gPlayerAvatar too.
        val facing = at.getValue(PLAYER_FACING.name)
        val objectEvents = u32(facing + 0x18)
        val avatar = u32(facing + 0x1C)
        val avatarSeen = listOf(
            "GetPlayerAvatarObjectId" to u32(at.getValue(AVATAR_SPRITE_ID.name) + 8),
            "CheckMovementInputNotOnBike" to u32(at.getValue(CHECK_MOVEMENT_INPUT.name) + 0x14),
            "CheckMovementInputNotOnBike" to u32(at.getValue(CHECK_MOVEMENT_INPUT.name) + 0x30),
            "CheckMovementInputNotOnBike" to u32(at.getValue(CHECK_MOVEMENT_INPUT.name) + 0x40),
            "UpdatePlayerAvatarTransitionState" to u32(at.getValue(TRANSITION_STATE.name) + 0x2C),
        )
        avatarSeen.firstOrNull { it.second != avatar }?.let { return refuse("gPlayerAvatar: ${it.first} does not agree with GetPlayerFacingDirection") }

        // The callbacks: CB2_Overworld's own place, and CB2_OverworldBasic 0xC before it, checked by its code.
        val cb2At = at.getValue(CB2_OVERWORLD.name)
        val basicAt = cb2At - 0xC
        if (!CB2_OVERWORLD_BASIC.matchesAt(rom, basicAt)) return refuse("CB2_OverworldBasic is not where CB2_Overworld says it is")
        val cb2 = ROM_BASE + cb2At + 1
        val basic = ROM_BASE + basicAt + 1

        // The palette buffers: BlendPalette's pool, faded straight after unfaded (0x400 bytes each).
        val blend = at.getValue(BLEND_PALETTE.name)
        val unfaded = u32(blend + 0x98)
        val faded = u32(blend + 0x9C)
        if (faded - unfaded != 0x400L) return refuse("BlendPalette: the two palette buffers are not 0x400 bytes apart")

        // Where things can be, each block on its own: exactly the emulator side's limits (configPlausible in sprite_core.h),
        // so what is accepted here is accepted there. gMain in IWRAM; gPlayerAvatar, gSprites and both palette buffers in
        // EWRAM only; the camera offsets and gObjectEvents in either. Every block took IWRAM or EWRAM here, so a build with
        // gSprites in IWRAM passed the scan and was refused there (rc32 audit P3 #114).
        fun ewram(a: Long, hi: Long) = a in 0x02000000L..hi
        fun iwram(a: Long, hi: Long) = a in 0x03000000L..hi
        if (main !in 0x03000000L..0x03007000L || main % 4 != 0L) return refuse("gMain is not in IWRAM")
        if (!ewram(sprites, 0x0203FFFFL - 64L * 0x44) || sprites % 4 != 0L) return refuse("gSprites is not in EWRAM")
        if (!(ewram(objectEvents, 0x0203FDBFL) || iwram(objectEvents, 0x03007DBFL)) || objectEvents % 4 != 0L) return refuse("gObjectEvents is not in RAM")
        if (!ewram(avatar, 0x0203FFE0L) || avatar % 4 != 0L) return refuse("gPlayerAvatar is not in EWRAM")
        fun camera(a: Long) = ewram(a, 0x0203FFFCL) || iwram(a, 0x03007FFCL)
        if (!camera(coordX) || !camera(coordY) || coordX % 2 != 0L || coordY % 2 != 0L) return refuse("the camera offsets are not in RAM")
        if (!ewram(unfaded, 0x0203FBFFL) || !ewram(faded, 0x0203FBFFL) || unfaded % 4 != 0L) return refuse("the palette buffers are not in EWRAM")

        return Outcome(
            OverworldAddresses(
                name = name, main = main, oamBufferOffset = oamOffset, cb2Overworld = cb2, cb2OverworldBasic = basic,
                playerAvatar = avatar, sprites = sprites, coordOffsetX = coordX, coordOffsetY = coordY,
                plttUnfaded = unfaded, plttFaded = faded, objectEvents = objectEvents,
            ),
            "found", gMainConfirmed,
        )
    }
}
