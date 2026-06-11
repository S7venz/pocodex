package com.example.myapplication.data

import com.example.myapplication.model.Pokemon
import com.example.myapplication.model.toPokemon
import com.example.myapplication.network.ApiClient

/**
 * Source unique des Pokémon : télécharge la liste UNE fois puis la garde en cache.
 * Les écrans (liste, fiche, combat) lisent ici sans refaire d'appel réseau.
 */
object PokedexRepository {

    private var cache: List<Pokemon> = emptyList()

    suspend fun tous(): List<Pokemon> {
        if (cache.isEmpty()) {
            cache = ApiClient.pokeApi.getPokedex()
                .filter { it.id in 1..151 && it.base != null }
                .map { it.toPokemon() }
                .sortedBy { it.id }
        }
        return cache
    }

    suspend fun parId(id: Int): Pokemon? = tous().firstOrNull { it.id == id }

    suspend fun adversaireAleatoire(saufId: Int): Pokemon =
        tous().filter { it.id != saufId }.random()
}
