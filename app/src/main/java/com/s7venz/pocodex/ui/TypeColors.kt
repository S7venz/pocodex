package com.s7venz.pocodex.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.s7venz.pocodex.R

/** Couleurs, noms FR et helpers visuels par type — direction « BESTIA » (carte holo). */
object TypeColors {

    /** Dégradé signature de chaque type : [clair, foncé]. */
    fun couleurs(type: String?): Pair<Int, Int> = when (type?.lowercase()) {
        "normal" -> 0xFFA8AFB6 to 0xFF7C858E
        "fire" -> 0xFFFF8A4C to 0xFFE8420F
        "water" -> 0xFF5AA9F0 to 0xFF2B6FD6
        "grass" -> 0xFF6BC56A to 0xFF36973F
        "electric" -> 0xFFFBD85D to 0xFFE6B000
        "ice" -> 0xFF79D6DA to 0xFF3FB3BC
        "fighting" -> 0xFFEE6175 to 0xFFC42741
        "poison" -> 0xFFC07BDB to 0xFF9A3FC0
        "ground" -> 0xFFDAA86B to 0xFFB97E3F
        "flying" -> 0xFFA6C0F0 to 0xFF6E8FE0
        "psychic" -> 0xFFFA82B0 to 0xFFEE4E8E
        "bug" -> 0xFFAECC4A to 0xFF7FA61F
        "rock" -> 0xFFCDB877 to 0xFFA88E45
        "ghost" -> 0xFF8474C0 to 0xFF5A4A9C
        "dragon" -> 0xFF6E80E0 to 0xFF3E50C8
        "dark" -> 0xFF6E6678 to 0xFF473F52
        "steel" -> 0xFF88B2C4 to 0xFF5A8294
        "fairy" -> 0xFFF4A8BE to 0xFFEC6F96
        else -> 0xFF888888 to 0xFF666666
    }.let { (a, b) -> a.toInt() to b.toInt() }

    /** Couleur claire du type. */
    fun clair(type: String?): Int = couleurs(type).first

    /** Couleur foncée (= couleur « pleine » utilisée pour les teintes solides). */
    fun color(type: String?): Int = couleurs(type).second

    /** Couleur de l'encre lisible sur le dégradé du type (blanc, sauf jaune). */
    fun encre(type: String?): Int =
        if (type?.lowercase() == "electric") 0xFF4A3A00.toInt() else Color.WHITE

    fun nomFr(type: String?): String = when (type?.lowercase()) {
        "fire" -> "Feu"
        "water" -> "Eau"
        "grass" -> "Plante"
        "electric" -> "Électrik"
        "psychic" -> "Psy"
        "ice" -> "Glace"
        "dragon" -> "Dragon"
        "dark" -> "Ténèbres"
        "fairy" -> "Fée"
        "poison" -> "Poison"
        "bug" -> "Insecte"
        "rock" -> "Roche"
        "ghost" -> "Spectre"
        "steel" -> "Acier"
        "fighting" -> "Combat"
        "ground" -> "Sol"
        "flying" -> "Vol"
        "normal" -> "Normal"
        else -> (type ?: "?").replaceFirstChar { it.uppercase() }
    }

    fun assombrir(c: Int, f: Float = 0.78f): Int =
        Color.rgb(
            (Color.red(c) * f).toInt(),
            (Color.green(c) * f).toInt(),
            (Color.blue(c) * f).toInt(),
        )

    fun eclaircir(c: Int, f: Float = 0.22f): Int =
        Color.rgb(
            (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
            (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
            (Color.blue(c) + (255 - Color.blue(c)) * f).toInt(),
        )

    /** Dégradé diagonal clair → foncé du type (cartes / en-têtes). */
    fun degrade(type: String?, rayonPx: Float = 0f): GradientDrawable {
        val (c0, c1) = couleurs(type)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c0, c1))
            .apply { cornerRadius = rayonPx }
    }

    /** Pastille pleine (couleur unique) — utilisée pour les statuts de combat. */
    fun chip(context: Context, texte: String, fond: Int, couleurTexte: Int = Color.WHITE): TextView {
        val bg = GradientDrawable().apply { setColor(fond) }
        return pastille(context, texte, bg, couleurTexte)
    }

    /** Pastille de type : dégradé signature + encre lisible + liseré blanc. */
    fun chipType(context: Context, typeKey: String?): TextView {
        val (c0, c1) = couleurs(typeKey)
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c0, c1))
        return pastille(context, nomFr(typeKey), bg, encre(typeKey))
    }

    private fun pastille(context: Context, texte: String, bg: GradientDrawable, ink: Int): TextView {
        val d = context.resources.displayMetrics.density
        bg.cornerRadius = 999 * d
        bg.setStroke((1.5f * d).toInt(), 0x99FFFFFF.toInt())
        return TextView(context).apply {
            text = texte
            setTextColor(ink)
            textSize = 12f
            typeface = ResourcesCompat.getFont(context, R.font.baloo2_black)
            includeFontPadding = false
            gravity = Gravity.CENTER
            setPadding((11 * d).toInt(), (5 * d).toInt(), (11 * d).toInt(), (5 * d).toInt())
            background = bg
        }
    }
}
