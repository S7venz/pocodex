package com.example.myapplication

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.myapplication.network.Network

/**
 * Classe Application : configure Coil pour qu'il charge les images
 * avec le même client OkHttp (IPv4) que Retrofit.
 */
class PokeApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(Network.client)
            .crossfade(true)
            .build()
}
