package com.app.bebinim.data.api

import com.app.bebinim.BebinimApplication
import com.app.bebinim.data.utils.TokenManager
import kotlinx.coroutines.runBlocking

/**
 * OkHttp Authenticator — refreshes JWT on 401 exactly like the original app:
 * max 2 retries, synchronized refresh, force-logout on server rejection.
 */
class TokenAuthenticator : okhttp3.Authenticator {

    companion object {
        private const val MAX_RETRY = 2
        @Volatile
        private var isRefreshing = false
    }

    override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): okhttp3.Request? {
        val retryCount = response.request.header("X-Retry-Count")?.toIntOrNull() ?: 0
        if (retryCount >= MAX_RETRY) {
            forceLogout()
            return null
        }

        synchronized(this) {
            if (isRefreshing) {
                try { Thread.sleep(1000) } catch (_: InterruptedException) {}
            }
            isRefreshing = true

            val context = BebinimApplication.appContext ?: return null
            val tokenManager = TokenManager(context)
            val currentToken = runBlocking { tokenManager.getToken() }

            return try {
                val refreshResponse = runBlocking {
                    RetrofitClient.apiService.refreshToken(mapOf("token" to (currentToken ?: "")))
                }
                val body = refreshResponse.body()
                val newToken = body?.data
                if (refreshResponse.isSuccessful && body?.status == "success" && !newToken.isNullOrBlank()) {
                    runBlocking { tokenManager.saveToken(newToken) }
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .header("X-Retry-Count", (retryCount + 1).toString())
                        .build()
                } else {
                    forceLogout()
                    null
                }
            } catch (e: java.io.IOException) {
                // network error — do NOT log out
                null
            } catch (e: Exception) {
                forceLogout()
                null
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun forceLogout() {
        val context = BebinimApplication.appContext ?: return
        runBlocking {
            TokenManager(context).clearAll()
        }
        UserPrefsHolder.clear()
        AuthEventBus.emitLogoutRequired()
    }

    // avoids a circular dependency on UserPreferences singleton
    private object UserPrefsHolder {
        fun clear() {
            try {
                BebinimApplication.appContext?.let {
                    it.getSharedPreferences("user_preferences", android.content.Context.MODE_PRIVATE)
                        .edit().clear().apply()
                }
            } catch (_: Exception) {}
        }
    }
}
