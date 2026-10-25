package com.personal.rando.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleBarTest {
    @Test
    fun `取不超过的整数距离`() {
        assertEquals(200.0, ScaleBars.nice(270.0), 0.0)
        assertEquals(500.0, ScaleBars.nice(500.0), 0.0)
        assertEquals(1000.0, ScaleBars.nice(1900.0), 0.0)
        assertEquals(50.0, ScaleBars.nice(99.0), 0.0)
        assertEquals(1.0, ScaleBars.nice(0.4), 0.0)
    }
}
