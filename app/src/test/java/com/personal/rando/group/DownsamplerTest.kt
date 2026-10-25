package com.personal.rando.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DownsamplerTest {

    private fun tone(hz: Double, samples: Int, start: Int = 0) = ShortArray(samples) { i ->
        (0.5 * Short.MAX_VALUE * sin(2 * PI * hz * (start + i) / 48_000)).toInt().toShort()
    }

    /** 跳过开头滤波器还没填满的一小段再算音量 */
    private fun levelDb(out: ShortArray) = Vox.levelDb(out.copyOfRange(40, out.size))

    private fun downsample(input: ShortArray): ShortArray {
        val out = ShortArray(input.size / 3)
        Downsampler().process(input, out)
        return out
    }

    @Test
    fun `人声频段的音量不变`() {
        val db = levelDb(downsample(tone(1_000.0, 9_600)))
        // 0.5 幅度的正弦是 -9 dBFS
        assertEquals(-9.0, db, 0.5)
    }

    @Test
    fun `16 kHz 放不下的高频被滤掉，不会折回来`() {
        val db = levelDb(downsample(tone(12_000.0, 9_600)))
        assertTrue("12 kHz 还剩 $db dB", db < -50)
    }

    @Test
    fun `一段段喂和一次喂结果一样`() {
        val input = tone(700.0, 960 * 5)
        val whole = downsample(input)
        val d = Downsampler()
        val pieces = (0 until 5).flatMap { k ->
            val out = ShortArray(320)
            d.process(input.copyOfRange(k * 960, (k + 1) * 960), out)
            out.toList()
        }
        assertEquals(whole.toList(), pieces)
    }
}
