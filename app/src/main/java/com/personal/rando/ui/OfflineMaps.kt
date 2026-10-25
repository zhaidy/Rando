package com.personal.rando.ui

import android.content.Context
import com.personal.rando.i18n.tr
import com.personal.rando.search.PlaceSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** 离线地图的情况：下好了没有、下了多少 */
data class OfflineMapState(
    /** 下好的时间；没下是 null */
    val downloadedAt: Long? = null,
    val bytes: Long = 0,
    /** 正在下：0..1；没在下是 null */
    val progress: Float? = null,
    val error: String? = null,
)

/**
 * 整个新加坡的地图存到手机里（OpenFreeMap 的矢量瓦片，0–14 级，再放大用 14 级的放大画）。
 * 规划和骑行视图两套样式用的是同一个瓦片源，下一份两边都能用；没信号的地方地图也不会空白
 */
class OfflineMaps(context: Context) {

    private val app = context.applicationContext
    private val manager by lazy {
        MapLibre.getInstance(app)
        OfflineManager.getInstance(app)
    }
    private val _state = MutableStateFlow(OfflineMapState())
    val state: StateFlow<OfflineMapState> = _state
    private var region: OfflineRegion? = null

    /** 看看手机里已经有没有 */
    fun refresh() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val r = offlineRegions?.firstOrNull { name(it) == NAME } ?: run {
                    _state.update { it.copy(downloadedAt = null, bytes = 0) }
                    return
                }
                region = r
                r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                    override fun onStatus(status: OfflineRegionStatus?) {
                        val done = status?.isComplete == true
                        _state.update {
                            it.copy(downloadedAt = if (done) savedAt(r) else null, bytes = status?.completedResourceSize ?: 0)
                        }
                    }

                    override fun onError(error: String?) = Unit
                })
            }

            override fun onError(error: String) = Unit
        })
    }

    /** 下载（已经有就重新下，拿最新的地图） */
    fun download() {
        if (_state.value.progress != null) return
        _state.update { it.copy(progress = 0f, error = null) }
        deleteThen {
            val b = PlaceSearch.SINGAPORE_BBOX.split(",").map { it.toDouble() }
            val bounds = LatLngBounds.Builder().include(LatLng(b[1], b[0])).include(LatLng(b[3], b[2])).build()
            val definition = OfflineTilePyramidRegionDefinition(MapLayers.STYLE_URL, bounds, 0.0, MAX_ZOOM, app.resources.displayMetrics.density)
            val metadata = JSONObject().put("name", NAME).put("at", System.currentTimeMillis()).toString().toByteArray()
            manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    region = offlineRegion
                    offlineRegion.setObserver(observer(offlineRegion))
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    _state.update { it.copy(progress = null, error = error) }
                }
            })
        }
    }

    fun cancel() {
        region?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        delete()
    }

    fun delete() = deleteThen { _state.value = OfflineMapState() }

    private fun observer(r: OfflineRegion) = object : OfflineRegion.OfflineRegionObserver {
        override fun onStatusChanged(status: OfflineRegionStatus) {
            val total = status.requiredResourceCount.coerceAtLeast(1)
            if (status.isComplete) {
                r.setDownloadState(OfflineRegion.STATE_INACTIVE)
                _state.value = OfflineMapState(downloadedAt = savedAt(r), bytes = status.completedResourceSize)
            } else {
                _state.update { it.copy(progress = status.completedResourceCount.toFloat() / total, bytes = status.completedResourceSize) }
            }
        }

        override fun onError(error: OfflineRegionError) {
            // 单个瓦片出错 MapLibre 会自己重试；只记下来给人看
            _state.update { it.copy(error = error.message) }
        }

        override fun mapboxTileCountLimitExceeded(limit: Long) {
            r.setDownloadState(OfflineRegion.STATE_INACTIVE)
            _state.update { it.copy(progress = null, error = tr("瓦片太多（上限 $limit）", "Too many tiles (limit $limit)")) }
        }
    }

    /** 删掉旧的再做 [then] */
    private fun deleteThen(then: () -> Unit) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val old = offlineRegions?.filter { name(it) == NAME }.orEmpty()
                if (old.isEmpty()) return then()
                var left = old.size
                old.forEach { r ->
                    r.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            if (--left == 0) then()
                        }

                        override fun onError(error: String) {
                            if (--left == 0) then()
                        }
                    })
                }
                region = null
            }

            override fun onError(error: String) = then()
        })
    }

    private fun name(r: OfflineRegion): String? = runCatching { JSONObject(String(r.metadata)).optString("name") }.getOrNull()
    private fun savedAt(r: OfflineRegion): Long? = runCatching { JSONObject(String(r.metadata)).optLong("at") }.getOrNull()

    private companion object {
        const val NAME = "singapore"
        /** OpenFreeMap 的矢量瓦片最大 14 级，再放大是把 14 级的放大画 */
        const val MAX_ZOOM = 14.0
    }
}
