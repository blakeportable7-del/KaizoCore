package com.ironmonone.app

import com.ironmonone.tracker.LossCondition
import java.io.File

/**
 * "Game is considered over when", changed during a run to a rule other than its settings file's own (IronMON rules
 * check R12, 2026-09-30): a Kaizo run set to "Entire party faints" filed nothing when its lead died, and nothing said
 * the rule had moved. The change goes on the run's log here, and the rule in effect at the end on its record
 * (lossRuleAtEnd), which the card and the shared line say. Outside a run there is no log to write.
 */
internal object RunRuleLog {
    fun changed(filesDir: File, rule: LossCondition, settingsName: String?, label: String = rule.label, at: Long = System.currentTimeMillis()) {
        if (settingsName.isNullOrBlank()) return
        runCatching {
            val store = PrepStore(filesDir)
            // The mode's own rule, read as the default is (RunModeName), so a sidecar-only mode is no change.
            val family = store.loadLastRun()?.first?.let { com.ironmonone.core.RomKind.byId(it) }?.family
            if (rule == LossCondition.forSettingsName(RunModeName.of(store.settingsFile(settingsName), family))) return
            store.runEvents(store.session())?.add(RunEvents.Kind.RULE, "game over", label, at)
        }
    }
}
