package com.personal.rando.routing

/**
 * 路线偏好。前两个是 Rando 自己的配置（见 [RandoProfiles]），要先上传；
 * 其余对应 BRouter 服务器自带的配置
 */
enum class BikeProfile(val label: String, val brouterName: String, val custom: String? = null) {
    PCN("公园连道", "", RandoProfiles.pcn(avoidRoads = false)),
    NO_ROADS("不上马路", "", RandoProfiles.pcn(avoidRoads = true)),
    SAFE("安全优先", "safety"),
    CITY("城市车", "trekking"),
    ROAD("公路车", "fastbike"),
    MTB("山地车", "mtb"),
}
