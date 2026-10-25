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
import com.personal.rando.places.ParkingStore
import com.personal.rando.group.GroupEvent
import com.personal.rando.places.HazardType
import com.personal.rando.places.HazardStore
import com.personal.rando.places.HazardAlerts
import com.personal.rando.places.Hazard
import com.personal.rando.places.onRoute
import com.personal.rando.places.Covered
import com.personal.rando.places.CoveredStretch
import com.personal.rando.places.nextAhead
import com.personal.rando.weather.RouteWeather
import com.personal.rando.weather.WeatherClient
import com.personal.rando.weather.Climate
import com.personal.rando.weather.ClimateClient
import com.personal.rando.weather.HydrationReminder
import com.personal.rando.places.AmenityType
import com.personal.rando.weather.along
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import com.personal.rando.group.SharedRoute
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.combine
import com.personal.rando.ui.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** 可能摔车了：[deadline] 前没点「我没事」就告诉队友；[sent] 已经告诉了 */
data class CrashAlert(val deadline: Long, val sent: Boolean = false)

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
    private val climateClient: ClimateClient = ClimateClient(),
    private val group: GroupRide? = null,
    /** 标过的路况：骑近了提醒；施工封路由 [router] 算路时绕开 */
    private val hazards: HazardStore? = null,
    /** 结束骑行时记下车停在哪儿 */
    private val parking: ParkingStore? = null,
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

    private val _climate = MutableStateFlow<Climate?>(null)
    /** 骑行中所在地方的气温、紫外线、空气质量，跟天气一起十分钟更新一次 */
    val climate: StateFlow<Climate?> = _climate

    private val _crash = MutableStateFlow<CrashAlert?>(null)
    /** 检测到可能摔车了，等你回答 */
    val crash: StateFlow<CrashAlert?> = _crash
    private val crashDetector = CrashDetector()
    private val crashWatch = CrashWatch(context, crashDetector) { scope.launch { onCrash() } }
    private var crashJob: Job? = null

    private val _amenities = MutableStateFlow<List<AmenityOnRoute>>(emptyList())
    /** 导航路线两旁的补给点 */
    val amenities: StateFlow<List<AmenityOnRoute>> = _amenities

    private var recorder: RideRecorder? = null
    private val daylight = DaylightAlerts()
    private var weatherJob: Job? = null
    private var amenityJob: Job? = null
    /** 上次播过的天气，变了才再说 */
    private var spokenWeather: String? = null
    private var spokenClimate: String? = null
    private val hydration = HydrationReminder()
    private val hazardAlerts = HazardAlerts()
    /** 当前导航路线的时间线，路况提醒用 */
    private var navTimeline: RouteTimeline? = null
    private var navigator: Navigator? = null
    private var roadAlerts: RoadAlerts? = null
    private val prompter = VoicePrompter()
    private var rerouteJob: Job? = null
    /** 还没经过的途经点在当前路线上的位置（米） */
    private var viaAlong: List<Double> = emptyList()
    private var lastRerouteAt = 0L
    /** 这次导航分享给队友的路线；导航中才进队的，进队时补发 */
    private var shared: SharedRoute? = null
    private var autoStopJob: Job? = null

    init {
        // 队友标的路况存下来；删掉的也跟着删
        if (group != null && hazards != null) {
            scope.launch {
                group.hazards.collect { e ->
                    when (e) {
                        is GroupEvent.HazardAdded -> hazards.add(e.hazard)
                        is GroupEvent.HazardRemoved -> hazards.remove(e.hazardId)
                        else -> Unit
                    }
                }
            }
        }
        group?.let { g ->
            scope.launch {
                g.state.map { it?.code }.distinctUntilChanged().collect { code -> if (code != null) shared?.let(g::shareRoute) }
            }
            // 一起骑、在记录时才开摔车检测（只通知队友）
            scope.launch {
                combine(g.state.map { it != null }, _recording.map { it != null }) { inGroup, riding -> inGroup && riding }
                    .distinctUntilChanged()
                    .collect { on -> if (on) crashWatch.start() else crashWatch.stop() }
            }
            // 队友可能摔车了：大声说出来
            scope.launch {
                var known = emptySet<String>()
                g.sos.collect { list ->
                    val here = _location.value
                    list.filter { "${it.id}:${it.ts}" !in known }.forEach { s ->
                        val far = here?.let { "，离你 ${Format.distance(Geo.distance(it, s.location).toInt())}" }.orEmpty()
                        speaker.say("注意，${s.name}可能摔车了$far")
                    }
                    known = list.map { "${it.id}:${it.ts}" }.toSet()
                }
            }
            // 队友分享了路线：骑车时不一定看屏幕，念一声
            scope.launch {
                g.offer.filterNotNull().distinctUntilChanged().collect { speaker.say("${it.name}分享了路线，${it.title}，要跟着骑吗") }
            }
        }
    }

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
        if (precise && (navigator != null || recorder != null)) hazardAlert(fix.location)?.let(speaker::say)
        group?.let { g ->
            g.onFix(fix)
            if (precise && recorder != null) g.stragglerAlert(fix.location)?.let(speaker::say)
            fix.speed?.let { crashDetector.onSpeed(fix.time, it.toDouble()) }
        }
        recorder?.let {
            it.add(fix)
            val state = it.state()
            _recording.value = state
            hydration.next(System.currentTimeMillis(), state.startedAt, _climate.value?.hot == true, waterAhead())?.let(speaker::say)
        }
    }

    private fun hazardAlert(me: LatLon): String? {
        val list = hazards?.all?.value.orEmpty()
        if (list.isEmpty()) return null
        val nav = _navigation.value
        val progress = nav?.progress?.takeIf { !it.offRoute }
        return hazardAlerts.next(list, me, progress?.let { navTimeline }, progress?.alongMeters)
    }

    /** 在现在的位置标一处路况，顺便告诉队友；还没定位到返回 false */
    fun reportHazard(type: HazardType): Boolean {
        val at = _location.value ?: return false
        val h = Hazard.new(type, at)
        hazards?.add(h)
        group?.shareHazard(h)
        // 自己标的不用再提醒自己
        hazardAlerts.skip(h.id)
        return true
    }

    /** 删掉一处路况（修好了、标错了），队友那边也删 */
    fun removeHazard(id: String) {
        hazards?.remove(id)
        group?.shareHazardRemoved(id)
    }

    /**
     * 自动开始的记录：在骑的时间 [AUTO_STOP_MS] 没涨（停下来了），就自动结束保存。
     * 开始导航说明是人在用，不再自动结束
     */
    private fun watchAutoStop() {
        autoStopJob = scope.launch {
            var lastMoving = _recording.value?.movingSeconds ?: 0
            var lastChange = System.currentTimeMillis()
            while (true) {
                delay(30_000)
                if (_navigation.value != null) return@launch
                val moving = _recording.value?.movingSeconds ?: return@launch
                val now = System.currentTimeMillis()
                if (moving != lastMoving) {
                    lastMoving = moving
                    lastChange = now
                } else if (now - lastChange >= AUTO_STOP_MS) {
                    val ride = stopRecording()
                    speaker.say(ride?.let { "停了一会儿，已经结束记录，骑了 ${Format.distance(it.distanceMeters.toInt())}" } ?: "停了一会儿，已经结束记录")
                    return@launch
                }
            }
        }
    }

    /** 念一句话（语音指令的回答） */
    fun say(text: String) = speaker.say(text)

    /** 「还有 3.2 公里，大约 11 分钟」；没在导航时说骑了多远 */
    fun remainingText(): String {
        val p = _navigation.value?.progress
        if (p != null) return "还有 ${Format.distance(p.remainingMeters.toInt())}，大约 ${(p.remainingSeconds + 59) / 60} 分钟"
        val r = _recording.value ?: return "现在没在导航"
        return "已经骑了 ${Format.distance(r.distanceMeters.toInt())}，没在导航"
    }

    /** 天气、气温、空气：语音问「天气怎么样」时说 */
    fun weatherText(): String {
        val parts = listOfNotNull(_weather.value?.summary, _climate.value?.summary?.let { "现在 $it" })
        return parts.joinToString("，").ifEmpty { "开始骑以后才知道沿途天气" }
    }

    /** 检测到可能摔车：倒计时，到时间还没说没事就告诉队友 */
    private fun onCrash() {
        if (_crash.value != null || group?.state?.value == null) return
        val deadline = System.currentTimeMillis() + CRASH_COUNTDOWN_MS
        _crash.value = CrashAlert(deadline)
        speaker.say("你可能摔车了，没事的话点我没事，${CRASH_COUNTDOWN_MS / 1000} 秒后通知队友")
        crashJob?.cancel()
        crashJob = scope.launch {
            delay(CRASH_COUNTDOWN_MS - 10_000)
            speaker.say("10 秒后通知队友")
            delay(10_000)
            sendSos()
        }
    }

    /** 马上告诉队友 */
    fun sendSos() {
        crashJob?.cancel()
        val at = _location.value ?: return
        group?.sendSos(at)
        _crash.update { (it ?: CrashAlert(System.currentTimeMillis())).copy(sent = true) }
        speaker.say("已经通知队友")
    }

    /** 我没事：取消倒计时，已经通知了的也撤回 */
    fun imOk() {
        crashJob?.cancel()
        if (_crash.value?.sent == true) group?.clearSos()
        _crash.value = null
    }

    /** 导航到 [target]（比如摔车的队友那儿），不分享给队友 */
    suspend fun navigateTo(target: LatLon, prefs: RoutePrefs, title: String) {
        val from = _location.value ?: throw IllegalStateException("还没定位到")
        val stops = listOf(from, target)
        startNavigation(router.route(stops, prefs), stops, prefs, title, share = false)
    }

    /** 导航中路线经过的有顶棚的路 */
    private var covered: List<CoveredStretch> = emptyList()

    /** 下雨提醒后面加一句：「，前方 300 米有顶棚可以躲雨」；导航中、前方一公里内有才说 */
    private fun shelterAhead(): String? {
        val along = _navigation.value?.progress?.takeIf { !it.offRoute }?.alongMeters ?: return null
        val roof = covered.nextAhead(along)
        val hut = _amenities.value.filter { it.amenity.type == AmenityType.SHELTER && it.alongMeters >= along }.minOfOrNull { it.alongMeters - along }
        val (meters, what) = listOfNotNull(roof?.let { it to "有顶棚的路" }, hut?.let { it to "避雨亭" }).minByOrNull { it.first } ?: return null
        return when {
            meters > SHELTER_MENTION_METERS -> null
            meters < 20 -> "，现在这段路有顶棚可以躲雨".takeIf { what == "有顶棚的路" } ?: "，旁边有避雨亭"
            else -> "，前方 ${Format.distance(meters.toInt())} 有${what}可以躲雨"
        }
    }

    /** 导航中前方最近的饮水机还有多远 */
    private fun waterAhead(): Double? {
        val along = _navigation.value?.progress?.takeIf { !it.offRoute }?.alongMeters ?: return null
        return _amenities.value.filter { it.amenity.type == AmenityType.WATER && it.alongMeters > along }.minOfOrNull { it.alongMeters - along }
    }

    /** [auto]：认出在骑车自动开始的；停下来很久没动就自动结束保存 */
    fun startRecording(auto: Boolean = false) {
        if (recorder != null) return
        val r = RideRecorder(System.currentTimeMillis())
        recorder = r
        autoStopJob?.cancel()
        if (auto) watchAutoStop()
        hydration.reset(r.state().startedAt)
        RideWidget.refresh(app)
        hazardAlerts.reset()
        _recording.value = r.state()
        daylight.reset()
        watchWeather()
        RideService.start(app)
    }

    /** 结束记录并保存；太短的（没怎么动）不存，返回 null */
    fun stopRecording(): Ride? {
        val r = recorder ?: return null
        recorder = null
        autoStopJob?.cancel()
        _recording.value = null
        // 都结束骑行了，人肯定没事
        imOk()
        RideWidget.refresh(app)
        stopWatchingWeather()
        val ride = r.toRide(System.currentTimeMillis())
        if (ride.distanceMeters < MIN_RIDE_METERS) return null
        (_location.value ?: ride.points.lastOrNull()?.location)?.let { parking?.park(it) }
        scope.launch {
            rides.save(ride)
            RideWidget.refresh(app)
            // 骑过的路：比一比上次
            RouteRecord.of(ride, rides.list())?.let { speaker.say(it.text) }
        }
        return ride
    }

    /**
     * 按 [stops]（起点、途经点、终点）算好的 [route] 开始导航。
     * 在一起骑时把路线分享给队友（[share]）；跟着队友的路线骑时不再分享出去
     */
    fun startNavigation(route: Route, stops: List<LatLon>, prefs: RoutePrefs, title: String, share: Boolean = true) {
        val destination = stops.last()
        val via = stops.drop(1).dropLast(1)
        shared = if (share) {
            SharedRoute(
                ts = System.currentTimeMillis(), title = title, meters = route.distanceMeters, stops = stops,
                profile = prefs.profile, avoidRoads = prefs.avoidRoads, avoidBridges = prefs.avoidBridges,
            ).also { group?.shareRoute(it) }
        } else {
            group?.clearRoute()
            null
        }
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
     * 原路返回：沿着刚结束的那次骑行的轨迹倒着回到出发的地方，开始一次新的导航。
     * 轨迹上每隔一段取一个点当途经点，BRouter 就会走回同样的路
     */
    suspend fun startReturnTrip(track: List<LatLon>, prefs: RoutePrefs) {
        val stops = ReturnTrip.stops(track) ?: throw IllegalStateException("还没骑多远，没有路可以原路返回")
        val route = router.route(stops, prefs)
        startNavigation(route, stops, prefs, title = "原路返回")
    }

    /** 跟着队友分享的路线骑：按同样的点和配置算，预计时间还按我的速度 */
    suspend fun followShared(shared: SharedRoute, mine: RoutePrefs) {
        val prefs = shared.prefs(mine)
        val route = router.route(shared.stops, prefs)
        startNavigation(route, shared.stops, prefs, title = shared.title, share = false)
    }

    /** 停掉导航；RideService 看到没事可做了会自己退出 */
    fun stopNavigation() {
        shared = null
        group?.clearRoute()
        navigator = null
        roadAlerts = null
        rerouteJob?.cancel()
        amenityJob?.cancel()
        _amenities.value = emptyList()
        covered = emptyList()
        _navigation.value = null
    }

    /** 换一条路线来跟；记下每个途经点在这条路线上的位置，骑过了就划掉 */
    private fun follow(route: Route, via: List<LatLon>) {
        navTimeline = RouteTimeline(route)
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
                val found = amenityClient.along(route.points)
                _amenities.value = found.amenities.onRoute(RouteTimeline(route))
                covered = withContext(Dispatchers.Default) { Covered.onRoute(route.points, found.covered) }
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
        spokenClimate = null
        weatherJob = scope.launch {
            while (true) {
                // 气温、空气质量：酷热、烟霾、紫外线很强时说一声，变了再说
                _location.value?.let { here ->
                    try {
                        val climate = climateClient.at(here)
                        _climate.value = climate
                        val warning = climate.warning
                        if (warning != null && warning != spokenClimate) speaker.say(warning)
                        spokenClimate = warning
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
                try {
                    val weather = weatherClient.fetch()
                    val ahead = aheadPoints()
                    val along = if (ahead.isNotEmpty()) weather.along(ahead) else null
                    _weather.value = along
                    val text = along?.takeIf { !it.dry }?.summary
                    if (text != null && text != spokenWeather) speaker.say("注意，$text${shelterAhead().orEmpty()}")
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
        _climate.value = null
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
        /** 下雨时前方这么远以内有地方躲才说 */
        const val SHELTER_MENTION_METERS = 1_000.0
        /** 准的定位在这么久以内，不准的不要 */
        const val KEEP_PRECISE_MS = 10_000L
        /** 骑到离途经点这么近就算经过了 */
        const val VIA_PASSED_METERS = 50.0
        /** 可能摔车后等这么久没回答就告诉队友 */
        const val CRASH_COUNTDOWN_MS = 30_000L
        /** 自动开始的记录停下来这么久就自动结束 */
        const val AUTO_STOP_MS = 10 * 60_000L
    }
}
