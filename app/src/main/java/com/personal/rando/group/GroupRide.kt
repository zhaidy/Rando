package com.personal.rando.group

import android.content.Context
import androidx.core.content.edit
import com.personal.rando.nav.Geo
import com.personal.rando.places.Hazard
import com.personal.rando.ride.Fix
import com.personal.rando.routing.LatLon
import com.personal.rando.ui.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
 * 断线了自动重连；跟着进程走，锁屏也不断（骑行记录的前台服务让进程活着）
 */
class GroupRide(
    context: Context,
    private val scope: CoroutineScope,
    private val serverUrl: String = GroupConfig.SERVER_URL,
    private val http: OkHttpClient = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build(),
) {
    private val prefs = context.applicationContext.getSharedPreferences("group", Context.MODE_PRIVATE)

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

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var lastSent: Fix? = null
    private var lastSentAt = 0L
    /** 掉队提醒：每个队友上次提醒的时间 */
    private val warnedAt = mutableMapOf<String, Long>()

    fun join(code: String, name: String) {
        leave()
        myName = name
        _state.value = GroupState(code)
        connect(code, attempt = 0)
    }

    fun leave() {
        reconnectJob?.cancel()
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

    fun sendVoice(stream: Int, seq: Int, end: Boolean, base64: String): Boolean =
        socket?.send(GroupProtocol.voice(stream, seq, end, base64)) == true

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
                return "${m.name}离你${Format.distance(d.toInt())}了"
            }
        }
        return null
    }

    private fun connect(code: String, attempt: Int) {
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
                    lastSent = null
                    _state.update { it?.copy(connected = true) }
                    myRoute?.let { webSocket.send(GroupProtocol.route(it)) }
                    mySos?.let { webSocket.send(GroupProtocol.sos(it)) }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = GroupProtocol.parse(text, System.currentTimeMillis()) ?: return
                scope.launch {
                    if (socket !== webSocket) return@launch
                    apply(event)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket, attempt)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dropped(webSocket, attempt)
        })
    }

    private fun dropped(webSocket: WebSocket, attempt: Int) {
        scope.launch {
            if (socket !== webSocket) return@launch
            val state = _state.value ?: return@launch
            socket = null
            _state.update { it?.copy(connected = false) }
            // 没信号、换基站时会断；退避着重连，最长半分钟一次
            reconnectJob = scope.launch {
                delay(minOf(30_000L, 1_000L shl minOf(attempt, 5)))
                if (_state.value?.code == state.code) connect(state.code, attempt + 1)
            }
        }
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
        }
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        const val SEND_METERS = 15.0
        const val MIN_INTERVAL_MS = 3_000L
        const val MAX_INTERVAL_MS = 15_000L
        const val STRAGGLER_METERS = 1_000.0
        const val WARN_INTERVAL_MS = 5 * 60_000L
    }
}
