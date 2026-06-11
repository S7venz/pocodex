package com.s7venz.pocodex.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Une ligne de la table "favoris" (un Pokémon mis en favori). */
@Entity(tableName = "favoris")
data class FavoriEntity(
    @PrimaryKey val id: Int,
    val name: String,
)
