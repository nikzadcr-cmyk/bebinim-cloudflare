package com.app.bebinim.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Lobby/music API (served by the same Cloudflare worker now).
 * NOTE: archive-movies endpoint removed by design.
 */
interface LobbyApiService {

    // ---- Music ----
    @GET("api/v1/lobby/music/all")
    suspend fun getAllMusic(
        @Query("limit") limit: Int = 30,
        @Query("skip") skip: Int = 0,
        @Query("search") search: String? = null,
        @Query("sortBy") sortBy: String? = null
    ): Response<MusicListResponse>

    @GET("api/v1/lobby/music/category/{categoryId}")
    suspend fun getMusicByCategory(
        @Path("categoryId") categoryId: String,
        @Query("limit") limit: Int = 30,
        @Query("skip") skip: Int = 0
    ): Response<MusicListResponse>

    @GET("api/v1/lobby/music/new-releases")
    suspend fun getNewReleases(@Query("limit") limit: Int = 20): Response<MusicListResponse>

    @GET("api/v1/lobby/music/trending")
    suspend fun getTrendingMusic(
        @Query("limit") limit: Int = 20,
        @Query("period") period: String = "week"
    ): Response<MusicListResponse>

    @GET("api/v1/lobby/music/random")
    suspend fun getRandomMusic(
        @Query("excludeId") excludeId: String? = null,
        @Query("categoryId") categoryId: String? = null,
        @Query("limit") limit: Int = 10
    ): Response<MusicListResponse>

    @GET("api/v1/lobby/music/recently-played")
    suspend fun getRecentlyPlayed(
        @Header("X-Device-Id") deviceId: String,
        @Query("limit") limit: Int = 10
    ): Response<MusicListResponse>

    @GET("api/v1/lobby/music/recommended")
    suspend fun getRecommendedMusic(
        @Header("X-Device-Id") deviceId: String,
        @Query("limit") limit: Int = 10
    ): Response<RecommendedMusicResponse>

    @GET("api/v1/lobby/music-categories")
    suspend fun getMusicCategories(): Response<MusicCategoriesResponse>

    @POST("api/v1/lobby/music/{musicId}/play")
    suspend fun registerPlay(@Path("musicId") musicId: String, @Header("X-Device-Id") deviceId: String): Response<ApiResponse<Boolean>>

    // ---- Artists ----
    @GET("api/v1/lobby/artists")
    suspend fun getArtists(
        @Query("limit") limit: Int = 20,
        @Query("skip") skip: Int = 0,
        @Query("search") search: String? = null
    ): Response<ArtistsResponse>

    @GET("api/v1/lobby/artists/popular")
    suspend fun getPopularArtists(@Query("limit") limit: Int = 10): Response<ArtistsResponse>

    @GET("api/v1/lobby/artists/followed")
    suspend fun getFollowedArtists(@Header("X-Device-Id") deviceId: String): Response<ArtistsResponse>

    @GET("api/v1/lobby/artist/{artistId}")
    suspend fun getArtist(@Path("artistId") artistId: String): Response<ArtistResponse>

    @GET("api/v1/lobby/artist/{artistId}/follow-status")
    suspend fun checkFollowStatus(@Path("artistId") artistId: String, @Header("X-Device-Id") deviceId: String): Response<FollowStatusResponse>

    @POST("api/v1/lobby/artist/{artistId}/follow")
    suspend fun followArtist(@Path("artistId") artistId: String, @Header("X-Device-Id") deviceId: String): Response<FollowResponse>

    @POST("api/v1/lobby/artist/{artistId}/unfollow")
    suspend fun unfollowArtist(@Path("artistId") artistId: String, @Header("X-Device-Id") deviceId: String): Response<FollowResponse>
}
