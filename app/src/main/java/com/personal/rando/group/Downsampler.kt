package com.personal.rando.group

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 降采样：每 [factor] 个样本留一个（48 kHz → 16 kHz）。先过一个低通（截止 [cutoffHz]），
 * 免得高频折回来变成刺耳的杂音。前后两段之间接得上，一段段喂进来和整段一次处理结果一样。
 */
class Downsampler(
    private val factor: Int = 3,
    inputRate: Int = 48_000,
    cutoffHz: Double = 7_000.0,
    taps: Int = 47,
) {
    /** 加汉明窗的 sinc 低通，系数加起来是 1（低频音量不变） */
    private val h: DoubleArray = run {
        val fc = cutoffHz / inputRate
        val mid = (taps - 1) / 2.0
        val raw = DoubleArray(taps) { i ->
            val x = i - mid
            val sinc = if (x == 0.0) 2 * fc else sin(2 * PI * fc * x) / (PI * x)
            sinc * (0.54 - 0.46 * cos(2 * PI * i / (taps - 1)))
        }
        val sum = raw.sum()
        DoubleArray(taps) { raw[it] / sum }
    }

    /** 上一段最后 taps-1 个样本，接着这一段一起算 */
    private var line = DoubleArray(taps - 1)

    /** [input] 的长度要是 [factor] 的整数倍；结果写进 [output]，返回写了多少个 */
    fun process(input: ShortArray, output: ShortArray): Int {
        require(input.size % factor == 0) { "${input.size} samples" }
        val count = input.size / factor
        require(output.size >= count) { "output too small" }
        val history = h.size - 1
        if (line.size != history + input.size) line = line.copyOf(history + input.size)
        for (i in input.indices) line[history + i] = input[i].toDouble()
        for (k in 0 until count) {
            val p = history + k * factor
            var sum = 0.0
            for (j in h.indices) sum += h[j] * line[p - j]
            output[k] = sum.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
        }
        line.copyInto(line, 0, input.size, input.size + history)
        return count
    }
}
