package com.personal.rando.group

import com.personal.rando.ride.Fix
import com.personal.rando.routing.LatLon
import com.personal.rando.ui.teammatesText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val voice = GroupProtocol.parse(
            """{"t":"voice","id":"a","name":"小红","format":"aac-live","sid":9,"seq":4,"end":true,"data":"AAAA"}""", 0,
        ) as GroupEvent.Voice
        assertEquals(GroupEvent.Voice("a", "小红", "aac-live", 9, 4, true, "AAAA"), voice)
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

    @Test
    fun `分享的路线发出去再收回来是一样的`() {
        val sent = SharedRoute(
            ts = 42, title = "Marina Bay Sands", meters = 13_600,
            stops = listOf(LatLon(1.3, 103.8), LatLon(1.31, 103.82), LatLon(1.2838, 103.8591)),
            profile = com.personal.rando.routing.BikeProfile.PCN, avoidRoads = true, avoidBridges = false,
        )
        val json = JSONObject(GroupProtocol.route(sent))
        assertEquals("route", json.getString("t"))
        assertEquals("PCN", json.getString("profile"))
        // 服务器转发时加上是谁分享的
        json.put("id", "a").put("name", "阿明")
        val got = (GroupProtocol.parse(json.toString(), 0) as GroupEvent.Route).route
        assertEquals(sent.copy(id = "a", name = "阿明"), got)
        assertEquals(GroupEvent.RouteCleared("a"), GroupProtocol.parse("""{"t":"route_clear","id":"a"}""", 0))
    }

    @Test
    fun `跟着骑用分享的路线配置，预计时间还按我的速度`() {
        val shared = SharedRoute(
            ts = 1, title = "x", meters = 0, stops = listOf(LatLon(1.3, 103.8), LatLon(1.31, 103.81)),
            profile = com.personal.rando.routing.BikeProfile.CITY, avoidRoads = false, avoidBridges = true,
        )
        val mine = com.personal.rando.routing.RoutePrefs(avoidRoads = true, pace = com.personal.rando.routing.Pace.FAST, mySpeed = 5.0)
        val prefs = shared.prefs(mine)
        assertEquals(com.personal.rando.routing.BikeProfile.CITY, prefs.profile)
        assertEquals(false, prefs.avoidRoads)
        assertEquals(true, prefs.avoidBridges)
        assertEquals(com.personal.rando.routing.Pace.FAST, prefs.pace)
        assertEquals(5.0, prefs.mySpeed!!, 0.0)
    }

    @Test
    fun `分享路线的提示文字`() {
        val r = SharedRoute(
            ts = 1, title = "Marina Bay Sands", meters = 13_600, stops = emptyList(),
            profile = com.personal.rando.routing.BikeProfile.PCN, avoidRoads = true, avoidBridges = false,
        )
        assertEquals("去 Marina Bay Sands · 13.6 公里 · 公园连道、不上马路", com.personal.rando.ui.sharedRouteText(r))
        assertEquals("原路返回 · 13.6 公里 · 公园连道、不上马路", com.personal.rando.ui.sharedRouteText(r.copy(title = "原路返回")))
    }

    @Test
    fun `求助消息`() {
        val json = JSONObject(GroupProtocol.sos(LatLon(1.3, 103.8)))
        assertEquals("sos", json.getString("t"))
        json.put("id", "a").put("name", "阿明").put("ts", 5)
        assertEquals(GroupEvent.Sos(SosAlert("a", "阿明", LatLon(1.3, 103.8), 5)), GroupProtocol.parse(json.toString(), 0))
        assertEquals(GroupEvent.SosCleared("a"), GroupProtocol.parse("""{"t":"sos_clear","id":"a"}""", 0))
    }

    @Test
    fun `对讲状态那一行`() {
        assertEquals("对讲没开：点「开对讲」队友才听得到你", com.personal.rando.ui.walkieText(WalkieState()))
        // 没开对讲也听得到队友
        assertEquals("小红 在说话…", com.personal.rando.ui.walkieText(WalkieState(talking = setOf("小红"))))
        assertEquals("在听 ▁▃·· · 蓝牙耳机 AirPods", com.personal.rando.ui.walkieText(WalkieState(on = true, device = "蓝牙耳机 AirPods", micBars = 2)))
        assertEquals("试一下：说一句话，会放给你自己听", com.personal.rando.ui.walkieText(WalkieState(on = true, testing = true)))
        assertEquals("对讲出问题了：麦克风打不开", com.personal.rando.ui.walkieText(WalkieState(problem = "麦克风打不开")))
    }

    @Test
    fun `骑行视图上的对讲`() {
        assertEquals("对讲没开", com.personal.rando.ui.rideWalkieText(WalkieState()))
        assertEquals("对讲开着", com.personal.rando.ui.rideWalkieText(WalkieState(on = true)))
        assertEquals("直连 2 人", com.personal.rando.ui.rideWalkieText(WalkieState(on = true, direct = 2)))
        assertEquals("小红 在说话…", com.personal.rando.ui.rideWalkieText(WalkieState(on = true, talking = setOf("小红"))))
        assertEquals("在听 ▁··· · 手机 · 直连 1 人", com.personal.rando.ui.walkieText(WalkieState(on = true, micBars = 1, direct = 1)))
    }

    @Test
    fun `直连的语音跟服务器转发的一样认`() {
        val text = GroupProtocol.localVoice("a", "阿明", 7, 2, true, "AAAA")
        assertEquals(GroupEvent.Voice("a", "阿明", AacCodec.FORMAT, 7, 2, true, "AAAA"), GroupProtocol.parse(text, 0))
    }

    @Test
    fun `直连广播的名字`() {
        val name = NearbyLink.endpointName("K7PX3M", "id-1", "小|红")
        assertEquals(NearbyLink.Peer("K7PX3M", "id-1", "小|红"), NearbyLink.parseEndpointName(name))
        assertEquals(null, NearbyLink.parseEndpointName("随便一个蓝牙设备"))
        assertEquals(null, NearbyLink.parseEndpointName("|id|名字"))
    }

    @Test
    fun `两条路来的语音只放先到的`() {
        val d = VoiceDedup()
        // 直连先到 0、1，服务器再来 0、1：丢掉
        assertTrue(d.accept("a", 1, 0))
        assertTrue(d.accept("a", 1, 1))
        assertFalse(d.accept("a", 1, 0))
        assertFalse(d.accept("a", 1, 1))
        // 直连断了，服务器接着送 2：照放，不断
        assertTrue(d.accept("a", 1, 2))
        // 另一个人、下一句话各算各的
        assertTrue(d.accept("b", 1, 0))
        assertTrue(d.accept("a", 2, 0))
        // 一句说完以后服务器那份才到：不会再放一遍
        assertFalse(d.accept("a", 2, 0))
    }

    @Test
    fun `直连丢了一段，服务器那份晚到还能补上`() {
        val d = VoiceDedup()
        assertTrue(d.accept("a", 1, 0))
        // 直连上 1 丢了、2 先到
        assertTrue(d.accept("a", 1, 2))
        // 服务器的 1 晚到：收下补上；2 再来一份就丢
        assertTrue(d.accept("a", 1, 1))
        assertFalse(d.accept("a", 1, 1))
        assertFalse(d.accept("a", 1, 2))
        // 太旧的（早就放过去了）不要
        assertTrue(d.accept("a", 1, 100))
        assertFalse(d.accept("a", 1, 3))
    }

    @Test
    fun `附近的队伍`() {
        val teams = NearbyTeam.parse(
            """{"teams":[{"code":"K7PX3M","names":["阿明","小红","阿强","小李"],"members":4,"meters":120},{"code":"AB12CD","names":[],"members":0,"meters":800}]}"""
        )
        assertEquals(listOf("K7PX3M", "AB12CD"), teams.map { it.code })
        assertEquals("阿明、小红、阿强 等 4 人", teams[0].namesText)
        assertEquals(120, teams[0].meters)
        assertEquals("", teams[1].namesText)
        assertEquals(emptyList<NearbyTeam>(), NearbyTeam.parse("{}"))
    }
}
