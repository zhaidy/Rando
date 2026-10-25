package com.personal.rando.ui

/**
 * 骑行视图的地图样式：黑底，马路画成灰色描边（灰线上再压一条细黑线），
 * 小路、公园连道画成暗一点的灰色单线；路都压暗，路线才显眼。不要文字、建筑，晃着看也一目了然。
 * 数据还是 OpenFreeMap 的矢量瓦片（OpenMapTiles 的 transportation 图层）
 */
object RideMapStyle {

    /** 道路按等级的宽度（米级缩放 17 时大约的像素宽），越大的路越宽 */
    private const val ROAD_BASE = """["match", ["get", "class"],
        "motorway", 20, "trunk", 18, "primary", 16, "secondary", 14, "tertiary", 13,
        "minor", 10, "service", 6, 8]"""

    private fun width(base: String, scale: Double, minus: Double = 0.0): String =
        """["interpolate", ["exponential", 1.5], ["zoom"],
            12, ["max", 0.5, ["-", ["*", $base, ${0.12 * scale}], ${minus * 0.3}]],
            18, ["max", 0.5, ["-", ["*", $base, ${1.6 * scale}], $minus]]]"""

    private const val ROADS = """["all", ["match", ["get", "class"],
        ["motorway", "trunk", "primary", "secondary", "tertiary", "minor", "service"], true, false],
        ["!=", ["get", "brunnel"], "tunnel"]]"""

    private const val PATHS = """["all", ["match", ["get", "class"], ["path", "pedestrian", "track"], true, false],
        ["!=", ["get", "brunnel"], "tunnel"]]"""

    val JSON: String = """
{
  "version": 8,
  "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
  "sources": {
    "openmaptiles": { "type": "vector", "url": "https://tiles.openfreemap.org/planet" }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#000000" } },
    { "id": "water", "type": "fill", "source": "openmaptiles", "source-layer": "water",
      "paint": { "fill-color": "#16304A" } },
    { "id": "paths", "type": "line", "source": "openmaptiles", "source-layer": "transportation",
      "filter": $PATHS,
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#5A5A5A", "line-width": ${width("4", 1.3)} } },
    { "id": "roads-outline", "type": "line", "source": "openmaptiles", "source-layer": "transportation",
      "filter": $ROADS,
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#6E6E6E", "line-width": ${width(ROAD_BASE, 1.0)} } },
    { "id": "roads-inner", "type": "line", "source": "openmaptiles", "source-layer": "transportation",
      "filter": $ROADS,
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#000000", "line-width": ${width(ROAD_BASE, 1.0, minus = 5.0)} } }
  ]
}
"""
}
