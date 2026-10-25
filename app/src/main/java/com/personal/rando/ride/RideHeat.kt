package com.personal.rando.ride

import com.personal.rando.i18n.tr
import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import kotlin.math.cos
import kotlin.math.floor

/** 骑过几次：浅黄、橙、红 */
enum class HeatLevel(private val zh: String, private val en: String, val color: String) {
    LOW("1–2 次", "1–2 times", "#FFC94D"),
    MID("3–9 次", "3–9 times", "#F57C00"),
    HIGH("10 次以上", "10+ times", "#D32F2F");

    val label: String get() = tr(zh, en)

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
        val dense = tracks.map { densify(it) }
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
