package com.app.bebinim

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.app.bebinim.data.api.RetrofitClient

class BebinimApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
    }

    /** ImageLoader with animated-GIF support — romantic movie-scene stickers animate. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(false)
            .build()

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
