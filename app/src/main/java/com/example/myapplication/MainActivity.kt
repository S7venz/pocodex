package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.PokedexRepository
import com.example.myapplication.model.Pokemon
import com.example.myapplication.ui.PokemonAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: PokemonAdapter
    private lateinit var progress: ProgressBar
    private lateinit var messageVide: TextView
    private lateinit var btnFavoris: ImageButton
    private val dao by lazy { AppDatabase.get(this).favoriDao() }

    private var tous: List<Pokemon> = emptyList()
    private var favIds: Set<Int> = emptySet()
    private var requete: String = ""
    private var montrerFavoris: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progress = findViewById(R.id.progress)
        messageVide = findViewById(R.id.messageVide)
        btnFavoris = findViewById(R.id.btnFavoris)

        adapter = PokemonAdapter { ouvrirDetail(it) }
        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = this@MainActivity.adapter
        }

        findViewById<EditText>(R.id.editRecherche).doAfterTextChanged { texte ->
            requete = texte?.toString().orEmpty()
            afficher()
        }

        btnFavoris.setOnClickListener {
            montrerFavoris = !montrerFavoris
            majIconeFavoris()
            afficher()
        }
        findViewById<ImageButton>(R.id.btnEquipe).setOnClickListener {
            startActivity(Intent(this, EquipeActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnOnline).setOnClickListener {
            startActivity(Intent(this, OnlineLobbyActivity::class.java))
        }

        charger()
    }

    override fun onResume() {
        super.onResume()
        if (tous.isNotEmpty()) {
            lifecycleScope.launch {
                favIds = dao.getAll().map { it.id }.toSet()
                afficher()
            }
        }
    }

    private fun ouvrirDetail(p: Pokemon) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, p.id))
    }

    private fun charger() {
        progress.isVisible = true
        messageVide.isVisible = false
        lifecycleScope.launch {
            try {
                tous = PokedexRepository.tous()
                favIds = dao.getAll().map { it.id }.toSet()
                afficher()
            } catch (e: Exception) {
                android.util.Log.e("Pokedex", "Erreur chargement : ${e.javaClass.simpleName} ${e.message}", e)
                messageVide.text = getString(R.string.erreur_reseau)
                messageVide.isVisible = true
                Toast.makeText(this@MainActivity, getString(R.string.erreur_reseau), Toast.LENGTH_LONG).show()
            } finally {
                progress.isVisible = false
            }
        }
    }

    private fun afficher() {
        val filtres = tous.filter { p ->
            p.nom.contains(requete, ignoreCase = true) &&
                (!montrerFavoris || favIds.contains(p.id))
        }
        adapter.submit(filtres)

        val vide = filtres.isEmpty()
        messageVide.isVisible = vide
        if (vide) {
            messageVide.text =
                if (montrerFavoris) getString(R.string.aucun_favori)
                else getString(R.string.aucun_resultat)
        }
    }

    private fun majIconeFavoris() {
        btnFavoris.setImageResource(if (montrerFavoris) R.drawable.ic_star else R.drawable.ic_star_border)
    }
}
