package com.personal.rando.group

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

/**
 * 跟队友手机之间的 WebRTC 直连：只开一条数据通道传对讲（不重传、不排队，像 UDP），
 * 不用 WebRTC 自己的录音、放音。对讲还是 [Walkie] 自己录、自己压 AAC、自己放；
 * 跟 [NearbyLink] 一样只是多一条路，收的人谁先到用谁（[VoiceDedup]）。
 *
 * 能打通就手机直接到手机，不绕服务器；丢一个包也不会卡住后面的（服务器那条是 TCP，丢了要等重传）。
 * 服务器断一下也不影响已经打通的直连。
 *
 * 打通前要交换 offer、answer 和各自的网络地址（ICE 候选），借服务器的房间转（[GroupProtocol.rtc]）。
 * 两人之间只让 id 小的那边发 offer，免得两边同时发撞车：开对讲时发一声 hi（fresh）给大家，
 * id 大的收到 hi 回一声 hi，id 小的收到 hi 就发 offer。
 * 两边都在运营商的内网里时要靠 TURN 中转，服务器配了才有（/ice）；打不通就还是走服务器
 */
class RtcLink(
    context: Context,
    private val scope: CoroutineScope,
    /** 拿 STUN / TURN 的地址；要带上队伍码和自己的 id，服务器只给队里的人 */
    private val iceUrl: () -> String?,
    private val http: OkHttpClient,
    /** 发信令：[to] 是 null 就发给所有人 */
    private val signal: (to: String?, data: JSONObject) -> Unit,
    /** 收到队友 [from] 发来的一条（在 WebRTC 的线程上调） */
    private val onMessage: (from: String, text: String) -> Unit,
) {
    private val app = context.applicationContext

    /** 原生库加载不了（不是 arm64 的手机）就是 null，不开直连 */
    private val factory: PeerConnectionFactory? by lazy {
        runCatching {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
            PeerConnectionFactory.builder().createPeerConnectionFactory()
        }.onFailure { Log.w(TAG, "webrtc unavailable", it) }.getOrNull()
    }

    private var myId = ""
    @Volatile var running = false
        private set
    private var iceServers: List<IceSpec> = DEFAULT_ICE
    private var startJob: Job? = null
    /** 应该连上的队友 id；不知道的时候是 null */
    private var team: Set<String>? = null

    private val peers = mutableMapOf<String, Peer>()
    /** 每个队友连续打不通几次了，退避着再试 */
    private val failures = mutableMapOf<String, Int>()
    private val retries = mutableMapOf<String, Job>()

    /** 对讲在别的线程发；关通道也在这把锁里，免得发到一半通道被释放 */
    private val lock = Any()
    @Volatile private var open: List<DataChannel> = emptyList()

    private val _peers = MutableStateFlow<Set<String>>(emptySet())
    /** 直连通道开着的队友 id */
    val connected: StateFlow<Set<String>> = _peers

    fun start(myId: String) {
        if (running) return
        running = true
        this.myId = myId
        startJob = scope.launch {
            // 第一次要加载十几 MB 的原生库，不放在主线程
            if (withContext(Dispatchers.IO) { factory } == null) {
                running = false
                return@launch
            }
            iceServers = fetchIce() ?: DEFAULT_ICE
            if (running) signal(null, JSONObject().put("k", "hi").put("fresh", true))
        }
    }

    fun stop() {
        if (!running) return
        running = false
        startJob?.cancel()
        retries.values.forEach { it.cancel() }
        retries.clear()
        failures.clear()
        signal(null, JSONObject().put("k", "bye"))
        peers.keys.toList().forEach { close(it) }
        team = null
    }

    /** 服务器重连上了：可能有人在断线的时候开了对讲，没打通的再打一遍 */
    fun hello() {
        if (!running) return
        scope.launch {
            // 开对讲时可能还没连上服务器，/ice 只给连在房间里的人：连上了再拿一次，免得只有 STUN
            fetchIce()?.let { iceServers = it }
            if (running) signal(null, JSONObject().put("k", "hi"))
        }
    }

    /** 队里现在有谁（不算自己）：离队的关掉 */
    fun setTeam(ids: Set<String>) {
        team = ids
        peers.keys.filter { it !in ids }.forEach { close(it) }
    }

    /** 发给所有直连上的队友；一个都没有返回 false */
    fun send(text: String): Boolean {
        val bytes = text.toByteArray()
        synchronized(lock) {
            var sent = false
            for (c in open) {
                // 发不出去越攒越多（路不通了还没发现）：旧的没意义，不再往里塞
                if (c.bufferedAmount() > MAX_BUFFERED) continue
                sent = runCatching { c.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false)) }.getOrDefault(false) || sent
            }
            return sent
        }
    }

    fun onSignal(from: String, data: JSONObject) {
        if (!running || from == myId) return
        if (team?.contains(from) == false) return
        when (data.optString("k")) {
            "hi" -> {
                // 对方刚开对讲：手上那条多半是旧的（对方没说 bye 就走了），重新来
                if (data.optBoolean("fresh")) close(from)
                // 已经通了就不用再来；没通的（offer、answer 半路丢了，卡着不会超时）重新打一遍
                if (from in _peers.value) return
                close(from)
                if (offers(myId, from)) offer(from) else signal(from, JSONObject().put("k", "hi"))
            }
            "offer" -> answer(from, data.optString("sdp"))
            "answer" -> peers[from]?.let { p ->
                // 设好了才能加对方的地址，先到的攒着
                p.pc.setRemoteDescription(Sdp(onSet = {
                    scope.launch { if (peers[from] === p) p.remoteSet() }
                }), SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp")))
            }
            "ice" -> peers[from]?.addIce(IceCandidate(data.optString("mid"), data.optInt("index"), data.optString("c")))
            "bye" -> close(from)
        }
    }

    private fun offer(to: String) {
        val p = newPeer(to) ?: return
        val init = DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0
        }
        p.attach(p.pc.createDataChannel(CHANNEL, init))
        // 回调在 WebRTC 自己的线程上：回到主线程、确认这条还没被关掉（dispose 过再用会崩）再设
        p.pc.createOffer(Sdp { desc ->
            scope.launch {
                if (peers[to] !== p) return@launch
                p.pc.setLocalDescription(Sdp(), desc)
                signal(to, JSONObject().put("k", "offer").put("sdp", desc.description))
            }
        }, MediaConstraints())
    }

    private fun answer(to: String, sdp: String) {
        close(to)
        val p = newPeer(to) ?: return
        p.pc.setRemoteDescription(Sdp(onSet = {
            scope.launch {
                if (peers[to] !== p) return@launch
                p.remoteSet()
                p.pc.createAnswer(Sdp { desc ->
                    scope.launch {
                        if (peers[to] !== p) return@launch
                        p.pc.setLocalDescription(Sdp(), desc)
                        signal(to, JSONObject().put("k", "answer").put("sdp", desc.description))
                    }
                }, MediaConstraints())
            }
        }), SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    private fun newPeer(id: String): Peer? {
        close(id)
        val f = factory ?: return null
        val config = PeerConnection.RTCConfiguration(iceServers.map { it.toIceServer() }).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // 换了网络（Wi-Fi、流量）接着找新的地址，不用重新打一遍
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val holder = arrayOfNulls<Peer>(1)
        val pc = f.createPeerConnection(config, Observer(id) { holder[0] }) ?: return null
        return Peer(id, pc).also { p ->
            holder[0] = p
            peers[id] = p
            // 信令转到一半丢了（服务器正好断线重连）：这条连接既不会通也不会报失败，一直卡着，
            // 对方再打招呼也会被当成「在连了」不理。到时候还没通就当打不通，重新来
            p.setupJob = scope.launch {
                delay(SETUP_TIMEOUT_MS)
                if (peers[id] === p && !p.isOpen()) {
                    Log.i(TAG, "setup timed out with $id")
                    failed(id)
                }
            }
        }
    }

    private fun close(id: String) {
        val p = peers.remove(id) ?: return
        p.disconnectJob?.cancel()
        p.setupJob?.cancel()
        synchronized(lock) {
            open = open.filter { it !== p.channel }
            p.channel?.let { c ->
                c.unregisterObserver()
                runCatching { c.close() }
                c.dispose()
            }
            p.channel = null
        }
        p.pc.dispose()
        publish()
    }

    /** 打不通、断了：关掉，过一会儿再打（打不通的越等越久） */
    private fun failed(id: String) {
        close(id)
        if (!running) return
        val n = failures.getOrDefault(id, 0)
        failures[id] = n + 1
        retries.remove(id)?.cancel()
        retries[id] = scope.launch {
            delay(minOf(MAX_RETRY_MS, RETRY_MS shl minOf(n, 4)))
            retries.remove(id)
            if (!running || id in peers || team?.contains(id) == false) return@launch
            if (offers(myId, id)) offer(id) else signal(id, JSONObject().put("k", "hi"))
        }
    }

    private fun publish() {
        val ids = mutableSetOf<String>()
        val channels = mutableListOf<DataChannel>()
        for (p in peers.values) {
            val c = p.channel ?: continue
            if (runCatching { c.state() }.getOrNull() == DataChannel.State.OPEN) {
                ids += p.id
                channels += c
            }
        }
        synchronized(lock) { open = channels }
        _peers.value = ids
    }

    private suspend fun fetchIce(): List<IceSpec>? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(ICE_TIMEOUT_MS) {
            runCatching {
                val url = iceUrl() ?: return@runCatching null
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (r.isSuccessful) parseIceServers(r.body.string()) else null
                }
            }.onFailure { Log.w(TAG, "ice servers", it) }.getOrNull()
        }?.takeIf { it.isNotEmpty() }
    }

    private inner class Peer(val id: String, val pc: PeerConnection) {
        var channel: DataChannel? = null
        var disconnectJob: Job? = null
        /** 打通前的计时，见 [newPeer] */
        var setupJob: Job? = null
        private var remote = false
        private val pendingIce = mutableListOf<IceCandidate>()

        fun attach(c: DataChannel) {
            channel = c
            c.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previous: Long) {}

                override fun onStateChange() {
                    scope.launch {
                        if (peers[id] !== this@Peer) return@launch
                        if (runCatching { c.state() }.getOrNull() == DataChannel.State.OPEN) {
                            Log.i(TAG, "connected to $id")
                            failures.remove(id)
                            setupJob?.cancel()
                        }
                        publish()
                    }
                }

                override fun onMessage(buffer: DataChannel.Buffer) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    onMessage(id, String(bytes))
                }
            })
        }

        fun isOpen(): Boolean = runCatching { channel?.state() }.getOrNull() == DataChannel.State.OPEN

        /** 对方的地址在 offer、answer 设好之前到了先攒着 */
        fun addIce(c: IceCandidate) {
            if (remote) pc.addIceCandidate(c) else pendingIce += c
        }

        fun remoteSet() {
            remote = true
            pendingIce.forEach { pc.addIceCandidate(it) }
            pendingIce.clear()
        }
    }

    private inner class Observer(private val id: String, private val peer: () -> Peer?) : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) {
            scope.launch {
                if (peers[id] === peer()) signal(id, JSONObject().put("k", "ice").put("c", c.sdp).put("mid", c.sdpMid).put("index", c.sdpMLineIndex))
            }
        }

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            scope.launch {
                val p = peer() ?: return@launch
                if (peers[id] !== p) return@launch
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        p.disconnectJob?.cancel()
                        p.disconnectJob = null
                    }
                    // 断了一下（换网络、进隧道）：给一会儿自己恢复，恢复不了再重新打
                    PeerConnection.PeerConnectionState.DISCONNECTED -> if (p.disconnectJob == null) {
                        p.disconnectJob = scope.launch {
                            delay(DISCONNECT_GRACE_MS)
                            if (peers[id] === p) failed(id)
                        }
                    }
                    PeerConnection.PeerConnectionState.FAILED -> failed(id)
                    else -> {}
                }
            }
        }

        override fun onDataChannel(c: DataChannel) {
            scope.launch {
                val p = peer()
                if (p == null || peers[id] !== p || c.label() != CHANNEL) {
                    c.dispose()
                    return@launch
                }
                p.attach(c)
                publish()
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onRenegotiationNeeded() {}
    }

    /** 只关心成功；失败记一笔 */
    private class Sdp(
        private val onSet: () -> Unit = {},
        private val onCreate: (SessionDescription) -> Unit = {},
    ) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = onCreate(desc)
        override fun onSetSuccess() = onSet()
        override fun onCreateFailure(error: String?) { Log.w(TAG, "sdp create failed: $error") }
        override fun onSetFailure(error: String?) { Log.w(TAG, "sdp set failed: $error") }
    }

    /** 一个 STUN / TURN 服务器 */
    data class IceSpec(val urls: List<String>, val username: String? = null, val credential: String? = null) {
        fun toIceServer(): PeerConnection.IceServer =
            PeerConnection.IceServer.builder(urls).apply {
                username?.let { setUsername(it) }
                credential?.let { setPassword(it) }
            }.createIceServer()
    }

    companion object {
        private const val TAG = "RtcLink"
        private const val CHANNEL = "voice"
        /** 通道里攒着没发出去的超过这么多（大约一秒的对讲）就先不往里塞 */
        private const val MAX_BUFFERED = 4 * 1024L
        private const val ICE_TIMEOUT_MS = 3_000L
        private const val RETRY_MS = 3_000L
        private const val MAX_RETRY_MS = 60_000L
        private const val DISCONNECT_GRACE_MS = 8_000L
        /** 开始打以后这么久还没通就重来 */
        private const val SETUP_TIMEOUT_MS = 15_000L

        /** 服务器的 /ice 拿不到时用 */
        val DEFAULT_ICE = listOf(IceSpec(listOf("stun:stun.cloudflare.com:3478", "stun:stun.l.google.com:19302")))

        /** 两人之间 id 小的发 offer */
        fun offers(myId: String, peerId: String): Boolean = myId < peerId

        /** 服务器 /ice 返回的 {"iceServers":[{urls,username,credential}]}；urls 可能是一个字符串 */
        fun parseIceServers(json: String): List<IceSpec> {
            val list = JSONObject(json).optJSONArray("iceServers") ?: return emptyList()
            return (0 until list.length()).mapNotNull { i ->
                val o = list.optJSONObject(i) ?: return@mapNotNull null
                val urls = when (val u = o.opt("urls")) {
                    is JSONArray -> (0 until u.length()).map { u.getString(it) }
                    is String -> listOf(u)
                    else -> emptyList()
                }
                if (urls.isEmpty()) null
                else IceSpec(urls, o.optString("username").ifEmpty { null }, o.optString("credential").ifEmpty { null })
            }
        }
    }
}
