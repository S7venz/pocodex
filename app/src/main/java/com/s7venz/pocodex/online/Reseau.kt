package com.s7venz.pocodex.online

import com.s7venz.pocodex.network.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Couche réseau du multijoueur, basée sur ntfy.sh (bus de messages public, sans inscription).
 * Le "topic" = le code de la partie.
 *
 * Réception en **streaming** : une seule connexion HTTP maintenue ouverte (faible latence,
 * pas de rate-limit lié au polling). L'appelant relance en cas de coupure (reconnexion).
 * Envoi : simple POST.
 */
object Reseau {

    private const val BASE = "https://ntfy.sh/"
    private val client = Network.client

    // Client dédié au streaming : aucun timeout de lecture (la connexion reste ouverte ;
    // ntfy envoie un "keepalive" périodique).
    private val clientFlux by lazy {
        Network.client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var flux: Call? = null
    private var ferme = false  // état "fermé" : empêche toute écoute orpheline après couper()

    data class Msg(val id: String, val time: Long, val payload: String)

    /** Publie un message (JSON) sur le topic. Renvoie true si la requête a réussi (sans jamais lever). */
    suspend fun publier(topic: String, payload: String): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BASE + topic).post(payload.toRequestBody()).build()
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    /**
     * Écoute le topic en streaming et appelle [onMessage] pour chaque message (dans l'ordre).
     * Bloque jusqu'à la fermeture du flux ou une erreur — à relancer côté appelant.
     * [depuis] = epoch secondes (0 = tout l'historique).
     */
    suspend fun ecouter(topic: String, depuis: Long, onMessage: suspend (Msg) -> Unit) =
        withContext(Dispatchers.IO) {
            val since = if (depuis <= 0L) "all" else depuis.toString()
            val req = Request.Builder().url("$BASE$topic/json?since=$since").build()
            val call = clientFlux.newCall(req)
            // Enregistrement atomique : si couper() est déjà passé, on annule sans écouter
            // (sinon une connexion créée pendant couper() resterait orpheline → fuite TCP).
            if (!enregistrerFlux(call)) { call.cancel(); return@withContext }
            call.execute().use { resp ->
                val source = resp.body?.source() ?: return@use
                while (!source.exhausted()) {
                    val ligne = source.readUtf8Line() ?: break
                    if (ligne.isBlank()) continue
                    runCatching {
                        val o = JSONObject(ligne)
                        if (o.optString("event") == "message") {
                            onMessage(Msg(o.optString("id"), o.optLong("time"), o.optString("message")))
                        }
                    }
                }
            }
        }

    /** Rouvre la couche réseau (à appeler à l'entrée de l'écran, avant de lancer l'écoute). */
    @Synchronized
    fun ouvrir() {
        ferme = false
    }

    /**
     * Enregistre le call de streaming de façon atomique vis-à-vis de [couper].
     * Renvoie false si la couche est déjà fermée (l'appelant doit annuler son call).
     */
    @Synchronized
    private fun enregistrerFlux(call: Call): Boolean {
        if (ferme) return false
        flux = call
        return true
    }

    /** Coupe la connexion de streaming en cours (à appeler en quittant l'écran). */
    @Synchronized
    fun couper() {
        ferme = true
        runCatching { flux?.cancel() }
        flux = null
    }
}
