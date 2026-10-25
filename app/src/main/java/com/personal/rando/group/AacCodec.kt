package com.personal.rando.group

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 对讲的声音压成 AAC（每秒 3 KB 左右），一帧一帧带 ADTS 头，边说边发、边收边放。
 * 用手机自带的编解码器，不用另外带库
 */
object AacCodec {

    const val FORMAT = "aac-live"
    const val BIT_RATE = 24_000
    /** 一帧 AAC 是 1024 个采样，16 kHz 时是 64 毫秒 */
    const val FRAME_SAMPLES = 1024
    const val ADTS_HEADER = 7

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

    /**
     * AAC-LC 单声道的 AudioSpecificConfig（解码器的 csd-0）：5 位编码类型、4 位采样率序号、4 位声道数、3 位 0
     */
    fun audioSpecificConfig(sampleRate: Int): ByteArray {
        val profile = 2 // AAC LC
        val freq = FREQUENCIES.indexOf(sampleRate).also { require(it >= 0) { "不支持的采样率 $sampleRate" } }
        val channels = 1
        val bits = (profile shl 11) or (freq shl 7) or (channels shl 3)
        return byteArrayOf((bits shr 8).toByte(), bits.toByte())
    }

    /** 把连在一起的几个 ADTS 帧拆开；不完整的尾巴丢掉 */
    fun splitAdts(bytes: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        var i = 0
        while (i + ADTS_HEADER <= bytes.size) {
            if (bytes[i] != 0xFF.toByte() || (bytes[i + 1].toInt() and 0xF0) != 0xF0) break
            val length = ((bytes[i + 3].toInt() and 3) shl 11) or ((bytes[i + 4].toInt() and 0xFF) shl 3) or ((bytes[i + 5].toInt() and 0xFF) shr 5)
            if (length < ADTS_HEADER || i + length > bytes.size) break
            frames += bytes.copyOfRange(i, i + length)
            i += length
        }
        return frames
    }
}

/** 边说边压：喂 PCM 进去，吐出已经压好的 ADTS 帧 */
class AacStreamEncoder(private val sampleRate: Int = Vox.SAMPLE_RATE) : Closeable {

    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
        configure(
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AacCodec.BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
            },
            null, null, MediaCodec.CONFIGURE_FLAG_ENCODE,
        )
        start()
    }
    private val info = MediaCodec.BufferInfo()
    private var samples = 0L

    fun encode(pcm: ShortArray): List<ByteArray> {
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { it.asShortBuffer().put(pcm) }.array()
        var offset = 0
        val out = mutableListOf<ByteArray>()
        while (offset < bytes.size) {
            val i = codec.dequeueInputBuffer(TIMEOUT_US)
            if (i < 0) {
                drain(out, 0)
                continue
            }
            val buf = codec.getInputBuffer(i)!!
            buf.clear()
            val n = minOf(buf.remaining(), bytes.size - offset)
            buf.put(bytes, offset, n)
            codec.queueInputBuffer(i, 0, n, samples * 1_000_000 / sampleRate, 0)
            samples += n / 2
            offset += n
        }
        drain(out, 0)
        return out
    }

    /** 说完了：把编码器里剩下的都倒出来 */
    fun finish(): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        val i = codec.dequeueInputBuffer(TIMEOUT_US * 10)
        if (i >= 0) codec.queueInputBuffer(i, 0, 0, samples * 1_000_000 / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(out, TIMEOUT_US * 10)
        return out
    }

    private fun drain(out: MutableList<ByteArray>, timeoutUs: Long) {
        while (true) {
            val o = codec.dequeueOutputBuffer(info, timeoutUs)
            if (o == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (o < 0) continue // 输出格式变了之类，接着拿
            val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (!config && info.size > 0) {
                val buf = codec.getOutputBuffer(o)!!
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val frame = ByteArray(AacCodec.ADTS_HEADER + info.size)
                AacCodec.adtsHeader(frame.size, sampleRate).copyInto(frame)
                buf.get(frame, AacCodec.ADTS_HEADER, info.size)
                out += frame
            }
            codec.releaseOutputBuffer(o, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
    }

    private companion object {
        const val TIMEOUT_US = 5_000L
    }
}

/**
 * 边收边解：喂 ADTS 帧进去，吐出 PCM。
 * 去掉 ADTS 头、给解码器 csd-0 再解：有的手机的解码器不认 KEY_IS_ADTS，喂 ADTS 帧一点都解不出来
 */
class AacStreamDecoder(sampleRate: Int = Vox.SAMPLE_RATE) : Closeable {

    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
        configure(
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setByteBuffer("csd-0", ByteBuffer.wrap(AacCodec.audioSpecificConfig(sampleRate)))
            },
            null, null, 0,
        )
        start()
    }
    private val info = MediaCodec.BufferInfo()
    private var timeUs = 0L

    fun decode(frames: List<ByteArray>): ShortArray {
        val pcm = ArrayList<ShortArray>()
        for (frame in frames) {
            // 输入缓冲满了：先把解好的拿走腾地方，再等一下；不要把这一帧丢掉（一下解一整句时会丢一大半）
            var i = codec.dequeueInputBuffer(TIMEOUT_US)
            var tries = 0
            while (i < 0 && tries++ < MAX_TRIES) {
                drain(pcm, 0)
                i = codec.dequeueInputBuffer(TIMEOUT_US)
            }
            if (i >= 0 && frame.size > AacCodec.ADTS_HEADER) {
                val size = frame.size - AacCodec.ADTS_HEADER
                codec.getInputBuffer(i)!!.apply { clear(); put(frame, AacCodec.ADTS_HEADER, size) }
                codec.queueInputBuffer(i, 0, size, timeUs, 0)
                timeUs += 64_000
            }
            drain(pcm, 0)
        }
        drain(pcm, 0)
        return join(pcm)
    }

    /** 说完了：把解码器里还没吐出来的都倒出来；之后这个解码器就不能再用了 */
    fun finish(): ShortArray {
        val pcm = ArrayList<ShortArray>()
        val i = codec.dequeueInputBuffer(TIMEOUT_US * 10)
        if (i >= 0) {
            codec.queueInputBuffer(i, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(pcm, TIMEOUT_US * 10)
        }
        return join(pcm)
    }

    private fun join(pcm: List<ShortArray>): ShortArray {
        val all = ShortArray(pcm.sumOf { it.size })
        var at = 0
        for (p in pcm) {
            p.copyInto(all, at)
            at += p.size
        }
        return all
    }

    /** 有的解码器把单声道解成双声道，那就两路取平均 */
    private var channels = 1

    /** 拿走解好的；[timeoutUs] 大于 0 时一直等到解码器说完了（或者等不到新的） */
    private fun drain(out: MutableList<ShortArray>, timeoutUs: Long) {
        while (true) {
            val o = codec.dequeueOutputBuffer(info, timeoutUs)
            if (o == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                continue
            }
            if (o < 0) continue
            if (info.size > 0) {
                val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val shorts = ShortArray(info.size / 2)
                buf.asShortBuffer().get(shorts)
                out += if (channels == 2) ShortArray(shorts.size / 2) { ((shorts[it * 2] + shorts[it * 2 + 1]) / 2).toShort() } else shorts
            }
            codec.releaseOutputBuffer(o, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        /** 输入缓冲一直满着，最多等这么多次（0.2 秒） */
        const val MAX_TRIES = 20
    }
}
