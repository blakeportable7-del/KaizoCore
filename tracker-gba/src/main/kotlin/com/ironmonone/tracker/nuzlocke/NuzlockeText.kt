package com.ironmonone.tracker.nuzlocke

/**
 * The ledger as a text file (2026-09-29): one line per fact, fields separated by tabs, the way the app's other
 * per-run files are written. A line starts with what it is; a line that is not understood, or that is cut off,
 * is skipped, so a file from a newer build still opens and a damaged one loses only what is damaged.
 *
 * The first line names the format. A file without it is not a ledger and [parse] says so (null) rather than
 * inventing an empty run over the top of somebody's file.
 */
object NuzlockeText {
    const val MAGIC = "KAIZOCORE-NUZLOCKE"
    const val VERSION = 1

    /** What the first lines of a file say about the run, for listing runs without reading them whole. */
    class Header(
        val id: String,
        val bind: String,
        val game: String,
        val preset: NuzlockePreset,
        val startedAt: Long,
        val status: RunStatus,
        val endReason: String,
        val version: Int,
    )

    private fun clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
    private fun b(v: Boolean) = if (v) "1" else "0"
    private fun g(v: Gender?) = v?.glyph ?: ""
    private fun gender(s: String?): Gender? = Gender.entries.firstOrNull { it.glyph == s }

    fun format(ledger: NuzlockeLedger): String {
        val m = ledger.meta
        val out = StringBuilder()
        fun line(vararg f: Any?) { out.append(f.joinToString("\t") { clean(it?.toString() ?: "") }).append('\n') }
        line(MAGIC, VERSION)
        line("run", m.id, m.bind, m.game, m.startedAt, m.status.key, m.endedAt, m.endReason)
        line("flag", "started", b(m.started)); line("flag", "partySeen", b(m.partySeen))
        line("flag", "whiteoutLatched", b(m.whiteoutLatched)); line("flag", "styleShift", b(m.styleShift))
        // A file from before the Game Boy and DS games has neither line and is a Gen 3 run.
        line("system", m.system.key); if (m.gameKey.isNotEmpty()) line("gamekey", m.gameKey)
        for ((k, v) in m.rules.toEntries()) line("rule", k, v)
        if (m.genlockeId.isNotEmpty() || m.leg > 0) line("genlocke", m.genlockeId, m.leg, m.carriedFrom)
        for (h in m.heirsIn) line("heirin", h.species, h.speciesName, h.nickname, h.level, g(h.gender), b(h.shiny))
        for (h in m.heirsOut) line("heirout", h.species, h.speciesName, h.nickname, h.level, g(h.gender), b(h.shiny))
        for (a in ledger.areas.values) {
            line("area", a.key, a.name)
            a.encounter?.let { e ->
                line("enc", a.key, e.species, e.speciesName, e.level, e.pid, e.method, g(e.gender), b(e.shiny), e.at, e.outcome.key, e.monId ?: "", b(e.manual))
            }
            for (x in a.extras) line("extra", a.key, x.kind.key, x.species, x.speciesName, x.level, x.pid, x.at, x.outcome.key, x.monId ?: "")
        }
        for (r in ledger.roster.values) {
            line("mon", r.id, r.species, r.speciesName, r.nickname, r.level, g(r.gender), r.origin.key, r.areaKey ?: "", r.areaName, r.at,
                b(r.inParty), b(r.alive), b(r.violation), r.partner ?: "", b(r.shiny), r.types.joinToString(","), r.highestLevel, b(r.forgiven))
            r.death?.let { d -> line("death", r.id, d.at, d.level, d.areaName, d.cause, d.badges, b(d.manual)) }
        }
        for (e in ledger.events) line("evt", e.at, e.kind, b(e.manual), e.areaKey ?: "", e.text)
        for (w in ledger.warnings) line("warn", w.id, w.at, w.kind.key, b(w.dismissed), w.text)
        for (n in ledger.notes) line("note", n)
        for (k in m.beatenBosses) line("beaten", k)
        return out.toString()
    }

    /** The first lines of a file as a [Header], or null when it is not a ledger. */
    fun header(lines: Sequence<String>): Header? {
        var version = 0
        var run: List<String>? = null
        val rules = HashMap<String, String>()
        var first = true
        for (raw in lines) {
            val f = raw.split('\t')
            if (first) {
                first = false
                if (f.getOrNull(0) != MAGIC) return null
                version = f.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
                continue
            }
            when (f.getOrNull(0)) {
                "run" -> run = f
                "rule" -> if (f.size >= 3) rules[f[1]] = f[2]
                // Everything the header needs comes before the first area or roster line.
                "area", "mon", "enc", "evt" -> break
            }
        }
        val r = run ?: return null
        val id = r.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return Header(
            id = id, bind = r.getOrNull(2) ?: "", game = r.getOrNull(3) ?: "",
            preset = NuzlockePreset.byKey(rules["preset"]) ?: NuzlockePreset.STANDARD,
            startedAt = r.getOrNull(4)?.toLongOrNull() ?: 0L,
            status = RunStatus.byKey(r.getOrNull(5)), endReason = r.getOrNull(7) ?: "", version = version,
        )
    }

    /** A whole ledger from its text, or null when the text is not a ledger. */
    fun parse(text: String): NuzlockeLedger? {
        val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return null
        val head = header(lines.asSequence()) ?: return null
        val runLine = lines.firstOrNull { it.startsWith("run\t") }?.split('\t') ?: return null
        val entries = HashMap<String, String>()
        for (l in lines) if (l.startsWith("rule\t")) { val f = l.split('\t'); if (f.size >= 3) entries[f[1]] = f[2] }
        val meta = RunMeta(head.id, head.bind, head.game, NuzlockeRules.fromEntries(entries), head.startedAt)
        meta.status = head.status
        meta.endedAt = runLine.getOrNull(6)?.toLongOrNull() ?: 0L
        meta.endReason = head.endReason
        val ledger = NuzlockeLedger(meta)

        fun heir(f: List<String>): Heir? {
            val species = f.getOrNull(1)?.toIntOrNull() ?: return null
            return Heir(species, f.getOrNull(2) ?: "", f.getOrNull(3) ?: "", f.getOrNull(4)?.toIntOrNull() ?: 0, gender(f.getOrNull(5)), f.getOrNull(6) == "1")
        }
        for (l in lines.drop(1)) {
            val f = l.split('\t')
            when (f[0]) {
                "flag" -> {
                    val on = f.getOrNull(2) == "1"
                    when (f.getOrNull(1)) {
                        "started" -> meta.started = on
                        "partySeen" -> meta.partySeen = on
                        "whiteoutLatched" -> meta.whiteoutLatched = on
                        "styleShift" -> meta.styleShift = on
                    }
                }
                "system" -> meta.system = NuzlockeSystem.byKey(f.getOrNull(1))
                "gamekey" -> meta.gameKey = f.getOrNull(1) ?: ""
                "beaten" -> f.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { meta.beatenBosses += it }
                "genlocke" -> { meta.genlockeId = f.getOrNull(1) ?: ""; meta.leg = f.getOrNull(2)?.toIntOrNull() ?: 0; meta.carriedFrom = f.getOrNull(3) ?: "" }
                "heirin" -> heir(f)?.let { meta.heirsIn += it }
                "heirout" -> heir(f)?.let { meta.heirsOut += it }
                "area" -> if (f.size >= 3) { ledger.areas[f[1]] = AreaRecord(f[1], f[2]) }
                "enc" -> if (f.size >= 13) {
                    val species = f[2].toIntOrNull(); val level = f[4].toIntOrNull(); val pid = f[5].toLongOrNull(); val at = f[9].toLongOrNull()
                    if (species != null && level != null && pid != null && at != null) {
                        val area = ledger.areas.getOrPut(f[1]) { AreaRecord(f[1], f[1]) }
                        area.encounter = Encounter(species, f[3], level, pid, f[6], gender(f[7]), f[8] == "1", at, Outcome.byKey(f[10]), f[11].toLongOrNull(), f[12] == "1")
                    }
                }
                "extra" -> if (f.size >= 10) {
                    val species = f[3].toIntOrNull(); val level = f[5].toIntOrNull(); val pid = f[6].toLongOrNull(); val at = f[7].toLongOrNull()
                    if (species != null && level != null && pid != null && at != null) {
                        val area = ledger.areas.getOrPut(f[1]) { AreaRecord(f[1], f[1]) }
                        area.extras += Extra(ExtraKind.byKey(f[2]), species, f[4], level, pid, at, Outcome.byKey(f[8]), f[9].toLongOrNull())
                    }
                }
                "mon" -> if (f.size >= 19) {
                    val id = f[1].toLongOrNull(); val species = f[2].toIntOrNull(); val level = f[5].toIntOrNull(); val at = f[10].toLongOrNull()
                    if (id != null && species != null && level != null && at != null) {
                        val r = RosterMon(id, species, f[3], f[4], level, gender(f[6]), Origin.byKey(f[7]), f[8].takeIf { it.isNotEmpty() }, f[9], at)
                        r.inParty = f[11] == "1"; r.alive = f[12] != "0"; r.violation = f[13] == "1"
                        r.partner = f[14].toLongOrNull(); r.shiny = f[15] == "1"
                        r.types = f[16].split(',').mapNotNull { it.trim().toIntOrNull() }
                        r.highestLevel = f[17].toIntOrNull() ?: level; r.forgiven = f[18] == "1"
                        ledger.roster[id] = r
                    }
                }
                "death" -> if (f.size >= 8) {
                    val mon = f[1].toLongOrNull()?.let { ledger.roster[it] }
                    val at = f[2].toLongOrNull(); val level = f[3].toIntOrNull()
                    if (mon != null && at != null && level != null) {
                        mon.death = Death(at, level, f[4], f[5], f[6].toIntOrNull() ?: 0, f[7] == "1")
                        // A death line makes the Pokemon dead whatever its own flag says.
                        mon.alive = false
                    }
                }
                "evt" -> if (f.size >= 6) {
                    f[1].toLongOrNull()?.let { ledger.events += Event(it, f[2], f.drop(5).joinToString(" "), f[3] == "1", f[4].takeIf { k -> k.isNotEmpty() }) }
                }
                "warn" -> if (f.size >= 6) {
                    f[2].toLongOrNull()?.let { ledger.warnings += Warning(f[1], it, WarnKind.byKey(f[3]), f.drop(5).joinToString(" "), f[4] == "1") }
                }
                "note" -> if (f.size >= 2) ledger.notes += f.drop(1).joinToString(" ")
            }
        }
        return ledger
    }
}
