package com.app.bebinim.data.utils

import android.content.Context
import android.provider.Settings

object DeviceUtils {
    @Volatile
    private var cached: String? = null

    fun getDeviceId(context: Context): String {
        cached?.let { return it }
        val id = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
        cached = id
        return id
    }
}
