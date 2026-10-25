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
import android.util.Log
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
        _state.value = VoiceState.Listening()
        retried = false
        offline = true
        if (walkie.state.value.on) {
            resumeWalkie = true
            // 等对讲真正放开麦克风再开始认，不然语音识别抢不到麦克风
            walkie.turnOff { if (_state.value is VoiceState.Listening) start() }
        } else {
            start()
        }
    }

    /** 这一次有没有重试过；先离线认，认不了（没有中文离线包）再联网认一次 */
    private var retried = false
    private var offline = true

    private fun start() {
        // 每次都新建一个：同一个识别器用过一次、出过错以后，有的手机会一直报忙
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(app).also { recognizer = it }
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        beep()
        r.startListening(intent)
    }

    fun cancel() {
        recognizer?.cancel()
        done()
    }

    private fun done() {
        _state.value = VoiceState.Idle
        recognizer?.destroy()
        recognizer = null
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
            Log.w(TAG, "recognizer error $error (offline=$offline, retried=$retried)")
            if (_state.value !is VoiceState.Listening) return
            if (!retried && retryable(error)) {
                // 没有中文离线包：换联网认；识别器忙、断开了：换一个新的再来一次
                retried = true
                if (error in LANGUAGE_ERRORS) offline = false
                Handler(Looper.getMainLooper()).postDelayed({ if (_state.value is VoiceState.Listening) start() }, 300)
                return
            }
            done()
            errorText(error)?.let(say)
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

    companion object {
        private const val TAG = "VoiceControl"
        // Android 12 以后才有的错误码；直接写数字，老系统上也能比
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
        private const val ERROR_CANNOT_CHECK_SUPPORT = 14
        private val LANGUAGE_ERRORS = setOf(ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE, ERROR_CANNOT_CHECK_SUPPORT)

        /** 换个方式再来一次可能就好了的错 */
        fun retryable(error: Int): Boolean = error in LANGUAGE_ERRORS || error in setOf(
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT, ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_AUDIO,
        )

        /** 出错了说什么：说清楚是哪儿的问题，不只说「出错了」 */
        fun errorText(error: Int): String? = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听清，再说一次"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                "语音识别要联网，或者下载中文离线语音包"
            in LANGUAGE_ERRORS -> "语音识别不支持中文，在系统设置里给语音识别下载中文语音包"
            SpeechRecognizer.ERROR_AUDIO -> "语音识别打不开麦克风，可能被别的 App 占用了"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "语音识别没有麦克风权限：在系统设置里给谷歌 App 打开麦克风权限"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, ERROR_SERVER_DISCONNECTED -> "语音识别正忙，过一会再试"
            SpeechRecognizer.ERROR_CLIENT -> "连不上手机的语音识别，在系统设置里把默认语音识别换成谷歌试试"
            else -> "语音识别出错了，错误码 $error"
        }
    }
}
