package com.personal.rando.places

import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import com.personal.rando.ui.Format
import org.json.JSONObject
import java.util.PriorityQueue

/** MRT / 轻轨站：站名、编号（换乘站有几个，比如 NS17、CC15）、位置、出口 */
data class MrtStation(
    val name: String,
    val zh: String,
    val codes: List<String>,
    val location: LatLon,
    val exits: List<MrtExit> = emptyList(),
) {
    /** 界面上显示的站名：中文界面用 [zh]，英文界面用 [name] */
    val label: String get() = tr(zh, name)

    /** 离 [p] 最近的出口；没有出口数据就是车站本身 */
    fun nearestExit(p: LatLon): MrtExit =
        exits.minByOrNull { Geo.distance(it.location, p) } ?: MrtExit("", location)
}

data class MrtExit(val name: String, val location: LatLon)

/** 坐一段：哪条线，从哪站到哪站，坐几站 */
data class MrtLeg(val line: String, val from: MrtStation, val to: MrtStation, val stops: Int)

data class MrtTrip(val legs: List<MrtLeg>) {
    val stops: Int get() = legs.sumOf { it.stops }
    val transfers: Int get() = (legs.size - 1).coerceAtLeast(0)

    /** 大概多久：一站 2 分钟，换乘 5 分钟，再加等车 3 分钟 */
    val minutes: Int get() = stops * 2 + transfers * 5 + 3

    /** 「坐东西线 金文泰 → 裕廊东，换南北线 → 碧山」 */
    val text: String
        get() = legs.mapIndexed { i, leg ->
            val line = MrtLines.name(leg.line)
            val head = if (i == 0) tr("坐$line ${leg.from.zh} → ${leg.to.zh}", "Take the $line ${leg.from.name} → ${leg.to.name}")
            else tr("换$line → ${leg.to.zh}", "change to the $line → ${leg.to.name}")
            head
        }.joinToString(tr("，", ", "))
}

object MrtLines {
    private val NAMES = mapOf(
        "NS" to "南北线", "EW" to "东西线", "CG" to "东西线机场支线", "NE" to "东北线", "CC" to "环线",
        "DT" to "滨海市区线", "TE" to "汤申-东海岸线", "BP" to "武吉班让轻轨",
        "SE" to "盛港轻轨", "SW" to "盛港轻轨", "STC" to "盛港轻轨", "PE" to "榜鹅轻轨", "PW" to "榜鹅轻轨", "PTC" to "榜鹅轻轨",
    )
    private val EN = mapOf(
        "NS" to "North South Line", "EW" to "East West Line", "CG" to "Changi Airport Branch", "NE" to "North East Line", "CC" to "Circle Line",
        "DT" to "Downtown Line", "TE" to "Thomson-East Coast Line", "BP" to "Bukit Panjang LRT",
        "SE" to "Sengkang LRT", "SW" to "Sengkang LRT", "STC" to "Sengkang LRT", "PE" to "Punggol LRT", "PW" to "Punggol LRT", "PTC" to "Punggol LRT",
    )

    fun name(line: String) = (if (Lang.english) EN[line] else NAMES[line]) ?: line

    /** 「NS17」→ ("NS", 17)；「STC」「CG」这种没数字的当 0 */
    fun split(code: String): Pair<String, Int> {
        val line = code.takeWhile { it.isLetter() }
        return line to (code.drop(line.length).toIntOrNull() ?: 0)
    }
}

object MrtData {
    fun parse(json: String): List<MrtStation> {
        val arr = JSONObject(json).getJSONArray("stations")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val codes = o.getJSONArray("c")
            val exits = o.getJSONArray("x")
            MrtStation(
                name = o.getString("n"),
                zh = o.optString("zh", o.getString("n")),
                codes = (0 until codes.length()).map { codes.getString(it) },
                location = LatLon(o.getDouble("lat"), o.getDouble("lon")),
                exits = (0 until exits.length()).map { exits.getJSONArray(it).let { e -> MrtExit(e.optString(2), LatLon(e.getDouble(0), e.getDouble(1))) } },
            )
        }
    }
}

/**
 * MRT 网：同一条线按编号相邻的两站连起来（编号空着的跳过），几个环线、轻轨的接头另外补上。
 * 算路按坐的站数，换乘一次算 3 站，挑站少、换乘少的
 */
class MrtNetwork(val stations: List<MrtStation>) {

    /** 每站每条线一个点：(站, 线) */
    private data class Node(val station: Int, val line: String)

    private val edges = HashMap<Node, MutableList<Node>>()

    init {
        // 每条线上的站按编号排
        val byLine = HashMap<String, MutableList<Pair<Int, Int>>>()
        stations.forEachIndexed { i, s ->
            s.codes.forEach { code ->
                val (line, n) = MrtLines.split(code)
                byLine.getOrPut(line) { mutableListOf() } += n to i
            }
        }
        for ((line, list) in byLine) {
            list.sortBy { it.first }
            list.zipWithNext { a, b -> link(line, a.second, b.second) }
        }
        fun stationOf(code: String) = stations.indexOfFirst { code in it.codes }.takeIf { it >= 0 }
        fun last(line: String) = byLine[line]?.maxByOrNull { it.first }?.second
        // 环线：Promenade（CC4）接 Bayfront（CC34）
        stationOf("CC4")?.let { a -> stationOf("CC34")?.let { b -> link("CC", a, b) } }
        // 武吉班让轻轨：最后一站绕回 Bukit Panjang（BP6）
        last("BP")?.let { a -> stationOf("BP6")?.let { b -> link("BP", a, b) } }
        // 盛港、榜鹅轻轨：两个环都从总站出发、绕一圈回来
        for ((hub, loops) in listOf("STC" to listOf("SE", "SW"), "PTC" to listOf("PE", "PW"))) {
            val h = stationOf(hub) ?: continue
            for (loop in loops) {
                byLine[loop]?.minByOrNull { it.first }?.second?.let { link(loop, h, it) }
                last(loop)?.let { link(loop, h, it) }
            }
        }
    }

    private fun link(line: String, a: Int, b: Int) {
        if (a == b) return
        edges.getOrPut(Node(a, line)) { mutableListOf() } += Node(b, line)
        edges.getOrPut(Node(b, line)) { mutableListOf() } += Node(a, line)
    }

    /** 每站有哪些线 */
    private fun lines(i: Int): Set<String> = stations[i].codes.map { MrtLines.split(it).first }.toSet() +
        edges.keys.filter { it.station == i }.map { it.line }

    /** 离 [p] 最近的站（按最近的出口算） */
    fun nearest(p: LatLon): MrtStation = stations.minBy { Geo.distance(it.nearestExit(p).location, p) }

    /** 从 [from] 坐到 [to]；到不了返回 null */
    fun trip(from: MrtStation, to: MrtStation): MrtTrip? {
        val a = stations.indexOf(from)
        val b = stations.indexOf(to)
        if (a < 0 || b < 0) return null
        if (a == b) return MrtTrip(emptyList())
        val cost = HashMap<Node, Int>()
        val prev = HashMap<Node, Node>()
        val queue = PriorityQueue<Pair<Int, Node>>(compareBy { it.first })
        lines(a).forEach { l -> Node(a, l).let { cost[it] = 0; queue += 0 to it } }
        var end: Node? = null
        while (queue.isNotEmpty()) {
            val (c, n) = queue.poll()!!
            if (c > (cost[n] ?: Int.MAX_VALUE)) continue
            if (n.station == b) {
                end = n
                break
            }
            val next = edges[n].orEmpty().map { it to c + 1 } +
                lines(n.station).filter { it != n.line }.map { Node(n.station, it) to c + TRANSFER_COST }
            for ((m, mc) in next) {
                if (mc < (cost[m] ?: Int.MAX_VALUE)) {
                    cost[m] = mc
                    prev[m] = n
                    queue += mc to m
                }
            }
        }
        val path = generateSequence(end ?: return null) { prev[it] }.toList().reversed()
        // 连着同一条线的并成一段；在同一站换线的那一步不算站
        val legs = mutableListOf<MrtLeg>()
        var start = path.first()
        var stops = 0
        for ((p, q) in path.zipWithNext()) {
            if (p.station == q.station) {
                if (stops > 0) legs += MrtLeg(p.line, stations[start.station], stations[p.station], stops)
                start = q
                stops = 0
            } else {
                stops++
            }
        }
        if (stops > 0) legs += MrtLeg(path.last().line, stations[start.station], stations[path.last().station], stops)
        return MrtTrip(legs)
    }

    private companion object {
        const val TRANSFER_COST = 3
    }
}

/** 骑累了坐 MRT 回家：骑到哪个站、坐到哪个站、再骑多远 */
data class MrtHome(
    val board: MrtStation,
    val boardExit: MrtExit,
    val toStationMeters: Int,
    val alight: MrtStation,
    val alightExit: MrtExit,
    val fromStationMeters: Int,
    val trip: MrtTrip,
) {
    val text: String
        get() {
            if (Lang.english) {
                val exit = boardExit.name.takeIf { it.isNotEmpty() }?.let { " (Exit $it)" }.orEmpty()
                val stops = if (trip.stops == 1) "1 stop" else "${trip.stops} stops"
                return "Ride ${Format.distance(toStationMeters)} to ${board.name} station$exit, " +
                    "${trip.text} ($stops, about ${trip.minutes} min), " +
                    "then ride ${Format.distance(fromStationMeters)} home"
            }
            val exit = boardExit.name.takeIf { it.isNotEmpty() }?.let { "（$it 出口）" }.orEmpty()
            return "骑 ${Format.distance(toStationMeters)} 到${board.zh}站$exit，" +
                "${trip.text}（${trip.stops} 站，约 ${trip.minutes} 分钟），" +
                "出站再骑 ${Format.distance(fromStationMeters)} 到家"
        }

    companion object {
        /** 离家这么近就直接骑回去 */
        const val RIDE_HOME_METERS = 3_000.0

        /** 算一下；离家很近或者最近的站就是家门口的站，返回 null（直接骑回家） */
        fun plan(network: MrtNetwork, me: LatLon, home: LatLon): MrtHome? {
            if (Geo.distance(me, home) < RIDE_HOME_METERS) return null
            val board = network.nearest(me)
            val alight = network.nearest(home)
            if (board == alight) return null
            val trip = network.trip(board, alight) ?: return null
            val boardExit = board.nearestExit(me)
            val alightExit = alight.nearestExit(home)
            return MrtHome(
                board, boardExit, Geo.distance(me, boardExit.location).toInt(),
                alight, alightExit, Geo.distance(alightExit.location, home).toInt(), trip,
            )
        }
    }
}
