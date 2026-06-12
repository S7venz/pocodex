package com.s7venz.pocodex

import android.content.Context

/**
 * État du compte joueur, persisté dans les SharedPreferences "compte".
 *
 * Conserve le jeton de session, le pseudo et un cache local du classement
 * (elo / victoires / défaites) renvoyé par le serveur, ainsi que l'URL serveur
 * et le mode hors-ligne. Le contexte global vient de [PokeApp.instance].
 */
object Compte {

    private const val FICHIER = "compte"
    private const val SERVEUR_DEFAUT = "http://10.0.2.2:8080"

    private val prefs
        get() = PokeApp.instance.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /** URL du serveur PoCodex (défaut : hôte du Mac depuis l'émulateur). */
    var serveur: String
        get() = prefs.getString("serveur", SERVEUR_DEFAUT).orEmpty().ifBlank { SERVEUR_DEFAUT }
        set(v) = prefs.edit().putString("serveur", v.trim()).apply()

    /** Jeton de session (vide = non connecté au serveur). */
    var jeton: String
        get() = prefs.getString("jeton", "").orEmpty()
        set(v) = prefs.edit().putString("jeton", v).apply()

    /** Pseudo du joueur connecté. */
    var pseudo: String
        get() = prefs.getString("pseudo", "").orEmpty()
        set(v) = prefs.edit().putString("pseudo", v).apply()

    /** Cache local du classement ELO. */
    var elo: Int
        get() = prefs.getInt("elo", 1000)
        set(v) = prefs.edit().putInt("elo", v).apply()

    /** Cache local du nombre de victoires. */
    var victoires: Int
        get() = prefs.getInt("victoires", 0)
        set(v) = prefs.edit().putInt("victoires", v).apply()

    /** Cache local du nombre de défaites. */
    var defaites: Int
        get() = prefs.getInt("defaites", 0)
        set(v) = prefs.edit().putInt("defaites", v).apply()

    /** Le joueur a choisi de jouer sans compte (mode hors-ligne). */
    var horsLigne: Boolean
        get() = prefs.getBoolean("horsLigne", false)
        set(v) = prefs.edit().putBoolean("horsLigne", v).apply()

    /** Vrai si un jeton de session est présent. */
    val estConnecte: Boolean
        get() = jeton.isNotEmpty()

    /** Sauvegarde le profil reçu du serveur et bascule en mode connecté. */
    fun memoriser(jeton: String, pseudo: String, elo: Int, victoires: Int, defaites: Int) {
        prefs.edit()
            .putString("jeton", jeton)
            .putString("pseudo", pseudo)
            .putInt("elo", elo)
            .putInt("victoires", victoires)
            .putInt("defaites", defaites)
            .putBoolean("horsLigne", false)
            .apply()
    }

    /** Efface le jeton et le mode hors-ligne (retour à l'écran d'accueil). */
    fun deconnecter() {
        prefs.edit()
            .remove("jeton")
            .remove("pseudo")
            .putBoolean("horsLigne", false)
            .apply()
    }
}
