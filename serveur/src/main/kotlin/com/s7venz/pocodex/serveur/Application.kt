package com.s7venz.pocodex.serveur

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import kotlinx.serialization.Serializable
import java.time.Duration

/** Réponse de la sonde de santé : {"ok":true,"version":"1.0"}. */
@Serializable
private data class Sante(val ok: Boolean = true, val version: String = "1.0")

/**
 * Point d'entrée du serveur PoCodex.
 *
 * Lit la configuration depuis l'environnement :
 *  - PORT : port d'écoute (défaut 8080) ;
 *  - BDD  : chemin du fichier SQLite (défaut "pocodex-serveur.db").
 *
 * Démarre un serveur Netty et installe les modules applicatifs.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val cheminBdd = System.getenv("BDD") ?: "pocodex-serveur.db"

    // Ouverture de la base (création des tables si nécessaire) avant d'écouter.
    Bdd.ouvrir(cheminBdd)

    embeddedServer(Netty, port = port) {
        module()
    }.start(wait = true)
}

/**
 * Module applicatif Ktor : branche les plugins (sérialisation JSON, WebSockets,
 * journalisation des appels) et toutes les routes (REST + WebSocket).
 *
 * Isolé dans une fonction d'extension pour être réutilisé tel quel par les tests.
 */
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(WebSockets) {
        // Ping régulier pour garder les connexions WebSocket vivantes.
        pingPeriod = Duration.ofSeconds(20)
    }
    install(CallLogging)

    routing {
        // Sonde de santé : permet de vérifier que le serveur répond.
        get("/api/sante") {
            call.respond(Sante())
        }

        // Routes REST des comptes (inscription / connexion / profil).
        routesComptes()

        // Salles de combat en WebSocket (relai host-authoritative + ELO).
        routesSalles()
    }
}
