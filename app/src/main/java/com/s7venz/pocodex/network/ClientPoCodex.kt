package com.s7venz.pocodex.network

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Client REST du serveur PoCodex (comptes : inscription, connexion, profil).
 *
 * Réutilise le client OkHttp partagé ([Network.client], DNS IPv4 + repli DoH) et
 * Gson pour (dé)sérialiser. Toutes les fonctions sont suspend et tournent sur
 * [Dispatchers.IO]. Le contrat exact est décrit dans `serveur/PROTOCOLE.md`.
 */
object ClientPoCodex {

    private val gson = Gson()
    private val typeJson = "application/json; charset=utf-8".toMediaType()

    /** Profil + jeton renvoyés par /api/inscription et /api/connexion. */
    data class CompteInfos(
        val jeton: String,
        val pseudo: String,
        val elo: Int,
        val victoires: Int,
        val defaites: Int,
    )

    /** DTO du corps d'erreur {"erreur":"<message FR>"}. */
    private data class ErreurDto(val erreur: String? = null)

    /** Retire un éventuel `/` final de l'URL serveur ("http://x:8080/" -> "http://x:8080"). */
    private fun normaliser(serveur: String): String = serveur.trim().trimEnd('/')

    /** Corps JSON {"pseudo":..,"mdp":..} pour inscription / connexion. */
    private fun corpsIdentifiants(pseudo: String, mdp: String) =
        gson.toJson(mapOf("pseudo" to pseudo, "mdp" to mdp)).toRequestBody(typeJson)

    /** Crée un compte. En cas de succès, ouvre une session (jeton). */
    suspend fun inscription(serveur: String, pseudo: String, mdp: String): CompteInfos =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${normaliser(serveur)}/api/inscription")
                .post(corpsIdentifiants(pseudo, mdp))
                .build()
            executerCompte(req)
        }

    /** Connecte un compte existant (nouveau jeton à chaque connexion). */
    suspend fun connexion(serveur: String, pseudo: String, mdp: String): CompteInfos =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${normaliser(serveur)}/api/connexion")
                .post(corpsIdentifiants(pseudo, mdp))
                .build()
            executerCompte(req)
        }

    /** Profil du joueur connecté à partir de son jeton (Bearer). */
    suspend fun moi(serveur: String, jeton: String): CompteInfos =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${normaliser(serveur)}/api/moi")
                .header("Authorization", "Bearer $jeton")
                .get()
                .build()
            // /api/moi ne renvoie pas de jeton : on réinjecte celui fourni.
            val infos = executerCompte(req)
            infos.copy(jeton = jeton)
        }

    /**
     * Exécute la requête et mappe la réponse en [CompteInfos].
     * HTTP non-2xx -> lève une [IOException] avec le message français du serveur
     * ({"erreur":..}) ou un repli générique « Serveur injoignable ».
     */
    private fun executerCompte(req: Request): CompteInfos {
        val reponse = try {
            Network.client.newCall(req).execute()
        } catch (e: IOException) {
            throw IOException("Serveur injoignable", e)
        }
        reponse.use {
            val corps = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw IOException(messageErreur(corps))
            }
            val infos = try {
                gson.fromJson(corps, CompteInfos::class.java)
            } catch (e: JsonSyntaxException) {
                null
            }
            return infos ?: throw IOException("Serveur injoignable")
        }
    }

    /** Extrait le message FR du corps d'erreur, ou un repli générique. */
    private fun messageErreur(corps: String): String {
        val msg = runCatching { gson.fromJson(corps, ErreurDto::class.java)?.erreur }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "Serveur injoignable"
    }
}
