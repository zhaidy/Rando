package com.personal.rando.screenshots

import com.personal.rando.routing.RouteOption
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.personal.rando.nav.NavProgress
import com.personal.rando.places.SavedPlace
import com.personal.rando.ride.NavigationState
import com.personal.rando.ride.RecordingState
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Maneuver
import com.personal.rando.routing.RoutePrefs
import com.personal.rando.routing.RouteParser
import com.personal.rando.routing.Turn
import com.personal.rando.search.Place
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.personal.rando.ride.Ride
import com.personal.rando.ride.TrackPoint
import com.personal.rando.ui.RandoTheme
import com.personal.rando.ui.RideDetail
import com.personal.rando.ui.RideList
import com.personal.rando.ui.RiddenMap
import com.personal.rando.ui.ReviewPage
import com.personal.rando.ride.Reviews
import com.personal.rando.ride.ReviewSpan
import com.personal.rando.ui.RideView
import com.personal.rando.ui.HintPill
import com.personal.rando.ui.NavigationOverlay
import com.personal.rando.ui.NoteTarget
import com.personal.rando.ui.PlannerSheet
import com.personal.rando.ui.PrefsRow
import com.personal.rando.ui.RecordingPill
import com.personal.rando.ui.RouteState
import com.personal.rando.ui.PlannerState
import com.personal.rando.ui.PlannerActions
import com.personal.rando.ui.LoopRequest
import com.personal.rando.ui.GroupPill
import com.personal.rando.ui.FinishedCard
import com.personal.rando.group.GroupState
import com.personal.rando.group.WalkieState
import com.personal.rando.group.Teammate
import com.personal.rando.places.Amenity
import com.personal.rando.places.AmenityOnRoute
import com.personal.rando.places.AmenityType
import com.personal.rando.places.onRoute
import com.personal.rando.routing.RouteTimeline
import com.personal.rando.weather.AreaForecast
import com.personal.rando.weather.RouteWeather
import com.personal.rando.ui.SearchBar
import com.personal.rando.ui.SegmentCard
import com.personal.rando.ui.RiddenMap
import com.personal.rando.ui.RiddenMode
import com.personal.rando.places.PcnData
import com.personal.rando.places.PcnCoverage
import com.personal.rando.ui.SegmentInfo
import com.personal.rando.ui.markLabel
import com.personal.rando.routing.RoadKind
import com.personal.rando.ui.SideRail
import com.personal.rando.ui.targets
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 界面截图：把各个界面画在真实的新加坡地图截图上，看液态玻璃和排版。
 * ./gradlew testDebugUnitTest -Pscreenshots -Proborazzi.test.record=true，图在 app/build/screenshots/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class UiScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val route = RouteParser.parse(File("src/test/resources/preview/route.json").readText())
    private val mbs = Place("Marina Bay Sands", "", LatLon(1.2838, 103.8591))
    private val prefs = RoutePrefs(BikeProfile.PCN, avoidRoads = true, mySpeed = 5.1)
    private val savedRoutes = listOf(
        com.personal.rando.places.SavedRoute("东海岸一圈", listOf(LatLon(1.30, 103.90), LatLon(1.30, 103.90)), BikeProfile.PCN, true, false, 21_300, 0),
        com.personal.rando.places.SavedRoute("上班", listOf(LatLon(1.35, 103.82), LatLon(1.28, 103.85)), BikeProfile.PCN, true, false, 9_800, 0),
    )
    private val saved = listOf(
        SavedPlace(SavedPlace.HOME, LatLon(1.35, 103.82)),
        SavedPlace(SavedPlace.WORK, LatLon(1.28, 103.85)),
        SavedPlace("East Coast Park", LatLon(1.30, 103.91)),
    )

    private fun shot(name: String, content: @Composable BoxScope.() -> Unit) {
        compose.setContent { OverMap(content = content) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Composable
    private fun BoxScope.Planner(
        query: String = "",
        results: List<Place> = emptyList(),
        destination: Place? = null,
        routeState: RouteState = RouteState.Idle,
        recording: RecordingState? = null,
        picking: Boolean = false,
        via: List<Place> = emptyList(),
        loop: LoopRequest? = null,
        weather: RouteWeather? = null,
        amenities: List<AmenityOnRoute> = emptyList(),
        group: GroupState? = null,
        finished: Ride? = null,
        climate: com.personal.rando.weather.Climate? = null,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchBar(query, if (picking) "搜索起点" else "去哪儿骑？", results, 42, {}, {}, {}, {})
            PrefsRow(prefs, {})
            if (picking) HintPill("长按地图选起点，或者在上面搜一个地方", onCancel = {})
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SideRail(showLocation = true, showRecord = recording == null, {}, {}, {}, onGroup = {}.takeIf { group == null }, modifier = Modifier.align(Alignment.End))
            group?.let { GroupPill(it, {}, {}, walkie = WalkieState(on = true, device = "蓝牙耳机 AirPods", talking = setOf("小红"))) }
            finished?.let { FinishedCard(it, {}, {}) }
            recording?.let { RecordingPill(it, {}, {}) }
            PlannerSheet(
                state = PlannerState(prefs = prefs, destination = destination, pickingStart = picking, route = routeState, via = via, loop = loop, weather = weather, amenities = amenities, climate = climate),
                saved = saved,
                actions = PlannerActions(),
                savedRoutes = savedRoutes,
                parked = com.personal.rando.places.ParkedBike(LatLon(1.29, 103.85), System.currentTimeMillis() - 2 * 3_600_000L),
                here = LatLon(1.30, 103.85),
            )
        }
    }

    @Test fun plannerEmpty() = shot("1_planner_empty") { Planner() }

    @Test fun plannerRoute() = shot("2_planner_route") { Planner(destination = mbs, routeState = RouteState.Ready(route, emptyList())) }

    @Test fun plannerChoices() = shot("2f_planner_choices") {
        val options = listOf(
            RouteOption(route, RoutePrefs(), best = listOf(com.personal.rando.routing.Measure.ROAD)),
            RouteOption(
                route.copy(distanceMeters = route.distanceMeters - 900, durationSeconds = route.durationSeconds - 300),
                RoutePrefs(profile = com.personal.rando.routing.BikeProfile.ROAD),
                best = listOf(com.personal.rando.routing.Measure.TIME, com.personal.rando.routing.Measure.DISTANCE),
                better = mapOf(com.personal.rando.routing.Measure.TIME to 300, com.personal.rando.routing.Measure.DISTANCE to 900),
                worse = mapOf(com.personal.rando.routing.Measure.ROAD to 1400),
            ),
            RouteOption(route, RoutePrefs(), better = mapOf(com.personal.rando.routing.Measure.CROSSINGS to 3)),
        )
        Planner(destination = mbs, routeState = RouteState.Ready(route, emptyList(), options))
    }

    /** 卡片中间往下划收起，再往上划展开，不用非得拖顶上的拖动条 */
    @Test fun plannerSwipeAnywhere() {
        compose.setContent { OverMap { Planner(destination = mbs, routeState = RouteState.Ready(route, emptyList())) } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("距离").performTouchInput { swipeDown(startY = centerY, endY = centerY + 400f) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onAllNodesWithText("距离").assertCountEquals(0)
        compose.onNodeWithText("开始导航").performTouchInput { swipeUp(startY = centerY, endY = centerY - 400f) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("距离").assertExists()
    }

    @Test fun plannerPicking() = shot("2e_planner_picking") { Planner(picking = true) }

    @Test fun plannerOutlook() = shot("2d_planner_outlook") {
        val p = route.points
        val amenities = listOf(
            Amenity(AmenityType.WATER, p[p.size / 5], null), Amenity(AmenityType.WATER, p[p.size / 2], null),
            Amenity(AmenityType.TOILET, p[p.size / 3], null), Amenity(AmenityType.SHOP, p[p.size * 2 / 3], "7-Eleven"),
        ).onRoute(RouteTimeline(route))
        val weather = RouteWeather(listOf(AreaForecast("Bukit Merah", p[0], "Thundery Showers")), emptyList())
        val climate = com.personal.rando.weather.Climate(tempC = 32.0, humidity = 68.0, uv = 9, psi = 148, psiRegion = "west")
        Planner(destination = mbs, routeState = RouteState.Ready(route, emptyList()), weather = weather, amenities = amenities, climate = climate)
    }

    @Test fun plannerVia() = shot("2b_planner_via") {
        Planner(
            destination = mbs,
            via = listOf(Place("Gardens by the Bay", "", LatLon(1.28, 103.86))),
            routeState = RouteState.Ready(route, emptyList()),
        )
    }

    @Test fun plannerLoop() = shot("2c_planner_loop") {
        Planner(loop = LoopRequest(20_000), routeState = RouteState.Ready(route, emptyList()))
    }

    @Test fun plannerLoading() = shot("3_planner_loading") { Planner(destination = mbs, routeState = RouteState.Loading) }

    @Test fun search() = shot("4_search") {
        Planner(
            query = "marina",
            results = listOf(
                Place("Marina Bay Sands", "10 Bayfront Avenue · Downtown Core", LatLon(1.28, 103.86)),
                Place("Marina Barrage", "8 Marina Gardens Drive", LatLon(1.28, 103.87)),
                Place("Marina Square", "6 Raffles Boulevard", LatLon(1.29, 103.86)),
            ),
        )
    }

    @Test fun focus() = shot("5_focus_card") {
        val items = route.targets(NoteTarget.CROSSINGS)
        Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
            SegmentCard(items[2], 2, items.size, {}, {}, {})
        }
    }

    @Test fun focusMark() = shot("5b_focus_mark") {
        val p = route.points.take(10)
        val info = SegmentInfo("自行车道", RoadKind.CYCLEWAY.color, 320, p[5], p, null, RoadKind.CYCLEWAY)
        Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
            SegmentCard(info, 0, 3, {}, {}, {}, markLabel = info.markLabel(false))
        }
    }

    @Test fun groupRide() = shot("6b_group") {
        Planner(
            recording = RecordingState(startedAt = 0, distanceMeters = 5320.0, movingSeconds = 1234, currentSpeed = 5.2),
            group = GroupState("K7PX3M", connected = true, teammates = listOf(Teammate("a", "小红"), Teammate("b", "阿明"))),
        )
    }

    @Test fun finished() = shot("6c_finished") { Planner(finished = rides[0]) }

    @Test fun recording() = shot("6_recording") {
        Planner(recording = RecordingState(startedAt = 0, distanceMeters = 5320.0, movingSeconds = 1234, currentSpeed = 5.2))
    }

    @Test fun navigation() = shot("7_navigation") {
        val nav = NavigationState(
            route, mbs.location, prefs = prefs,
            progress = NavProgress(
                alongMeters = 2000.0, remainingMeters = 4930.0, remainingSeconds = 1860,
                next = Maneuver(10, Turn.RIGHT), distanceToNext = 880.0,
                following = Maneuver(12, Turn.LEFT), distanceNextToFollowing = 60.0,
                offRoute = false, arrived = false,
            ),
        )
        NavigationOverlay(nav, {}, {})
    }

    private val rides = listOf(
        Ride(1_729_738_800_000, 1_729_742_400_000, 18_240.0, 3_312, 42.0, 9.1, route.points.map { TrackPoint(it, null, 0) }),
        Ride(1_729_652_400_000, 1_729_654_200_000, 7_830.0, 1_510, 12.0, 7.4, emptyList()),
        Ride(1_729_566_000_000, 1_729_570_000_000, 24_100.0, 4_620, 66.0, 10.3, emptyList()),
    ) + (1..12).map { week ->
        // 前几周每周一次，柱状图有高有低
        val start = 1_729_566_000_000 - week * 7L * 86_400_000
        Ride(start, start + 3_600_000, 8_000.0 + (week * 5_300 % 21_000), 3_000, 20.0, 8.0, emptyList())
    }

    /** 截图里的「现在」：2024-10-24 晚上 */
    private val now = 1_729_771_200_000

    @Test fun rideList() = shot("8_ride_list") { RideList(rides, {}, {}, {}, now) }

    @Test fun review() = shot("8d_review") {
        val r = Reviews.of(rides, ReviewSpan.MONTH, now).copy(newPcnMeters = 12_300.0, previousMeters = 30_000.0)
        ReviewPage(r, ReviewSpan.MONTH, {}, {}, {}, {}, {})
    }

    @Test fun pcnMap() = shot("8c_pcn_map") {
        val loops = PcnData.parse(java.io.File("src/main/assets/pcn.json").readText())
        val pcn = PcnCoverage.of(loops, rides.map { r -> r.points.map { it.location } } + listOf(loops[5].lines.take(80).flatten()))
        RiddenMap(rides, {}, RiddenMode.PCN, {}, pcn) { Box(it) }
    }

    @Test fun riddenMap() = shot("12_ridden_map") {
        RiddenMap(rides, {}) { m -> Image(mapImage("map_light.png"), null, m, contentScale = ContentScale.Crop) }
    }

    /** 分享图：没网时轨迹画在纯色底上；有地图时叠在地图上 */
    @Test fun shareCard() {
        val ride = rides[0]
        val track = ride.points.map { it.location }
        val plain = com.personal.rando.ui.ShareCard.draw(
            null, listOf(com.personal.rando.ui.ShareCard.fit(track, 1080f, 900f)), ride, "这条路第 3 次骑，比上次快 1 分 20 秒，最好成绩！",
        )
        File("build/screenshots").mkdirs()
        File("build/screenshots/12_share_card.png").outputStream().use { plain.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 今天骑的两次合成一张分享图 */
    @Test fun shareDayCard() {
        val evening = Ride(
            1_729_764_000_000, 1_729_766_400_000, 9_100.0, 1_980, 15.0, 8.2,
            route.points.take(route.points.size / 2).reversed().map { TrackPoint(LatLon(it.lat + 0.004, it.lon + 0.006), null, 0) },
        )
        val day = listOf(rides[0], evening)
        val card = com.personal.rando.ui.ShareCard.drawDay(
            null, com.personal.rando.ui.ShareCard.fitAll(day.map { r -> r.points.map { it.location } }, 1080f, 900f).map { listOf(it) }, day,
        )
        File("build/screenshots").mkdirs()
        File("build/screenshots/12b_share_day_card.png").outputStream().use { card.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun rideDetail() {
        compose.setContent {
            RandoTheme {
                RideDetail(rides[0], {}, {}) { m -> Image(mapImage("map_light.png"), null, m, contentScale = ContentScale.Crop) }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/screenshots/9_ride_detail.png")
    }

    @Test fun rideView() {
        val nav = NavigationState(
            route, mbs.location, prefs = prefs,
            progress = NavProgress(
                alongMeters = 2400.0, remainingMeters = 4930.0, remainingSeconds = 1860,
                next = Maneuver(10, Turn.RIGHT), distanceToNext = 880.0,
                following = null, distanceNextToFollowing = Double.MAX_VALUE,
                offRoute = false, arrived = false,
            ),
        )
        compose.setContent {
            RandoTheme {
                RideView(nav, RecordingState(0, 2400.0, 600, currentSpeed = 5.4), {}, {}, map = { m, _ ->
                    Image(mapImage("map_dark.png"), null, m, contentScale = ContentScale.Crop)
                })
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/screenshots/10_ride_view.png")
    }

    @Test fun freeRide() {
        compose.setContent {
            RandoTheme {
                RideView(null, RecordingState(System.currentTimeMillis() - 2_100_000, 8350.0, 1720, ascendMeters = 34.0, currentSpeed = 6.1), {}, {}, teammates = "队友：小红 120 米 · 阿明 1.4 公里", walkie = WalkieState(on = true, direct = 2), map = { m, _ ->
                    Image(mapImage("map_dark.png"), null, m, contentScale = ContentScale.Crop)
                })
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/screenshots/11_free_ride.png")
    }
}
