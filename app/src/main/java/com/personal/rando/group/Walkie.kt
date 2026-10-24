package com.personal.rando.group

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
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
enum class WalkieMode(val label: String) {
    LIVE("实时"),
    CLIP("整句"),
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
    /** 每个正在说话的队友一路播放 */
    private val players = mutableMapOf<String, Player>()

    init {
        scope.launch { group.voices.collect { if (_state.value.on) receive(it) } }
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun turnOn() {
        if (_state.value.on || !hasMicPermission()) return
        val device = route.start()
        _state.update { it.copy(on = true, device = device) }
        listenJob = scope.launch(Dispatchers.Default) { listen() }
    }

    fun turnOff() {
        if (!_state.value.on) return
        listenJob?.cancel()
        listenJob = null
        players.values.forEach { it.close() }
        players.clear()
        route.stop()
        _state.update { it.copy(on = false, sending = false, talking = emptySet(), device = null) }
    }

    fun setMode(mode: WalkieMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        _state.update { it.copy(mode = mode) }
    }

    fun setLevel(level: VoxLevel) {
        prefs.edit { putString(KEY_LEVEL, level.name) }
        _state.update { it.copy(level = level) }
    }

    // ───────────── 说 ─────────────

    @SuppressLint("MissingPermission")
    private suspend fun listen() {
        val minBuffer = AudioRecord.getMinBufferSize(Vox.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // 通话用的输入：跟着通话线路走（蓝牙耳机的麦克风），带回声消除和降噪
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, Vox.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, Vox.FRAME_SAMPLES * 2 * 10),
            )
        } catch (e: Exception) {
            Log.w(TAG, "mic unavailable", e)
            withContext(Dispatchers.Main) { turnOff() }
            return
        }
        val effects = listOfNotNull(
            if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull() else null,
            if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull() else null,
        )
        val vox = Vox(_state.value.level)
        var sender: Sender? = null
        try {
            record.startRecording()
            val frame = ShortArray(Vox.FRAME_SAMPLES)
            while (currentCoroutineContext().isActive) {
                val n = record.read(frame, 0, frame.size)
                if (n <= 0) {
                    delay(20)
                    continue
                }
                vox.level = _state.value.level
                for (event in vox.feed(frame.copyOf(n))) {
                    when (event) {
                        is Vox.Event.Start -> {
                            sender = Sender(_state.value.mode).also { s -> event.frames.forEach(s::add) }
                            _state.update { it.copy(sending = true) }
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
        } finally {
            sender?.finish()
            runCatching { record.stop() }
            record.release()
            effects.forEach { it.release() }
            _state.update { it.copy(sending = false) }
        }
    }

    /**
     * 一句话：实时的边压边发，攒够 [CHUNK_FRAMES] 帧 AAC（0.13 秒）就发一小段；
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

    // ───────────── 听 ─────────────

    private fun receive(voice: GroupEvent.Voice) {
        val player = players[voice.id]?.takeIf { it.stream == voice.stream }
            ?: Player(voice.id, voice.name, voice.stream).also { new ->
                players.put(voice.id, new)?.close()
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

        private suspend fun run() {
            _state.update { it.copy(talking = it.talking + name) }
            val decoder = runCatching { AacStreamDecoder() }.getOrNull()
            val track = newTrack()
            var buffered = 0
            var playing = false
            var first = true
            try {
                if (decoder == null) return
                while (true) {
                    val voice = withTimeoutOrNull(SILENCE_MS) { inbox.receiveCatching().getOrNull() } ?: break
                    // 整句发来的（一段就说完）先嘀一声，像对讲机
                    if (first && voice.seq == 0 && voice.end) beep()
                    first = false
                    val frames = AacCodec.splitAdts(Base64.decode(voice.data, Base64.DEFAULT))
                    val pcm = decoder.decode(frames)
                    if (pcm.isNotEmpty()) {
                        track.write(pcm, 0, pcm.size)
                        buffered += pcm.size
                        if (!playing && (buffered >= PREBUFFER_SAMPLES || voice.end)) {
                            track.play()
                            playing = true
                        }
                    }
                    if (voice.end) {
                        if (!playing) track.play()
                        // 等最后写进去的放完
                        delay(TAIL_MS)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "play failed", e)
            } finally {
                runCatching { track.stop() }
                track.release()
                decoder?.close()
                _state.update { it.copy(talking = it.talking - name) }
                withContext(Dispatchers.Main.immediate) { if (players[id] === this@Player) players.remove(id) }
            }
        }

        private suspend fun beep() {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 60)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                delay(180)
                tone.release()
            }
        }

        private fun newTrack(): AudioTrack {
            val min = AudioTrack.getMinBufferSize(Vox.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
                .setBufferSizeInBytes(maxOf(min, Vox.SAMPLE_RATE * 2)) // 一秒的缓冲
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }
    }

    private companion object {
        const val TAG = "Walkie"
        const val KEY_LEVEL = "level"
        const val KEY_MODE = "mode"
        /** 攒两帧 AAC（约 0.13 秒）发一次 */
        const val CHUNK_FRAMES = 2
        /** 先攒 0.2 秒再放 */
        const val PREBUFFER_SAMPLES = Vox.SAMPLE_RATE / 5
        /** 一秒半没收到这个人的声音就当他说完了（网络断了、最后一段丢了） */
        const val SILENCE_MS = 1_500L
        const val TAIL_MS = 400L
    }
}
