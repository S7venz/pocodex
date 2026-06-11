package com.example.myapplication.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Client OkHttp partagé par Retrofit ET Coil.
 *
 * On force l'IPv4 : l'émulateur Android a souvent une IPv6 cassée
 * (ConnectException / EHOSTUNREACH). On filtre donc les adresses IPv6.
 */
object Network {

    private val ipv4Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val toutes = Dns.SYSTEM.lookup(hostname)
            val ipv4 = toutes.filterIsInstance<Inet4Address>()
            // Si pas d'IPv4 (vrai appareil en IPv6 pur), on garde tout.
            return ipv4.ifEmpty { toutes }
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ipv4Dns)
            .build()
    }
}
