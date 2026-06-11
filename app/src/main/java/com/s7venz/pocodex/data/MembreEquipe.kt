package com.s7venz.pocodex.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un Pokémon de l'équipe du joueur (stocké en base Room). */
@Entity(tableName = "equipe")
data class MembreEquipe(
    @PrimaryKey val id: Int,
    val ordre: Int,
)
