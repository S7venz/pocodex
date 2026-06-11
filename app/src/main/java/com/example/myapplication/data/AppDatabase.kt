package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Base de données Room : favoris + équipe. */
@Database(entities = [FavoriEntity::class, MembreEquipe::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriDao(): FavoriDao
    abstract fun equipeDao(): EquipeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pokedex.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
