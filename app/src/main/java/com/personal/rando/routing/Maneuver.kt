package com.personal.rando.routing

import com.personal.rando.i18n.tr
import com.personal.rando.nav.Geo
import kotlin.math.abs

/** 路线上第 pointIndex 个坐标处要做的动作 */
data class Maneuver(val pointIndex: Int, val turn: Turn, val exit: Int = 0, val angle: Int = 0) {

    /** 往左拐的：看 BRouter 给的转角，没有就看动作 */
    val isLeft: Boolean get() = if (angle != 0) angle < 0 else "LEFT" in turn.name

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

        /** 按转角（左负右正）定动作，分界跟 BRouter 一样 */
        fun fromAngle(angle: Int): Turn = when {
            angle < -159 || angle > 159 -> U_TURN
            angle < -135 -> SHARP_LEFT
            angle < -45 -> LEFT
            angle < -15 -> SLIGHT_LEFT
            angle <= 15 -> STRAIGHT
            angle <= 45 -> SLIGHT_RIGHT
            angle <= 135 -> RIGHT
            else -> SHARP_RIGHT
        }
    }
}

/**
 * BRouter 在错位路口、过马路的斜道上会连着给好几个挨得很近的提示，比如「稍向右」，5 米后「向左急转」；
 * 还会把明明拐了大弯的地方标成「直行」（过马路中间的安全岛上先右后左，标成直行、右转、左转）。
 * 挨得这么近的合成一个，往哪拐看路线本身的形状：进来前那 [LOOK_METERS] 米朝哪、出去后那 [LOOK_METERS] 米朝哪。
 * 只有一个真拐弯、形状也是往这边拐的，照用 BRouter 的说法（「靠左走」「出口」这些留着）；环岛不合
 */
internal fun mergeClose(maneuvers: List<Maneuver>, points: List<LatLon>, meters: Double = MERGE_METERS): List<Maneuver> {
    if (maneuvers.isEmpty() || points.size < 2) return maneuvers
    val cumulative = DoubleArray(points.size).also { acc ->
        for (i in 1 until points.size) acc[i] = acc[i - 1] + Geo.distance(points[i - 1], points[i])
    }
    fun pointAt(m: Double): LatLon {
        val at = m.coerceIn(0.0, cumulative.last())
        val i = (1 until points.size).firstOrNull { cumulative[it] >= at } ?: return points.last()
        val length = cumulative[i] - cumulative[i - 1]
        val t = if (length > 0) (at - cumulative[i - 1]) / length else 0.0
        val a = points[i - 1]
        val b = points[i]
        return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }
    /** 从 [from] 米进来、[to] 米出去，路线拐了多少度（左负右正） */
    fun shapeAngle(from: Double, to: Double): Int {
        val entry = Geo.bearing(pointAt(from - LOOK_METERS), pointAt(from))
        val exit = Geo.bearing(pointAt(to), pointAt(to + LOOK_METERS))
        return (((exit - entry) % 360 + 540) % 360 - 180).toInt()
    }
    val groups = mutableListOf<MutableList<Maneuver>>()
    for (m in maneuvers) {
        val last = groups.lastOrNull()?.last()
        if (last != null && !last.turn.isRoundabout && !m.turn.isRoundabout && cumulative[m.pointIndex] - cumulative[last.pointIndex] <= meters) {
            groups.last().add(m)
        } else {
            groups.add(mutableListOf(m))
        }
    }
    return groups.map { group ->
        val first = group.first()
        if (first.turn.isRoundabout) return@map first
        val angle = shapeAngle(cumulative[first.pointIndex], cumulative[group.last().pointIndex])
        val turns = group.filter { it.turn != Turn.STRAIGHT }
        val only = turns.singleOrNull()
        when {
            only != null && (abs(angle) <= SAME_WAY_DEGREES || (angle < 0) == only.isLeft) -> only
            turns.isEmpty() && abs(angle) <= STRAIGHT_DEGREES -> first
            else -> Maneuver(pointIndex = first.pointIndex, turn = Turn.fromAngle(angle), angle = angle)
        }
    }
}

/** 两个提示离这么近（米）就合成一个 */
internal const val MERGE_METERS = 25.0
/** 看路线形状时往前、往后各看这么远（米）；BRouter 标的点比实际拐弯早几米，太短会看错 */
private const val LOOK_METERS = 20.0
/** 形状拐得比这小（度），不跟 BRouter 唯一那个拐弯的方向较真 */
private const val SAME_WAY_DEGREES = 20
/** 标成「直行」的，形状拐得比这大（度）才改成拐弯 */
private const val STRAIGHT_DEGREES = 45

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
