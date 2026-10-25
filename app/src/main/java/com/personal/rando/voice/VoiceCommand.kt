package com.personal.rando.voice

import com.personal.rando.places.HazardType
import com.personal.rando.places.SavedPlace

/** 听懂的一句话 */
sealed interface VoiceCommand {
    /** 去某个地方：先找收藏的地方，没有就搜 */
    data class GoTo(val place: String) : VoiceCommand
    /** 骑一圈多少公里 */
    data class Loop(val km: Int) : VoiceCommand
    /** 骑收藏的路线（按名字找） */
    data class Route(val name: String) : VoiceCommand
    data object StopNavigation : VoiceCommand
    /** 还有多远、还要多久 */
    data object Remaining : VoiceCommand
    data class Report(val type: HazardType) : VoiceCommand
    data class Walkie(val on: Boolean) : VoiceCommand
    data object Weather : VoiceCommand
    /** 骑累了坐 MRT 回家 */
    data object MrtHome : VoiceCommand
    /** 没听懂 */
    data class Unknown(val text: String) : VoiceCommand
}

/**
 * 把识别出来的话变成指令（中文、英文都认）。说法尽量宽：「导航回家」「回家」「带我去公司」「去 Funan」
 * 「骑一圈二十公里」「骑东海岸那条」「结束导航」「还有多远」「这里在施工」「打开对讲」
 */
object VoiceParser {

    /** 中文说法认不出来再按英文认（英文界面用英文识别） */
    fun parse(raw: String): VoiceCommand =
        parseChinese(raw).takeIf { it !is VoiceCommand.Unknown } ?: parseEnglish(raw)

    private fun parseChinese(raw: String): VoiceCommand {
        val text = raw.trim().trimEnd('。', '！', '!', '？', '?', '.').replace(" ", "")
        if (text.isEmpty()) return VoiceCommand.Unknown(raw)

        if (Regex("(结束|停止|退出|取消|不要)(导航|骑行)?$|^别导航|(关闭|关掉|关了)导航").containsMatchIn(text)) return VoiceCommand.StopNavigation
        if (Regex("还有多远|还要多久|多久到|还剩多少|还有多少|多远到").containsMatchIn(text)) return VoiceCommand.Remaining
        if (Regex("天气|下雨|会不会下|热不热|空气").containsMatchIn(text)) return VoiceCommand.Weather
        if (Regex("(坐|搭)(地铁|MRT|捷运|车)回家", RegexOption.IGNORE_CASE).containsMatchIn(text)) return VoiceCommand.MrtHome

        // 先认对讲：「对讲不通」不是封路
        if ("对讲" in text) {
            return VoiceCommand.Walkie(on = !Regex("关|停|闭|不要|别|不用|不想").containsMatchIn(text))
        }

        when {
            "封路" in text || "不通" in text || "封了" in text -> return VoiceCommand.Report(HazardType.CLOSED)
            "施工" in text || "修路" in text -> return VoiceCommand.Report(HazardType.CONSTRUCTION)
            Regex("路面|坑|不平|颠|烂路|积水").containsMatchIn(text) -> return VoiceCommand.Report(HazardType.BAD_SURFACE)
        }

        Regex("骑?一圈(.*?)(公里|千米|km|K)?(吧|左右)?$", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val km = number(m.groupValues[1])
            if (km != null && km in 1..200) return VoiceCommand.Loop(km)
        }

        Regex("^(导航|带我)?(回|去|到)(.+?)(吧|那里|那儿)?$").find(text)?.let { m ->
            val verb = m.groupValues[2]
            val place = m.groupValues[3]
            return VoiceCommand.GoTo(if (verb == "回" && place == "家") "家" else place)
        }
        if (text.startsWith("导航") && text.length > 2) return VoiceCommand.GoTo(text.removePrefix("导航"))
        if (text == "回家") return VoiceCommand.GoTo("家")

        // 「骑二十公里」没说一圈也是骑一圈
        Regex("^骑(.+?)(公里|千米|km|K)$", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val km = number(m.groupValues[1])
            if (km != null && km in 1..200) return VoiceCommand.Loop(km)
        }

        Regex("^(骑|走)(.+?)(那条|那条路|那圈|路线)?$").find(text)?.let { m ->
            return VoiceCommand.Route(m.groupValues[2])
        }
        return VoiceCommand.Unknown(raw)
    }

    /**
     * 英文说法：「navigate home」「take me to Funan」「ride a 20 km loop」「ride the East Coast route」
     * 「stop navigation」「how far」「there's construction here」「turn on the walkie」
     */
    fun parseEnglish(raw: String): VoiceCommand {
        val original = raw.trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")
        val text = original.lowercase()
        if (text.isEmpty()) return VoiceCommand.Unknown(raw)
        fun has(pattern: String) = Regex(pattern).containsMatchIn(text)

        if (has("^(stop|end|cancel|quit|exit)( the)?( navigation| navigating| route| ride)?$|^stop navigat")) return VoiceCommand.StopNavigation
        if (has("how (far|long|much (further|farther|longer))|time left|distance left|^eta$")) return VoiceCommand.Remaining
        if (has("(take|by|ride|catch)( the)? (mrt|train|subway|metro) home")) return VoiceCommand.MrtHome
        // 按整词认：「train」「Rainforest」里也有 rain
        if (has("\\b(weather|rain(s|ing|y)?|forecast)\\b|hot is it|air quality")) return VoiceCommand.Weather

        when {
            has("closed|blocked") -> return VoiceCommand.Report(HazardType.CLOSED)
            has("construction|road ?works") -> return VoiceCommand.Report(HazardType.CONSTRUCTION)
            has("pothole|bumpy|rough|bad (road|surface)|flood|puddle") -> return VoiceCommand.Report(HazardType.BAD_SURFACE)
        }

        if (has("walkie|intercom|radio")) return VoiceCommand.Walkie(on = !has("\\b(off|stop|close|disable|end)\\b"))

        Regex("loop (of )?(.+?) ?(km|kilometers?|kilometres?|k)?$").find(text)?.let { m ->
            val km = englishNumber(m.groupValues[2])
            if (km != null && km in 1..200) return VoiceCommand.Loop(km)
        }
        Regex("^(ride |do )?(a )?(.+?) ?(km|kilometers?|kilometres?|k) loop$").find(text)?.let { m ->
            val km = englishNumber(m.groupValues[3])
            if (km != null && km in 1..200) return VoiceCommand.Loop(km)
        }

        if (has("^((navigate|take me|bring me|go|head|ride|directions)( back)? )?home$")) return VoiceCommand.GoTo(SavedPlace.HOME)
        Regex("^(?:navigate|navigation|take me|bring me|go|head|directions)(?: to)? (.+)$", RegexOption.IGNORE_CASE)
            .find(original)?.let { m ->
                val place = m.groupValues[1].replace(Regex("^(?i)(the|my) "), "")
                // 收藏的家、公司存的是中文名
                return VoiceCommand.GoTo(
                    when (place.lowercase()) {
                        "home", "place", "house" -> SavedPlace.HOME
                        "work", "office", "workplace" -> SavedPlace.WORK
                        else -> place
                    }
                )
            }

        Regex("^ride (?:the |my )?(.+?)(?: route| loop| one)?$", RegexOption.IGNORE_CASE)
            .find(original)?.let { return VoiceCommand.Route(it.groupValues[1]) }
        return VoiceCommand.Unknown(raw)
    }

    /** 「20」「twenty」「twenty five」「twenty-five」「a hundred」 */
    fun englishNumber(s: String): Int? {
        val t = s.trim().lowercase()
        t.toIntOrNull()?.let { return it }
        val words = t.replace('-', ' ').split(' ').filter { it.isNotEmpty() && it != "and" }
        if (words.isEmpty()) return null
        var total = 0
        for (w in words) {
            when (w) {
                "hundred" -> total = (if (total == 0) 1 else total) * 100
                "a" -> Unit
                in ENGLISH -> total += ENGLISH.getValue(w)
                else -> return null
            }
        }
        return total.takeIf { it > 0 }
    }

    private val ENGLISH = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    ).withIndex().associate { it.value to it.index } +
        listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety").withIndex().associate { it.value to (it.index + 2) * 10 }

    /** 「20」「二十」「二十五」「十」「一百」 */
    fun number(s: String): Int? {
        val t = s.trim()
        t.toIntOrNull()?.let { return it }
        if (t.isEmpty()) return null
        val digits = mapOf('零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        var total = 0
        var current = 0
        for (ch in t) {
            when {
                ch in digits -> current = digits.getValue(ch)
                ch == '十' -> {
                    total += (if (current == 0) 1 else current) * 10
                    current = 0
                }
                ch == '百' -> {
                    total += (if (current == 0) 1 else current) * 100
                    current = 0
                }
                else -> return null
            }
        }
        return total + current
    }
}
