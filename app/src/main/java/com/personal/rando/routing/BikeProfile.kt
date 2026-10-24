package com.personal.rando.routing

/** 车型对应 BRouter 服务器上的路线配置 */
enum class BikeProfile(val label: String, val brouterName: String) {
    ROAD("公路车", "fastbike"),
    CITY("城市车", "trekking"),
    SAFE("安全优先", "safety"),
    MTB("山地车", "mtb"),
}
