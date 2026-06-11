package com.example.myapplication.network

import com.example.myapplication.model.PokemonDto
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

/**
 * Récupère le pokédex complet (un seul fichier JSON hébergé sur GitHub :
 * noms FR, types, stats, tailles… — joignable depuis ce réseau).
 */
interface PokeApi {
    @GET("pokedex.json")
    suspend fun getPokedex(): List<PokemonDto>
}

object ApiClient {
    private const val BASE_URL =
        "https://raw.githubusercontent.com/Purukitto/pokemon-data.json/master/"

    val pokeApi: PokeApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(Network.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PokeApi::class.java)
    }
}
