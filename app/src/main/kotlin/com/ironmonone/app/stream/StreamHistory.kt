package com.ironmonone.app.stream

import com.ironmonone.app.RunRecord
import java.io.File

/**
 * The run history page for viewers (Blake's streamer list, item 4): /history as a page that reads well in a browser and
 * as an OBS browser source, and /history.json for anyone's own overlay.
 *
 * It reads the app's own record of finished runs, RunHistory (prep/runhistory-<game>.tsv, filed by RunHistoryHook when
 * a run ends and by PrepStore when a new run replaces an unfinished one), for the game and mode last randomized
 * (prep/lastrun.txt): every past attempt on that game with that settings file, how far each got, the best of them, and
 * what ended runs most often. Nothing new is stored for it.
 *
 * Only the run facts go out: attempt, how it ended, badges, where, the Pokemon and trainer that ended it, time played,
 * when. No seed, no settings file path, no ROM name, no integrity counts, nothing about the phone or the player.
 */
object StreamHistory {
    /** Rows of the page when the address does not say (?rows=), and the most it will draw. */
    const val ROWS = 12
    const val MAX_ROWS = 100
    /** The JSON carries at most this many attempts, newest first. */
    const val MAX_JSON = 500
    /** How many of the most common Pokemon and trainers are named. */
    const val COMMON = 5

    /** The Pokemon that ended the most lost runs, most first, ties by name: (name, count). */
    fun commonPokemon(records: List<RunRecord>, limit: Int = COMMON): List<Pair<String, Int>> =
        common(records.filter { it.outcome == RunRecord.Outcome.LOST }.mapNotNull { it.killer?.name?.takeIf { n -> n.isNotBlank() } }, limit)

    /** The trainers whose Pokemon ended the most lost runs: a wild Pokemon has no trainer and is not counted here. */
    fun commonTrainers(records: List<RunRecord>, limit: Int = COMMON): List<Pair<String, Int>> =
        common(records.filter { it.outcome == RunRecord.Outcome.LOST }.map { it.trainer.trim() }.filter { it.isNotEmpty() }, limit)

    private fun common(names: List<String>, limit: Int): List<Pair<String, Int>> =
        names.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key to it.value }

    /** The run that got furthest: a win before anything short of one, then the most badges; among equals the first. */
    fun best(records: List<RunRecord>): RunRecord? =
        records.sortedWith(compareBy<RunRecord> { it.attempt }.thenBy { it.ended })
            .fold(null as RunRecord?) { b, r -> if (b == null || com.ironmonone.app.beats(r, b)) r else b }

    /**
     * Everything the page and the JSON say, for [game] (its title) and [mode] (the settings file's name for people),
     * from [records] on [ruleset] alone. A null [game] is a phone that has not randomized a game yet.
     */
    fun facts(game: String?, mode: String?, records: List<RunRecord>, ruleset: String?): Map<String, Any?> {
        val mine = if (ruleset == null) emptyList() else records.filter { it.ruleset == ruleset }
        val newest = mine.sortedWith(compareByDescending<RunRecord> { it.attempt }.thenByDescending { it.ended })
        return linkedMapOf(
            "app" to "KaizoCore",
            "game" to game,
            "mode" to mode,
            "runs" to mine.size,
            "wins" to mine.count { it.outcome == RunRecord.Outcome.WON },
            "best" to best(mine)?.let { row(it) },
            "commonPokemon" to commonPokemon(mine).map { (n, c) -> linkedMapOf("name" to n, "count" to c) },
            "commonTrainers" to commonTrainers(mine).map { (n, c) -> linkedMapOf("name" to n, "count" to c) },
            "attempts" to newest.take(MAX_JSON).map { row(it) },
        )
    }

    /** One run, as the page and the JSON show it. */
    private fun row(r: RunRecord): Map<String, Any?> = linkedMapOf(
        "attempt" to r.attempt,
        "outcome" to when (r.outcome) { RunRecord.Outcome.LOST -> "lost"; RunRecord.Outcome.WON -> "won"; RunRecord.Outcome.ENDED -> "ended" },
        "badges" to r.badges,
        "area" to r.location.trim().ifEmpty { null },
        "lostTo" to r.killer?.takeIf { r.outcome == RunRecord.Outcome.LOST }?.let { linkedMapOf("name" to it.name, "level" to it.level) },
        "trainer" to r.trainer.trim().takeIf { it.isNotEmpty() && r.outcome == RunRecord.Outcome.LOST },
        "playSeconds" to r.playSeconds,
        "ended" to r.ended,
    )

    fun json(facts: Map<String, Any?>): String = Json.write(facts)

    /**
     * Where the facts come from in the app: the game and settings file last randomized, and that game's history file.
     * Read on every request (a few hundred short lines at most), so a run that just ended shows at the next refresh.
     * Never throws: anything unreadable is a page with no runs.
     */
    fun source(filesDir: File): () -> Map<String, Any?> {
        val store by lazy { com.ironmonone.app.PrepStore(filesDir) }
        return {
            runCatching {
                val (romId, settingsName) = store.loadLastRun() ?: return@runCatching null
                val game = com.ironmonone.core.RomKind.byId(romId) ?: return@runCatching null
                val records = com.ironmonone.app.RunHistory(store.runHistoryFile(game)).all()
                val mode = runCatching { com.ironmonone.app.RunModeName.of(store.settingsFile(settingsName), game.family) }
                    .getOrDefault(settingsName).removeSuffix(".rnqs")
                facts(game.displayName, mode, records, settingsName)
            }.getOrNull() ?: facts(null, null, emptyList(), null)
        }
    }

    // ------------------------------------------------------------------ the page

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun badges(n: Int) = if (n == 1) "1 badge" else "$n badges"

    /** "Lost to Lv.24 Graveler (Leader Brock), Route 4", "Won", "Ended early, Viridian City". */
    @Suppress("UNCHECKED_CAST")
    private fun how(row: Map<String, Any?>): String {
        val lostTo = row["lostTo"] as? Map<String, Any?>
        val trainer = row["trainer"] as? String
        val area = row["area"] as? String
        val head = when (row["outcome"]) {
            "won" -> "Won"
            "ended" -> "Ended early"
            else -> if (lostTo != null) "Lost to Lv.${lostTo["level"]} ${lostTo["name"]}" + (trainer?.let { " ($it)" } ?: "") else "Lost"
        }
        return esc(head + if (area != null && row["outcome"] != "won") ", $area" else "")
    }

    private fun time(row: Map<String, Any?>): String =
        (row["playSeconds"] as? Int)?.takeIf { it > 0 }?.let { com.ironmonone.app.playTimeText(it) } ?: ""

    /**
     * The page. [query] is the address's: bg=none leaves the page's own background out (the boxes keep theirs), for
     * an OBS browser source over a scene; rows=N draws that many of the latest attempts; w=N sets the width in pixels.
     */
    @Suppress("UNCHECKED_CAST")
    fun page(facts: Map<String, Any?>, query: Map<String, String> = emptyMap()): String {
        val rows = query["rows"]?.toIntOrNull()?.coerceIn(1, MAX_ROWS) ?: ROWS
        val clear = query["bg"] == "none"
        val width = query["w"]?.toIntOrNull()?.coerceIn(240, 1920)
        val game = facts["game"] as? String
        val mode = facts["mode"] as? String
        val runs = facts["runs"] as? Int ?: 0
        val wins = facts["wins"] as? Int ?: 0
        val attempts = (facts["attempts"] as? List<Map<String, Any?>>).orEmpty()
        val best = facts["best"] as? Map<String, Any?>
        val pokemon = (facts["commonPokemon"] as? List<Map<String, Any?>>).orEmpty()
        val trainers = (facts["commonTrainers"] as? List<Map<String, Any?>>).orEmpty()

        val body = StringBuilder()
        body.append("<section class=\"box head\"><div class=\"label\">RUN HISTORY</div>")
        if (game == null) {
            body.append("<div class=\"title\">No game yet</div><p class=\"dim\">Runs show here once a Kaizo IronMON run has been played.</p></section>")
        } else {
            body.append("<div class=\"title\">").append(esc(game)).append("</div>")
            if (!mode.isNullOrBlank()) body.append("<div class=\"dim\">").append(esc(mode)).append("</div>")
            body.append("<div class=\"nums\"><div><b>").append(runs).append("</b><span>").append(if (runs == 1) "run" else "runs")
                .append("</span></div><div><b>").append(wins).append("</b><span>").append(if (wins == 1) "win" else "wins").append("</span></div></div></section>")
            if (runs == 0) body.append("<section class=\"box\"><p class=\"dim\">No finished runs on this game and mode yet.</p></section>")
        }
        if (best != null) {
            body.append("<section class=\"box\"><div class=\"label\">BEST RUN</div><div class=\"best\"><span class=\"att\">Attempt ")
                .append(best["attempt"]).append("</span> <span class=\"gold\">").append(badges(best["badges"] as? Int ?: 0)).append("</span></div>")
                .append("<div>").append(how(best)).append("</div>")
            time(best).takeIf { it.isNotEmpty() }?.let { body.append("<div class=\"dim\">").append(it).append(" played</div>") }
            body.append("</section>")
        }
        if (pokemon.isNotEmpty() || trainers.isNotEmpty()) {
            body.append("<section class=\"box\"><div class=\"label\">WHAT ENDS RUNS</div>")
            fun list(head: String, items: List<Map<String, Any?>>) {
                if (items.isEmpty()) return
                body.append("<div class=\"sub\">").append(head).append("</div><ol>")
                for (i in items) body.append("<li><span>").append(esc(i["name"].toString())).append("</span><b>").append(i["count"]).append("</b></li>")
                body.append("</ol>")
            }
            list("Pokémon", pokemon)
            list("Trainers", trainers)
            body.append("</section>")
        }
        if (attempts.isNotEmpty()) {
            body.append("<section class=\"box\"><div class=\"label\">LATEST RUNS</div><table>")
            for (a in attempts.take(rows)) {
                body.append("<tr class=\"").append(a["outcome"]).append("\"><td class=\"att\">#").append(a["attempt"]).append("</td><td>")
                    .append(how(a)).append("</td><td class=\"gold\">").append(a["badges"]).append("</td></tr>")
            }
            body.append("</table><div class=\"dim small\">The last column is badges.</div></section>")
        }
        return PAGE
            .replace("%BG%", if (clear) "transparent" else "#0b0b0b")
            .replace("%W%", width?.let { "${it}px" } ?: "100%")
            .replace("%MAXW%", if (width != null) "none" else "560px")
            .replace("%BODY%", body.toString())
    }

    private val PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>KaizoCore run history</title>
<style>
html,body{margin:0;background:%BG%}
body{font:15px/1.35 "Segoe UI",Roboto,Arial,sans-serif;color:#e8e8e8}
#root{box-sizing:border-box;width:%W%;max-width:%MAXW%;padding:0}
.box{box-sizing:border-box;background:#0b0b0b;border:2px solid #3a3a3a;border-radius:2px;padding:10px 14px;margin:0 0 8px}
.label{color:#9a9a9a;letter-spacing:2px;font-size:13px;margin-bottom:4px}
.title{font-size:22px;font-weight:700}
.dim{color:#9a9a9a}
.small{font-size:12px;margin-top:4px}
.gold{color:#ffd54a;font-weight:700}
.nums{display:flex;gap:24px;margin-top:6px}
.nums b{color:#ffd54a;font-size:34px;font-variant-numeric:tabular-nums;margin-right:6px}
.nums span{color:#9a9a9a}
.best{font-size:18px}
.att{color:#e8e8e8;font-weight:700;font-variant-numeric:tabular-nums}
.sub{color:#9a9a9a;margin:6px 0 2px}
ol{margin:0;padding-left:22px}
ol li span{display:inline-block;min-width:60%}
ol li b{color:#ffd54a}
table{width:100%;border-collapse:collapse}
td{padding:3px 6px 3px 0;vertical-align:top;border-bottom:1px solid #222}
td.att{white-space:nowrap;width:1%}
td.gold{text-align:right;width:1%}
tr.won td{color:#4cc38a}
</style></head><body>
<div id="root">%BODY%</div>
<script>
(function () {
  // A run that ends while the page is up shows at the next look, without a reload that would flash on a stream.
  var root = document.getElementById('root');
  function look() {
    fetch(location.href, { cache: 'no-store' }).then(function (r) { return r.ok ? r.text() : null; }).then(function (t) {
      if (!t) return;
      var d = new DOMParser().parseFromString(t, 'text/html');
      var n = d.getElementById('root');
      if (n && n.innerHTML !== root.innerHTML) root.innerHTML = n.innerHTML;
    }, function () { });
  }
  setInterval(look, 30000);
})();
</script></body></html>
"""
}
