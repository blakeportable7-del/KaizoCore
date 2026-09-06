package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settings picker must never show two rows with the same title.
 *
 * It did: "FRLG Kaizo.rnqs" and "FRLG Kaizo (edited).rnqs" both parse to the
 * label "FRLG Kaizo", so the picker listed the same words twice and the only
 * distinguishing text was the filename in the subtitle.
 */
class RnqsLabelTest {

    @Test
    fun `colliding labels fall back to the file stem`() {
        val out = RnqsInfo.displayLabels(
            listOf("FRLG Kaizo.rnqs", "FRLG Kaizo (edited).rnqs"),
        )
        assertEquals("FRLG Kaizo" to "FRLG Kaizo", out[0])
        assertEquals("FRLG Kaizo (edited)" to "FRLG Kaizo", out[1])
        // The point of the exercise: the two TITLES differ.
        assertTrue(out[0].first != out[1].first)
    }

    @Test
    fun `a unique label is left alone`() {
        val out = RnqsInfo.displayLabels(
            listOf("FRLG Kaizo.rnqs", "RSE NatDex v1.2 Kaizo.rnqs"),
        )
        assertEquals("FRLG Kaizo", out[0].first)
        assertEquals("RSE Nat. Dex Kaizo", out[1].first)
    }

    @Test
    fun `every title in a real picker list is unique`() {
        val files = listOf(
            "DPPt Kaizo.rnqs", "FRLG Kaizo.rnqs", "FRLG Kaizo (edited).rnqs",
            "FRLG NatDex v1.2 Kaizo.rnqs", "RSE Kaizo.rnqs",
            "RSE NatDex v1.2 Kaizo.rnqs",
        )
        val titles = RnqsInfo.displayLabels(files).map { it.first }
        assertEquals(titles.size, titles.toSet().size, "duplicate titles: $titles")
    }
}
