package com.personal.rando

import android.app.Application
import android.content.Context
import com.personal.rando.i18n.Lang
import com.personal.rando.places.HazardStore
import com.personal.rando.places.ParkingStore
import com.personal.rando.places.MrtData
import com.personal.rando.places.MrtNetwork
import com.personal.rando.places.SavedPlaceStore
import com.personal.rando.places.SavedRouteStore
import com.personal.rando.routing.BRouterClient
import com.personal.rando.places.PcnData
import com.personal.rando.places.PcnLoop
import com.personal.rando.routing.RoadCorrectionStore
import com.personal.rando.ride.RideSession
import com.personal.rando.ride.SettingsStore
import com.personal.rando.group.GroupRide
import com.personal.rando.group.Walkie
import com.personal.rando.voice.VoiceControl
import com.personal.rando.ui.OfflineMaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RandoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Lang.apply(settings.settings.value.language)
    }

    /** 跟着进程走的协程；导航、记录在界面关掉后也要继续 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 一起骑；位置由 [session] 收到定位时顺便发出去 */
    val group: GroupRide by lazy { GroupRide(this, appScope) }

    /** 标过的路况；施工、封路的地方算路时绕开 */
    val hazards: HazardStore by lazy { HazardStore(this) }

    /** 规划和导航共用：算路时带上要绕开的路况 */
    val router: BRouterClient by lazy {
        BRouterClient(avoid = { hazards.avoidPoints() }, corrections = { roadCorrections.all.value })
    }

    /** 标过「其实是马路」的地方 */
    val roadCorrections: RoadCorrectionStore by lazy { RoadCorrectionStore(this) }

    val session: RideSession by lazy { RideSession(this, appScope, router = router, group = group, hazards = hazards, parking = parking, voice = { settings.settings.value.voice }) }

    /** 一起骑时的声控对讲；开着的时候导航播报跟着走通话线路，不然队友说话时听不到导航 */
    val walkie: Walkie by lazy {
        Walkie(this, appScope, group).also { w ->
            appScope.launch { w.state.map { it.on }.distinctUntilChanged().collect(session::useCallAudio) }
        }
    }

    /** 语音指令：点麦克风或者按耳机键说话 */
    val voice: VoiceControl by lazy { VoiceControl(this, walkie).also { it.say = session::say } }

    val savedPlaces: SavedPlaceStore by lazy { SavedPlaceStore(this) }

    /** 公园连道的 6 个大环（数据在 assets/pcn.json） */
    val pcn: List<PcnLoop> by lazy { PcnData.parse(assets.open("pcn.json").bufferedReader().use { it.readText() }) }

    /** 新加坡 MRT、轻轨站（数据在 assets/mrt.json） */
    val mrt: MrtNetwork by lazy { MrtNetwork(MrtData.parse(assets.open("mrt.json").bufferedReader().use { it.readText() })) }

    /** 车停在哪儿 */
    val parking: ParkingStore by lazy { ParkingStore(this) }

    /** 体重、播报声音等设置 */
    val settings: SettingsStore by lazy { SettingsStore(this) }

    val savedRoutes: SavedRouteStore by lazy { SavedRouteStore(this) }

    /** 存在手机里的新加坡地图 */
    val offlineMaps: OfflineMaps by lazy { OfflineMaps(this) }
}

val Context.rando: RandoApp get() = applicationContext as RandoApp
