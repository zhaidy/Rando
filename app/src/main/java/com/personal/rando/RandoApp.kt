package com.personal.rando

import android.app.Application
import android.content.Context
import com.personal.rando.places.HazardStore
import com.personal.rando.places.ParkingStore
import com.personal.rando.places.SavedPlaceStore
import com.personal.rando.places.SavedRouteStore
import com.personal.rando.routing.BRouterClient
import com.personal.rando.ride.RideSession
import com.personal.rando.ride.SettingsStore
import com.personal.rando.group.GroupRide
import com.personal.rando.group.Walkie
import com.personal.rando.voice.VoiceControl
import com.personal.rando.ui.OfflineMaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RandoApp : Application() {
    /** 跟着进程走的协程；导航、记录在界面关掉后也要继续 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 一起骑；位置由 [session] 收到定位时顺便发出去 */
    val group: GroupRide by lazy { GroupRide(this, appScope) }

    /** 标过的路况；施工、封路的地方算路时绕开 */
    val hazards: HazardStore by lazy { HazardStore(this) }

    /** 规划和导航共用：算路时带上要绕开的路况 */
    val router: BRouterClient by lazy { BRouterClient(avoid = { hazards.avoidPoints() }) }

    val session: RideSession by lazy { RideSession(this, appScope, router = router, group = group, hazards = hazards, parking = parking) }

    /** 一起骑时的声控对讲 */
    val walkie: Walkie by lazy { Walkie(this, appScope, group) }

    /** 语音指令：点麦克风或者按耳机键说话 */
    val voice: VoiceControl by lazy { VoiceControl(this, walkie).also { it.say = session::say } }

    val savedPlaces: SavedPlaceStore by lazy { SavedPlaceStore(this) }

    /** 车停在哪儿 */
    val parking: ParkingStore by lazy { ParkingStore(this) }

    /** 体重、自动开始记录 */
    val settings: SettingsStore by lazy { SettingsStore(this) }

    val savedRoutes: SavedRouteStore by lazy { SavedRouteStore(this) }

    /** 存在手机里的新加坡地图 */
    val offlineMaps: OfflineMaps by lazy { OfflineMaps(this) }
}

val Context.rando: RandoApp get() = applicationContext as RandoApp
