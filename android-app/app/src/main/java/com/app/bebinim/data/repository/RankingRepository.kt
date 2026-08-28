package com.app.bebinim.data.repository

import com.app.bebinim.data.api.ApiResponse
import com.app.bebinim.data.api.BebinimApiService
import com.app.bebinim.data.api.LeaderboardResponse
import com.app.bebinim.data.api.MyRankResponse
import com.app.bebinim.data.api.RetrofitClient
import com.app.bebinim.data.api.UserPublicProfile
import com.app.bebinim.data.api.WebLoginResponse
import com.app.bebinim.data.api.GenerateWebLoginRequest
import com.app.bebinim.data.utils.TokenManager
import kotlinx.coroutines.flow.first
import retrofit2.Response

class RankingRepository {

    private val api: BebinimApiService = RetrofitClient.apiService

    suspend fun getLeaderboard(): ApiResponse<LeaderboardResponse> = safe { api.getLeaderboard() }

    suspend fun getMyRank(token: String): ApiResponse<MyRankResponse> = safe { api.getMyRank(token) }

    suspend fun getUserPublicProfile(userId: String): ApiResponse<UserPublicProfile> =
        safe { api.getUserPublicProfile(userId) }

    /** Generates a temporary web-login URL for the embedded profile WebView. */
    suspend fun generateWebProfileUrl(userId: String): ApiResponse<WebLoginResponse> {
        val context = com.app.bebinim.BebinimApplication.appContext ?: return ApiResponse(
            status = "error", message = "خطا در ساخت لینک پروفایل"
        )
        val token = TokenManager(context).getToken().orEmpty()
        return safe {
            api.generateWebLogin(
                GenerateWebLoginRequest(token = token, redirect = "/user/ranking/profile-embed/$userId")
            )
        }
    }

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
