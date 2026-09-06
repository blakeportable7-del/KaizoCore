package com.ironmonone.app

/**
 * Settings-file identification by name — the Kotlin port of tools/SettingsMatcher,
 * which was validated against all 72 real .rnqs files on Blake's machine.
 *
 * Match on game tag + NatDex + ruleset, never on an exact filename (brief section 5):
 * real names carry version tokens ("RSE NatDex v1.2 Kaizo.rnqs") the brief never
 * predicted.
 */
data class RnqsInfo(
    val fileName: String,
    val gameTag: String?,   // "RSE", "FRLG", ...
    val ruleset: String?,   // normalised: "kaizo", "superkaizo", ...
    val natDex: Boolean,
) {
    val complete: Boolean get() = gameTag != null && ruleset != null

    /** The Gen 1 second pass ("RBY PART 2.rnqs"): applied by the app after the chosen preset, never chosen itself. */
    val secondPass: Boolean get() = fileName.removeSuffix(".rnqs").removeSuffix(".RNQS").lowercase().replace(Regex("[^a-z0-9]"), "").contains("part2")

    /** "RSE Nat. Dex Kaizo" style label for the picker. */
    val label: String get() = buildString {
        append(gameTag ?: "?")
        if (natDex) append(" Nat. Dex")
        append(" ")
        append(RULESET_LABELS[ruleset] ?: ruleset ?: "?")
    }

    companion object {
        // Longest-first: "superkaizo" must match before "kaizo".
        private val RULESETS = listOf(
            "survivalrevival", "ironmonjourney", "kaizodoubles", "superkaizo",
            "chaoskaizo", "evokaizo", "standard", "survival", "ultimate", "kaizo",
        )
        // Longest-first again, and both the reference's full file names and the
        // short tags people actually type. A file named "DPPt Kaizo.rnqs" was
        // labelled "? Kaizo" because only the long form was listed.
        private val GAME_TAGS = listOf(
            "diamondpearlplatinum" to "DPPt", "heartgoldsoulsilver" to "HGSS",
            "goldsilvercrystal" to "GSC", "redblueyellow" to "RBY",
            "blackwhite2" to "B2W2", "blackwhite" to "BW",
            "dppt" to "DPPt", "hgss" to "HGSS", "b2w2" to "B2W2",
            "frlg" to "FRLG", "rse" to "RSE", "gsc" to "GSC", "rby" to "RBY", "bw" to "BW",
        )
        private val RULESET_LABELS = mapOf(
            "kaizo" to "Kaizo", "superkaizo" to "Super Kaizo", "standard" to "Standard",
            "ultimate" to "Ultimate", "survival" to "Survival", "kaizodoubles" to "Kaizo Doubles",
            "chaoskaizo" to "Chaos Kaizo", "evokaizo" to "Evo Kaizo",
            "survivalrevival" to "Survival Revival", "ironmonjourney" to "Ironmon Journey",
        )

        /** "Super Kaizo" for "superkaizo"; the raw key if it has no label yet. */
        fun rulesetLabel(key: String?): String = RULESET_LABELS[key] ?: key ?: "?"

        /**
         * Display labels for a whole picker, guaranteed unique.
         *
         * [label] is derived from the game tag and ruleset, so two different
         * files can produce the SAME text: "FRLG Kaizo.rnqs" and
         * "FRLG Kaizo (edited).rnqs" both read "FRLG Kaizo". The picker then
         * showed two identical rows and the only way to tell them apart was
         * the filename underneath, which is not what a label is for.
         *
         * Where a label collides, the file stem leads instead - unique by
         * definition, since two files in one directory cannot share a name -
         * and the parsed label moves to the subtitle so nothing is lost.
         *
         * Returns title to subtitle, in the input order.
         */
        fun displayLabels(fileNames: List<String>): List<Pair<String, String>> {
            val parsed = fileNames.map { it to parse(it) }
            val counts = parsed.groupingBy { it.second.label }.eachCount()
            return parsed.map { (name, info) ->
                val stem = name.removeSuffix(".rnqs").removeSuffix(".RNQS")
                if ((counts[info.label] ?: 0) > 1) stem to info.label
                else info.label to name
            }
        }

        fun parse(fileName: String): RnqsInfo {
            val n = fileName.removeSuffix(".rnqs").removeSuffix(".RNQS")
                .lowercase().replace(Regex("[^a-z0-9]"), "")
            return RnqsInfo(
                fileName = fileName,
                gameTag = GAME_TAGS.firstOrNull { n.contains(it.first) }?.second,
                ruleset = RULESETS.firstOrNull { n.contains(it) },
                natDex = n.contains("natdex"),
            )
        }
    }
}
