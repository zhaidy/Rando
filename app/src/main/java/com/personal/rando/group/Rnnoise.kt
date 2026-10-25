package com.personal.rando.group

import android.util.Log
import java.io.Closeable

/**
 * RNNoise 降噪（原生库，见 src/main/cpp）：小神经网络听出哪些是人声，把风声、路噪这些压下去。
 * 只认 48 kHz，一次处理 480 个样本（10 毫秒）。原生库加载不了时 [createOrNull] 返回 null，退回不降噪。
 */
class Rnnoise private constructor(private var state: Long) : Closeable {
    private val buffer = FloatArray(FRAME_SAMPLES)

    /** 就地降噪一段 48 kHz 的声音，长度要是 [FRAME_SAMPLES] 的整数倍 */
    fun process(pcm: ShortArray) {
        check(state != 0L) { "closed" }
        require(pcm.size % FRAME_SAMPLES == 0) { "${pcm.size} samples" }
        for (start in pcm.indices step FRAME_SAMPLES) {
            for (i in 0 until FRAME_SAMPLES) buffer[i] = pcm[start + i].toFloat()
            nativeProcess(state, buffer)
            for (i in 0 until FRAME_SAMPLES) {
                pcm[start + i] = buffer[i].coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            }
        }
    }

    override fun close() {
        if (state != 0L) nativeDestroy(state)
        state = 0L
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val FRAME_SAMPLES = 480
        private const val TAG = "Rnnoise"

        private val loaded by lazy {
            runCatching { System.loadLibrary("rnnoise_jni") }
                .onFailure { Log.w(TAG, "native library unavailable", it) }
                .isSuccess
        }

        fun createOrNull(): Rnnoise? = if (loaded) nativeCreate().takeIf { it != 0L }?.let(::Rnnoise) else null

        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(state: Long)
        @JvmStatic private external fun nativeProcess(state: Long, frame: FloatArray): Float
    }
}
