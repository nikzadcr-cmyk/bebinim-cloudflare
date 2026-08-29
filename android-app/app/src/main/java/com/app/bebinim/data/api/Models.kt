package com.app.bebinim.data.api

// ---- Envelope ----
data class ApiResponse<T>(
    val status: String = "error",
    val message: String = "",
    val data: T? = null
)

// ---- User / Auth ----
data class User(
    val id: String,
    val name: String,
    val username: String,
    val email: String,
    val roles: List<Role> = emptyList(),
    val createdAt: String? = null
)

data class Role(val name: String, val label: String)

data class UserInfo(
    val id: String = "",
    val name: String? = null,
    val username: String = "",
    val email: String? = null
)

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(
    val name: String,
    val password: String,
    val phone_number: String
)

data class RegisterResponse(val token: String, val user: UserInfo)
data class SendLoginOtpRequest(val identity: String)
data class SendOtpData(val expires_in: Int = 120, val dev_code: String? = null)
data class VerifyLoginOtpRequest(val identity: String, val otp_code: String)
data class GenerateWebLoginRequest(val token: String, val redirect: String)
data class WebLoginResponse(val token: String, val url: String)

// ---- Plans ----
data class Plan(
    val id: String,
    val name: String,
    val description: String = "",
    val price: Int = 0,
    val priceFormatted: String = "",
    val duration: Int = 0,
    val durationDays: Int = 0,
    val features: List<String> = emptyList(),
    val type: String = "",
    val users: Int = 0
)

data class PlanStatus(
    val hasActivePlan: Boolean = false,
    val planName: String = "",
    val status: String = "",
    val message: String = "",
    val daysRemaining: Int = 0,
    val expirationDate: String? = null,
    val planDetails: PlanDetails? = null
)

data class PlanDetails(val name: String = "", val expiration: Long = 0, val users: Int = 0)

// ---- Ranking ----
data class LeaderboardEntry(
    val userId: String,
    val displayName: String,
    val position: Int,
    val totalHours: Int,
    val rankLevel: Int = 1,
    val rankName: String = "",
    val rankColor: String = "",
    val rankIcon: String = "",
    val rankImg: String = ""
)

data class RankTier(
    val level: Int,
    val name: String,
    val color: String,
    val img: String? = null,
    val minHours: Int,
    val maxHours: Int? = null
)

data class LeaderboardResponse(
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val allRanks: List<RankTier> = emptyList()
)

data class RankInfo(
    val name: String = "",
    val level: Int = 1,
    val totalHours: Int = 0,
    val totalMinutes: Int = 0,
    val progress: Int = 0,
    val hoursToNext: Int = 0,
    val isMaxRank: Boolean = false,
    val nextRank: String = "",
    val color: String = "",
    val icon: String = "",
    val img: String? = null
)

data class MyRankResponse(
    val userId: String = "",
    val displayName: String = "",
    val position: Int? = null,
    val rank: RankInfo = RankInfo()
)

data class Achievement(val title: String = "", val description: String = "", val iconUrl: String? = null)
data class UserPublicProfile(
    val userId: String,
    val displayName: String,
    val rank: RankInfo,
    val achievements: List<Achievement> = emptyList()
)

// ---- Lobby ----
data class CreateLobbyRequest(val lobbyType: String = "movie")
data class JoinLobbyRequest(val code: String)
data class LobbyTokenResponse(
    val code: String,
    val token: String,
    val lobbyType: String = "movie",
    val maxUsers: Int? = 8,
    val expiration: Long? = null
)

data class LobbyUserInfo(val user_id: String = "", val username: String = "")
data class ActiveLobby(
    val code: String,
    val creater: String = "",
    val is_owner: Boolean = false,
    val lobbyType: String = "movie",
    val userplan: String = "",
    val users: List<LobbyUserInfo> = emptyList()
)

data class ActiveLobbiesResponse(val lobbies: List<ActiveLobby> = emptyList())

// ---- Music ----
data class ArtistInfo(val id: String? = null, val name: String? = null, val image: String? = null, val verified: Boolean? = null)

data class Music(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val artistName: String? = null,
    val artistInfo: ArtistInfo? = null,
    val audioUrl: String,
    val coverImage: String? = null,
    val duration: Int? = null,
    val playCount: Int? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val categoryColor: String? = null
)

data class MusicListResponse(val musics: List<Music> = emptyList(), val success: Boolean = true, val total: Int? = null)
data class RecommendedMusicResponse(val musics: List<Music> = emptyList(), val basedOn: String = "", val success: Boolean = true)
data class MusicCategory(val id: String, val name: String, val slug: String? = null, val color: String? = null, val image: String? = null)
data class MusicCategoriesResponse(val categories: List<MusicCategory> = emptyList(), val success: Boolean = true)

data class ArtistSocialLinks(
    val spotify: String? = null,
    val instagram: String? = null,
    val telegram: String? = null,
    val twitter: String? = null,
    val soundcloud: String? = null
)

data class Artist(
    val id: String,
    val name: String,
    val englishName: String? = null,
    val bio: String? = null,
    val image: String? = null,
    val coverImage: String? = null,
    val followers: Int? = null,
    val monthlyListeners: Int? = null,
    val trackCount: Int? = null,
    val verified: Boolean? = null,
    val genres: List<String> = emptyList(),
    val socialLinks: ArtistSocialLinks? = null
)

data class ArtistsResponse(val artists: List<Artist> = emptyList(), val success: Boolean = true, val total: Int? = null)
data class ArtistResponse(val artist: Artist, val tracks: List<Music> = emptyList(), val success: Boolean = true)
data class FollowResponse(val success: Boolean = true, val isFollowing: Boolean = false)
data class FollowStatusResponse(val success: Boolean = true, val isFollowing: Boolean = false)
