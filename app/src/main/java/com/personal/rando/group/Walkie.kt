package com.personal.rando.group

import com.personal.rando.i18n.tr
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * 对讲方式：实时是边说边发，像打电话；整句是说完一句再整句发出去，网络差的时候更稳，
 * 收到的人先「嘀」一声再放
 */
enum class WalkieMode(private val zh: String, private val en: String) {
    LIVE("实时", "Live"),
    CLIP("整句", "Clips");

    val label: String get() = tr(zh, en)
}

/** 对讲：开没开、方式、灵敏度、声音走哪儿、我在不在说、谁在说 */
data class WalkieState(
    val on: Boolean = false,
    val mode: WalkieMode = WalkieMode.LIVE,
    val level: VoxLevel = VoxLevel.NORMAL,
    /** 「蓝牙耳机 xxx」或者「手机喇叭和麦克风」 */
    val device: String? = null,
    /** 我正在说，声音正在发出去 */
    val sending: Boolean = false,
    /** 正在说话的队友 */
    val talking: Set<String> = emptySet(),
    /** 麦克风听到的音量，0–4 格；看得出麦克风在不在收音 */
    val micBars: Int = 0,
    /** 出了什么问题（麦克风打不开、编码器出错），没问题是 null */
    val problem: String? = null,
    /** 正在试：说一句回放给自己听 */
    val testing: Boolean = false,
    /** 直连上的队友有几个（蓝牙、Wi-Fi 或者 WebRTC） */
    val direct: Int = 0,
    /** 闭麦：还开着对讲、听得到队友，但自己的声音不发出去；关对讲后就不闭了 */
    val muted: Boolean = false,
)

/**
 * 声控实时对讲：不用按键，像打电话。打开后走通话线路（优先蓝牙耳机的麦克风和耳机，系统开回声消除），
 * 一直听麦克风，听到你说话就边说边发（每 0.1 秒多一小段），队友那边边收边放，延迟半秒左右；
 * 几个人可以同时说，各放各的，系统混在一起。没人说话的时候什么都不发，省流量也省服务器额度。
 * 骑行的前台服务带着麦克风类型，锁屏也能用
 */
class Walkie(
    context: Context,
    private val scope: CoroutineScope,
    private val group: GroupRide,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("walkie", Context.MODE_PRIVATE)
    private val route = AudioRoute(app) { device -> _state.update { it.copy(device = device) } }

    private val _state = MutableStateFlow(
        WalkieState(
            mode = WalkieMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: WalkieMode.LIVE,
            level = VoxLevel.entries.firstOrNull { it.name == prefs.getString(KEY_LEVEL, null) } ?: VoxLevel.NORMAL,
        )
    )
    val state: StateFlow<WalkieState> = _state

    private var listenJob: Job? = null
    /** 语音指令借走了麦克风，见 [pauseMic] */
    private var micPaused = false
    /** 每个正在说话的队友一路播放 */
    private val players = mutableMapOf<String, Player>()
    /** 每个队友上一句话的编号：新的一句开始后，旧的那句晚到的段（直连丢了、服务器那份才到）不再拿来新开一路 */
    private val retired = mutableMapOf<String, Int>()
    /** 服务器、直连各来一份，只放先到的 */
    private val dedup = VoiceDedup()

    init {
        // 队友说话：不管自己开没开对讲都放出来（开了对讲才会把自己的声音发出去）
        scope.launch { group.voices.collect { receive(it) } }
        scope.launch { group.localPeers.collect { peers -> _state.update { it.copy(direct = peers.size) } } }
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun turnOn() {
        if (_state.value.on || !hasMicPermission()) return
        val device = route.start()
        group.startLocal()
        _state.update { it.copy(on = true, device = device, problem = null) }
        listenJob = scope.launch(Dispatchers.Default) { listen() }
    }

    /**
     * 语音指令要用麦克风：只停自己收音，对讲还算开着，闭麦、直连都不动（不然每说一句指令直连都要重新打一遍）。
     * [onMicReleased] 在麦克风真正放开以后调；对讲没开着返回 false
     */
    fun pauseMic(onMicReleased: () -> Unit): Boolean {
        if (!_state.value.on || micPaused) return false
        micPaused = true
        val job = listenJob
        job?.cancel()
        listenJob = null
        route.stop()
        _state.update { it.copy(sending = false, micBars = 0, testing = false) }
        scope.launch { job?.join(); onMicReleased() }
        return true
    }

    /** 语音指令用完麦克风，接着收音；中间关了对讲就不开了 */
    fun resumeMic() {
        if (!micPaused) return
        micPaused = false
        if (!_state.value.on || !hasMicPermission()) return
        val device = route.start()
        _state.update { it.copy(device = device) }
        listenJob = scope.launch(Dispatchers.Default) { listen() }
    }

    fun turnOff() {
        if (!_state.value.on) return
        micPaused = false
        listenJob?.cancel()
        listenJob = null
        players.values.forEach { it.close() }
        players.clear()
        retired.clear()
        route.stop()
        group.stopLocal()
        _state.update { it.copy(on = false, sending = false, device = null, micBars = 0, testing = false, muted = false) }
    }

    /**
     * 试一下：录 3 秒（不管有没有听到说话），压缩、解压一遍再放给自己听，不发给队友。
     * 听得到就说明麦克风、编码、喇叭（耳机）都没问题；几乎没声音会直接说是麦克风的问题，
     * 解压不出来就放没压过的原声，说是编码的问题
     */
    fun test() {
        if (!_state.value.on) return
        _state.update { it.copy(testing = true, problem = null) }
    }

    fun setMode(mode: WalkieMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        _state.update { it.copy(mode = mode) }
    }

    /** 闭麦、开麦；说到一半闭麦，这句话就停在这里 */
    fun setMuted(muted: Boolean) {
        if (!_state.value.on) return
        _state.update { it.copy(muted = muted) }
    }

    fun setLevel(level: VoxLevel) {
        prefs.edit { putString(KEY_LEVEL, level.name) }
        _state.update { it.copy(level = level) }
    }

    // ───────────── 说 ─────────────

    @SuppressLint("MissingPermission")
    private suspend fun listen() {
        // 能降噪就按 48 kHz 录、过 RNNoise 再降到 16 kHz；原生库或 48 kHz 用不了就直接按 16 kHz 录
        var denoiser = Rnnoise.createOrNull()
        var record = denoiser?.let { openMic(Rnnoise.SAMPLE_RATE) }
        if (record == null) {
            denoiser?.close()
            denoiser = null
            record = openMic(Vox.SAMPLE_RATE)
        }
        if (record == null) {
            // turnOff 会取消正在跑的这个协程，提示要先放进去，放在后面就执行不到了
            _state.update { it.copy(problem = tr("麦克风打不开，可能被别的 App 占用了", "Can't open the mic, another app may be using it")) }
            withContext(Dispatchers.Main) { turnOff() }
            return
        }
        val effects = listOfNotNull(
            if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull() else null,
            if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull() else null,
        )
        val downsampler = Downsampler(Rnnoise.SAMPLE_RATE / Vox.SAMPLE_RATE, Rnnoise.SAMPLE_RATE)
        var vox = Vox(_state.value.level)
        var sender: Sender? = null
        var lastBars = -1
        var framesSinceBars = 0
        var test: SelfTest? = null
        var failedReads = 0
        try {
            record.startRecording()
            val frame = ShortArray(Vox.FRAME_SAMPLES)
            val raw = if (denoiser != null) ShortArray(Vox.FRAME_SAMPLES * Rnnoise.SAMPLE_RATE / Vox.SAMPLE_RATE) else frame
            while (currentCoroutineContext().isActive) {
                val got = record.read(raw, 0, raw.size, AudioRecord.READ_BLOCKING)
                if (got <= 0) {
                    // 一直读不到（麦克风被系统收走了）：说出来，不要「试一下」一直卡在录
                    if (++failedReads == READ_FAILURES) {
                        Log.w(TAG, "mic read failed: $got")
                        test?.cancel()
                        test = null
                        _state.update { it.copy(testing = false, sending = false, problem = tr("麦克风读不到声音（$got），可能被别的 App 占用了", "No sound from the mic ($got), another app may be using it")) }
                    }
                    delay(20)
                    continue
                }
                failedReads = 0
                val n = if (denoiser != null) {
                    // 偶尔读不满一帧：后面补静音，RNNoise 只收整帧
                    raw.fill(0, got)
                    denoiser.process(raw)
                    downsampler.process(raw, frame)
                } else {
                    got
                }
                vox.level = _state.value.level
                if (_state.value.testing && test == null) {
                    sender?.finish()
                    sender = null
                    test = runCatching { SelfTest() }
                        .onFailure { e ->
                            Log.w(TAG, "encoder failed", e)
                            _state.update { it.copy(testing = false, problem = tr("这台手机的声音编码出错了", "Audio encoding failed on this phone")) }
                        }
                        .getOrNull()
                    _state.update { it.copy(sending = test != null) }
                }
                // 音量格子：一秒刷新五次
                if (++framesSinceBars >= 10) {
                    framesSinceBars = 0
                    val bars = micBars(Vox.levelDb(frame.copyOf(n)))
                    if (bars != lastBars) {
                        lastBars = bars
                        _state.update { it.copy(micBars = bars) }
                    }
                }
                test?.let { t ->
                    if (t.add(frame.copyOf(n))) {
                        t.finish()
                        test = null
                    }
                }
                // 试一下时不发；放自己的声音时也不听，免得喇叭里的声音又被录进去发给队友
                if (test != null || selfPlaying) continue
                // 闭麦：正在发的这句收尾（整句的不发了），声控从头听，开麦后不会接着半句话发
                if (_state.value.muted) {
                    if (sender != null) {
                        sender?.finish(keep = false)
                        sender = null
                        _state.update { it.copy(sending = false) }
                    }
                    if (vox.isTalking) vox = Vox(_state.value.level)
                    continue
                }
                for (event in vox.feed(frame.copyOf(n))) {
                    when (event) {
                        is Vox.Event.Start -> {
                            // 编码器建不起来（个别手机）：说出来，不要悄悄地不发
                            sender = runCatching { Sender(_state.value.mode) }
                                .onFailure { e ->
                                    Log.w(TAG, "encoder failed", e)
                                    _state.update { it.copy(problem = tr("这台手机的声音编码出错了", "Audio encoding failed on this phone")) }
                                }
                                .getOrNull()
                                ?.also { s -> event.frames.forEach(s::add) }
                            _state.update { it.copy(sending = sender != null) }
                        }
                        is Vox.Event.Speech -> sender?.add(event.frame)
                        is Vox.Event.End -> {
                            // 太短的（一阵风、一声喇叭）：整句的就不发了；实时的已经发出去，只是收个尾
                            sender?.finish(keep = event.keep)
                            sender = null
                            _state.update { it.copy(sending = false) }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 录着录着编码器、麦克风被系统收走了：关掉对讲说一声，不要让整个 App 闪退
            Log.w(TAG, "listen failed", e)
            _state.update { it.copy(problem = tr("对讲出错了，已经关掉，可以再开一次", "Walkie-talkie hit an error and was turned off, try turning it on again")) }
            withContext(Dispatchers.Main) { turnOff() }
        } finally {
            runCatching { sender?.finish() }
            runCatching { test?.cancel() }
            runCatching { record.stop() }
            record.release()
            effects.forEach { it.release() }
            denoiser?.close()
            _state.update { it.copy(sending = false, micBars = 0) }
        }
    }

    /** 通话用的输入：跟着通话线路走（蓝牙耳机的麦克风），带回声消除和降噪；打不开返回 null */
    @SuppressLint("MissingPermission")
    private fun openMic(sampleRate: Int): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, sampleRate / 50 * 2 * 10),
            )
        } catch (e: Exception) {
            Log.w(TAG, "mic unavailable at $sampleRate Hz", e)
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "mic not initialized at $sampleRate Hz")
            record.release()
            return null
        }
        return record
    }

    /**
     * 一句话：实时的边压边发，攒够 [CHUNK_FRAMES] 帧 AAC（64 毫秒）就发一小段；
     * 整句的压好攒着，说完一起发（只有一段，seq 0，end）
     */
    private inner class Sender(private val mode: WalkieMode) {
        private val stream = Random.nextInt(1, Int.MAX_VALUE)
        private val encoder = AacStreamEncoder()
        private val pending = mutableListOf<ByteArray>()
        private var seq = 0

        fun add(pcm: ShortArray) {
            pending += encoder.encode(pcm)
            if (mode == WalkieMode.LIVE && pending.size >= CHUNK_FRAMES) send(end = false)
        }

        fun finish(keep: Boolean = true) {
            runCatching { pending += encoder.finish() }
            if (mode == WalkieMode.LIVE || keep) send(end = true)
            encoder.close()
        }

        private fun send(end: Boolean) {
            val bytes = pending.fold(ByteArray(0)) { acc, f -> acc + f }
            pending.clear()
            group.sendVoice(stream, seq++, end, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
    }

    /** 放自己的声音（试一下）的时候不收音 */
    @Volatile private var selfPlaying = false

    /** 试一下：录满 [TEST_SAMPLES] 再放 */
    private inner class SelfTest {
        private val encoder = AacStreamEncoder()
        private val chunks = mutableListOf<ByteArray>()
        private val raw = mutableListOf<ShortArray>()
        private var samples = 0
        private var loudest = Double.NEGATIVE_INFINITY

        /** 录够了返回 true */
        fun add(pcm: ShortArray): Boolean {
            chunks += encoder.encode(pcm)
            raw += pcm
            samples += pcm.size
            loudest = maxOf(loudest, Vox.levelDb(pcm))
            return samples >= TEST_SAMPLES
        }

        fun finish() {
            runCatching { chunks += encoder.finish() }
            encoder.close()
            // 跟队友那边一样解压一遍；解不出来大半，就放原声，免得什么都听不到
            val frames = AacCodec.splitAdts(chunks.fold(ByteArray(0)) { acc, c -> acc + c })
            val decoded = runCatching { AacStreamDecoder().use { it.decode(frames) + it.finish() } }
                .onFailure { e -> Log.w(TAG, "decoder failed", e) }
                .getOrDefault(ShortArray(0))
            val codecOk = decoded.size >= samples * 3 / 4
            Log.i(TAG, "self test: ${raw.size} reads, $samples samples, ${frames.size} aac frames, ${decoded.size} decoded, loudest $loudest dB")
            val problem = when {
                loudest < QUIET_DB -> tr("麦克风几乎没收到声音：看看是不是被别的 App 占用了，或者麦克风被手、壳挡住了", "The mic picked up almost nothing: check whether another app is using it, or a hand or case is covering it")
                !codecOk -> {
                    val percent = decoded.size * 100 / samples.coerceAtLeast(1)
                    tr("这台手机解不开压缩的声音（${frames.size} 帧解出 $percent%），放的是原声，可能听不到队友", "This phone can't decode compressed audio (${frames.size} frames, $percent% decoded). Playing the raw recording; you may not hear teammates")
                }
                callVolume() == 0 -> tr("通话音量是 0：说话的时候按音量加大键", "Call volume is 0: press volume up while talking")
                else -> null
            }
            _state.update { it.copy(testing = false, sending = false, problem = problem) }
            playSelf(if (codecOk) decoded else raw.fold(ShortArray(0)) { acc, c -> acc + c })
        }

        fun cancel() {
            runCatching { encoder.close() }
            _state.update { it.copy(testing = false) }
        }
    }

    /** dBFS → 0–4 格：-55 以下没声音，-20 以上满格 */
    private fun micBars(db: Double): Int = when {
        db < -55 -> 0
        db < -45 -> 1
        db < -35 -> 2
        db < -25 -> 3
        else -> 4
    }

    // ───────────── 听 ─────────────

    private fun receive(voice: GroupEvent.Voice) {
        if (!dedup.accept(voice.id, voice.stream, voice.seq)) return
        if (retired[voice.id] == voice.stream) return
        val player = players[voice.id]?.takeIf { it.stream == voice.stream }
            ?: Player(voice.id, voice.name, voice.stream).also { new ->
                // 同一个人接着说下一句（一句说太长会被切开）：上一句不掐掉，让它把攒着的放完
                players.put(voice.id, new)?.let { old ->
                    retired[voice.id] = old.stream
                    old.finish()
                }
            }
        player.offer(voice)
    }

    /**
     * 一个队友的一句话：解码后写进一路通话音轨。先攒 0.2 秒再开始放，网络抖一下也不断；
     * 说完或者一秒多没新数据就收掉
     */
    private inner class Player(private val id: String, private val name: String, val stream: Int) {
        private val inbox = Channel<GroupEvent.Voice>(Channel.UNLIMITED)
        private val job = scope.launch(Dispatchers.Default) { run() }

        fun offer(voice: GroupEvent.Voice) {
            inbox.trySend(voice)
        }

        fun close() {
            inbox.close()
            job.cancel()
        }

        /** 不会再有新的段了：排着的放完再收 */
        fun finish() {
            inbox.close()
        }

        private suspend fun run() {
            talking(name, +1)
            val decoder = runCatching { AacStreamDecoder() }.getOrNull()
            // 同时好几个人在说、正在打电话时可能开不出音轨
            val track = runCatching { newTrack() }.getOrNull()
            // 开始放之前收到的先攒着：没在放的时候往音轨里写，缓冲（一秒）满了后面的会被丢掉
            val waiting = mutableListOf<ShortArray>()
            var buffered = 0
            var written = 0L
            var playing = false
            var first = true
            try {
                if (decoder == null) {
                    _state.update { it.copy(problem = tr("这台手机的声音解码出错了，听不到队友", "Audio decoding failed on this phone, you won't hear teammates")) }
                    return
                }
                if (track == null) {
                    _state.update { it.copy(problem = tr("喇叭打不开，放不了声音", "Can't open the speaker, no sound can play")) }
                    return
                }
                // 直连不保证顺序：按段号排好再解码，缺的那段等一小会儿（服务器那份可能还在路上），等不到就跳过
                val pending = sortedMapOf<Int, GroupEvent.Voice>()
                var next = 0
                var done = false
                while (!done) {
                    val got = withTimeoutOrNull(if (pending.isEmpty()) SILENCE_MS else REORDER_WAIT_MS) { inbox.receiveCatching() }
                    // 没等到，或者已经开始说下一句了（不会再来了）：等着前面段的这几段直接接着放
                    val incoming = got?.getOrNull()
                    if (incoming == null) {
                        if (pending.isEmpty()) break
                        next = pending.firstKey()
                    } else {
                        if (incoming.seq < next) continue
                        pending[incoming.seq] = incoming
                        if (pending.size > REORDER_MAX) next = pending.firstKey()
                    }
                    while (!done && pending.isNotEmpty() && pending.firstKey() == next) {
                        val voice = pending.remove(next)!!
                        next++
                        // 整句发来的（一段就说完）先嘀一声，像对讲机
                        if (first && voice.seq == 0 && voice.end) beep()
                        first = false
                        val frames = AacCodec.splitAdts(Base64.decode(voice.data, Base64.DEFAULT))
                        // 最后一段：把解码器里剩下的也倒出来，句尾不丢
                        val pcm = decoder.decode(frames).let { if (voice.end) it + decoder.finish() else it }
                        if (pcm.isNotEmpty()) {
                            waiting += pcm
                            buffered += pcm.size
                        }
                        if (!playing && (buffered >= PREBUFFER_SAMPLES || voice.end)) {
                            track.play()
                            playing = true
                        }
                        if (playing) {
                            // 在放的时候写，缓冲满了会等着放出去一些再写，一句长话也不会丢。
                            // 网络卡了一下、攒着的一下子全到：音轨里排着的超过 MAX_LAG_SAMPLES 就丢掉这段，
                            // 不然这句话后面每个字都慢那么多
                            for (chunk in waiting) {
                                if (written - played(track) > MAX_LAG_SAMPLES) continue
                                written += track.write(chunk, 0, chunk.size).coerceAtLeast(0)
                            }
                            waiting.clear()
                        }
                        if (voice.end) done = true
                    }
                }
                // 说完了，或者一秒多没新数据（最后一段丢了）：攒着没放的也放掉，等放完再关
                if (!playing && waiting.isNotEmpty()) track.play()
                for (chunk in waiting) written += track.write(chunk, 0, chunk.size).coerceAtLeast(0)
                drain(track, written)
            } catch (e: Exception) {
                Log.w(TAG, "play failed", e)
            } finally {
                track?.let { runCatching { it.stop() }; it.release() }
                decoder?.close()
                talking(name, -1)
                withContext(Dispatchers.Main.immediate) { if (players[id] === this@Player) players.remove(id) }
            }
        }

        private suspend fun beep() {
            runCatching {
                val tone = ToneGenerator(if (_state.value.on) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC, 60)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                delay(180)
                tone.release()
            }
        }
    }

    /**
     * 等写进去的都放完再关；最多多等一秒。
     * 先 stop()：流式音轨要等缓冲（一秒）写满才开始放，一句不到一秒的话一直不放，
     * 以前等完就直接 release，什么都听不到；stop() 以后系统会把缓冲里剩下的放完
     */
    private suspend fun drain(track: AudioTrack, written: Long) {
        runCatching { track.stop() }
        val limit = System.currentTimeMillis() + written * 1000 / Vox.SAMPLE_RATE + 1_000
        while (System.currentTimeMillis() < limit && played(track) < written) delay(50)
        delay(TAIL_MS)
    }

    /** 已经放出去的采样数 */
    private fun played(track: AudioTrack): Long = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    private fun newTrack(): AudioTrack {
        val min = AudioTrack.getMinBufferSize(Vox.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // 开着对讲走通话线路（蓝牙耳机）；没开对讲时像导航播报一样放（蓝牙耳机或者喇叭），不会跑到听筒去
        val usage = if (_state.value.on) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Vox.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            // Android 12 起能设攒够 0.2 秒就开始放，缓冲给一秒；之前的版本要等缓冲写满才开始放，
            // 一秒的缓冲就是多等一秒，所以只给 0.2 秒
            .setBufferSizeInBytes(maxOf(min, if (Build.VERSION.SDK_INT >= 31) Vox.SAMPLE_RATE * 2 else PREBUFFER_SAMPLES * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { track ->
                if (Build.VERSION.SDK_INT >= 31) runCatching { track.startThresholdInFrames = PREBUFFER_SAMPLES }
            }
    }

    /**
     * 每个名字有几路在放：同一个人接着说下一句时，新的一路先开始、上一句才放完收掉，
     * 直接按名字加减会把正在说的也减没了（同名的队友也一样）
     */
    private val talkers = mutableMapOf<String, Int>()

    private fun talking(name: String, delta: Int) = synchronized(talkers) {
        val n = (talkers[name] ?: 0) + delta
        if (n > 0) talkers[name] = n else talkers.remove(name)
        _state.update { it.copy(talking = talkers.keys.toSet()) }
    }

    /** 试一下：把录到的放给自己听；放的时候不收音 */
    private fun playSelf(pcm: ShortArray) {
        selfPlaying = true
        scope.launch(Dispatchers.Default) {
            talking(SELF_NAME, +1)
            val track = runCatching { newTrack() }.getOrNull()
            try {
                if (track == null) {
                    _state.update { it.copy(problem = tr("喇叭打不开，放不了声音", "Can't open the speaker, no sound can play")) }
                    return@launch
                }
                track.play()
                val written = track.write(pcm, 0, pcm.size).coerceAtLeast(0).toLong()
                drain(track, written)
            } catch (e: Exception) {
                Log.w(TAG, "self play failed", e)
            } finally {
                track?.let { runCatching { it.stop() }; it.release() }
                talking(SELF_NAME, -1)
                selfPlaying = false
            }
        }
    }

    private fun callVolume(): Int =
        app.getSystemService(AudioManager::class.java)?.getStreamVolume(AudioManager.STREAM_VOICE_CALL) ?: -1

    private companion object {
        const val TAG = "Walkie"
        /** 试一下时「谁在说」里显示的 */
        val SELF_NAME: String get() = tr("你自己", "You")
        /** 连着这么多次读不到麦克风（一秒）就说麦克风有问题 */
        const val READ_FAILURES = 50
        const val KEY_LEVEL = "level"
        const val KEY_MODE = "mode"
        /** 一帧 AAC（64 毫秒）发一次：直连是 UDP，小包多发不吃亏，少攒一帧就少等一帧 */
        const val CHUNK_FRAMES = 1
        /** 先攒 0.2 秒再放 */
        const val PREBUFFER_SAMPLES = Vox.SAMPLE_RATE / 5
        /** 音轨里排着超过 0.6 秒没放的就丢掉新来的，追上来 */
        const val MAX_LAG_SAMPLES = Vox.SAMPLE_RATE * 3 / 5
        /** 一秒半没收到这个人的声音就当他说完了（网络断了、最后一段丢了） */
        const val SILENCE_MS = 1_500L
        /** 缺了一段：最多等这么久、或者后面攒了这么多段，就不等了 */
        const val REORDER_WAIT_MS = 150L
        const val REORDER_MAX = 3
        const val TAIL_MS = 200L
        /** 试一下录 3 秒 */
        const val TEST_SAMPLES = Vox.SAMPLE_RATE * 3
        /** 3 秒里最响的一下都不到这么响（dBFS）：麦克风几乎没收到声音 */
        const val QUIET_DB = -50.0
    }
}
