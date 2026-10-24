package com.personal.rando.ui

import android.graphics.Color
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.RoadKind
import com.personal.rando.routing.RoadPiece
import com.personal.rando.routing.Route
import com.personal.rando.routing.pieces
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** 地图上自己画的东西：路线和起终点 */
object MapLayers {

    /** OpenFreeMap 的免费矢量地图，不需要 key */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    private const val TRACK = "rando-track"
    private const val ROUTE = "rando-route"
    private const val START = "rando-start"
    private const val END = "rando-end"

    fun install(style: Style) {
        listOf(TRACK, ROUTE, START, END).forEach { style.addSource(GeoJsonSource(it)) }
        // 已经骑过的轨迹压在路线下面
        style.addLayer(
            LineLayer(TRACK, TRACK).withProperties(
                lineColor(Color.parseColor("#E0672B")), lineWidth(4f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        // 先画一圈白边，路线在浅色底图上更显眼
        style.addLayer(
            LineLayer("$ROUTE-casing", ROUTE).withProperties(
                lineColor(Color.WHITE), lineWidth(9f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        // 每段按路的种类上色（公园连道绿、马路红……），颜色放在每段的 color 属性里
        style.addLayer(
            LineLayer(ROUTE, ROUTE).withProperties(
                lineColor(Expression.toColor(Expression.get("color"))), lineWidth(5.5f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        style.addLayer(pin(START, "#2E7D4F"))
        style.addLayer(pin(END, "#E0672B"))
    }

    fun show(style: Style, route: Route?, start: LatLon?, end: LatLon?) {
        style.getSourceAs<GeoJsonSource>(ROUTE)?.setGeoJson(route?.let(::coloredRoute) ?: EMPTY)
        style.getSourceAs<GeoJsonSource>(START)?.setGeoJson(point(start))
        style.getSourceAs<GeoJsonSource>(END)?.setGeoJson(point(end))
    }

    /** 按路的种类切成几段；分不出来时整条画成公园连道的绿色 */
    private fun coloredRoute(route: Route): FeatureCollection {
        val pieces = route.pieces().ifEmpty {
            if (route.points.size >= 2) listOf(RoadPiece(RoadKind.PCN, route.points)) else emptyList()
        }
        return FeatureCollection.fromFeatures(
            pieces.map { piece ->
                Feature.fromGeometry(LineString.fromLngLats(piece.points.map { Point.fromLngLat(it.lon, it.lat) }))
                    .apply { addStringProperty("color", piece.kind.color) }
            }
        )
    }

    fun showTrack(style: Style, track: List<LatLon>?) {
        style.getSourceAs<GeoJsonSource>(TRACK)?.setGeoJson(line(track))
    }

    private fun line(points: List<LatLon>?): FeatureCollection =
        if (points != null && points.size >= 2) {
            FeatureCollection.fromFeature(
                Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) }))
            )
        } else {
            EMPTY
        }

    private fun pin(id: String, color: String) = CircleLayer(id, id).withProperties(
        circleRadius(8f), circleColor(Color.parseColor(color)),
        circleStrokeColor(Color.WHITE), circleStrokeWidth(3f),
    )

    private fun point(p: LatLon?): FeatureCollection =
        if (p == null) EMPTY else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)))

    private val EMPTY: FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())
}
