package com.personal.rando.routing

import com.personal.rando.i18n.tr

/** 路线上第 pointIndex 个坐标处要做的动作 */
data class Maneuver(val pointIndex: Int, val turn: Turn, val exit: Int = 0, val angle: Int = 0) {

    /** 播报和显示用的说法，比如「左转」「进入环岛，从第 2 个出口出」 */
    val text: String
        get() = if (turn.isRoundabout && exit > 0) tr("进入环岛，从第 $exit 个出口出", "At the roundabout, take the ${ordinal(exit)} exit") else turn.text
}

/** BRouter 的动作代码（VoiceHint 里的常量） */
enum class Turn(val code: Int, private val zh: String, private val en: String, val arrow: String) {
    STRAIGHT(1, "直行", "Continue straight", "↑"),
    LEFT(2, "左转", "Turn left", "↰"),
    SLIGHT_LEFT(3, "稍向左", "Bear left", "↖"),
    SHARP_LEFT(4, "向左急转", "Turn sharp left", "↙"),
    RIGHT(5, "右转", "Turn right", "↱"),
    SLIGHT_RIGHT(6, "稍向右", "Bear right", "↗"),
    SHARP_RIGHT(7, "向右急转", "Turn sharp right", "↘"),
    KEEP_LEFT(8, "靠左走", "Keep left", "↖"),
    KEEP_RIGHT(9, "靠右走", "Keep right", "↗"),
    U_TURN_LEFT(10, "掉头", "Make a U-turn", "↶"),
    U_TURN(11, "掉头", "Make a U-turn", "↶"),
    U_TURN_RIGHT(12, "掉头", "Make a U-turn", "↷"),
    ROUNDABOUT(14, "进入环岛", "Enter the roundabout", "⟳"),
    ROUNDABOUT_LEFT(15, "进入环岛", "Enter the roundabout", "⟲"),
    BEELINE(16, "直线前往", "Head straight to the destination", "↑"),
    EXIT_LEFT(17, "从左侧出口出", "Take the exit on the left", "↖"),
    EXIT_RIGHT(18, "从右侧出口出", "Take the exit on the right", "↗");

    /** 播报和显示用的说法，跟着界面语言 */
    val text: String get() = tr(zh, en)

    val isRoundabout: Boolean get() = this == ROUNDABOUT || this == ROUNDABOUT_LEFT

    companion object {
        /** 偏离路线（13）、终点（100）这类不是路口动作的代码不要 */
        fun fromCode(code: Int): Turn? = entries.firstOrNull { it.code == code }
    }
}

/** 英文序数：1st、2nd、3rd、11th、22nd */
internal fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
