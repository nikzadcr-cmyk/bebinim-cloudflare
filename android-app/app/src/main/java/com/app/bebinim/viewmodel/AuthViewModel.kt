package com.app.bebinim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.bebinim.data.api.AuthEventBus
import com.app.bebinim.data.repository.AuthRepository
import com.app.bebinim.data.utils.TokenManager
import com.app.bebinim.BebinimApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
    data class OtpSent(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()
    private val tokenManager = TokenManager(BebinimApplication.appContext!!)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing

    private val _showNoInternetDialog = MutableStateFlow(false)
    val showNoInternetDialog: StateFlow<Boolean> = _showNoInternetDialog

    val token = tokenManager.tokenFlow

    init {
        checkLoginStatus()
        observeForceLogout()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            val t = tokenManager.getToken()
            _isLoggedIn.value = !t.isNullOrBlank()
            _isInitializing.value = false
        }
    }

    private fun observeForceLogout() {
        viewModelScope.launch {
            AuthEventBus.logoutRequired.collect {
                logout()
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("لطفاً تمام فیلدها را پر کنید")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val response = repository.login(email, password)
            if (response.status == "success" && response.data != null) {
                tokenManager.saveToken(response.data)
                _isLoggedIn.value = true
                _authState.value = AuthState.Success("ورود موفقیت‌آمیز بود")
            } else {
                _authState.value = AuthState.Error(response.message.ifBlank { "مشخصات ورودی اشتباه است" })
            }
        }
    }

    fun sendLoginOtp(identity: String) {
        if (identity.isBlank()) {
            _authState.value = AuthState.Error("لطفاً ایمیل یا شماره موبایل را وارد کنید")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val response = repository.sendLoginOtp(identity)
            if (response.status == "success" && response.data != null) {
                _authState.value = AuthState.OtpSent("کد ارسال شد")
            } else {
                _authState.value = AuthState.Error(response.message.ifBlank { "خطا در ارسال کد" })
            }
        }
    }

    fun verifyLoginOtp(identity: String, otp: String) {
        if (otp.isBlank()) {
            _authState.value = AuthState.Error("کد تایید را وارد کنید")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val response = repository.verifyLoginOtp(identity, otp)
            if (response.status == "success" && response.data != null) {
                tokenManager.saveToken(response.data)
                // fetch user info
                try {
                    val userResponse = com.app.bebinim.data.api.RetrofitClient.apiService
                        .getUser("Bearer ${response.data}")
                    val user = userResponse.body()?.data
                    if (user != null) {
                        tokenManager.saveUserInfo(user.id, user.username, user.name, user.email)
                    }
                } catch (_: Exception) {
                }
                _isLoggedIn.value = true
                _authState.value = AuthState.Success("ورود موفقیت‌آمیز بود")
            } else {
                _authState.value = AuthState.Error(response.message.ifBlank { "کد تایید اشتباه است" })
            }
        }
    }

    fun register(name: String, phoneNumber: String, password: String) {
        val cleanPhone = phoneNumber.replace(Regex("[\\s\\-]"), "")
        if (!Regex("^(\\+98|0)?9\\d{9}$").matches(cleanPhone)) {
            _authState.value = AuthState.Error("شماره تلفن معتبر نیست. مثال: 09123456789")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("رمز عبور باید حداقل ۶ کاراکتر باشد")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val response = repository.register(name, cleanPhone, password)
            if (response.status == "success" && response.data != null) {
                tokenManager.saveToken(response.data.token)
                val user = response.data.user
                tokenManager.saveUserInfo(user.id, user.username, user.name, user.email)
                _isLoggedIn.value = true
                _authState.value = AuthState.Success("ثبت‌نام موفقیت‌آمیز بود")
            } else {
                _authState.value = AuthState.Error(response.message.ifBlank { "خطا در ثبت‌نام" })
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun dismissNoInternetDialog() {
        _showNoInternetDialog.value = false
    }

    fun logout() {
        viewModelScope.launch {
            tokenManager.clearAll()
            _isLoggedIn.value = false
            _authState.value = AuthState.Idle
        }
    }
}
