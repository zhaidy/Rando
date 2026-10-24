package com.personal.rando.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
import com.personal.rando.places.Amenity
import com.personal.rando.places.AmenityOnRoute
import com.personal.rando.places.AmenityType
import com.personal.rando.places.onRoute
import com.personal.rando.routing.RouteTimeline
import com.personal.rando.weather.AreaForecast
import com.personal.rando.weather.RouteWeather
import com.personal.rando.ui.SearchBar
import com.personal.rando.ui.SegmentCard
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
    private val prefs = RoutePrefs(BikeProfile.PCN, avoidRoads = true)
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
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchBar(query, if (picking) "搜索起点" else "去哪儿骑？", results, 42, {}, {}, {}, {})
            PrefsRow(prefs, {})
            if (picking) HintPill("长按地图选起点，或者在上面搜一个地方")
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SideRail(showLocation = true, showRecord = recording == null, {}, {}, {}, Modifier.align(Alignment.End))
            recording?.let { RecordingPill(it, {}, {}) }
            PlannerSheet(
                state = PlannerState(prefs = prefs, destination = destination, pickingStart = picking, route = routeState, via = via, loop = loop, weather = weather, amenities = amenities),
                saved = saved,
                actions = PlannerActions(),
            )
        }
    }

    @Test fun plannerEmpty() = shot("1_planner_empty") { Planner() }

    @Test fun plannerRoute() = shot("2_planner_route") { Planner(destination = mbs, routeState = RouteState.Ready(route, emptyList())) }

    @Test fun plannerOutlook() = shot("2d_planner_outlook") {
        val p = route.points
        val amenities = listOf(
            Amenity(AmenityType.WATER, p[p.size / 5], null), Amenity(AmenityType.WATER, p[p.size / 2], null),
            Amenity(AmenityType.TOILET, p[p.size / 3], null), Amenity(AmenityType.SHOP, p[p.size * 2 / 3], "7-Eleven"),
        ).onRoute(RouteTimeline(route))
        val weather = RouteWeather(listOf(AreaForecast("Bukit Merah", p[0], "Thundery Showers")), emptyList())
        Planner(destination = mbs, routeState = RouteState.Ready(route, emptyList()), weather = weather, amenities = amenities)
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
        NavigationOverlay(nav, {}, {}, {})
    }

    private val rides = listOf(
        Ride(1_729_738_800_000, 1_729_742_400_000, 18_240.0, 3_312, 42.0, 9.1, route.points.map { TrackPoint(it, null, 0) }),
        Ride(1_729_652_400_000, 1_729_654_200_000, 7_830.0, 1_510, 12.0, 7.4, emptyList()),
        Ride(1_729_566_000_000, 1_729_570_000_000, 24_100.0, 4_620, 66.0, 10.3, emptyList()),
    )

    @Test fun rideList() = shot("8_ride_list") { RideList(rides, {}, {}) }

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
                RideView(nav, RecordingState(0, 2400.0, 600, currentSpeed = 5.4), {}, {}, map = { m ->
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
                RideView(null, RecordingState(0, 8350.0, 1720, ascendMeters = 34.0, currentSpeed = 6.1), {}, {}, onReturn = {}, onDashboard = {}, map = { m ->
                    Image(mapImage("map_dark.png"), null, m, contentScale = ContentScale.Crop)
                })
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/screenshots/11_free_ride.png")
    }
}
