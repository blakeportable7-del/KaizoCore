package com.ironmonone.app

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SetupScreen's carousel checkboxes: the saved list keeps the setup's own order. */
class CarouselItemsTest {
    @AfterTest fun defaults() { TrackerOptions.carouselItems = TrackerOptions.CAROUSEL_DEFAULT }

    @Test
    fun `turning items off and back on keeps the reference order`() {
        TrackerOptions.setCarouselItem("Notes", false)
        TrackerOptions.setCarouselItem("Badges", false)
        assertFalse(TrackerOptions.carouselShows("Notes"))
        assertEquals("RouteInfo,Trainers,LastAttack,BattleDetails,Pedometer,GachaMon", TrackerOptions.carouselItems)
        TrackerOptions.setCarouselItem("Badges", true)
        TrackerOptions.setCarouselItem("Notes", true)
        assertEquals(TrackerOptions.CAROUSEL_DEFAULT, TrackerOptions.carouselItems)
        assertTrue(TrackerOptions.carouselShows("Pedometer"))
    }
}
