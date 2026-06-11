package com.s7venz.pocodex

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import com.s7venz.pocodex.combat.Attaque
import com.s7venz.pocodex.combat.Combattant
import com.s7venz.pocodex.combat.Inventaire
import com.s7venz.pocodex.combat.MoteurCombat
import com.s7venz.pocodex.combat.Statut
import com.s7venz.pocodex.data.AppDatabase
import com.s7venz.pocodex.data.MembreEquipe
import com.s7venz.pocodex.data.PokedexRepository
import com.s7venz.pocodex.ui.Deco
import com.s7venz.pocodex.ui.TypeColors
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CombatActivity : AppCompatActivity() {

    private enum class Etat { MENU, ATTAQUE, SAC, POKEMON, FORCE }

    private var joueurId = 1
    private lateinit var equipe: MutableList<Combattant>
    private var actifIndex = 0
    private lateinit var advEquipe: MutableList<Combattant>
    private var advActifIndex = 0
    private var etat = Etat.MENU
    private var enCours = false

    private val equipeDao by lazy { AppDatabase.get(this).equipeDao() }

    private val vibreur: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
    }

    private fun actif(): Combattant = equipe[actifIndex]
    private fun advActif(): Combattant = advEquipe[advActifIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_combat)
        setSupportActionBar(findViewById(R.id.combatToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.combat_titre)
        window.statusBarColor = 0xFFD61F0C.toInt()

        joueurId = intent.getIntExtra(EXTRA_ID, 1)
        demarrer()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun demarrer() {
        findViewById<LinearLayout>(R.id.movesContainer).removeAllViews()
        findViewById<TextView>(R.id.logText).text = ""
        findViewById<ProgressBar>(R.id.combatProgress).isVisible = true
        enCours = true

        lifecycleScope.launch {
            try {
                val ids = equipeDao.tous().map { it.id }.toMutableList()
                ids.remove(joueurId)
                ids.add(0, joueurId)
                equipe = ids.distinct().take(6)
                    .mapNotNull { id -> PokedexRepository.parId(id)?.let { MoteurCombat.depuisPokemon(it) } }
                    .toMutableList()
                if (equipe.isEmpty()) {
                    equipe = mutableListOf(MoteurCombat.depuisPokemon(PokedexRepository.tous().first()))
                }
                actifIndex = 0

                val advIds = (1..151).toList().shuffled().filter { it !in ids }.take(3)
                advEquipe = advIds
                    .mapNotNull { id -> PokedexRepository.parId(id)?.let { MoteurCombat.depuisPokemon(it) } }
                    .toMutableList()
                advActifIndex = 0

                bindAdversaire()
                bindJoueur()
                etat = Etat.MENU
                afficherMenu()
                log("Un Dresseur veut se battre !")
                log("Le Dresseur envoie ${advActif().nom} !")
                log("En avant, ${actif().nom} !")
                enCours = false
            } catch (e: Exception) {
                Toast.makeText(this@CombatActivity, getString(R.string.erreur_reseau), Toast.LENGTH_LONG).show()
                finish()
            } finally {
                findViewById<ProgressBar>(R.id.combatProgress).isVisible = false
            }
        }
    }

    // ---------- Plaques ----------

    private fun bindAdversaire() {
        findViewById<TextView>(R.id.advNom).text = advActif().nom
        val img = findViewById<ImageView>(R.id.advSprite)
        img.alpha = 1f; img.translationY = 0f; img.clearColorFilter()
        img.load(advActif().spriteUrl) { crossfade(true) }
        remplirChips(findViewById(R.id.advChips), advActif())
        setHp(findViewById(R.id.advHpBar), findViewById(R.id.advHpTxt), advActif())
        entree(img, depuisDroite = true)
    }

    private fun bindJoueur() {
        findViewById<TextView>(R.id.joueurNom).text = actif().nom
        val img = findViewById<ImageView>(R.id.joueurSprite)
        img.alpha = 1f; img.translationY = 0f; img.clearColorFilter()
        img.load(actif().spriteUrl) { crossfade(true) }
        remplirChips(findViewById(R.id.joueurChips), actif())
        setHp(findViewById(R.id.joueurHpBar), findViewById(R.id.joueurHpTxt), actif())
        entree(img, depuisDroite = false)
    }

    private fun remplirChips(container: LinearLayout, c: Combattant) {
        container.removeAllViews()
        val d = resources.displayMetrics.density
        for (t in c.types) {
            val chip = TypeColors.chipType(this, t).apply { textSize = 9f }
            container.addView(chip, marge(d))
        }
        c.statut?.let { s ->
            container.addView(TypeColors.chip(this, s.court, s.couleur).apply { textSize = 10f }, marge(d))
        }
    }

    private fun marge(d: Float) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).also { it.marginEnd = (5 * d).toInt() }

    private fun setHp(bar: ProgressBar, txt: TextView, c: Combattant) {
        bar.max = c.pvMax
        bar.progress = c.pv
        bar.progressTintList = ColorStateList.valueOf(couleurPv(c.pv, c.pvMax))
        txt.text = "${c.pv} / ${c.pvMax} PV"
    }

    // ---------- Menus ----------

    private fun afficherMenu() {
        val c = findViewById<LinearLayout>(R.id.movesContainer)
        c.removeAllViews()
        when (etat) {
            Etat.MENU -> {
                val r1 = rangee()
                r1.addView(boutonGrille("ATTAQUE", faceRouge(), 0xFFFFFFFF.toInt()) { etat = Etat.ATTAQUE; afficherMenu() })
                r1.addView(boutonGrille("SAC", faceOr(), 0xFF5A3D00.toInt()) { etat = Etat.SAC; afficherMenu() })
                c.addView(r1)
                val r2 = rangee()
                r2.addView(boutonGrille("POKÉMON", faceBleu(), 0xFFFFFFFF.toInt()) { etat = Etat.POKEMON; afficherMenu() })
                r2.addView(boutonGrille("FUITE", faceGris(), 0xFFFFFFFF.toInt()) { finish() })
                c.addView(r2)
            }
            Etat.ATTAQUE -> {
                val atks = actif().attaques
                var i = 0
                while (i < atks.size) {
                    val r = rangee()
                    r.addView(boutonAttaque(atks[i]))
                    if (i + 1 < atks.size) r.addView(boutonAttaque(atks[i + 1])) else r.addView(remplisseur())
                    c.addView(r)
                    i += 2
                }
                c.addView(boutonPleine("‹  RETOUR", faceGris(), 0xFFFFFFFF.toInt(), true) { etat = Etat.MENU; afficherMenu() })
            }
            Etat.SAC -> {
                val dispo = Inventaire.disponibles()
                if (dispo.isEmpty()) log("Le sac est vide !")
                for (ligne in dispo) {
                    val face = if (ligne.objet.capture) faceRouge() else faceVert()
                    c.addView(boutonPleine("${ligne.objet.nom}   ×${ligne.quantite}", face, 0xFFFFFFFF.toInt(), true) { jouerObjet(ligne) })
                }
                c.addView(boutonPleine("‹  RETOUR", faceGris(), 0xFFFFFFFF.toInt(), true) { etat = Etat.MENU; afficherMenu() })
            }
            Etat.POKEMON, Etat.FORCE -> {
                equipe.forEachIndexed { i, m ->
                    val dispo = m.enVie && i != actifIndex
                    val txt = when {
                        !m.enVie -> "${m.nom}   K.O."
                        i == actifIndex -> "${m.nom}   (au combat)"
                        else -> "${m.nom}   ${m.pv}/${m.pvMax}"
                    }
                    val (a, b) = TypeColors.couleurs(m.types.firstOrNull())
                    val face = if (dispo) Deco.bouton3d(this, a, b, TypeColors.assombrir(b, 0.55f)) else faceGris()
                    c.addView(boutonPleine(txt, face, TypeColors.encre(m.types.firstOrNull()), dispo) {
                        jouerSwitch(i, forcer = etat == Etat.FORCE)
                    })
                }
                if (etat != Etat.FORCE) c.addView(boutonPleine("‹  RETOUR", faceGris(), 0xFFFFFFFF.toInt(), true) { etat = Etat.MENU; afficherMenu() })
            }
        }
    }

    // ---------- Boutons « chunky 3D » ----------

    private fun rangee(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).also { it.topMargin = Deco.dp(this@CombatActivity, 9f) }
    }

    private fun faceRouge() = Deco.bouton3d(this, 0xFFFF5B48.toInt(), 0xFFD61F0C.toInt(), 0xFF7E1206.toInt())
    private fun faceOr() = Deco.bouton3d(this, 0xFFFFC94D.toInt(), 0xFFE6A01F.toInt(), 0xFFA06F10.toInt())
    private fun faceBleu() = Deco.bouton3d(this, 0xFF5AA9F0.toInt(), 0xFF2B6FD6.toInt(), 0xFF1A4A96.toInt())
    private fun faceGris() = Deco.bouton3d(this, 0xFF54545F.toInt(), 0xFF33333C.toInt(), 0xFF1C1C22.toInt())
    private fun faceVert() = Deco.bouton3d(this, 0xFF6BC56A.toInt(), 0xFF36973F.toInt(), 0xFF1F5C25.toInt())

    private fun boutonGrille(texte: String, face: android.graphics.drawable.Drawable, ink: Int, action: () -> Unit): View {
        val v = construireBouton(texte, null, face, ink, true, action)
        v.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .also { it.marginStart = Deco.dp(this, 5f); it.marginEnd = Deco.dp(this, 5f) }
        return v
    }

    private fun boutonAttaque(atk: Attaque): View {
        val (a, b) = TypeColors.couleurs(atk.type)
        val face = Deco.bouton3d(this, a, b, TypeColors.assombrir(b, 0.55f))
        val v = construireBouton(atk.nom, TypeColors.nomFr(atk.type).uppercase(), face, TypeColors.encre(atk.type), true) { jouerAttaque(atk) }
        v.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .also { it.marginStart = Deco.dp(this, 5f); it.marginEnd = Deco.dp(this, 5f) }
        return v
    }

    private fun remplisseur(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            .also { it.marginStart = Deco.dp(this@CombatActivity, 5f); it.marginEnd = Deco.dp(this@CombatActivity, 5f) }
    }

    private fun boutonPleine(texte: String, face: android.graphics.drawable.Drawable, ink: Int, actif: Boolean, action: () -> Unit): View {
        val v = construireBouton(texte, null, face, ink, actif, action)
        v.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).also { it.topMargin = Deco.dp(this, 9f) }
        return v
    }

    private fun construireBouton(
        texte: String,
        sous: String?,
        face: android.graphics.drawable.Drawable,
        ink: Int,
        actif: Boolean,
        action: () -> Unit,
    ): LinearLayout {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = face
            isEnabled = actif
            alpha = if (actif) 1f else 0.5f
            val padH = Deco.dp(this@CombatActivity, 13f)
            setPadding(padH, Deco.dp(this@CombatActivity, 12f), padH, Deco.dp(this@CombatActivity, 17f))
            setOnClickListener { if (!enCours && actif) action() }
        }
        ll.addView(TextView(this).apply {
            text = texte
            setTextColor(ink)
            textSize = 15f
            typeface = ResourcesCompat.getFont(this@CombatActivity, R.font.baloo2_black)
            includeFontPadding = false
            gravity = Gravity.CENTER
        })
        if (sous != null) {
            ll.addView(TextView(this).apply {
                text = sous
                setTextColor(ink)
                alpha = 0.9f
                textSize = 6f
                typeface = ResourcesCompat.getFont(this@CombatActivity, R.font.press_start_2p)
                includeFontPadding = false
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = Deco.dp(this@CombatActivity, 5f) }
            })
        }
        return ll
    }

    // ---------- Tour de jeu ----------

    private fun jouerAttaque(move: Attaque) {
        enCours = true
        etat = Etat.MENU
        afficherMenu()
        lifecycleScope.launch {
            val moveAdv = MoteurCombat.choisirIA(advActif(), actif())
            val sequence = if (actif().vitesse >= advActif().vitesse) listOf(true, false) else listOf(false, true)
            for (cestJoueur in sequence) {
                val att = if (cestJoueur) actif() else advActif()
                val def = if (cestJoueur) advActif() else actif()
                val mv = if (cestJoueur) move else moveAdv
                val (peut, msg) = MoteurCombat.peutAgir(att)
                if (msg != null) log(msg)
                if (!peut) { delay(650); continue }
                if (executerAttaque(att, def, mv, cestJoueur)) return@launch
                delay(650)
            }
            if (appliquerStatutsFinTour()) return@launch
            finTour()
        }
    }

    private fun jouerObjet(ligne: Inventaire.Ligne) {
        if (ligne.objet.capture) { tenterCapture(ligne); return }
        enCours = true
        etat = Etat.MENU
        afficherMenu()
        lifecycleScope.launch {
            val avant = actif().pv
            actif().pv = (actif().pv + ligne.objet.soin).coerceAtMost(actif().pvMax)
            Inventaire.consommer(ligne.objet)
            log("Tu utilises ${ligne.objet.nom} ! ${actif().nom} récupère ${actif().pv - avant} PV.")
            animerHp(findViewById(R.id.joueurHpBar), findViewById(R.id.joueurHpTxt), actif())
            delay(750)
            riposteEtFin()
        }
    }

    private fun tenterCapture(ligne: Inventaire.Ligne) {
        enCours = true
        etat = Etat.MENU
        afficherMenu()
        lifecycleScope.launch {
            Inventaire.consommer(ligne.objet)
            log("Tu lances une ${ligne.objet.nom} ! …")
            secouer(findViewById(R.id.advSprite))
            delay(900)
            val ratio = advActif().pv.toDouble() / advActif().pvMax
            val chance = (0.30 + 0.55 * (1 - ratio)).coerceIn(0.05, 0.95)
            if ((1..100).random() <= (chance * 100).toInt()) {
                log("Gagné ! ${advActif().nom} est capturé ! 🎉")
                vibrer(120)
                val n = equipeDao.nombre()
                if (n < 6) equipeDao.ajouter(MembreEquipe(advActif().id, n))
                advActif().pv = 0
                animerKo(findViewById(R.id.advSprite))
                delay(700)
                val suivant = advEquipe.indexOfFirst { it.enVie }
                if (suivant >= 0) {
                    advActifIndex = suivant; bindAdversaire()
                    log("Le Dresseur envoie ${advActif().nom} !"); finTour()
                } else {
                    finPartie("Capturé !", "Bien joué ! Tu as gagné le combat.")
                }
            } else {
                log("Oh non ! ${advActif().nom} s'est échappé !")
                delay(500)
                riposteEtFin()
            }
        }
    }

    private fun jouerSwitch(index: Int, forcer: Boolean) {
        if (index == actifIndex || !equipe[index].enVie) return
        val ancien = actif().nom
        actifIndex = index
        bindJoueur()
        log("$ancien, reviens ! En avant, ${actif().nom} !")
        if (forcer) {
            etat = Etat.MENU; afficherMenu(); enCours = false
        } else {
            enCours = true; etat = Etat.MENU; afficherMenu()
            lifecycleScope.launch { delay(700); riposteEtFin() }
        }
    }

    private suspend fun riposteEtFin() {
        val (peut, msg) = MoteurCombat.peutAgir(advActif())
        if (msg != null) log(msg)
        if (peut) {
            delay(400)
            val ia = MoteurCombat.choisirIA(advActif(), actif())
            if (executerAttaque(advActif(), actif(), ia, cibleEstAdversaire = false)) return
        }
        if (appliquerStatutsFinTour()) return
        finTour()
    }

    /** Exécute une attaque (avec tous les effets). Renvoie true si un K.O. a été géré. */
    private suspend fun executerAttaque(att: Combattant, def: Combattant, atk: Attaque, cibleEstAdversaire: Boolean): Boolean {
        val r = MoteurCombat.degats(att, def, atk)
        val attaqImg = findViewById<ImageView>(if (cibleEstAdversaire) R.id.joueurSprite else R.id.advSprite)
        val cibleImg = findViewById<ImageView>(if (cibleEstAdversaire) R.id.advSprite else R.id.joueurSprite)

        if (r.rate) {
            log("${att.nom} utilise ${atk.nom}… mais ça rate !")
            texteFlottant(cibleImg, "Raté !", 0xFFBBBBBB.toInt(), 22f)
            return false
        }

        lunge(attaqImg, cibleEstAdversaire)
        delay(120)
        animerProjectile(atk.type, attaqImg, cibleImg)
        delay(380)

        def.pv = (def.pv - r.degats).coerceAtLeast(0)
        log("${att.nom} utilise ${atk.nom} !" + (if (r.critique) " Coup critique !" else "") + "  (-${r.degats} PV)")
        MoteurCombat.messageEfficacite(r.mult).takeIf { it.isNotEmpty() }?.let { log(it) }

        flashCible(cibleImg)
        secouer(cibleImg)
        texteFlottant(cibleImg, if (r.critique) "-${r.degats} !" else "-${r.degats}",
            if (r.critique) 0xFFFFD54F.toInt() else 0xFFFFFFFF.toInt(), if (r.critique) 32f else 24f)
        vibrer(if (r.critique) 70 else 25)
        if (r.critique) { tremblerEcran(); ecranFlash(0xFFFFFFFF.toInt()) }
        else if (r.mult >= 2.0) ecranFlash(TypeColors.color(atk.type))

        if (cibleEstAdversaire) animerHp(findViewById(R.id.advHpBar), findViewById(R.id.advHpTxt), def)
        else animerHp(findViewById(R.id.joueurHpBar), findViewById(R.id.joueurHpTxt), def)

        if (def.enVie && r.statutInflige != null) {
            def.statut = r.statutInflige
            if (r.statutInflige == Statut.SOMMEIL) def.toursSommeil = (1..3).random()
            log("${def.nom} ${MoteurCombat.messageStatut(r.statutInflige)}")
            flashStatut(cibleImg, r.statutInflige.couleur)
            if (cibleEstAdversaire) remplirChips(findViewById(R.id.advChips), def)
            else remplirChips(findViewById(R.id.joueurChips), def)
        }

        if (!def.enVie) {
            gererKo(cibleEstAdversaire)
            return true
        }
        return false
    }

    private suspend fun appliquerStatutsFinTour(): Boolean {
        for (cestJoueur in listOf(true, false)) {
            val c = if (cestJoueur) actif() else advActif()
            if (!c.enVie) continue
            val (dmg, msg) = MoteurCombat.degatsStatut(c)
            if (dmg > 0) {
                c.pv = (c.pv - dmg).coerceAtLeast(0)
                log(msg!!)
                val img = findViewById<ImageView>(if (cestJoueur) R.id.joueurSprite else R.id.advSprite)
                flashStatut(img, c.statut?.couleur ?: 0xFFFFFFFF.toInt())
                if (cestJoueur) animerHp(findViewById(R.id.joueurHpBar), findViewById(R.id.joueurHpTxt), c)
                else animerHp(findViewById(R.id.advHpBar), findViewById(R.id.advHpTxt), c)
                delay(600)
                if (!c.enVie) { gererKo(!cestJoueur); return true }
            }
        }
        return false
    }

    private suspend fun gererKo(cibleEstAdversaire: Boolean) {
        vibrer(120)
        if (cibleEstAdversaire) {
            log("${advActif().nom} est K.O. !")
            animerKo(findViewById(R.id.advSprite))
            delay(750)
            val suivant = advEquipe.indexOfFirst { it.enVie }
            if (suivant >= 0) {
                advActifIndex = suivant
                bindAdversaire()
                log("Le Dresseur envoie ${advActif().nom} !")
                finTour()
            } else {
                finPartie("Victoire !", "Tu as vaincu le Dresseur ! 🏆")
            }
        } else {
            log("${actif().nom} est K.O. !")
            animerKo(findViewById(R.id.joueurSprite))
            delay(750)
            if (equipe.any { it.enVie }) {
                etat = Etat.FORCE
                afficherMenu()
                log("Choisis ton prochain Pokémon !")
                enCours = false
            } else {
                finPartie("Défaite…", "Toute ton équipe est K.O. 💀")
            }
        }
    }

    private fun finTour() {
        etat = Etat.MENU
        afficherMenu()
        enCours = false
    }

    private fun finPartie(titre: String, message: String) {
        enCours = true
        vibrer(200)
        findViewById<LinearLayout>(R.id.movesContainer).removeAllViews()
        AlertDialog.Builder(this)
            .setTitle(titre)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Rejouer") { _, _ -> demarrer() }
            .setNegativeButton("Retour") { _, _ -> finish() }
            .show()
    }

    // ---------- Effets ----------

    private val emojis = mapOf(
        "fire" to "🔥", "water" to "💧", "electric" to "⚡", "grass" to "🍃", "ice" to "❄️",
        "fighting" to "👊", "poison" to "☠️", "ground" to "⛰️", "flying" to "🌪️", "psychic" to "🔮",
        "bug" to "🐛", "rock" to "🪨", "ghost" to "👻", "dragon" to "🐉", "dark" to "🌙",
        "steel" to "⚙️", "fairy" to "✨", "normal" to "⭐",
    )

    private fun vibrer(ms: Long) {
        val v = vibreur ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun entree(img: ImageView, depuisDroite: Boolean) {
        img.translationX = if (depuisDroite) 420f else -420f
        img.animate().translationX(0f).setDuration(380).start()
    }

    private fun lunge(view: View, versHautDroite: Boolean) {
        val dx = if (versHautDroite) 55f else -55f
        val dy = if (versHautDroite) -55f else 55f
        view.animate().translationXBy(dx).translationYBy(dy).setDuration(110).withEndAction {
            view.animate().translationXBy(-dx).translationYBy(-dy).setDuration(120).start()
        }.start()
    }

    private fun animerProjectile(type: String, depart: View, cible: View) {
        val arene = findViewById<FrameLayout>(R.id.arene)
        val tv = TextView(this).apply {
            text = emojis[type] ?: "⭐"
            textSize = 34f
            gravity = Gravity.CENTER
            scaleX = 0.6f; scaleY = 0.6f
        }
        arene.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        tv.x = depart.x + depart.width / 2f - 20
        tv.y = depart.y + depart.height / 2f - 20
        tv.animate()
            .x(cible.x + cible.width / 2f - 20)
            .y(cible.y + cible.height / 2f - 20)
            .scaleX(1.4f).scaleY(1.4f).rotationBy(540f)
            .setDuration(380)
            .withEndAction { arene.removeView(tv) }
            .start()
    }

    private fun flashCible(img: ImageView) {
        img.setColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_ATOP)
        img.postDelayed({ img.clearColorFilter() }, 90)
        img.postDelayed({ img.setColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_ATOP) }, 160)
        img.postDelayed({ img.clearColorFilter() }, 250)
    }

    private fun flashStatut(img: ImageView, couleur: Int) {
        img.setColorFilter(couleur, PorterDuff.Mode.SRC_ATOP)
        img.postDelayed({ img.clearColorFilter() }, 380)
    }

    private fun ecranFlash(couleur: Int) {
        val f = findViewById<View>(R.id.flashEcran)
        f.setBackgroundColor(couleur)
        f.alpha = 0f
        f.animate().alpha(0.55f).setDuration(90).withEndAction {
            f.animate().alpha(0f).setDuration(190).start()
        }.start()
    }

    private fun texteFlottant(cible: View, texte: String, couleur: Int, taille: Float) {
        val arene = findViewById<FrameLayout>(R.id.arene)
        val tv = TextView(this).apply {
            text = texte
            setTextColor(couleur)
            textSize = taille
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(8f, 0f, 2f, 0xFF000000.toInt())
        }
        arene.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        tv.x = cible.x + cible.width / 2f - 24
        tv.y = cible.y + cible.height / 3f
        tv.animate().translationYBy(-140f).alpha(0f).setDuration(950)
            .withEndAction { arene.removeView(tv) }.start()
    }

    private fun secouer(view: View) {
        view.alpha = 0.5f
        view.animate().alpha(1f).setDuration(240).start()
        ObjectAnimator.ofFloat(view, "translationX", 0f, 26f, -26f, 16f, -16f, 0f).apply {
            duration = 340; start()
        }
    }

    private fun tremblerEcran() {
        val v = findViewById<View>(R.id.arene)
        ObjectAnimator.ofFloat(v, "translationX", 0f, 20f, -20f, 13f, -13f, 0f).apply {
            duration = 300; start()
        }
    }

    private fun animerKo(view: View) {
        view.animate().alpha(0f).translationYBy(80f).rotationBy(20f).setDuration(500).start()
    }

    private fun animerHp(bar: ProgressBar, txt: TextView, c: Combattant) {
        bar.max = c.pvMax
        ObjectAnimator.ofInt(bar, "progress", bar.progress, c.pv).apply { duration = 500; start() }
        bar.progressTintList = ColorStateList.valueOf(couleurPv(c.pv, c.pvMax))
        txt.text = "${c.pv} / ${c.pvMax} PV"
    }

    private fun couleurPv(pv: Int, max: Int): Int {
        val ratio = pv.toDouble() / max
        return when {
            ratio > 0.5 -> 0xFF4CAF50.toInt()
            ratio > 0.2 -> 0xFFFB8C00.toInt()
            else -> 0xFFE53935.toInt()
        }
    }

    private fun log(message: String) {
        val txt = findViewById<TextView>(R.id.logText)
        txt.append(if (txt.text.isEmpty()) message else "\n$message")
        val scroll = findViewById<ScrollView>(R.id.logScroll)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    companion object {
        const val EXTRA_ID = "extra_id"
    }
}
