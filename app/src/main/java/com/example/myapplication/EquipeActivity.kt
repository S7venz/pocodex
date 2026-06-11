package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.PokedexRepository
import com.example.myapplication.model.Pokemon
import com.example.myapplication.ui.Deco
import com.example.myapplication.ui.TypeColors
import kotlinx.coroutines.launch

class EquipeActivity : AppCompatActivity() {

    private val dao by lazy { AppDatabase.get(this).equipeDao() }
    private var equipe: List<Pokemon> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equipe)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnCombatEquipe).setOnClickListener {
            equipe.firstOrNull()?.let {
                startActivity(Intent(this, CombatActivity::class.java).putExtra(CombatActivity.EXTRA_ID, it.id))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        charger()
    }

    private fun charger() {
        lifecycleScope.launch {
            val ids = dao.tous().map { it.id }
            equipe = ids.mapNotNull { PokedexRepository.parId(it) }
            afficher()
        }
    }

    private fun afficher() {
        val conteneur = findViewById<LinearLayout>(R.id.conteneurEquipe)
        val btn = findViewById<TextView>(R.id.btnCombatEquipe)
        conteneur.removeAllViews()
        findViewById<TextView>(R.id.txtCount).text = "${equipe.size}/6"

        if (equipe.isEmpty()) {
            btn.visibility = View.GONE
            conteneur.addView(etatVide())
            return
        }
        btn.visibility = View.VISIBLE
        for (p in equipe) conteneur.addView(carte(p))
        repeat(6 - equipe.size) { conteneur.addView(slotVide()) }
    }

    // ---------- Carte d'un membre ----------

    private fun carte(p: Pokemon): View {
        val (c0, c1) = TypeColors.couleurs(p.typePrincipal)
        val rangee = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Deco.carte3d(this@EquipeActivity, c0, c1)
            setPadding(d(12), d(12), d(12), d(12) + d(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = d(12) }
        }

        val spriteBox = FrameLayout(this).apply {
            background = Deco.bloc(0x33FFFFFF, Deco.dpf(this@EquipeActivity, 13f))
            layoutParams = LinearLayout.LayoutParams(d(60), d(60))
        }
        val img = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            val pad = d(4)
            setPadding(pad, pad, pad, pad)
            load(p.artworkUrl) { crossfade(true) }
        }
        spriteBox.addView(img)

        val milieu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = d(13) }
        }
        val ligneNom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        ligneNom.addView(TextView(this).apply {
            text = p.nom
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 17f
            typeface = ResourcesCompat.getFont(this@EquipeActivity, R.font.baloo2_black)
            includeFontPadding = false
            setShadowLayer(3f, 0f, 1f, 0x4D000000)
        })
        ligneNom.addView(TextView(this).apply {
            text = p.numero
            setTextColor(0xD9FFFFFF.toInt())
            textSize = 7f
            typeface = ResourcesCompat.getFont(this@EquipeActivity, R.font.press_start_2p)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.marginStart = d(7); it.bottomMargin = d(2) }
        })
        milieu.addView(ligneNom)

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = d(7) }
        }
        for (t in p.types) {
            chips.addView(TypeColors.chipType(this, t).apply { textSize = 10f }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.marginEnd = d(5) })
        }
        milieu.addView(chips)

        val retirer = TextView(this).apply {
            text = getString(R.string.retirer)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            typeface = ResourcesCompat.getFont(this@EquipeActivity, R.font.baloo2_bold)
            background = Deco.bloc(0x33000000, Deco.dpf(this@EquipeActivity, 10f))
            setPadding(d(12), d(7), d(12), d(7))
            setOnClickListener {
                lifecycleScope.launch { dao.retirer(p.id); charger() }
            }
        }

        rangee.addView(spriteBox)
        rangee.addView(milieu)
        rangee.addView(retirer)
        return rangee
    }

    // ---------- Emplacement libre ----------

    private fun slotVide(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_empty_slot, theme)
        setPadding(d(18), d(18), d(18), d(18))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).also { it.bottomMargin = d(12) }
        addView(TextView(this@EquipeActivity).apply {
            text = "+  Emplacement libre"
            setTextColor(0xFF9AA0AC.toInt())
            textSize = 14f
            typeface = ResourcesCompat.getFont(this@EquipeActivity, R.font.baloo2_bold)
        })
    }

    // ---------- État vide ----------

    private fun etatVide(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(d(30), d(56), d(30), d(40))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        val cercle = FrameLayout(this@EquipeActivity).apply {
            background = Deco.anneau(this@EquipeActivity, 5f, 0xFFC8CCD6.toInt())
            layoutParams = LinearLayout.LayoutParams(d(130), d(130))
        }
        cercle.addView(TextView(this@EquipeActivity).apply {
            text = "?"
            setTextColor(0xFFC8CCD6.toInt())
            textSize = 22f
            typeface = ResourcesCompat.getFont(this@EquipeActivity, R.font.press_start_2p)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.gravity = Gravity.CENTER }
        })
        addView(cercle)
        addView(TextView(this@EquipeActivity).apply {
            text = "ÉQUIPE VIDE"
            setTextColor(0xFF3A3A46.toInt())
            textSize = 21f
            typeface = ResourcesCompat.getFont(this@EquipeActivity, R.font.bungee)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = d(24); it.bottomMargin = d(10) }
        })
        addView(TextView(this@EquipeActivity).apply {
            text = getString(R.string.equipe_vide)
            gravity = Gravity.CENTER
            setTextColor(0xFF8A8A96.toInt())
            textSize = 14f
            typeface = ResourcesCompat.getFont(this@EquipeActivity, R.font.outfit)
            layoutParams = LinearLayout.LayoutParams(d(250), LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = d(24) }
        })
        addView(TextView(this@EquipeActivity).apply {
            text = "Parcourir le Codex"
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            typeface = ResourcesCompat.getFont(this@EquipeActivity, R.font.baloo2_black)
            background = Deco.bouton3d(this@EquipeActivity, 0xFFFF5B48.toInt(), 0xFFD61F0C.toInt(), 0xFF7E1206.toInt())
            setPadding(d(26), d(14), d(26), d(14) + d(5))
            setOnClickListener { finish() }
        })
    }

    private fun d(v: Int): Int = Deco.dp(this, v.toFloat())
}
