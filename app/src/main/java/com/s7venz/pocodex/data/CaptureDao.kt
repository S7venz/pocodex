package com.s7venz.pocodex.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Accès à la table des Pokémon capturés (complétion du Codex). */
@Dao
interface CaptureDao {

    /** Enregistre une capture. IGNORE : une capture déjà acquise n'est pas écrasée. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ajouter(c: CaptureEntity)

    @Query("SELECT id FROM captures")
    suspend fun ids(): List<Int>

    @Query("SELECT COUNT(*) FROM captures")
    suspend fun nombre(): Int
}
