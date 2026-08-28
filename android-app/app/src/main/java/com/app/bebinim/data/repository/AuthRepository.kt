package com.app.bebinim.data.repository

import com.app.bebinim.data.api.ApiResponse
import com.app.bebinim.data.api.BebinimApiService
import com.app.bebinim.data.api.LoginRequest
import com.app.bebinim.data.api.RegisterRequest
import com.app.bebinim.data.api.RetrofitClient
import com.app.bebinim.data.api.RegisterResponse
import com.app.bebinim.data.api.SendLoginOtpRequest
import com.app.bebinim.data.api.SendOtpData
import com.app.bebinim.data.api.VerifyLoginOtpRequest
import retrofit2.Response

class AuthRepository {

    private val api: BebinimApiService = RetrofitClient.apiService

    suspend fun login(email: String, password: String): ApiResponse<String> =
        safe { api.login(LoginRequest(email, password)) }

    suspend fun sendLoginOtp(identity: String): ApiResponse<SendOtpData> =
        safe { api.sendLoginOtp(SendLoginOtpRequest(identity)) }

    suspend fun verifyLoginOtp(identity: String, otp: String): ApiResponse<String> =
        safe { api.verifyLoginOtp(VerifyLoginOtpRequest(identity, otp)) }

    suspend fun register(name: String, phoneNumber: String, password: String): ApiResponse<RegisterResponse> =
        safe { api.register(RegisterRequest(name, password, phoneNumber)) }

    suspend fun refreshToken(token: String): ApiResponse<String> =
        safe { api.refreshToken(mapOf("token" to token)) }

    private inline fun <reified T> safe(block: () -> Response<ApiResponse<T>>): ApiResponse<T> {
        return try {
            val response = block()
            response.body() ?: ApiResponse(status = "error", message = "خطا در ارتباط با سرور")
        } catch (e: java.io.IOException) {
            ApiResponse(status = "error", message = "خطای شبکه. لطفاً اتصال اینترنت را بررسی کنید")
        } catch (e: Exception) {
            ApiResponse(status = "error", message = "خطا در ارتباط با سرور")
        }
    }
}
