package com.personal.rando.ride

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideHeatTest {

    /** 往北骑 1 公里；[offset] 往东偏几米，模拟每次 GPS 的偏差 */
    private fun north(offset: Double = 0.0, fromLat: Double = 1.30) =
        (0..100).map { LatLon(fromLat + it * 0.00009, 103.80 + offset / 111_300) }

    @Test
    fun `次数分三档`() {
        assertEquals(listOf(HeatLevel.LOW, HeatLevel.LOW, HeatLevel.MID, HeatLevel.MID, HeatLevel.HIGH),
            listOf(1, 2, 3, 9, 10).map(HeatLevel::of))
    }

    @Test
    fun `同一条路骑三次是橙色，GPS 偏几米也算同一条`() {
        val pieces = RideHeat.pieces(listOf(north(0.0), north(6.0), north(-8.0)))
        assertTrue(pieces.isNotEmpty())
        assertTrue(pieces.all { it.level == HeatLevel.MID })
    }

    @Test
    fun `只有重叠的那一截变色`() {
        // 第二次只骑了后半段
        val pieces = RideHeat.pieces(listOf(north(), north(fromLat = 1.3045), north(fromLat = 1.3045)))
        val levels = pieces.map { it.level }.toSet()
        assertEquals(setOf(HeatLevel.LOW, HeatLevel.MID), levels)
    }

    @Test
    fun `一次骑行来回走同一条路只算一次`() {
        val there = north()
        val pieces = RideHeat.pieces(listOf(there + there.asReversed()))
        assertTrue(pieces.all { it.level == HeatLevel.LOW })
    }

    @Test
    fun `骑十次是红色，画在最上面`() {
        val pieces = RideHeat.pieces(List(10) { north(it.toDouble()) } + listOf(north(fromLat = 1.32)))
        assertEquals(HeatLevel.HIGH, pieces.last().level)
        assertEquals(HeatLevel.LOW, pieces.first().level)
    }
}
