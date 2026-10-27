package com.personal.rando.routing

import com.personal.rando.nav.Geo
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeManeuversTest {

    /** 从起点按一段段（方向, 米）走出来的路线，每 5 米一个点；返回点和每段拐弯处的序号 */
    private fun path(vararg legs: Pair<Double, Double>): Pair<List<LatLon>, List<Int>> {
        val points = mutableListOf(LatLon(1.3, 103.8))
        val corners = mutableListOf<Int>()
        for ((bearing, meters) in legs) {
            corners += points.size - 1
            repeat((meters / 5).toInt()) { points += Geo.offset(points.last(), bearing, 5.0) }
        }
        return points to corners.drop(1)
    }

    @Test
    fun `稍向右紧接着向左急转，看形状是左转`() {
        // 往北 → 稍向右走 5 米 → 往西
        val (points, at) = path(0.0 to 100.0, 30.0 to 5.0, 270.0 to 100.0)
        val merged = mergeClose(listOf(Maneuver(at[0], Turn.SLIGHT_RIGHT, angle = 31), Maneuver(at[1], Turn.SHARP_LEFT, angle = -118)), points)
        assertEquals(Turn.LEFT, merged.single().turn)
        assertEquals(at[0], merged.single().pointIndex)
    }

    @Test
    fun `错位路口右转紧接着左转，合成直行`() {
        val (points, at) = path(0.0 to 100.0, 90.0 to 10.0, 0.0 to 100.0)
        val merged = mergeClose(listOf(Maneuver(at[0], Turn.RIGHT, angle = 85), Maneuver(at[1], Turn.LEFT, angle = -89)), points)
        assertEquals(Turn.STRAIGHT, merged.single().turn)
    }

    @Test
    fun `夹着直行的只有一个真拐弯、形状也往那边拐，原样留着`() {
        val (points, at) = path(0.0 to 100.0, 340.0 to 10.0, 340.0 to 100.0)
        val keep = Maneuver(at[1], Turn.KEEP_LEFT, angle = -20)
        assertEquals(listOf(keep), mergeClose(listOf(Maneuver(at[0], Turn.STRAIGHT), keep), points))
    }

    @Test
    fun `标成直行其实拐了大弯，按形状说`() {
        // BRouter 标了直行、15 米后右转，实际是先左拐 90° 再右拐一点
        val (points, at) = path(0.0 to 100.0, 270.0 to 15.0, 300.0 to 100.0)
        val merged = mergeClose(listOf(Maneuver(at[0], Turn.STRAIGHT), Maneuver(at[1], Turn.SLIGHT_RIGHT, angle = 30)), points)
        assertEquals(Turn.LEFT, merged.single().turn)
    }

    @Test
    fun `离得远的和环岛不合`() {
        val (points, at) = path(0.0 to 100.0, 90.0 to 100.0, 0.0 to 100.0)
        val far = listOf(Maneuver(at[0], Turn.RIGHT, angle = 90), Maneuver(at[1], Turn.LEFT, angle = -90))
        assertEquals(far, mergeClose(far, points))
        val roundabout = listOf(Maneuver(at[0], Turn.RIGHT, angle = 90), Maneuver(at[0] + 1, Turn.ROUNDABOUT, exit = 2, angle = 30))
        assertEquals(roundabout, mergeClose(roundabout, points))
    }

    @Test
    fun `两个左转挨着，掉头`() {
        val (points, at) = path(0.0 to 100.0, 270.0 to 10.0, 180.0 to 100.0)
        val merged = mergeClose(listOf(Maneuver(at[0], Turn.LEFT, angle = -90), Maneuver(at[1], Turn.LEFT, angle = -90)), points)
        assertEquals(Turn.U_TURN, merged.single().turn)
    }

    /** West Coast Road 过马路那段（brouter.de 实际返回的）：斜着过马路，中间安全岛上先右后左 */
    private val westCoast = listOf(
        LatLon(1.308565, 103.757989), LatLon(1.309195, 103.757525), LatLon(1.30938, 103.757401),
        LatLon(1.309783, 103.757113), LatLon(1.30989, 103.757046), LatLon(1.310015, 103.756978),
        LatLon(1.310072, 103.756957), LatLon(1.310158, 103.756936), LatLon(1.31027, 103.756928),
        LatLon(1.310311, 103.756779), LatLon(1.310382, 103.756667), LatLon(1.310471, 103.756601),
        LatLon(1.31059, 103.756501), LatLon(1.310655, 103.75645), LatLon(1.310713, 103.756476),
        LatLon(1.31076, 103.756489), LatLon(1.310788, 103.756497), LatLon(1.310828, 103.756392),
        LatLon(1.310883, 103.756301), LatLon(1.310927, 103.756243), LatLon(1.310973, 103.756196),
        LatLon(1.311093, 103.756103),
    )

    @Test
    fun `West Coast Road：过马路时左转，安全岛上那串不说右转`() {
        val hints = listOf(
            Maneuver(8, Turn.LEFT, angle = -70), Maneuver(9, Turn.STRAIGHT, angle = 16),
            Maneuver(12, Turn.STRAIGHT, angle = 1), Maneuver(13, Turn.RIGHT, angle = 62), Maneuver(14, Turn.LEFT, angle = -93),
        )
        assertEquals(listOf(Turn.LEFT, Turn.STRAIGHT), mergeClose(hints, westCoast).map { it.turn })
    }
}
