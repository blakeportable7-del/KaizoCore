package com.ironmonone.app.engine.hns

/**
 * One Heart & Soul build's layout, as tools/hns/layout.py exports it (docs/HNS-KAIZO.md, Layout): symbols with their
 * addresses, struct sizes and field offsets (bitfields as [shift, width] inside the field's unit), constants, enum
 * families, the TM/HM list, the charmap, table shapes and the script statics with the ROM addresses of their operands.
 * Nothing here is typed in by hand; every address the engine writes comes from this file.
 */
class HnsLayout private constructor(root: Map<String, Any?>) {

    class Field(val offset: Int, val size: Int, val count: Int, val shift: Int, val width: Int, val signed: Boolean) {
        val isBits: Boolean get() = width > 0
    }

    class Struct(val size: Int, val fields: Map<String, Field>) {
        fun f(name: String): Field = fields[name] ?: error("struct has no field $name")
    }

    class Table(val struct: String?, val elem: String?, val count: Int)

    class Machine(val kind: String, val num: Int, val move: Int, val item: Int)

    /** A script operand: the value the build holds and where it sits. */
    class Operand(val value: Int, val addr: Int, val size: Int)

    /** A givemon, giveegg, setwildbattle, seteventmon, setwildbossbattle or species setvar, with its operands. */
    class ScriptMon(
        val kind: String, val label: String, val file: String, val text: String,
        val operands: Map<String, Operand>, val usedBy: List<String>,
    )

    /** A script line that names a species (case, checkspecies, bufferspeciesname...), with the addresses holding it. */
    class SpeciesRef(val kind: String, val label: String, val file: String, val text: String, val refs: List<Pair<Int, Int>>)

    val buildCrc: Long
    val romBase: Int
    val symbols: Map<String, Pair<Int, Int>>
    val structs: Map<String, Struct>
    val constants: Map<String, Long>
    val enums: Map<String, Map<String, Int>>
    val machines: List<Machine>
    val charmap: Map<Int, String>
    val tables: Map<String, Table>
    val scriptMons: List<ScriptMon>
    val speciesRefs: List<SpeciesRef>
    /** Where data/event_scripts.s starts in the ROM. */
    val scriptDataBase: Int

    /**
     * A hidden item of a map (a BgEvent of kind BG_EVENT_HIDDEN_ITEM): [addr] is its bgUnion u32, which holds the item
     * in bits [itemShift, itemShift + itemWidth) beside its flag, quantity and underfoot bits (layout.py proves them
     * against every map.json). The comfort build exports them; the plain build's layout has none.
     */
    class HiddenItem(val map: String, val x: Int, val y: Int, val addr: Int, val item: Int, val quantity: Int, val flagName: String)

    val hiddenItems: List<HiddenItem>
    /** The item's bits inside a hidden item's u32: (shift, width). */
    val hiddenItemBits: Pair<Int, Int>

    init {
        buildCrc = (root["buildCrc"] as String).toLong(16)
        romBase = hex(root["romBase"] as String)
        symbols = (root["symbols"] as Map<*, *>).entries.associate { (k, v) ->
            val m = v as Map<*, *>
            k as String to (hex(m["addr"] as String) to (m["size"] as Long).toInt())
        }
        structs = (root["structs"] as Map<*, *>).entries.associate { (k, v) ->
            val m = v as Map<*, *>
            val fields = (m["fields"] as Map<*, *>).entries.associate { (fk, fv) ->
                val f = fv as Map<*, *>
                val bits = f["bits"] as List<*>?
                fk as String to Field(
                    offset = (f["offset"] as Long).toInt(), size = (f["size"] as Long).toInt(),
                    count = (f["count"] as Long?)?.toInt() ?: 0,
                    shift = (bits?.get(0) as Long?)?.toInt() ?: 0, width = (bits?.get(1) as Long?)?.toInt() ?: 0,
                    signed = f["signed"] == true,
                )
            }
            k as String to Struct((m["size"] as Long).toInt(), fields)
        }
        constants = (root["constants"] as Map<*, *>).entries.associate { (k, v) -> k as String to (v as Long) }
        enums = (root["enums"] as Map<*, *>).entries.associate { (k, v) ->
            k as String to (v as Map<*, *>).entries.associate { (ek, ev) -> ek as String to (ev as Long).toInt() }
        }
        machines = (root["machines"] as List<*>).map {
            val m = it as Map<*, *>
            Machine(m["kind"] as String, (m["num"] as Long).toInt(), (m["move"] as Long).toInt(), (m["item"] as Long).toInt())
        }
        charmap = (root["charmap"] as Map<*, *>).entries.associate { (k, v) -> (k as String).toInt(16) to v as String }
        tables = (root["tables"] as Map<*, *>).entries.associate { (k, v) ->
            val m = v as Map<*, *>
            k as String to Table(m["struct"] as String?, m["elem"] as String?, (m["count"] as Long).toInt())
        }
        val scripts = root["scripts"] as Map<*, *>
        scriptDataBase = hex(scripts["scriptDataBase"] as String)
        scriptMons = (scripts["mons"] as List<*>).map {
            val m = it as Map<*, *>
            val ops = LinkedHashMap<String, Operand>()
            for ((k, v) in m) {
                val key = k as String
                if (key.endsWith("Addr")) {
                    val name = key.removeSuffix("Addr")
                    val value = (m[name] as Long?)?.toInt() ?: continue
                    val size = (m[name + "Size"] as Long?)?.toInt() ?: 2
                    ops[name] = Operand(value, hex(v as String), size)
                }
            }
            ScriptMon(
                m["kind"] as String, m["label"] as String, m["file"] as String, m["text"] as String, ops,
                (m["usedBy"] as List<*>?)?.map { u -> u as String } ?: emptyList(),
            )
        }
        val hidden = root["hiddenItems"] as Map<*, *>?
        hiddenItems = (hidden?.get("items") as List<*>?).orEmpty().map {
            val m = it as Map<*, *>
            HiddenItem(m["map"] as String, (m["x"] as Long).toInt(), (m["y"] as Long).toInt(), hex(m["addr"] as String),
                (m["item"] as Long).toInt(), (m["quantity"] as Long).toInt(), (m["flagName"] as String?) ?: "")
        }
        hiddenItemBits = ((hidden?.get("bits") as Map<*, *>?)?.get("item") as List<*>?)
            ?.let { (it[0] as Long).toInt() to (it[1] as Long).toInt() } ?: (0 to 11)
        speciesRefs = (scripts["speciesRefs"] as List<*>).map {
            val m = it as Map<*, *>
            SpeciesRef(
                m["kind"] as String, m["label"] as String, m["file"] as String, m["text"] as String,
                (m["refs"] as List<*>).map { r ->
                    val rm = r as Map<*, *>
                    (rm["species"] as Long).toInt() to hex(rm["addr"] as String)
                },
            )
        }
    }

    fun sym(name: String): Int = symbols[name]?.first ?: error("layout has no symbol $name")
    fun hasSym(name: String): Boolean = name in symbols
    fun struct(name: String): Struct = structs[name] ?: error("layout has no struct $name")
    fun const(name: String): Int = (constants[name] ?: error("layout has no constant $name")).toInt()
    fun enum(family: String): Map<String, Int> = enums[family] ?: emptyMap()
    fun enumValue(family: String, name: String): Int? = enums[family]?.get(name)

    /** The address of record [i] of [table]. */
    fun rec(table: String, i: Int): Int {
        val t = tables[table] ?: error("layout has no table $table")
        val size = struct(t.struct ?: error("$table is not a struct table")).size
        return sym(table) + i * size
    }

    companion object {
        fun parse(json: String): HnsLayout {
            @Suppress("UNCHECKED_CAST")
            return HnsLayout(HnsJson.parse(json) as Map<String, Any?>)
        }

        private fun hex(s: String): Int = s.removePrefix("0x").removePrefix("0X").toLong(16).toInt()
    }
}

/** What species-<build>.json adds to the layout: the constant name of each species and its Gen 1-3 scope. */
class HnsSpeciesFile private constructor(root: Map<String, Any?>) {
    class Entry(val id: Int, val const: String, val name: String, val gen13Scope: Boolean, val enabled: Boolean)

    val buildCrc: Long = (root["buildCrc"] as String).toLong(16)
    val species: List<Entry> = (root["species"] as List<*>).map {
        val m = it as Map<*, *>
        Entry((m["id"] as Long).toInt(), (m["const"] as String?) ?: "", (m["name"] as String?) ?: "",
            m["gen13Scope"] == true, m["enabled"] == true)
    }

    companion object {
        fun parse(json: String): HnsSpeciesFile {
            @Suppress("UNCHECKED_CAST")
            return HnsSpeciesFile(HnsJson.parse(json) as Map<String, Any?>)
        }
    }
}
