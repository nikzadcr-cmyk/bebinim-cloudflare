package com.app.bebinim

import android.app.Application
import com.app.bebinim.data.api.RetrofitClient

class BebinimApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
    }

    companion object {
        @Volatile
        var appContext: Application? = null
            private set

        init {
            // set in onCreate too, this covers early access
        }
    }

    init {
        appContext = this
    }
}
