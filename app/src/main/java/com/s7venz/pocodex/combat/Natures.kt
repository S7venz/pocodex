package com.s7venz.pocodex.combat

/** Les 5 stats que les natures peuvent modifier (les PV ne sont jamais affectés). */
enum class Stat { ATTAQUE, DEFENSE, ATK_SPE, DEF_SPE, VITESSE }

/** Une nature : booste une stat (+10 %) et en baisse une autre (-10 %), ou neutre. */
data class Nature(val nomFr: String, val plus: Stat?, val moins: Stat?)

object Natures {

    val toutes = listOf(
        Nature("Hardi", null, null),
        Nature("Solo", Stat.ATTAQUE, Stat.DEFENSE),
        Nature("Rigide", Stat.ATTAQUE, Stat.ATK_SPE),
        Nature("Mauvais", Stat.ATTAQUE, Stat.DEF_SPE),
        Nature("Brave", Stat.ATTAQUE, Stat.VITESSE),
        Nature("Assuré", Stat.DEFENSE, Stat.ATTAQUE),
        Nature("Docile", null, null),
        Nature("Relax", Stat.DEFENSE, Stat.ATK_SPE),
        Nature("Malin", Stat.DEFENSE, Stat.DEF_SPE),
        Nature("Lâche", Stat.DEFENSE, Stat.VITESSE),
        Nature("Modeste", Stat.ATK_SPE, Stat.ATTAQUE),
        Nature("Doux", Stat.ATK_SPE, Stat.DEFENSE),
        Nature("Pudique", null, null),
        Nature("Foufou", Stat.ATK_SPE, Stat.DEF_SPE),
        Nature("Discret", Stat.ATK_SPE, Stat.VITESSE),
        Nature("Calme", Stat.DEF_SPE, Stat.ATTAQUE),
        Nature("Gentil", Stat.DEF_SPE, Stat.DEFENSE),
        Nature("Prudent", Stat.DEF_SPE, Stat.ATK_SPE),
        Nature("Bizarre", null, null),
        Nature("Malpoli", Stat.DEF_SPE, Stat.VITESSE),
        Nature("Timide", Stat.VITESSE, Stat.ATTAQUE),
        Nature("Pressé", Stat.VITESSE, Stat.DEFENSE),
        Nature("Jovial", Stat.VITESSE, Stat.ATK_SPE),
        Nature("Naïf", Stat.VITESSE, Stat.DEF_SPE),
        Nature("Sérieux", null, null),
    )

    /** Nature stable pour une espèce donnée (déterministe selon l'id). */
    fun pour(id: Int): Nature = toutes[(id * 7 + 3).mod(toutes.size)]

    /** Multiplicateur appliqué à une stat selon la nature. */
    fun modif(nature: Nature, stat: Stat): Double = when (stat) {
        nature.plus -> 1.1
        nature.moins -> 0.9
        else -> 1.0
    }
}
