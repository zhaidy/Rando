package com.personal.rando.group

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** 对讲：开没开、灵敏度、我在不在说、谁在说 */
data class WalkieState(
    val on: Boolean = false,
    val level: VoxLevel = VoxLevel.NORMAL,
    /** 正在录我说的话 */
    val sending: Boolean = false,
    /** 正在播谁的话 */
    val playing: String? = null,
)

/**
 * 声控对讲机：不用按键。打开后一直听麦克风，听到你说话就录下来，说完停顿一下自动发给队友；
 * 收到队友的话排队自动播放，播之前「嘀」一声。播放、导航播报的时候不听，免得把喇叭里的声音又发出去。
 * 骑行的前台服务带着麦克风权限，锁屏也能用
 */
class Walkie(
    context: Context,
    private val scope: CoroutineScope,
    private val group: GroupRide,
    /** 导航语音正在播 */
    private val ttsSpeaking: () -> Boolean,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("walkie", Context.MODE_PRIVATE)
    private val audio = app.getSystemService(AudioManager::class.java)

    private val _state = MutableStateFlow(
        WalkieState(level = VoxLevel.entries.firstOrNull { it.name == prefs.getString(KEY_LEVEL, null) } ?: VoxLevel.NORMAL)
    )
    val state: StateFlow<WalkieState> = _state

    private var listenJob: Job? = null
    private val inbox = Channel<GroupEvent.Voice>(capacity = 16)
    @Volatile private var playing = false

    init {
        scope.launch { group.voices.collect { if (_state.value.on) inbox.trySend(it) } }
        scope.launch { playLoop() }
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun turnOn() {
        if (_state.value.on || !hasMicPermission()) return
        _state.update { it.copy(on = true) }
        listenJob = scope.launch(Dispatchers.Default) { listen() }
    }

    fun turnOff() {
        listenJob?.cancel()
        listenJob = null
        _state.update { it.copy(on = false, sending = false) }
    }

    fun setLevel(level: VoxLevel) {
        prefs.edit { putString(KEY_LEVEL, level.name) }
        _state.update { it.copy(level = level) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun listen() {
        val minBuffer = AudioRecord.getMinBufferSize(Vox.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // 通话用的输入：手机会开降噪、自动增益，风声车声小一些
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, Vox.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, Vox.FRAME_SAMPLES * 2 * 10),
            )
        } catch (e: Exception) {
            Log.w(TAG, "mic unavailable", e)
            turnOff()
            return
        }
        val suppressor = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
        val vox = Vox(_state.value.level)
        val clip = ArrayList<ShortArray>()
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
                // 喇叭在响（队友的话、导航播报）时不听；正在说的这段直接结束
                if (playing || ttsSpeaking()) {
                    if (vox.isTalking) finish(clip, keep = clip.size >= MIN_SEND_FRAMES)
                    vox.pause()
                    continue
                }
                for (event in vox.feed(frame.copyOf(n))) {
                    when (event) {
                        is Vox.Event.Start -> {
                            clip.clear()
                            clip.addAll(event.frames)
                            _state.update { it.copy(sending = true) }
                        }
                        is Vox.Event.Speech -> clip += event.frame
                        is Vox.Event.End -> finish(clip, event.keep)
                    }
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            suppressor?.release()
            _state.update { it.copy(sending = false) }
        }
    }

    /** 一段话说完：压缩、发出去 */
    private fun finish(clip: ArrayList<ShortArray>, keep: Boolean) {
        _state.update { it.copy(sending = false) }
        if (!keep || clip.isEmpty()) {
            clip.clear()
            return
        }
        val pcm = ShortArray(clip.sumOf { it.size })
        var i = 0
        for (f in clip) {
            f.copyInto(pcm, i)
            i += f.size
        }
        clip.clear()
        scope.launch(Dispatchers.Default) {
            try {
                val aac = AacCodec.encode(pcm)
                val ms = pcm.size * 1000 / Vox.SAMPLE_RATE
                group.sendVoice(AacCodec.FORMAT, ms, Base64.encodeToString(aac, Base64.NO_WRAP))
            } catch (e: Exception) {
                Log.w(TAG, "encode failed", e)
            }
        }
    }

    /** 队友的话一条条播，播之前嘀一声，播的时候音乐、导航先压低 */
    private suspend fun playLoop() {
        for (voice in inbox) {
            if (!_state.value.on) continue
            val file = File(app.cacheDir, "walkie-${System.nanoTime()}.aac")
            try {
                withContext(Dispatchers.IO) { file.writeBytes(Base64.decode(voice.data, Base64.DEFAULT)) }
                playing = true
                _state.update { it.copy(playing = voice.name) }
                val focus = requestFocus()
                beep()
                play(file)
                focus?.let { audio?.abandonAudioFocusRequest(it) }
            } catch (e: Exception) {
                Log.w(TAG, "play failed", e)
            } finally {
                file.delete()
                // 喇叭停了再等一下才开始听，免得录到回声
                delay(300)
                playing = false
                _state.update { it.copy(playing = null) }
            }
        }
    }

    private fun requestFocus(): AudioFocusRequest? {
        val am = audio ?: return null
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ATTRIBUTES)
            .build()
        am.requestAudioFocus(request)
        return request
    }

    private suspend fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            delay(180)
            tone.release()
        }
    }

    private suspend fun play(file: File) = suspendCancellableCoroutine { cont ->
        val player = MediaPlayer()
        val done = { _: MediaPlayer ->
            player.release()
            if (cont.isActive) cont.resume(Unit)
        }
        try {
            player.setAudioAttributes(ATTRIBUTES)
            player.setDataSource(file.path)
            player.setOnCompletionListener { done(it) }
            player.setOnErrorListener { mp, _, _ ->
                done(mp)
                true
            }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            done(player)
        }
        cont.invokeOnCancellation { runCatching { player.release() } }
    }

    private companion object {
        const val TAG = "Walkie"
        const val KEY_LEVEL = "level"
        /** 被打断时少于这么长（半秒）就不发了 */
        const val MIN_SEND_FRAMES = 25
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
