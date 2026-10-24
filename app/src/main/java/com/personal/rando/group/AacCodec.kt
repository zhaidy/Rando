package com.personal.rando.group

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 对讲录音压成 AAC（每秒 3 KB 左右），带 ADTS 头，收到的一方直接存成 .aac 文件就能播。
 * 用手机自带的硬件/系统编码器，不用另外带库
 */
object AacCodec {

    const val FORMAT = "aac"
    private const val BIT_RATE = 24_000
    private const val TIMEOUT_US = 10_000L

    fun encode(pcm: ShortArray, sampleRate: Int = Vox.SAMPLE_RATE): ByteArray {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val input = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { it.asShortBuffer().put(pcm) }.array()
            val out = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var offset = 0
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        buf.clear()
                        val n = minOf(buf.remaining(), input.size - offset)
                        val timeUs = offset / 2 * 1_000_000L / sampleRate
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.put(input, offset, n)
                            codec.queueInputBuffer(i, 0, n, timeUs, 0)
                            offset += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (o >= 0) {
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!config && info.size > 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val frame = ByteArray(info.size)
                        buf.get(frame)
                        out.write(adtsHeader(frame.size + ADTS_HEADER, sampleRate))
                        out.write(frame)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            return out.toByteArray()
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private const val ADTS_HEADER = 7
    private val FREQUENCIES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000)

    /** ADTS 帧头：AAC-LC、单声道、没有 CRC；[length] 包括这 7 个字节 */
    fun adtsHeader(length: Int, sampleRate: Int): ByteArray {
        val profile = 2 // AAC LC
        val freq = FREQUENCIES.indexOf(sampleRate).also { require(it >= 0) { "不支持的采样率 $sampleRate" } }
        val channels = 1
        return byteArrayOf(
            0xFF.toByte(),
            0xF9.toByte(),
            (((profile - 1) shl 6) or (freq shl 2) or (channels shr 2)).toByte(),
            (((channels and 3) shl 6) or (length shr 11)).toByte(),
            ((length and 0x7FF) shr 3).toByte(),
            (((length and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }
}
