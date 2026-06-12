package com.s7venz.pocodex

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.s7venz.pocodex.network.ClientPoCodex
import kotlinx.coroutines.launch

/**
 * Porte d'entrée du jeu : connexion / inscription au serveur PoCodex, ou mode
 * hors-ligne. Si une session existe déjà (jeton) ou si le mode hors-ligne a été
 * choisi, on file directement sur le Codex — sauf si l'intent porte l'extra
 * [EXTRA_FORCER] (le lobby s'en sert pour proposer « se connecter »).
 */
class AccueilActivity : AppCompatActivity() {

    private lateinit var champPseudo: EditText
    private lateinit var champMdp: EditText
    private lateinit var champServeur: EditText
    private lateinit var btnConnexion: TextView
    private lateinit var btnCreer: TextView
    private lateinit var btnHorsLigne: TextView

    private var enCours = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-skip : session présente ou mode hors-ligne -> Codex direct.
        val forcer = intent.getBooleanExtra(EXTRA_FORCER, false)
        if (!forcer && (Compte.estConnecte || Compte.horsLigne)) {
            allerAuCodex()
            return
        }

        setContentView(R.layout.activity_accueil)

        champPseudo = findViewById(R.id.champPseudo)
        champMdp = findViewById(R.id.champMdp)
        champServeur = findViewById(R.id.champServeur)
        btnConnexion = findViewById(R.id.btnConnexion)
        btnCreer = findViewById(R.id.btnCreer)
        btnHorsLigne = findViewById(R.id.btnHorsLigne)

        champServeur.setText(Compte.serveur)

        btnConnexion.setOnClickListener { tenter(creation = false) }
        btnCreer.setOnClickListener { tenter(creation = true) }
        btnHorsLigne.setOnClickListener {
            Compte.horsLigne = true
            allerAuCodex()
        }
    }

    /** Lance une connexion (creation=false) ou une inscription (creation=true). */
    private fun tenter(creation: Boolean) {
        if (enCours) return

        val pseudo = champPseudo.text.toString().trim()
        val mdp = champMdp.text.toString()
        val serveur = champServeur.text.toString().trim().ifBlank { Compte.serveur }

        // Persiste l'URL serveur à chaque tentative.
        Compte.serveur = serveur

        // Validation locale (messages FR).
        if (pseudo.length < 3 || pseudo.length > 16) {
            Toast.makeText(this, getString(R.string.accueil_err_pseudo), Toast.LENGTH_SHORT).show()
            return
        }
        if (mdp.length < 6) {
            Toast.makeText(this, getString(R.string.accueil_err_mdp), Toast.LENGTH_SHORT).show()
            return
        }

        basculerChargement(true)
        lifecycleScope.launch {
            val resultat = runCatching {
                if (creation) ClientPoCodex.inscription(serveur, pseudo, mdp)
                else ClientPoCodex.connexion(serveur, pseudo, mdp)
            }
            resultat.onSuccess { infos ->
                Compte.memoriser(infos.jeton, infos.pseudo, infos.elo, infos.victoires, infos.defaites)
                allerAuCodex()
            }.onFailure { e ->
                basculerChargement(false)
                val msg = e.message?.takeIf { it.isNotBlank() } ?: "Serveur injoignable"
                Toast.makeText(this@AccueilActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Active/désactive les boutons et affiche « … » pendant l'appel réseau. */
    private fun basculerChargement(charge: Boolean) {
        enCours = charge
        btnConnexion.isEnabled = !charge
        btnCreer.isEnabled = !charge
        btnHorsLigne.isEnabled = !charge
        btnConnexion.alpha = if (charge) 0.6f else 1f
        btnCreer.alpha = if (charge) 0.6f else 1f
        btnConnexion.text =
            if (charge) getString(R.string.accueil_chargement) else getString(R.string.accueil_connexion)
        btnCreer.text =
            if (charge) getString(R.string.accueil_chargement) else getString(R.string.accueil_creer)
    }

    private fun allerAuCodex() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        /** Extra booléen : force l'affichage de l'accueil même si déjà connecté. */
        const val EXTRA_FORCER = "forcer"
    }
}
