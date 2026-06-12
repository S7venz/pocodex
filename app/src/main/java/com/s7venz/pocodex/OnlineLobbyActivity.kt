package com.s7venz.pocodex

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.s7venz.pocodex.data.AppDatabase
import com.s7venz.pocodex.network.ClientPoCodex
import com.s7venz.pocodex.ui.Deco
import kotlinx.coroutines.launch

/**
 * Lobby du combat en ligne : créer une partie (l'hôte reçoit le code du serveur)
 * ou en rejoindre une par code. Le jeu en ligne exige un compte : si le joueur
 * n'est pas connecté, on affiche un panneau « Connecte-toi pour jouer en ligne »
 * qui renvoie vers l'accueil.
 */
class OnlineLobbyActivity : AppCompatActivity() {

    private val equipeDao by lazy { AppDatabase.get(this).equipeDao() }

    // Champs de la carte profil (rafraîchis dans onResume) — null si non connecté.
    private var profilElo: TextView? = null
    private var profilScore: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Compte.estConnecte) {
            setContentView(panneauConnexion())
            return
        }

        setContentView(R.layout.activity_online_lobby)

        // Carte profil BESTIA, insérée en tête du contenu (au-dessus de Créer/Rejoindre).
        findViewById<LinearLayout>(R.id.lobbyContenu).addView(carteProfil(), 0)

        // « Créer une partie » : rôle hôte, SANS code (il viendra du serveur).
        findViewById<TextView>(R.id.txtCode).text = "—"

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.btnCreer).setOnClickListener {
            lancer(OnlineCombatActivity.ROLE_HOST, "")
        }
        findViewById<TextView>(R.id.btnRejoindre).setOnClickListener {
            val saisi = findViewById<EditText>(R.id.editCode).text.toString().trim().uppercase()
            if (saisi.length < 4) {
                Toast.makeText(this, "Entre un code valide", Toast.LENGTH_SHORT).show()
            } else {
                lancer(OnlineCombatActivity.ROLE_GUEST, saisi)
            }
        }
    }

    private fun lancer(role: String, code: String) {
        lifecycleScope.launch {
            val monId = equipeDao.tous().firstOrNull()?.id ?: 25 // défaut : Pikachu
            startActivity(
                Intent(this@OnlineLobbyActivity, OnlineCombatActivity::class.java)
                    .putExtra(OnlineCombatActivity.EXTRA_ROLE, role)
                    .putExtra(OnlineCombatActivity.EXTRA_CODE, code)
                    .putExtra(OnlineCombatActivity.EXTRA_MONID, monId),
            )
        }
    }

    /**
     * Au retour sur le lobby (notamment après un combat classé), on rafraîchit
     * silencieusement le profil depuis le serveur. En cas d'échec, on garde le
     * cache local ([Compte]) — l'en-tête reste affiché avec les dernières valeurs.
     */
    override fun onResume() {
        super.onResume()
        if (!Compte.estConnecte) return
        majProfil() // affiche d'abord le cache (instantané)
        lifecycleScope.launch {
            runCatching { ClientPoCodex.moi(Compte.serveur, Compte.jeton) }
                .onSuccess { infos ->
                    Compte.memoriser(infos.jeton, infos.pseudo, infos.elo, infos.victoires, infos.defaites)
                    majProfil()
                }
        }
    }

    /** Met à jour les libellés ELO / V·D de la carte profil depuis le cache [Compte]. */
    private fun majProfil() {
        profilElo?.text = "ELO ${Compte.elo}"
        profilScore?.text = "V ${Compte.victoires} · D ${Compte.defaites}"
    }

    /** Carte profil BESTIA : pseudo, ELO, bilan V/D, bouton « Déconnexion ». */
    private fun carteProfil(): LinearLayout {
        val carte = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Deco.carte3d(
                context,
                ContextCompat.getColor(context, R.color.panneau_haut),
                ContextCompat.getColor(context, R.color.panneau),
            )
            setPadding(Deco.dp(context, 18f), Deco.dp(context, 16f), Deco.dp(context, 14f), Deco.dp(context, 18f))
        }

        // Bloc texte (pseudo + ELO + bilan), prend l'espace restant.
        val infos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pseudo = TextView(this).apply {
            text = Compte.pseudo.ifBlank { "Joueur" }
            typeface = ResourcesCompat.getFont(context, R.font.baloo2_black)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
        }
        profilElo = TextView(this).apply {
            typeface = ResourcesCompat.getFont(context, R.font.press_start_2p)
            setTextColor(ContextCompat.getColor(context, R.color.or))
            textSize = 11f
            setPadding(0, Deco.dp(context, 6f), 0, 0)
        }
        profilScore = TextView(this).apply {
            typeface = ResourcesCompat.getFont(context, R.font.outfit)
            setTextColor(ContextCompat.getColor(context, R.color.gris4))
            textSize = 13f
            setPadding(0, Deco.dp(context, 5f), 0, 0)
        }
        infos.addView(pseudo)
        infos.addView(profilElo)
        infos.addView(profilScore)

        val bouton = TextView(this).apply {
            text = "Déconnexion"
            typeface = ResourcesCompat.getFont(context, R.font.outfit_semibold)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            background = Deco.bloc(
                ContextCompat.getColor(context, R.color.gris_btn2),
                Deco.dpf(context, 10f),
            )
            setPadding(Deco.dp(context, 14f), Deco.dp(context, 9f), Deco.dp(context, 14f), Deco.dp(context, 9f))
            setOnClickListener {
                Compte.deconnecter()
                startActivity(
                    Intent(this@OnlineLobbyActivity, AccueilActivity::class.java)
                        .putExtra(AccueilActivity.EXTRA_FORCER, true),
                )
                finish()
            }
        }

        carte.addView(infos, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        carte.addView(bouton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        // Marge sous la carte (séparation avec le bloc « Créer »).
        carte.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = Deco.dp(this@OnlineLobbyActivity, 18f) }

        majProfil()
        return carte
    }

    /** Panneau BESTIA affiché quand le joueur n'est pas connecté. */
    private fun panneauConnexion(): ScrollView {
        val nuit = ContextCompat.getColor(this, R.color.nuit)
        val racine = ScrollView(this).apply {
            setBackgroundColor(nuit)
            fitsSystemWindows = true
        }
        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Deco.dp(context, 28f), Deco.dp(context, 56f), Deco.dp(context, 28f), Deco.dp(context, 28f))
        }

        val carte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Deco.carte3d(
                context,
                ContextCompat.getColor(context, R.color.rouge_clair),
                ContextCompat.getColor(context, R.color.rouge),
            )
            setPadding(Deco.dp(context, 24f), Deco.dp(context, 28f), Deco.dp(context, 24f), Deco.dp(context, 28f))
        }

        val embleme = TextView(this).apply {
            text = "🌐"
            textSize = 44f
            gravity = Gravity.CENTER
        }
        val titre = TextView(this).apply {
            text = "Connecte-toi\npour jouer en ligne"
            typeface = ResourcesCompat.getFont(context, R.font.bungee)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            gravity = Gravity.CENTER
            setShadowLayer(0.01f, 0f, Deco.dpf(context, 2f), ContextCompat.getColor(context, R.color.rouge_fonce))
            setPadding(0, Deco.dp(context, 10f), 0, 0)
        }
        val sous = TextView(this).apply {
            text = "Un compte est nécessaire pour le classement ELO et les combats en temps réel."
            typeface = ResourcesCompat.getFont(context, R.font.outfit)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, Deco.dp(context, 8f), 0, Deco.dp(context, 20f))
        }
        val bouton = TextView(this).apply {
            text = "SE CONNECTER"
            typeface = ResourcesCompat.getFont(context, R.font.baloo2_black)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.btn_red)
            setPadding(0, Deco.dp(context, 14f), 0, Deco.dp(context, 18f))
            setOnClickListener {
                startActivity(
                    Intent(this@OnlineLobbyActivity, AccueilActivity::class.java)
                        .putExtra(AccueilActivity.EXTRA_FORCER, true),
                )
                finish()
            }
        }
        val boutonLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        carte.addView(embleme)
        carte.addView(titre)
        carte.addView(sous)
        carte.addView(bouton, boutonLp)

        val retour = TextView(this).apply {
            text = "Retour"
            typeface = ResourcesCompat.getFont(context, R.font.outfit)
            setTextColor(ContextCompat.getColor(context, R.color.gris4))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, Deco.dp(context, 22f), 0, 0)
            setOnClickListener { finish() }
        }

        colonne.addView(carte, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        colonne.addView(retour)
        racine.addView(colonne)
        return racine
    }
}
