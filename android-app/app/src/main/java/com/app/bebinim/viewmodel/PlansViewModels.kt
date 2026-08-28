package com.app.bebinim.viewmodel

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.app.bebinim.data.api.PlanStatus
import com.app.bebinim.data.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class MyPlansState {
    object Loading : MyPlansState()
    data class Success(val planStatus: PlanStatus) : MyPlansState()
    data class Error(val message: String) : MyPlansState()
}

class MyPlansViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlanRepository()

    private val _state = MutableStateFlow<MyPlansState>(MyPlansState.Loading)
    val state: StateFlow<MyPlansState> = _state

    init {
        loadMyPlans()
    }

    fun loadMyPlans() {
        _state.value = MyPlansState.Loading
        viewModelScope.launch {
            val response = repository.verifyPlan()
            if (response.status == "success" && response.data != null) {
                _state.value = MyPlansState.Success(response.data)
            } else {
                _state.value = MyPlansState.Error(response.message.ifBlank { "خطا در دریافت وضعیت پلن" })
            }
        }
    }
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    data class UserInfo(
        val username: String = "کاربر",
        val name: String? = null,
        val email: String? = null
    )

    private val tokenManager = com.app.bebinim.data.utils.TokenManager(application)

    private val _userInfo = MutableStateFlow(UserInfo())
    val userInfo: StateFlow<UserInfo> = _userInfo

    init {
        loadUserInfo()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            val username = tokenManager.usernameFlow
            val name = tokenManager.nameFlow
            val email = tokenManager.emailFlow
            kotlinx.coroutines.flow.combine(username, name, email) { u, n, e ->
                Triple(u, n, e)
            }.collect { (u, n, e) ->
                _userInfo.value = UserInfo(username = u ?: "کاربر", name = n, email = e)
            }
        }
    }
}
