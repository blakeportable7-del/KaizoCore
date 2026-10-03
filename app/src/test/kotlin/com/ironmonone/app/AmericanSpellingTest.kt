package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every word a player reads is American English (Blake, 2026-10-03, seeing "Tracker colours" in the theme dialog: "Dang,
 * I hate how you used British spelling on colors"). The sweep reads what a player reads where the source holds it: every
 * string literal in every module's main Kotlin and Java, the rules pages and the stream page in the assets, the res
 * strings, the settings help and the trackers' tables (their # comment lines left out), and the pages around the app:
 * README, NOTICE, the release notes, the wiki, the site and the bug form. Comments and names in code are not read.
 *
 * A word [BRITISH] matches fails it, unless it sits inside one of [KEPT]: text that is someone else's or names something
 * outside the app, and is never shown as KaizoCore's own words.
 */
class AmericanSpellingTest {
    private val root = File("..").canonicalFile

    /** Every module's main sources, the vendored randomizers' too: the randomizer log they write is shown on the phone. */
    private val sources = listOf(
        "app/src/main/kotlin", "tracker-gba/src/main/kotlin", "tracker-nds/src/main/kotlin", "core-api/src/main/kotlin",
        "core-patch/src/main/kotlin", "core-recipe/src/main/kotlin", "editor/src/main/kotlin", "libretrodroid/src/main/java",
        "engine-zx/src", "engine-natdex/src",
    )

    /** What the app shows from its files: a folder, and the extension of the files it reads from there. */
    private val shown = listOf(
        "app/src/main/assets/rulesets" to "md", "app/src/main/assets/stream" to "html", "app/src/main/res/values" to "xml",
        "libretrodroid/src/main/res/values" to "xml",
        "editor/src/main/resources" to "tsv", "tracker-gba/src/main/resources" to "tsv", "tracker-nds/src/main/resources" to "tsv",
    )

    /** The pages around the app a player reads: a file, or a folder of them. */
    private val pages = listOf(
        "README.md", "NOTICE", "docs/RELEASE-NOTES.md", "docs/wiki", "site/index.html", "site/thanks.html", ".github/ISSUE_TEMPLATE",
    )

    /**
     * The British spellings: Blake's list (colour, licence, behaviour, centre, recognise, favourite, defence, grey), the
     * others KaizoCore had (learnt, levelling, unrecognised), and the rest of their families. Whole words, any case, any
     * ending: "Colours", "recolouring", "unrecognised" all match; "colorization", "analyses" and "Blastoise" do not.
     */
    private val BRITISH = Regex(
        listOf(
            "\\w*colour\\w*", "\\w*favour\\w*", "\\w*behaviour\\w*", "\\w*honour\\w*", "\\w*neighbour\\w*", "\\w*flavour\\w*",
            "\\w*armour\\w*", "\\w*humour\\w*", "\\w*labour\\w*", "\\w*rumour\\w*",
            "licences?", "licenced", "defences?", "offences?", "pretences?",
            "centre[sd]?", "centring", "metres?", "litres?", "theatres?", "fibres?", "calibre", "sombre", "manoeuvr\\w*",
            "grey(s|ed|er|est|ing|ish|ness|scale)?",
            "\\w*recognis\\w*",
            "\\w*(initiali|normali|organi|reali|customi|randomi|optimi|minimi|maximi|summari|synchroni|seriali|finali|" +
                "categori|standardi|prioriti|emphasi|apologi|authori|capitali|speciali|visuali|locali|personali|moderni|" +
                "saniti|memori|utili|stabili)s(e|ed|es|ing|ation|ations|er|ers)",
            "analys(e|ed|ing|er|ers)", "paralys(e|ed|es|ing)", "catalogue[sd]?",
            "learnt", "levell(ed|ing|er|ers)", "travell(ed|ing|er|ers)", "cancell(ed|ing)", "labell(ed|ing)", "modell(ed|ing)",
            "signall(ed|ing)", "fuell(ed|ing)", "jewellery", "counsellors?",
            "judgements?", "acknowledgements?", "ageing", "artefacts?", "whilst", "amongst", "programmes?", "maths",
            "aluminium", "tyres?", "cheques?", "pyjamas", "sceptic\\w*", "enrol(s|ment)?", "fulfil(s|ment)?", "instalments?",
            "skilful", "wilful", "practis(e|ed|es|ing)", "aeroplanes?",
        ).joinToString("|", prefix = "\\b(", postfix = ")\\b"),
        RegexOption.IGNORE_CASE,
    )

    /**
     * Exact text that may keep a British spelling, and why. The sweep still reads the rest of the line around it, and
     * every entry must still be found somewhere, so none outlives its reason.
     */
    private val KEPT = mapOf(
        "unrecognised rom type: " to "the randomizer's own line in the vendored source (engine-zx and engine-natdex, " +
            "changed only where NOTICE says), printed to the system log and never shown",
        "Licences.kt" to "the source file NOTICE names",
    )

    private val used = HashSet<String>()

    /** The British words in [text], once [KEPT]'s exact text is taken out of it. */
    private fun british(text: String): List<String> {
        var t = text
        for (k in KEPT.keys) if (k in t) { used += k; t = t.replace(k, " ") }
        return BRITISH.findAll(t).map { it.value }.toList()
    }

    private fun path(f: File) = f.relativeTo(root).invariantSeparatorsPath

    /** A text file's lines as the player sees them: a table's # comment lines and XML and HTML comments are never shown. */
    private fun lines(f: File): List<Pair<Int, String>> {
        var text = f.readText().replace("\r\n", "\n")
        if (f.extension == "xml" || f.extension == "html") text = text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)) { m -> "\n".repeat(m.value.count { it == '\n' }) }
        return text.lines().mapIndexedNotNull { n, l -> if (f.extension == "tsv" && l.startsWith("#")) null else n + 1 to l }
    }

    @Test
    fun `every word a player reads is spelled the American way`() {
        val hits = ArrayList<String>()
        var literals = 0
        for (dir in sources) {
            val files = File(root, dir).walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
            assertTrue(files.isNotEmpty(), "no sources in $dir: did it move?")
            for (f in files) for ((line, text) in Literals(f.readText().replace("\r\n", "\n"), java = f.extension == "java").all()) {
                literals++
                british(text).takeIf { it.isNotEmpty() }?.let { hits += "${path(f)}:$line: $it in \"${text.take(140)}\"" }
            }
        }
        assertTrue(literals > 15_000, "the sweep read only $literals string literals")
        val files = shown.flatMap { (dir, ext) ->
            File(root, dir).walkTopDown().filter { it.isFile && it.extension == ext }.toList().also { assertTrue(it.isNotEmpty(), "no .$ext in $dir") }
        } + pages.flatMap { p ->
            val f = File(root, p)
            assertTrue(f.exists(), "$p is gone: did it move?")
            if (f.isDirectory) f.walkTopDown().filter { it.isFile }.toList() else listOf(f)
        }
        assertTrue(files.size > 180, "the sweep read only ${files.size} files")
        for (f in files) for ((line, text) in lines(f)) {
            british(text).takeIf { it.isNotEmpty() }?.let { hits += "${path(f)}:$line: $it in \"${text.trim().take(140)}\"" }
        }
        assertTrue(hits.isEmpty(), "British spellings a player can read (${hits.size}):\n" + hits.joinToString("\n"))
        assertEquals(KEPT.keys, used, "an entry in KEPT that nothing holds any more")
    }

    @Test
    fun `the sweep reads strings, templates and raw strings, and skips comments, chars and names`() {
        val src = listOf(
            "// Tracker colours, in a comment",
            "/* colours /* nested */ still a comment */",
            "val a = \"Tracker colours\"",
            "val b = \"\"\"raw centre \${ if (x) \"grey\" else 'c' }\"\"\"",
            "val c = '\"'; val `licence name` = \"\$honour and \${n} favourites\\n\"",
        ).joinToString("\n")
        val found = Literals(src, java = false).all()
        assertEquals(listOf(3 to "Tracker colours", 4 to "grey", 4 to "raw centre {}", 5 to "{} and {} favourites\n"), found)
        assertEquals(listOf("colours", "grey", "centre", "favourites"), found.flatMap { british(it.second) })
        // Java's comments do not nest, and its literals are read the same way.
        assertEquals(listOf(2 to "behaviour"), Literals("/* a /* b */ String s =\n\"behaviour\"; char q = '\\'';", java = true).all())
        for (w in listOf("colour", "Colours", "recoloured", "colourisation", "licence", "Licences", "behaviour", "centre", "centred",
            "recognise", "unrecognised", "favourite", "Favourites", "defence", "grey", "greyed", "learnt", "levelling", "initialise"))
            assertEquals(listOf(w), british("the $w here"), w)
        for (w in listOf("color", "colorization", "license", "licensed", "behavior", "center", "centered", "recognize", "favorite",
            "defense", "gray", "Greyhound", "learned", "leveling", "Blastoise", "Illumise", "Synchronoise", "advise", "surprise",
            "emphasis", "analyses", "categories", "Paralysis", "programmer", "cancellation", "dialogue", "towards"))
            assertEquals(emptyList<String>(), british("the $w here"), w)
        // A kept line is left out, and only that text: the rest of the line is still read.
        assertEquals(emptyList<String>(), british("unrecognised rom type: Ruby"))
        assertEquals(listOf("colour"), british("unrecognised rom type: colour"))
    }

    /**
     * The string literals of Kotlin or Java source, each with the line it starts on: "..." with its escapes read, raw
     * """...""", and the literals inside a template, whose code stands as {}. Comments, char literals and `quoted names`
     * are skipped, so an apostrophe or a quote in them opens nothing.
     */
    private class Literals(private val s: String, private val java: Boolean) {
        private val found = ArrayList<Pair<Int, String>>()
        private var i = 0
        private var line = 1

        fun all(): List<Pair<Int, String>> {
            code(template = false)
            return found
        }

        /** Code up to the end, or in a template up to its closing brace. */
        private fun code(template: Boolean) {
            var depth = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\n' -> { line++; i++ }
                    s.startsWith("//", i) -> while (i < s.length && s[i] != '\n') i++
                    s.startsWith("/*", i) -> comment()
                    s.startsWith("\"\"\"", i) -> { i += 3; string(raw = true) }
                    c == '"' -> { i++; string(raw = false) }
                    c == '\'' -> char()
                    c == '`' && !java -> { i++; while (i < s.length && s[i] != '`' && s[i] != '\n') i++; i++ }
                    template && c == '{' -> { depth++; i++ }
                    template && c == '}' -> { i++; if (depth-- == 0) return }
                    else -> i++
                }
            }
        }

        /** A block comment: Kotlin's nest, Java's do not. */
        private fun comment() {
            var depth = 0
            while (i < s.length) {
                when {
                    s.startsWith("/*", i) && (depth == 0 || !java) -> { depth++; i += 2 }
                    s.startsWith("*/", i) -> { i += 2; if (--depth == 0) return }
                    else -> { if (s[i] == '\n') line++; i++ }
                }
            }
        }

        /** 'a', '\n', 'é' or '\'', skipped whole. */
        private fun char() {
            val end = when {
                s.startsWith("\\u", i + 1) -> i + 7
                i + 1 < s.length && s[i + 1] == '\\' -> i + 3
                else -> i + 2
            }
            i = if (end < s.length && s[end] == '\'') end + 1 else i + 1
        }

        /** A string from just after its opening quotes to just after its closing ones. */
        private fun string(raw: Boolean) {
            val start = line
            val text = StringBuilder()
            while (i < s.length) {
                val c = s[i]
                if (raw && s.startsWith("\"\"\"", i)) {
                    var j = i
                    while (j < s.length && s[j] == '"') j++
                    repeat(j - i - 3) { text.append('"') }   // quotes before the closing three are the string's own
                    i = j
                    break
                }
                if (!raw && c == '"') { i++; break }
                if (!raw && c == '\n') break   // never closed on its line: it ends there
                when {
                    !raw && c == '\\' && i + 1 < s.length -> {
                        val e = s[i + 1]
                        if (e == 'u') {
                            text.append(s.substring(i + 2, minOf(i + 6, s.length)).toIntOrNull(16)?.toChar() ?: '?'); i += 6
                        } else {
                            text.append(when (e) { 'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; 'b' -> '\b'; else -> e }); i += 2
                        }
                    }
                    !java && c == '$' && i + 1 < s.length && s[i + 1] == '{' -> { i += 2; code(template = true); text.append("{}") }
                    !java && c == '$' && i + 1 < s.length && (s[i + 1].isLetter() || s[i + 1] == '_') -> {
                        i++
                        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                        text.append("{}")
                    }
                    else -> { if (c == '\n') line++; text.append(c); i++ }
                }
            }
            found += start to text.toString()
        }
    }
}
