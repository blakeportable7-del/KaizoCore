package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/** The Gen 2 reference's IV estimate (Utils.estimateIVs, ExtrasScreen.displayJudgeMessage). */
class IvEstimateTest {

    @Test
    fun `the fraction is the stat sum against the BST, held between 0 and 1`() {
        // Level 50, six stats summing to 461, BST 309: (461 - 50 - 35) * 50 / 50 - 309 = 67, over 96.
        assertEquals(67.0 / 96, IvEstimate.fraction(80, 76, 75, 77, 78, 75, 50, 309), 1e-9)
        assertEquals(0.0, IvEstimate.fraction(10, 5, 5, 5, 5, 5, 50, 309), "below the BST is 0")
        assertEquals(1.0, IvEstimate.fraction(400, 300, 300, 300, 300, 300, 50, 309), "past it is 1")
    }

    @Test
    fun `the judge's words follow the reference's ranges, gaps included`() {
        fun at(e: Double) = IvEstimate.judge(e)
        assertEquals("Outstanding!!!", at(151.0))
        assertEquals("Quite impressive!!", at(150.0)); assertEquals("Quite impressive!!", at(121.0))
        assertEquals("Above average!", at(120.0)); assertEquals("Above average!", at(91.0))
        assertEquals("Decent.", at(90.9))
        // Integer ranges on a fractional number: between 150 and 151 nothing matches but the last branch.
        assertEquals("Decent.", at(150.5)); assertEquals("Decent.", at(120.5))
        assertEquals("Quite impressive!!", IvEstimate.verdict(67.0 / 96), "129.8")
    }
}
