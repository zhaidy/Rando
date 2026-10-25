package com.personal.rando.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.rando.i18n.tr
import com.personal.rando.places.SavedPlace
import com.personal.rando.places.SavedPlaceStore
import com.personal.rando.rando
import com.personal.rando.ride.RideSession
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.RoutePrefs
import com.personal.rando.routing.RouteOption
import com.personal.rando.routing.RouteOptions
import com.personal.rando.routing.RoadCorrectionStore
import com.personal.rando.weather.RouteWind
import com.personal.rando.places.Covered
import com.personal.rando.places.CoveredStretch
import com.personal.rando.weather.Wind
import com.personal.rando.weather.WindClient
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Route
import com.personal.rando.routing.LoopPlanner
import com.personal.rando.nav.Geo
import kotlin.math.abs
import com.personal.rando.places.AmenityClient
import com.personal.rando.places.AmenityOnRoute
import com.personal.rando.places.onRoute
import com.personal.rando.routing.RouteTimeline
import com.personal.rando.weather.RouteWeather
import com.personal.rando.weather.Weather
import com.personal.rando.weather.WeatherClient
import com.personal.rando.weather.Climate
import com.personal.rando.weather.ClimateClient
import com.personal.rando.weather.along
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.personal.rando.routing.LoopShape
import com.personal.rando.routing.LoopScore
import com.personal.rando.search.Place
import com.personal.rando.search.PlaceSearch
import com.personal.rando.places.SavedRouteStore
import com.personal.rando.places.SavedRoute
import com.personal.rando.group.SharedRoute
import com.personal.rando.routing.Pace
import com.personal.rando.routing.MySpeed
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RouteState {
    data object Idle : RouteState
    /** 起点用「我的位置」，但还没定位到 */
    data object WaitingForLocation : RouteState
    data object Loading : RouteState
    /**
     * [stops]：依次经过的点，起点、途经点、终点。[options] 是几条可选的路线（只有一条时是空的），
     * [route] 是选中的第 [chosen] 条
     */
    data class Ready(
        val route: Route,
        val stops: List<LatLon>,
        val options: List<RouteOption> = emptyList(),
        val chosen: Int = 0,
    ) : RouteState {
        /** 选中的那条是按什么偏好算的；null 就是按现在的偏好 */
        val chosenPrefs: RoutePrefs? get() = options.getOrNull(chosen)?.prefs
    }
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
    /** 下一次长按地图或搜索选的是家（从设置里点进来的），不算路 */
    val pickingHome: Boolean = false,
    /** 不去哪儿，骑一圈回到起点 */
    val loop: LoopRequest? = null,
    val query: String = "",
    val results: List<Place> = emptyList(),
    val route: RouteState = RouteState.Idle,
    /** 沿途天气；还没查到或者查不到是 null */
    val weather: RouteWeather? = null,
    /** 起点现在的气温、紫外线、空气质量 */
    val climate: Climate? = null,
    /** 沿途补给点 */
    val amenities: List<AmenityOnRoute> = emptyList(),
    /** 沿途的风 */
    val wind: RouteWind? = null,
    /** 路线经过的有顶棚的路 */
    val covered: List<CoveredStretch> = emptyList(),
)

/** 长按地图选的点的名字；不是搜到的地方，收藏、存路线时不拿它当名字 */
private const val MAP_POINT = "地图上的点"
private const val MAP_POINT_EN = "Map point"

/** 名字是不是「地图上的点 / 起点 / 途经点」（中英文都算，切过语言也认得） */
fun isMapPlaceName(name: String): Boolean = name.startsWith("地图上") || name.startsWith("Map ")

/** 骑一圈：大约多远，第几次换方向 */
data class LoopRequest(val targetMeters: Int, val attempt: Int = 0)

/** 规划路线；开始导航后交给 [RideSession]，它跟着进程走，界面关了也继续 */
class RouteViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val router = app.rando.router
    private val places = PlaceSearch()
    val session: RideSession = app.rando.session
    val savedPlaces: SavedPlaceStore = app.rando.savedPlaces
    val savedRoutes: SavedRouteStore = app.rando.savedRoutes
    val roadCorrections: RoadCorrectionStore = app.rando.roadCorrections

    /** 记住上次选的路线偏好 */
    private val stored = app.getSharedPreferences("planner", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        saved.get<String>(KEY_PLAN)?.let { PlanCodec.decode(it, PlannerState(prefs = loadPrefs())) } ?: PlannerState(prefs = loadPrefs())
    )
    val state: StateFlow<PlannerState> = _state

    private var myLocation: LatLon? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private val weatherClient = WeatherClient()
    private val climateClient = ClimateClient()
    private val amenityClient = AmenityClient()
    private val windClient = WindClient()
    /** 风也十分钟内不重复查 */
    private var cachedWind: Pair<Long, Wind>? = null
    /** 天气十分钟内不重复查 */
    private var cachedWeather: Pair<Long, Weather>? = null

    init {
        viewModelScope.launch { session.location.filterNotNull().collect(::onMyLocation) }
        // 打开时、每骑完一次重新算一下我的平均速度
        viewModelScope.launch {
            session.recording.map { it != null }.distinctUntilChanged().filter { !it }.collect { refreshMySpeed() }
        }
        // 被系统关掉后恢复的：路线没存，重新算
        planRoute()
        // 起点、终点、途经点一变就存下来
        viewModelScope.launch {
            _state.map { PlanCodec.encode(it) }.distinctUntilChanged().collect { saved[KEY_PLAN] = it }
        }
    }

    fun setPrefs(prefs: RoutePrefs) {
        stored.edit {
            putString(KEY_PROFILE, prefs.profile.name)
            putBoolean(KEY_AVOID_ROADS, prefs.avoidRoads)
            putBoolean(KEY_AVOID_BRIDGES, prefs.avoidBridges)
            putBoolean(KEY_SHADE_AT_NOON, prefs.shadeAtNoon)
            putBoolean(KEY_LIT_AT_NIGHT, prefs.litAtNight)
            putString(KEY_PACE, prefs.pace.name)
        }
        _state.update { it.copy(prefs = prefs) }
        planRoute()
    }

    fun setPace(pace: Pace) = setPrefs(_state.value.prefs.copy(pace = pace))

    private suspend fun refreshMySpeed() {
        val speed = MySpeed.of(session.rides.list().map { it.distanceMeters to it.movingSeconds })
        if (speed == _state.value.prefs.mySpeed) return
        _state.update { it.copy(prefs = it.prefs.copy(mySpeed = speed)) }
        // 路线已经在了，按新的速度重新算一下预计时间
        if (_state.value.route != RouteState.Idle) planRoute()
    }

    private fun loadPrefs(): RoutePrefs {
        val name = stored.getString(KEY_PROFILE, null)
        // 旧版本把「不上马路」存成单独的一种配置
        if (name == "NO_ROADS") return RoutePrefs(BikeProfile.PCN, avoidRoads = true)
        return RoutePrefs(
            profile = BikeProfile.entries.firstOrNull { it.name == name } ?: BikeProfile.PCN,
            avoidRoads = stored.getBoolean(KEY_AVOID_ROADS, false),
            avoidBridges = stored.getBoolean(KEY_AVOID_BRIDGES, false),
            shadeAtNoon = stored.getBoolean(KEY_SHADE_AT_NOON, true),
            litAtNight = stored.getBoolean(KEY_LIT_AT_NIGHT, true),
            pace = Pace.entries.firstOrNull { it.name == stored.getString(KEY_PACE, null) } ?: Pace.MINE,
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
                places.search(query.trim(), _state.value.start?.location ?: myLocation)
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
        if (_state.value.pickingHome) return saveHome(place.location)
        _state.update { it.copy(query = "", results = emptyList()).with(place) }
        planRoute()
    }

    fun onMapLongPress(point: LatLon) {
        if (_state.value.pickingHome) return saveHome(point)
        _state.update { it.with(Place(name = tr(MAP_POINT, MAP_POINT_EN), detail = "", location = point)) }
        planRoute()
    }

    /** 选中的地方按现在在选什么放进去：起点、途经点，或者目的地 */
    private fun PlannerState.with(place: Place): PlannerState = when {
        pickingStart -> copy(start = if (place.isMapPoint) place.copy(name = tr("地图上的起点", "Map start")) else place, pickingStart = false)
        pickingVia -> copy(via = insertVia(this, if (place.isMapPoint) place.copy(name = tr("地图上的途经点", "Map stop")) else place), pickingVia = false)
        else -> copy(destination = place, loop = null)
    }

    private val Place.isMapPoint get() = detail.isEmpty() && (name == MAP_POINT || name == MAP_POINT_EN)

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

    fun startPickingVia() = _state.update { it.copy(pickingVia = true, pickingStart = false, pickingHome = false) }

    /** 从设置里点「在地图上选」：下一次搜索或长按地图的地方存成家 */
    fun startPickingHome() = _state.update { it.copy(pickingHome = true, pickingStart = false, pickingVia = false) }

    private fun saveHome(location: LatLon) {
        savedPlaces.save(SavedPlace(SavedPlace.HOME, location))
        _state.update { it.copy(pickingHome = false, query = "", results = emptyList()) }
    }

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

    fun chooseSaved(place: SavedPlace) = choosePlace(Place(place.label, "", place.location))

    /** 语音说的：路线一算好就开始导航 */
    private var startWhenReady = false

    /** 语音「去某个地方」：收藏的地方优先，没有就搜，取第一个结果；算好路直接开始导航 */
    fun goTo(name: String, onFailed: (String) -> Unit) {
        savedPlaces.places.value.firstOrNull { it.name == name }?.let {
            arriveAt(Place(it.label, "", it.location))
            return
        }
        viewModelScope.launch {
            val place = try {
                places.search(name, _state.value.start?.location ?: myLocation).firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (place == null) {
                onFailed(tr("没找到$name", "Couldn't find ${SavedPlace.label(name)}"))
                return@launch
            }
            arriveAt(place)
        }
    }

    /** 语音说的地方一定是目的地：正在选家、起点、途经点的都先取消，不然会放错位置、一直等着自动开始 */
    private fun arriveAt(place: Place) {
        _state.update { it.copy(pickingHome = false, pickingStart = false, pickingVia = false) }
        startWhenReady = true
        choosePlace(place)
    }

    /** 语音「骑一圈」「骑某条收藏的路线」：算好直接开始 */
    fun startLoop(km: Int) {
        startWhenReady = true
        chooseLoop(km)
    }

    fun startSavedRoute(name: String): Boolean {
        val r = savedRoutes.routes.value.firstOrNull { it.name == name }
            ?: savedRoutes.routes.value.firstOrNull { name in it.name || it.name in name }
            ?: return false
        startWhenReady = true
        chooseSavedRoute(r)
        return true
    }

    /** 收藏现在这条路线：起点、途经点、终点和路线配置 */
    fun saveRoute(name: String) {
        val s = _state.value
        val ready = s.route as? RouteState.Ready ?: return
        val prefs = ready.chosenPrefs ?: s.prefs
        savedRoutes.save(
            SavedRoute(
                name = name, stops = ready.stops, profile = prefs.profile, avoidRoads = prefs.avoidRoads,
                avoidBridges = prefs.avoidBridges, meters = ready.route.distanceMeters, savedAt = System.currentTimeMillis(),
            )
        )
    }

    /** 再骑一次收藏的路线：人在起点附近就从我的位置出发，不然从收藏的起点出发 */
    fun chooseSavedRoute(r: SavedRoute) {
        searchJob?.cancel()
        routeJob?.cancel()
        val first = r.stops.first()
        val nearStart = myLocation?.let { Geo.distance(it, first) <= NEAR_START_METERS } == true
        val via = r.stops.drop(1).dropLast(1).mapIndexed { i, p -> Place(tr("途经点 ${i + 1}", "Stop ${i + 1}"), "", p) }
        _state.update {
            it.copy(
                query = "", results = emptyList(), loop = null, pickingStart = false, pickingVia = false,
                start = if (nearStart) null else Place(tr("${r.name}的起点", "Start of ${r.name}"), "", first),
                destination = Place(if (r.loop) tr("${r.name}（回到起点）", "${r.name} (back to start)") else r.name, "", r.stops.last()),
                via = via,
                prefs = r.prefs(it.prefs),
            )
        }
        planRoute()
    }

    fun startPickingStart() = _state.update { it.copy(pickingStart = true, pickingVia = false, pickingHome = false) }

    /** 不选起点 / 途经点 / 家了，路线照旧 */
    fun cancelPicking() {
        searchJob?.cancel()
        _state.update { it.copy(pickingStart = false, pickingVia = false, pickingHome = false, query = "", results = emptyList()) }
    }

    fun useMyLocationAsStart() {
        _state.update { it.copy(start = null, pickingStart = false) }
        planRoute()
    }

    fun clearRoute() {
        startWhenReady = false
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
        val title = s.destination?.name ?: s.loop?.let { tr("骑一圈 ${it.targetMeters / 1000} 公里", "${it.targetMeters / 1000} km loop") } ?: tr("路线", "Route")
        session.startNavigation(ready.route, ready.stops, ready.chosenPrefs ?: s.prefs, title)
    }

    /** 换成第 [index] 条可选的路线；天气、补给点按这条重新查 */
    fun chooseOption(index: Int) {
        val ready = _state.value.route as? RouteState.Ready ?: return
        val option = ready.options.getOrNull(index) ?: return
        if (index == ready.chosen) return
        routeJob?.cancel()
        _state.update {
            it.copy(route = ready.copy(route = option.route, chosen = index), weather = null, amenities = emptyList(), wind = null, covered = emptyList())
        }
        routeJob = viewModelScope.launch { loadExtras(option.route) }
    }

    fun retry() = planRoute()

    /** 导航到某个点（比如摔车的队友那儿）；算路失败时告诉 [onFailed] */
    fun navigateTo(target: LatLon, title: String, onFailed: (String) -> Unit) {
        val prefs = _state.value.prefs
        viewModelScope.launch {
            try {
                session.navigateTo(target, prefs, title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailed(e.message ?: tr("算路失败", "Routing failed"))
            }
        }
    }

    /** 跟着队友分享的路线骑；算路失败时告诉 [onFailed] */
    fun followShared(shared: SharedRoute, onFailed: (String) -> Unit) {
        val prefs = _state.value.prefs
        viewModelScope.launch {
            try {
                session.followShared(shared, prefs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailed(e.message ?: tr("算路失败", "Routing failed"))
            }
        }
    }

    /** 沿刚骑完的轨迹原路返回，直接开始导航 */
    fun returnTrip(track: List<LatLon>, onFailed: (String) -> Unit) {
        val prefs = _state.value.prefs
        viewModelScope.launch {
            try {
                session.startReturnTrip(track, prefs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailed(e.message ?: tr("算路失败", "Routing failed"))
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
        _state.update { it.copy(route = RouteState.Loading, weather = null, climate = null, amenities = emptyList(), wind = null, covered = emptyList()) }
        routeJob = viewModelScope.launch {
            // 去某个地方时另外算几条不同的，先把按偏好算的那条显示出来，其余的算好再给选
            val stops = listOf(from) + s.via.map { it.location } + (s.destination?.location ?: from)
            val others = if (s.loop != null) emptyList() else RouteOptions.variants(s.prefs).drop(1).map { v ->
                async { quietly { router.route(stops, v.prefs, v.alternative) }?.let { it to v.prefs } }
            }
            val next = try {
                if (s.loop != null) {
                    planLoop(from, s.loop, s.prefs)
                } else {
                    RouteState.Ready(router.route(stops, s.prefs), stops)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                others.forEach { it.cancel() }
                RouteState.Failed(e.message ?: tr("算路失败", "Routing failed"))
            }
            _state.update { it.copy(route = next) }
            if (startWhenReady) {
                startWhenReady = false
                if (next is RouteState.Ready) startNavigation() else if (next is RouteState.Failed) session.say(tr("算路失败，${next.message}", "Routing failed, ${next.message}"))
            }
            if (next is RouteState.Ready && others.isNotEmpty()) launch {
                val found = others.awaitAll().filterNotNull()
                val options = withContext(Dispatchers.Default) { RouteOptions.pick(listOf(next.route to s.prefs) + found) }
                if (options.size > 1) _state.update { st ->
                    val ready = st.route as? RouteState.Ready
                    if (ready?.route === next.route) st.copy(route = ready.copy(options = options)) else st
                }
            }
            if (next is RouteState.Ready) loadExtras(next.route)
        }
    }

    /**
     * 画一圈：同时试几个方向的圈，每个先按经验估大小，长度差太多按比例再调一次；
     * 最后挑马路最少、长度最接近的那条。途经点是按形状画的，有的方向附近只有马路，
     * 开了「不上马路」也只能走马路去碰它，所以要多试几个方向比一比
     */
    private suspend fun planLoop(from: LatLon, loop: LoopRequest, prefs: RoutePrefs): RouteState = coroutineScope {
        val target = loop.targetMeters.toDouble()
        val shapes = (0 until LOOP_CANDIDATES)
            .mapNotNull { LoopPlanner.plan(from, target, loop.attempt * LOOP_CANDIDATES + it) }
            .distinct()
        if (shapes.isEmpty()) return@coroutineScope RouteState.Failed(tr("附近画不下这么大的圈，换个距离试试", "Can't fit a loop that size around here, try another distance"))
        val tried = shapes.map { shape -> async { tryLoop(from, shape, target, prefs) } }.awaitAll()
        val best = tried.mapNotNull { it.getOrNull() }.minByOrNull { (route, _) -> LoopScore.of(route, target) }
            ?: return@coroutineScope RouteState.Failed(tried.firstNotNullOfOrNull { it.exceptionOrNull()?.message } ?: tr("算路失败", "Routing failed"))
        // 先逆风出去、顺风回来：回来的路上累了，别再顶风
        val wind = windAt(from)
        if (wind != null && wind.along(best.first.points).betterReversed()) {
            val stops = best.second.reversed()
            quietly { router.route(stops, prefs) }?.let { return@coroutineScope RouteState.Ready(it, stops) }
        }
        RouteState.Ready(best.first, best.second)
    }

    private suspend fun windAt(at: LatLon): Wind? {
        val now = System.currentTimeMillis()
        cachedWind?.takeIf { now - it.first < WEATHER_TTL_MS }?.let { return it.second }
        return quietly { windClient.at(at) }?.also { cachedWind = now to it }
    }

    /** 按一个圈算路线；返回 (路线, 依次经过的点) */
    private suspend fun tryLoop(from: LatLon, shape: LoopShape, target: Double, prefs: RoutePrefs): Result<Pair<Route, List<LatLon>>> {
        val stops = listOf(from) + shape.waypoints + from
        val route = try {
            router.route(stops, prefs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val ratio = target / route.distanceMeters.coerceAtLeast(1)
        val adjusted = (if (abs(ratio - 1) > LOOP_TOLERANCE) shape.scaled(ratio) else null)
            ?: return Result.success(route to stops)
        val retryStops = listOf(from) + adjusted.waypoints + from
        val retry = quietly { router.route(retryStops, prefs) }
        return if (retry != null && LoopScore.of(retry, target) < LoopScore.of(route, target)) {
            Result.success(retry to retryStops)
        } else {
            Result.success(route to stops)
        }
    }

    /** 路线算好后再查天气和补给点，查不到就不显示，不耽误看路线 */
    private suspend fun loadExtras(route: Route) = coroutineScope {
        launch {
            val now = System.currentTimeMillis()
            val weather = cachedWeather?.takeIf { now - it.first < WEATHER_TTL_MS }?.second
                ?: quietly { weatherClient.fetch() }?.also { cachedWeather = now to it }
            weather?.let { w -> _state.update { it.copy(weather = w.along(route.points)) } }
        }
        launch {
            route.points.firstOrNull()?.let { windAt(it) }?.let { w -> _state.update { it.copy(wind = w.along(route.points)) } }
        }
        launch {
            route.points.firstOrNull()?.let { start -> quietly { climateClient.at(start) } }
                ?.let { c -> _state.update { it.copy(climate = c) } }
        }
        launch {
            quietly { amenityClient.along(route.points) }?.let { found ->
                val covered = withContext(Dispatchers.Default) { Covered.onRoute(route.points, found.covered) }
                _state.update { it.copy(amenities = found.amenities.onRoute(RouteTimeline(route)), covered = covered) }
            }
        }
    }

    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val WEATHER_TTL_MS = 10 * 60_000L
        /** 每次同时试几个方向 */
        const val LOOP_CANDIDATES = 4
        /** 圈的长度差这么多以内就不调了 */
        const val LOOP_TOLERANCE = 0.15
        const val KEY_PLAN = "plan"
        const val KEY_PROFILE = "profile"
        const val KEY_AVOID_ROADS = "avoid_roads"
        const val KEY_AVOID_BRIDGES = "avoid_bridges"
        const val KEY_SHADE_AT_NOON = "shade_at_noon"
        const val KEY_LIT_AT_NIGHT = "lit_at_night"
        const val KEY_PACE = "pace"
        /** 离收藏路线的起点这么近，就从我的位置出发 */
        const val NEAR_START_METERS = 300.0
    }
}
