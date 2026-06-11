package com.example.myapplication.combat

import com.example.myapplication.model.Pokemon

enum class Categorie { PHYSIQUE, SPECIAL }

/** Une vraie attaque : nom, type, catégorie, puissance, précision (+ éventuel statut). */
data class Attaque(
    val nom: String,
    val type: String,
    val cat: Categorie,
    val puissance: Int,
    val precision: Int,
    val statut: Statut? = null,
    val chanceStatut: Int = 0,
)

object Attaques {

    private val P = Categorie.PHYSIQUE
    private val S = Categorie.SPECIAL

    private val secours = Attaque("Charge", "normal", P, 40, 100)

    private val parType: Map<String, List<Attaque>> = mapOf(
        "normal" to listOf(Attaque("Plaquage", "normal", P, 85, 100), Attaque("Hyper Voix", "normal", S, 90, 100)),
        "fire" to listOf(Attaque("Déflagration", "fire", S, 110, 85, Statut.BRULURE, 10), Attaque("Lance-Flammes", "fire", S, 90, 100, Statut.BRULURE, 10)),
        "water" to listOf(Attaque("Hydrocanon", "water", S, 110, 80), Attaque("Surf", "water", S, 90, 100)),
        "electric" to listOf(Attaque("Fatal-Foudre", "electric", S, 110, 70, Statut.PARALYSIE, 30), Attaque("Tonnerre", "electric", S, 90, 100, Statut.PARALYSIE, 10)),
        "grass" to listOf(Attaque("Lance-Soleil", "grass", S, 120, 100), Attaque("Tranch'Herbe", "grass", P, 55, 95)),
        "ice" to listOf(Attaque("Blizzard", "ice", S, 110, 70, Statut.GEL, 10), Attaque("Laser Glace", "ice", S, 90, 100, Statut.GEL, 10)),
        "fighting" to listOf(Attaque("Close Combat", "fighting", P, 120, 100), Attaque("Poing-Karaté", "fighting", P, 50, 100)),
        "poison" to listOf(Attaque("Bomb-Beurk", "poison", S, 90, 100, Statut.POISON, 30), Attaque("Direct Toxik", "poison", P, 80, 100, Statut.POISON, 20)),
        "ground" to listOf(Attaque("Séisme", "ground", P, 100, 100), Attaque("Tunnel", "ground", P, 80, 100)),
        "flying" to listOf(Attaque("Rapace", "flying", P, 120, 100), Attaque("Cru-Ailes", "flying", P, 60, 100)),
        "psychic" to listOf(Attaque("Psyko", "psychic", S, 90, 100), Attaque("Choc Mental", "psychic", S, 50, 100)),
        "bug" to listOf(Attaque("Bourdon", "bug", S, 90, 100), Attaque("Plaie-Croix", "bug", P, 80, 100)),
        "rock" to listOf(Attaque("Lame de Roc", "rock", P, 100, 80), Attaque("Éboulement", "rock", P, 75, 90)),
        "ghost" to listOf(Attaque("Ball'Ombre", "ghost", S, 80, 100), Attaque("Griffe Ombre", "ghost", P, 70, 100)),
        "dragon" to listOf(Attaque("Dracochoc", "dragon", S, 85, 100), Attaque("Draco-Griffe", "dragon", P, 80, 100)),
        "dark" to listOf(Attaque("Vibrobscur", "dark", S, 80, 100), Attaque("Tranche-Nuit", "dark", P, 70, 100)),
        "steel" to listOf(Attaque("Luminocanon", "steel", S, 80, 100), Attaque("Tête de Fer", "steel", P, 80, 100)),
        "fairy" to listOf(Attaque("Pouvoir Lunaire", "fairy", S, 95, 100), Attaque("Câlinerie", "fairy", S, 90, 90)),
    )

    fun pour(p: Pokemon): List<Attaque> {
        val choisies = mutableListOf<Attaque>()
        p.types.distinct().forEach { t -> parType[t]?.let { choisies.addAll(it) } }
        parType["normal"]?.firstOrNull()?.let { choisies.add(it) }
        val finales = choisies.distinctBy { it.nom }.take(4)
        return finales.ifEmpty { listOf(secours) }
    }
}
