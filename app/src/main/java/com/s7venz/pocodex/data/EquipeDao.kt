package com.s7venz.pocodex.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EquipeDao {

    @Query("SELECT * FROM equipe ORDER BY ordre")
    suspend fun tous(): List<MembreEquipe>

    @Query("SELECT EXISTS(SELECT 1 FROM equipe WHERE id = :id)")
    suspend fun present(id: Int): Boolean

    @Query("SELECT COUNT(*) FROM equipe")
    suspend fun nombre(): Int

    @Query("SELECT COALESCE(MAX(ordre), -1) FROM equipe")
    suspend fun ordreMax(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun ajouter(membre: MembreEquipe)

    @Query("DELETE FROM equipe WHERE id = :id")
    suspend fun retirer(id: Int)
}
