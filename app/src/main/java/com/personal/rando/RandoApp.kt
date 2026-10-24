package com.personal.rando

import android.app.Application
import android.content.Context
import com.personal.rando.places.SavedPlaceStore
import com.personal.rando.ride.RideSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RandoApp : Application() {
    /** 跟着进程走的协程；导航、记录在界面关掉后也要继续 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val session: RideSession by lazy { RideSession(this, appScope) }

    val savedPlaces: SavedPlaceStore by lazy { SavedPlaceStore(this) }
}

val Context.rando: RandoApp get() = applicationContext as RandoApp
