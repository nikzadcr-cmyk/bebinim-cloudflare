package com.app.bebinim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.bebinim.BebinimApplication
import com.app.bebinim.data.api.LeaderboardEntry
import com.app.bebinim.data.api.MyRankResponse
import com.app.bebinim.data.api.RankTier
import com.app.bebinim.data.repository.RankingRepository
import com.app.bebinim.data.utils.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class RankingState {
    object Loading : RankingState()
    data class Success(
        val leaderboard: List<LeaderboardEntry>,
        val allRanks: List<RankTier>,
        val myRank: MyRankResponse?
    ) : RankingState()
    data class Error(val message: String) : RankingState()
}

sealed class UserProfileState {
    object Idle : UserProfileState()
    object Loading : UserProfileState()
    data class Success(val url: String) : UserProfileState()
    data class Error(val message: String) : UserProfileState()
}

class RankingViewModel : ViewModel() {

    private val repository = RankingRepository()
    private val tokenManager = TokenManager(BebinimApplication.appContext!!)

    private val _state = MutableStateFlow<RankingState>(RankingState.Loading)
    val state: StateFlow<RankingState> = _state

    private val _userProfileState = MutableStateFlow<UserProfileState>(UserProfileState.Idle)
    val userProfileState: StateFlow<UserProfileState> = _userProfileState

    init {
        load()
    }

    fun load() {
        _state.value = RankingState.Loading
        viewModelScope.launch {
            try {
                val leaderboardResponse = repository.getLeaderboard()
                if (leaderboardResponse.status == "success" && leaderboardResponse.data != null) {
                    var myRank: MyRankResponse? = null
                    try {
                        val token = tokenManager.getToken()
                        if (!token.isNullOrBlank()) {
                            val myRankResponse = repository.getMyRank(token)
                            if (myRankResponse.status == "success") myRank = myRankResponse.data
                        }
                    } catch (_: Exception) {
                    }
                    _state.value = RankingState.Success(
                        leaderboardResponse.data.leaderboard,
                        leaderboardResponse.data.allRanks,
                        myRank
                    )
                } else {
                    _state.value = RankingState.Error(leaderboardResponse.message.ifBlank { "خطا در دریافت رنکینگ" })
                }
            } catch (e: Exception) {
                _state.value = RankingState.Error("خطا در دریافت رنکینگ")
            }
        }
    }

    fun loadUserProfile(userId: String) {
        _userProfileState.value = UserProfileState.Loading
        viewModelScope.launch {
            try {
                val response = repository.generateWebProfileUrl(userId)
                if (response.status == "success" && response.data != null) {
                    _userProfileState.value = UserProfileState.Success(response.data.url)
                } else {
                    _userProfileState.value = UserProfileState.Error(response.message.ifBlank { "خطا در ساخت لینک پروفایل" })
                }
            } catch (_: Exception) {
                _userProfileState.value = UserProfileState.Error("خطا در ساخت لینک پروفایل")
            }
        }
    }

    fun clearProfile() {
        _userProfileState.value = UserProfileState.Idle
    }
}
