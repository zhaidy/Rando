package com.personal.rando.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class VoiceChoiceTest {

    private fun v(name: String, lang: String = "zh", country: String = "CN", quality: Int = 300, network: Boolean = false, notInstalled: Boolean = false) =
        VoiceOption(name, Locale(lang, country), quality, 300, network, notInstalled)

    @Test
    fun `自动挑不联网里音质最高的普通话`() {
        val all = listOf(
            v("en", lang = "en", country = "US", quality = 500),
            v("cmn-local-normal", quality = 300),
            v("cmn-network", quality = 500, network = true),
            v("cmn-local-high", lang = "cmn", quality = 400),
            v("yue", country = "HK", quality = 500),
            v("cmn-not-installed", quality = 500, notInstalled = true),
        )
        assertEquals("cmn-local-high", VoiceChoice.best(all)?.name)
        assertEquals(listOf("cmn-network", "cmn-local-high", "cmn-local-normal"), VoiceChoice.choices(all).map { it.name })
    }

    @Test
    fun `一样的音质先挑大陆的`() {
        assertEquals("cn", VoiceChoice.best(listOf(v("tw", country = "TW"), v("cn")))?.name)
    }

    @Test
    fun `选了的还在就用选的，不在了就自动挑`() {
        val all = listOf(v("a", quality = 400), v("b", quality = 300, network = true))
        assertEquals("b", VoiceChoice.pick(all, "b")?.name)
        assertEquals("a", VoiceChoice.pick(all, "gone")?.name)
        assertEquals("a", VoiceChoice.pick(all, null)?.name)
    }

    @Test
    fun `没有中文声音就不挑`() {
        assertNull(VoiceChoice.best(listOf(v("en", lang = "en", country = "US"))))
    }

    @Test
    fun `说明`() {
        assertEquals("很高音质 · 要联网", VoiceChoice.describe(v("x", quality = 500, network = true)))
        assertEquals("普通音质 · 台湾口音", VoiceChoice.describe(v("x", country = "TW")))
    }
}
