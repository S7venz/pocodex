package com.s7venz.pocodex.online

import android.os.Handler
import android.os.Looper
import com.s7venz.pocodex.network.Network
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Client WebSocket du combat en ligne, branché sur NOTRE serveur PoCodex
 * (remplace ntfy.sh). Réutilise le client OkHttp partagé ([Network.client],
 * DNS IPv4 + repli DoH). Le contrat des messages est décrit dans
 * `serveur/PROTOCOLE.md` (section WebSocket).
 *
 * - Connexion : `ws(s)://<hote>/ws?jeton=<jeton>` (l'URL serveur http(s) est
 *   convertie en ws(s)).
 * - Les callbacks ([surMessage], [surFermeture]) sont **toujours** délivrés sur
 *   le thread principal (Handler du Looper principal) → manipulation directe
 *   de l'UI sans repasser par runOnUiThread.
 * - **Pas de reconnexion automatique** : le serveur traite une déconnexion en
 *   pleine partie comme un FORFAIT. Sur échec ou fermeture, on remonte
 *   [surFermeture] et l'activité quitte proprement.
 */
object ClientWs {

    private val principal = Handler(Looper.getMainLooper())

    @Volatile private var ws: WebSocket? = null

    /** Empêche un double appel de [surFermeture] (échec + fermeture). */
    @Volatile private var fermeNotifiee = false

    /**
     * Ouvre une connexion WebSocket vers [serveur] (URL REST http(s)) authentifiée
     * par [jeton]. [surMessage] reçoit chaque message JSON ; [surFermeture] est
     * appelé une seule fois quand la connexion se termine (raison lisible).
     */
    fun connecter(
        serveur: String,
        jeton: String,
        surMessage: (JSONObject) -> Unit,
        surFermeture: (raison: String) -> Unit,
    ) {
        fermer() // repart propre si une connexion précédente traînait
        fermeNotifiee = false

        val url = versWs(serveur) + "/ws?jeton=" + jeton
        val req = Request.Builder().url(url).build()

        ws = Network.client.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val o = runCatching { JSONObject(text) }.getOrNull() ?: return
                principal.post { surMessage(o) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // Réponse polie : on accuse la fermeture demandée par le serveur.
                runCatching { webSocket.close(1000, null) }
                notifierFermeture(reason.ifBlank { "Connexion fermée" }, surFermeture)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                notifierFermeture(reason.ifBlank { "Connexion fermée" }, surFermeture)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                notifierFermeture(t.message ?: "Connexion perdue", surFermeture)
            }
        })
    }

    /** Envoie un objet JSON sur la connexion (ignoré si la connexion est tombée). */
    fun envoyer(objet: JSONObject) {
        ws?.send(objet.toString())
    }

    /** Ferme la connexion proprement (à appeler en quittant l'écran). */
    fun fermer() {
        fermeNotifiee = true // une fermeture volontaire ne doit pas alerter l'activité
        runCatching { ws?.close(1000, null) }
        ws = null
    }

    /** Remonte la fermeture au plus une fois, sur le thread principal. */
    private fun notifierFermeture(raison: String, surFermeture: (String) -> Unit) {
        if (fermeNotifiee) return
        fermeNotifiee = true
        ws = null
        principal.post { surFermeture(raison) }
    }

    /** http://x → ws://x ; https://x → wss://x ; retire le `/` final éventuel. */
    private fun versWs(serveur: String): String {
        val base = serveur.trim().trimEnd('/')
        return when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> base
        }
    }
}
