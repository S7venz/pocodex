package com.s7venz.pocodex.model

import com.google.gson.annotations.SerializedName

// DTOs qui correspondent au JSON (un seul fichier riche : noms FR, types, stats…).

data class PokemonDto(
    val id: Int = 0,
    val name: NameDto? = null,
    val type: List<String> = emptyList(),
    val base: BaseDto? = null,
    val profile: ProfileDto? = null,
    val description: String? = null,
    val species: String? = null,
)

data class NameDto(
    val english: String? = null,
    val french: String? = null,
)

data class BaseDto(
    @SerializedName("HP") val hp: Int = 0,
    @SerializedName("Attack") val attack: Int = 0,
    @SerializedName("Defense") val defense: Int = 0,
    @SerializedName("Sp. Attack") val spAttack: Int = 0,
    @SerializedName("Sp. Defense") val spDefense: Int = 0,
    @SerializedName("Speed") val speed: Int = 0,
)

data class ProfileDto(
    val height: String? = null,
    val weight: String? = null,
)
