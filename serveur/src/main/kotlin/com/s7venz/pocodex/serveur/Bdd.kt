package com.s7venz.pocodex.serveur

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Accès à la base SQLite du serveur (JDBC brut, sans ORM).
 *
 * Le trafic est faible : on privilégie la simplicité. Toutes les méthodes
 * publiques s'exécutent sous un verrou unique ([verrou]) — accès strictement
 * sérialisé, donc aucune course de données ni souci de concurrence SQLite.
 *
 * Schéma :
 *  - comptes  : un joueur (pseudo unique, mot de passe haché bcrypt, ELO, bilan).
 *  - sessions : un jeton d'authentification rattaché à un compte (avec expiration).
 *  - parties  : l'historique des combats en ligne (code, hôte, invité, vainqueur).
 */
object Bdd {

    /** Verrou global : toute opération base passe par ici (accès sérialisé). */
    private val verrou = Any()

    @Volatile
    private var connexion: Connection? = null

    /**
     * Ouvre (une seule fois) la connexion JDBC SQLite vers le fichier [chemin]
     * et crée les tables si elles n'existent pas. À appeler au démarrage.
     */
    fun ouvrir(chemin: String) = synchronized(verrou) {
        if (connexion != null) return@synchronized
        // sqlite-jdbc s'enregistre automatiquement, mais on force le chargement.
        Class.forName("org.sqlite.JDBC")
        val co = DriverManager.getConnection("jdbc:sqlite:$chemin")
        co.createStatement().use { st ->
            // WAL : meilleures perfs en lecture/écriture concurrentes ;
            // foreign_keys : les contraintes de clés étrangères sont respectées.
            st.execute("PRAGMA journal_mode=WAL")
            st.execute("PRAGMA foreign_keys=ON")
        }
        connexion = co
        creerTables()
    }

    /** Ferme la connexion (utile pour les tests). */
    fun fermer() = synchronized(verrou) {
        connexion?.close()
        connexion = null
    }

    private fun co(): Connection =
        connexion ?: error("Base non ouverte : appeler Bdd.ouvrir(chemin) au démarrage.")

    private fun creerTables() {
        co().createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS comptes (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    pseudo    TEXT UNIQUE NOT NULL COLLATE NOCASE,
                    mdp_hash  TEXT NOT NULL,
                    elo       INTEGER NOT NULL DEFAULT 1000,
                    victoires INTEGER NOT NULL DEFAULT 0,
                    defaites  INTEGER NOT NULL DEFAULT 0,
                    cree_le   INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS sessions (
                    jeton      TEXT PRIMARY KEY,
                    compte_id  INTEGER NOT NULL REFERENCES comptes(id),
                    cree_le    INTEGER NOT NULL,
                    expire_le  INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS parties (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    code        TEXT NOT NULL,
                    hote_id     INTEGER NOT NULL,
                    invite_id   INTEGER,
                    vainqueur_id INTEGER,
                    debut       INTEGER NOT NULL,
                    fin         INTEGER
                )
                """.trimIndent()
            )
        }
    }

    // ----------------------------------------------------------------------
    //  Comptes
    // ----------------------------------------------------------------------

    /** Représentation d'un compte tel que stocké en base. */
    data class Compte(
        val id: Long,
        val pseudo: String,
        val mdpHash: String,
        val elo: Int,
        val victoires: Int,
        val defaites: Int,
    )

    /** Vrai si un compte porte déjà ce pseudo (comparaison insensible à la casse). */
    fun pseudoExiste(pseudo: String): Boolean = synchronized(verrou) {
        co().prepareStatement("SELECT 1 FROM comptes WHERE pseudo = ? COLLATE NOCASE").use { ps ->
            ps.setString(1, pseudo)
            ps.executeQuery().use { rs -> rs.next() }
        }
    }

    /** Crée un compte et renvoie son identifiant. Le pseudo doit être libre. */
    fun creerCompte(pseudo: String, mdpHash: String): Long = synchronized(verrou) {
        co().prepareStatement(
            "INSERT INTO comptes (pseudo, mdp_hash, cree_le) VALUES (?, ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        ).use { ps ->
            ps.setString(1, pseudo)
            ps.setString(2, mdpHash)
            ps.setLong(3, System.currentTimeMillis())
            ps.executeUpdate()
            ps.generatedKeys.use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }

    /** Récupère un compte par son pseudo (insensible à la casse), ou null. */
    fun compteParPseudo(pseudo: String): Compte? = synchronized(verrou) {
        co().prepareStatement("SELECT * FROM comptes WHERE pseudo = ? COLLATE NOCASE").use { ps ->
            ps.setString(1, pseudo)
            ps.executeQuery().use { rs -> if (rs.next()) lireCompte(rs) else null }
        }
    }

    /** Récupère un compte par son identifiant, ou null. */
    fun compteParId(id: Long): Compte? = synchronized(verrou) {
        co().prepareStatement("SELECT * FROM comptes WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) lireCompte(rs) else null }
        }
    }

    /**
     * Applique le résultat d'un combat à un compte : nouvel ELO et incrément
     * du compteur de victoires ou de défaites.
     */
    fun majResultat(compteId: Long, nouvelElo: Int, aGagne: Boolean) = synchronized(verrou) {
        val colonne = if (aGagne) "victoires" else "defaites"
        co().prepareStatement(
            "UPDATE comptes SET elo = ?, $colonne = $colonne + 1 WHERE id = ?"
        ).use { ps ->
            ps.setInt(1, nouvelElo)
            ps.setLong(2, compteId)
            ps.executeUpdate()
        }
    }

    private fun lireCompte(rs: ResultSet) = Compte(
        id = rs.getLong("id"),
        pseudo = rs.getString("pseudo"),
        mdpHash = rs.getString("mdp_hash"),
        elo = rs.getInt("elo"),
        victoires = rs.getInt("victoires"),
        defaites = rs.getInt("defaites"),
    )

    // ----------------------------------------------------------------------
    //  Sessions
    // ----------------------------------------------------------------------

    /** Enregistre une session (jeton → compte) avec sa date d'expiration. */
    fun creerSession(jeton: String, compteId: Long, expireLe: Long) = synchronized(verrou) {
        co().prepareStatement(
            "INSERT INTO sessions (jeton, compte_id, cree_le, expire_le) VALUES (?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, jeton)
            ps.setLong(2, compteId)
            ps.setLong(3, System.currentTimeMillis())
            ps.setLong(4, expireLe)
            ps.executeUpdate()
        }
    }

    /**
     * Renvoie le compte rattaché à un jeton encore valide, ou null si le jeton
     * est inconnu ou expiré.
     */
    fun compteDeSession(jeton: String): Compte? = synchronized(verrou) {
        co().prepareStatement(
            """
            SELECT c.* FROM sessions s
            JOIN comptes c ON c.id = s.compte_id
            WHERE s.jeton = ? AND s.expire_le > ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, jeton)
            ps.setLong(2, System.currentTimeMillis())
            ps.executeQuery().use { rs -> if (rs.next()) lireCompte(rs) else null }
        }
    }

    // ----------------------------------------------------------------------
    //  Parties
    // ----------------------------------------------------------------------

    /** Crée une partie (au moment où l'hôte ouvre une salle) et renvoie son id. */
    fun creerPartie(code: String, hoteId: Long): Long = synchronized(verrou) {
        co().prepareStatement(
            "INSERT INTO parties (code, hote_id, debut) VALUES (?, ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        ).use { ps ->
            ps.setString(1, code)
            ps.setLong(2, hoteId)
            ps.setLong(3, System.currentTimeMillis())
            ps.executeUpdate()
            ps.generatedKeys.use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }

    /** Renseigne l'identifiant de l'invité quand il rejoint la partie. */
    fun majInvite(partieId: Long, inviteId: Long) = synchronized(verrou) {
        co().prepareStatement("UPDATE parties SET invite_id = ? WHERE id = ?").use { ps ->
            ps.setLong(1, inviteId)
            ps.setLong(2, partieId)
            ps.executeUpdate()
        }
    }

    /** Clôt une partie : enregistre le vainqueur et l'heure de fin. */
    fun terminerPartie(partieId: Long, vainqueurId: Long) = synchronized(verrou) {
        co().prepareStatement(
            "UPDATE parties SET vainqueur_id = ?, fin = ? WHERE id = ?"
        ).use { ps ->
            ps.setLong(1, vainqueurId)
            ps.setLong(2, System.currentTimeMillis())
            ps.setLong(3, partieId)
            ps.executeUpdate()
        }
    }
}
