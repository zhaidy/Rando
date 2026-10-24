package com.personal.rando.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class VoxTest {

    private val random = Random(7)

    /** 一帧 20 毫秒：[hz] 的正弦（模拟人声），幅度 [amplitude]（0..1），再加一点白噪声 */
    private fun frame(hz: Double, amplitude: Double, noise: Double = 0.003, start: Int = 0) =
        ShortArray(Vox.FRAME_SAMPLES) { i ->
            val t = (start + i).toDouble() / Vox.SAMPLE_RATE
            val v = amplitude * sin(2 * PI * hz * t) + noise * (random.nextDouble() * 2 - 1)
            (v * Short.MAX_VALUE).toInt().toShort()
        }

    private fun quiet() = frame(0.0, 0.0)

    private fun run(vox: Vox, frames: List<ShortArray>) = frames.flatMap { vox.feed(it) }

    @Test
    fun `安静时不开口，说一秒话开口再收口，开口前补上一小段`() {
        val vox = Vox()
        val events = run(vox, List(50) { quiet() } + List(50) { frame(800.0, 0.2, start = it * 320) } + List(60) { quiet() })
        val start = events.filterIsInstance<Vox.Event.Start>().single()
        assertTrue("开口前的一段也带上", start.frames.size > 4)
        val end = events.filterIsInstance<Vox.Event.End>().single()
        assertTrue(end.keep)
        assertFalse(vox.isTalking)
    }

    @Test
    fun `一下短促的声音不发`() {
        val vox = Vox()
        val events = run(vox, List(50) { quiet() } + List(6) { frame(1000.0, 0.3, start = it * 320) } + List(60) { quiet() })
        assertEquals(1, events.filterIsInstance<Vox.Event.Start>().size)
        assertFalse(events.filterIsInstance<Vox.Event.End>().single().keep)
    }

    @Test
    fun `低频的风声大部分被滤掉，不算说话`() {
        val vox = Vox()
        val events = run(vox, List(50) { quiet() } + List(100) { frame(30.0, 0.15, start = it * 320) })
        assertTrue(events.none { it is Vox.Event.Start })
    }

    @Test
    fun `一直有噪音时噪音底跟上去，噪音本身不算说话，比它响的话才算`() {
        val vox = Vox(VoxLevel.WINDY)
        val windy = List(150) { frame(0.0, 0.0, noise = 0.05) }
        assertTrue(run(vox, windy).none { it is Vox.Event.Start })
        val loud = run(vox, List(50) { i -> frame(900.0, 0.6, noise = 0.05, start = i * 320) })
        assertTrue(loud.any { it is Vox.Event.Start })
    }

    @Test
    fun `说得太久自动切一段`() {
        val vox = Vox(maxFrames = 100)
        val events = run(vox, List(30) { quiet() } + List(150) { frame(700.0, 0.2, start = it * 320) })
        assertTrue(events.filterIsInstance<Vox.Event.End>().isNotEmpty())
    }

    @Test
    fun `ADTS 头：AAC-LC、16 kHz、单声道、长度写对`() {
        val h = AacCodec.adtsHeader(7 + 300, 16_000)
        assertEquals(7, h.size)
        assertEquals(0xFF, h[0].toInt() and 0xFF)
        assertEquals(0xF9, h[1].toInt() and 0xFF)
        // profile LC = 1，频率序号 8（16 kHz）
        assertEquals((1 shl 6) or (8 shl 2), h[2].toInt() and 0xFF)
        val length = ((h[3].toInt() and 3) shl 11) or ((h[4].toInt() and 0xFF) shl 3) or ((h[5].toInt() and 0xFF) shr 5)
        assertEquals(307, length)
    }
}
