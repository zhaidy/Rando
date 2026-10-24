package com.personal.rando.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.rando.nav.NavProgress
import com.personal.rando.nav.Navigator
import com.personal.rando.nav.VoicePrompter
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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
    /** 不为 null 就是在导航中 */
    val navigation: NavigationState? = null,
)

data class NavigationState(
    /** 还没定位到时是 null */
    val progress: NavProgress? = null,
    val rerouting: Boolean = false,
)

class RouteViewModel(
    private val router: BRouterClient = BRouterClient(),
    private val places: PlaceSearch = PlaceSearch(),
) : ViewModel() {

    private val _state = MutableStateFlow(PlannerState())
    val state: StateFlow<PlannerState> = _state

    private var myLocation: LatLon? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null

    /** 要播报的话，界面那边交给 TTS 念 */
    private val _speech = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val speech: SharedFlow<String> = _speech

    private var navigator: Navigator? = null
    private val prompter = VoicePrompter()
    private var rerouteJob: Job? = null
    private var lastRerouteAt = 0L

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
        stopNavigation()
        routeJob?.cancel()
        _state.update { it.copy(destination = null, route = RouteState.Idle) }
    }

    fun onMyLocation(point: LatLon) {
        val first = myLocation == null
        myLocation = point
        if (first && _state.value.route == RouteState.WaitingForLocation) planRoute()
        navigate(point)
    }

    fun startNavigation() {
        val ready = _state.value.route as? RouteState.Ready ?: return
        val nav = Navigator(ready.route)
        navigator = nav
        prompter.reset()
        _state.update { it.copy(navigation = NavigationState(), results = emptyList(), query = "") }
        _speech.tryEmit(prompter.start(ready.route.distanceMeters.toDouble()))
        myLocation?.let(::navigate)
    }

    fun stopNavigation() {
        navigator = null
        rerouteJob?.cancel()
        _state.update { it.copy(navigation = null) }
    }

    private fun navigate(point: LatLon) {
        val nav = navigator ?: return
        val progress = nav.update(point)
        _state.update { s -> s.navigation?.let { s.copy(navigation = it.copy(progress = progress)) } ?: s }
        prompter.next(progress)?.let { _speech.tryEmit(it) }
        if (progress.offRoute) reroute(point)
    }

    /** 骑偏了：从当前位置重新算到终点；隔一会儿才重算一次，免得一路狂发请求 */
    private fun reroute(from: LatLon) {
        val s = _state.value
        val to = s.destination?.location ?: return
        val now = System.currentTimeMillis()
        if (rerouteJob?.isActive == true || now - lastRerouteAt < REROUTE_INTERVAL_MS) return
        lastRerouteAt = now
        _state.update { it.copy(navigation = it.navigation?.copy(rerouting = true)) }
        rerouteJob = viewModelScope.launch {
            try {
                val route = router.route(from, to, s.profile)
                if (navigator == null) return@launch
                navigator = Navigator(route)
                prompter.reset()
                _state.update {
                    it.copy(
                        route = RouteState.Ready(route, SafetyScore.of(route)),
                        navigation = it.navigation?.copy(rerouting = false),
                    )
                }
                myLocation?.let(::navigate)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 没网之类的，过一会儿还偏着会再试
                _state.update { it.copy(navigation = it.navigation?.copy(rerouting = false)) }
            }
        }
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

    companion object {
        private const val REROUTE_INTERVAL_MS = 15_000L
    }
}
