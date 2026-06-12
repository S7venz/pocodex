package com.s7venz.pocodex

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import com.s7venz.pocodex.combat.Natures
import com.s7venz.pocodex.data.AppDatabase
import com.s7venz.pocodex.data.CaptureEntity
import com.s7venz.pocodex.data.FavoriEntity
import com.s7venz.pocodex.data.MembreEquipe
import com.s7venz.pocodex.data.PokedexRepository
import com.s7venz.pocodex.model.Pokemon
import com.s7venz.pocodex.ui.TypeColors
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private var pokeId: Int = 1
    private var nom: String = ""
    private var estFavori = false
    private var estDansEquipe = false
    private val db by lazy { AppDatabase.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        pokeId = intent.getIntExtra(EXTRA_ID, 1)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<FrameLayout>(R.id.btnFavori).setOnClickListener { basculerFavori() }
        findViewById<LinearLayout>(R.id.btnCombat).setOnClickListener {
            startActivity(Intent(this, CombatActivity::class.java).putExtra(CombatActivity.EXTRA_ID, pokeId))
        }
        findViewById<TextView>(R.id.btnEquipe).setOnClickListener { basculerEquipe() }
        charger()
    }

    private fun charger() {
        val progress = findViewById<ProgressBar>(R.id.detailProgress)
        progress.isVisible = true
        lifecycleScope.launch {
            try {
                val p = PokedexRepository.parId(pokeId)
                if (p != null) {
                    afficher(p)
                    estFavori = db.favoriDao().isFavorite(pokeId)
                    estDansEquipe = db.equipeDao().present(pokeId)
                    majBoutons()
                } else {
                    Toast.makeText(this@DetailActivity, getString(R.string.erreur_donnees), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, getString(R.string.erreur_donnees), Toast.LENGTH_LONG).show()
            } finally {
                progress.isVisible = false
            }
        }
    }

    private fun afficher(p: Pokemon) {
        nom = p.nom
        val d = resources.displayMetrics.density
        val couleur = TypeColors.color(p.typePrincipal)

        // En-tête : dégradé du type, coins bas arrondis
        val entete = findViewById<View>(R.id.entete)
        val r = 32 * d
        entete.background = TypeColors.degrade(p.typePrincipal).apply {
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        }
        window.statusBarColor = couleur

        findViewById<TextView>(R.id.txtNumero).text = p.numero
        findViewById<TextView>(R.id.txtNom).text = p.nom
        findViewById<ImageView>(R.id.imgArt).load(p.artworkUrl) { crossfade(true) }
        findViewById<TextView>(R.id.txtTaille).text = p.taille
        findViewById<TextView>(R.id.txtPoids).text = p.poids
        findViewById<TextView>(R.id.txtNature).text = Natures.pour(p.id).nomFr
        findViewById<TextView>(R.id.txtDescription).text = p.description

        val chips = findViewById<LinearLayout>(R.id.chipsTypes)
        chips.removeAllViews()
        for (t in p.types) {
            val chip = TypeColors.chipType(this, t)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.marginEnd = (7 * d).toInt() }
            chips.addView(chip, lp)
        }

        val cont = findViewById<LinearLayout>(R.id.conteneurStats)
        cont.removeAllViews()
        val stats = listOf(
            "PV" to p.pv,
            "Attaque" to p.attaque,
            "Défense" to p.defense,
            "Atq. Spé." to p.attaqueSpe,
            "Déf. Spé." to p.defenseSpe,
            "Vitesse" to p.vitesse,
        )
        for ((nomStat, valeur) in stats) {
            val ligne = layoutInflater.inflate(R.layout.item_stat, cont, false)
            ligne.findViewById<TextView>(R.id.statNom).text = nomStat
            ligne.findViewById<TextView>(R.id.statVal).text = valeur.toString()
            ligne.findViewById<ProgressBar>(R.id.statBar).apply {
                max = 200
                progress = valeur
                progressTintList = ColorStateList.valueOf(couleur)
            }
            cont.addView(ligne)
        }
    }

    private fun basculerFavori() {
        lifecycleScope.launch {
            if (estFavori) db.favoriDao().delete(pokeId) else db.favoriDao().insert(FavoriEntity(pokeId, nom))
            estFavori = !estFavori
            majBoutons()
        }
    }

    private fun basculerEquipe() {
        lifecycleScope.launch {
            val dao = db.equipeDao()
            if (estDansEquipe) {
                dao.retirer(pokeId)
                estDansEquipe = false
            } else {
                if (dao.nombre() >= 6) {
                    Toast.makeText(this@DetailActivity, getString(R.string.equipe_pleine), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                dao.ajouter(MembreEquipe(pokeId, dao.ordreMax() + 1))
                db.captureDao().ajouter(CaptureEntity(pokeId, false))
                estDansEquipe = true
                Toast.makeText(this@DetailActivity, "$nom rejoint l'équipe !", Toast.LENGTH_SHORT).show()
            }
            majBoutons()
        }
    }

    private fun majBoutons() {
        findViewById<ImageView>(R.id.imgFavori)
            .setImageResource(if (estFavori) R.drawable.ic_star else R.drawable.ic_star_border)
        findViewById<TextView>(R.id.btnEquipe).text = if (estDansEquipe) "✓" else "+"
    }

    companion object {
        const val EXTRA_ID = "extra_id"
    }
}
