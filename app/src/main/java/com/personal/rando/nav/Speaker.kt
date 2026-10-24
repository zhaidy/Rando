package com.personal.rando.nav

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 系统自带的 TTS 播报；手机没装中文语音时退回系统默认语言 */
class Speaker(context: Context) {

    private var ready = false
    /** TTS 引擎还没启动好时先记着最后一句，好了再念（比如「开始导航」） */
    private var pending: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault())
            }
            pending?.let(::say)
            pending = null
        }
    }

    init {
        // 按导航语音播放：放音乐时音乐会自动压低
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    /** 排队念，路口提示和路况提醒挨着来时不会互相打断 */
    fun say(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_ADD, null, text) else pending = text
    }
}
