package com.personal.rando.ride

import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import kotlin.math.cos
import kotlin.math.floor

/** 骑过几次：浅黄、橙、红 */
enum class HeatLevel(val label: String, val color: String) {
    LOW("1–2 次", "#FFC94D"),
    MID("3–9 次", "#F57C00"),
    HIGH("10 次以上", "#D32F2F");

    companion object {
        fun of(count: Int): HeatLevel = when {
            count >= 10 -> HIGH
            count >= 3 -> MID
            else -> LOW
        }
    }
}

/** 一段连着的、骑过次数在同一档的轨迹 */
data class HeatPiece(val level: HeatLevel, val points: List<LatLon>)

/**
 * 热力线：把地图切成 [CELL_METERS] 见方的小格，数每一格有几次骑行经过（一次骑行只算一次），
 * 再按每个点所在格的次数给轨迹分段上色。
 * 同一条路每次 GPS 会偏几米，可能落到隔壁格，所以数的时候把周围一圈格子也算上
 */
object RideHeat {

    const val CELL_METERS = 25.0

    private const val METERS_PER_DEGREE = 111_320.0
    /** 新加坡在赤道边上，经度一度的长度按 1.35° 纬度算就够准 */
    private val LON_METERS = METERS_PER_DEGREE * cos(Math.toRadians(1.35))

    private data class Cell(val x: Int, val y: Int)

    private fun cell(p: LatLon) = Cell(floor(p.lon * LON_METERS / CELL_METERS).toInt(), floor(p.lat * METERS_PER_DEGREE / CELL_METERS).toInt())

    fun pieces(tracks: List<List<LatLon>>): List<HeatPiece> {
        val dense = tracks.map { densify(trimStill(it)) }
        // 每格被哪几次骑行经过
        val rides = HashMap<Cell, MutableSet<Int>>()
        dense.forEachIndexed { i, track -> track.forEach { rides.getOrPut(cell(it)) { HashSet() } += i } }
        val counts = HashMap<Cell, Int>()
        fun count(c: Cell): Int = counts.getOrPut(c) {
            val near = HashSet<Int>()
            for (dx in -1..1) for (dy in -1..1) rides[Cell(c.x + dx, c.y + dy)]?.let(near::addAll)
            near.size
        }
        val result = mutableListOf<HeatPiece>()
        for (track in dense) {
            if (track.size < 2) continue
            var level = HeatLevel.of(count(cell(track[0])))
            var piece = mutableListOf(track[0])
            for (p in track.drop(1)) {
                piece += p
                val next = HeatLevel.of(count(cell(p)))
                if (next != level) {
                    if (piece.size >= 2) result += HeatPiece(level, piece)
                    piece = mutableListOf(p)
                    level = next
                }
            }
            if (piece.size >= 2) result += HeatPiece(level, piece)
        }
        // 次数多的画在上面
        return result.sortedBy { it.level.ordinal }
    }

    /**
     * 剪掉开头、结尾原地乱飘的那截（以前的记录没滤掉在家里、车棚下飘的点）：
     * 从头往后找，往前 [STRAIGHT_METERS] 的路走出去的直线距离不到 [STRAIGHT_SHARE] 就是在原地打转，剪掉；结尾倒过来一样
     */
    fun trimStill(track: List<LatLon>): List<LatLon> {
        val start = firstMoving(track)
        if (start < 0) return emptyList()
        val end = track.size - 1 - firstMoving(track.asReversed())
        return if (end > start) track.subList(start, end + 1) else emptyList()
    }

    /** 第一个开始真正往外走的点；一直在打转就是 -1 */
    private fun firstMoving(track: List<LatLon>): Int {
        for (i in 0 until track.size - 1) {
            var path = 0.0
            var j = i
            while (j < track.size - 1 && path < STRAIGHT_METERS) {
                path += Geo.distance(track[j], track[j + 1])
                j++
            }
            // 剩下的不够一截就不剪了
            if (path < STRAIGHT_METERS) return if (i == 0) 0 else -1
            if (Geo.distance(track[i], track[j]) >= path * STRAIGHT_SHARE) return i
        }
        return if (track.size == 1) 0 else -1
    }

    private const val STRAIGHT_METERS = 100.0
    private const val STRAIGHT_SHARE = 0.6

    /** 两点隔得比半格还远就在中间补点，免得跳过格子 */
    private fun densify(track: List<LatLon>): List<LatLon> {
        if (track.size < 2) return track
        val result = mutableListOf(track[0])
        for (i in 1 until track.size) {
            val a = track[i - 1]
            val b = track[i]
            val steps = (Geo.distance(a, b) / (CELL_METERS / 2)).toInt()
            for (s in 1..steps) {
                val t = s.toDouble() / (steps + 1)
                result += LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            }
            result += b
        }
        return result
    }
}
