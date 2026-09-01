package com.app.hamfilm.desktop.net

import com.app.hamfilm.desktop.ChatMessage
import com.app.hamfilm.desktop.ConnectionState
import com.app.hamfilm.desktop.LobbyInfo
import com.app.hamfilm.desktop.LobbyUser
import com.app.hamfilm.desktop.MusicMetadata
import com.app.hamfilm.desktop.PlaybackSyncState
import com.app.hamfilm.desktop.ReadyStatus
import com.app.hamfilm.desktop.SubtitleInfo
import com.app.hamfilm.desktop.VideoSyncState
import com.app.hamfilm.desktop.VoiceCredential
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * WebSocket manager — byte-compatible port of the Android app's WebSocketManager
 * (basemsg-* protocol over the Cloudflare Durable Object lobby server),
 * including the reconnect-generation logic and the voice backpressure guard.
 */
class HamSocket private constructor() {

    companion object {
        /** max bytes of pending voice in the OkHttp send queue before frames are dropped
         *  (~0.5s of voice at 32KB/s — keeps chat text flowing, avoids write timeouts) */
        const val MAX_VOICE_QUEUE_BYTES = 16L * 1024

        @Volatile
        private var instance: HamSocket? = null

        fun getInstance(): HamSocket =
            instance ?: synchronized(this) {
                instance ?: HamSocket().also { instance = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isVerified = MutableStateFlow(false)
    val isVerified: StateFlow<Boolean> = _isVerified

    private val _joinSuccess = MutableStateFlow("")
    val joinSuccess: StateFlow<String> = _joinSuccess

    private val _lobbyClosed = MutableStateFlow(false)
    val lobbyClosed: StateFlow<Boolean> = _lobbyClosed

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId

    private val _lobbyInfo = MutableStateFlow<LobbyInfo?>(null)
    val lobbyInfo: StateFlow<LobbyInfo?> = _lobbyInfo

    private val _users = MutableStateFlow<List<LobbyUser>>(emptyList())
    val users: StateFlow<List<LobbyUser>> = _users

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _newMessageReceived = MutableStateFlow(false)
    val newMessageReceived: StateFlow<Boolean> = _newMessageReceived

    private val _currentVideoUrl = MutableStateFlow("")
    val currentVideoUrl: StateFlow<String> = _currentVideoUrl

    private val _currentPlaybackMode = MutableStateFlow("link")
    val currentPlaybackMode: StateFlow<String> = _currentPlaybackMode

    private val _videoSyncState = MutableStateFlow<VideoSyncState?>(null)
    val videoSyncState: StateFlow<VideoSyncState?> = _videoSyncState

    private val _playbackSyncState = MutableStateFlow<PlaybackSyncState?>(null)
    val playbackSyncState: StateFlow<PlaybackSyncState?> = _playbackSyncState

    private val _subtitleInfo = MutableStateFlow<SubtitleInfo?>(null)
    val subtitleInfo: StateFlow<SubtitleInfo?> = _subtitleInfo

    private val _readyStatus = MutableStateFlow<ReadyStatus?>(null)
    val readyStatus: StateFlow<ReadyStatus?> = _readyStatus

    private val _allUsersReady = MutableStateFlow(false)
    val allUsersReady: StateFlow<Boolean> = _allUsersReady

    private val _micEnabledUserIds = MutableStateFlow<Set<String>>(emptySet())
    val micEnabledUserIds: StateFlow<Set<String>> = _micEnabledUserIds

    private val _musicMetadata = MutableStateFlow<MusicMetadata?>(null)
    val musicMetadata: StateFlow<MusicMetadata?> = _musicMetadata

    private val _sharedFileName = MutableStateFlow("")
    val sharedFileName: StateFlow<String> = _sharedFileName

    private val _displayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val displayNames: StateFlow<Map<String, String>> = _displayNames

    private val _userIcons = MutableStateFlow<Map<String, String>>(emptyMap())
    val userIcons: StateFlow<Map<String, String>> = _userIcons

    private var webSocket: WebSocket? = null
    private var authToken: String = ""
    private var lobbyToken: String = ""
    private var hasJoinedLobby = false
    private var reconnectAttempts = 0
    private var isReconnecting = false
    // bumped on every connect()/disconnect() — pending reconnect coroutines from an old
    // generation must not resurrect a socket the user already left
    private var connectGeneration = 0

    private var pendingVoiceTokenRequest: CompletableDeferred<VoiceCredential>? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)      // liveness: detect dead sockets faster
        .readTimeout(0, TimeUnit.MILLISECONDS)   // WebSockets must NOT have a read timeout
        .writeTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ---------------- connection ----------------
    fun connect(token: String) {
        connectGeneration++
        if (webSocket != null) return
        authToken = token
        _isVerified.value = false
        if (!isReconnecting) {
            hasJoinedLobby = false
            reconnectAttempts = 0
        }

        val url = Api.WS_URL + "?token=" + URLEncoder.encode(token, "UTF-8")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
    }

    private val listener = object : okhttp3.WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _isConnected.value = true
            _connectionState.value = ConnectionState.Connected
            reconnectAttempts = 0
            // first message: verify with lobby token
            webSocket.send(JSONObject().apply {
                put("type", "verify")
                put("data", authToken)
            }.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                handleMessage(text)
            } catch (_: Exception) {
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            // binary voice frames from the relay — forward to the voice manager
            VoiceRouter.onBinary(bytes.toByteArray())
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (code != 1000 && authToken.isNotEmpty() && reconnectAttempts < 5) {
                scheduleReconnect()
            } else {
                handleDisconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (reconnectAttempts < 10 && authToken.isNotEmpty()) {
                scheduleReconnect()
            } else {
                handleDisconnect()
                if (hasJoinedLobby) {
                    _connectionState.value =
                        ConnectionState.Error("اتصال قطع شد. لطفاً اتصال اینترنت خود را بررسی کنید.")
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (isReconnecting) return
        isReconnecting = true
        reconnectAttempts++
        val gen = connectGeneration
        val delayMs = if (reconnectAttempts <= 2) 500L * reconnectAttempts
        else 2000L * (reconnectAttempts - 1).coerceAtMost(5)
        scope.launch {
            delay(delayMs)
            if (gen != connectGeneration) {
                isReconnecting = false
                return@launch
            }
            webSocket = null
            // keep isReconnecting=true THROUGH connect() so hasJoinedLobby survives and
            // verify-result re-joins the lobby (mid-room reconnect flow)
            connect(authToken)
            isReconnecting = false
        }
    }

    private fun handleDisconnect() {
        webSocket = null
        _isConnected.value = false
        _isVerified.value = false
        _connectionState.value = ConnectionState.Disconnected
        resetLobbyState()
    }

    fun disconnect() {
        connectGeneration++
        reconnectAttempts = 999
        isReconnecting = false
        try { webSocket?.close(1000, "User disconnected") } catch (_: Exception) {}
        webSocket = null
        authToken = ""
        lobbyToken = ""
        handleDisconnect()
    }

    private fun resetLobbyState() {
        _lobbyInfo.value = null
        _users.value = emptyList()
        _lobbyClosed.value = false
        _messages.value = emptyList()
        _currentVideoUrl.value = ""
        _currentPlaybackMode.value = "link"
        _videoSyncState.value = null
        _playbackSyncState.value = null
        _readyStatus.value = null
        _allUsersReady.value = false
        _musicMetadata.value = null
        _isHost.value = false
        _joinSuccess.value = ""
        _sharedFileName.value = ""
        _micEnabledUserIds.value = emptySet()
        hasJoinedLobby = false
        lobbyToken = ""
    }

    // ---------------- outbound (exact protocol) ----------------
    fun sendLobbyToken(token: String) {
        lobbyToken = token
        send(JSONObject().apply { put("type", "basemsg-join-to-lobby"); put("data", token) })
        hasJoinedLobby = true
    }

    fun leaveLobby() {
        val code = _lobbyInfo.value?.code ?: return
        send(JSONObject().apply {
            put("type", "basemsg-exit-lobby"); put("lobbycode", code); put("data", JSONObject())
        })
        resetLobbyState()
    }

    fun exitLobby() {
        val code = _lobbyInfo.value?.code ?: return
        send(JSONObject().apply { put("type", "basemsg-exit-lobby"); put("lobbycode", code); put("data", "") })
        send(JSONObject().apply { put("type", "close-socket-connection") })
        resetLobbyState()
    }

    fun closeLobby() {
        val code = _lobbyInfo.value?.code ?: return
        send(JSONObject().apply { put("type", "basemsg-close-lobby"); put("lobbycode", code); put("data", "") })
    }

    fun sendMessage(message: String, myDisplayName: String = "من") {
        val code = _lobbyInfo.value?.code ?: return
        _messages.value = _messages.value + ChatMessage(
            myDisplayName, message, System.currentTimeMillis(), _currentUserId.value
        )
        send(JSONObject().apply {
            put("type", "basemsg-chat"); put("lobbycode", code)
            put("data", JSONObject().apply { put("text", message); put("to", JSONObject.NULL) })
        })
    }

    fun sendAlias(displayName: String, iconId: String = "") {
        val code = _lobbyInfo.value?.code ?: return
        send(JSONObject().apply {
            put("type", "basemsg-alias"); put("lobbycode", code)
            put("data", JSONObject().apply {
                put("name", displayName)
                if (iconId.isNotBlank()) put("icon", iconId)
            })
        })
    }

    fun updateVideoUrl(url: String) {
        _currentVideoUrl.value = url
    }

    fun clearPlayer() {
        _currentVideoUrl.value = ""
        _videoSyncState.value = null
    }

    fun sendVideoLink(link: String) {
        val code = _lobbyInfo.value?.code ?: return
        send(JSONObject().apply {
            put("type", "basemsg-change-vlink"); put("lobbycode", code)
            put("data", JSONObject().apply { put("nlink", link); put("vcurrenttime", 0) })
        })
    }

    fun sendMusicWithMetadata(
        audioUrl: String, name: String, artist: String,
        coverImage: String, duration: Int, musicId: String = ""
    ) {
        val code = _lobbyInfo.value?.code ?: return
        val meta = JSONObject().apply {
            put("audioUrl", audioUrl); put("name", name); put("artist", artist)
            put("coverImage", coverImage); put("duration", duration); put("musicId", musicId)
        }
        send(JSONObject().apply { put("type", "basemsg-music-metadata"); put("lobbycode", code); put("data", meta) })
        _musicMetadata.value = MusicMetadata(audioUrl, name, artist, coverImage, duration, musicId)
        if (audioUrl.isNotBlank()) _currentVideoUrl.value = audioUrl
    }

    fun sendModeChange(mode: String) {
        val code = _lobbyInfo.value?.code ?: return
        send(JSONObject().apply {
            put("type", "basemsg-change-mode"); put("lobbycode", code)
            put("data", JSONObject().apply { put("mode", mode) })
        })
    }

    fun sendSharedFileMode(fileName: String = "فایل محلی") {
        val code = _lobbyInfo.value?.code ?: return
        send(JSONObject().apply {
            put("type", "basemsg-change-mode"); put("lobbycode", code)
            put("data", JSONObject().apply { put("mode", "shared"); put("fileName", fileName) })
        })
    }

    fun updateVideoState(currentTime: Double, isPlaying: Boolean) {
        val code = _lobbyInfo.value?.code ?: return
        send(JSONObject().apply {
            put("type", "basemsg-play-pause"); put("lobbycode", code)
            put("data", if (isPlaying) "play" else "pause")
            put("currentTime", currentTime)
        })
    }

    fun seekVideo(currentTime: Double) {
        val code = _lobbyInfo.value?.code ?: return
        send(JSONObject().apply {
            put("type", "basemsg-click-bar"); put("lobbycode", code)
            put("data", JSONObject().apply { put("currentTime", currentTime) })
        })
    }

    fun sendMicStatus(enabled: Boolean) {
        val code = _lobbyInfo.value?.code ?: return
        send(JSONObject().apply {
            put("type", "basemsg-mic-status"); put("lobbycode", code)
            put("data", JSONObject().apply { put("enabled", enabled) })
        })
    }

    fun sendPlayerReady(lobbyCode: String) {
        send(JSONObject().apply {
            put("type", "basemsg-player-ready"); put("lobbycode", lobbyCode); put("data", "ready")
        })
    }

    suspend fun requestVoiceToken(timeoutMs: Long = 5000): VoiceCredential? {
        val code = _lobbyInfo.value?.code ?: return null
        val deferred = CompletableDeferred<VoiceCredential>()
        pendingVoiceTokenRequest = deferred
        send(JSONObject().apply { put("type", "basemsg-get-voice-token"); put("lobbycode", code) })
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        pendingVoiceTokenRequest = null
        return result
    }

    fun sendSubtitle(url: String, language: String = "fa", label: String = "فارسی") {
        _subtitleInfo.value = SubtitleInfo(url, language, label, "text/vtt")
    }

    /** Voice backpressure guard — drop stale audio instead of delaying chat text. */
    fun rawSendBinary(bytes: ByteArray) {
        val ws = webSocket ?: return
        try {
            if (ws.queueSize() > MAX_VOICE_QUEUE_BYTES) return
            ws.send(okio.Buffer().write(bytes).readByteString())
        } catch (_: Exception) {}
    }

    private fun send(json: JSONObject) {
        val ws = webSocket ?: return
        try { ws.send(json.toString()) } catch (_: Exception) {}
    }

    // ---------------- inbound ----------------
    private fun handleMessage(text: String) {
        val obj = JSONObject(text)
        val type = obj.optString("type", "")
        val state = obj.optInt("state", 0)
        val data = obj.opt("data")

        when (type) {
            "verify-result" -> {
                if (state == 1) {
                    _isVerified.value = true
                    if (hasJoinedLobby && lobbyToken.isNotBlank()) {
                        sendLobbyToken(lobbyToken)
                    }
                } else {
                    val msg = obj.optString("msg", "")
                    if (msg == "device-limit-exceeded") {
                        val max = obj.optJSONObject("data")?.optInt("max_devices", 1) ?: 1
                        _connectionState.value = ConnectionState.Error("device-limit-exceeded:$max")
                    } else {
                        _connectionState.value = ConnectionState.Error("Authentication failed")
                    }
                }
            }

            "basemsg-join-to-lobby" -> {
                if (state != 1) {
                    _connectionState.value = ConnectionState.Error(data?.toString() ?: "join failed")
                    return
                }
                val d = obj.optJSONObject("data") ?: return
                _currentUserId.value = d.optString("unit_socket_id")
                _isHost.value = d.optBoolean("is_creator", false)
                _lobbyClosed.value = false
                if (_users.value.isEmpty()) {
                    _users.value = listOf(
                        LobbyUser(
                            userId = _currentUserId.value, realId = _currentUserId.value,
                            username = "من", displayName = "من", isHost = _isHost.value
                        )
                    )
                }
                _lobbyInfo.value = LobbyInfo(d.optString("code"), d.optString("lobbyType", "movie"))
                _joinSuccess.value = d.optString("code")
            }

            "basemsg-new-connection" -> {
                val d = obj.optJSONObject("data") ?: return
                val socketId = d.optString("unit_socket_id")
                val creator = d.optString("creater")
                val creatorFake = d.optString("creater_fake_id")
                _currentUserId.value = socketId
                _isHost.value = socketId == creator || socketId == creatorFake
                if (_lobbyInfo.value == null) _lobbyInfo.value = LobbyInfo(d.optString("code"), "movie")
                if (_users.value.isEmpty()) {
                    _users.value = listOf(
                        LobbyUser(socketId, socketId, "من", "من", _isHost.value)
                    )
                }
            }

            "basemsg-change-vlink" -> {
                val d = obj.optJSONObject("data") ?: return
                val url = d.optString("nlink", "").ifBlank { d.optString("url", "") }
                val linkType = d.optString("type", "link")
                _currentPlaybackMode.value = when (linkType) {
                    "aparat", "shared", "archive", "radio", "webview" -> linkType
                    else -> "link"
                }
                if (url.isNotBlank()) _currentVideoUrl.value = url
            }

            "basemsg-change-mode" -> {
                val d = obj.optJSONObject("data") ?: return
                val mode = d.optString("mode", "link")
                if (mode != _currentPlaybackMode.value) {
                    _currentPlaybackMode.value = mode
                    _currentVideoUrl.value = ""
                }
                if (mode == "shared") _sharedFileName.value = d.optString("fileName", "")
                if (mode == "radio" || mode == "webview" || mode == "aparat") {
                    val url = d.optString("url", "")
                    if (url.isNotBlank()) _currentVideoUrl.value = url
                }
            }

            "basemsg-play-pause" -> {
                val playing = data == "play"
                val time = obj.optDouble("currentTime", 0.0)
                _videoSyncState.value = VideoSyncState(time, playing, isSeek = false)
            }

            "basemsg-click-bar" -> {
                val d = obj.optJSONObject("data")
                val time = d?.optDouble("currentTime", 0.0) ?: 0.0
                _videoSyncState.value =
                    VideoSyncState(time, _videoSyncState.value?.isPlaying ?: false, isSeek = true)
            }

            "basemsg-playback-sync" -> {
                if (state != 1) return
                val d = obj.optJSONObject("data") ?: return
                val vlinkRaw = d.opt("vlink")
                val url = when (vlinkRaw) {
                    is String -> vlinkRaw
                    is JSONObject -> vlinkRaw.optString("nlink", "").ifBlank { vlinkRaw.optString("url", "") }
                    else -> ""
                }
                val mode = d.optString("mode", "link")
                val playing = d.optString("playing", "pause") == "play"
                _playbackSyncState.value =
                    PlaybackSyncState(url, d.optDouble("currentTime", 0.0), playing, mode, fromSync = true)
            }

            "basemsg-chat" -> {
                if (state != 1) return
                val userId = obj.optString("user_id", "")
                val d = obj.optJSONObject("data") ?: return
                val text = d.optString("text", "")
                val cat = d.optString("cat", "")
                if (userId == _currentUserId.value && cat != "system") return
                if (cat == "system") {
                    _messages.value = _messages.value + ChatMessage(
                        "سیستم", text, System.currentTimeMillis(), "SYSTEM", isSystemMessage = true
                    )
                } else {
                    val name = _displayNames.value[userId]
                        ?: _users.value.firstOrNull { it.userId == userId }?.displayName
                        ?: "کاربر"
                    _messages.value = _messages.value + ChatMessage(name, text, System.currentTimeMillis(), userId)
                    _newMessageReceived.value = !_newMessageReceived.value
                }
            }

            "basemsg-alias" -> {
                if (state != 1) return
                val userId = obj.optString("user_id", "")
                val d = obj.optJSONObject("data")
                val name = d?.optString("name", "") ?: return
                val icon = d?.optString("icon", "") ?: ""
                _displayNames.value = _displayNames.value + (userId to name)
                if (icon.isNotBlank()) {
                    _userIcons.value = _userIcons.value + (userId to icon)
                }
                _users.value = _users.value.map {
                    if (it.userId == userId) it.copy(displayName = name) else it
                }
            }

            "basemsg-lobby-users" -> {
                if (state != 1) return
                val arr = obj.optJSONArray("data") ?: return
                val list = mutableListOf<LobbyUser>()
                val names = _displayNames.value.toMutableMap()
                val icons = _userIcons.value.toMutableMap()
                for (i in 0 until arr.length()) {
                    val u = arr.optJSONObject(i) ?: continue
                    val uid = u.optString("user_id")
                    val realId = u.optString("real_id", uid)
                    val alias = u.optString("alias", "")
                    val icon = u.optString("icon", "")
                    val username = u.optString("username", u.optString("email", "نامشخص"))
                    names[uid] = alias.ifBlank { username }
                    if (icon.isNotBlank()) icons[uid] = icon
                    list.add(
                        LobbyUser(
                            userId = uid, realId = realId, username = username,
                            displayName = alias.ifBlank { username },
                            isHost = u.optBoolean("is_creator", false) || u.optBoolean("isHost", false)
                        )
                    )
                }
                _displayNames.value = names
                _userIcons.value = icons
                _users.value = list
            }

            "basemsg-exit-lobby" -> {
                val arr = obj.optJSONArray("data") ?: return
                val list = mutableListOf<LobbyUser>()
                for (i in 0 until arr.length()) {
                    val u = arr.optJSONObject(i) ?: continue
                    val uid = u.optString("user_id")
                    val username = u.optString("username", u.optString("email", "نامشخص"))
                    list.add(LobbyUser(uid, u.optString("real_id", uid), username, username, i == 0))
                }
                _users.value = list
            }

            "basemsg-ready-status" -> {
                val d = obj.optJSONObject("data") ?: return
                _allUsersReady.value = false
                _readyStatus.value = ReadyStatus(d.optInt("ready_count"), d.optInt("total_count"))
            }

            "basemsg-all-ready" -> {
                val d = obj.optJSONObject("data") ?: return
                _allUsersReady.value = true
                _readyStatus.value = ReadyStatus(d.optInt("ready_count"), d.optInt("total_count"))
            }

            "basemsg-mic-status" -> {
                if (state != 1) return
                val userId = obj.optString("user_id", "")
                val enabled = obj.optJSONObject("data")?.optBoolean("enabled", false) ?: false
                val current = _micEnabledUserIds.value.toMutableSet()
                if (enabled) current.add(userId) else current.remove(userId)
                _micEnabledUserIds.value = current
            }

            "basemsg-music-metadata" -> {
                val d = obj.optJSONObject("data") ?: return
                val meta = MusicMetadata(
                    audioUrl = d.optString("audioUrl"),
                    name = d.optString("name"),
                    artist = d.optString("artist"),
                    coverImage = d.optString("coverImage"),
                    duration = d.optInt("duration", 0),
                    musicId = d.optString("musicId")
                )
                _musicMetadata.value = meta
                if (meta.audioUrl.isNotBlank()) _currentVideoUrl.value = meta.audioUrl
            }

            "basemsg-voice-token" -> {
                if (state == 1) {
                    val d = obj.optJSONObject("data")
                    if (d != null) {
                        pendingVoiceTokenRequest?.complete(
                            VoiceCredential(
                                d.optString("token"),
                                d.optString("key"),
                                d.optInt("expiresInSec", 0)
                            )
                        )
                    }
                } else {
                    pendingVoiceTokenRequest?.completeExceptionally(
                        Exception(obj.optString("msg", "voice token error"))
                    )
                }
            }

            "basemsg-close-lobby" -> {
                _lobbyClosed.value = true
                _lobbyInfo.value = null
                _users.value = emptyList()
                _messages.value = emptyList()
            }
        }
    }

    /** VoiceRouter hook — the voice manager registers a binary listener here. */
    object VoiceRouter {
        @Volatile
        var binaryListener: ((ByteArray) -> Unit)? = null

        fun onBinary(bytes: ByteArray) {
            binaryListener?.invoke(bytes)
        }
    }
}
