package com.personal.rando.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.rando.rando
import com.personal.rando.ride.RideSession
import com.personal.rando.routing.BRouterClient
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Route
import com.personal.rando.routing.SafetyScore
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
    data class Ready(val route: Route, val safety: SafetyScore.Result?) : RouteState
    data class Failed(val message: String) : RouteState
}

data class PlannerState(
    val profile: BikeProfile = BikeProfile.CITY,
    /** null 表示从我的位置出发 */
    val start: Place? = null,
    val destination: Place? = null,
    /** 下一次长按地图设的是起点 */
    val pickingStart: Boolean = false,
    val query: String = "",
    val results: List<Place> = emptyList(),
    val route: RouteState = RouteState.Idle,
)

/** 规划路线；开始导航后交给 [RideSession]，它跟着进程走，界面关了也继续 */
class RouteViewModel(app: Application) : AndroidViewModel(app) {

    private val router = BRouterClient()
    private val places = PlaceSearch()
    val session: RideSession = app.rando.session

    private val _state = MutableStateFlow(PlannerState())
    val state: StateFlow<PlannerState> = _state

    private var myLocation: LatLon? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null

    init {
        viewModelScope.launch { session.location.filterNotNull().collect(::onMyLocation) }
    }

    fun setProfile(profile: BikeProfile) {
        _state.update { it.copy(profile = profile) }
        planRoute()
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
        _state.update {
            val cleared = it.copy(query = "", results = emptyList())
            if (it.pickingStart) cleared.copy(start = place, pickingStart = false)
            else cleared.copy(destination = place)
        }
        planRoute()
    }

    fun onMapLongPress(point: LatLon) {
        val place = Place(name = "地图上的点", detail = "", location = point)
        _state.update {
            if (it.pickingStart) it.copy(start = place.copy(name = "地图上的起点"), pickingStart = false)
            else it.copy(destination = place)
        }
        planRoute()
    }

    fun startPickingStart() = _state.update { it.copy(pickingStart = true) }

    fun useMyLocationAsStart() {
        _state.update { it.copy(start = null, pickingStart = false) }
        planRoute()
    }

    fun clearRoute() {
        routeJob?.cancel()
        _state.update { it.copy(destination = null, route = RouteState.Idle) }
    }

    private fun onMyLocation(point: LatLon) {
        val first = myLocation == null
        myLocation = point
        if (first && _state.value.route == RouteState.WaitingForLocation) planRoute()
    }

    fun startNavigation() {
        val s = _state.value
        val ready = s.route as? RouteState.Ready ?: return
        val destination = s.destination ?: return
        _state.update { it.copy(results = emptyList(), query = "") }
        session.startNavigation(ready.route, destination.location, s.profile)
    }

    fun retry() = planRoute()

    private fun planRoute() {
        val s = _state.value
        val to = s.destination?.location ?: return
        val from = s.start?.location ?: myLocation
        routeJob?.cancel()
        if (from == null) {
            _state.update { it.copy(route = RouteState.WaitingForLocation) }
            return
        }
        _state.update { it.copy(route = RouteState.Loading) }
        routeJob = viewModelScope.launch {
            val next = try {
                val route = router.route(from, to, s.profile)
                RouteState.Ready(route, SafetyScore.of(route))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RouteState.Failed(e.message ?: "算路失败")
            }
            _state.update { it.copy(route = next) }
        }
    }
}
