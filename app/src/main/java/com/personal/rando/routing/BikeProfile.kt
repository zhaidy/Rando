package com.personal.rando.routing

/**
 * 车型配置。「公园连道」是 Rando 自己的配置（见 [RandoProfiles]），要先上传；
 * 其余对应 BRouter 服务器自带的配置
 */
enum class BikeProfile(val label: String, val brouterName: String, val custom: Boolean = false) {
    PCN("公园连道", "", custom = true),
    SAFE("安全优先", "safety"),
    CITY("城市车", "trekking"),
    ROAD("公路车", "fastbike"),
    MTB("山地车", "mtb"),
}

/** 路线偏好：车型配置，加两个只对「公园连道」起作用的开关 */
data class RoutePrefs(
    val profile: BikeProfile = BikeProfile.PCN,
    val avoidRoads: Boolean = false,
    val avoidBridges: Boolean = false,
) {
    /** 要上传的配置内容；用服务器自带的配置时是 null */
    val customProfile: String?
        get() = if (profile.custom) RandoProfiles.pcn(avoidRoads, avoidBridges) else null
}
