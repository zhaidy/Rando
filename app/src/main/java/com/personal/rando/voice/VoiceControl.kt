package com.personal.rando.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.personal.rando.group.Walkie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 语音指令现在在干什么：没事、在听（听到一半的字）、刚听完（听到了什么） */
sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val partial: String = "") : VoiceState
}

/**
 * 语音指令：点麦克风按钮或者按耳机上的键，「嘀」一声后说话，听完交给 [handler] 去做。
 * 用手机自带的语音识别（有离线包就离线认）；开着对讲时先停一下对讲的麦克风，听完再开
 */
class VoiceControl(context: Context, private val walkie: Walkie) {

    private val app = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var resumeWalkie = false

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state

    /** 听懂了一句话要做什么；界面打开时设上 */
    var handler: ((VoiceCommand) -> Unit)? = null

    /** 没听清、没装语音识别时说一声 */
    var say: (String) -> Unit = {}

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(app)

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** 开始听；已经在听就停下 */
    fun listen() {
        if (_state.value is VoiceState.Listening) {
            cancel()
            return
        }
        if (!available) {
            say("手机上没有语音识别，装一个谷歌语音输入就能用")
            return
        }
        if (!hasMicPermission()) {
            say("先在 Rando 里允许使用麦克风")
            return
        }
        if (walkie.state.value.on) {
            walkie.turnOff()
            resumeWalkie = true
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(app).also { recognizer = it }
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        beep()
        _state.value = VoiceState.Listening()
        r.startListening(intent)
    }

    fun cancel() {
        recognizer?.cancel()
        done()
    }

    private fun done() {
        _state.value = VoiceState.Idle
        if (resumeWalkie) {
            resumeWalkie = false
            walkie.turnOn()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle) {
            val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            _state.value = VoiceState.Listening(text)
        }

        override fun onResults(results: Bundle) {
            val candidates = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            done()
            // 几个候选里取第一个听得懂的
            val command = candidates.map(VoiceParser::parse).firstOrNull { it !is VoiceCommand.Unknown }
                ?: VoiceCommand.Unknown(candidates.firstOrNull().orEmpty())
            handler?.invoke(command) ?: say("打开 Rando 再说")
        }

        override fun onError(error: Int) {
            done()
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> say("没听清，再说一次")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    say("语音识别要联网，或者下载中文离线语音包")
                SpeechRecognizer.ERROR_CLIENT -> Unit
                else -> say("语音识别出错了")
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 300)
        }
    }
}
