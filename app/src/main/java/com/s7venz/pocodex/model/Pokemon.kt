package com.s7venz.pocodex.model

/** Modèle "métier" propre, utilisé par toute l'app. */
data class Pokemon(
    val id: Int,
    val nom: String,
    val types: List<String>, // clés en minuscules : "fire", "grass"…
    val pv: Int,
    val attaque: Int,
    val defense: Int,
    val attaqueSpe: Int,
    val defenseSpe: Int,
    val vitesse: Int,
    val taille: String,
    val poids: String,
    val description: String,
) {
    val numero: String get() = "#%03d".format(id)
    val typePrincipal: String get() = types.firstOrNull() ?: "normal"
    val artworkUrl: String
        get() = "file:///android_asset/artwork/%03d.png".format(id)
}

fun PokemonDto.toPokemon(): Pokemon = Pokemon(
    id = id,
    nom = name?.french ?: name?.english ?: "???",
    types = type.map { it.lowercase() },
    pv = base?.hp ?: 1,
    attaque = base?.attack ?: 1,
    defense = base?.defense ?: 1,
    attaqueSpe = base?.spAttack ?: 1,
    defenseSpe = base?.spDefense ?: 1,
    vitesse = base?.speed ?: 1,
    taille = profile?.height ?: "?",
    poids = profile?.weight ?: "?",
    description = description ?: "",
)
