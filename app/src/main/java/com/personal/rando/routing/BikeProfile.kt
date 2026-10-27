package com.personal.rando.routing

import com.personal.rando.i18n.tr

/**
 * 车型配置。「公园连道」是 Rando 自己的配置（见 [RandoProfiles]），要先上传；
 * 其余对应 BRouter 服务器自带的配置
 */
enum class BikeProfile(private val zh: String, private val en: String, val brouterName: String, val custom: Boolean = false) {
    PCN("公园连道", "Park connectors", "", custom = true),
    SAFE("安全优先", "Safest", "safety"),
    CITY("城市车", "City bike", "trekking"),
    ROAD("公路车", "Road bike", "fastbike"),
    MTB("山地车", "Mountain bike", "mtb");

    val label: String get() = tr(zh, en)
}

/** 路线偏好：车型配置，加几个只对「公园连道」起作用的开关，和预计时间按什么速度算 */
data class RoutePrefs(
    val profile: BikeProfile = BikeProfile.PCN,
    val avoidRoads: Boolean = false,
    val avoidBridges: Boolean = false,
    /** 少过马路：宁可绕一点 */
    val avoidCrossings: Boolean = false,
    val pace: Pace = Pace.MINE,
    /** 我骑过的平均速度（米/秒），骑得还不够多时是 null，这时「按我的速度」用 BRouter 的默认 */
    val mySpeed: Double? = null,
    /** 中午要不要挑有树荫的路（用户的开关） */
    val shadeAtNoon: Boolean = true,
    /** 天黑要不要挑有路灯的路（用户的开关） */
    val litAtNight: Boolean = true,
    /** 中午：挑有树荫的路（算路时按时间和 [shadeAtNoon] 定，不存） */
    val shade: Boolean = false,
    /** 天黑：挑有路灯的路（算路时按时间和 [litAtNight] 定，不存） */
    val night: Boolean = false,
) {
    /** 预计时间：选了档位时 BRouter 已经按那个功率算好了；按我的速度就用距离除以平均速度 */
    fun duration(route: Route): Int {
        val speed = mySpeed?.takeIf { pace == Pace.MINE && it > 0 } ?: return route.durationSeconds
        return (route.distanceMeters / speed).toInt()
    }

    /** 要上传的配置内容；用服务器自带的配置时是 null */
    val customProfile: String?
        get() = if (profile.custom) RandoProfiles.pcn(avoidRoads, avoidBridges, shade, night, avoidCrossings) else null
}
