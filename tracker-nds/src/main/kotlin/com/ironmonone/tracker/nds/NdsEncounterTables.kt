package com.ironmonone.tracker.nds

/**
 * LOCATION_DATA[game].encounters, the DS tracker's vanilla encounter tables
 * (nds/encounters-<table>.tsv, made by tools/trainer-data/convert_nds_encounters.py
 * by running LocationData.lua). Keyed by the area name the tracker reads, so an
 * area is found exactly when the reference would find it. Only the early areas
 * have one (5 Platinum, 12 HeartGold/SoulSilver, 7 Black/White, 4 Black 2/White 2).
 */
object NdsEncounterTables {
    /** One vanilla entry of a slot: a level or a level range, and its percent. */
    data class Entry(val level: Int?, val minLevel: Int?, val maxLevel: Int?, val percent: String) {
        /** HoverFrameFactory formatEncounterEntry: "Level 5 (30%)" or "Level 3 - 5 (30%)". */
        fun label(): String =
            (if (level != null) "Level $level" else "Level $minLevel - $maxLevel") + " ($percent%)"
    }

    /** encounters[areaName]: totalPokemon and vanillaData, one list of entries per slot. */
    data class Area(val name: String, val totalPokemon: Int, val slots: List<List<Entry>>) {
        /** vanillaData[1][1].levelRange ~= nil: seen levels then read as a range. */
        val usesRange: Boolean get() = slots.firstOrNull()?.firstOrNull()?.level == null
    }

    /** GameInfo LOCATION_DATA: Diamond, Pearl and Platinum share Platinum's table. */
    fun tableKey(badgeSet: String): String? = when (badgeSet) {
        "DPPT" -> "pt"; "HGSS" -> "hgss"; "BW" -> "bw"; "BW2" -> "b2w2"; else -> null
    }

    private val cache = HashMap<String, Map<String, Area>>()

    fun area(badgeSet: String, areaName: String): Area? {
        if (areaName.isBlank()) return null
        val key = tableKey(badgeSet) ?: return null
        return table(key)[areaName]
    }

    @Synchronized
    fun table(key: String): Map<String, Area> = cache.getOrPut(key) {
        val out = HashMap<String, Area>()
        javaClass.getResourceAsStream("/nds/encounters-$key.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("#")) return@forEach
                val p = line.split('	')
                if (p.size < 3) return@forEach
                val slots = p[2].split(';').map { slot ->
                    slot.split(',').mapNotNull { e ->
                        val lv = e.substringBefore(':'); val pct = e.substringAfter(':', "")
                        if ('-' in lv) Entry(null, lv.substringBefore('-').toIntOrNull(), lv.substringAfter('-').toIntOrNull(), pct)
                        else lv.toIntOrNull()?.let { Entry(it, null, null, pct) }
                    }
                }
                out[p[0]] = Area(p[0], p[1].toIntOrNull() ?: slots.size, slots)
            }
        }
        out
    }
}
