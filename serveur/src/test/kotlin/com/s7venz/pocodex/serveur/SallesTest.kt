package com.s7venz.pocodex.serveur

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test d'intégration des salles de combat en WebSocket : deux clients (hôte et
 * invité) sur le même serveur de test ; vérifie l'appariement, le relai des
 * messages `etat`/`act`, la clôture par `fin`, le classement reçu des deux côtés
 * et la mise à jour de l'ELO en base.
 */
class SallesTest {

    private lateinit var fichierBdd: File
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun avant() {
        Bdd.fermer()
        fichierBdd = File.createTempFile("pocodex-test-salles", ".db").apply { delete() }
        Bdd.ouvrir(fichierBdd.absolutePath)
    }

    @AfterTest
    fun apres() {
        Bdd.fermer()
        fichierBdd.parentFile.listFiles { f -> f.name.startsWith(fichierBdd.name) }
            ?.forEach { it.delete() }
    }

    /** Lit la prochaine trame texte et la décode en objet JSON. */
    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.recevoir(): JsonObject {
        while (true) {
            val frame = incoming.receive()
            if (frame is Frame.Text) {
                return json.parseToJsonElement(frame.readText()).jsonObject
            }
        }
    }

    /** Envoie un objet JSON sous forme de trame texte. */
    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.envoyer(o: JsonObject) {
        send(Frame.Text(o.toString()))
    }

    @Test
    fun combatCompletAvecElo() = io.ktor.server.testing.testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        // Deux comptes : un hôte (Sacha) et un invité (Régis).
        val jetonHote = inscrire(client, "SachaH", "motdepasse")
        val jetonInvite = inscrire(client, "RegisI", "motdepasse")

        // Synchronisation entre les deux coroutines.
        val codeSalle = CompletableDeferred<String>()
        val joinRecuParHote = CompletableDeferred<JsonObject>()
        val actRecuParHote = CompletableDeferred<JsonObject>()
        val classementHote = CompletableDeferred<JsonObject>()

        withTimeout(20_000) {
            // --- Coroutine HÔTE ---
            val tacheHote = launch {
                client.webSocket("/ws?jeton=$jetonHote") {
                    envoyer(buildJsonObject { put("k", "creer") })
                    val salle = recevoir() // {"k":"salle","code":"..."}
                    assertEquals("salle", salle["k"]!!.jsonPrimitive.content)
                    val code = salle["code"]!!.jsonPrimitive.content
                    assertEquals(5, code.length, "Le code de salle doit faire 5 caractères")
                    codeSalle.complete(code)

                    // Reçoit le `join` de l'invité.
                    val join = recevoir()
                    joinRecuParHote.complete(join)

                    // Envoie un état (tour de l'invité) → doit être relayé à l'invité.
                    envoyer(buildJsonObject {
                        put("k", "etat")
                        put("seq", 1)
                        put("tour", "guest")
                        put("pvHote", 100)
                    })

                    // Reçoit l'`act` relayé depuis l'invité.
                    val act = recevoir()
                    actRecuParHote.complete(act)

                    // Clôture : l'hôte gagne.
                    envoyer(buildJsonObject {
                        put("k", "fin")
                        put("vainqueurEstHote", true)
                    })

                    // Reçoit son classement.
                    classementHote.complete(recevoir())
                }
            }

            // --- Coroutine INVITÉ ---
            val code = codeSalle.await()
            client.webSocket("/ws?jeton=$jetonInvite") {
                envoyer(buildJsonObject {
                    put("k", "rejoindre")
                    put("code", code)
                    put("pokemon", 25)
                })

                // Reçoit les infos de l'adversaire.
                val infos = recevoir()
                assertEquals("infos", infos["k"]!!.jsonPrimitive.content)
                val adv = infos["adversaire"]!!.jsonObject
                assertEquals("SachaH", adv["pseudo"]!!.jsonPrimitive.content)

                // Reçoit l'état relayé par l'hôte.
                val etat = recevoir()
                assertEquals("etat", etat["k"]!!.jsonPrimitive.content)
                assertEquals("guest", etat["tour"]!!.jsonPrimitive.content)

                // Joue son coup → relayé à l'hôte.
                envoyer(buildJsonObject {
                    put("k", "act")
                    put("seq", 1)
                    put("move", 0)
                })

                // Reçoit son classement (défaite).
                val classement = recevoir()
                assertEquals("classement", classement["k"]!!.jsonPrimitive.content)
                assertEquals("hote", classement["vainqueur"]!!.jsonPrimitive.content)
                // L'invité a perdu : delta négatif.
                assertTrue(classement["delta"]!!.jsonPrimitive.content.toInt() < 0)
            }

            tacheHote.join()
        }

        // --- Vérifications sur les messages relayés ---
        val join = joinRecuParHote.await()
        assertEquals("join", join["k"]!!.jsonPrimitive.content)
        assertEquals(25, join["id"]!!.jsonPrimitive.content.toInt())
        assertEquals("RegisI", join["pseudo"]!!.jsonPrimitive.content)

        val act = actRecuParHote.await()
        assertEquals("act", act["k"]!!.jsonPrimitive.content)
        assertEquals(0, act["move"]!!.jsonPrimitive.content.toInt())

        val clHote = classementHote.await()
        assertEquals("classement", clHote["k"]!!.jsonPrimitive.content)
        assertEquals("hote", clHote["vainqueur"]!!.jsonPrimitive.content)
        // L'hôte a gagné : delta positif.
        assertTrue(clHote["delta"]!!.jsonPrimitive.content.toInt() > 0)

        // --- Vérification en base : ELO modifié, bilans à jour ---
        val hote = Bdd.compteParPseudo("SachaH")!!
        val invite = Bdd.compteParPseudo("RegisI")!!
        assertTrue(hote.elo > 1000, "L'ELO de l'hôte (vainqueur) doit augmenter")
        assertTrue(invite.elo < 1000, "L'ELO de l'invité (perdant) doit baisser")
        assertEquals(1, hote.victoires)
        assertEquals(0, hote.defaites)
        assertEquals(0, invite.victoires)
        assertEquals(1, invite.defaites)
        // K=32, ELO égaux → ±16.
        assertEquals(1016, hote.elo)
        assertEquals(984, invite.elo)
    }

    /** Inscrit un compte via l'API REST et renvoie son jeton. */
    private suspend fun inscrire(
        client: io.ktor.client.HttpClient,
        pseudo: String,
        mdp: String,
    ): String {
        val r = client.post("/api/inscription") {
            contentType(ContentType.Application.Json)
            setBody("""{"pseudo":"$pseudo","mdp":"$mdp"}""")
        }
        return json.parseToJsonElement(r.bodyAsText()).jsonObject["jeton"]!!.jsonPrimitive.content
    }
}
