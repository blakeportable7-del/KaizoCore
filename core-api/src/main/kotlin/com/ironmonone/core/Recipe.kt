package com.ironmonone.core

enum class PatchFormat { BPS, IPS, UPS, XDELTA }

/**
 * Where a patch sits in the pipeline. Order is fixed by the brief (section 7):
 *   clean -> NatDex BPS -> QoL -> randomize -> play
 */
enum class PatchStage { AFTER_CLEAN, AFTER_NATDEX, BEFORE_RANDOMIZE }

data class Patch(
    val id: String,
    val displayName: String,
    val oneLineEffect: String,
    val format: PatchFormat,
    val appliesTo: Set<RomKind>,
    val stage: PatchStage,
    val uri: String,
) {
    fun compatibleWith(kind: RomKind) = kind in appliesTo
}

/**
 * One step of the pipeline. Stages 1-3 are deterministic, so their outputs are cached
 * by (inputCrc, stepId). Only [Randomize] consumes a seed.
 */
sealed interface RecipeStep {
    val label: String

    data class Clean(val kind: RomKind) : RecipeStep {
        override val label get() = "Clean ${kind.displayName}"
    }

    data class Apply(val patch: Patch) : RecipeStep {
        override val label get() = patch.displayName
    }

    data class Randomize(val engineId: String, val settings: Settings) : RecipeStep {
        override val label get() = "${settings.gameTag} ${settings.ruleset} randomize"
    }
}

/** Outcome of running one step, and what the timeline UI renders from. */
data class StepResult(
    val step: RecipeStep,
    val inputCrc: Long,
    val outputCrc: Long,
    val outputUri: String,
    val fromCache: Boolean,
)

/**
 * The ordered pipeline for a profile.
 *
 * The last deterministic output is the PREPARED ROM. New Run re-runs only
 * [RecipeStep.Randomize] against it, which is why New Run takes seconds and why it is
 * structurally incapable of randomizing an already-randomized ROM.
 */
data class Recipe(val steps: List<RecipeStep>) {
    init {
        require(steps.count { it is RecipeStep.Randomize } == 1) {
            "A recipe must randomize exactly once"
        }
        require(steps.last() is RecipeStep.Randomize) {
            "Randomize must be the final step; patching a randomized ROM is forbidden"
        }
    }

    val deterministicSteps: List<RecipeStep> get() = steps.dropLast(1)
    val randomizeStep: RecipeStep.Randomize get() = steps.last() as RecipeStep.Randomize
}
