package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuresTest {

    private fun tags(text: String) = RouteParser.parseTags(text)

    private fun route(vararg segs: Triple<Int, String, String>) = Route(
        points = (0..segs.size).map { LatLon(1.3 + it * 0.0001, 103.8) },
        distanceMeters = segs.sumOf { it.first },
        durationSeconds = 0,
        ascendMeters = 0,
        segments = segs.mapIndexed { i, (d, way, node) -> RouteSegment(d, tags(way), i + 1, tags(node)) },
    )

    @Test
    fun `步行道上的桥是天桥，公园连道和允许骑车的桥不是`() {
        assertTrue(isOverpass(tags("highway=footway bridge=yes")))
        assertTrue(isOverpass(tags("highway=steps bridge=yes")))
        assertFalse(isOverpass(tags("highway=cycleway bridge=yes lcn=yes")))
        assertFalse(isOverpass(tags("highway=footway bridge=yes bicycle=designated")))
        assertFalse(isOverpass(tags("highway=primary bridge=yes")))
    }

    @Test
    fun `地下通道不算商场里的通道`() {
        assertTrue(isUnderpass(tags("highway=footway tunnel=yes")))
        assertFalse(isUnderpass(tags("highway=footway tunnel=building_passage")))
    }

    @Test
    fun `连着的几段桥并成一座，两头的电梯算这座的`() {
        val r = route(
            Triple(100, "highway=footway", "highway=elevator"),
            Triple(40, "highway=footway bridge=yes", ""),
            Triple(44, "highway=footway bridge=yes", "highway=elevator"),
            Triple(100, "highway=footway", ""),
        )
        val list = r.structures()
        assertEquals(1, list.size)
        val s = list.single()
        assertEquals(StructureKind.OVERPASS, s.kind)
        assertEquals(84, s.lengthMeters)
        assertTrue(s.hasElevator)
        assertEquals("有电梯", s.access)
        assertEquals(3, s.path.size)
    }

    @Test
    fun `只有台阶的天桥要扛车，太短的小桥不算`() {
        val stairs = route(
            Triple(100, "highway=footway", ""),
            Triple(8, "highway=steps", ""),
            Triple(50, "highway=footway bridge=yes", ""),
            Triple(8, "highway=steps", ""),
        )
        assertEquals("只有台阶，要扛车", stairs.structures().single().access)

        val drain = route(Triple(100, "highway=footway", ""), Triple(6, "highway=footway bridge=yes", ""))
        assertTrue(drain.structures().isEmpty())
    }

    @Test
    fun `两头什么都没标的写没标明`() {
        val r = route(Triple(200, "highway=footway", ""), Triple(60, "highway=footway bridge=yes", ""), Triple(200, "highway=footway", ""))
        assertEquals("上下方式没标明", r.structures().single().access)
    }
}
