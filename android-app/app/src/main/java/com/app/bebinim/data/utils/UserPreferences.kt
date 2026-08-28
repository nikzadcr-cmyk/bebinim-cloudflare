package com.app.bebinim.data.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences layer kept for compatibility with the original app design
 * (user_preferences / jwt_token / user_id / user_email).
 * Decodes the JWT payload to extract real_id + email claims.
 */
class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_JWT = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
    }

    fun saveAuthToken(token: String) {
        prefs.edit().putString(KEY_JWT, token).apply()
        try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))
                val json = org.json.JSONObject(payload)
                json.optString("real_id").takeIf { it.isNotBlank() }?.let {
                    prefs.edit().putString(KEY_USER_ID, it).apply()
                }
                json.optString("email").takeIf { it.isNotBlank() && it != "null" }?.let {
                    prefs.edit().putString(KEY_USER_EMAIL, it).apply()
                }
            }
        } catch (_: Exception) {
            // payload not decodable — ignore
        }
    }

    fun getToken(): String? = prefs.getString(KEY_JWT, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    val isUserLoggedIn: Boolean
        get() = !getUserEmail().isNullOrBlank()

    fun clearUser() {
        prefs.edit().clear().apply()
    }
}
