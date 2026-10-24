package com.personal.rando.places

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlacesTest {

    private val park = SavedPlace("East Coast Park", LatLon(1.3008, 103.9122))
    private val home = SavedPlace(SavedPlace.HOME, LatLon(1.35, 103.82))
    private val work = SavedPlace(SavedPlace.WORK, LatLon(1.28, 103.85))

    @Test
    fun `存进去再读出来不变`() {
        val list = listOf(home, work, park)
        assertEquals(list, SavedPlacesJson.decode(SavedPlacesJson.encode(list)))
    }

    @Test
    fun `家和公司排最前`() {
        var list = emptyList<SavedPlace>()
        list = SavedPlacesJson.upsert(list, park)
        list = SavedPlacesJson.upsert(list, work)
        list = SavedPlacesJson.upsert(list, home)
        assertEquals(listOf(home, work, park), list)
    }

    @Test
    fun `同名的换成新位置`() {
        val moved = home.copy(location = LatLon(1.40, 103.80))
        assertEquals(listOf(moved, park), SavedPlacesJson.upsert(listOf(home, park), moved))
    }

    @Test
    fun `30 米以内算同一个地方`() {
        assertTrue(park.isAt(LatLon(1.3009, 103.9123)))
        assertFalse(park.isAt(LatLon(1.3020, 103.9122)))
    }
}
