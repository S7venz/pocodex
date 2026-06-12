package com.s7venz.pocodex.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Base de données Room : favoris + équipe + captures. */
@Database(
    entities = [FavoriEntity::class, MembreEquipe::class, CaptureEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriDao(): FavoriDao
    abstract fun equipeDao(): EquipeDao
    abstract fun captureDao(): CaptureDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v2 → v3 : suivi des captures et marquage chromatique.
         * - nouvelle table `captures` (id + shiny) ;
         * - colonne `shiny` ajoutée à `equipe`.
         * Migration non destructive : l'équipe existante est préservée.
         * Les définitions SQL doivent correspondre EXACTEMENT à ce que Room attend
         * (types, NOT NULL, DEFAULT) sinon la validation du schéma échoue au démarrage.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `captures` " +
                        "(`id` INTEGER NOT NULL, `shiny` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "ALTER TABLE `equipe` ADD COLUMN `shiny` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pokedex.db",
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
