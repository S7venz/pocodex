package com.s7venz.pocodex.serveur

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests des routes de comptes (inscription, connexion, sessions).
 * Chaque classe de test utilise une base SQLite temporaire isolée.
 */
class ComptesTest {

    private lateinit var fichierBdd: File

    @BeforeTest
    fun avant() {
        // Base fraîche par exécution de test pour l'isolation.
        Bdd.fermer()
        fichierBdd = File.createTempFile("pocodex-test-comptes", ".db").apply { delete() }
        Bdd.ouvrir(fichierBdd.absolutePath)
    }

    @AfterTest
    fun apres() {
        Bdd.fermer()
        // Supprime db + journaux WAL/SHM.
        fichierBdd.parentFile.listFiles { f -> f.name.startsWith(fichierBdd.name) }
            ?.forEach { it.delete() }
    }

    /** Extrait un champ texte d'une réponse JSON. */
    private suspend fun champ(reponse: HttpResponse, cle: String): String =
        Json.parseToJsonElement(reponse.bodyAsText()).jsonObject[cle]!!.jsonPrimitive.content

    @Test
    fun inscriptionOk() = testApplication {
        application { module() }
        val client = clientJson()
        val r = client.post("/api/inscription") {
            contentType(ContentType.Application.Json)
            setBody("""{"pseudo":"Sacha","mdp":"pikachu"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("Sacha", champ(r, "pseudo"))
        assertEquals("1000", champ(r, "elo"))
        assertTrue(champ(r, "jeton").length == 64, "Le jeton doit faire 64 caractères hex (32 octets)")
    }

    @Test
    fun inscriptionDoublon409() = testApplication {
        application { module() }
        val client = clientJson()
        client.post("/api/inscription") {
            contentType(ContentType.Application.Json)
            setBody("""{"pseudo":"Ondine","mdp":"staross"}""")
        }
        val r = client.post("/api/inscription") {
            contentType(ContentType.Application.Json)
            setBody("""{"pseudo":"ondine","mdp":"autre1"}""") // même pseudo, casse différente
        }
        assertEquals(HttpStatusCode.Conflict, r.status)
        assertEquals("Pseudo déjà pris", champ(r, "erreur"))
    }

    @Test
    fun connexionOkEtMauvaisMdp() = testApplication {
        application { module() }
        val client = clientJson()
        client.post("/api/inscription") {
            contentType(ContentType.Application.Json)
            setBody("""{"pseudo":"Pierre","mdp":"onixxx"}""")
        }

        // Bon mot de passe → 200
        val ok = client.post("/api/connexion") {
            contentType(ContentType.Application.Json)
            setBody("""{"pseudo":"Pierre","mdp":"onixxx"}""")
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("Pierre", champ(ok, "pseudo"))

        // Mauvais mot de passe → 401
        val ko = client.post("/api/connexion") {
            contentType(ContentType.Application.Json)
            setBody("""{"pseudo":"Pierre","mdp":"faux"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, ko.status)
        assertEquals("Pseudo ou mot de passe incorrect", champ(ko, "erreur"))
    }

    @Test
    fun moiAvecEtSansJeton() = testApplication {
        application { module() }
        val client = clientJson()
        val inscription = client.post("/api/inscription") {
            contentType(ContentType.Application.Json)
            setBody("""{"pseudo":"Regis","mdp":"motdepasse"}""")
        }
        val jeton = champ(inscription, "jeton")

        // Avec jeton → 200 + profil
        val avec = client.get("/api/moi") { bearerAuth(jeton) }
        assertEquals(HttpStatusCode.OK, avec.status)
        assertEquals("Regis", champ(avec, "pseudo"))

        // Sans jeton → 401
        val sans = client.get("/api/moi")
        assertEquals(HttpStatusCode.Unauthorized, sans.status)

        // Jeton bidon → 401
        val faux = client.get("/api/moi") { bearerAuth("0000") }
        assertEquals(HttpStatusCode.Unauthorized, faux.status)
    }

    /** Client de test avec négociation JSON. */
    private fun io.ktor.server.testing.ApplicationTestBuilder.clientJson() =
        createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
}
