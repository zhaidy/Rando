package com.personal.rando.ui

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 分享图上照片小卡片放哪：每张离拍照的点 [REACH] 或者再远一点，从几个方向里挑一个
 * ——不出地图、不压别的卡片、不盖住照片的点和轨迹，差不多的时候放近一点、往上放（下面是渐变和文字）
 */
object PhotoPins {
    const val SIZE = 208f
    const val REACH = 170f
    private const val EDGE = 24f
    private const val NEAR = 40f

    /**
     * 每张卡片的中心；[anchors] 是拍照的点，[track] 是轨迹上的点（尽量不盖住），
     * 地图是 [w]×[h]（底下 [bottom] 留给渐变）。
     * 最多四张、每张二十个位置，所有搭配都试一遍挑最好的——一张张贪心放的话，先放的会占掉后面那张唯一的好位置
     */
    fun layout(anchors: List<Pair<Float, Float>>, w: Float, h: Float, track: List<Pair<Float, Float>> = emptyList(), bottom: Float = 60f): List<Pair<Float, Float>> {
        val half = SIZE / 2
        val spots = anchors.map { (ax, ay) ->
            REACHES.flatMapIndexed { far, reach ->
                DIRECTIONS.mapIndexed { order, deg ->
                    val rad = Math.toRadians(deg)
                    val cx = (ax + reach * cos(rad).toFloat()).coerceIn(EDGE + half, w - EDGE - half)
                    val cy = (ay - reach * sin(rad).toFloat()).coerceIn(EDGE + half, h - bottom - half)
                    // 卡片盖住了拍照的点（自己的也算），线就看不见了
                    val dots = anchors.count { (x, y) -> inside(x, y, cx, cy, 12f) }
                    // 盖住的轨迹按占卡片的比例算：整张压在路上和压住另一张差不多
                    val road = track.count { (x, y) -> inside(x, y, cx, cy, 8f) }.toDouble() / track.size.coerceAtLeast(1) * 20 * SIZE * SIZE
                    val up = if (cy < ay) 0.0 else 400.0
                    Spot(cx, cy, dots * SIZE * SIZE / 2 + road + up + far * 600.0 + order)
                }
            }.sortedBy { it.cost }
        }

        /** 两张放在一起的代价：卡片重叠；连线从别的卡片底下穿过去、和别的线交叉、擦着别的点过去，就看不出是哪张的了 */
        fun pair(i: Int, a: Spot, j: Int, b: Spot): Double {
            val (ax, ay) = anchors[i]
            val (bx, by) = anchors[j]
            val tangled = crosses(ax, ay, a.x, a.y, b.x, b.y) || crosses(bx, by, b.x, b.y, a.x, a.y) ||
                intersect(ax, ay, a.x, a.y, bx, by, b.x, b.y) || near(bx, by, ax, ay, a.x, a.y) || near(ax, ay, bx, by, b.x, b.y)
            return overlap(a.x, a.y, b.x, b.y) + if (tangled) SIZE * SIZE / 2.0 else 0.0
        }

        val chosen = arrayOfNulls<Spot>(anchors.size)
        var best = Double.MAX_VALUE
        var bestPick = emptyList<Spot>()
        fun search(i: Int, sum: Double) {
            if (sum >= best) return
            if (i == anchors.size) {
                best = sum
                bestPick = chosen.map { it!! }
                return
            }
            for (spot in spots[i]) {
                chosen[i] = spot
                search(i + 1, sum + spot.cost + (0 until i).sumOf { j -> pair(j, chosen[j]!!, i, spot) })
            }
        }
        search(0, 0.0)
        return bestPick.map { it.x to it.y }
    }

    private class Spot(val x: Float, val y: Float, val cost: Double)

    /** 从 ([ax], [ay]) 连到 ([bx], [by]) 的线中途穿过中心在 ([cx], [cy]) 的卡片 */
    private fun crosses(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Boolean =
        (1 until 10).any { k -> inside(ax + (bx - ax) * k / 10, ay + (by - ay) * k / 10, cx, cy, 0f) }

    /** 点 ([px], [py]) 离线段 a→b 不到 [NEAR]（点就是线的起点不算） */
    private fun near(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        if (hypot(px - ax, py - ay) < 1f) return false
        val len2 = (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
        val t = if (len2 > 0) (((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / len2).coerceIn(0f, 1f) else 0f
        return hypot(px - (ax + t * (bx - ax)), py - (ay + t * (by - ay))) < NEAR
    }

    /** 线段 a→b 和 c→d 交叉（端点碰到不算） */
    private fun intersect(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
        fun side(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float) = (qx - px) * (ry - py) - (qy - py) * (rx - px)
        return side(ax, ay, bx, by, cx, cy) * side(ax, ay, bx, by, dx, dy) < 0 &&
            side(cx, cy, dx, dy, ax, ay) * side(cx, cy, dx, dy, bx, by) < 0
    }

    /** ([x], [y]) 在中心是 ([cx], [cy]) 的卡片里（四周再放宽 [pad]） */
    private fun inside(x: Float, y: Float, cx: Float, cy: Float, pad: Float): Boolean {
        val r = SIZE / 2 + pad
        return x in (cx - r)..(cx + r) && y in (cy - r)..(cy + r)
    }

    /** 两张卡片重叠的面积 */
    private fun overlap(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = max(0f, SIZE - kotlin.math.abs(ax - bx))
        val dy = max(0f, SIZE - kotlin.math.abs(ay - by))
        return min(dx, SIZE) * min(dy, SIZE)
    }

    /** 每张歪一点，左右交替，像随手贴上去的 */
    fun tilt(i: Int): Float = TILTS[i % TILTS.size]

    // 0° 朝右，逆时针；先试左上、右上
    private val REACHES = listOf(REACH, REACH * 1.6f)
    private val DIRECTIONS = listOf(120.0, 60.0, 90.0, 150.0, 30.0, 180.0, 0.0, 210.0, 330.0, 270.0)
    private val TILTS = floatArrayOf(-4f, 3f, -2f, 4f)
}
