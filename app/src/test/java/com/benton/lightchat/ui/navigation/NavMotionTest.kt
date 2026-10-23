package com.benton.lightchat.ui.navigation

import androidx.navigationevent.NavigationEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class NavMotionTest {

    @Test
    fun leftEdgeMovesRight() {
        assertEquals(1, NavMotion.predictiveDirection(NavigationEvent.EDGE_LEFT))
    }

    @Test
    fun rightEdgeMovesLeft() {
        assertEquals(-1, NavMotion.predictiveDirection(NavigationEvent.EDGE_RIGHT))
    }

    @Test
    fun unknownEdgeFallsBackToLeftEdgeDirection() {
        assertEquals(1, NavMotion.predictiveDirection(NavigationEvent.EDGE_NONE))
    }
}
