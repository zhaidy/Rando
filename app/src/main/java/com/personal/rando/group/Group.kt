package com.personal.rando.group

import com.personal.rando.ride.Fix
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.RoutePrefs
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** 一起骑的服务器：server/ 部署到 Cloudflare 后的地址（rando-group 加上账号的 workers.dev 子域名） */
object GroupConfig {
    const val SERVER_URL = "https://rando-group.dongyuzhai.workers.dev"
}

/** 一个队友：最后一次报的位置 */
data class Teammate(
    val id: String,
    val name: String,
    val location: LatLon? = null,
    /** 米/秒 */
    val speed: Double? = null,
    val bearing: Double? = null,
    /** 收到这个位置的时间（本机时间） */
    val updatedAt: Long = 0,
) {
    /** 太久没报位置：可能没信号了 */
    fun stale(now: Long): Boolean = now - updatedAt > STALE_MS

    companion object {
        const val STALE_MS = 60_000L
    }
}

/**
 * 分享的路线：起点、途经点、终点和路线配置。队友按同样的点和配置找 BRouter 算，得到同一条路
 * （只传几个点，不传整条路线）
 */
data class SharedRoute(
    /** 分享的人；自己发出去的时候不用填 */
    val id: String = "",
    val name: String = "",
    /** 分享的时间，用来认出是不是同一次分享（断线重连会再收到） */
    val ts: Long,
    /** 去哪儿：「Marina Bay Sands」「骑一圈」「原路返回」 */
    val title: String,
    val meters: Int,
    val stops: List<LatLon>,
    val profile: BikeProfile,
    val avoidRoads: Boolean,
    val avoidBridges: Boolean,
) {
    /** 跟着骑时用的配置：路线配置照分享的，预计时间还按我自己的速度 */
    fun prefs(mine: RoutePrefs): RoutePrefs = mine.copy(profile = profile, avoidRoads = avoidRoads, avoidBridges = avoidBridges)
}

/** 收到的消息 */
sealed interface GroupEvent {
    data class Roster(val members: List<Teammate>) : GroupEvent
    data class Joined(val id: String, val name: String) : GroupEvent
    data class Left(val id: String) : GroupEvent
    data class Position(val mate: Teammate) : GroupEvent
    /**
     * 对讲语音的一小段（0.1 秒多）：[data] 是 base64 的几帧 AAC；
     * [stream] 是这一句话的编号，换一句就换；[end] 表示这句说完了
     */
    data class Voice(
        val id: String,
        val name: String,
        val format: String,
        val stream: Int,
        val seq: Int,
        val end: Boolean,
        val data: String,
    ) : GroupEvent
    data class Route(val route: SharedRoute) : GroupEvent
    data class RouteCleared(val id: String) : GroupEvent
}

/** 和 server/src/index.js 约定的消息格式 */
object GroupProtocol {

    fun position(fix: Fix): String = JSONObject().apply {
        put("t", "pos")
        put("lat", fix.location.lat)
        put("lon", fix.location.lon)
        fix.speed?.let { put("speed", it.toDouble()) }
        fix.bearing?.let { put("bearing", it.toDouble()) }
    }.toString()

    fun voice(stream: Int, seq: Int, end: Boolean, base64: String): String =
        JSONObject()
            .put("t", "voice").put("format", AacCodec.FORMAT)
            .put("sid", stream).put("seq", seq).put("end", end)
            .put("data", base64)
            .toString()

    fun route(r: SharedRoute): String = JSONObject().apply {
        put("t", "route")
        put("ts", r.ts)
        put("title", r.title)
        put("meters", r.meters)
        put("profile", r.profile.name)
        put("avoidRoads", r.avoidRoads)
        put("avoidBridges", r.avoidBridges)
        put("stops", JSONArray().apply { r.stops.forEach { put(JSONArray().put(it.lat).put(it.lon)) } })
    }.toString()

    const val ROUTE_CLEAR = """{"t":"route_clear"}"""

    /** 认不出来的消息返回 null */
    fun parse(text: String, now: Long): GroupEvent? = try {
        val o = JSONObject(text)
        when (o.optString("t")) {
            "roster" -> {
                val list = o.getJSONArray("members")
                GroupEvent.Roster((0 until list.length()).map { mate(list.getJSONObject(it), now) })
            }
            "join" -> GroupEvent.Joined(o.getString("id"), o.optString("name", "队友"))
            "leave" -> GroupEvent.Left(o.getString("id"))
            "pos" -> GroupEvent.Position(mate(o, now))
            "voice" -> GroupEvent.Voice(
                o.getString("id"), o.optString("name", "队友"), o.optString("format"),
                o.optInt("sid"), o.optInt("seq"), o.optBoolean("end"), o.optString("data"),
            )
            "route" -> {
                val list = o.getJSONArray("stops")
                GroupEvent.Route(
                    SharedRoute(
                        id = o.getString("id"),
                        name = o.optString("name", "队友"),
                        ts = o.getLong("ts"),
                        title = o.optString("title"),
                        meters = o.optInt("meters"),
                        stops = (0 until list.length()).map { list.getJSONArray(it).let { p -> LatLon(p.getDouble(0), p.getDouble(1)) } },
                        // 不认识的配置（新版本加的）按公园连道算
                        profile = BikeProfile.entries.firstOrNull { it.name == o.optString("profile") } ?: BikeProfile.PCN,
                        avoidRoads = o.optBoolean("avoidRoads"),
                        avoidBridges = o.optBoolean("avoidBridges"),
                    )
                )
            }
            "route_clear" -> GroupEvent.RouteCleared(o.getString("id"))
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun mate(o: JSONObject, now: Long): Teammate {
        val located = o.has("lat") && o.has("lon")
        return Teammate(
            id = o.getString("id"),
            name = o.optString("name", "队友"),
            location = if (located) LatLon(o.getDouble("lat"), o.getDouble("lon")) else null,
            speed = o.optDouble("speed").takeIf { !it.isNaN() },
            bearing = o.optDouble("bearing").takeIf { !it.isNaN() },
            updatedAt = if (located) now else 0,
        )
    }
}

/** 队伍码：6 位，去掉容易看错的 0 O 1 I */
object TeamCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random.Default): String = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** 用户输进来的：去掉空格，转大写；不像队伍码返回 null */
    fun normalize(input: String): String? = input.filterNot { it.isWhitespace() || it == '-' }.uppercase().takeIf { Regex("[A-Z0-9]{4,12}").matches(it) }
}
