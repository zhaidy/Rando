package com.personal.rando.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.rando.places.SavedPlace
import com.personal.rando.places.SavedPlaceStore
import com.personal.rando.rando
import com.personal.rando.ride.RideSession
import com.personal.rando.routing.BRouterClient
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.RoutePrefs
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Route
import com.personal.rando.routing.LoopPlanner
import com.personal.rando.nav.Geo
import kotlin.math.abs
import com.personal.rando.search.Place
import com.personal.rando.search.PlaceSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RouteState {
    data object Idle : RouteState
    /** 起点用「我的位置」，但还没定位到 */
    data object WaitingForLocation : RouteState
    data object Loading : RouteState
    /** [stops]：依次经过的点，起点、途经点、终点 */
    data class Ready(val route: Route, val stops: List<LatLon>) : RouteState
    data class Failed(val message: String) : RouteState
}

data class PlannerState(
    val prefs: RoutePrefs = RoutePrefs(),
    /** null 表示从我的位置出发 */
    val start: Place? = null,
    val destination: Place? = null,
    /** 下一次长按地图设的是起点 */
    val pickingStart: Boolean = false,
    /** 途中要经过的地方，按顺路的顺序 */
    val via: List<Place> = emptyList(),
    /** 下一次长按地图或搜索选的是途经点 */
    val pickingVia: Boolean = false,
    /** 不去哪儿，骑一圈回到起点 */
    val loop: LoopRequest? = null,
    val query: String = "",
    val results: List<Place> = emptyList(),
    val route: RouteState = RouteState.Idle,
)

/** 骑一圈：大约多远，第几次换方向 */
data class LoopRequest(val targetMeters: Int, val attempt: Int = 0)

/** 规划路线；开始导航后交给 [RideSession]，它跟着进程走，界面关了也继续 */
class RouteViewModel(app: Application) : AndroidViewModel(app) {

    private val router = BRouterClient()
    private val places = PlaceSearch()
    val session: RideSession = app.rando.session
    val savedPlaces: SavedPlaceStore = app.rando.savedPlaces

    /** 记住上次选的路线偏好 */
    private val stored = app.getSharedPreferences("planner", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(PlannerState(prefs = loadPrefs()))
    val state: StateFlow<PlannerState> = _state

    private var myLocation: LatLon? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null

    init {
        viewModelScope.launch { session.location.filterNotNull().collect(::onMyLocation) }
    }

    fun setPrefs(prefs: RoutePrefs) {
        stored.edit {
            putString(KEY_PROFILE, prefs.profile.name)
            putBoolean(KEY_AVOID_ROADS, prefs.avoidRoads)
            putBoolean(KEY_AVOID_BRIDGES, prefs.avoidBridges)
        }
        _state.update { it.copy(prefs = prefs) }
        planRoute()
    }

    private fun loadPrefs(): RoutePrefs {
        val name = stored.getString(KEY_PROFILE, null)
        // 旧版本把「不上马路」存成单独的一种配置
        if (name == "NO_ROADS") return RoutePrefs(BikeProfile.PCN, avoidRoads = true)
        return RoutePrefs(
            profile = BikeProfile.entries.firstOrNull { it.name == name } ?: BikeProfile.PCN,
            avoidRoads = stored.getBoolean(KEY_AVOID_ROADS, false),
            avoidBridges = stored.getBoolean(KEY_AVOID_BRIDGES, false),
        )
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // 停手再搜，别每敲一个字就请求一次
            val results = try {
                places.search(query.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            _state.update { it.copy(results = results) }
        }
    }

    fun choosePlace(place: Place) {
        searchJob?.cancel()
        _state.update { it.copy(query = "", results = emptyList()).with(place) }
        planRoute()
    }

    fun onMapLongPress(point: LatLon) {
        _state.update { it.with(Place(name = "地图上的点", detail = "", location = point)) }
        planRoute()
    }

    /** 选中的地方按现在在选什么放进去：起点、途经点，或者目的地 */
    private fun PlannerState.with(place: Place): PlannerState = when {
        pickingStart -> copy(start = if (place.isMapPoint) place.copy(name = "地图上的起点") else place, pickingStart = false)
        pickingVia -> copy(via = insertVia(this, if (place.isMapPoint) place.copy(name = "地图上的途经点") else place), pickingVia = false)
        else -> copy(destination = place, loop = null)
    }

    private val Place.isMapPoint get() = detail.isEmpty() && name == "地图上的点"

    /** 插在最顺路的位置：加进去后直线总长增加最少的那个空当 */
    private fun insertVia(s: PlannerState, place: Place): List<Place> {
        val from = s.start?.location ?: myLocation
        val to = s.destination?.location
        if (from == null || to == null) return s.via + place
        val stops = listOf(from) + s.via.map { it.location } + to
        val best = (0 until stops.size - 1).minBy { i ->
            Geo.distance(stops[i], place.location) + Geo.distance(place.location, stops[i + 1]) - Geo.distance(stops[i], stops[i + 1])
        }
        return s.via.toMutableList().apply { add(best, place) }
    }

    fun startPickingVia() = _state.update { it.copy(pickingVia = true, pickingStart = false) }

    fun removeVia(place: Place) {
        _state.update { it.copy(via = it.via - place) }
        planRoute()
    }

    /** 骑一圈大约 [km] 公里，回到起点 */
    fun chooseLoop(km: Int) {
        _state.update { it.copy(loop = LoopRequest(km * 1000), destination = null, via = emptyList(), pickingVia = false) }
        planRoute()
    }

    /** 换个方向再画一圈 */
    fun anotherLoop() {
        _state.update { s -> s.copy(loop = s.loop?.let { it.copy(attempt = it.attempt + 1) }) }
        planRoute()
    }

    fun chooseSaved(place: SavedPlace) = choosePlace(Place(place.name, "", place.location))

    fun startPickingStart() = _state.update { it.copy(pickingStart = true, pickingVia = false) }

    fun useMyLocationAsStart() {
        _state.update { it.copy(start = null, pickingStart = false) }
        planRoute()
    }

    fun clearRoute() {
        routeJob?.cancel()
        _state.update { it.copy(destination = null, via = emptyList(), loop = null, pickingVia = false, route = RouteState.Idle) }
    }

    private fun onMyLocation(point: LatLon) {
        val first = myLocation == null
        myLocation = point
        if (first && _state.value.route == RouteState.WaitingForLocation) planRoute()
    }

    fun startNavigation() {
        val s = _state.value
        val ready = s.route as? RouteState.Ready ?: return
        _state.update { it.copy(results = emptyList(), query = "") }
        session.startNavigation(ready.route, ready.stops.last(), ready.stops.drop(1).dropLast(1), s.prefs)
    }

    fun retry() = planRoute()

    /** 沿骑过的轨迹原路返回，直接开始导航 */
    fun returnTrip(onFailed: (String) -> Unit) {
        val prefs = session.navigation.value?.prefs ?: _state.value.prefs
        viewModelScope.launch {
            try {
                session.startReturnTrip(prefs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailed(e.message ?: "算路失败")
            }
        }
    }

    private fun planRoute() {
        val s = _state.value
        if (s.destination == null && s.loop == null) return
        val from = s.start?.location ?: myLocation
        routeJob?.cancel()
        if (from == null) {
            _state.update { it.copy(route = RouteState.WaitingForLocation) }
            return
        }
        _state.update { it.copy(route = RouteState.Loading) }
        routeJob = viewModelScope.launch {
            val next = try {
                if (s.loop != null) {
                    planLoop(from, s.loop, s.prefs)
                } else {
                    val stops = listOf(from) + s.via.map { it.location } + s.destination!!.location
                    RouteState.Ready(router.route(stops, s.prefs), stops)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RouteState.Failed(e.message ?: "算路失败")
            }
            _state.update { it.copy(route = next) }
        }
    }

    /**
     * 画一圈：先按经验估个圈，算出来太长太短就按比例调一次；
     * 某个方向算不出来（途经点在水库、工地里）就换个方向，最多试三个
     */
    private suspend fun planLoop(from: LatLon, loop: LoopRequest, prefs: RoutePrefs): RouteState {
        val target = loop.targetMeters.toDouble()
        var error: Exception? = null
        for (k in 0 until LOOP_TRIES) {
            val shape = LoopPlanner.plan(from, target, loop.attempt + k)
                ?: return RouteState.Failed("附近画不下这么大的圈，换个距离试试")
            try {
                var stops = listOf(from) + shape.waypoints + from
                var route = router.route(stops, prefs)
                val ratio = target / route.distanceMeters.coerceAtLeast(1)
                val adjusted = if (abs(ratio - 1) > LOOP_TOLERANCE) shape.scaled(ratio) else null
                if (adjusted != null) {
                    val retryStops = listOf(from) + adjusted.waypoints + from
                    val retry = try {
                        router.route(retryStops, prefs)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (retry != null && abs(retry.distanceMeters - target) < abs(route.distanceMeters - target)) {
                        route = retry
                        stops = retryStops
                    }
                }
                return RouteState.Ready(route, stops)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
        }
        return RouteState.Failed(error?.message ?: "算路失败")
    }

    private companion object {
        const val LOOP_TRIES = 3
        /** 圈的长度差这么多以内就不调了 */
        const val LOOP_TOLERANCE = 0.15
        const val KEY_PROFILE = "profile"
        const val KEY_AVOID_ROADS = "avoid_roads"
        const val KEY_AVOID_BRIDGES = "avoid_bridges"
    }
}
