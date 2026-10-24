package com.personal.rando.places

import android.content.Context
import androidx.core.content.edit
import com.personal.rando.routing.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** 车停在哪儿：结束骑行时的位置，可以再拍一张照片 */
data class ParkedBike(val location: LatLon, val at: Long, val photo: String? = null)

/** 只记最近一次停车；取车后清掉 */
class ParkingStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("parking", Context.MODE_PRIVATE)
    private val _bike = MutableStateFlow(load())
    val bike: StateFlow<ParkedBike?> = _bike

    /** 拍车位照片存到这里（每次覆盖） */
    val photoFile: File get() = File(app.filesDir, "parking").apply { mkdirs() }.let { File(it, "bike.jpg") }

    fun park(at: LatLon, now: Long = System.currentTimeMillis()) {
        // 新停的车，旧照片不算了
        photoFile.delete()
        write(ParkedBike(at, now))
    }

    fun photoTaken() {
        val b = _bike.value ?: return
        if (photoFile.exists()) write(b.copy(photo = photoFile.absolutePath))
    }

    fun clear() {
        photoFile.delete()
        _bike.value = null
        prefs.edit { clear() }
    }

    private fun write(b: ParkedBike) {
        _bike.value = b
        prefs.edit {
            putFloat(KEY_LAT, b.location.lat.toFloat())
            putFloat(KEY_LON, b.location.lon.toFloat())
            putLong(KEY_AT, b.at)
            putString(KEY_PHOTO, b.photo)
        }
    }

    private fun load(): ParkedBike? {
        if (!prefs.contains(KEY_AT)) return null
        return ParkedBike(
            LatLon(prefs.getFloat(KEY_LAT, 0f).toDouble(), prefs.getFloat(KEY_LON, 0f).toDouble()),
            prefs.getLong(KEY_AT, 0),
            prefs.getString(KEY_PHOTO, null)?.takeIf { File(it).exists() },
        )
    }

    private companion object {
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_AT = "at"
        const val KEY_PHOTO = "photo"
    }
}
