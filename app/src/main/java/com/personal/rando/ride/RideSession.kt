package com.personal.rando.ride

import android.content.Context
import com.personal.rando.nav.NavProgress
import com.personal.rando.nav.Navigator
import com.personal.rando.nav.RoadAlerts
import com.personal.rando.nav.Speaker
import com.personal.rando.nav.VoicePrompter
import com.personal.rando.routing.BRouterClient
import com.personal.rando.routing.RoutePrefs
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Route
import com.personal.rando.routing.RouteTimeline
import com.personal.rando.routing.ReturnTrip
import com.personal.rando.nav.DaylightAlerts
import com.personal.rando.group.GroupRide
import com.personal.rando.nav.Geo
import com.personal.rando.places.AmenityClient
import com.personal.rando.places.AmenityOnRoute
import com.personal.rando.places.onRoute
import com.personal.rando.weather.RouteWeather
import com.personal.rando.weather.WeatherClient
import com.personal.rando.weather.along
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NavigationState(
    /** 当前在走的路线；骑偏重新规划后会换成新的 */
    val route: Route,
    val destination: LatLon,
    /** 还没经过的途经点，按顺序；骑偏重新算路时要接着经过它们 */
    val via: List<LatLon> = emptyList(),
    val prefs: RoutePrefs,
    /** 还没定位到时是 null */
    val progress: NavProgress? = null,
    val rerouting: Boolean = false,
    /** 最近一次定位的误差（米）；信号差时告诉人为什么还没开始导航 */
    val gpsAccuracy: Float? = null,
) {
    /** 还没对上路线时显示的话：信号太差就说出误差，免得以为卡住了 */
    val waitingText: String
        get() = gpsAccuracy?.takeIf { it > NAV_MAX_ACCURACY_METERS }
            ?.let { "GPS 信号弱，误差约 ${Math.round(it)} 米，到空旷处会好些" }
            ?: "等待 GPS 定位…"
}

/** 导航时精度比这差的定位不要 */
const val NAV_MAX_ACCURACY_METERS = 50f

/**
 * 一次骑行：导航、轨迹记录和语音都放在这里，跟着进程而不是界面走。
 * 锁屏、切到别的 App 时由 [RideService] 继续喂定位进来。
 */
class RideSession(
    context: Context,
    private val scope: CoroutineScope,
    private val router: BRouterClient = BRouterClient(),
    private val weatherClient: WeatherClient = WeatherClient(),
    private val amenityClient: AmenityClient = AmenityClient(),
    private val group: GroupRide? = null,
) {
    private val app = context.applicationContext
    private val speaker by lazy { Speaker(app) }
    val rides = RideStore(app)

    private val _location = MutableStateFlow<LatLon?>(null)
    /** 最近一次定位，规划路线时当起点 */
    val location: StateFlow<LatLon?> = _location

    private val _fix = MutableStateFlow<Fix?>(null)
    /** 最近一次定位，带速度和方向；骑行视图的地图拿它画蓝点 */
    val fix: StateFlow<Fix?> = _fix

    private val _navigation = MutableStateFlow<NavigationState?>(null)
    /** 不为 null 就是在导航中 */
    val navigation: StateFlow<NavigationState?> = _navigation

    private val _recording = MutableStateFlow<RecordingState?>(null)
    /** 不为 null 就是在记录轨迹 */
    val recording: StateFlow<RecordingState?> = _recording

    private val _weather = MutableStateFlow<RouteWeather?>(null)
    /** 骑行中前方（或附近）的天气，十分钟更新一次 */
    val weather: StateFlow<RouteWeather?> = _weather

    private val _amenities = MutableStateFlow<List<AmenityOnRoute>>(emptyList())
    /** 导航路线两旁的补给点 */
    val amenities: StateFlow<List<AmenityOnRoute>> = _amenities

    private var recorder: RideRecorder? = null
    private val daylight = DaylightAlerts()
    private var weatherJob: Job? = null
    private var amenityJob: Job? = null
    /** 上次播过的天气，变了才再说 */
    private var spokenWeather: String? = null
    private var navigator: Navigator? = null
    private var roadAlerts: RoadAlerts? = null
    private val prompter = VoicePrompter()
    private var rerouteJob: Job? = null
    /** 还没经过的途经点在当前路线上的位置（米） */
    private var viaAlong: List<Double> = emptyList()
    private var lastRerouteAt = 0L

    fun onFix(fix: Fix) {
        // GPS 和网络定位一起听：有了准的点，几秒内来的明显更不准的就不要，免得蓝点来回跳
        val last = _fix.value
        if (last != null && fix.accuracy != null && last.accuracy != null &&
            fix.accuracy > last.accuracy * 2 && fix.time - last.time < KEEP_PRECISE_MS
        ) {
            return
        }
        _location.value = fix.location
        _fix.value = fix
        // 网络定位误差上百米，导航时会被当成骑偏了
        val precise = fix.accuracy == null || fix.accuracy <= NAV_MAX_ACCURACY_METERS
        if (navigator != null) {
            if (fix.accuracy != _navigation.value?.gpsAccuracy) _navigation.update { it?.copy(gpsAccuracy = fix.accuracy) }
            if (precise) navigate(fix.location)
        }
        if (navigator != null || recorder != null) daylight.next(System.currentTimeMillis())?.let(speaker::say)
        group?.let { g ->
            g.onFix(fix)
            if (precise && recorder != null) g.stragglerAlert(fix.location)?.let(speaker::say)
        }
        recorder?.let {
            it.add(fix)
            _recording.value = it.state()
        }
    }

    fun startRecording() {
        if (recorder != null) return
        val r = RideRecorder(System.currentTimeMillis())
        recorder = r
        _recording.value = r.state()
        daylight.reset()
        watchWeather()
        RideService.start(app)
    }

    /** 结束记录并保存；太短的（没怎么动）不存，返回 null */
    fun stopRecording(): Ride? {
        val r = recorder ?: return null
        recorder = null
        _recording.value = null
        stopWatchingWeather()
        val ride = r.toRide(System.currentTimeMillis())
        if (ride.distanceMeters < MIN_RIDE_METERS) return null
        scope.launch { rides.save(ride) }
        return ride
    }

    fun startNavigation(route: Route, destination: LatLon, via: List<LatLon>, prefs: RoutePrefs) {
        follow(route, via)
        loadAmenities(route)
        rerouteJob?.cancel()
        _navigation.value = NavigationState(route, destination, via, prefs)
        speaker.say(prompter.start(route.distanceMeters.toDouble()))
        // 导航顺便记录轨迹，结束时一起保存
        startRecording()
        RideService.start(app)
    }

    /**
     * 原路返回：沿着这次骑过的轨迹倒着回到出发的地方。
     * 轨迹上每隔一段取一个点当途经点，BRouter 就会走回同样的路
     */
    suspend fun startReturnTrip(prefs: RoutePrefs) {
        val track = _recording.value?.track?.map { it.location }.orEmpty()
        val stops = ReturnTrip.stops(track) ?: throw IllegalStateException("还没骑多远，没有路可以原路返回")
        val route = router.route(stops, prefs)
        startNavigation(route, stops.last(), stops.drop(1).dropLast(1), prefs)
    }

    /** 能不能原路返回：在记录，而且离出发的地方有段距离了 */
    fun canReturn(recording: RecordingState?): Boolean = ReturnTrip.stops(recording?.track?.map { it.location }.orEmpty()) != null

    /** 停掉导航；RideService 看到没事可做了会自己退出 */
    fun stopNavigation() {
        navigator = null
        roadAlerts = null
        rerouteJob?.cancel()
        amenityJob?.cancel()
        _amenities.value = emptyList()
        _navigation.value = null
    }

    /** 换一条路线来跟；记下每个途经点在这条路线上的位置，骑过了就划掉 */
    private fun follow(route: Route, via: List<LatLon>) {
        val nav = Navigator(route)
        navigator = nav
        viaAlong = via.map { nav.alongOf(it) }
        roadAlerts = RoadAlerts(RouteTimeline(route))
        prompter.reset()
    }

    private fun navigate(point: LatLon) {
        val nav = navigator ?: return
        val progress = nav.update(point)
        val passed = if (progress.offRoute) 0 else viaAlong.takeWhile { progress.alongMeters >= it - VIA_PASSED_METERS }.size
        if (passed > 0) viaAlong = viaAlong.drop(passed)
        _navigation.update { it?.copy(progress = progress, via = it.via.drop(passed)) }
        prompter.next(progress)?.let(speaker::say)
        if (!progress.offRoute && !progress.arrived) roadAlerts?.next(progress.alongMeters)?.let(speaker::say)
        if (progress.offRoute) reroute(point)
    }

    /** 查路线两旁的补给点；查不到就算了，不影响导航 */
    private fun loadAmenities(route: Route) {
        amenityJob?.cancel()
        amenityJob = scope.launch {
            try {
                _amenities.value = amenityClient.along(route.points).onRoute(RouteTimeline(route))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 骑行中每十分钟看一次天气：导航时看前方还没骑的路，自由骑行时看附近。
     * 前方要下雨了（或者雨的情况变了）说一声
     */
    private fun watchWeather() {
        weatherJob?.cancel()
        spokenWeather = null
        weatherJob = scope.launch {
            while (true) {
                try {
                    val weather = weatherClient.fetch()
                    val ahead = aheadPoints()
                    val along = if (ahead.isNotEmpty()) weather.along(ahead) else null
                    _weather.value = along
                    val text = along?.takeIf { !it.dry }?.summary
                    if (text != null && text != spokenWeather) speaker.say("注意，$text")
                    spokenWeather = text
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 没网就下次再看
                }
                delay(WEATHER_INTERVAL_MS)
            }
        }
    }

    private fun stopWatchingWeather() {
        weatherJob?.cancel()
        _weather.value = null
    }

    /** 前方还没骑的路；没在导航就只有现在的位置 */
    private fun aheadPoints(): List<LatLon> {
        val here = _location.value
        val nav = _navigation.value ?: return listOfNotNull(here)
        val points = nav.route.points
        val from = here?.let { h -> points.indices.minByOrNull { Geo.distance(points[it], h) } } ?: 0
        return points.subList(from, points.size)
    }

    /** 骑偏了：从当前位置重新算到终点；隔一会儿才重算一次，免得一路狂发请求 */
    private fun reroute(from: LatLon) {
        val state = _navigation.value ?: return
        val now = System.currentTimeMillis()
        if (rerouteJob?.isActive == true || now - lastRerouteAt < REROUTE_INTERVAL_MS) return
        lastRerouteAt = now
        _navigation.update { it?.copy(rerouting = true) }
        rerouteJob = scope.launch {
            try {
                val via = _navigation.value?.via ?: return@launch
                val route = router.route(listOf(from) + via + state.destination, state.prefs)
                if (navigator == null) return@launch
                follow(route, via)
                loadAmenities(route)
                _navigation.update { it?.copy(route = route, via = via, rerouting = false) }
                _location.value?.let(::navigate)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 没网之类的，过一会儿还偏着会再试
                _navigation.update { it?.copy(rerouting = false) }
            }
        }
    }

    private companion object {
        const val REROUTE_INTERVAL_MS = 15_000L
        const val MIN_RIDE_METERS = 100.0
        const val WEATHER_INTERVAL_MS = 10 * 60_000L
        /** 准的定位在这么久以内，不准的不要 */
        const val KEEP_PRECISE_MS = 10_000L
        /** 骑到离途经点这么近就算经过了 */
        const val VIA_PASSED_METERS = 50.0
    }
}
