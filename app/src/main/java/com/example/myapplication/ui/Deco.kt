package com.example.myapplication.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/** Fabriques de drawables « game feel » construites en code (ombres dures, foil, reliefs). */
object Deco {

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density).toInt()
    fun dpf(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun gradient(
        c0: Int,
        c1: Int,
        rayonPx: Float,
        orient: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR,
    ): GradientDrawable = GradientDrawable(orient, intArrayOf(c0, c1)).apply { cornerRadius = rayonPx }

    /** Bouton « chunky 3D » : face en relief + ombre dure portée dessous. */
    fun bouton3d(
        ctx: Context,
        c0: Int,
        c1: Int,
        ombre: Int,
        rayonDp: Float = 13f,
        liftDp: Float = 5f,
        liseré: Boolean = true,
    ): LayerDrawable {
        val r = dpf(ctx, rayonDp)
        val face = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c0, c1)).apply {
            cornerRadius = r
            if (liseré) setStroke(dp(ctx, 2f), 0x40FFFFFF)
        }
        val sh = GradientDrawable().apply { setColor(ombre); cornerRadius = r }
        return LayerDrawable(arrayOf(sh, face)).apply { setLayerInset(1, 0, 0, 0, dp(ctx, liftDp)) }
    }

    /** Carte teintée par type, avec ombre dure et liseré foil (équipe, cartes pleines). */
    fun carte3d(ctx: Context, c0: Int, c1: Int, rayonDp: Float = 18f, liftDp: Float = 6f): LayerDrawable {
        val r = dpf(ctx, rayonDp)
        val face = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c0, c1)).apply {
            cornerRadius = r
            setStroke(dp(ctx, 2f), 0x8CFFFFFF.toInt())
        }
        val sh = GradientDrawable().apply { setColor(0x33000000); cornerRadius = r }
        return LayerDrawable(arrayOf(sh, face)).apply { setLayerInset(1, 0, 0, 0, dp(ctx, liftDp)) }
    }

    /** Rectangle arrondi simple (fond plat). */
    fun bloc(couleur: Int, rayonPx: Float, strokeDp: Int = 0, strokeColor: Int = 0, ctx: Context? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(couleur)
            cornerRadius = rayonPx
            if (strokeDp > 0 && ctx != null) setStroke(dp(ctx, strokeDp.toFloat()), strokeColor)
        }

    /** Reflet holographique diagonal (foil). */
    fun sheen(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            intArrayOf(0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF),
        )

    /** Anneau (cercle évidé) pour les filigranes « orbe ». */
    fun anneau(ctx: Context, strokeDp: Float, couleur: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(ctx, strokeDp), couleur)
        }

    /** Plateforme ovale de l'arène (herbe). */
    fun plateforme(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF7FC96A.toInt(), 0xFF3F8A3A.toInt()))
            .apply { shape = GradientDrawable.OVAL }

    /** Décor de l'arène : ciel → herbe (multi-stops). */
    fun fondArene(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                0xFF7EC3F0.toInt(), 0xFFA9DCF5.toInt(), 0xFFCFEEDE.toInt(),
                0xFF8FD17A.toInt(), 0xFF5CB85C.toInt(),
            ),
        )
}
