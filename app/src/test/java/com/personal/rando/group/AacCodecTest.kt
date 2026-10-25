package com.personal.rando.group

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AacCodecTest {

    @Test
    fun `解码器的 csd-0：AAC-LC、16 kHz、单声道`() {
        assertArrayEquals(byteArrayOf(0x14, 0x08), AacCodec.audioSpecificConfig(16_000))
        assertArrayEquals(byteArrayOf(0x12, 0x08), AacCodec.audioSpecificConfig(44_100))
    }

    @Test
    fun `ADTS 帧拆开后长度对得上`() {
        val a = AacCodec.adtsHeader(7 + 3, 16_000) + byteArrayOf(1, 2, 3)
        val b = AacCodec.adtsHeader(7 + 5, 16_000) + byteArrayOf(4, 5, 6, 7, 8)
        val frames = AacCodec.splitAdts(a + b)
        assertEquals(2, frames.size)
        assertArrayEquals(b, frames[1])
    }
}
