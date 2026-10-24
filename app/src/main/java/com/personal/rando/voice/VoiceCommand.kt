package com.personal.rando.voice

import com.personal.rando.places.HazardType

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
    /** 没听懂 */
    data class Unknown(val text: String) : VoiceCommand
}

/**
 * 把识别出来的中文变成指令。说法尽量宽：「导航回家」「回家」「带我去公司」「去 Funan」
 * 「骑一圈二十公里」「骑东海岸那条」「结束导航」「还有多远」「这里在施工」「打开对讲」
 */
object VoiceParser {

    fun parse(raw: String): VoiceCommand {
        val text = raw.trim().trimEnd('。', '！', '!', '？', '?', '.').replace(" ", "")
        if (text.isEmpty()) return VoiceCommand.Unknown(raw)

        if (Regex("(结束|停止|退出|取消|不要)(导航|骑行)?$|^别导航").containsMatchIn(text)) return VoiceCommand.StopNavigation
        if (Regex("还有多远|还要多久|多久到|还剩多少|还有多少|多远到").containsMatchIn(text)) return VoiceCommand.Remaining
        if (Regex("天气|下雨|会不会下|热不热|空气").containsMatchIn(text)) return VoiceCommand.Weather

        when {
            "封路" in text || "不通" in text || "封了" in text -> return VoiceCommand.Report(HazardType.CLOSED)
            "施工" in text || "修路" in text -> return VoiceCommand.Report(HazardType.CONSTRUCTION)
            Regex("路面|坑|不平|颠|烂路|积水").containsMatchIn(text) -> return VoiceCommand.Report(HazardType.BAD_SURFACE)
        }

        if ("对讲" in text) {
            return VoiceCommand.Walkie(on = !Regex("关|停|闭").containsMatchIn(text))
        }

        Regex("骑?一圈(.*?)(公里|千米|km|K)?$", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val km = number(m.groupValues[1])
            if (km != null && km in 1..200) return VoiceCommand.Loop(km)
        }

        Regex("^(导航|带我)?(回|去|到)(.+?)(吧|那里|那儿)?$").find(text)?.let { m ->
            val verb = m.groupValues[2]
            val place = m.groupValues[3]
            return VoiceCommand.GoTo(if (verb == "回" && place == "家") "家" else place)
        }
        if (text.startsWith("导航")) return VoiceCommand.GoTo(text.removePrefix("导航"))
        if (text == "回家") return VoiceCommand.GoTo("家")

        Regex("^(骑|走)(.+?)(那条|那条路|那圈|路线)?$").find(text)?.let { m ->
            return VoiceCommand.Route(m.groupValues[2])
        }
        return VoiceCommand.Unknown(raw)
    }

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
