package com.personal.rando.nav

import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Maneuver
import com.personal.rando.routing.Route
import com.personal.rando.routing.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {

    /** 纬度 0.001 度 ≈ 111 米 */
    private val step = 0.001
    private val metersPerStep = 111.32

    /** 往北骑 2 段，在第 2 个点右转往东骑 2 段 */
    private val route = Route(
        points = listOf(
            LatLon(1.300, 103.800),
            LatLon(1.300 + step, 103.800),
            LatLon(1.300 + 2 * step, 103.800),
            LatLon(1.300 + 2 * step, 103.800 + step),
            LatLon(1.300 + 2 * step, 103.800 + 2 * step),
        ),
        distanceMeters = 445,
        durationSeconds = 400,
        ascendMeters = 0,
        segments = emptyList(),
        maneuvers = listOf(
            Maneuver(pointIndex = 2, turn = Turn.RIGHT),
            Maneuver(pointIndex = 3, turn = Turn.LEFT),
        ),
    )

    @Test
    fun `在路线上：算出离下一个路口多远`() {
        val progress = Navigator(route).update(LatLon(1.300 + step / 2, 103.800))
        assertEquals(metersPerStep / 2, progress.alongMeters, 1.0)
        assertEquals(Turn.RIGHT, progress.next?.turn)
        assertEquals(metersPerStep * 1.5, progress.distanceToNext, 1.0)
        assertEquals(Turn.LEFT, progress.following?.turn)
        assertFalse(progress.offRoute)
        assertFalse(progress.arrived)
    }

    @Test
    fun `偏一点点还算在路线上，投影到最近的线段`() {
        // 往东偏了 10 米左右
        val progress = Navigator(route).update(LatLon(1.300 + step / 2, 103.80009))
        assertEquals(metersPerStep / 2, progress.alongMeters, 1.0)
        assertFalse(progress.offRoute)
    }

    @Test
    fun `过了路口就看下一个`() {
        val nav = Navigator(route)
        val progress = nav.update(LatLon(1.300 + 2 * step, 103.800 + step / 2))
        assertEquals(Turn.LEFT, progress.next?.turn)
        assertNull(progress.following)
    }

    @Test
    fun `剩余时间按剩余距离折算`() {
        val progress = Navigator(route).update(LatLon(1.300 + 2 * step, 103.800))
        assertEquals(2 * metersPerStep, progress.remainingMeters, 2.0)
        assertEquals(200.0, progress.remainingSeconds.toDouble(), 3.0)
    }

    @Test
    fun `连续几次都离路线很远才算骑偏`() {
        val nav = Navigator(route)
        nav.update(LatLon(1.300, 103.800))
        val far = LatLon(1.300 + step, 103.802) // 离路线 200 多米
        assertFalse(nav.update(far).offRoute)
        assertFalse(nav.update(far).offRoute)
        assertTrue(nav.update(far).offRoute)
        // 回到路线上就恢复
        assertFalse(nav.update(LatLon(1.300 + step, 103.800)).offRoute)
    }

    @Test
    fun `一次飘点不影响进度`() {
        val nav = Navigator(route)
        nav.update(LatLon(1.300 + step, 103.800))
        val progress = nav.update(LatLon(1.300 + step, 103.805))
        assertEquals(metersPerStep, progress.alongMeters, 1.0)
    }

    @Test
    fun `到终点附近算到达`() {
        val progress = Navigator(route).update(LatLon(1.300 + 2 * step, 103.800 + 2 * step - 0.0001))
        assertTrue(progress.arrived)
        assertNull(progress.next)
    }

    @Test
    fun `途经点对到路线上，离起点多远`() {
        val nav = Navigator(route)
        // 拐弯后往东半段的旁边
        val along = nav.alongOf(LatLon(1.300 + 2 * step + 0.0001, 103.800 + 1.5 * step))
        assertEquals(3.5 * metersPerStep, along, 3.0)
    }

    @Test
    fun `进断头路掉头出来：去程不会跳到回程上`() {
        // 往东一段，往北进一条断头路，掉头回来再往东
        val spur = Route(
            points = listOf(
                LatLon(1.300, 103.800),
                LatLon(1.300, 103.800 + step),
                LatLon(1.300 + step, 103.800 + step),
                LatLon(1.300, 103.800 + step),
                LatLon(1.300, 103.800 + 2 * step),
            ),
            distanceMeters = 445,
            durationSeconds = 400,
            ascendMeters = 0,
            segments = emptyList(),
            maneuvers = listOf(
                Maneuver(pointIndex = 1, turn = Turn.LEFT),
                Maneuver(pointIndex = 2, turn = Turn.U_TURN_LEFT),
                Maneuver(pointIndex = 3, turn = Turn.LEFT),
            ),
        )
        val nav = Navigator(spur)
        nav.update(LatLon(1.300, 103.800 + step / 2))
        // 断头路上偏一点点：还在去程，下一个是掉头
        val progress = nav.update(LatLon(1.300 + step / 3, 103.800 + step + 0.00002))
        assertEquals(Turn.U_TURN_LEFT, progress.next?.turn)
        assertEquals(metersPerStep * (1 + 1.0 / 3), progress.alongMeters, 3.0)
    }

    @Test
    fun `环线起步时飘一下不会跳到终点算到达`() {
        // 绕一圈回到起点旁边 30 米
        val loop = Route(
            points = listOf(
                LatLon(1.300, 103.800),
                LatLon(1.300 + step, 103.800),
                LatLon(1.300 + step, 103.800 + step),
                LatLon(1.300, 103.800 + step),
                LatLon(1.300, 103.800 + 0.0006),
            ),
            distanceMeters = 400,
            durationSeconds = 400,
            ascendMeters = 0,
            segments = emptyList(),
        )
        val nav = Navigator(loop)
        nav.update(LatLon(1.300, 103.800))
        // 离起点那段 50 米、离终点那段 15 米
        val progress = nav.update(LatLon(1.3000, 103.8005))
        assertFalse(progress.arrived)
    }
}
