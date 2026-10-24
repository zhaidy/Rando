package com.personal.rando.nav

import com.personal.rando.routing.Maneuver
import com.personal.rando.routing.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePrompterTest {

    private val right = Maneuver(pointIndex = 10, turn = Turn.RIGHT)
    private val left = Maneuver(pointIndex = 12, turn = Turn.LEFT)

    private fun progress(
        distanceToNext: Double,
        next: Maneuver? = right,
        following: Maneuver? = null,
        gap: Double = Double.MAX_VALUE,
        offRoute: Boolean = false,
        arrived: Boolean = false,
    ) = NavProgress(
        alongMeters = 0.0,
        remainingMeters = 1000.0,
        remainingSeconds = 300,
        next = next,
        distanceToNext = distanceToNext,
        following = following,
        distanceNextToFollowing = gap,
        offRoute = offRoute,
        arrived = arrived,
    )

    @Test
    fun `离得远不说，200 米内提前说一次，快到了再说一次`() {
        val p = VoicePrompter()
        assertNull(p.next(progress(500.0)))
        assertEquals("200 米后右转", p.next(progress(190.0)))
        assertNull(p.next(progress(150.0)))
        assertEquals("右转", p.next(progress(30.0)))
        assertNull(p.next(progress(20.0)))
    }

    @Test
    fun `距离取整：百米以上按 50 米，以下按 10 米`() {
        assertEquals("150 米后右转", VoicePrompter().next(progress(140.0)))
        assertEquals("80 米后右转", VoicePrompter().next(progress(83.0)))
    }

    @Test
    fun `一出现就很近的路口只说一次`() {
        val p = VoicePrompter()
        assertEquals("右转", p.next(progress(30.0)))
        assertNull(p.next(progress(25.0)))
    }

    @Test
    fun `两个路口挨着就连着说`() {
        assertEquals("右转，然后左转", VoicePrompter().next(progress(30.0, following = left, gap = 40.0)))
        assertEquals("右转", VoicePrompter().next(progress(30.0, following = left, gap = 150.0)))
    }

    @Test
    fun `骑偏只说一次，回到路线后再偏还会说`() {
        val p = VoicePrompter()
        assertEquals("已偏离路线，正在重新规划", p.next(progress(100.0, offRoute = true)))
        assertNull(p.next(progress(100.0, offRoute = true)))
        p.next(progress(500.0))
        assertEquals("已偏离路线，正在重新规划", p.next(progress(100.0, offRoute = true)))
    }

    @Test
    fun `到达只说一次`() {
        val p = VoicePrompter()
        assertEquals("已到达目的地", p.next(progress(0.0, next = null, arrived = true)))
        assertNull(p.next(progress(0.0, next = null, arrived = true)))
    }

    @Test
    fun `换了新路线后同一个序号的路口重新播`() {
        val p = VoicePrompter()
        assertEquals("右转", p.next(progress(30.0)))
        p.reset()
        assertEquals("右转", p.next(progress(30.0)))
    }

    @Test
    fun `开始导航报全程`() {
        assertEquals("开始导航，全程 15.9 公里", VoicePrompter().start(15863.0))
    }
}
