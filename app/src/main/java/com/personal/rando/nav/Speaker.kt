package com.personal.rando.nav

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 系统自带的 TTS 播报。声音按 [VoiceChoice] 挑：设置里选了就用选的，没选就用装好的音质最高的
 * 界面语言的声音（中文是普通话）；手机上一个这种语言的声音都没有时退回系统默认语言
 */
class Speaker(context: Context, private val wanted: () -> String? = { null }) {

    private var ready = false
    /** TTS 引擎还没启动好时先记着最后一句，好了再念（比如「开始导航」） */
    private var pending: String? = null
    private var voices: List<Voice> = emptyList()
    @Volatile private var applied: String? = null
    /** 要联网的声音念不出来以后，这个时间之前先用不联网的 */
    @Volatile private var offlineUntil = 0L
    /** 现在 TTS 设的是不是英文；界面换了语言，下次念之前跟着换 */
    private var english: Boolean? = null

    private val _options = MutableStateFlow<List<VoiceOption>>(emptyList())
    /** 设置里能选的声音，好的排前面；引擎启动好之前是空的 */
    val options: StateFlow<List<VoiceOption>> = _options

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            voices = runCatching { tts.voices?.toList() }.getOrNull().orEmpty()
            followLanguage()
            pending?.let(::say)
            pending = null
        }
    }

    init {
        useCall(false)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (errorCode != TextToSpeech.ERROR_NETWORK && errorCode != TextToSpeech.ERROR_NETWORK_TIMEOUT) return
                // 没信号：这一句换不联网的声音重念，之后一阵子都先不联网
                offlineUntil = SystemClock.elapsedRealtime() + OFFLINE_MS
                if (utteranceId != null && utteranceId != PREVIEW && !utteranceId.startsWith(RETRY)) {
                    useVoice(wanted())
                    tts.speak(utteranceId, TextToSpeech.QUEUE_ADD, null, RETRY + utteranceId)
                }
            }
        })
    }

    /**
     * 开着对讲时手机在「通话」状态（蓝牙耳机走通话线路），按导航语音放会被系统压掉或者放不出来，
     * 这时也按通话放，和队友的声音一起从耳机出；关了对讲再换回导航语音（放音乐时音乐会自动压低）
     */
    fun useCall(call: Boolean) {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (call) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    /** 界面语言换了：换 TTS 的语言，设置里能选的声音也换成这种语言的 */
    fun followLanguage() {
        if (!ready || english == Lang.english) return
        english = Lang.english
        _options.value = VoiceChoice.choices(voices.map(::option))
        applied = null
        val result = tts.setLanguage(Lang.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault())
        }
    }

    /** 排队念，路口提示和路况提醒挨着来时不会互相打断 */
    fun say(text: String) {
        if (!ready) {
            pending = text
            return
        }
        followLanguage()
        useVoice(wanted())
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, text)
    }

    /** 设置里试听：打断正在念的，用 [name] 念一句 */
    fun preview(name: String?, text: String = tr("前方 200 米右转，进入公园连道", "In 200 metres, turn right onto the park connector")) {
        if (!ready) return
        followLanguage()
        useVoice(name)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, PREVIEW)
    }

    private fun useVoice(name: String?) {
        val all = voices.map(::option)
        val online = SystemClock.elapsedRealtime() >= offlineUntil
        val choice = VoiceChoice.pick(all, name)?.takeIf { online || !it.network } ?: VoiceChoice.best(all) ?: return
        if (choice.name == applied) return
        voices.firstOrNull { it.name == choice.name }?.let { if (tts.setVoice(it) == TextToSpeech.SUCCESS) applied = it.name }
    }

    private fun option(v: Voice) = VoiceOption(
        name = v.name,
        locale = v.locale,
        quality = v.quality,
        latency = v.latency,
        network = v.isNetworkConnectionRequired,
        notInstalled = v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true,
    )

    private companion object {
        const val OFFLINE_MS = 5 * 60_000L
        const val PREVIEW = "\u0000preview"
        const val RETRY = "\u0000retry:"
    }
}
