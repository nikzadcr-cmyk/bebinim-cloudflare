package com.app.hamfilm.desktop

import com.google.gson.Gson
import java.io.File
import java.util.Base64
import java.util.UUID

/** Persistent app state: ~/.hamfilm/{device-id, session.json} */
object Store {

    private val gson = Gson()

    val dir: File = File(System.getProperty("user.home"), ".hamfilm").apply { mkdirs() }

    private val deviceFile = File(dir, "device-id")
    private val sessionFile = File(dir, "session.json")

    /** Stable per-install device id (mirrors Android's X-Device-ID header). */
    val deviceId: String by lazy {
        try {
            if (deviceFile.isFile && deviceFile.readText().isNotBlank()) {
                deviceFile.readText().trim()
            } else {
                val id = UUID.randomUUID().toString()
                deviceFile.writeText(id)
                id
            }
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
    }

    var session: SessionUser? = null
        private set

    init {
        loadSession()
    }

    fun saveSession(user: SessionUser) {
        session = user
        try {
            sessionFile.writeText(gson.toJson(user))
        } catch (_: Exception) {
        }
    }

    fun clearSession() {
        session = null
        try {
            sessionFile.delete()
        } catch (_: Exception) {
        }
    }

    private fun loadSession() {
        try {
            if (sessionFile.isFile) {
                session = gson.fromJson(sessionFile.readText(), SessionUser::class.java)
            }
        } catch (_: Exception) {
            session = null
        }
    }

    /** Decode JWT payload claims (real_id / email / username) — mirrors UserPreferences.kt. */
    fun decodeJwtClaims(token: String): Map<String, String> {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return emptyMap()
            val payload = String(
                Base64.getUrlDecoder().decode(
                    parts[1].replace('-', '+').replace('_', '/')
                        .let { it + "=".repeat((4 - it.length % 4) % 4) }
                )
            )
            val json = org.json.JSONObject(payload)
            val out = mutableMapOf<String, String>()
            for (key in listOf("real_id", "email", "username", "name", "id", "sub")) {
                val v = json.optString(key, "")
                if (v.isNotBlank() && v != "null") out[key] = v
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
