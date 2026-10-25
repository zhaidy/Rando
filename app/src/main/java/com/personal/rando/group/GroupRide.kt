package com.personal.rando.group

import com.personal.rando.i18n.tr
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.content.edit
import com.personal.rando.nav.Geo
import com.personal.rando.places.Hazard
import com.personal.rando.ride.Fix
import com.personal.rando.routing.LatLon
import com.personal.rando.ui.Format
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 正在一起骑：队伍码、连没连上、队友们 */
data class GroupState(
    val code: String,
    val connected: Boolean = false,
    val teammates: List<Teammate> = emptyList(),
)

/**
 * 一起骑：连上服务器的房间，隔几秒把自己的位置发出去，收到队友的位置、对讲语音。
 * 断线了自动重连；跟着进程走，锁屏也不断（在队里时前台服务一直开着，进程活着）
 */
class GroupRide(
    context: Context,
    private val scope: CoroutineScope,
    private val serverUrl: String = GroupConfig.SERVER_URL,
    // 10 秒一次心跳，到下一次还没回就当断了：信号没了 10–20 秒就发现，不用等到半分钟以后
    private val http: OkHttpClient = OkHttpClient.Builder().pingInterval(10, TimeUnit.SECONDS).build(),
) {
    private val prefs = context.applicationContext.getSharedPreferences("group", Context.MODE_PRIVATE)
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** 本机在队伍里的 id，装好就不变，重连时服务器认得是同一个人 */
    val myId: String = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also { prefs.edit { putString(KEY_ID, it) } }

    /** 上次用的名字 */
    var myName: String
        get() = prefs.getString(KEY_NAME, null).orEmpty()
        set(value) = prefs.edit { putString(KEY_NAME, value.trim()) }

    val configured: Boolean get() = serverUrl.isNotEmpty()

    private val _state = MutableStateFlow<GroupState?>(null)
    /** 不为 null 就是在队伍里 */
    val state: StateFlow<GroupState?> = _state

    private val _offer = MutableStateFlow<SharedRoute?>(null)
    /** 队友分享过来、还没回答的路线 */
    val offer: StateFlow<SharedRoute?> = _offer

    private val _sos = MutableStateFlow<List<SosAlert>>(emptyList())
    /** 可能摔车了的队友；对方点了「我没事」或者离队就撤掉 */
    val sos: StateFlow<List<SosAlert>> = _sos

    /** 我发出的求助还在：断线重连后再发一次 */
    private var mySos: LatLon? = null

    /** 我分享出去的路线；断线重连后再发一次 */
    private var myRoute: SharedRoute? = null
    /** 回答过的分享（队友 id 和时间），重连后再收到不再问 */
    private val answered = mutableSetOf<Pair<String, Long>>()

    private val _hazards = MutableSharedFlow<GroupEvent>(extraBufferCapacity = 16)
    /** 队友标的、删掉的路况（[GroupEvent.HazardAdded] / [GroupEvent.HazardRemoved]） */
    val hazards: SharedFlow<GroupEvent> = _hazards

    private val _voices = MutableSharedFlow<GroupEvent.Voice>(extraBufferCapacity = 64)
    /** 收到的对讲语音 */
    val voices: SharedFlow<GroupEvent.Voice> = _voices

    /** 离得近的队友直连（蓝牙、Wi-Fi），开着对讲时才开；只用来传对讲 */
    private val local = NearbyLink(context) { text ->
        (GroupProtocol.parse(text, System.currentTimeMillis()) as? GroupEvent.Voice)?.let { _voices.tryEmit(it) }
    }

    /** 跟队友手机 WebRTC 直连，开着对讲时才开；也只用来传对讲，信令借服务器的房间转 */
    private val rtc = RtcLink(
        context, scope,
        iceUrl = {
            _state.value?.code?.let { code ->
                "$serverUrl/ice".toHttpUrl().newBuilder().addQueryParameter("room", code).addQueryParameter("id", myId).build().toString()
            }
        },
        http = http,
        signal = { to, data -> socket?.send(GroupProtocol.rtc(to, data)) },
    ) { from, text ->
        // 只认对方自己的声音
        (GroupProtocol.parse(text, System.currentTimeMillis()) as? GroupEvent.Voice)?.takeIf { it.id == from }?.let { _voices.tryEmit(it) }
    }

    /** 直连上的队友 id（蓝牙、Wi-Fi 直连或者 WebRTC 直连） */
    val localPeers: StateFlow<Set<String>> = combine(local.peers, rtc.connected) { a, b -> a + b }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun hasLocalPermissions(): Boolean = local.hasPermissions()

    /** 开对讲时开直连；没有「附近的设备」权限、蓝牙关着就不开蓝牙 / Wi-Fi 直连，只走服务器和 WebRTC */
    fun startLocal() {
        val s = _state.value ?: return
        val team = s.teammates.map { it.id }.toSet()
        local.start(s.code, myId, myName)
        local.setTeam(team)
        rtc.setTeam(team)
        rtc.start(myId)
    }

    fun stopLocal() {
        local.stop()
        rtc.stop()
    }

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    /** 现在用的网络（Wi-Fi、流量）；换了网络旧连接多半已经死了，但要等心跳超时才知道 */
    private var network: Network? = null
    private var watchingNetwork = false
    private var lastSent: Fix? = null
    private var lastSentAt = 0L
    /** 掉队提醒：每个队友上次提醒的时间 */
    private val warnedAt = mutableMapOf<String, Long>()

    /** 附近一公里内、十分钟内有人报过位置的队伍，近的在前 */
    suspend fun nearby(at: LatLon): List<NearbyTeam> = withContext(Dispatchers.IO) {
        val url = "$serverUrl/nearby".toHttpUrl().newBuilder()
            .addQueryParameter("lat", String.format(Locale.US, "%.5f", at.lat))
            .addQueryParameter("lon", String.format(Locale.US, "%.5f", at.lon))
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException(tr("找附近的队伍 HTTP ${response.code}", "Finding nearby groups failed (HTTP ${response.code})"))
            NearbyTeam.parse(response.body.string())
        }
    }

    fun join(code: String, name: String) {
        // 开着对讲换队伍：leave 会关掉直连，换好后接着开
        val wasLocal = local.running || rtc.running
        leave()
        myName = name
        _state.value = GroupState(code)
        watchNetwork()
        connect(code)
        if (wasLocal) startLocal()
    }

    fun leave() {
        unwatchNetwork()
        reconnectJob?.cancel()
        // 先关直连：要趁连接还在把 bye 发给队友，不然对方要干等到超时
        local.stop()
        rtc.stop()
        socket?.close(1000, "bye")
        socket = null
        lastSent = null
        warnedAt.clear()
        myRoute = null
        mySos = null
        _sos.value = emptyList()
        answered.clear()
        _offer.value = null
        _state.value = null
    }

    /** 开始导航时把路线分享给队友；不在队伍里就不发 */
    fun shareRoute(route: SharedRoute) {
        if (_state.value == null) return
        myRoute = route
        socket?.send(GroupProtocol.route(route))
    }

    /** 结束导航：队友那边撤掉还没回答的提示 */
    fun clearRoute() {
        if (myRoute == null) return
        myRoute = null
        socket?.send(GroupProtocol.ROUTE_CLEAR)
    }

    /** 我可能摔车了：告诉队友在哪儿 */
    fun sendSos(at: LatLon) {
        mySos = at
        socket?.send(GroupProtocol.sos(at))
    }

    /** 标了一处路况，告诉队友 */
    fun shareHazard(h: Hazard) {
        socket?.send(GroupProtocol.hazard(h))
    }

    fun shareHazardRemoved(id: String) {
        socket?.send(GroupProtocol.hazardRemoved(id))
    }

    /** 我没事 */
    fun clearSos() {
        if (mySos == null) return
        mySos = null
        socket?.send(GroupProtocol.SOS_CLEAR)
    }

    /** 看到了，不用再提示这个队友的求助 */
    fun dismissSos(id: String) {
        _sos.update { list -> list.filterNot { it.id == id } }
    }

    /** 跟不跟着骑都回答过了，不再问 */
    fun answerOffer() {
        val r = _offer.value ?: return
        answered += r.id to r.ts
        _offer.value = null
    }

    /** 新定位：动了够远或者隔了够久才发，省流量也省服务器额度 */
    fun onFix(fix: Fix) {
        val ws = socket ?: return
        if (_state.value?.connected != true) return
        val now = System.currentTimeMillis()
        val last = lastSent
        val moved = last == null || Geo.distance(last.location, fix.location) >= SEND_METERS
        if (now - lastSentAt < MIN_INTERVAL_MS || (!moved && now - lastSentAt < MAX_INTERVAL_MS)) return
        if (ws.send(GroupProtocol.position(fix))) {
            lastSent = fix
            lastSentAt = now
        }
    }

    /**
     * 服务器、蓝牙 / Wi-Fi 直连、WebRTC 直连几条路都发：收的人谁先到用谁，重复的丢掉；
     * 直连连上、断开都不用切换，说到一半也不断。服务器那条一直发：直连断了要几秒才发现，这几秒靠它
     */
    fun sendVoice(stream: Int, seq: Int, end: Boolean, base64: String): Boolean {
        val text = GroupProtocol.localVoice(myId, myName, stream, seq, end, base64)
        val direct = local.send(text) or rtc.send(text)
        // 网络卡住时发不出去的会在 OkHttp 里排队，通了以后一下子全到，队友听到的是好几秒前的话，
        // 后面也一直慢那么多：排着的超过一秒就先不发这段（最后一段照发，队友那边好收尾）
        val ws = socket
        val server = ws != null && (end || ws.queueSize() < MAX_VOICE_QUEUE) && ws.send(GroupProtocol.voice(stream, seq, end, base64))
        return direct || server
    }

    /**
     * 掉队提醒：队友离我超过 [STRAGGLER_METERS]，每人每 [WARN_INTERVAL_MS] 最多说一次。
     * 返回要念的话
     */
    fun stragglerAlert(me: LatLon, now: Long = System.currentTimeMillis()): String? {
        val mates = _state.value?.teammates ?: return null
        for (m in mates) {
            val at = m.location ?: continue
            if (m.stale(now)) continue
            val d = Geo.distance(me, at)
            if (d > STRAGGLER_METERS && now - (warnedAt[m.id] ?: 0) > WARN_INTERVAL_MS) {
                warnedAt[m.id] = now
                return tr("${m.name}离你${Format.distance(d.toInt())}了", "${m.name} is ${Format.distance(d.toInt())} away from you")
            }
        }
        return null
    }

    private fun connect(code: String) {
        // OkHttp 的 WebSocket 地址写 https，连的时候自己换成 wss
        val url = serverUrl.toHttpUrl().newBuilder()
            .addPathSegment("room").addPathSegment(code)
            .addQueryParameter("id", myId)
            .addQueryParameter("name", myName)
            .build()
        val request = Request.Builder().url(url).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    if (socket !== webSocket) return@launch
                    // 连上了：下次断了从 1 秒重新退避，不要接着上次的半分钟
                    reconnectAttempt = 0
                    lastSent = null
                    _state.update { it?.copy(connected = true) }
                    myRoute?.let { webSocket.send(GroupProtocol.route(it)) }
                    mySos?.let { webSocket.send(GroupProtocol.sos(it)) }
                    // 断线的时候可能有人开了对讲，没打通的再打（已经打通的不受服务器断线影响）
                    rtc.hello()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = GroupProtocol.parse(text, System.currentTimeMillis()) ?: return
                scope.launch {
                    if (socket !== webSocket) return@launch
                    apply(event)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "socket failed", t)
                dropped(webSocket)
            }
        })
    }

    /** 连续连不上几次了；连上就清零 */
    private var reconnectAttempt = 0

    private fun dropped(webSocket: WebSocket) {
        scope.launch {
            if (socket !== webSocket) return@launch
            val state = _state.value ?: return@launch
            socket = null
            _state.update { it?.copy(connected = false) }
            // 没信号、换基站时会断；退避着重连，最长半分钟一次（网络回来了会马上连，见 watchNetwork）
            val attempt = reconnectAttempt++
            reconnectJob = scope.launch {
                delay(minOf(30_000L, 1_000L shl minOf(attempt, 5)))
                if (_state.value?.code == state.code) connect(state.code)
            }
        }
    }

    /** 马上重连：换了网络、网络回来了，不等心跳超时、不等退避 */
    private fun reconnectNow() {
        val state = _state.value ?: return
        reconnectJob?.cancel()
        socket?.let { old ->
            socket = null
            old.cancel()
        }
        _state.update { it?.copy(connected = false) }
        reconnectAttempt = 0
        connect(state.code)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(net: Network) {
            scope.launch {
                if (!watchingNetwork) return@launch
                val before = network
                network = net
                // 注册时会先报一次现在的网络，那次不用重连；之后换了网络，或者断着的时候网络回来了，马上连
                if ((before != null && before != net) || socket == null) reconnectNow()
            }
        }
    }

    private fun watchNetwork() {
        if (watchingNetwork) return
        network = connectivity?.activeNetwork
        watchingNetwork = runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }.isSuccess && connectivity != null
    }

    private fun unwatchNetwork() {
        if (!watchingNetwork) return
        watchingNetwork = false
        network = null
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
    }

    private fun apply(event: GroupEvent) {
        when (event) {
            is GroupEvent.Roster -> _state.update { it?.copy(teammates = event.members) }
            is GroupEvent.Joined -> _state.update { s ->
                s?.copy(teammates = s.teammates.filterNot { it.id == event.id } + Teammate(event.id, event.name))
            }
            is GroupEvent.Left -> {
                _state.update { s -> s?.copy(teammates = s.teammates.filterNot { it.id == event.id }) }
                if (_offer.value?.id == event.id) _offer.value = null
                _sos.update { list -> list.filterNot { it.id == event.id } }
            }
            is GroupEvent.Sos -> if (event.alert.id != myId) {
                _sos.update { list -> list.filterNot { it.id == event.alert.id } + event.alert }
            }
            is GroupEvent.SosCleared -> _sos.update { list -> list.filterNot { it.id == event.id } }
            is GroupEvent.HazardAdded, is GroupEvent.HazardRemoved -> _hazards.tryEmit(event)
            is GroupEvent.Route -> if (event.route.id != myId && (event.route.id to event.route.ts) !in answered) _offer.value = event.route
            is GroupEvent.RouteCleared -> if (_offer.value?.id == event.id) _offer.value = null
            is GroupEvent.Position -> _state.update { s ->
                s?.copy(teammates = s.teammates.filterNot { it.id == event.mate.id } + event.mate)
            }
            is GroupEvent.Voice -> _voices.tryEmit(event)
            is GroupEvent.Rtc -> rtc.onSignal(event.id, event.data)
        }
        if (event is GroupEvent.Roster || event is GroupEvent.Joined || event is GroupEvent.Left) {
            _state.value?.let { s ->
                val team = s.teammates.map { it.id }.toSet()
                local.setTeam(team)
                rtc.setTeam(team)
            }
        }
    }

    companion object {
        private const val TAG = "GroupRide"
        /** OkHttp 里排着没发出去的超过这么多字节（大约一秒的对讲）就先不发对讲 */
        private const val MAX_VOICE_QUEUE = 4 * 1024L
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        const val SEND_METERS = 15.0
        const val MIN_INTERVAL_MS = 3_000L
        const val MAX_INTERVAL_MS = 15_000L
        const val STRAGGLER_METERS = 1_000.0
        const val WARN_INTERVAL_MS = 5 * 60_000L
    }
}
