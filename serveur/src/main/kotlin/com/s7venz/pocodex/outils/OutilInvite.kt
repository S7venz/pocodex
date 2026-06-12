package com.s7venz.pocodex.outils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.URLBuilder
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Simulateur d'invité pour les tests de bout en bout du combat en ligne.
 *
 * Se connecte au WebSocket du serveur, rejoint une salle existante (par code) et
 * affiche tout ce qu'il reçoit. À chaque état où c'est son tour (`tour=="guest"`),
 * il joue automatiquement `{"k":"act","seq":<seq>,"move":0}` deux secondes plus tard.
 *
 * Usage :
 *   ./gradlew :serveur:simulerInvite --args="ws://localhost:8080 <jeton> <code> <idPokemon>"
 */
fun main(args: Array<String>) {
    if (args.size < 4) {
        println("Usage : simulerInvite <urlServeur> <jeton> <code> <idPokemon>")
        println("Exemple : simulerInvite ws://localhost:8080 abc123... ABC12 25")
        return
    }
    val urlServeur = args[0]
    val jeton = args[1]
    val code = args[2]
    val idPokemon = args[3].toIntOrNull() ?: 25

    val json = Json { ignoreUnknownKeys = true }
    val client = HttpClient(CIO) { install(WebSockets) }

    // Construit l'URL ws://host:port/ws?jeton=...
    val cible = URLBuilder("$urlServeur/ws").apply {
        parameters.append("jeton", jeton)
    }.buildString()

    println("[invité] Connexion à $cible …")

    runBlocking {
        try {
            client.webSocket(cible) {
            // Rejoint la salle avec le Pokémon choisi.
            envoyer(buildJsonObject {
                put("k", "rejoindre")
                put("code", code)
                put("pokemon", idPokemon)
            }.toString())
            println("[invité] → rejoindre $code avec le Pokémon #$idPokemon")

            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val texte = frame.readText()
                println("[invité] ← $texte")

                val objet = runCatching { json.parseToJsonElement(texte).jsonObject }.getOrNull()
                    ?: continue
                val k = objet["k"]?.jsonPrimitive?.content

                when (k) {
                    "etat" -> {
                        val tour = objet["tour"]?.jsonPrimitive?.content
                        val seq = objet["seq"]?.jsonPrimitive?.intOrNull ?: 0
                        if (tour == "guest") {
                            // Joue automatiquement après 2 s.
                            launch {
                                delay(2000)
                                val act = buildJsonObject {
                                    put("k", "act")
                                    put("seq", seq)
                                    put("move", 0)
                                }.toString()
                                println("[invité] → act (seq=$seq, move=0)")
                                envoyer(act)
                            }
                        }
                    }
                    "classement", "deco" -> {
                        println("[invité] Fin de partie : $texte")
                    }
                }
            }
            }
        } catch (e: Exception) {
            // Le serveur a fermé la connexion (p. ex. jeton invalide → code 4401),
            // ou le canal a été coupé : on l'affiche proprement sans pile d'appels.
            println("[invité] Connexion fermée par le serveur (${e.message ?: e::class.simpleName}).")
        }
    }
    client.close()
}

/** Petit raccourci pour envoyer un message texte. */
private suspend fun io.ktor.websocket.WebSocketSession.envoyer(texte: String) {
    send(Frame.Text(texte))
}
