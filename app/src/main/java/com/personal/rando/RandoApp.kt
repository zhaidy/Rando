package com.personal.rando

import android.app.Application
import android.content.Context
import com.personal.rando.places.SavedPlaceStore
import com.personal.rando.ride.RideSession
import com.personal.rando.group.GroupRide
import com.personal.rando.group.Walkie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RandoApp : Application() {
    /** 跟着进程走的协程；导航、记录在界面关掉后也要继续 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 一起骑；位置由 [session] 收到定位时顺便发出去 */
    val group: GroupRide by lazy { GroupRide(this, appScope) }

    val session: RideSession by lazy { RideSession(this, appScope, group = group) }

    /** 一起骑时的声控对讲 */
    val walkie: Walkie by lazy { Walkie(this, appScope, group) { session.speaking } }

    val savedPlaces: SavedPlaceStore by lazy { SavedPlaceStore(this) }
}

val Context.rando: RandoApp get() = applicationContext as RandoApp
