package com.s7venz.pocodex.combat

/** Un objet du sac : potion (soin) ou Poké Ball (capture). */
data class Objet(val nom: String, val soin: Int, val capture: Boolean = false)

/** Sac du joueur (réinitialisé à chaque lancement de l'app). */
object Inventaire {

    data class Ligne(val objet: Objet, var quantite: Int)

    private const val QTE_POTION = 5
    private const val QTE_SUPER_POTION = 3
    private const val QTE_HYPER_POTION = 1
    private const val QTE_POKE_BALL = 5

    val lignes = mutableListOf(
        Ligne(Objet("Potion", 20), QTE_POTION),
        Ligne(Objet("Super Potion", 50), QTE_SUPER_POTION),
        Ligne(Objet("Hyper Potion", 120), QTE_HYPER_POTION),
        Ligne(Objet("Poké Ball", 0, capture = true), QTE_POKE_BALL),
    )

    fun disponibles(): List<Ligne> = lignes.filter { it.quantite > 0 }

    fun consommer(objet: Objet) {
        lignes.firstOrNull { it.objet.nom == objet.nom }?.let {
            if (it.quantite > 0) it.quantite--
        }
    }

    fun reinitialiser() {
        lignes[0].quantite = QTE_POTION
        lignes[1].quantite = QTE_SUPER_POTION
        lignes[2].quantite = QTE_HYPER_POTION
        lignes[3].quantite = QTE_POKE_BALL
    }
}
