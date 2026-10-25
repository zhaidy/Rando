package com.personal.rando.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** 设置里选的界面语言；[SYSTEM] 跟着手机的系统语言走 */
enum class Language { SYSTEM, ZH, EN }

/**
 * 现在用哪种语言。文案都写成 `tr("中文", "English")`，按这里挑一个。
 * 是 Compose 的 state：界面上读到的文字在切换语言后会自动重画。
 * 没调 [apply] 之前（比如单元测试里）是中文
 */
object Lang {

    var english: Boolean by mutableStateOf(false)
        private set

    /** 日期、TTS、语音识别用的地区 */
    val locale: Locale get() = if (english) Locale.UK else Locale.SIMPLIFIED_CHINESE

    fun apply(choice: Language, system: Locale = Locale.getDefault()) {
        english = resolve(choice, system)
    }

    /** 跟系统时：系统是中文就中文，其他语言都用英文 */
    fun resolve(choice: Language, system: Locale): Boolean = when (choice) {
        Language.ZH -> false
        Language.EN -> true
        Language.SYSTEM -> system.language != "zh"
    }

    /** 测试里临时换语言 */
    fun <T> with(english: Boolean, block: () -> T): T {
        val before = this.english
        this.english = english
        return try { block() } finally { this.english = before }
    }
}

/** 按现在的语言挑一句 */
fun tr(zh: String, en: String): String = if (Lang.english) en else zh
