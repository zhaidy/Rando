package com.personal.rando.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceControlTest {

    @Test
    fun `没有中文离线包换联网再认一次`() {
        assertTrue(VoiceControl.retryable(13))
        assertTrue(VoiceControl.retryable(12))
        assertTrue(VoiceControl.retryable(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertFalse(VoiceControl.retryable(SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(VoiceControl.retryable(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
    }

    @Test
    fun `出错说清楚是哪儿的问题`() {
        assertEquals("没听清，再说一次", VoiceControl.errorText(SpeechRecognizer.ERROR_NO_MATCH))
        assertTrue(VoiceControl.errorText(13)!!.contains("中文语音包"))
        assertTrue(VoiceControl.errorText(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)!!.contains("麦克风权限"))
        // 不认识的错误码也说出来，方便查
        assertTrue(VoiceControl.errorText(99)!!.contains("99"))
        assertNotNull(VoiceControl.errorText(SpeechRecognizer.ERROR_CLIENT))
    }
}
