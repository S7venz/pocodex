package com.s7venz.pocodex.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO (Data Access Object) : les opérations possibles sur la table favoris.
 * Room génère automatiquement le code SQL derrière ces fonctions.
 */
@Dao
interface FavoriDao {

    @Query("SELECT * FROM favoris ORDER BY id")
    suspend fun getAll(): List<FavoriEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favoris WHERE id = :id)")
    suspend fun isFavorite(id: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favori: FavoriEntity)

    @Query("DELETE FROM favoris WHERE id = :id")
    suspend fun delete(id: Int)
}
