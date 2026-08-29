package com.app.bebinim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.bebinim.BebinimApplication
import com.app.bebinim.data.api.ApiResponse
import com.app.bebinim.data.api.CreateLobbyRequest
import com.app.bebinim.data.api.JoinLobbyRequest
import com.app.bebinim.data.api.LobbyApiService
import com.app.bebinim.data.api.Music
import com.app.bebinim.data.api.RetrofitClient
import com.app.bebinim.data.utils.TokenManager
import com.app.bebinim.data.voicechat.VoiceRelayManager
import com.app.bebinim.data.websocket.LobbyInfo
import com.app.bebinim.data.websocket.WebSocketManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class CreateLobbyState {
    object Idle : CreateLobbyState()
    object Loading : CreateLobbyState()
    data class Error(val message: String) : CreateLobbyState()
}

class LobbyViewModel : ViewModel() {

    private val webSocketManager = WebSocketManager.getInstance()
    private val tokenManager = TokenManager(BebinimApplication.appContext!!)
    private val lobbyApi: LobbyApiService = RetrofitClient.lobbyApiService

    // streams forwarded from the websocket manager
    val connectionState = webSocketManager.connectionState
    val lobbyInfo: StateFlow<LobbyInfo?> = webSocketManager.lobbyInfo
    val messages = webSocketManager.messages
    val newMessageReceived = webSocketManager.newMessageReceived
    val users = webSocketManager.users
    val currentVideoUrl = webSocketManager.currentVideoUrl
    val currentPlaybackMode = webSocketManager.currentPlaybackMode
    val videoSyncState = webSocketManager.videoSyncState
    val playbackSyncState = webSocketManager.playbackSyncState
    val subtitleInfo = webSocketManager.subtitleInfo
    val displayNames = webSocketManager.displayNames
    val userIcons = webSocketManager.userIcons
    val joinSuccess = webSocketManager.joinSuccess
    val currentUserId = webSocketManager.currentUserId
    val isHost = webSocketManager.isHost
    val lobbyClosed = webSocketManager.lobbyClosed
    val allUsersReady = webSocketManager.allUsersReady
    val readyStatus = webSocketManager.readyStatus
    val musicMetadata = webSocketManager.musicMetadata
    val micEnabledUserIds = webSocketManager.micEnabledUserIds
    val sharedFileName = webSocketManager.sharedFileName

    private val _createLobbyState = MutableStateFlow<CreateLobbyState>(CreateLobbyState.Idle)
    val createLobbyState: StateFlow<CreateLobbyState> = _createLobbyState

    private val _lobbyCode = MutableStateFlow("")
    val lobbyCode: StateFlow<String> = _lobbyCode

    private val _shouldNavigate = MutableStateFlow(false)
    val shouldNavigate: StateFlow<Boolean> = _shouldNavigate

    private val _activeLobbies = MutableStateFlow<List<com.app.bebinim.data.api.ActiveLobby>>(emptyList())
    val activeLobbies: StateFlow<List<com.app.bebinim.data.api.ActiveLobby>> = _activeLobbies

    private val _activeLobbiesLoading = MutableStateFlow(false)
    val activeLobbiesLoading: StateFlow<Boolean> = _activeLobbiesLoading

    private val _isMicEnabled = MutableStateFlow(false)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled

    private var currentLobbyToken: String = ""
    private var currentLobbyType: String = "movie"
    private var voiceJoinedLobbyCode: String = ""

    // ---------------- create / join ----------------
    fun createLobby(lobbyType: String = "movie") {
        _createLobbyState.value = CreateLobbyState.Loading
        _shouldNavigate.value = true
        currentLobbyType = lobbyType
        viewModelScope.launch {
            try {
                val token = tokenManager.getToken() ?: throw IllegalStateException()
                val response = RetrofitClient.apiService.createLobbyToken(
                    "Bearer $token", CreateLobbyRequest(lobbyType)
                )
                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.data != null) {
                    currentLobbyToken = body.data.token
                    _lobbyCode.value = body.data.code
                    connectWithLobbyToken(currentLobbyToken)
                } else {
                    _createLobbyState.value = CreateLobbyState.Error(
                        body?.message?.takeIf { it.isNotBlank() } ?: "خطا در ساخت لابی"
                    )
                }
            } catch (e: java.io.IOException) {
                _createLobbyState.value = CreateLobbyState.Error("خطای شبکه. لطفاً اتصال اینترنت را بررسی کنید")
            } catch (e: Exception) {
                _createLobbyState.value = CreateLobbyState.Error("خطا در ساخت لابی")
            }
        }
    }

    fun joinLobby(code: String) {
        if (code.isBlank()) {
            _createLobbyState.value = CreateLobbyState.Error("لطفا کد لابی را وارد کنید")
            return
        }
        _createLobbyState.value = CreateLobbyState.Loading
        viewModelScope.launch {
            try {
                val token = tokenManager.getToken() ?: throw IllegalStateException()
                val response = RetrofitClient.apiService.joinLobbyToken(
                    "Bearer $token", JoinLobbyRequest(code.trim().uppercase())
                )
                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.data != null) {
                    currentLobbyToken = body.data.token
                    currentLobbyType = body.data.lobbyType
                    _lobbyCode.value = body.data.code
                    connectWithLobbyToken(currentLobbyToken)
                } else {
                    _createLobbyState.value = CreateLobbyState.Error(
                        body?.message?.takeIf { it.isNotBlank() } ?: "خطا در ورود به لابی"
                    )
                }
            } catch (e: java.io.IOException) {
                _createLobbyState.value = CreateLobbyState.Error("خطای شبکه. لطفاً اتصال اینترنت را بررسی کنید")
            } catch (e: Exception) {
                _createLobbyState.value = CreateLobbyState.Error("خطا در ورود به لابی")
            }
        }
    }

    private suspend fun connectWithLobbyToken(token: String) {
        webSocketManager.disconnect()
        delay(60) // tiny gap so the old socket tear-down settles (was 200ms — slow join)
        webSocketManager.connect(token)

        val connected = withTimeoutOrNull(15000) {
            webSocketManager.connectionState.first { it is com.app.bebinim.data.websocket.ConnectionState.Connected }
            webSocketManager.isVerified.first { it }
        } != null
        if (connected != true) {
            _createLobbyState.value = CreateLobbyState.Error("خطا در اتصال به سرور")
            return
        }
        webSocketManager.sendLobbyToken(token)

        val joined = withTimeoutOrNull(10000) {
            webSocketManager.joinSuccess.first { it.isNotBlank() }
        }
        if (joined == null) {
            _createLobbyState.value = CreateLobbyState.Error("سرور پاسخ نداد. لطفا دوباره تلاش کنید")
        }
    }

    fun clearState() {
        _createLobbyState.value = CreateLobbyState.Idle
        _shouldNavigate.value = false
    }

    // ---------------- active lobbies ----------------
    fun fetchActiveLobbies(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _activeLobbiesLoading.value = true
            try {
                val token = tokenManager.getToken() ?: return@launch
                val response = RetrofitClient.apiService.getActiveLobbies("Bearer $token")
                val body = response.body()
                if (body?.status == "success" && body.data != null) {
                    _activeLobbies.value = body.data.lobbies
                }
            } catch (_: Exception) {
            } finally {
                if (!silent) _activeLobbiesLoading.value = false
            }
        }
    }

    // ---------------- lobby actions ----------------
    fun sendAlias(name: String, iconId: String = "") = webSocketManager.sendAlias(name, iconId)
    fun sendMessage(text: String) = webSocketManager.sendMessage(text)
    fun sendVideoLink(link: String) = webSocketManager.sendVideoLink(link)
    fun sendModeChange(mode: String) = webSocketManager.sendModeChange(mode)
    fun sendWebViewMode(url: String) = webSocketManager.sendWebViewMode(url)
    fun sendSharedFileMode(fileName: String) = webSocketManager.sendSharedFileMode(fileName)
    fun sendMusicWithMetadata(
        audioUrl: String, name: String, artist: String,
        coverImage: String, duration: Int, musicId: String = ""
    ) = webSocketManager.sendMusicWithMetadata(audioUrl, name, artist, coverImage, duration, musicId)
    fun updateVideoState(currentTime: Double, isPlaying: Boolean) =
        webSocketManager.updateVideoState(currentTime, isPlaying)
    fun seekVideo(time: Double) = webSocketManager.seekVideo(time)
    fun sendPlayerReady(code: String) = webSocketManager.sendPlayerReady(code)
    fun sendSubtitle(url: String, language: String = "fa", label: String = "فارسی") =
        webSocketManager.sendSubtitle(url, language, label)
    fun clearPlayer() {
        // original clearPlayer is LOCAL ONLY (resets url + pending sync, sends nothing)
        webSocketManager.clearPlayer()
    }

    /** Set the player URL locally without broadcasting (original had this on the ViewModel too). */
    fun updateVideoUrl(url: String) = webSocketManager.updateVideoUrl(url)

    fun exitLobby() {
        viewModelScope.launch {
            voiceRelayManager?.leaveCall()
            voiceRelayManager = null
            _isMicEnabled.value = false
            webSocketManager.exitLobby()
        }
    }

    fun closeLobby() {
        viewModelScope.launch {
            voiceRelayManager?.leaveCall()
            voiceRelayManager = null
            _isMicEnabled.value = false
            webSocketManager.closeLobby()
        }
    }

    fun leaveLobbySilent() {
        // FULL cleanup — the original left the socket half-alive here, so joinSuccess and
        // the users list survived inside the WebSocketManager singleton. Re-entering the
        // create/join screen then replayed the stale joinSuccess and threw the user straight
        // back into the closed room (the "میره توی اتاق قبلی و هنگ میکنه" bug).
        // NOTE: synchronous on purpose — viewModelScope may already be dying during dispose.
        voiceRelayManager?.leaveCall()
        voiceRelayManager = null
        _isMicEnabled.value = false
        webSocketManager.disconnect()
        clearState()
    }

    /** Called when entering the create/join screen — wipes any stale lobby/socket state. */
    fun resetSession() {
        voiceRelayManager?.leaveCall()
        voiceRelayManager = null
        _isMicEnabled.value = false
        webSocketManager.disconnect()
        clearState()
    }

    // ---------------- voice ----------------
    private var voiceRelayManager: VoiceRelayManager? = null

    fun initVoiceChat() {
        val code = _lobbyCode.value.ifBlank { webSocketManager.lobbyInfo.value?.code ?: "" }
        if (code.isBlank()) return
        if (voiceJoinedLobbyCode == code && voiceRelayManager != null) return
        ensureVoiceStarted(code)
    }

    /** Starts the voice relay for [code] — safe to call repeatedly. */
    private fun ensureVoiceStarted(code: String) {
        if (voiceJoinedLobbyCode == code && voiceRelayManager != null) return
        val context = BebinimApplication.appContext ?: return
        try {
            val manager = VoiceRelayManager.getInstance(context)
            manager.start(code, webSocketManager.currentUserId.value)
            voiceRelayManager = manager
            voiceJoinedLobbyCode = code
        } catch (_: Exception) {
        }
    }

    fun sendMicToggle(enabled: Boolean) {
        _isMicEnabled.value = enabled
        webSocketManager.sendMicStatus(enabled)
        // self-heal: if initVoiceChat never ran (blank code/userId at the time), start now —
        // otherwise the mic icon shows ON while no audio is ever captured/sent
        if (voiceRelayManager == null) {
            val code = _lobbyCode.value.ifBlank { webSocketManager.lobbyInfo.value?.code ?: "" }
            if (code.isNotBlank()) ensureVoiceStarted(code)
        }
        voiceRelayManager?.setMicrophoneEnabled(enabled)
    }

    override fun onCleared() {
        voiceRelayManager?.leaveCall()
        super.onCleared()
    }
}
