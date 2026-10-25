package com.personal.rando.ride

import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharePrivacyTest {

    private val home = LatLon(1.3150, 103.7650)

    /** 从家往正东每 10 米一个点，一直骑到 [meters] 米 */
    private fun eastFromHome(meters: Int) = (0..meters step 10).map { Geo.offset(home, 90.0, it.toDouble()) }

    @Test
    fun `家一定在圈里而且离圈边至少 250 米`() {
        for (seed in 0L until 200L) {
            val zone = SharePrivacy.zone(home, seed)
            assertTrue(zone.radius - Geo.distance(zone.center, home) >= 250.0)
            assertTrue(zone.radius in 400.0..500.0)
        }
    }

    @Test
    fun `同一个 seed 圈不变，不同 seed 圆心不一样`() {
        val a = SharePrivacy.zone(home, 42)
        val b = SharePrivacy.zone(home, 42)
        val c = SharePrivacy.zone(home, 43)
        assertEquals(a.center, b.center)
        assertTrue(Geo.distance(a.center, c.center) > 1.0)
    }

    @Test
    fun `圈里的轨迹不画`() {
        val zone = SharePrivacy.zone(home, 7)
        val pieces = SharePrivacy.hide(eastFromHome(2000), zone)
        assertEquals(1, pieces.size)
        assertTrue(pieces.flatten().all { Geo.distance(it, zone.center) > zone.radius })
        assertTrue(Geo.distance(pieces.first().first(), home) > 250.0)
    }

    @Test
    fun `中途穿过家附近断成两段`() {
        val zone = SharePrivacy.zone(home, 7)
        val west = Geo.offset(home, 270.0, 1500.0)
        val track = (0..300).map { Geo.offset(west, 90.0, it * 10.0) }
        assertEquals(2, SharePrivacy.hide(track, zone).size)
    }

    @Test
    fun `全在圈里一段都不剩`() {
        assertTrue(SharePrivacy.hide(eastFromHome(100), SharePrivacy.zone(home, 1)).isEmpty())
    }

    @Test
    fun `没收藏家就头尾各剪 200 米`() {
        val track = eastFromHome(1000)
        val kept = SharePrivacy.hide(track, null).single()
        assertEquals(200.0, Geo.distance(track.first(), kept.first()), 1.0)
        assertEquals(200.0, Geo.distance(track.last(), kept.last()), 1.0)
    }

    @Test
    fun `不到 400 米的整段都剪掉`() {
        assertTrue(SharePrivacy.hide(eastFromHome(300), null).isEmpty())
    }
}
