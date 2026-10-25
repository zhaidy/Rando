package com.personal.rando.nav

import com.personal.rando.i18n.tr
import com.personal.rando.ui.Format

/**
 * 决定什么时候播什么：每个路口远一点提前说一次，快到了再说一次；
 * 骑偏了、到了也各说一次。只出文字，真正发声交给 TTS。
 */
class VoicePrompter {

    /** 路口在路线上的位置 → 已经播到哪一档 */
    private val announced = mutableMapOf<Int, Stage>()
    private var saidOffRoute = false
    private var saidArrived = false

    private enum class Stage { EARLY, NOW }

    fun start(totalMeters: Double): String =
        tr("开始导航，全程 ${Format.distance(totalMeters.toInt())}", "Starting navigation, ${Format.distance(totalMeters.toInt())} in total")

    fun next(progress: NavProgress): String? {
        if (progress.arrived) {
            if (saidArrived) return null
            saidArrived = true
            return tr("已到达目的地", "You have arrived")
        }
        if (progress.offRoute) {
            if (saidOffRoute) return null
            saidOffRoute = true
            return tr("已偏离路线，正在重新规划", "Off route, rerouting")
        }
        saidOffRoute = false

        val next = progress.next ?: return null
        val distance = progress.distanceToNext
        val stage = announced[next.pointIndex]
        return when {
            distance <= NOW_METERS && stage != Stage.NOW -> {
                announced[next.pointIndex] = Stage.NOW
                val following = progress.following
                if (following != null && progress.distanceNextToFollowing <= THEN_METERS) {
                    tr("${next.text}，然后${following.text}", "${next.text}, then ${lower(following.text)}")
                } else {
                    next.text
                }
            }
            distance <= EARLY_METERS && distance > NOW_METERS * 2 && stage == null -> {
                announced[next.pointIndex] = Stage.EARLY
                val ahead = Format.distance(roundDistance(distance))
                tr("${ahead}后${next.text}", "In $ahead, ${lower(next.text)}")
            }
            else -> null
        }
    }

    /** 新路线的路口序号全变了，之前播过的记录作废 */
    fun reset() {
        announced.clear()
        saidOffRoute = false
        saidArrived = false
    }

    /** 「Turn left」放句中变成「turn left」 */
    private fun lower(text: String) = text.replaceFirstChar { it.lowercase() }

    private fun roundDistance(meters: Double): Int =
        if (meters >= 100) (Math.round(meters / 50) * 50).toInt() else (Math.round(meters / 10) * 10).toInt()

    companion object {
        const val EARLY_METERS = 200.0
        const val NOW_METERS = 35.0
        /** 两个路口挨得这么近就连着说「左转，然后右转」 */
        const val THEN_METERS = 60.0
    }
}
