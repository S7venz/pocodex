package com.s7venz.pocodex.serveur

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.SecureRandom

/**
 * Logique métier des comptes : validation, hachage du mot de passe (bcrypt),
 * création de session (jeton aléatoire), authentification par jeton.
 *
 * Indépendant de Ktor pour être testable et réutilisable (notamment par
 * l'authentification WebSocket des salles de combat).
 */
object Comptes {

    /** Durée de validité d'une session : 30 jours (en millisecondes). */
    private const val DUREE_SESSION_MS = 30L * 24 * 60 * 60 * 1000

    /** Coût bcrypt (cf. consignes : 12 tours). */
    private const val COUT_BCRYPT = 12

    /** Pseudo : 3 à 16 caractères alphanumériques ou tiret bas. */
    private val MOTIF_PSEUDO = Regex("^[A-Za-z0-9_]{3,16}$")

    /** Longueur minimale du mot de passe. */
    private const val MDP_MIN = 6

    private val aleatoire = SecureRandom()

    /** Résultat d'une opération d'authentification (inscription ou connexion). */
    sealed interface Resultat {
        /** Succès : la session est créée, on renvoie le compte et le jeton. */
        data class Ok(val compte: Bdd.Compte, val jeton: String) : Resultat

        /** Échec : message d'erreur (en français) et code HTTP à renvoyer. */
        data class Erreur(val message: String, val code: Int) : Resultat
    }

    /** Validation du pseudo : null si valide, sinon le message d'erreur. */
    fun erreurPseudo(pseudo: String): String? =
        if (MOTIF_PSEUDO.matches(pseudo)) null
        else "Pseudo invalide (3 à 16 caractères : lettres, chiffres ou _)"

    /** Validation du mot de passe : null si valide, sinon le message d'erreur. */
    fun erreurMdp(mdp: String): String? =
        if (mdp.length >= MDP_MIN) null
        else "Mot de passe trop court (au moins $MDP_MIN caractères)"

    /**
     * Inscrit un nouveau joueur. Valide les entrées, vérifie l'unicité du
     * pseudo, hache le mot de passe et ouvre une session.
     */
    fun inscrire(pseudo: String, mdp: String): Resultat {
        erreurPseudo(pseudo)?.let { return Resultat.Erreur(it, 400) }
        erreurMdp(mdp)?.let { return Resultat.Erreur(it, 400) }
        if (Bdd.pseudoExiste(pseudo)) {
            return Resultat.Erreur("Pseudo déjà pris", 409)
        }
        val hash = BCrypt.withDefaults().hashToString(COUT_BCRYPT, mdp.toCharArray())
        val id = Bdd.creerCompte(pseudo, hash)
        val compte = Bdd.compteParId(id)!!
        val jeton = ouvrirSession(id)
        return Resultat.Ok(compte, jeton)
    }

    /**
     * Connecte un joueur existant : vérifie le mot de passe (bcrypt) et ouvre
     * une nouvelle session. Message volontairement générique en cas d'échec.
     */
    fun connecter(pseudo: String, mdp: String): Resultat {
        val compte = Bdd.compteParPseudo(pseudo)
            ?: return Resultat.Erreur("Pseudo ou mot de passe incorrect", 401)
        val verif = BCrypt.verifyer().verify(mdp.toCharArray(), compte.mdpHash)
        if (!verif.verified) {
            return Resultat.Erreur("Pseudo ou mot de passe incorrect", 401)
        }
        val jeton = ouvrirSession(compte.id)
        return Resultat.Ok(compte, jeton)
    }

    /** Renvoie le compte associé à un jeton valide, ou null. */
    fun parJeton(jeton: String?): Bdd.Compte? {
        if (jeton.isNullOrBlank()) return null
        return Bdd.compteDeSession(jeton)
    }

    /** Crée une session (jeton aléatoire de 32 octets, valable 30 jours). */
    private fun ouvrirSession(compteId: Long): String {
        val jeton = jetonAleatoire()
        Bdd.creerSession(jeton, compteId, System.currentTimeMillis() + DUREE_SESSION_MS)
        return jeton
    }

    /** Génère un jeton : 32 octets aléatoires (SecureRandom) en hexadécimal. */
    private fun jetonAleatoire(): String {
        val octets = ByteArray(32)
        aleatoire.nextBytes(octets)
        return octets.joinToString("") { "%02x".format(it) }
    }
}
