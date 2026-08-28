package com.app.bebinim.data.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

/**
 * JWT + user info storage (same keys as the original app: auth_prefs / jwt_token / user_id / ...)
 */
class TokenManager(private val context: Context) {

    companion object {
        private val KEY_JWT = stringPreferencesKey("jwt_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_NAME = stringPreferencesKey("name")
        private val KEY_EMAIL = stringPreferencesKey("email")
    }

    val tokenFlow: Flow<String?> = context.authDataStore.data.map { it[KEY_JWT] }

    suspend fun getToken(): String? = context.authDataStore.data.first()[KEY_JWT]

    suspend fun saveToken(token: String) {
        context.authDataStore.edit { it[KEY_JWT] = token }
    }

    suspend fun saveUserInfo(id: String, username: String, name: String?, email: String?) {
        context.authDataStore.edit {
            it[KEY_USER_ID] = id
            it[KEY_USERNAME] = username
            it[KEY_NAME] = name ?: ""
            it[KEY_EMAIL] = email ?: ""
        }
    }

    val userIdFlow: Flow<String?> = context.authDataStore.data.map { it[KEY_USER_ID] }
    val usernameFlow: Flow<String?> = context.authDataStore.data.map { it[KEY_USERNAME] }
    val nameFlow: Flow<String?> = context.authDataStore.data.map { it[KEY_NAME] }
    val emailFlow: Flow<String?> = context.authDataStore.data.map { it[KEY_EMAIL] }

    suspend fun clearAll() {
        context.authDataStore.edit { it.clear() }
    }
}
