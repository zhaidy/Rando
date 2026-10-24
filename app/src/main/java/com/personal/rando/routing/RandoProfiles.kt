package com.personal.rando.routing

/**
 * Rando 自己的 BRouter 路线配置：新加坡公园连道（PCN）优先。
 * brouter.de 自带的配置不认识 PCN；PCN 在 OpenStreetMap 里是本地自行车路线（lcn），
 * 这里让它代价最低，自行车道、人行道次之，马路最高。
 * 「不上马路」模式把马路的代价再乘 15（有人行道的马路按人行道算，新加坡允许在人行道上骑车）。
 * 算路前先上传到服务器，换回一个 custom_xxx 的配置名。
 */
object RandoProfiles {

    fun pcn(avoidRoads: Boolean): String =
        PCN.replace("assign avoidRoads = 0", "assign avoidRoads = ${if (avoidRoads) 1 else 0}")

    private val PCN = """
# Rando：新加坡公园连道（PCN）优先
---context:global
assign validForBikes = true
# 1 = 尽量不上马路（马路代价大很多），0 = 只是偏好公园连道
assign avoidRoads = 0
# 路口转向提示（导航播报要用），和 BRouter 自带的配置一样
assign turnInstructionMode = 1
assign turnInstructionCatchingRange = 40
assign turnInstructionRoundabouts = true

---context:way
assign isPcn = or route_bicycle_lcn=yes or route_bicycle_rcn=yes or route_bicycle_ncn=yes lcn=yes
assign bikeForbidden = bicycle=no|dismount
assign isPath = highway=cycleway|footway|path|pedestrian|track|bridleway
assign isRoad = highway=residential|living_street|service|unclassified|road|tertiary|tertiary_link|secondary|secondary_link|primary|primary_link|trunk|trunk_link
assign hasFootpath = sidewalk=both|left|right|yes
assign privateAccess = and access=private|no not bicycle=yes|designated|permissive
assign wrongWay = and reversedirection=yes oneway=yes|true|1

assign roadFactor
  if ( highway=residential|living_street|service|unclassified|road ) then 1.6
  else if ( highway=tertiary|tertiary_link ) then 2.5
  else if ( highway=secondary|secondary_link ) then 4
  else if ( highway=primary|primary_link ) then 6
  else 10

assign roadCost
  if ( avoidRoads ) then ( if ( hasFootpath ) then 3 else multiply roadFactor 15 )
  else roadFactor

assign costfactor
  if ( highway= ) then 10000
  else if ( highway=motorway|motorway_link|proposed|abandoned|construction ) then 10000
  else if ( privateAccess ) then 10000
  else if ( and isRoad wrongWay ) then 10000
  else if ( highway=steps ) then 20
  else if ( and isPath bikeForbidden ) then 5
  else if ( and isPath isPcn ) then 1
  else if ( highway=cycleway ) then 1.1
  else if ( isPath ) then 1.3
  else if ( isRoad ) then roadCost
  # 商场走廊、站台之类：能走，推车过去
  else 5

# 下面这些是生成转向提示用的，照抄 BRouter 自带的 trekking 配置
assign priorityclassifier
  if ( highway=motorway ) then 30
  else if ( highway=motorway_link ) then 29
  else if ( highway=trunk ) then 28
  else if ( highway=trunk_link ) then 27
  else if ( highway=primary ) then 26
  else if ( highway=primary_link ) then 25
  else if ( highway=secondary ) then 24
  else if ( highway=secondary_link ) then 23
  else if ( highway=tertiary ) then 22
  else if ( highway=tertiary_link ) then 21
  else if ( highway=unclassified ) then 20
  else if ( highway=residential|living_street|service|cycleway ) then 6
  else if ( bicycle=designated ) then 6
  else if ( highway=track ) then 4
  else if ( highway=bridleway|road|path|footway ) then 4
  else if ( highway=steps|pedestrian ) then 2
  else 0

assign isbadoneway = and isRoad wrongWay
assign isgoodoneway = if reversedirection=yes then oneway=-1
                      else if oneway= then junction=roundabout else oneway=yes|true|1
assign isroundabout = junction=roundabout
assign islinktype = highway=motorway_link|trunk_link|primary_link|secondary_link|tertiary_link
assign isgoodforcars = if greater priorityclassifier 6 then true
                       else if ( highway=residential|living_street|service ) then true
                       else false

assign classifiermask add          isbadoneway
                      add multiply isgoodoneway   2
                      add multiply isroundabout   4
                      add multiply islinktype     8
                          multiply isgoodforcars 16

# 用到才会出现在返回的 WayTags 里：路线卡片要靠它认出单独画的斑马线
assign isCrossingWay = or footway=crossing cycleway=crossing

assign turncost = 20
assign initialcost = 0

---context:node
assign initialcost = 0
""".trimIndent() + "\n"
}
