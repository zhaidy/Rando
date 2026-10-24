package com.personal.rando.routing

/** 路线上第 pointIndex 个坐标处要做的动作 */
data class Maneuver(val pointIndex: Int, val turn: Turn, val exit: Int = 0, val angle: Int = 0) {

    /** 播报和显示用的说法，比如「左转」「进入环岛，从第 2 个出口出」 */
    val text: String
        get() = if (turn.isRoundabout && exit > 0) "进入环岛，从第 $exit 个出口出" else turn.text
}

/** BRouter 的动作代码（VoiceHint 里的常量） */
enum class Turn(val code: Int, val text: String, val arrow: String) {
    STRAIGHT(1, "直行", "↑"),
    LEFT(2, "左转", "↰"),
    SLIGHT_LEFT(3, "稍向左", "↖"),
    SHARP_LEFT(4, "向左急转", "↙"),
    RIGHT(5, "右转", "↱"),
    SLIGHT_RIGHT(6, "稍向右", "↗"),
    SHARP_RIGHT(7, "向右急转", "↘"),
    KEEP_LEFT(8, "靠左走", "↖"),
    KEEP_RIGHT(9, "靠右走", "↗"),
    U_TURN_LEFT(10, "掉头", "↶"),
    U_TURN(11, "掉头", "↶"),
    U_TURN_RIGHT(12, "掉头", "↷"),
    ROUNDABOUT(14, "进入环岛", "⟳"),
    ROUNDABOUT_LEFT(15, "进入环岛", "⟲"),
    BEELINE(16, "直线前往", "↑"),
    EXIT_LEFT(17, "从左侧出口出", "↖"),
    EXIT_RIGHT(18, "从右侧出口出", "↗");

    val isRoundabout: Boolean get() = this == ROUNDABOUT || this == ROUNDABOUT_LEFT

    companion object {
        /** 偏离路线（13）、终点（100）这类不是路口动作的代码不要 */
        fun fromCode(code: Int): Turn? = entries.firstOrNull { it.code == code }
    }
}
