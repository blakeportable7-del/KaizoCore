package com.ironmonone.tracker.nds

/**
 * The DS tracker's heals box (MainScreen.setUpMiscInfo, MainScreen.lua:801-809):
 * the bag's healing and status items as Program.scanForHealingItems finds them
 * (Program.lua:334-377), the "Heals:" line (calculateHealPercent, lua:379-411),
 * "Status items:" (getStatusTotals, lua:751-759) and the lists a tap shows
 * (HoverFrameFactory readItemDataIntoFrame, lua:331-391). The items are
 * ItemData's HEALING_ITEMS and STATUS_ITEMS in their sort orders
 * (nds/bag-items.tsv, tools/trainer-data/convert_nds_bag_items.py).
 */
object NdsHeals {
    /** A healing item: a flat [amount] of HP, or [amount] percent of max HP when [percent]. */
    data class Heal(val id: Int, val name: String, val amount: Double, val percent: Boolean)

    /** A status item and what it cures (MiscData.STATUS_TYPE: All, Burn, Sleep...). */
    data class Cure(val id: Int, val name: String, val cures: String)

    private val rows: List<List<String>> by lazy {
        NdsHeals::class.java.getResourceAsStream("/nds/bag-items.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.toList()
        } ?: emptyList()
    }

    /** HEALING_ITEMS in HEALING_ID_SORT_ORDER. */
    val HEALS: List<Heal> by lazy {
        rows.filter { it.size >= 5 && it[0] == "heal" }.mapNotNull { r ->
            val id = r[1].toIntOrNull() ?: return@mapNotNull null
            Heal(id, r[2], r[3].toDoubleOrNull() ?: 0.0, r[4] == "PERCENTAGE")
        }
    }

    /** STATUS_ITEMS in STATUS_ID_SORT_ORDER. */
    val CURES: List<Cure> by lazy {
        rows.filter { it.size >= 4 && it[0] == "status" }.mapNotNull { r -> r[1].toIntOrNull()?.let { Cure(it, r[2], r[3]) } }
    }

    private val healById by lazy { HEALS.associateBy { it.id } }
    private val cureById by lazy { CURES.associateBy { it.id } }

    fun isHeal(id: Int): Boolean = id in healById
    fun isCure(id: Int): Boolean = id in cureById

    /**
     * calculateHealPercent (Program.lua:379-411) over [items] (id to quantity) for a
     * Pokemon of [maxHp]: a flat heal is its share of max HP, at most 100 percent a
     * use, a percentage heal its percent; with "Bag heals show HP instead" a flat
     * heal is its raw HP, uncapped, and a percentage heal that share of [maxHp].
     * The sum is rounded (math.floor(x + 0.5)). Returns (total, how many items).
     */
    fun totals(items: Map<Int, Int>, maxHp: Int, showHp: Boolean): Pair<Int, Int> {
        if (maxHp <= 0) return 0 to 0
        var healing = 0.0
        var count = 0
        for ((id, qty) in items) {
            val h = healById[id] ?: continue
            healing += when {
                h.percent && showHp -> (h.amount / 100) * qty * maxHp
                h.percent -> h.amount * qty
                showHp -> h.amount * qty
                else -> minOf(h.amount / maxHp * 100, 100.0) * qty
            }
            count += qty
        }
        return kotlin.math.floor(healing + 0.5).toInt() to count
    }

    /** MainScreen.lua:808: "Heals: 150% (3)", or "Heals: 60 HP (3)" with the HP option. */
    fun healsLine(items: Map<Int, Int>, maxHp: Int, showHp: Boolean): String {
        val (total, count) = totals(items, maxHp, showHp)
        return "Heals: $total${if (showHp) " HP" else "%"} ($count)"
    }

    /** MainScreen.lua:809: "Status items: N", every status item in the bag counted. */
    fun statusLine(items: Map<Int, Int>): String = "Status items: " + items.filterKeys { it in cureById }.values.sum()

    /** readItemDataIntoFrame's plural: "Potions", "Berry Juices", an item ending in y as "ies". */
    private fun plural(name: String, qty: Int): String =
        if (qty <= 1) name else if (name.endsWith("y")) name.dropLast(1) + "ies" else name + "s"

    private fun amount(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

    /** The "Healing Items" list, in sort order: "3 Potions (20 HP)", "1 Sitrus Berry (25%)". */
    fun healingList(items: Map<Int, Int>): List<String> = HEALS.mapNotNull { h ->
        items[h.id]?.let { q -> "$q ${plural(h.name, q)} (${amount(h.amount)}${if (h.percent) "%" else " HP"})" }
    }

    /** The "Status Items" list, in sort order: "2 Antidotes (Poison)". */
    fun statusList(items: Map<Int, Int>): List<String> = CURES.mapNotNull { c ->
        items[c.id]?.let { q -> "$q ${plural(c.name, q)} (${c.cures})" }
    }

    /** onItemBagInfoHover (MainScreen.lua:265-284) for an empty list: "You currently do not have any healing items." */
    fun emptyText(kind: String): String = "You currently do not have any ${kind.lowercase()} items."
}
