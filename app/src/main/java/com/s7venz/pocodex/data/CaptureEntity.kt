package com.s7venz.pocodex.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un Pokémon capturé (suivi de la complétion du Codex). Une capture reste acquise. */
@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey val id: Int,
    val shiny: Boolean = false,
)
