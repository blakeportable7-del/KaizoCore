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

    /** The 60% levels pre-pass ("RSE PRE-PASS.rnqs"): applied by the app before the chosen preset (ExtraPasses), never chosen itself. */
    val prePass: Boolean get() = fileName.removeSuffix(".rnqs").removeSuffix(".RNQS").lowercase().replace(Regex("[^a-z0-9]"), "").contains("prepass")

    /** Either of the two: a file the app runs around a preset, so no Mode row or settings list offers it. */
    val appliedByApp: Boolean get() = secondPass || prePass

    /** "RSE Nat. Dex Kaizo" style label for the picker. */
    val label: String get() = buildString {
        append(gameTag ?: "?")
        if (natDex) append(" Nat. Dex")
        // A game's file with no ruleset word in it (a build made in Build your own) is just the game, not "FRLG ?" (2026-09-29).
        if (ruleset != null || gameTag == null) {
            append(" ")
            append(RULESET_LABELS[ruleset] ?: ruleset ?: "?")
        }
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
            "survivalrevival" to "Survival Revival", "ironmonjourney" to "IronMON Journey",
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

        /**
         * Display labels for real files, read with [of] so a renamed preset's
         * sidecar counts. Same contract as [displayLabels].
         */
        fun displayLabelsFor(files: List<java.io.File>): List<Pair<String, String>> {
            val parsed = files.map { it.name to of(it) }
            val counts = parsed.groupingBy { it.second.label }.eachCount()
            return parsed.map { (name, info) ->
                val stem = name.removeSuffix(".rnqs").removeSuffix(".RNQS")
                // A file with no ruleset word is titled by its own name too: "FRLG" alone says nothing (2026-09-29).
                if ((counts[info.label] ?: 0) > 1 || info.gameTag == null || info.ruleset == null) stem to info.label
                else info.label to name
            }
        }

        /** The sidecar beside a preset: "FRLG my run.rnqs" -> "FRLG my run.rnqs.meta". */
        fun metaFile(preset: java.io.File): java.io.File = java.io.File(preset.parentFile, preset.name + ".meta")

        /**
         * Records what a preset is for, beside it, so a name without the tokens
         * still pairs: a Nat. Dex preset saved as "My run" was refused on its own
         * Nat. Dex ROM and listed as "? ?" (2026-09-27, audit). Plain key=value
         * lines; a null field is left out.
         */
        fun writeMeta(preset: java.io.File, gameTag: String?, natDex: Boolean, ruleset: String?) {
            runCatching {
                metaFile(preset).writeText(buildString {
                    gameTag?.let { append("family=").append(it).append('\n') }
                    append("natdex=").append(natDex).append('\n')
                    ruleset?.let { append("ruleset=").append(it).append('\n') }
                })
            }
        }

        private val engineCache = HashMap<String, Pair<Long, Boolean?>>()

        /**
         * True when only the Nat. Dex fork reads [f], false when only ZX does,
         * null when neither (or the file is gone). Remembered per file and
         * modification time: the pairing guard asks on every recomposition.
         */
        private fun readsAsNatDex(f: java.io.File): Boolean? = synchronized(engineCache) {
            val stamp = f.lastModified()
            engineCache[f.absolutePath]?.let { (t, v) -> if (t == stamp) return v }
            fun reads(read: (java.io.FileInputStream) -> Any) =
                runCatching { java.io.FileInputStream(f).use { read(it) } }.isSuccess
            val v = when {
                reads { com.dabomstew.pkrandom.Settings.read(it) } -> true
                reads { com.dabomstew.pkrandomzx.Settings.read(it) } -> false
                else -> null
            }
            engineCache[f.absolutePath] = stamp to v
            v
        }

        /**
         * What a preset FILE is for: its name first, then the sidecar written at
         * save time ([writeMeta]) fills what the name lacks, and a file with no
         * game tag and no sidecar is judged by which engine can read it. The
         * name alone made a Nat. Dex preset renamed "My run" look vanilla.
         */
        fun of(file: java.io.File): RnqsInfo {
            val byName = parse(file.name)
            val meta = runCatching {
                metaFile(file).takeIf { it.isFile }?.readLines()
                    ?.mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
                    ?.toMap()
            }.getOrNull()
            val natDex = when {
                byName.natDex -> true
                meta?.get("natdex") != null -> meta["natdex"] == "true"
                byName.gameTag == null -> readsAsNatDex(file) ?: false
                else -> false
            }
            return byName.copy(
                gameTag = byName.gameTag ?: meta?.get("family")?.takeIf { it.isNotBlank() },
                ruleset = byName.ruleset ?: meta?.get("ruleset")?.takeIf { it.isNotBlank() },
                natDex = natDex,
            )
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
