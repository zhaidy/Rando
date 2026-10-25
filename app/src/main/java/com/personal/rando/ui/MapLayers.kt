package com.personal.rando.ui

import android.graphics.Color
import com.personal.rando.routing.LatLon
import com.personal.rando.places.Amenity
import com.personal.rando.places.Hazard
import com.personal.rando.ride.HeatPiece
import com.personal.rando.places.PcnPiece
import com.personal.rando.group.Teammate
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
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
    private const val ALTERNATIVES = "rando-alternatives"
    private const val CROSSINGS = "rando-crossings"
    private const val FOCUS_LINE = "rando-focus-line"
    private const val FOCUS_POINT = "rando-focus-point"
    private const val STRUCTURES = "rando-structures"
    private const val START = "rando-start"
    private const val END = "rando-end"
    private const val VIA = "rando-via"
    private const val AMENITIES = "rando-amenities"
    private const val TEAM = "rando-team"
    private const val HAZARDS = "rando-hazards"

    /** [dark]：骑行视图的黑底地图，路线外面不描白边；路线、轨迹画粗一点，户外好认 */
    fun install(style: Style, dark: Boolean = false) {
        listOf(TRACK, FOCUS_LINE, ALTERNATIVES, ROUTE, STRUCTURES, CROSSINGS, AMENITIES, HAZARDS, FOCUS_POINT, VIA, START, END, TEAM).forEach { style.addSource(GeoJsonSource(it)) }
        // 已经骑过的轨迹压在路线下面
        style.addLayer(
            LineLayer(TRACK, TRACK).withProperties(
                lineColor(Color.parseColor("#E0672B")), lineWidth(if (dark) 6f else 4f),
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
        // 没选中的几条可选路线：灰色细线，压在选中的那条下面
        style.addLayer(
            LineLayer(ALTERNATIVES, ALTERNATIVES).withProperties(
                lineColor(Color.parseColor("#7E8A84")), lineWidth(4.5f), lineOpacity(0.7f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        // 先画一圈白边，路线在浅色底图上更显眼
        style.addLayer(
            LineLayer("$ROUTE-casing", ROUTE).withProperties(
                lineColor(if (dark) Color.BLACK else Color.WHITE), lineWidth(if (dark) 13f else 9f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        // 每段按路的种类上色（公园连道绿、马路红……），颜色放在每段的 color 属性里
        style.addLayer(
            LineLayer(ROUTE, ROUTE).withProperties(
                lineColor(Expression.toColor(Expression.get("color"))), lineWidth(if (dark) 9f else 5.5f),
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
        // 标过的路况：大一点的圆点，颜色按种类（施工橙、封路红、路面不好棕），中间一个感叹号
        style.addLayer(
            CircleLayer(HAZARDS, HAZARDS).withProperties(
                circleRadius(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(11, 4f), Expression.stop(16, 10f))),
                circleColor(Expression.toColor(Expression.get("color"))),
                circleStrokeColor(Color.WHITE), circleStrokeWidth(2f),
            )
        )
        style.addLayer(
            SymbolLayer("$HAZARDS-mark", HAZARDS).withProperties(
                textField("!"), textColor(Color.WHITE), textSize(13f),
                textFont(arrayOf("Noto Sans Bold")), textAllowOverlap(true), textIgnorePlacement(true),
            ).apply { minZoom = 13f }
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
        // 队友：彩色圆点，上面写名字；信号断了的变灰
        style.addLayer(
            CircleLayer(TEAM, TEAM).withProperties(
                circleRadius(8f), circleColor(Expression.toColor(Expression.get("color"))),
                circleStrokeColor(Color.WHITE), circleStrokeWidth(3f),
            )
        )
        style.addLayer(
            SymbolLayer("$TEAM-name", TEAM).withProperties(
                textField(Expression.get("name")), textFont(arrayOf("Noto Sans Regular")), textSize(13f),
                textOffset(arrayOf(0f, -1.6f)), textAllowOverlap(true), textIgnorePlacement(true),
                textColor(if (dark) Color.WHITE else Color.parseColor("#13241B")),
                textHaloColor(if (dark) Color.BLACK else Color.WHITE), textHaloWidth(1.5f),
            )
        )
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

    fun showHazards(style: Style, hazards: List<Hazard>) {
        style.getSourceAs<GeoJsonSource>(HAZARDS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                hazards.map { h ->
                    Feature.fromGeometry(Point.fromLngLat(h.location.lon, h.location.lat))
                        .apply { addStringProperty("color", h.type.color) }
                }
            )
        )
    }

    fun showTeam(style: Style, mates: List<Teammate>, now: Long = System.currentTimeMillis()) {
        style.getSourceAs<GeoJsonSource>(TEAM)?.setGeoJson(
            FeatureCollection.fromFeatures(
                mates.mapNotNull { m ->
                    val at = m.location ?: return@mapNotNull null
                    Feature.fromGeometry(Point.fromLngLat(at.lon, at.lat)).apply {
                        addStringProperty("name", m.name)
                        addStringProperty("color", if (m.stale(now)) "#9E9E9E" else mateColor(m.id))
                    }
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
     * 骑过的路：按骑过几次分段上色（浅黄、橙、红），次数多的压在上面。
     * 用在单独的地图上，不和导航的图层混在一起
     */
    fun showRidden(style: Style, pieces: List<HeatPiece>) {
        val id = "rando-ridden"
        val source = style.getSourceAs<GeoJsonSource>(id) ?: GeoJsonSource(id).also { src ->
            style.addSource(src)
            // 底图的马路是黄色的，垫一圈深色描边，浅黄的线才看得出来
            style.addLayer(
                LineLayer("$id-casing", id).withProperties(
                    lineColor(Color.parseColor("#3E2723")), lineWidth(6.5f), lineOpacity(0.55f),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
            style.addLayer(
                LineLayer(id, id).withProperties(
                    lineColor(Expression.toColor(Expression.get("color"))), lineWidth(4f),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
        }
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                pieces.map { piece ->
                    Feature.fromGeometry(LineString.fromLngLats(piece.points.map { Point.fromLngLat(it.lon, it.lat) }))
                        .apply { addStringProperty("color", piece.level.color) }
                }
            )
        )
    }

    /** 公园连道：骑过的绿色，没骑过的灰色；[pieces] 为空就清掉 */
    fun showPcn(style: Style, pieces: List<PcnPiece>) {
        val id = "rando-pcn"
        val source = style.getSourceAs<GeoJsonSource>(id) ?: GeoJsonSource(id).also { src ->
            style.addSource(src)
            style.addLayer(
                LineLayer(id, id).withProperties(
                    lineColor(Expression.toColor(Expression.get("color"))),
                    lineWidth(Expression.toNumber(Expression.get("width"))),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
        }
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                // 骑过的画在上面
                pieces.sortedBy { it.ridden }.map { piece ->
                    Feature.fromGeometry(LineString.fromLngLats(piece.points.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                        addStringProperty("color", if (piece.ridden) PCN_RIDDEN else PCN_NOT_YET)
                        addNumberProperty("width", if (piece.ridden) 4.5f else 3f)
                    }
                }
            )
        )
    }

    const val PCN_RIDDEN = "#1B7F3B"
    const val PCN_NOT_YET = "#9E9E9E"

    fun showAlternatives(style: Style, routes: List<Route>) {
        style.getSourceAs<GeoJsonSource>(ALTERNATIVES)?.setGeoJson(
            FeatureCollection.fromFeatures(
                routes.filter { it.points.size >= 2 }.map { r ->
                    Feature.fromGeometry(LineString.fromLngLats(r.points.map { Point.fromLngLat(it.lon, it.lat) }))
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
