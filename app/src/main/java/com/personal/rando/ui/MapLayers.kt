package com.personal.rando.ui

import android.graphics.Color
import com.personal.rando.routing.LatLon
import com.personal.rando.places.Amenity
import com.personal.rando.routing.RoadKind
import com.personal.rando.routing.RoadPiece
import com.personal.rando.routing.Route
import com.personal.rando.routing.crossings
import com.personal.rando.routing.pieces
import com.personal.rando.routing.structures
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
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
    private const val CROSSINGS = "rando-crossings"
    private const val FOCUS_LINE = "rando-focus-line"
    private const val FOCUS_POINT = "rando-focus-point"
    private const val STRUCTURES = "rando-structures"
    private const val START = "rando-start"
    private const val END = "rando-end"
    private const val VIA = "rando-via"
    private const val AMENITIES = "rando-amenities"

    /** [dark]：骑行视图的黑底地图，路线外面不描白边 */
    fun install(style: Style, dark: Boolean = false) {
        listOf(TRACK, FOCUS_LINE, ROUTE, STRUCTURES, CROSSINGS, AMENITIES, FOCUS_POINT, VIA, START, END).forEach { style.addSource(GeoJsonSource(it)) }
        // 已经骑过的轨迹压在路线下面
        style.addLayer(
            LineLayer(TRACK, TRACK).withProperties(
                lineColor(Color.parseColor("#E0672B")), lineWidth(4f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        // 指给你看的那段：路线下面垫一层黄色光晕
        style.addLayer(
            LineLayer(FOCUS_LINE, FOCUS_LINE).withProperties(
                lineColor(Color.parseColor("#FFC93C")), lineWidth(18f), lineOpacity(0.85f), lineBlur(2f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        // 先画一圈白边，路线在浅色底图上更显眼
        style.addLayer(
            LineLayer("$ROUTE-casing", ROUTE).withProperties(
                lineColor(if (dark) Color.BLACK else Color.WHITE), lineWidth(9f),
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
        // 天桥、地下通道：路线上再压一条白色虚线
        style.addLayer(
            LineLayer(STRUCTURES, STRUCTURES).withProperties(
                lineColor(Color.WHITE), lineWidth(2.5f),
                lineDasharray(arrayOf(1.5f, 1.2f)),
            )
        )
        // 过马路的地方：小圆点，颜色按路口种类
        style.addLayer(
            CircleLayer(CROSSINGS, CROSSINGS).withProperties(
                circleRadius(4.5f), circleColor(Expression.toColor(Expression.get("color"))),
                circleStrokeColor(Color.WHITE), circleStrokeWidth(1.5f),
            )
        )
        // 沿途补给：小圆点，颜色按种类（饮水机蓝、厕所紫……），缩小看全程时点也跟着变小
        style.addLayer(
            CircleLayer(AMENITIES, AMENITIES).withProperties(
                circleRadius(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(11, 2f), Expression.stop(16, 6f))),
                circleColor(Expression.toColor(Expression.get("color"))),
                circleStrokeColor(Color.WHITE), circleStrokeWidth(1.5f),
            )
        )
        // 指给你看的路口：一个黄圈
        style.addLayer(
            CircleLayer(FOCUS_POINT, FOCUS_POINT).withProperties(
                circleRadius(14f), circleColor(Color.TRANSPARENT),
                circleStrokeColor(Color.parseColor("#FFC93C")), circleStrokeWidth(4f),
            )
        )
        style.addLayer(pin(VIA, "#2F7DE1").withProperties(circleRadius(6.5f)))
        style.addLayer(pin(START, "#2E7D4F"))
        style.addLayer(pin(END, "#E0672B"))
    }

    fun show(style: Style, route: Route?, start: LatLon?, end: LatLon?, via: List<LatLon> = emptyList()) {
        style.getSourceAs<GeoJsonSource>(ROUTE)?.setGeoJson(route?.let(::coloredRoute) ?: EMPTY)
        style.getSourceAs<GeoJsonSource>(STRUCTURES)?.setGeoJson(
            FeatureCollection.fromFeatures(
                route?.structures().orEmpty().filter { it.path.size >= 2 }.map { st ->
                    Feature.fromGeometry(LineString.fromLngLats(st.path.map { Point.fromLngLat(it.lon, it.lat) }))
                }
            )
        )
        style.getSourceAs<GeoJsonSource>(CROSSINGS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                route?.crossings().orEmpty().map { c ->
                    Feature.fromGeometry(Point.fromLngLat(c.location.lon, c.location.lat))
                        .apply { addStringProperty("color", c.type.color) }
                }
            )
        )
        style.getSourceAs<GeoJsonSource>(START)?.setGeoJson(point(start))
        style.getSourceAs<GeoJsonSource>(END)?.setGeoJson(point(end))
        style.getSourceAs<GeoJsonSource>(VIA)?.setGeoJson(
            FeatureCollection.fromFeatures(via.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })
        )
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

    fun showAmenities(style: Style, amenities: List<Amenity>) {
        style.getSourceAs<GeoJsonSource>(AMENITIES)?.setGeoJson(
            FeatureCollection.fromFeatures(
                amenities.map { a ->
                    Feature.fromGeometry(Point.fromLngLat(a.location.lon, a.location.lat))
                        .apply { addStringProperty("color", a.type.color) }
                }
            )
        )
    }

    /** 高亮一段路（或一个路口）；传 null 清掉 */
    fun showFocus(style: Style, points: List<LatLon>?) {
        style.getSourceAs<GeoJsonSource>(FOCUS_LINE)?.setGeoJson(line(points?.takeIf { it.size >= 2 }))
        style.getSourceAs<GeoJsonSource>(FOCUS_POINT)?.setGeoJson(point(points?.singleOrNull()))
    }

    /**
     * 骑过的路：所有记录的轨迹叠在一起画，半透明，同一条路骑得越多越浓。
     * 用在单独的地图上，不和导航的图层混在一起
     */
    fun showRidden(style: Style, tracks: List<List<LatLon>>) {
        val id = "rando-ridden"
        val source = style.getSourceAs<GeoJsonSource>(id) ?: GeoJsonSource(id).also { src ->
            style.addSource(src)
            style.addLayer(
                LineLayer(id, id).withProperties(
                    lineColor(Color.parseColor("#E0672B")), lineWidth(4f), lineOpacity(0.35f),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
        }
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                tracks.filter { it.size >= 2 }.map { t ->
                    Feature.fromGeometry(LineString.fromLngLats(t.map { Point.fromLngLat(it.lon, it.lat) }))
                }
            )
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
