package com.s7venz.pocodex.combat

/** Un objet du sac : potion (soin) ou Poké Ball (capture). */
data class Objet(val nom: String, val soin: Int, val capture: Boolean = false)

/** Sac du joueur (réinitialisé à chaque lancement de l'app). */
object Inventaire {

    data class Ligne(val objet: Objet, var quantite: Int)

    val lignes = mutableListOf(
        Ligne(Objet("Potion", 20), 5),
        Ligne(Objet("Super Potion", 50), 3),
        Ligne(Objet("Hyper Potion", 120), 1),
        Ligne(Objet("Poké Ball", 0, capture = true), 5),
    )

    fun disponibles(): List<Ligne> = lignes.filter { it.quantite > 0 }

    fun consommer(objet: Objet) {
        lignes.firstOrNull { it.objet.nom == objet.nom }?.let {
            if (it.quantite > 0) it.quantite--
        }
    }
}
