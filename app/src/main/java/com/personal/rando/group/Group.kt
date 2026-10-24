package com.personal.rando.group

import com.personal.rando.ride.Fix
import com.personal.rando.routing.LatLon
import org.json.JSONObject
import kotlin.random.Random

/** 一起骑的服务器（server/ 部署到 Cloudflare 后的地址）；空的说明还没部署 */
object GroupConfig {
    const val SERVER_URL = ""
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

/** 收到的消息 */
sealed interface GroupEvent {
    data class Roster(val members: List<Teammate>) : GroupEvent
    data class Joined(val id: String, val name: String) : GroupEvent
    data class Left(val id: String) : GroupEvent
    data class Position(val mate: Teammate) : GroupEvent
    /** 对讲语音：[data] 是 base64 的音频 */
    data class Voice(val id: String, val name: String, val format: String, val millis: Int, val data: String) : GroupEvent
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

    fun voice(format: String, millis: Int, base64: String): String =
        JSONObject().put("t", "voice").put("format", format).put("ms", millis).put("data", base64).toString()

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
            "voice" -> GroupEvent.Voice(o.getString("id"), o.optString("name", "队友"), o.optString("format"), o.optInt("ms"), o.getString("data"))
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
