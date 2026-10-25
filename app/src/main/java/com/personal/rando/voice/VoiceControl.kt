package com.personal.rando.voice

import android.Manifest
import android.content.ComponentName
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
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.personal.rando.group.Walkie
import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 语音指令现在在干什么：没事、在听（听到一半的字）、刚听完（听到了什么） */
sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val partial: String = "") : VoiceState
}

/**
 * 语音指令：点麦克风按钮或者按耳机上的键，「嘀」一声后说话，听完交给 [handler] 去做。
 * 用手机自带的语音识别（有离线包就离线认）；开着对讲时先停一下对讲的麦克风，听完再开。
 * 默认的识别服务一直忙、连不上时，换手机上装的别的识别服务（谷歌 App 的优先），都不行再弹系统的语音输入框
 */
class VoiceControl(context: Context, private val walkie: Walkie) {

    private val app = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var resumeWalkie = false
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state

    /** 听懂了一句话要做什么；界面打开时设上 */
    var handler: ((VoiceCommand) -> Unit)? = null

    /** 没听清、没装语音识别时说一声 */
    var say: (String) -> Unit = {}

    /** 弹系统的语音输入框（识别服务都用不了时）；界面打开时设上，弹出来了返回 true，结果交给 [dialogResult] */
    var dialog: ((Intent) -> Boolean)? = null

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
            say(tr("手机上没有语音识别，装一个谷歌语音输入就能用", "No speech recognition on this phone. Install Google voice typing to use it"))
            return
        }
        if (!hasMicPermission()) {
            say(tr("先在 Rando 里允许使用麦克风", "Allow Rando to use the microphone first"))
            return
        }
        _state.value = VoiceState.Listening()
        services = order(installedServices()) { it.packageName }
        attempt = 0
        offline = true
        if (walkie.state.value.on) {
            resumeWalkie = true
            // 等对讲真正放开麦克风再开始认，不然语音识别抢不到麦克风
            walkie.turnOff { if (_state.value is VoiceState.Listening) start() }
        } else {
            start()
        }
    }

    /** 轮着试的识别服务（null 是系统默认的）、现在试到第几个；先离线认，认不了（没有中文离线包）再联网认 */
    private var services: List<ComponentName?> = listOf(null)
    private var attempt = 0
    private var offline = true

    private fun installedServices(): List<ComponentName> =
        runCatching {
            app.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
        }.getOrDefault(emptyList())

    private fun start() {
        val service = services[attempt]
        Log.i(TAG, "listening with ${service ?: "default"} (offline=$offline)")
        // 每次都新建一个：同一个识别器用过一次、出过错以后，有的手机会一直报忙
        val r = SpeechRecognizer.createSpeechRecognizer(app, service).also { recognizer = it }
        r.setRecognitionListener(listener)
        val intent = recognizeIntent()
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
        beep()
        r.startListening(intent)
    }

    private fun recognizeIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Lang.english) "en-SG" else "zh-CN")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

    /**
     * 换个方式再认一次。旧的识别器先销毁，等一会再建新的：
     * 销毁了马上新建，识别服务那边上一次还没收完，新的一来就报忙
     */
    private fun restart() {
        recognizer?.destroy()
        recognizer = null
        main.postDelayed({ if (_state.value is VoiceState.Listening) start() }, RESTART_DELAY_MS)
    }

    /** 系统的语音输入框认完了：[candidates] 是听到的几个候选，没认出来、取消了是 null */
    fun dialogResult(candidates: List<String>?) {
        if (_state.value !is VoiceState.Listening) return
        if (candidates.isNullOrEmpty()) {
            done()
            return
        }
        handle(candidates)
    }

    fun cancel() {
        recognizer?.cancel()
        done()
    }

    private fun handle(candidates: List<String>) {
        done()
        // 几个候选里取第一个听得懂的
        val command = candidates.map(VoiceParser::parse).firstOrNull { it !is VoiceCommand.Unknown }
            ?: VoiceCommand.Unknown(candidates.firstOrNull().orEmpty())
        handler?.invoke(command) ?: say(tr("打开 Rando 再说", "Open Rando first"))
    }

    private fun done() {
        _state.value = VoiceState.Idle
        main.removeCallbacksAndMessages(null)
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
            handle(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
        }

        override fun onError(error: Int) {
            Log.w(TAG, "recognizer error $error (service=${services[attempt] ?: "default"}, offline=$offline)")
            if (_state.value !is VoiceState.Listening) return
            if (error in LANGUAGE_ERRORS && offline) {
                // 没有中文离线包：同一个服务换联网认
                offline = false
                restart()
                return
            }
            if (retryable(error) && attempt + 1 < services.size) {
                // 这个识别服务忙、断开了：换下一个
                attempt++
                offline = true
                restart()
                return
            }
            recognizer?.destroy()
            recognizer = null
            // 识别服务都用不了：弹系统的语音输入框，对讲等它认完再开
            if (retryable(error) && dialog?.invoke(recognizeIntent()) == true) return
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
        private const val RESTART_DELAY_MS = 800L
        private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        private const val MAX_SERVICES = 4
        // Android 12 以后才有的错误码；直接写数字，老系统上也能比
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
        private const val ERROR_CANNOT_CHECK_SUPPORT = 14
        private val LANGUAGE_ERRORS = setOf(ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE, ERROR_CANNOT_CHECK_SUPPORT)

        /**
         * 轮着试的识别服务：先用系统默认的（null），再试手机上装的，谷歌 App 的排前面
         * （有的手机默认的是厂商自己的、或者只认离线的谷歌语音服务，第三方 App 一用就一直报忙）
         */
        fun <T : Any> order(installed: List<T>, packageName: (T) -> String): List<T?> =
            (listOf<T?>(null) + installed.sortedBy { if (packageName(it) == GOOGLE_APP) 0 else 1 }).take(MAX_SERVICES)

        /** 换个方式再来一次可能就好了的错 */
        fun retryable(error: Int): Boolean = error in LANGUAGE_ERRORS || error in setOf(
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT, ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_AUDIO,
        )

        /** 出错了说什么：说清楚是哪儿的问题，不只说「出错了」 */
        fun errorText(error: Int): String? = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> tr("没听清，再说一次", "Didn't catch that, please say it again")
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                tr("语音识别要联网，或者下载中文离线语音包", "Speech recognition needs internet, or download the offline speech pack")
            in LANGUAGE_ERRORS -> tr("语音识别不支持中文，在系统设置里给语音识别下载中文语音包", "Speech recognition doesn't support this language. Download the language pack in system settings")
            SpeechRecognizer.ERROR_AUDIO -> tr("语音识别打不开麦克风，可能被别的 App 占用了", "Speech recognition can't open the microphone. Another app may be using it")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> tr("语音识别没有麦克风权限：在系统设置里给谷歌 App 打开麦克风权限", "Speech recognition has no microphone permission. Allow it for the Google app in system settings")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> tr("语音识别正忙，过一会再试；一直忙的话重启一下手机", "Speech recognition is busy. Try again later, or restart the phone if it stays busy")
            ERROR_SERVER_DISCONNECTED -> tr("语音识别断开了，在系统设置里把默认语音识别换成谷歌试试", "Speech recognition disconnected. Try switching the default to Google in system settings")
            SpeechRecognizer.ERROR_CLIENT -> tr("连不上手机的语音识别，在系统设置里把默认语音识别换成谷歌试试", "Can't connect to speech recognition. Try switching the default to Google in system settings")
            else -> tr("语音识别出错了，错误码 $error", "Speech recognition error $error")
        }
    }
}
