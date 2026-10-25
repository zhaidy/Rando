package com.personal.rando.nav

import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import java.util.Locale

/** 手机上的一个 TTS 声音（从 [android.speech.tts.Voice] 抄过来，方便测试） */
data class VoiceOption(
    val name: String,
    val locale: Locale,
    /** [android.speech.tts.Voice.QUALITY_VERY_LOW] 100 … [android.speech.tts.Voice.QUALITY_VERY_HIGH] 500 */
    val quality: Int,
    /** 同上，100 最快 … 500 最慢 */
    val latency: Int,
    val network: Boolean,
    /** 还没下载语音数据 */
    val notInstalled: Boolean = false,
)

/** 挑播报用的声音：现在界面语言的（中文是普通话）、装好了的、音质最高的 */
object VoiceChoice {

    /** 普通话的地区，越靠前越优先；香港的中文声音一般是粤语，不要 */
    private val MANDARIN = listOf("CN", "SG", "TW", "")

    /** 英文声音的地区，越靠前越优先：新加坡的念本地地名最顺 */
    private val ENGLISH = listOf("SG", "GB", "AU", "US", "IN", "")

    fun isMandarin(v: VoiceOption): Boolean =
        (v.locale.language == "zh" || v.locale.language == "cmn") && v.locale.country in MANDARIN

    fun isEnglish(v: VoiceOption): Boolean = v.locale.language == "en"

    private fun regions(): List<String> = if (Lang.english) ENGLISH else MANDARIN

    /** 设置里列出来的声音：现在语言的，好的排前面 */
    fun choices(all: List<VoiceOption>): List<VoiceOption> =
        all.filter { (if (Lang.english) isEnglish(it) else isMandarin(it)) && !it.notInstalled }.sortedWith(ORDER)

    /**
     * 自动挑：只挑不用联网的（路上信号不好、念得晚了路口都过了），音质高的优先，
     * 一样就挑大陆的（英文挑新加坡的）、快的；一个合适的都没有返回 null（退回系统默认）
     */
    fun best(all: List<VoiceOption>): VoiceOption? = choices(all).firstOrNull { !it.network }

    /** 选了 [wanted] 而且还在、也是现在语言的就用它，不然自动挑（换了语言后旧的声音不能再用） */
    fun pick(all: List<VoiceOption>, wanted: String?): VoiceOption? =
        wanted?.let { w -> choices(all).firstOrNull { it.name == w } } ?: best(all)

    /** 「很高音质 · 要联网」 */
    fun describe(v: VoiceOption): String = listOfNotNull(
        when {
            v.quality >= 500 -> tr("很高音质", "Very high quality")
            v.quality >= 400 -> tr("高音质", "High quality")
            v.quality >= 300 -> tr("普通音质", "Normal quality")
            else -> tr("低音质", "Low quality")
        },
        tr("要联网", "Needs internet").takeIf { v.network },
        if (Lang.english) when (v.locale.country) {
            "SG" -> "Singapore accent"
            "GB" -> "British accent"
            "US" -> "American accent"
            "AU" -> "Australian accent"
            "IN" -> "Indian accent"
            else -> null
        } else when (v.locale.country) {
            "TW" -> "台湾口音"
            "SG" -> "新加坡口音"
            else -> null
        },
    ).joinToString(" · ")

    private val ORDER = compareByDescending<VoiceOption> { it.quality }
        .thenBy { regions().indexOf(it.locale.country).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        .thenBy { it.network }
        .thenBy { it.latency }
        .thenBy { it.name }
}
