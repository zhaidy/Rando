package com.personal.rando.group

import com.personal.rando.i18n.tr
import kotlin.math.log10
import kotlin.math.sqrt

/** 声控灵敏度：比背景噪音（风声、车声）高出多少分贝才算在说话 */
enum class VoxLevel(private val zh: String, private val en: String, val marginDb: Double) {
    SENSITIVE("灵敏", "Sensitive", 9.0),
    NORMAL("一般", "Normal", 13.0),
    WINDY("风大", "Windy", 18.0);

    val label: String get() = tr(zh, en)
}

/**
 * 声控：一帧帧（20 毫秒）听麦克风，判断什么时候开始说话、什么时候说完。
 *
 * 背景噪音一直在变（骑快了风声大），所以跟踪一个「噪音底」：安静时它很快降下来，
 * 有声音时只慢慢往上爬，说话那几秒抬不了多少。比噪音底高出 [VoxLevel.marginDb] 就算有人声。
 * 连着几帧都是人声才开口（一阵风吹一下不算），停下来 [hangoverFrames] 帧才算说完，
 * 前面再补上开口前的一小段，免得第一个字被吃掉。
 * 先过一个高通滤波，把风声、路噪这些低频去掉一大半再算音量
 */
class Vox(
    var level: VoxLevel = VoxLevel.NORMAL,
    private val startFrames: Int = 4,
    private val hangoverFrames: Int = 40,
    /** 开口前补 0.2 秒：补的越多，队友听到的整句话就越晚 */
    private val prerollFrames: Int = 10,
    /** 有人声的帧少于这么多（0.3 秒）就当是杂音 */
    private val minFrames: Int = 15,
    private val maxFrames: Int = 1000,
) {
    sealed interface Event {
        /** 开始说话：[frames] 是开口前补上的那一小段加上已经判断为人声的几帧 */
        data class Start(val frames: List<ShortArray>) : Event
        data class Speech(val frame: ShortArray) : Event
        /** 说完了；[keep] 为 false 时太短（多半是一阵风、一声喇叭），这段不要发 */
        data class End(val keep: Boolean) : Event
    }

    /** 噪音底（dBFS） */
    var floorDb = -60.0
        private set

    private val preroll = ArrayDeque<ShortArray>()
    private var loud = 0
    private var quiet = 0
    private var talking = false
    private var length = 0
    /** 这一段里真正有人声的帧数，用来判断是不是太短 */
    private var voicedFrames = 0
    private var frames = 0
    private val hp = doubleArrayOf(0.0, 0.0, 0.0, 0.0)

    val isTalking: Boolean get() = talking

    /** 喂一帧 16 位 PCM，返回这帧引起的事件 */
    fun feed(raw: ShortArray): List<Event> {
        val frame = highPass(raw)
        val db = levelDb(frame)
        // 刚打开的半秒只用来听听背景有多吵
        if (frames++ < WARMUP_FRAMES) {
            floorDb = if (frames == 1) db else floorDb + (db - floorDb) / frames
            return emptyList()
        }
        val voiced = db > floorDb + level.marginDb && db > MIN_SPEECH_DB
        trackFloor(db)

        if (!talking) {
            preroll.addLast(frame)
            while (preroll.size > prerollFrames + startFrames) preroll.removeFirst()
            loud = if (voiced) loud + 1 else 0
            if (loud >= startFrames) {
                talking = true
                quiet = 0
                length = preroll.size
                voicedFrames = loud
                val start = Event.Start(preroll.toList())
                preroll.clear()
                return listOf(start)
            }
            return emptyList()
        }

        length++
        if (voiced) voicedFrames++
        quiet = if (voiced) 0 else quiet + 1
        val events = mutableListOf<Event>(Event.Speech(frame))
        if (quiet >= hangoverFrames || length >= maxFrames) {
            talking = false
            loud = 0
            events += Event.End(keep = voicedFrames >= minFrames)
        }
        return events
    }

    /** 播队友的话、导航播报时先别听，免得把喇叭里的声音又发出去 */
    fun pause() {
        preroll.clear()
        loud = 0
    }

    /**
     * 噪音底：变安静时很快跟下来；变吵时没在说话就一秒左右跟上去（骑快了风大），
     * 在说话时只慢慢跟（几秒），一段话说不完就把自己的声音当成噪音
     */
    private fun trackFloor(db: Double) {
        val rate = when {
            db < floorDb -> 0.3
            !talking -> 0.02
            else -> 0.003
        }
        floorDb += (db - floorDb) * rate
    }

    /** 两个一阶高通串起来，截止大约 300 Hz（16 kHz 采样），风声、路噪的低频压得更狠 */
    private fun highPass(input: ShortArray): ShortArray {
        val out = ShortArray(input.size)
        for (i in input.indices) {
            var x = input[i].toDouble()
            for (stage in 0..1) {
                val y = HP_ALPHA * (hp[stage * 2 + 1] + x - hp[stage * 2])
                hp[stage * 2] = x
                hp[stage * 2 + 1] = y
                x = y
            }
            out[i] = x.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
        }
        return out
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = SAMPLE_RATE / 50
        /** 再小的声音不管多突出都不算说话（手机放兜里、蹭一下） */
        const val MIN_SPEECH_DB = -45.0
        private const val HP_ALPHA = 0.895
        private const val WARMUP_FRAMES = 25

        fun levelDb(frame: ShortArray): Double {
            if (frame.isEmpty()) return -100.0
            var sum = 0.0
            for (s in frame) sum += s.toDouble() * s
            val rms = sqrt(sum / frame.size) / Short.MAX_VALUE
            return if (rms <= 1e-5) -100.0 else 20 * log10(rms)
        }
    }
}
