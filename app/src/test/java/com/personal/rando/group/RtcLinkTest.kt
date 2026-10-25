package com.personal.rando.group

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RtcLinkTest {

    @Test
    fun `信令发出去再收回来是一样的`() {
        val sent = JSONObject(GroupProtocol.rtc("bbbbbbbb-2", JSONObject().put("k", "offer").put("sdp", "v=0")))
        assertEquals("rtc", sent.getString("t"))
        assertEquals("bbbbbbbb-2", sent.getString("to"))
        assertFalse(JSONObject(GroupProtocol.rtc(null, JSONObject().put("k", "hi"))).has("to"))

        // 服务器转过来时把 to 换成发的人
        val event = GroupProtocol.parse("""{"t":"rtc","id":"aaaaaaaa-1","data":{"k":"offer","sdp":"v=0"}}""", 0) as GroupEvent.Rtc
        assertEquals("aaaaaaaa-1", event.id)
        assertEquals("v=0", event.data.getString("sdp"))
        assertNull(GroupProtocol.parse("""{"t":"rtc","id":"aaaaaaaa-1"}""", 0))
    }

    @Test
    fun `两人之间只有一边发 offer`() {
        assertTrue(RtcLink.offers("aaaa", "bbbb"))
        assertFalse(RtcLink.offers("bbbb", "aaaa"))
    }

    @Test
    fun `认出服务器给的 STUN 和 TURN`() {
        val list = RtcLink.parseIceServers(
            """{"iceServers":[{"urls":["stun:stun.cloudflare.com:3478"]},""" +
                """{"urls":"turn:turn.cloudflare.com:3478?transport=udp","username":"u","credential":"p"},{"urls":[]}]}"""
        )
        assertEquals(2, list.size)
        assertEquals(listOf("stun:stun.cloudflare.com:3478"), list[0].urls)
        assertNull(list[0].username)
        assertEquals(RtcLink.IceSpec(listOf("turn:turn.cloudflare.com:3478?transport=udp"), "u", "p"), list[1])
        assertTrue(RtcLink.parseIceServers("{}").isEmpty())
    }
}
