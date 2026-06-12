package com.s7venz.pocodex.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Client OkHttp partagé (REST + WebSocket du serveur PoCodex, et Coil).
 *
 * Résolution DNS résiliente :
 *  1) résolveur système, filtré IPv4 et borné à 2 s (instantané sur un vrai téléphone ;
 *     l'émulateur traîne ou échoue parfois — UnknownHostException) ;
 *  2) repli en DNS-over-HTTPS (Cloudflare) si le système échoue ou expire.
 *
 * Sur un vrai téléphone, le DNS système répond tout de suite : le repli DoH ne sert jamais.
 */
object Network {

    // Client minimal pour amorcer le DoH (connexion par IP directe, sans DNS).
    private val bootstrap by lazy { OkHttpClient.Builder().build() }
    private val pool = Executors.newCachedThreadPool()

    private val doh by lazy {
        DnsOverHttps.Builder()
            .client(bootstrap)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .includeIPv6(false)
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
            )
            .build()
    }

    private fun ipv4(addrs: List<InetAddress>): List<InetAddress> =
        addrs.filterIsInstance<Inet4Address>().ifEmpty { addrs }

    private val dnsResilient = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            // 1) DNS système, borné à 2 s
            runCatching {
                pool.submit<List<InetAddress>> { Dns.SYSTEM.lookup(hostname) }.get(2, TimeUnit.SECONDS)
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return ipv4(it) }
            // 2) Repli DNS-over-HTTPS (émulateur au DNS cassé)
            runCatching { doh.lookup(hostname) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return ipv4(it) }
            // 3) Dernier recours (lève UnknownHostException si vraiment rien)
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dnsResilient)
            .build()
    }
}
