package com.personal.rando.group

import com.personal.rando.ride.Fix
import com.personal.rando.routing.LatLon
import com.personal.rando.ui.teammatesText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GroupTest {

    @Test
    fun `发出去的位置带上速度和方向`() {
        val json = JSONObject(GroupProtocol.position(Fix(LatLon(1.3, 103.8), speed = 5f, bearing = 90f)))
        assertEquals("pos", json.getString("t"))
        assertEquals(1.3, json.getDouble("lat"), 1e-9)
        assertEquals(5.0, json.getDouble("speed"), 1e-9)
        assertEquals(90.0, json.getDouble("bearing"), 1e-9)
    }

    @Test
    fun `认出服务器发来的各种消息`() {
        val roster = GroupProtocol.parse(
            """{"t":"roster","members":[{"id":"a","name":"小红","lat":1.3,"lon":103.8,"ts":1},{"id":"b","name":"阿明"}]}""", now = 99,
        ) as GroupEvent.Roster
        assertEquals(listOf("小红", "阿明"), roster.members.map { it.name })
        assertEquals(LatLon(1.3, 103.8), roster.members[0].location)
        assertEquals(99L, roster.members[0].updatedAt)
        assertNull(roster.members[1].location)

        assertEquals(GroupEvent.Joined("c", "小李"), GroupProtocol.parse("""{"t":"join","id":"c","name":"小李"}""", 0))
        assertEquals(GroupEvent.Left("c"), GroupProtocol.parse("""{"t":"leave","id":"c"}""", 0))
        val voice = GroupProtocol.parse("""{"t":"voice","id":"a","name":"小红","format":"aac","ms":1500,"data":"AAAA"}""", 0) as GroupEvent.Voice
        assertEquals(1500, voice.millis)
        assertNull(GroupProtocol.parse("pong", 0))
        assertNull(GroupProtocol.parse("""{"t":"what"}""", 0))
    }

    @Test
    fun `队伍码 6 位，不含容易看错的字`() {
        val codes = (1..200).map { TeamCode.generate(Random(it)) }
        assertTrue(codes.all { it.length == 6 && it.none { c -> c in "01OI" } })
        assertEquals("K7PX3M", TeamCode.normalize(" k7px-3m "))
        assertNull(TeamCode.normalize("ab"))
        assertNull(TeamCode.normalize("好的好的"))
    }

    @Test
    fun `队友按离我多远排，信号断了的放后面`() {
        val me = LatLon(1.300, 103.800)
        val now = 100_000L
        val mates = listOf(
            Teammate("a", "阿明", LatLon(1.310, 103.800), updatedAt = now),
            Teammate("b", "小红", LatLon(1.301, 103.800), updatedAt = now),
            Teammate("c", "小李", LatLon(1.300, 103.801), updatedAt = now - 120_000),
            Teammate("d", "老王"),
        )
        assertEquals("队友：小红 111 米 · 阿明 1.1 公里 · 小李 信号断了 · 老王 还没定位", teammatesText(mates, me, now))
        assertNull(teammatesText(emptyList(), me, now))
    }
}
