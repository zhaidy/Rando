package com.personal.rando.ride

import android.content.Context
import com.personal.rando.nav.NavProgress
import com.personal.rando.nav.Navigator
import com.personal.rando.nav.Speaker
import com.personal.rando.nav.VoicePrompter
import com.personal.rando.routing.BRouterClient
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Route
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
    val profile: BikeProfile,
    /** 还没定位到时是 null */
    val progress: NavProgress? = null,
    val rerouting: Boolean = false,
)

/**
 * 一次骑行：导航状态和语音都放在这里，跟着进程而不是界面走。
 * 锁屏、切到别的 App 时由 [RideService] 继续喂定位进来。
 */
class RideSession(
    context: Context,
    private val scope: CoroutineScope,
    private val router: BRouterClient = BRouterClient(),
) {
    private val app = context.applicationContext
    private val speaker by lazy { Speaker(app) }

    private val _location = MutableStateFlow<LatLon?>(null)
    /** 最近一次定位，规划路线时当起点 */
    val location: StateFlow<LatLon?> = _location

    private val _navigation = MutableStateFlow<NavigationState?>(null)
    /** 不为 null 就是在导航中 */
    val navigation: StateFlow<NavigationState?> = _navigation

    private var navigator: Navigator? = null
    private val prompter = VoicePrompter()
    private var rerouteJob: Job? = null
    private var lastRerouteAt = 0L

    fun onFix(fix: Fix) {
        _location.value = fix.location
        // 网络定位误差上百米，导航时会被当成骑偏了
        val precise = fix.accuracy == null || fix.accuracy <= NAV_MAX_ACCURACY_METERS
        if (navigator != null && precise) navigate(fix.location)
    }

    fun startNavigation(route: Route, destination: LatLon, profile: BikeProfile) {
        navigator = Navigator(route)
        prompter.reset()
        rerouteJob?.cancel()
        _navigation.value = NavigationState(route, destination, profile)
        speaker.say(prompter.start(route.distanceMeters.toDouble()))
        RideService.start(app)
    }

    /** 停掉导航；RideService 看到没事可做了会自己退出 */
    fun stopNavigation() {
        navigator = null
        rerouteJob?.cancel()
        _navigation.value = null
    }

    private fun navigate(point: LatLon) {
        val nav = navigator ?: return
        val progress = nav.update(point)
        _navigation.update { it?.copy(progress = progress) }
        prompter.next(progress)?.let(speaker::say)
        if (progress.offRoute) reroute(point)
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
                val route = router.route(from, state.destination, state.profile)
                if (navigator == null) return@launch
                navigator = Navigator(route)
                prompter.reset()
                _navigation.update { it?.copy(route = route, rerouting = false) }
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
        /** 导航时精度比这差的定位不要 */
        const val NAV_MAX_ACCURACY_METERS = 50f
    }
}
