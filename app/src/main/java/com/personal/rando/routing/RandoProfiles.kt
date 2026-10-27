package com.personal.rando.routing

/**
 * Rando 自己的 BRouter 路线配置：新加坡公园连道（PCN）优先。
 * brouter.de 自带的配置不认识 PCN；PCN 在 OpenStreetMap 里是本地自行车路线（lcn），
 * 这里让它代价最低，自行车道、人行道次之，马路最高。
 * 「不上马路」模式把马路的代价再乘 15（有人行道的马路按人行道算，新加坡允许在人行道上骑车）；
 * 「不走天桥」把人行天桥的代价乘 20（公园连道、自行车道上的桥不算天桥，跨河的平桥照走）。
 * 「少过马路」每过一个路口点（斑马线、红绿灯）、每进一段单独画的斑马线都加一笔代价，
 * 相当于多骑 [CROSSING_COST] 米公园连道，宁可绕一点也少过几次。
 * 中午（[shade]）没树荫的路贵一点，挑树多的走；天黑（[night]）没路灯的小路贵一点。
 * 树荫用的是 BRouter 自带的 estimated_forest_class（按路两边的树林、绿地估的 1–6 级）。
 * 算路前先上传到服务器，换回一个 custom_xxx 的配置名。
 */
object RandoProfiles {

    fun pcn(avoidRoads: Boolean, avoidBridges: Boolean, shade: Boolean = false, night: Boolean = false, avoidCrossings: Boolean = false): String =
        PCN.replace("assign avoidRoads = 0", "assign avoidRoads = ${if (avoidRoads) 1 else 0}")
            .replace("assign avoidBridges = 0", "assign avoidBridges = ${if (avoidBridges) 1 else 0}")
            .replace("assign avoidCrossings = 0", "assign avoidCrossings = ${if (avoidCrossings) 1 else 0}")
            .replace("assign shade = 0", "assign shade = ${if (shade) 1 else 0}")
            .replace("assign night = 0", "assign night = ${if (night) 1 else 0}")

    /** 「少过马路」过一次马路的代价，相当于多骑这么多米公园连道 */
    const val CROSSING_COST = 100

    private val PCN = """
# Rando：新加坡公园连道（PCN）优先
---context:global
assign validForBikes = true
# 1 = 尽量不上马路（马路代价大很多），0 = 只是偏好公园连道
assign avoidRoads = 0
# 1 = 尽量不走人行天桥
assign avoidBridges = 0
# 1 = 少过马路：每过一次加一笔代价
assign avoidCrossings = 0
# 1 = 中午，挑有树荫的路
assign shade = 0
# 1 = 天黑，挑有路灯的路
assign night = 0
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

# 人行天桥：步行道上的桥，公园连道和允许骑车的桥不算；地下通道顺便引用，返回的 WayTags 里才有 tunnel
assign isOverpass = and bridge=yes and highway=footway|path|pedestrian|steps|corridor not or isPcn bicycle=yes|designated
assign isUnderpass = and tunnel=yes isPath

assign baseCost
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

# 树荫：4–6 级树多，2–3 级有一些，1 级和没有的基本晒着
assign shadeFactor
  if ( not shade ) then 1
  else if ( estimated_forest_class=4|5|6 ) then 1
  else if ( estimated_forest_class=2|3 ) then 1.15
  else 1.3

# 路灯：新加坡的马路都有灯，只看小路
assign litFactor
  if ( not night ) then 1
  else if ( or lit=yes isRoad ) then 1
  else 1.3

assign costfactor
  multiply shadeFactor multiply litFactor
  if ( and avoidBridges isOverpass ) then multiply baseCost 20
  else baseCost

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

# 用到才会出现在返回的 WayTags 里：路线卡片要靠它认出单独画的斑马线，算树荫、路灯占多少
assign isCrossingWay = or footway=crossing cycleway=crossing
assign hasForest = estimated_forest_class=1|2|3|4|5|6
assign isLit = lit=yes

assign turncost = 20
# 少过马路：单独画的斑马线（路口点另外在下面算）
assign initialcost if ( and avoidCrossings isCrossingWay ) then $CROSSING_COST else 0

---context:node
# 少过马路：斑马线、红绿灯这些路口点
assign isCrossingNode = or highway=crossing|traffic_signals crossing=traffic_signals|zebra|marked|uncontrolled|unmarked|yes
assign initialcost if ( and avoidCrossings isCrossingNode ) then $CROSSING_COST else 0
""".trimIndent() + "\n"
}
