package com.app.bebinim.data.api

import com.app.bebinim.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Main REST API — protocol-compatible with the original app.
 * NOTE: ticket/support and archive-movie endpoints removed by design.
 */
interface BebinimApiService {

    @POST("api/v1/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<String>>

    @POST("api/v1/login/send-otp")
    suspend fun sendLoginOtp(@Body request: SendLoginOtpRequest): Response<ApiResponse<SendOtpData>>

    @POST("api/v1/login/verify-otp")
    suspend fun verifyLoginOtp(@Body request: VerifyLoginOtpRequest): Response<ApiResponse<String>>

    @POST("api/v1/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<RegisterResponse>>

    @POST("api/v1/refresh-token")
    suspend fun refreshToken(@Body body: Map<String, String>): Response<ApiResponse<String>>

    @GET("api/v1/user")
    suspend fun getUser(@Header("Authorization") token: String): Response<ApiResponse<User>>

    @POST("api/v1/generate-web-login")
    suspend fun generateWebLogin(@Body request: GenerateWebLoginRequest): Response<ApiResponse<WebLoginResponse>>

    // ---- Plans ----
    @GET("api/v1/plans")
    suspend fun getPlans(): Response<ApiResponse<List<Plan>>>

    @GET("api/v1/plans/{id}")
    suspend fun getPlanDetails(@Path("id") planId: String): Response<ApiResponse<Plan>>

    @POST("api/v1/verify-plan")
    suspend fun verifyPlan(@Body body: Map<String, String>): Response<ApiResponse<PlanStatus>>

    @GET("api/v1/user/plan")
    suspend fun getMyActivePlan(@Header("Authorization") token: String): Response<ApiResponse<Plan>>

    // ---- Lobby ----
    @POST("api/v1/lobby/create-token")
    suspend fun createLobbyToken(
        @Header("Authorization") token: String,
        @Body request: CreateLobbyRequest
    ): Response<ApiResponse<LobbyTokenResponse>>

    @POST("api/v1/lobby/join-token")
    suspend fun joinLobbyToken(
        @Header("Authorization") token: String,
        @Body request: JoinLobbyRequest
    ): Response<ApiResponse<LobbyTokenResponse>>

    @GET("api/v1/lobby/active")
    suspend fun getActiveLobbies(@Header("Authorization") token: String): Response<ApiResponse<ActiveLobbiesResponse>>

    // ---- Ranking ----
    @GET("api/v1/ranking")
    suspend fun getLeaderboard(): Response<ApiResponse<LeaderboardResponse>>

    @GET("api/v1/ranking/me")
    suspend fun getMyRank(@Header("Authorization") token: String): Response<ApiResponse<MyRankResponse>>

    @GET("api/v1/ranking/user/{userId}")
    suspend fun getUserPublicProfile(@Path("userId") userId: String): Response<ApiResponse<UserPublicProfile>>
}
