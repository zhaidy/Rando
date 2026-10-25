package com.personal.rando.i18n

import com.personal.rando.nav.VoiceChoice
import com.personal.rando.nav.VoiceOption
import com.personal.rando.ui.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LangTest {

    @Test
    fun `跟随系统时中文系统用中文，别的都用英文`() {
        assertFalse(Lang.resolve(Language.SYSTEM, Locale.SIMPLIFIED_CHINESE))
        assertFalse(Lang.resolve(Language.SYSTEM, Locale.TRADITIONAL_CHINESE))
        assertTrue(Lang.resolve(Language.SYSTEM, Locale.US))
        assertTrue(Lang.resolve(Language.SYSTEM, Locale("ms", "MY")))
        assertFalse(Lang.resolve(Language.ZH, Locale.US))
        assertTrue(Lang.resolve(Language.EN, Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `默认是中文`() {
        assertEquals("你好", tr("你好", "Hello"))
        assertEquals("Hello", Lang.with(english = true) { tr("你好", "Hello") })
        assertEquals("你好", tr("你好", "Hello"))
    }

    @Test
    fun `英文的距离和时间`() = Lang.with(english = true) {
        assertEquals("850 m", Format.distance(850))
        assertEquals("15.9 km", Format.distance(15_900))
        assertEquals("15.9km", Format.shortDistance(15_900))
        assertEquals("45 min", Format.duration(45 * 60))
        assertEquals("2 h", Format.duration(2 * 3600))
        assertEquals("1 h 5 min", Format.duration(65 * 60))
        assertEquals("18.0 km/h", Format.speed(5.0))
    }

    @Test
    fun `英文界面挑英文声音，新加坡的优先`() = Lang.with(english = true) {
        fun v(name: String, lang: String, country: String) = VoiceOption(name, Locale(lang, country), 400, 300, network = false)
        val all = listOf(v("cmn", "zh", "CN"), v("us", "en", "US"), v("sg", "en", "SG"))
        assertEquals(listOf("sg", "us"), VoiceChoice.choices(all).map { it.name })
        assertEquals("High quality · Singapore accent", VoiceChoice.describe(all[2]))
    }
}
