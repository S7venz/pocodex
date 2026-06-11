package com.example.myapplication.combat

import com.example.myapplication.model.Pokemon

/** Les altérations de statut. */
enum class Statut(val court: String, val couleur: Int) {
    BRULURE("BRÛ", 0xFFEE8130.toInt()),
    POISON("PSN", 0xFFA552CC.toInt()),
    PARALYSIE("PAR", 0xFFE0B000.toInt()),
    SOMMEIL("SOM", 0xFF8A8F98.toInt()),
    GEL("GEL", 0xFF4FB0C6.toInt()),
}

/** Un combattant : stats de base + nature + statut + ses attaques. */
data class Combattant(
    val id: Int,
    val nom: String,
    val types: List<String>,
    val nature: Nature,
    val pvMax: Int,
    var pv: Int,
    private val baseAtk: Int,
    private val baseDef: Int,
    private val baseAtkSpe: Int,
    private val baseDefSpe: Int,
    private val baseVit: Int,
    val attaques: List<Attaque>,
) {
    var statut: Statut? = null
    var toursSommeil: Int = 0

    val attaque get() = (baseAtk * Natures.modif(nature, Stat.ATTAQUE) * if (statut == Statut.BRULURE) 0.5 else 1.0).toInt()
    val defense get() = (baseDef * Natures.modif(nature, Stat.DEFENSE)).toInt()
    val attaqueSpe get() = (baseAtkSpe * Natures.modif(nature, Stat.ATK_SPE)).toInt()
    val defenseSpe get() = (baseDefSpe * Natures.modif(nature, Stat.DEF_SPE)).toInt()
    val vitesse get() = (baseVit * Natures.modif(nature, Stat.VITESSE) * if (statut == Statut.PARALYSIE) 0.5 else 1.0).toInt()

    val enVie: Boolean get() = pv > 0
    val spriteUrl: String
        get() = "https://raw.githubusercontent.com/Purukitto/pokemon-data.json/master/images/pokedex/hires/%03d.png".format(id)
}

data class Resultat(
    val degats: Int,
    val mult: Double,
    val critique: Boolean,
    val rate: Boolean,
    val statutInflige: Statut? = null,
)

object MoteurCombat {

    fun depuisPokemon(p: Pokemon): Combattant {
        val pvMax = p.pv * 2 + 20
        return Combattant(
            id = p.id, nom = p.nom, types = p.types, nature = Natures.pour(p.id),
            pvMax = pvMax, pv = pvMax,
            baseAtk = p.attaque, baseDef = p.defense,
            baseAtkSpe = p.attaqueSpe, baseDefSpe = p.defenseSpe, baseVit = p.vitesse,
            attaques = Attaques.pour(p),
        )
    }

    fun degats(att: Combattant, def: Combattant, atk: Attaque): Resultat {
        if ((1..100).random() > atk.precision) return Resultat(0, 1.0, false, rate = true)
        val mult = TypeChart.multiplicateur(atk.type, def.types)
        if (mult == 0.0) return Resultat(0, 0.0, false, false)

        val statAtt = if (atk.cat == Categorie.PHYSIQUE) att.attaque else att.attaqueSpe
        val statDef = if (atk.cat == Categorie.PHYSIQUE) def.defense else def.defenseSpe
        val stab = if (atk.type in att.types) 1.5 else 1.0
        val critique = (1..16).random() == 1
        val critMult = if (critique) 1.5 else 1.0
        val alea = (85..100).random() / 100.0

        val base = atk.puissance * (statAtt.toDouble() / statDef.coerceAtLeast(1)) / 4.0
        val d = (base * stab * mult * critMult * alea).toInt().coerceAtLeast(1)

        val inflige = if (atk.statut != null && def.statut == null && (1..100).random() <= atk.chanceStatut) atk.statut else null
        return Resultat(d, mult, critique, false, inflige)
    }

    fun choisirIA(att: Combattant, cible: Combattant): Attaque =
        att.attaques.maxByOrNull { a ->
            val stab = if (a.type in att.types) 1.5 else 1.0
            a.puissance * stab * TypeChart.multiplicateur(a.type, cible.types)
        } ?: att.attaques.first()

    fun messageEfficacite(mult: Double): String = when {
        mult == 0.0 -> "Ça n'a aucun effet…"
        mult >= 2.0 -> "Super efficace !"
        mult < 1.0 -> "Pas très efficace…"
        else -> ""
    }

    /** Peut-il agir ce tour ? (gère sommeil / gel / paralysie) */
    fun peutAgir(c: Combattant): Pair<Boolean, String?> = when (c.statut) {
        Statut.SOMMEIL ->
            if (c.toursSommeil <= 0) { c.statut = null; true to "${c.nom} se réveille !" }
            else { c.toursSommeil--; false to "${c.nom} dort profondément…" }
        Statut.GEL ->
            if ((1..100).random() <= 20) { c.statut = null; true to "${c.nom} dégèle !" }
            else false to "${c.nom} est gelé, il ne peut pas bouger !"
        Statut.PARALYSIE ->
            if ((1..100).random() <= 25) false to "${c.nom} est paralysé, il ne peut pas attaquer !"
            else true to null
        else -> true to null
    }

    /** Dégâts de fin de tour (brûlure / poison). */
    fun degatsStatut(c: Combattant): Pair<Int, String?> = when (c.statut) {
        Statut.BRULURE -> (c.pvMax / 16).coerceAtLeast(1) to "${c.nom} souffre de sa brûlure…"
        Statut.POISON -> (c.pvMax / 8).coerceAtLeast(1) to "${c.nom} souffre du poison…"
        else -> 0 to null
    }

    fun messageStatut(s: Statut): String = when (s) {
        Statut.BRULURE -> "est brûlé !"
        Statut.POISON -> "est empoisonné !"
        Statut.PARALYSIE -> "est paralysé !"
        Statut.SOMMEIL -> "s'endort !"
        Statut.GEL -> "est gelé !"
    }
}

/** Table des types officielle (valeurs ≠ 1.0 seulement). */
object TypeChart {

    private val chart: Map<String, Map<String, Double>> = mapOf(
        "normal" to mapOf("rock" to 0.5, "ghost" to 0.0, "steel" to 0.5),
        "fire" to mapOf("fire" to 0.5, "water" to 0.5, "grass" to 2.0, "ice" to 2.0, "bug" to 2.0, "rock" to 0.5, "dragon" to 0.5, "steel" to 2.0),
        "water" to mapOf("fire" to 2.0, "water" to 0.5, "grass" to 0.5, "ground" to 2.0, "rock" to 2.0, "dragon" to 0.5),
        "electric" to mapOf("water" to 2.0, "electric" to 0.5, "grass" to 0.5, "ground" to 0.0, "flying" to 2.0, "dragon" to 0.5),
        "grass" to mapOf("fire" to 0.5, "water" to 2.0, "grass" to 0.5, "poison" to 0.5, "ground" to 2.0, "flying" to 0.5, "bug" to 0.5, "rock" to 2.0, "dragon" to 0.5, "steel" to 0.5),
        "ice" to mapOf("fire" to 0.5, "water" to 0.5, "grass" to 2.0, "ice" to 0.5, "ground" to 2.0, "flying" to 2.0, "dragon" to 2.0, "steel" to 0.5),
        "fighting" to mapOf("normal" to 2.0, "ice" to 2.0, "poison" to 0.5, "flying" to 0.5, "psychic" to 0.5, "bug" to 0.5, "rock" to 2.0, "ghost" to 0.0, "dark" to 2.0, "steel" to 2.0, "fairy" to 0.5),
        "poison" to mapOf("grass" to 2.0, "poison" to 0.5, "ground" to 0.5, "rock" to 0.5, "ghost" to 0.5, "steel" to 0.0, "fairy" to 2.0),
        "ground" to mapOf("fire" to 2.0, "electric" to 2.0, "grass" to 0.5, "poison" to 2.0, "flying" to 0.0, "bug" to 0.5, "rock" to 2.0, "steel" to 2.0),
        "flying" to mapOf("electric" to 0.5, "grass" to 2.0, "fighting" to 2.0, "bug" to 2.0, "rock" to 0.5, "steel" to 0.5),
        "psychic" to mapOf("fighting" to 2.0, "poison" to 2.0, "psychic" to 0.5, "dark" to 0.0, "steel" to 0.5),
        "bug" to mapOf("fire" to 0.5, "grass" to 2.0, "fighting" to 0.5, "poison" to 0.5, "flying" to 0.5, "psychic" to 2.0, "ghost" to 0.5, "dark" to 2.0, "steel" to 0.5, "fairy" to 0.5),
        "rock" to mapOf("fire" to 2.0, "ice" to 2.0, "fighting" to 0.5, "ground" to 0.5, "flying" to 2.0, "bug" to 2.0, "steel" to 0.5),
        "ghost" to mapOf("normal" to 0.0, "psychic" to 2.0, "ghost" to 2.0, "dark" to 0.5),
        "dragon" to mapOf("dragon" to 2.0, "steel" to 0.5, "fairy" to 0.0),
        "dark" to mapOf("fighting" to 0.5, "psychic" to 2.0, "ghost" to 2.0, "dark" to 0.5, "fairy" to 0.5),
        "steel" to mapOf("fire" to 0.5, "water" to 0.5, "electric" to 0.5, "ice" to 2.0, "rock" to 2.0, "steel" to 0.5, "fairy" to 2.0),
        "fairy" to mapOf("fire" to 0.5, "fighting" to 2.0, "poison" to 0.5, "dragon" to 2.0, "dark" to 2.0, "steel" to 0.5),
    )

    fun multiplicateur(attaquant: String, defenseurs: List<String>): Double {
        var m = 1.0
        for (d in defenseurs) m *= chart[attaquant]?.get(d) ?: 1.0
        return m
    }
}
