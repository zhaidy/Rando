package com.personal.rando.group

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 离得近的队友直连：蓝牙、Wi-Fi 直接连手机（谷歌的 Nearby Connections），不走服务器。
 * 骑在一起的时候（几十米内）延迟更低，没信号的地方（隧道、公园深处）也听得到。
 *
 * 对讲两条路一起发：服务器那条一直发，直连上的队友再多发一份；收的人谁先到用谁，
 * 后到的那份按句子编号、段号丢掉（[VoiceDedup]）。所以连上、断开直连都不用切换，说到一半也不断
 *
 * 广播的名字是「队伍码|id|名字」，只连同一个队伍码的；两边都看到对方时 id 小的那边去连，免得撞车。
 * 队里的人都直连上了就停止搜（搜很费电，也会拖慢已有的连接），有人断了再搜
 */
class NearbyLink(context: Context, private val onMessage: (String) -> Unit) {
    private val app = context.applicationContext
    private val client by lazy { Nearby.getConnectionsClient(app) }

    private var code: String? = null
    private var myId = ""
    private var myName = ""
    private var discovering = false
    /** 应该连上的队友 id（服务器的名单）；不知道的时候是 null，一直搜 */
    private var wanted: Set<String>? = null

    /** endpointId → 队友 id */
    private val connected = mutableMapOf<String, String>()
    /** 正在连、正在等对方同意的 endpoint */
    private val pending = mutableSetOf<String>()

    /** 发给谁：对讲在别的线程发，用一份拷贝 */
    @Volatile private var targets: List<String> = emptyList()

    private val _peers = MutableStateFlow<Set<String>>(emptySet())
    /** 直连上的队友 id */
    val peers: StateFlow<Set<String>> = _peers

    val running: Boolean get() = code != null

    fun hasPermissions(): Boolean = PERMISSIONS.all { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }

    fun start(code: String, myId: String, myName: String) {
        if (this.code == code) return
        stop()
        if (!hasPermissions()) return
        this.code = code
        this.myId = myId
        this.myName = myName
        val name = endpointName(code, myId, myName)
        client.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { Log.w(TAG, "advertise failed", it) }
        updateDiscovery()
    }

    fun stop() {
        if (code == null) return
        code = null
        runCatching {
            client.stopAdvertising()
            client.stopDiscovery()
            client.stopAllEndpoints()
        }
        discovering = false
        connected.clear()
        pending.clear()
        names.clear()
        targets = emptyList()
        _peers.value = emptySet()
    }

    /** 队里现在有谁（不算自己）：都直连上了就不搜了 */
    fun setTeam(ids: Set<String>) {
        wanted = ids
        updateDiscovery()
    }

    /** 发给所有直连上的队友 */
    fun send(text: String): Boolean {
        val to = targets
        if (to.isEmpty()) return false
        client.sendPayload(to, Payload.fromBytes(text.toByteArray()))
        return true
    }

    private fun updateDiscovery() {
        if (code == null) return
        val want = wanted
        val need = want == null || want.isEmpty() || !connected.values.containsAll(want)
        if (need && !discovering) {
            discovering = true
            client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
                .addOnFailureListener { e ->
                    Log.w(TAG, "discovery failed", e)
                    discovering = false
                }
        } else if (!need && discovering) {
            discovering = false
            client.stopDiscovery()
        }
    }

    private fun publish() {
        targets = connected.keys.toList()
        _peers.value = connected.values.toSet()
        updateDiscovery()
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val peer = parseEndpointName(info.endpointName) ?: return
            if (peer.code != code || peer.id == myId) return
            if (endpointId in connected || endpointId in pending || peer.id in connected.values) return
            // 两边都搜到对方时只让一边去连
            if (myId > peer.id) return
            pending += endpointId
            client.requestConnection(endpointName(code.orEmpty(), myId, myName), endpointId, lifecycle)
                .addOnFailureListener { e ->
                    Log.w(TAG, "connect failed", e)
                    pending -= endpointId
                }
        }

        override fun onEndpointLost(endpointId: String) {}
    }

    /** 连上的时候记下是哪个队友 */
    private val names = mutableMapOf<String, String>()

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val peer = parseEndpointName(info.endpointName)
            if (peer == null || peer.code != code || peer.id == myId) {
                client.rejectConnection(endpointId)
                return
            }
            pending += endpointId
            names[endpointId] = peer.id
            client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pending -= endpointId
            val id = names.remove(endpointId)
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK && id != null) {
                connected[endpointId] = id
                Log.i(TAG, "connected to $id")
                publish()
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)?.let { Log.i(TAG, "disconnected from $it") }
            publish()
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (endpointId !in connected) return
            payload.asBytes()?.let { onMessage(String(it)) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    data class Peer(val code: String, val id: String, val name: String)

    companion object {
        private const val TAG = "NearbyLink"
        private const val SERVICE_ID = "com.personal.rando.walkie"
        /** 每个人都可以连好几个人，几个人互相连成一团 */
        private val STRATEGY = Strategy.P2P_CLUSTER

        /** 要的权限：Android 12 起是「附近的设备」，之前是定位 */
        val PERMISSIONS: List<String> = when {
            Build.VERSION.SDK_INT >= 33 -> listOf(
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.NEARBY_WIFI_DEVICES,
            )
            Build.VERSION.SDK_INT >= 31 -> listOf(
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION,
            )
            else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        fun endpointName(code: String, id: String, name: String): String = "$code|$id|$name"

        fun parseEndpointName(s: String): Peer? {
            val parts = s.split('|', limit = 3)
            if (parts.size < 3 || parts[0].isEmpty() || parts[1].isEmpty()) return null
            return Peer(parts[0], parts[1], parts[2])
        }
    }
}

/**
 * 同一段语音会从服务器、直连各来一份：记住最近收过的（谁、哪句、第几段），重复的丢掉。
 * 也记着每句话放到第几段了，晚到的旧段（另一条路慢了一拍）也丢掉，不倒回去放
 */
class VoiceDedup(private val capacity: Int = 256) {
    private val latest = object : LinkedHashMap<Pair<String, Int>, Int>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, Int>?) = size > capacity
    }

    /** 这段没收过、也不比收过的旧，要放就返回 true */
    fun accept(id: String, stream: Int, seq: Int): Boolean {
        val key = id to stream
        val last = latest[key]
        if (last != null && seq <= last) return false
        latest[key] = seq
        return true
    }
}
