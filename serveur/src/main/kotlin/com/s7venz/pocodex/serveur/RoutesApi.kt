package com.s7venz.pocodex.serveur

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

// ----------------------------------------------------------------------------
//  Corps des requêtes / réponses (sérialisés en JSON)
// ----------------------------------------------------------------------------

/** Corps reçu pour l'inscription et la connexion. */
@Serializable
data class IdentifiantsDto(val pseudo: String = "", val mdp: String = "")

/** Profil renvoyé après inscription / connexion (avec le jeton de session). */
@Serializable
data class SessionDto(
    val jeton: String,
    val pseudo: String,
    val elo: Int,
    val victoires: Int,
    val defaites: Int,
)

/** Profil renvoyé par /api/moi (sans le jeton). */
@Serializable
data class ProfilDto(
    val pseudo: String,
    val elo: Int,
    val victoires: Int,
    val defaites: Int,
)

/** Corps d'erreur générique : {"erreur":"..."} (message en français). */
@Serializable
data class ErreurDto(val erreur: String)

// ----------------------------------------------------------------------------
//  Routes
// ----------------------------------------------------------------------------

/**
 * Récupère le compte authentifié à partir de l'en-tête
 * `Authorization: Bearer <jeton>`, ou null si absent/invalide/expiré.
 *
 * Helper réutilisable par toutes les routes protégées.
 */
fun compteDuJeton(call: ApplicationCall): Bdd.Compte? {
    val entete = call.request.headers["Authorization"] ?: return null
    val jeton = entete.removePrefix("Bearer ").trim()
    return Comptes.parJeton(jeton)
}

/** Routes REST des comptes : inscription, connexion, profil. */
fun Route.routesComptes() {

    // Création de compte. 409 si le pseudo est déjà pris.
    post("/api/inscription") {
        val corps = call.receive<IdentifiantsDto>()
        when (val r = Comptes.inscrire(corps.pseudo, corps.mdp)) {
            is Comptes.Resultat.Ok -> call.respond(
                SessionDto(r.jeton, r.compte.pseudo, r.compte.elo, r.compte.victoires, r.compte.defaites)
            )
            is Comptes.Resultat.Erreur ->
                call.respond(HttpStatusCode.fromValue(r.code), ErreurDto(r.message))
        }
    }

    // Connexion. 401 si pseudo inconnu ou mot de passe erroné.
    post("/api/connexion") {
        val corps = call.receive<IdentifiantsDto>()
        when (val r = Comptes.connecter(corps.pseudo, corps.mdp)) {
            is Comptes.Resultat.Ok -> call.respond(
                SessionDto(r.jeton, r.compte.pseudo, r.compte.elo, r.compte.victoires, r.compte.defaites)
            )
            is Comptes.Resultat.Erreur ->
                call.respond(HttpStatusCode.fromValue(r.code), ErreurDto(r.message))
        }
    }

    // Profil du joueur connecté. 401 si jeton absent/invalide/expiré.
    get("/api/moi") {
        val compte = compteDuJeton(call)
        if (compte == null) {
            call.respond(HttpStatusCode.Unauthorized, ErreurDto("Jeton invalide ou expiré"))
        } else {
            call.respond(ProfilDto(compte.pseudo, compte.elo, compte.victoires, compte.defaites))
        }
    }
}
