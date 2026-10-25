package com.personal.rando.group

import com.personal.rando.i18n.tr
import com.personal.rando.ride.Fix
import com.personal.rando.routing.LatLon
import com.personal.rando.places.Hazard
import com.personal.rando.places.HazardJson
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

/** 队友可能摔车了：在哪儿、什么时候 */
data class SosAlert(val id: String, val name: String, val location: LatLon, val ts: Long)

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
    /** WebRTC 直连的信令（[RtcLink]）：[id] 是谁发的 */
    data class Rtc(val id: String, val data: JSONObject) : GroupEvent
    data class RouteCleared(val id: String) : GroupEvent
    data class Sos(val alert: SosAlert) : GroupEvent
    data class SosCleared(val id: String) : GroupEvent
    /** 队友标了一处路况；[hazard] 的 by 是队友的名字 */
    data class HazardAdded(val hazard: Hazard) : GroupEvent
    data class HazardRemoved(val hazardId: String) : GroupEvent
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

    /** 直连发给队友的语音：没有服务器帮忙填，自己带上 id 和名字（跟服务器转发的一样） */
    fun localVoice(id: String, name: String, stream: Int, seq: Int, end: Boolean, base64: String): String =
        JSONObject(voice(stream, seq, end, base64)).put("id", id).put("name", name).toString()

    /** WebRTC 直连的信令：[to] 是 null 就发给队里所有人 */
    fun rtc(to: String?, data: JSONObject): String =
        JSONObject().put("t", "rtc").apply { if (to != null) put("to", to) }.put("data", data).toString()

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

    fun sos(at: LatLon): String = JSONObject().put("t", "sos").put("lat", at.lat).put("lon", at.lon).toString()

    const val SOS_CLEAR = """{"t":"sos_clear"}"""

    /** 路况自己的 id 放在 hid 里，id 是服务器填的「谁标的」 */
    fun hazard(h: Hazard): String = HazardJson.encode(h).apply {
        put("hid", remove("id"))
        remove("by")
        put("t", "hazard")
    }.toString()

    fun hazardRemoved(id: String): String = JSONObject().put("t", "hazard_del").put("hid", id).toString()

    /** 认不出来的消息返回 null */
    fun parse(text: String, now: Long): GroupEvent? = try {
        val o = JSONObject(text)
        when (o.optString("t")) {
            "roster" -> {
                val list = o.getJSONArray("members")
                // 位置是服务器上存的最后一个，可能是很久以前的：按服务器时间算它多旧（老服务器没给就跟本机时间比）
                val serverNow = o.optLong("now", now)
                GroupEvent.Roster((0 until list.length()).map { i ->
                    val m = list.getJSONObject(i)
                    val age = m.optLong("ts", 0).takeIf { it > 0 }?.let { (serverNow - it).coerceAtLeast(0) }
                    mate(m, now).let { mate -> if (mate.location != null) mate.copy(updatedAt = age?.let { now - it } ?: 0) else mate }
                })
            }
            "join" -> GroupEvent.Joined(o.getString("id"), o.optString("name", MATE))
            "leave" -> GroupEvent.Left(o.getString("id"))
            "pos" -> GroupEvent.Position(mate(o, now))
            "voice" -> GroupEvent.Voice(
                o.getString("id"), o.optString("name", MATE), o.optString("format"),
                o.optInt("sid"), o.optInt("seq"), o.optBoolean("end"), o.optString("data"),
            )
            "route" -> {
                val list = o.getJSONArray("stops")
                GroupEvent.Route(
                    SharedRoute(
                        id = o.getString("id"),
                        name = o.optString("name", MATE),
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
            "rtc" -> GroupEvent.Rtc(o.getString("id"), o.getJSONObject("data"))
            "route_clear" -> GroupEvent.RouteCleared(o.getString("id"))
            "sos" -> GroupEvent.Sos(
                SosAlert(o.getString("id"), o.optString("name", MATE), LatLon(o.getDouble("lat"), o.getDouble("lon")), o.optLong("ts")),
            )
            "sos_clear" -> GroupEvent.SosCleared(o.getString("id"))
            "hazard" -> HazardJson.decode(JSONObject(o.toString()).put("id", o.getString("hid")).put("by", o.optString("name", MATE)))
                ?.let { GroupEvent.HazardAdded(it) }
            "hazard_del" -> GroupEvent.HazardRemoved(o.getString("hid"))
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun mate(o: JSONObject, now: Long): Teammate {
        val located = o.has("lat") && o.has("lon")
        return Teammate(
            id = o.getString("id"),
            name = o.optString("name", MATE),
            location = if (located) LatLon(o.getDouble("lat"), o.getDouble("lon")) else null,
            speed = o.optDouble("speed").takeIf { !it.isNaN() },
            bearing = o.optDouble("bearing").takeIf { !it.isNaN() },
            updatedAt = if (located) now else 0,
        )
    }
}

/** 队伍码：6 位，去掉容易看错的 0 O 1 I */
/** 附近的一个队伍：队伍码、队里的人、离我多远 */
data class NearbyTeam(val code: String, val names: List<String>, val meters: Int) {
    /** 「阿明、小红 等 4 人」 */
    val namesText: String
        get() = names.take(3).joinToString(tr("、", ", ")) + if (names.size > 3) tr(" 等 ${names.size} 人", " and ${names.size - 3} more") else ""

    companion object {
        /** 服务器 /nearby 返回的 {"teams":[{code,names,members,meters}]} */
        fun parse(json: String): List<NearbyTeam> {
            val teams = JSONObject(json).optJSONArray("teams") ?: return emptyList()
            return (0 until teams.length()).map { i ->
                val t = teams.getJSONObject(i)
                val names = t.optJSONArray("names") ?: JSONArray()
                NearbyTeam(t.getString("code"), (0 until names.length()).map { names.getString(it) }, t.optInt("meters"))
            }
        }
    }
}

object TeamCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random.Default): String = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** 用户输进来的：去掉空格，转大写；不像队伍码返回 null */
    fun normalize(input: String): String? = input.filterNot { it.isWhitespace() || it == '-' }.uppercase().takeIf { Regex("[A-Z0-9]{4,12}").matches(it) }
}

/** 没报名字的队友 */
private val MATE: String get() = tr("队友", "Teammate")
