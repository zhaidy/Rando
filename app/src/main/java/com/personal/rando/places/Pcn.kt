package com.personal.rando.places

import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.floor

/** 公园连道的一个大环（NParks 分的 6 个 Loop），[lines] 是组成它的一段段路 */
data class PcnLoop(val name: String, val lines: List<List<LatLon>>) {
    /** 「西部探险环线」 */
    val zh: String get() = ZH[name] ?: name

    private companion object {
        val ZH = mapOf(
            "Central Urban Loop" to "中部市区环线",
            "Eastern Coastal Loop" to "东部海岸环线",
            "North Eastern Riverine Loop" to "东北河畔环线",
            "Northern Explorer Loop" to "北部探索环线",
            "Southern Ridges Loop" to "南部山脊环线",
            "Western Adventure Loop" to "西部探险环线",
        )
    }
}

/** 公园连道的数据：NParks 的 Park Connector Loop（data.gov.sg），抽稀到 5 米精度，放在 assets/pcn.json */
object PcnData {
    fun parse(json: String): List<PcnLoop> {
        val loops = JSONObject(json).getJSONArray("loops")
        return (0 until loops.length()).map { i ->
            val o = loops.getJSONObject(i)
            val lines = o.getJSONArray("l")
            PcnLoop(
                o.getString("n"),
                (0 until lines.length()).map { j ->
                    val flat = lines.getJSONArray(j)
                    (0 until flat.length() / 2).map { k -> LatLon(flat.getDouble(2 * k), flat.getDouble(2 * k + 1)) }
                },
            )
        }
    }
}

/** 一个大环骑过多少 */
data class LoopProgress(val loop: PcnLoop, val riddenMeters: Double, val totalMeters: Double) {
    val share: Double get() = if (totalMeters > 0) riddenMeters / totalMeters else 0.0
}

/** 地图上的一小段公园连道：骑过没有 */
data class PcnPiece(val ridden: Boolean, val points: List<LatLon>)

/** 全岛公园连道骑过多少：每个大环的进度，和画地图用的一段段 */
data class PcnProgress(val loops: List<LoopProgress>, val pieces: List<PcnPiece>) {
    val riddenMeters: Double get() = loops.sumOf { it.riddenMeters }
    val totalMeters: Double get() = loops.sumOf { it.totalMeters }
    val share: Double get() = if (totalMeters > 0) riddenMeters / totalMeters else 0.0
}

/**
 * 点亮公园连道：把骑过的轨迹落到 [CELL_METERS] 见方的格子里，
 * 公园连道每隔 [STEP_METERS] 看一下，所在的格子或者旁边一圈格子有轨迹经过就算骑过
 * （GPS 偏几米、连道画在路中间还是路边都不要紧）
 */
object PcnCoverage {

    const val CELL_METERS = 25.0
    private const val STEP_METERS = 15.0
    private const val METERS_PER_DEGREE = 111_320.0
    private val LON_METERS = METERS_PER_DEGREE * cos(Math.toRadians(1.35))

    private fun cell(p: LatLon): Long {
        val x = floor(p.lon * LON_METERS / CELL_METERS).toLong()
        val y = floor(p.lat * METERS_PER_DEGREE / CELL_METERS).toLong()
        return (x shl 32) xor (y and 0xFFFFFFFFL)
    }

    private fun near(cells: Set<Long>, p: LatLon): Boolean {
        val x = floor(p.lon * LON_METERS / CELL_METERS).toLong()
        val y = floor(p.lat * METERS_PER_DEGREE / CELL_METERS).toLong()
        for (dx in -1L..1L) for (dy in -1L..1L) {
            if (((x + dx) shl 32) xor ((y + dy) and 0xFFFFFFFFL) in cells) return true
        }
        return false
    }

    fun of(loops: List<PcnLoop>, tracks: List<List<LatLon>>): PcnProgress {
        val cells = HashSet<Long>()
        tracks.forEach { track -> densify(track, CELL_METERS / 2).forEach { cells += cell(it) } }
        val pieces = mutableListOf<PcnPiece>()
        val progress = loops.map { loop ->
            var ridden = 0.0
            var total = 0.0
            for (line in loop.lines) {
                val dense = densify(line, STEP_METERS)
                if (dense.size < 2) continue
                var state = near(cells, dense[0])
                var piece = mutableListOf(dense[0])
                for (i in 1 until dense.size) {
                    val p = dense[i]
                    val d = Geo.distance(dense[i - 1], p)
                    val here = near(cells, p)
                    total += d
                    // 两头都骑过才算这一小段骑过
                    if (here && state) ridden += d
                    piece += p
                    if (here != state) {
                        pieces += PcnPiece(state, piece)
                        piece = mutableListOf(p)
                        state = here
                    }
                }
                if (piece.size >= 2) pieces += PcnPiece(state, piece)
            }
            LoopProgress(loop, ridden, total)
        }
        return PcnProgress(progress, pieces)
    }

    /** 两点隔得比 [step] 远就在中间补点 */
    private fun densify(line: List<LatLon>, step: Double): List<LatLon> {
        if (line.size < 2) return line
        val result = mutableListOf(line[0])
        for (i in 1 until line.size) {
            val a = line[i - 1]
            val b = line[i]
            val n = (Geo.distance(a, b) / step).toInt()
            for (s in 1..n) {
                val t = s.toDouble() / (n + 1)
                result += LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            }
            result += b
        }
        return result
    }
}
