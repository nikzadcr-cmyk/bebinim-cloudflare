package com.app.hamfilm.desktop.net

import com.app.hamfilm.desktop.ActiveLobby
import com.app.hamfilm.desktop.LobbyToken
import com.app.hamfilm.desktop.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * REST client — protocol-compatible with the Android app's Retrofit client
 * (same endpoints, same X-Device-ID / X-User-Email / X-User-ID headers).
 */
object Api {

    const val BASE_URL = "https://bebinim-backend.agora-chat.workers.dev/"
    const val WS_URL = "wss://bebinim-backend.agora-chat.workers.dev/ws"

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val headersInterceptor = Interceptor { chain ->
        val builder = chain.request().newBuilder()
            .header("X-Device-ID", Store.deviceId)
        val claims = Store.session?.let { Store.decodeJwtClaims(it.token) } ?: emptyMap()
        claims["email"]?.let { builder.header("X-User-Email", it) }
        claims["real_id"]?.let { builder.header("X-User-ID", it) }
        chain.proceed(builder.build())
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(headersInterceptor)
        .build()

    sealed class ApiError : Exception() {
        class Network : ApiError()
        data class Server(val serverMessage: String) : ApiError()
    }

    private suspend fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(BASE_URL + path.removePrefix("/"))
            when (method) {
                "GET" -> builder.get()
                else -> builder.method(method, body?.toString()?.toRequestBody(JSON_TYPE))
            }
            if (!auth.isNullOrBlank()) builder.header("Authorization", "Bearer $auth")
            http.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
                if (!resp.isSuccessful && json.length() == 0) {
                    throw ApiError.Server("خطای سرور (${resp.code})")
                }
                json
            }
        } catch (e: ApiError) {
            throw e
        } catch (_: Exception) {
            throw ApiError.Network()
        }
    }

    private fun messageOf(json: JSONObject, fallback: String): String {
        val m = json.optString("message", "")
        return if (m.isNotBlank() && m != "null") m else fallback
    }

    private fun netMessage(e: Exception): String =
        if (e is ApiError.Network) "خطای شبکه. لطفاً اتصال اینترنت را بررسی کنید"
        else if (e is ApiError.Server) e.serverMessage
        else "خطای نامشخص"

    // ---------------- auth ----------------

    suspend fun login(email: String, password: String): Result<String> = try {
        val res = call(
            "POST", "api/v1/login",
            JSONObject().put("email", email.trim()).put("password", password)
        )
        if (res.optString("status") == "success" && res.optString("data", "").isNotBlank()) {
            Result.success(res.getString("data"))
        } else Result.failure(ApiError.Server(messageOf(res, "ایمیل یا رمز عبور اشتباه است")))
    } catch (e: Exception) {
        Result.failure(Exception(netMessage(e)))
    }

    suspend fun sendLoginOtp(identity: String): Result<Int?> = try {
        val res = call(
            "POST", "api/v1/login/send-otp",
            JSONObject().put("identity", identity.trim())
        )
        if (res.optString("status") == "success") {
            val d = res.optJSONObject("data")
            Result.success(d?.optInt("expires_in") ?: 120)
        } else Result.failure(ApiError.Server(messageOf(res, "ارسال کد ناموفق بود")))
    } catch (e: Exception) {
        Result.failure(Exception(netMessage(e)))
    }

    suspend fun verifyLoginOtp(identity: String, code: String): Result<String> = try {
        val res = call(
            "POST", "api/v1/login/verify-otp",
            JSONObject().put("identity", identity.trim()).put("otp_code", code)
        )
        if (res.optString("status") == "success" && res.optString("data", "").isNotBlank()) {
            Result.success(res.getString("data"))
        } else Result.failure(ApiError.Server(messageOf(res, "کد وارد شده اشتباه است")))
    } catch (e: Exception) {
        Result.failure(Exception(netMessage(e)))
    }

    data class RegisterOk(val token: String, val userId: String, val username: String)

    suspend fun register(name: String, phone: String, password: String): Result<RegisterOk> = try {
        val res = call(
            "POST", "api/v1/register",
            JSONObject()
                .put("name", name.trim())
                .put("phone_number", phone.trim())
                .put("password", password)
        )
        val d = res.optJSONObject("data")
        if (res.optString("status") == "success" && d != null && d.optString("token").isNotBlank()) {
            val user = d.optJSONObject("user")
            Result.success(
                RegisterOk(
                    token = d.getString("token"),
                    userId = user?.optString("id") ?: "",
                    username = user?.optString("username") ?: ""
                )
            )
        } else Result.failure(ApiError.Server(messageOf(res, "خطا در ثبت‌نام")))
    } catch (e: Exception) {
        Result.failure(Exception(netMessage(e)))
    }

    suspend fun refreshToken(current: String): Result<String> = try {
        val res = call("POST", "api/v1/refresh-token", JSONObject().put("token", current))
        if (res.optString("status") == "success" && res.optString("data", "").isNotBlank()) {
            Result.success(res.getString("data"))
        } else Result.failure(ApiError.Server("نشست منقضی شده — دوباره وارد شوید"))
    } catch (e: Exception) {
        Result.failure(Exception(netMessage(e)))
    }

    // ---------------- lobby ----------------

    suspend fun createLobbyToken(auth: String, lobbyType: String = "movie"): Result<LobbyToken> = try {
        val res = call(
            "POST", "api/v1/lobby/create-token",
            JSONObject().put("lobbyType", lobbyType), auth
        )
        val d = res.optJSONObject("data")
        if (res.optString("status") == "success" && d != null && d.optString("token").isNotBlank()) {
            Result.success(
                LobbyToken(
                    code = d.optString("code"),
                    token = d.getString("token"),
                    lobbyType = d.optString("lobbyType", "movie")
                )
            )
        } else Result.failure(ApiError.Server(messageOf(res, "خطا در ساخت لابی")))
    } catch (e: Exception) {
        Result.failure(Exception(netMessage(e)))
    }

    suspend fun joinLobbyToken(auth: String, code: String): Result<LobbyToken> = try {
        val res = call(
            "POST", "api/v1/lobby/join-token",
            JSONObject().put("code", code.trim().uppercase()), auth
        )
        val d = res.optJSONObject("data")
        if (res.optString("status") == "success" && d != null && d.optString("token").isNotBlank()) {
            Result.success(
                LobbyToken(
                    code = d.optString("code"),
                    token = d.getString("token"),
                    lobbyType = d.optString("lobbyType", "movie")
                )
            )
        } else Result.failure(ApiError.Server(messageOf(res, "خطا در ورود به لابی")))
    } catch (e: Exception) {
        Result.failure(Exception(netMessage(e)))
    }

    suspend fun activeLobbies(auth: String): Result<List<ActiveLobby>> = try {
        val res = call("GET", "api/v1/lobby/active", auth = auth)
        val d = res.optJSONObject("data")
        val arr: JSONArray? = d?.optJSONArray("lobbies")
        if (res.optString("status") == "success" && arr != null) {
            val list = mutableListOf<ActiveLobby>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    ActiveLobby(
                        code = o.optString("code"),
                        creater = o.optString("creater"),
                        lobbyType = o.optString("lobbyType", "movie"),
                        usersCount = o.optJSONArray("users")?.length() ?: 0
                    )
                )
            }
            Result.success(list)
        } else Result.failure(ApiError.Server(messageOf(res, "خطا در دریافت لیست لابی‌ها")))
    } catch (e: Exception) {
        Result.failure(Exception(netMessage(e)))
    }
}
