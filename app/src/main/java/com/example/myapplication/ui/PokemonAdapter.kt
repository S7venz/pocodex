package com.example.myapplication.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.myapplication.R
import com.example.myapplication.model.Pokemon

/** Grille des Pokémon : carte colorée selon le type principal + badges de types. */
class PokemonAdapter(
    private val onClick: (Pokemon) -> Unit,
) : RecyclerView.Adapter<PokemonAdapter.VH>() {

    private val items = mutableListOf<Pokemon>()

    fun submit(liste: List<Pokemon>) {
        items.clear()
        items.addAll(liste)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_pokemon, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val fond = v.findViewById<View>(R.id.carteFond)
        private val img = v.findViewById<ImageView>(R.id.imgPokemon)
        private val nom = v.findViewById<TextView>(R.id.txtNom)
        private val num = v.findViewById<TextView>(R.id.txtNumero)
        private val chips = v.findViewById<LinearLayout>(R.id.chipsContainer)

        fun bind(p: Pokemon) {
            val d = itemView.resources.displayMetrics.density
            num.text = p.numero
            nom.text = p.nom
            fond.background = TypeColors.degrade(p.typePrincipal, 18 * d)
            img.load(p.artworkUrl) { crossfade(true) }

            chips.removeAllViews()
            for (t in p.types) {
                val chip = TypeColors.chipType(itemView.context, t).apply { textSize = 11f }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.marginEnd = (6 * d).toInt() }
                chips.addView(chip, lp)
            }

            itemView.setOnClickListener { onClick(p) }
        }
    }
}
