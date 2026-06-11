package com.s7venz.pocodex

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.s7venz.pocodex.network.Network

/**
 * Classe Application : expose un contexte global (lecture des assets hors-ligne)
 * et configure Coil avec le même client OkHttp (IPv4) — utilisé pour le combat en ligne.
 */
class PokeApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(Network.client)
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: PokeApp
            private set
    }
}
