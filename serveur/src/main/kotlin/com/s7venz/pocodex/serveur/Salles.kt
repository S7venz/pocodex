package com.s7venz.pocodex.serveur

import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * Salles de combat en ligne — relai « host-authoritative ».
 *
 * Aucun moteur de jeu côté serveur : l'hôte (créateur de la salle) fait tourner
 * le combat et diffuse l'état (`etat`) ; l'invité envoie son coup (`act`). Le
 * serveur ne fait que **relayer** ces deux messages d'un joueur à l'autre, gérer
 * l'appariement par code, et clôturer la partie (calcul de l'ELO, persistance).
 *
 * Registre 100 % en mémoire (les salles sont éphémères) ; l'historique des
 * parties et les scores ELO, eux, sont persistés en base.
 */
object Salles {

    private val log = LoggerFactory.getLogger("Salles")

    /** Alphabet des codes de salle : sans 0/O/1/I (lisibilité). */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Longueur d'un code de salle. */
    private const val LONGUEUR_CODE = 5

    /** Coefficient K du calcul ELO. */
    private const val K_ELO = 32.0

    /** Sérialiseur JSON partagé. */
    private val json = Json { ignoreUnknownKeys = true }

    /** Un joueur connecté : sa session WS, son compte, le Pokémon engagé. */
    class SessionJoueur(
        val compte: Bdd.Compte,
        val ws: DefaultWebSocketSession,
        @Volatile var pokemon: Int = 0,
    )

    /** Une salle de combat : l'hôte, l'invité (s'il a rejoint), l'id de partie. */
    class Salle(
        val code: String,
        val hote: SessionJoueur,
        val partieId: Long,
    ) {
        @Volatile var invite: SessionJoueur? = null

        /** Vrai une fois la partie close (évite un double calcul ELO). */
        @Volatile var terminee: Boolean = false
    }

    /** Registre code → salle (concurrent : plusieurs sessions y accèdent). */
    private val salles = ConcurrentHashMap<String, Salle>()

    /**
     * Salle active d'un compte (id compte → code).
     * Garantit qu'un jeton/compte ne tient qu'une seule salle à la fois.
     */
    private val salleParCompte = ConcurrentHashMap<Long, String>()

    /** Génère un code de salle unique (non déjà utilisé). */
    private fun nouveauCode(): String {
        var code: String
        do {
            code = (1..LONGUEUR_CODE).map { ALPHABET.random() }.joinToString("")
        } while (salles.containsKey(code))
        return code
    }

    // ----------------------------------------------------------------------
    //  Envoi de messages JSON
    // ----------------------------------------------------------------------

    private suspend fun envoyer(session: DefaultWebSocketSession, objet: JsonObject) {
        session.send(Frame.Text(json.encodeToString(JsonObject.serializer(), objet)))
    }

    private suspend fun erreur(session: DefaultWebSocketSession, message: String) {
        envoyer(session, buildJsonObject {
            put("k", "erreur")
            put("message", message)
        })
    }

    // ----------------------------------------------------------------------
    //  Calcul ELO
    // ----------------------------------------------------------------------

    /**
     * Score attendu du joueur A face à B : 1/(1+10^((eloB−eloA)/400)).
     */
    private fun attendu(eloA: Int, eloB: Int): Double =
        1.0 / (1.0 + 10.0.pow((eloB - eloA) / 400.0))

    /** Nouvel ELO après un résultat (scoreReel = 1 victoire, 0 défaite). */
    private fun nouvelElo(elo: Int, eloAdversaire: Int, scoreReel: Double): Int =
        Math.round(elo + K_ELO * (scoreReel - attendu(elo, eloAdversaire))).toInt()

    /**
     * Clôt une salle : calcule l'ELO des deux joueurs, met à jour la base
     * (comptes + partie), notifie chaque joueur via `{"k":"classement",...}`
     * et retire la salle du registre. Idempotent grâce à [Salle.terminee].
     *
     * @param vainqueurEstHote vrai si l'hôte a gagné.
     */
    private suspend fun cloturer(salle: Salle, vainqueurEstHote: Boolean) {
        // Section critique : un seul calcul ELO par salle.
        synchronized(salle) {
            if (salle.terminee) return
            salle.terminee = true
        }
        val invite = salle.invite

        // Recharge les ELO à jour depuis la base (au cas où d'autres parties
        // auraient eu lieu entre-temps — robustesse).
        val hoteCompte = Bdd.compteParId(salle.hote.compte.id) ?: salle.hote.compte
        val inviteCompte = invite?.let { Bdd.compteParId(it.compte.id) ?: it.compte }

        if (inviteCompte != null) {
            val eloHote = hoteCompte.elo
            val eloInvite = inviteCompte.elo
            val scoreHote = if (vainqueurEstHote) 1.0 else 0.0
            val nouvelHote = nouvelElo(eloHote, eloInvite, scoreHote)
            val nouvelInvite = nouvelElo(eloInvite, eloHote, 1.0 - scoreHote)

            // Persistance : ELO + bilan de chaque joueur, vainqueur de la partie.
            Bdd.majResultat(hoteCompte.id, nouvelHote, vainqueurEstHote)
            Bdd.majResultat(inviteCompte.id, nouvelInvite, !vainqueurEstHote)
            val vainqueurId = if (vainqueurEstHote) hoteCompte.id else inviteCompte.id
            Bdd.terminerPartie(salle.partieId, vainqueurId)

            val cote = if (vainqueurEstHote) "hote" else "invite"

            // Notifie chaque joueur de son nouveau score et du delta.
            runCatching {
                envoyer(salle.hote.ws, buildJsonObject {
                    put("k", "classement")
                    put("elo", nouvelHote)
                    put("delta", nouvelHote - eloHote)
                    put("vainqueur", cote)
                })
            }
            runCatching {
                envoyer(invite.ws, buildJsonObject {
                    put("k", "classement")
                    put("elo", nouvelInvite)
                    put("delta", nouvelInvite - eloInvite)
                    put("vainqueur", cote)
                })
            }
        } else {
            // Partie jamais commencée (pas d'invité) : on clôt sans ELO.
            Bdd.terminerPartie(salle.partieId, hoteCompte.id)
        }

        fermerSalle(salle)
    }

    /** Retire la salle du registre et libère les compteurs par compte. */
    private fun fermerSalle(salle: Salle) {
        salles.remove(salle.code)
        salleParCompte.remove(salle.hote.compte.id, salle.code)
        salle.invite?.let { salleParCompte.remove(it.compte.id, salle.code) }
    }

    // ----------------------------------------------------------------------
    //  Boucle de session WebSocket
    // ----------------------------------------------------------------------

    /**
     * Gère une session WebSocket authentifiée pour [compte].
     * Lit les messages, met à jour/relaie selon le champ discriminant `k`.
     */
    suspend fun gererSession(compte: Bdd.Compte, ws: DefaultWebSocketSession) {
        val joueur = SessionJoueur(compte, ws)
        var salle: Salle? = null
        var estHote = false

        try {
            for (frame in ws.incoming) {
                if (frame !is Frame.Text) continue
                val texte = frame.readText()
                val objet = runCatching { json.parseToJsonElement(texte).jsonObject }.getOrNull()
                    ?: continue
                val k = objet["k"]?.jsonPrimitive?.content ?: continue

                when (k) {
                    "creer" -> {
                        if (salleParCompte.containsKey(compte.id)) {
                            erreur(ws, "Vous avez déjà une salle active")
                            continue
                        }
                        val code = nouveauCode()
                        val partieId = Bdd.creerPartie(code, compte.id)
                        val nouvelle = Salle(code, joueur, partieId)
                        salles[code] = nouvelle
                        salleParCompte[compte.id] = code
                        salle = nouvelle
                        estHote = true
                        envoyer(ws, buildJsonObject {
                            put("k", "salle")
                            put("code", code)
                        })
                    }

                    "rejoindre" -> {
                        if (salleParCompte.containsKey(compte.id)) {
                            erreur(ws, "Vous avez déjà une salle active")
                            continue
                        }
                        val code = objet["code"]?.jsonPrimitive?.content?.uppercase()
                        val cible = code?.let { salles[it] }
                        if (cible == null) {
                            erreur(ws, "Salle introuvable")
                            continue
                        }
                        if (cible.invite != null) {
                            erreur(ws, "Salle déjà pleine")
                            continue
                        }
                        joueur.pokemon = objet["pokemon"]?.jsonPrimitive?.intOrNull ?: 0
                        cible.invite = joueur
                        salleParCompte[compte.id] = cible.code
                        Bdd.majInvite(cible.partieId, compte.id)
                        salle = cible
                        estHote = false

                        // Notifie l'hôte de l'arrivée + transmet le Pokémon engagé.
                        envoyer(cible.hote.ws, buildJsonObject {
                            put("k", "join")
                            put("id", joueur.pokemon)
                            put("pseudo", compte.pseudo)
                            put("elo", compte.elo)
                        })
                        // Donne à l'invité les infos de l'adversaire (l'hôte).
                        envoyer(ws, buildJsonObject {
                            put("k", "infos")
                            put("adversaire", buildJsonObject {
                                put("pseudo", cible.hote.compte.pseudo)
                                put("elo", cible.hote.compte.elo)
                            })
                        })
                    }

                    "etat" -> {
                        // Hôte → invité : on relaie l'objet tel quel.
                        val courante = salle
                        if (estHote && courante != null) {
                            courante.invite?.let { runCatching { envoyer(it.ws, objet) } }
                        }
                    }

                    "act" -> {
                        // Invité → hôte : on relaie l'objet tel quel.
                        val courante = salle
                        if (!estHote && courante != null) {
                            runCatching { envoyer(courante.hote.ws, objet) }
                        }
                    }

                    "fin" -> {
                        // Seul l'hôte (autorité du combat) peut clôturer.
                        val courante = salle
                        if (estHote && courante != null) {
                            val vainqueurEstHote =
                                objet["vainqueurEstHote"]?.jsonPrimitive?.booleanOrNull ?: true
                            cloturer(courante, vainqueurEstHote)
                            salle = null
                        }
                    }

                    else -> {
                        // `k` inconnu : ignoré (robustesse).
                    }
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            // Fermeture normale du canal : rien de spécial.
        } catch (e: Exception) {
            log.warn("Erreur de session WebSocket (compte ${compte.id})", e)
        } finally {
            gererDeconnexion(salle, joueur, estHote)
        }
    }

    /**
     * À la déconnexion d'un joueur : prévient l'autre (`{"k":"deco"}`) et, si la
     * partie avait commencé (invité présent), traite le déconnecté comme FORFAIT
     * (même calcul ELO qu'une fin normale).
     */
    private suspend fun gererDeconnexion(
        salle: Salle?,
        joueur: SessionJoueur,
        estHote: Boolean,
    ) {
        if (salle == null) return
        if (salle.terminee) {
            // Déjà clôturée (fin normale) : juste libérer si besoin.
            fermerSalle(salle)
            return
        }

        val invite = salle.invite
        val partieCommencee = invite != null

        // L'autre joueur (celui qui reste).
        val autre: SessionJoueur? = if (estHote) invite else salle.hote

        // Prévient l'autre de la déconnexion.
        autre?.let { runCatching { envoyer(it.ws, buildJsonObject { put("k", "deco") }) } }

        if (partieCommencee) {
            // Le déconnecté est forfait : le vainqueur est l'autre.
            // Si l'hôte se déconnecte → l'invité gagne (vainqueurEstHote = false).
            // Si l'invité se déconnecte → l'hôte gagne (vainqueurEstHote = true).
            cloturer(salle, vainqueurEstHote = !estHote)
        } else {
            // Personne n'a rejoint : on retire simplement la salle.
            fermerSalle(salle)
        }
    }
}

/** Salle de combat en WebSocket : authentifie via le jeton avant d'accepter. */
fun Route.routesSalles() {
    webSocket("/ws") {
        // Authentification AVANT d'accepter durablement : jeton en paramètre.
        val jeton = call.request.queryParameters["jeton"]
        val compte = Comptes.parJeton(jeton)
        if (compte == null) {
            close(CloseReason(4401, "Jeton invalide ou expiré"))
            return@webSocket
        }
        Salles.gererSession(compte, this)
    }
}

