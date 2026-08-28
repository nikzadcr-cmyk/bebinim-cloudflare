package com.app.bebinim.data.api

import android.content.Context
import com.app.bebinim.BuildConfig
import com.app.bebinim.data.utils.DeviceUtils
import com.app.bebinim.data.utils.TokenManager
import com.app.bebinim.data.utils.UserPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Retrofit singleton — points to the Cloudflare backend. */
object RetrofitClient {

    private const val BASE_URL = BuildConfig.BASE_URL

    var appContext: Context? = null
        private set

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private val customGson: Gson = GsonBuilder().setLenient().create()

    private val headersInterceptor = Interceptor { chain ->
        val ctx = appContext
        val builder = chain.request().newBuilder().apply {
            if (ctx != null) {
                header("X-Device-ID", DeviceUtils.getDeviceId(ctx))
                val prefs = UserPreferences(ctx)
                prefs.getUserEmail()?.let { header("X-User-Email", it) }
                prefs.getUserId()?.let { header("X-User-ID", it) }
            }
        }
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(headersInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(TokenAuthenticator())
            .build()
    }

    val apiService: BebinimApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .build()
            .create(BebinimApiService::class.java)
    }

    val lobbyApiService: LobbyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .build()
            .create(LobbyApiService::class.java)
    }
}
