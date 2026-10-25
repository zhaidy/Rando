package com.personal.rando.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PhotoPinsTest {

    private val half = PhotoPins.SIZE / 2

    private fun overlaps(a: Pair<Float, Float>, b: Pair<Float, Float>) =
        abs(a.first - b.first) < PhotoPins.SIZE && abs(a.second - b.second) < PhotoPins.SIZE

    @Test
    fun `卡片都在地图里`() {
        val anchors = listOf(10f to 10f, 1070f to 20f, 540f to 890f, 5f to 880f)
        PhotoPins.layout(anchors, 1080f, 900f).forEach { (x, y) ->
            assertTrue(x - half >= 0f && x + half <= 1080f)
            assertTrue(y - half >= 0f && y + half <= 900f)
        }
    }

    @Test
    fun `拍在一处的几张也不叠在一起`() {
        val cards = PhotoPins.layout(listOf(540f to 500f, 545f to 505f, 550f to 500f), 1080f, 900f)
        for (i in cards.indices) for (j in i + 1 until cards.size) assertTrue("$i 和 $j 叠了", !overlaps(cards[i], cards[j]))
    }

    @Test
    fun `卡片不盖住拍照的点，空着的时候往上放`() {
        val anchor = 540f to 500f
        val (x, y) = PhotoPins.layout(listOf(anchor), 1080f, 900f).single()
        assertTrue(y < anchor.second)
        assertTrue(abs(x - anchor.first) > half || abs(y - anchor.second) > half)
    }

    @Test
    fun `旁边有空地就不压在轨迹上`() {
        // 一条横穿地图中间的路，照片拍在路上
        val road = (0..1080 step 10).map { it.toFloat() to 450f }
        val (_, y) = PhotoPins.layout(listOf(540f to 450f), 1080f, 900f, road).single()
        assertTrue(abs(y - 450f) > half)
    }
}
