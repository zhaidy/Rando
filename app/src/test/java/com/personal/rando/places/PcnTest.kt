package com.personal.rando.places

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PcnTest {

    private val loops = PcnData.parse(File("src/main/assets/pcn.json").readText())

    @Test
    fun `六个大环，全岛三百多公里`() {
        assertEquals(6, loops.size)
        assertTrue(loops.all { it.zh.endsWith("环线") })
        val total = PcnCoverage.of(loops, emptyList()).totalMeters
        assertTrue("$total", total in 300_000.0..380_000.0)
    }

    /** 一条往东 1.1 公里的直线连道 */
    private val line = PcnLoop("Eastern Coastal Loop", listOf((0..10).map { LatLon(1.3, 103.9 + it * 0.001) }))

    @Test
    fun `骑过一半就点亮一半`() {
        // 沿着连道骑了前一半，GPS 偏了 8 米
        val track = (0..5).map { LatLon(1.30007, 103.9 + it * 0.001) }
        val p = PcnCoverage.of(listOf(line), listOf(track))
        assertEquals(0.5, p.share, 0.05)
        assertTrue(p.pieces.any { it.ridden } && p.pieces.any { !it.ridden })
        assertEquals("东部海岸环线", p.loops[0].loop.zh)
    }

    @Test
    fun `没骑过、旁边一条街骑过都不算`() {
        assertEquals(0.0, PcnCoverage.of(listOf(line), emptyList()).share, 0.0)
        val street = (0..10).map { LatLon(1.3008, 103.9 + it * 0.001) }
        assertEquals(0.0, PcnCoverage.of(listOf(line), listOf(street)).share, 0.0)
    }
}
