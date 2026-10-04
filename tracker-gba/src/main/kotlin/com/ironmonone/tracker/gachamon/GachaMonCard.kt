package com.ironmonone.tracker.gachamon

/**
 * One GachaMon card: GachaMonData.IGachaMon (data/GachaMonData.lua:1270-1590), its fields uncompressed. What goes to
 * and from the reference's 31-byte record is [GachaMonCodec]'s.
 */
data class GachaMonCard(
    /** The rating rules it was made under (GachaMonFileManager.Version): its stars follow that version's thresholds. */
    val version: Int,
    /** The Pokemon's personality value; for a prize card, the trainer's id (createPokemonDataFromDefeatedTrainers). */
    val personality: Long,
    /** The game's own species id (Gen 3's internal numbering, a Nat. Dex build's past 411). */
    val pokemonId: Int,
    val level: Int,
    val abilityId: Int,
    val ratingScore: Int,
    val battlePower: Int,
    val favorite: Int = 0,
    val gameWinner: Int = 0,
    /** The attempt (the PC tracker's seed count) it was collected on. */
    val seedNumber: Int,
    /** Bit n set: it helped win badge n + 1. */
    val badges: Int = 0,
    val type1: Int,
    val type2: Int,
    val stats: SixStats,
    /** Up to four move ids in slot order, 0 for none. */
    val moveIds: List<Int>,
    /** 1 Ruby, 2 Emerald, 3 FireRed, 4 Sapphire, 5 LeafGreen (gameVersionToNumber); 0 unknown. */
    val gameVersion: Int,
    /** 1: keep in the collection (a capture marked to keep, or a card already there). */
    val keep: Int = 0,
    val isShiny: Int = 0,
    /** 1 male, 2 female, 0 neither. */
    val gender: Int = 0,
    val nature: Int,
    val year: Int,
    val month: Int,
    val day: Int,
) {
    /** pidIndex (getAssociatedRecentMon): personality plus species id, how the captures of a run are told apart. */
    val pidIndex: Long get() = personality + pokemonId

    /** calculateStars with the reference's tables for this card's version. */
    fun stars(rs: GachaMonRatingSystem = GachaMonRatingSystem.default): Int = rs.stars(ratingScore, version)

    /** The game's name (numberToGameVersion), "?" when unknown. */
    val gameName: String get() = GAME_NAMES.getOrNull(gameVersion - 1) ?: "?"

    companion object {
        val GAME_NAMES = listOf("Ruby", "Emerald", "FireRed", "Sapphire", "LeafGreen")

        /** gameVersionToNumber, from the tracker's routeVersion ("firered", "emerald"): 0 when unknown. */
        fun gameVersionOf(routeVersion: String): Int = when (routeVersion.lowercase()) {
            "ruby" -> 1
            "emerald" -> 2
            "firered" -> 3
            "sapphire" -> 4
            "leafgreen" -> 5
            else -> 0
        }
    }
}

/**
 * GachaMonFileManager's binary streams, versions 1 and 2 (format "BIHBBBHBBBIIII", 31 bytes, little-endian), and the
 * share code: that record in base 64 (GachaMonData.getShareablyCode), which goes between KaizoCore and the PC tracker.
 */
object GachaMonCodec {
    /** GachaMonFileManager.Version: the version new cards are made under. */
    const val CURRENT_VERSION = 2
    /** Bytes in one record, the version byte included. */
    const val SIZE = 31

    /** The field limits of the record: anything past them cannot go into a share code. */
    const val MAX_SPECIES = 0x7FF
    const val MAX_MOVE = 0x1FF
    const val MAX_ABILITY = 0xFF

    /** BinaryStreams[1].Writer (version 2 writes the same bytes). Null for a version the reference has no writer for. */
    fun toBytes(c: GachaMonCard): ByteArray? {
        if (c.version != 1 && c.version != 2) return null
        val out = ByteArray(SIZE)
        var at = 0
        fun put(v: Long, n: Int) { var x = v; repeat(n) { out[at++] = (x and 0xFF).toByte(); x = x ushr 8 } }
        val battlePower = c.battlePower / 1000
        // Compress into a 2-byte value: 11 bits of species, 4 of Battle Power, 1 of favorite
        val idPowerFavorite = (c.pokemonId and MAX_SPECIES).toLong() + ((battlePower and 0xF).toLong() shl 11) + ((c.favorite and 1).toLong() shl 15)
        val levelAndWinner = (c.level and 0x7F).toLong() + ((c.gameWinner and 1).toLong() shl 7)
        val stats1 = (c.stats.hp and 0x3FF).toLong() + ((c.stats.atk and 0x3FF).toLong() shl 10) + ((c.stats.def and 0x3FF).toLong() shl 20)
        val stats2 = (c.stats.spa and 0x3FF).toLong() + ((c.stats.spd and 0x3FF).toLong() shl 10) + ((c.stats.spe and 0x3FF).toLong() shl 20)
        val c1 = moveIdsGameVersionKeep(c)
        val c2 = ((c.isShiny and 1) + ((c.gender and 3) shl 1) + ((c.nature and 0x1F) shl 3)).toLong()
        val c3 = dateBits(c.year, c.month, c.day).toLong()
        val movepair1 = c1 and 0xFFFFFFFFL
        val movepair2 = ((c1 ushr 32) and 0xFF) + (c2 shl 8) + (c3 shl 16)
        put(c.version.toLong(), 1)
        put(c.personality, 4)
        put(idPowerFavorite, 2)
        put(levelAndWinner, 1)
        // Past a byte the PC tracker would keep the low byte, another ability: an ability it cannot hold goes as none.
        put((if (c.abilityId in 0..MAX_ABILITY) c.abilityId else 0).toLong(), 1)
        put(c.ratingScore.toLong(), 1)
        put(c.seedNumber.toLong(), 2)
        put(c.badges.toLong(), 1)
        put(c.type1.toLong(), 1)
        put(c.type2.toLong(), 1)
        put(stats1, 4)
        put(stats2, 4)
        put(movepair1, 4)
        put(movepair2, 4)
        return out
    }

    /**
     * KVVVMMMM MMMMMMMM ...: four 9-bit move ids, the 3-bit game version and the keep bit, 40 bits. A Nat. Dex move past
     * 511 does not fit nine bits (the PC tracker's sum would spill it into the next move): it goes as no move.
     */
    fun moveIdsGameVersionKeep(c: GachaMonCard): Long {
        fun move(i: Int): Long = c.moveIds.getOrElse(i) { 0 }.let { if (it in 0..MAX_MOVE) it else 0 }.toLong()
        return move(0) +
            (move(1) shl 9) +
            (move(2) shl 18) +
            (move(3) shl 27) +
            ((c.gameVersion and 7).toLong() shl 36) +
            ((c.keep and 1).toLong() shl 39)
    }

    /** YYYYYYYM MMMDDDDD: day, month, and the year after 2000 (compressDateObtained). */
    fun dateBits(year: Int, month: Int, day: Int): Int = (day and 0x1F) + ((month and 0xF) shl 5) + (((year - 2000).coerceAtLeast(0) and 0x7F) shl 9)

    /** BinaryStreams[1].Reader (and version 2's). Null for a short record or a version the reference cannot read. */
    fun fromBytes(b: ByteArray, offset: Int = 0): GachaMonCard? {
        if (offset < 0 || b.size - offset < SIZE) return null
        var at = offset
        fun get(n: Int): Long { var v = 0L; for (k in 0 until n) v = v or ((b[at + k].toLong() and 0xFF) shl (8 * k)); at += n; return v }
        val version = get(1).toInt()
        if (version != 1 && version != 2) return null
        val personality = get(4)
        val idPowerFavorite = get(2).toInt()
        val levelAndWinner = get(1).toInt()
        val ability = get(1).toInt()
        val rating = get(1).toInt()
        val seed = get(2).toInt()
        val badges = get(1).toInt()
        val type1 = get(1).toInt()
        val type2 = get(1).toInt()
        val stats1 = get(4)
        val stats2 = get(4)
        val movepair1 = get(4)
        val movepair2 = get(4)
        val c1 = movepair1 + ((movepair2 and 0xFF) shl 32)
        val c2 = ((movepair2 ushr 8) and 0xFF).toInt()
        val c3 = ((movepair2 ushr 16) and 0xFFFF).toInt()
        fun bits(v: Long, from: Int, width: Int): Int = ((v ushr from) and ((1L shl width) - 1)).toInt()
        return GachaMonCard(
            version = version,
            personality = personality,
            pokemonId = idPowerFavorite and MAX_SPECIES,
            level = levelAndWinner and 0x7F,
            abilityId = ability,
            ratingScore = rating,
            battlePower = ((idPowerFavorite ushr 11) and 0xF) * 1000,
            favorite = (idPowerFavorite ushr 15) and 1,
            gameWinner = (levelAndWinner ushr 7) and 1,
            seedNumber = seed,
            badges = badges,
            type1 = type1,
            type2 = type2,
            stats = SixStats(
                hp = bits(stats1, 0, 10), atk = bits(stats1, 10, 10), def = bits(stats1, 20, 10),
                spa = bits(stats2, 0, 10), spd = bits(stats2, 10, 10), spe = bits(stats2, 20, 10),
            ),
            moveIds = listOf(bits(c1, 0, 9), bits(c1, 9, 9), bits(c1, 18, 9), bits(c1, 27, 9)),
            gameVersion = bits(c1, 36, 3),
            keep = bits(c1, 39, 1),
            isShiny = c2 and 1,
            gender = (c2 ushr 1) and 3,
            nature = (c2 ushr 3) and 0x1F,
            year = 2000 + ((c3 ushr 9) and 0x7F),
            month = (c3 ushr 5) and 0xF,
            day = c3 and 0x1F,
        )
    }

    /** GachaMonData.getShareablyCode: the record in standard base 64 ("" for a card the reference could not write). */
    fun shareCode(c: GachaMonCard): String = toBytes(c)?.let { java.util.Base64.getEncoder().encodeToString(it) } ?: ""

    /**
     * transformCodeIntoGachaMon: a share code back into its card, or null. Spaces, line breaks and anything else that is
     * not base 64 are dropped first, as StructEncoder.decodeBase64 does, so a code pasted from a chat still reads.
     */
    fun fromShareCode(code: String): GachaMonCard? {
        val clean = code.filter { it.isLetterOrDigit() && it.code < 128 || it == '+' || it == '/' || it == '=' }
        if (clean.isEmpty()) return null
        val bytes = runCatching { java.util.Base64.getDecoder().decode(clean) }.getOrNull() ?: return null
        return fromBytes(bytes)
    }

    /** A run of records end to end, as the PC tracker's collection file (FullCollection.gccg) holds them. */
    fun readAll(b: ByteArray): List<GachaMonCard> {
        val out = ArrayList<GachaMonCard>()
        var at = 0
        while (at + SIZE <= b.size) {
            out += fromBytes(b, at) ?: break
            at += SIZE
        }
        return out
    }
}

/**
 * A whole PC tracker collection (FullCollection.gccg, GachaMonFileManager.saveCollectionToFile: one 31-byte record after
 * another) merged into a collection. The PC tracker's own reader stops at the first record it cannot read
 * (getCollectionFromFile); this one counts it, steps over it and goes on, since every record is the same size.
 */
object GachaMonImport {
    /** What an import found: the cards to add, how many were there already, and how many records could not be read. */
    data class Result(val added: List<GachaMonCard>, val already: Int, val unreadable: Int, val favorited: List<GachaMonCard> = emptyList()) {
        /** "N added, M already in your collection, K couldn't be read". */
        val line: String get() = "${added.size} added, $already already in your collection, $unreadable couldn't be read"
    }

    /**
     * One card whatever happened to it after it was made: the favorite, keep and game-winner marks are the only things a
     * card can change (GachaMon.update), so two records equal past those are the same card.
     */
    fun identity(c: GachaMonCard): GachaMonCard = c.copy(favorite = 0, keep = 0, gameWinner = 0)

    /**
     * [file]'s cards that [existing] does not hold, each kept and with its favorite mark as the file has it. A card the
     * file repeats is counted once. A card already held that the file marks a favorite comes back in [Result.favorited]
     * (the held copy, to mark): an import never takes a favorite away.
     */
    fun merge(existing: List<GachaMonCard>, file: ByteArray): Result {
        val held = existing.associateBy(::identity)
        val seen = HashSet<GachaMonCard>()
        val added = ArrayList<GachaMonCard>()
        val favorited = LinkedHashMap<GachaMonCard, GachaMonCard>()
        var already = 0
        var unreadable = 0
        var at = 0
        while (at < file.size) {
            val card = GachaMonCodec.fromBytes(file, at)
            at += GachaMonCodec.SIZE
            if (card == null) { unreadable++; continue }
            val id = identity(card)
            val mine = held[id]
            if (mine != null || !seen.add(id)) {
                already++
                if (card.favorite == 1) {
                    if (mine != null && mine.favorite != 1) favorited[id] = mine
                    else added.indexOfFirst { identity(it) == id }.takeIf { it >= 0 }?.let { added[it] = added[it].copy(favorite = 1) }
                }
                continue
            }
            added += card.copy(keep = 1)
        }
        return Result(added, already, unreadable, favorited.values.toList())
    }
}
