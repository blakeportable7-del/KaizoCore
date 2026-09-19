package com.ironmonone.tracker

/**
 * MiscData.getMonGender: a Gen 3 Pokemon's gender from its species' gender
 * ratio (base stats +0x10) and the low byte of its personality value.
 * 0 is always male, 254 always female, 255 genderless; otherwise it is male
 * when the personality's low byte is at or above the ratio.
 */
object Gender3 {
    const val MALE = 0
    const val FEMALE = 1

    /** MALE, FEMALE, or null for a genderless species or an unknown ratio. */
    fun of(ratio: Int, pid: Long): Int? = when (ratio) {
        0 -> MALE
        254 -> FEMALE
        255 -> null
        else -> if ((pid and 0xFF) >= ratio) MALE else FEMALE
    }
}
