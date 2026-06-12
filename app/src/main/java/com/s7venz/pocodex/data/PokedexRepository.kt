package com.s7venz.pocodex.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.s7venz.pocodex.PokeApp
import com.s7venz.pocodex.model.Pokemon
import com.s7venz.pocodex.model.PokemonDto
import com.s7venz.pocodex.model.toPokemon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Source unique des Pokémon : tout est embarqué dans l'app (assets/pokedex.json).
 * Aucune connexion requise — les Pokémon restent visibles même hors-ligne.
 * La liste est lue UNE fois puis gardée en cache.
 */
object PokedexRepository {

    private var cache: List<Pokemon> = emptyList()
    private val mutex = Mutex()

    suspend fun tous(): List<Pokemon> {
        mutex.withLock {
            if (cache.isEmpty()) {
                cache = withContext(Dispatchers.IO) {
                    val json = PokeApp.instance.assets
                        .open("pokedex.json")
                        .bufferedReader()
                        .use { it.readText() }
                    val type = object : TypeToken<List<PokemonDto>>() {}.type
                    Gson().fromJson<List<PokemonDto>>(json, type)
                        .filter { it.id in 1..151 && it.base != null }
                        .map { it.toPokemon() }
                        .sortedBy { it.id }
                }
            }
        }
        return cache
    }

    suspend fun parId(id: Int): Pokemon? = tous().firstOrNull { it.id == id }

    suspend fun adversaireAleatoire(saufId: Int): Pokemon =
        tous().filter { it.id != saufId }.random()
}
