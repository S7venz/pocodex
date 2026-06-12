package com.s7venz.pocodex

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.s7venz.pocodex.data.AppDatabase
import com.s7venz.pocodex.data.PokedexRepository
import com.s7venz.pocodex.model.Pokemon
import com.s7venz.pocodex.ui.Deco
import kotlinx.coroutines.launch

/**
 * Ligue PoCodex : 4 combats enchaînés à difficulté croissante.
 * La progression est persistée dans SharedPreferences("ligue") :
 *  - `etape` (1..4) : prochaine étape à battre.
 *  - `titres` (Int) : nombre de fois où la Ligue a été bouclée.
 */
class LigueActivity : AppCompatActivity() {

    /** Une étape : dresseur, taille d'équipe, bornes de BST (percentiles). */
    private data class Etape(
        val numero: Int,
        val dresseur: String,
        val taille: Int,
        val percMin: Int,
        val percMax: Int,
    )

    private val etapes = listOf(
        Etape(1, "Dresseur Théo", 2, 0, 40),
        Etape(2, "Dresseuse Maya", 3, 30, 70),
        Etape(3, "Champion Aldo", 4, 55, 85),
        Etape(4, "Maître Orion", 6, 75, 100),
    )

    private val prefs by lazy { getSharedPreferences("ligue", Context.MODE_PRIVATE) }
    private val equipeDao by lazy { AppDatabase.get(this).equipeDao() }

    /** BST absolus triés (1..151), calculés une fois pour convertir les percentiles en bornes. */
    private var bstsTries: List<Int> = emptyList()
    private var equipeVide = true

    private val lanceurCombat = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { resultat ->
        when {
            resultat.resultCode == RESULT_OK -> avancer()
            // Vraie défaite uniquement (l'arène pose EXTRA_LIGUE_DEFAITE) ; un simple retour
            // arrière renvoie aussi RESULT_CANCELED mais sans cet extra → on ne réinitialise pas.
            resultat.data?.getBooleanExtra(CombatActivity.EXTRA_LIGUE_DEFAITE, false) == true -> {
                prefs.edit().putInt("etape", 1).apply()
                Toast.makeText(this, "Défaite ! La Ligue reprend du début.", Toast.LENGTH_LONG).show()
            }
        }
        charger()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ligue)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        charger()
    }

    private fun charger() {
        lifecycleScope.launch {
            if (bstsTries.isEmpty()) {
                bstsTries = PokedexRepository.tous().map { bst(it) }.sorted()
            }
            equipeVide = equipeDao.nombre() == 0
            afficher()
        }
    }

    /** BST (somme des 6 stats de base). */
    private fun bst(p: Pokemon): Int =
        p.pv + p.attaque + p.defense + p.attaqueSpe + p.defenseSpe + p.vitesse

    /** BST absolu au percentile [p] (0..100), via le rang le plus proche sur la liste triée. */
    private fun bstAuPercentile(p: Int): Int {
        if (bstsTries.isEmpty()) return 0
        val idx = Math.round((p / 100.0) * (bstsTries.size - 1)).toInt()
            .coerceIn(0, bstsTries.size - 1)
        return bstsTries[idx]
    }

    private fun afficher() {
        val etapeCourante = prefs.getInt("etape", 1).coerceIn(1, 4)
        val titres = prefs.getInt("titres", 0)

        val cptTitres = findViewById<TextView>(R.id.txtTitres)
        if (titres > 0) {
            cptTitres.text = "🏆 ×$titres"
            cptTitres.visibility = View.VISIBLE
        } else {
            cptTitres.visibility = View.GONE
        }

        val conteneur = findViewById<LinearLayout>(R.id.conteneurLigue)
        conteneur.removeAllViews()
        for (e in etapes) {
            val statut = when {
                e.numero < etapeCourante -> Statut.BATTUE
                e.numero == etapeCourante -> Statut.COURANTE
                else -> Statut.VERROUILLEE
            }
            conteneur.addView(carteEtape(e, statut))
        }
        if (equipeVide) {
            conteneur.addView(messageEquipeVide())
        }
    }

    private enum class Statut { BATTUE, COURANTE, VERROUILLEE }

    // ---------- Carte d'une étape ----------

    private fun carteEtape(e: Etape, statut: Statut): View {
        // Fond : vert (battue), rouge BESTIA (courante), gris (verrouillée).
        val fond = when (statut) {
            Statut.BATTUE -> Deco.carte3d(this, 0xFF6BC56A.toInt(), 0xFF36973F.toInt())
            Statut.COURANTE -> Deco.carte3d(this, 0xFFFF5B48.toInt(), 0xFFD61F0C.toInt())
            Statut.VERROUILLEE -> Deco.carte3d(this, 0xFF54545F.toInt(), 0xFF33333C.toInt())
        }
        val carte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = fond
            setPadding(d(16), d(15), d(16), d(15) + d(6))
            alpha = if (statut == Statut.VERROUILLEE) 0.55f else 1f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = d(14) }
        }

        // Ligne du haut : « ÉTAPE N » (pixel) + marqueur d'état (✓ / 🔒).
        val haut = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        haut.addView(TextView(this).apply {
            text = "ÉTAPE ${e.numero}"
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 9f
            typeface = ResourcesCompat.getFont(this@LigueActivity, R.font.press_start_2p)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val marqueur = when (statut) {
            Statut.BATTUE -> "✓"
            Statut.VERROUILLEE -> "🔒"
            Statut.COURANTE -> ""
        }
        if (marqueur.isNotEmpty()) {
            haut.addView(TextView(this).apply {
                text = marqueur
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 20f
                typeface = ResourcesCompat.getFont(this@LigueActivity, R.font.baloo2_black)
                includeFontPadding = false
            })
        }
        carte.addView(haut)

        // Nom du dresseur (Baloo black).
        carte.addView(TextView(this).apply {
            text = e.dresseur
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            typeface = ResourcesCompat.getFont(this@LigueActivity, R.font.baloo2_black)
            includeFontPadding = false
            setShadowLayer(3f, 0f, 1f, 0x4D000000)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = d(8) }
        })

        // Sous-titre (Outfit) : effectif + montée en puissance.
        carte.addView(TextView(this).apply {
            text = "${e.taille} Pokémon · niveau croissant"
            setTextColor(0xE6FFFFFF.toInt())
            textSize = 14f
            typeface = ResourcesCompat.getFont(this@LigueActivity, R.font.outfit)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = d(3) }
        })

        // Étape courante : gros bouton « ⚔ COMBATTRE ».
        if (statut == Statut.COURANTE) {
            val actif = !equipeVide
            val combat = TextView(this).apply {
                text = "⚔  COMBATTRE"
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 17f
                typeface = ResourcesCompat.getFont(this@LigueActivity, R.font.bungee)
                background = ResourcesCompat.getDrawable(resources, R.drawable.btn_red, theme)
                setPadding(d(20), d(13), d(20), d(13) + d(4))
                isEnabled = actif
                alpha = if (actif) 1f else 0.5f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = d(14) }
                setOnClickListener { if (actif) lancerEtape(e) }
            }
            carte.addView(combat)
        }
        return carte
    }

    private fun messageEquipeVide(): View = TextView(this).apply {
        text = getString(R.string.ligue_equipe_vide)
        gravity = Gravity.CENTER
        setTextColor(0xFF8A8A96.toInt())
        textSize = 14f
        typeface = ResourcesCompat.getFont(this@LigueActivity, R.font.baloo2_bold)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).also { it.topMargin = d(8) }
    }

    // ---------- Lancement & progression ----------

    private fun lancerEtape(e: Etape) {
        lifecycleScope.launch {
            val premier = equipeDao.tous().firstOrNull()?.id ?: return@launch
            val intent = Intent(this@LigueActivity, CombatActivity::class.java)
                .putExtra(CombatActivity.EXTRA_ID, premier)
                .putExtra(CombatActivity.EXTRA_LIGUE, true)
                .putExtra(CombatActivity.EXTRA_LIGUE_TAILLE, e.taille)
                .putExtra(CombatActivity.EXTRA_LIGUE_BST_MIN, bstAuPercentile(e.percMin))
                .putExtra(CombatActivity.EXTRA_LIGUE_BST_MAX, bstAuPercentile(e.percMax))
                .putExtra(CombatActivity.EXTRA_LIGUE_NOM, e.dresseur)
            lanceurCombat.launch(intent)
        }
    }

    /** Victoire : on passe à l'étape suivante ; après la 4ᵉ, on devient Champion. */
    private fun avancer() {
        val etape = prefs.getInt("etape", 1)
        val suivante = etape + 1
        if (suivante > 4) {
            val titres = prefs.getInt("titres", 0) + 1
            prefs.edit().putInt("etape", 1).putInt("titres", titres).apply()
            AlertDialog.Builder(this)
                .setTitle("🏆 CHAMPION DE LA LIGUE !")
                .setMessage("Tu as vaincu les 4 dresseurs et remporté la Ligue PoCodex !\nUn nouveau titre rejoint ta collection. 🎉")
                .setCancelable(false)
                .setPositiveButton("Génial !", null)
                .show()
        } else {
            prefs.edit().putInt("etape", suivante).apply()
        }
    }

    private fun d(v: Int): Int = Deco.dp(this, v.toFloat())
}
