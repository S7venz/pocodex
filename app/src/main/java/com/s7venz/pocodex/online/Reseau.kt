package com.s7venz.pocodex.online

import com.s7venz.pocodex.network.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Couche réseau du multijoueur en ligne, basée sur ntfy.sh (bus de messages public,
 * sans inscription). Le "topic" = le code de la partie. On réutilise le client OkHttp
 * IPv4 (compatible émulateur).
 */
object Reseau {

    private const val BASE = "https://ntfy.sh/"
    private val client = Network.client

    /** Publie un message (JSON) sur le topic. */
    suspend fun publier(topic: String, payload: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BASE + topic).post(payload.toRequestBody()).build()
        runCatching { client.newCall(req).execute().use { } }
        Unit
    }

    data class Msg(val id: String, val time: Long, val payload: String)

    /** Lit les messages du topic publiés depuis `depuis` (epoch secondes ; 0 = tout). */
    suspend fun lire(topic: String, depuis: Long): List<Msg> = withContext(Dispatchers.IO) {
        val since = if (depuis <= 0L) "all" else depuis.toString()
        val req = Request.Builder().url("$BASE$topic/json?poll=1&since=$since").build()
        val out = mutableListOf<Msg>()
        runCatching {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@use
                for (ligne in body.lineSequence()) {
                    if (ligne.isBlank()) continue
                    runCatching {
                        val o = JSONObject(ligne)
                        if (o.optString("event") == "message") {
                            out.add(Msg(o.optString("id"), o.optLong("time"), o.optString("message")))
                        }
                    }
                }
            }
        }
        out
    }
}
