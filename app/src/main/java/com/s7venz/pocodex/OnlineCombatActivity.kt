package com.s7venz.pocodex

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import com.s7venz.pocodex.combat.Combattant
import com.s7venz.pocodex.combat.MoteurCombat
import com.s7venz.pocodex.combat.Statut
import com.s7venz.pocodex.data.PokedexRepository
import com.s7venz.pocodex.online.Reseau
import com.s7venz.pocodex.ui.TypeColors
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OnlineCombatActivity : AppCompatActivity() {

    private lateinit var role: String
    private lateinit var code: String
    private lateinit var topic: String
    private var monId = 25

    private lateinit var moi: Combattant
    private var adv: Combattant? = null

    private var tour = "host"
    private var seq = 0
    private var fini = false
    private var enCours = false
    private var enJeu = false

    private var lastTime = 0L
    private val vus = HashSet<String>()
    private val tampon = mutableListOf<String>()

    // Fiabilité réseau
    private var mid = ""                     // identifiant de match (anti-collision sur topic public)
    private var seqVu = -1                    // dernier seq d'état appliqué (invité)
    private var coupEnAttente = -1           // coup invité en attente d'accusé (pour renvoi)
    private var dernierEtat: String? = null  // dernier état publié (hôte) — pour re-diffusion
    private var demarrageHote = false        // démarrage hôte en cours (anti double-join pendant la suspension)
    private var finAffichee = false          // dialogue de fin déjà affiché (anti double-dialogue)

    private val estHote get() = role == ROLE_HOST
    private val monCamp get() = if (estHote) "host" else "guest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_combat)
        setSupportActionBar(findViewById(R.id.combatToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.online_titre)
        window.statusBarColor = 0xFF2A2D3A.toInt()

        role = intent.getStringExtra(EXTRA_ROLE) ?: ROLE_HOST
        code = intent.getStringExtra(EXTRA_CODE) ?: "????"
        monId = intent.getIntExtra(EXTRA_MONID, 25)
        topic = "pkmncombatv2_$code"
        // Session fraîche : on ignore l'historique du topic (parties précédentes sur le code).
        lastTime = System.currentTimeMillis() / 1000 - 5
        android.util.Log.i("PokeOnline", "topic=$topic role=$role monId=$monId")

        findViewById<ProgressBar>(R.id.combatProgress).isVisible = true
        findViewById<TextView>(R.id.advNom).text = "…"

        lifecycleScope.launch {
            try {
                moi = MoteurCombat.depuisPokemon(PokedexRepository.parId(monId) ?: PokedexRepository.tous().first())
                bindMoi()
                if (estHote) {
                    log("Partie créée ! Code : $code")
                    log("Partage ce code et attends ton adversaire…")
                } else {
                    envoyer(joinPayload())
                    log("Connexion à la partie $code …")
                }
                afficherAttente(if (estHote) "Code : $code — en attente…" else "Connexion…")
            } catch (e: Exception) {
                log("Erreur réseau.")
            } finally {
                findViewById<ProgressBar>(R.id.combatProgress).isVisible = false
            }
            ecouterBoucle()
            tickRetransmission()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Reseau.couper()
    }

    /** Réception en streaming (connexion longue) + reconnexion automatique. */
    private fun ecouterBoucle() {
        lifecycleScope.launch {
            while (isActive && !isFinishing && !fini) {
                runCatching {
                    Reseau.ecouter(topic, lastTime) { m ->
                        if (m.time > lastTime) lastTime = m.time
                        if (vus.add(m.id)) withContext(Dispatchers.Main) { traiter(m.payload) }
                    }
                }.onFailure { android.util.Log.w("PokeOnline", "flux KO: $it") }
                if (isActive && !isFinishing && !fini) delay(1500) // flux coupé : on reconnecte
            }
        }
    }

    /** Retransmissions périodiques : anti-perte de message (join / coup / état). */
    private fun tickRetransmission() {
        lifecycleScope.launch {
            while (isActive && !isFinishing && !fini) {
                delay(4000)
                renvoyerSiNecessaire()
            }
        }
    }

    private fun renvoyerSiNecessaire() {
        when {
            fini -> {}
            !estHote && !enJeu -> envoyer(joinPayload())                          // pas encore en jeu → on (re)rejoint
            !estHote && tour == "guest" && coupEnAttente >= 0 ->
                envoyer(actPayload(coupEnAttente))                               // notre coup n'a pas été accusé → on le renvoie
            !estHote && enJeu && tour == "host" -> envoyer(joinPayload())         // on attend l'hôte → on le relance (resync)
            estHote && enJeu && tour == "guest" -> dernierEtat?.let { envoyer(it) } // l'invité doit jouer → on rediffuse l'état
        }
    }

    private fun joinPayload() = """{"k":"join","id":${moi.id}}"""

    private fun actPayload(moveIdx: Int) =
        JSONObject().put("k", "act").put("seq", seq).put("move", moveIdx).put("mid", mid).toString()

    private fun envoyer(payload: String) {
        lifecycleScope.launch { Reseau.publier(topic, payload) }
    }

    private fun randomMid(): String {
        val c = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..6).map { c.random() }.joinToString("")
    }

    private suspend fun traiter(payload: String) {
        val o = runCatching { JSONObject(payload) }.getOrNull() ?: return
        when (o.optString("k")) {
            "join" -> when {
                estHote && adv == null && !demarrageHote -> demarrerHote(o.optInt("id"))
                estHote && adv != null -> dernierEtat?.let { envoyer(it) }   // invité qui re-rejoint → on lui renvoie l'état
            }
            "act" -> if (estHote && enJeu && tour == "guest" && !enCours &&
                o.optInt("seq") == seq && o.optString("mid") == mid) {
                recevoirActionInvite(o.optInt("move"))
            }
            "state" -> if (!estHote) appliquerEtat(o)
        }
    }

    // ---------- Côté HÔTE (autoritaire) ----------

    private suspend fun demarrerHote(advId: Int) {
        demarrageHote = true  // verrou avant toute suspension : empêche un 2ᵉ join de relancer le démarrage
        adv = MoteurCombat.depuisPokemon(PokedexRepository.parId(advId) ?: PokedexRepository.tous().first())
        bindAdv()
        enJeu = true
        mid = randomMid()
        tour = if (moi.vitesse >= adv!!.vitesse) "host" else "guest"
        seq = 1
        log("${adv!!.nom} a rejoint le combat !")
        publierEtat("Le combat commence !")
        majTour()
    }

    private suspend fun recevoirActionInvite(moveIdx: Int) {
        enCours = true
        val a = adv ?: return
        val atk = a.attaques.getOrElse(moveIdx) { a.attaques.first() }
        resoudre(a, moi, atk, attaquantEstMoi = false)
        if (!moi.enVie) { finirHote("guest"); return }
        tour = "host"; seq++
        publierEtat(tampon.joinToString("\n")); tampon.clear()
        enCours = false
        majTour()
    }

    private fun jouerCoupHote(moveIdx: Int) {
        enCours = true
        afficherAttente("…")
        lifecycleScope.launch {
            val a = adv ?: return@launch
            resoudre(moi, a, moi.attaques[moveIdx], attaquantEstMoi = true)
            if (!a.enVie) { finirHote("host"); return@launch }
            tour = "guest"; seq++
            publierEtat(tampon.joinToString("\n")); tampon.clear()
            enCours = false
            majTour()
        }
    }

    /** Calcul + application + animation locale (hôte uniquement). */
    private fun resoudre(att: Combattant, def: Combattant, atk: com.s7venz.pocodex.combat.Attaque, attaquantEstMoi: Boolean) {
        val r = MoteurCombat.degats(att, def, atk)
        if (r.rate) { ajouter("${att.nom} utilise ${atk.nom}… mais ça rate !"); return }
        def.pv = (def.pv - r.degats).coerceAtLeast(0)
        ajouter("${att.nom} utilise ${atk.nom} !" + (if (r.critique) " Coup critique !" else "") + "  (-${r.degats} PV)")
        MoteurCombat.messageEfficacite(r.mult).takeIf { it.isNotEmpty() }?.let { ajouter(it) }
        if (def.enVie && r.statutInflige != null) {
            def.statut = r.statutInflige
            ajouter("${def.nom} ${MoteurCombat.messageStatut(r.statutInflige)}")
        }
        animerCoup(cibleEstAdv = attaquantEstMoi, degats = r.degats, crit = r.critique)
        majBarres()
    }

    private suspend fun finirHote(vainqueur: String) {
        fini = true
        tour = "host"
        publierEtat(tampon.joinToString("\n")); tampon.clear()
        finPartie(vainqueur)
    }

    private fun publierEtat(msg: String) {
        val a = adv ?: return
        val o = JSONObject()
        o.put("k", "state"); o.put("mid", mid); o.put("seq", seq); o.put("tour", tour)
        o.put("fini", fini); o.put("vainqueur", if (fini) (if (!moi.enVie) "guest" else "host") else "")
        o.put("h", objCombattant(moi))  // l'hôte est "h"
        o.put("g", objCombattant(a))
        o.put("msg", msg)
        val etat = o.toString()
        dernierEtat = etat
        lifecycleScope.launch { Reseau.publier(topic, etat) }
    }

    // ---------- Côté INVITÉ (affichage) ----------

    private suspend fun appliquerEtat(o: JSONObject) {
        // Match : on adopte l'identifiant de l'hôte, puis on ignore les autres parties du topic.
        val mEtat = o.optString("mid")
        if (mid.isEmpty()) mid = mEtat
        if (mid.isNotEmpty() && mEtat != mid) return
        // Anti-doublon : un état déjà appliqué (re-diffusion) est ignoré.
        val s = o.optInt("seq")
        if (s <= seqVu) return
        seqVu = s
        coupEnAttente = -1  // l'hôte a fait avancer la partie → notre coup est accusé

        val h = o.getJSONObject("h")
        val g = o.getJSONObject("g")
        if (adv == null) {
            adv = MoteurCombat.depuisPokemon(PokedexRepository.parId(h.getInt("id")) ?: PokedexRepository.tous().first())
            bindAdv()
            enJeu = true
        }
        val a = adv ?: return
        val ancienMoi = moi.pv
        val ancienAdv = a.pv
        // invité : moi = "g", adversaire = "h"
        moi.pv = g.getInt("pv"); moi.statut = parseStatut(g.optString("st"))
        a.pv = h.getInt("pv"); a.statut = parseStatut(h.optString("st"))

        val msg = o.optString("msg")
        if (msg.isNotBlank()) msg.split("\n").forEach { if (it.isNotBlank()) log(it) }

        if (a.pv < ancienAdv) animerCoup(cibleEstAdv = true, degats = ancienAdv - a.pv, crit = false)
        if (moi.pv < ancienMoi) animerCoup(cibleEstAdv = false, degats = ancienMoi - moi.pv, crit = false)
        majBarres()
        majChips()

        seq = s; tour = o.optString("tour")
        if (o.optBoolean("fini")) finPartie(o.optString("vainqueur")) else { enCours = false; majTour() }
    }

    private fun jouerCoupInvite(moveIdx: Int) {
        enCours = true
        coupEnAttente = moveIdx
        log("Tu choisis ${moi.attaques[moveIdx].nom}…")
        afficherAttente("En attente de l'hôte…")
        envoyer(actPayload(moveIdx))
    }

    // ---------- UI commune ----------

    private fun majTour() {
        if (fini) return
        val monTour = !enCours && enJeu && tour == monCamp
        if (monTour) afficherAttaques() else {
            afficherAttente(if (!enJeu) "En attente…" else "Au tour de l'adversaire…")
        }
    }

    private fun afficherAttaques() {
        val c = findViewById<LinearLayout>(R.id.movesContainer)
        c.removeAllViews()
        moi.attaques.forEachIndexed { i, atk ->
            bouton(atk.nom, TypeColors.color(atk.type), true) {
                if (estHote) jouerCoupHote(i) else jouerCoupInvite(i)
            }
        }
    }

    private fun afficherAttente(texte: String) {
        val c = findViewById<LinearLayout>(R.id.movesContainer)
        c.removeAllViews()
        bouton("⏳  $texte", 0xFF8A8F98.toInt(), false) {}
    }

    private fun bouton(texte: String, couleur: Int, actif: Boolean, action: () -> Unit) {
        val d = resources.displayMetrics.density
        val b = MaterialButton(this).apply {
            text = texte
            isEnabled = actif
            backgroundTintList = ColorStateList.valueOf(couleur)
            cornerRadius = (12 * d).toInt()
            insetTop = 0; insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = (5 * d).toInt() }
            setOnClickListener { if (!enCours) action() }
        }
        findViewById<LinearLayout>(R.id.movesContainer).addView(b)
    }

    private fun bindMoi() {
        findViewById<TextView>(R.id.joueurNom).text = moi.nom
        val img = findViewById<ImageView>(R.id.joueurSprite)
        img.alpha = 1f; img.translationY = 0f
        img.load(moi.spriteUrl) { crossfade(true) }
        img.scaleX = -1f
        remplirChips(findViewById(R.id.joueurChips), moi)
        setHp(findViewById(R.id.joueurHpBar), findViewById(R.id.joueurHpTxt), moi)
    }

    private fun bindAdv() {
        val a = adv ?: return
        findViewById<TextView>(R.id.advNom).text = a.nom
        val img = findViewById<ImageView>(R.id.advSprite)
        img.alpha = 1f; img.translationY = 0f
        img.load(a.spriteUrl) { crossfade(true) }
        remplirChips(findViewById(R.id.advChips), a)
        setHp(findViewById(R.id.advHpBar), findViewById(R.id.advHpTxt), a)
    }

    private fun majChips() {
        remplirChips(findViewById(R.id.advChips), adv ?: return)
        remplirChips(findViewById(R.id.joueurChips), moi)
    }

    private fun remplirChips(container: LinearLayout, c: Combattant) {
        container.removeAllViews()
        val d = resources.displayMetrics.density
        for (t in c.types) {
            val chip = TypeColors.chip(this, TypeColors.nomFr(t), TypeColors.color(t)).apply { textSize = 10f }
            container.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.marginEnd = (5 * d).toInt() })
        }
        c.statut?.let { s ->
            container.addView(TypeColors.chip(this, s.court, s.couleur).apply { textSize = 10f }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.marginEnd = (5 * d).toInt() })
        }
    }

    private fun setHp(bar: ProgressBar, txt: TextView, c: Combattant) {
        bar.max = c.pvMax; bar.progress = c.pv
        bar.progressTintList = ColorStateList.valueOf(couleurPv(c.pv, c.pvMax))
        txt.text = "${c.pv} / ${c.pvMax} PV"
    }

    private fun majBarres() {
        animerHp(findViewById(R.id.advHpBar), findViewById(R.id.advHpTxt), adv ?: return)
        animerHp(findViewById(R.id.joueurHpBar), findViewById(R.id.joueurHpTxt), moi)
    }

    private fun finPartie(vainqueur: String) {
        if (finAffichee) return  // écho d'un état fini=true (reconnexion) → on n'empile pas un 2ᵉ dialogue
        finAffichee = true
        fini = true
        findViewById<LinearLayout>(R.id.movesContainer).removeAllViews()
        if (vainqueur == monCamp) { animerKo(findViewById(R.id.advSprite)) } else { animerKo(findViewById(R.id.joueurSprite)) }
        AlertDialog.Builder(this)
            .setTitle(if (vainqueur == monCamp) "Victoire !" else "Défaite…")
            .setMessage(if (vainqueur == monCamp) "Tu as gagné le combat en ligne ! 🏆" else "Tu as perdu… 💀")
            .setCancelable(false)
            .setPositiveButton("Retour") { _, _ -> finish() }
            .show()
    }

    // ---------- Effets ----------

    private val emojis = mapOf(
        "fire" to "🔥", "water" to "💧", "electric" to "⚡", "grass" to "🍃", "ice" to "❄️",
        "fighting" to "👊", "poison" to "☠️", "ground" to "⛰️", "flying" to "🌪️", "psychic" to "🔮",
        "bug" to "🐛", "rock" to "🪨", "ghost" to "👻", "dragon" to "🐉", "dark" to "🌙",
        "steel" to "⚙️", "fairy" to "✨", "normal" to "⭐",
    )

    private fun animerCoup(cibleEstAdv: Boolean, degats: Int, crit: Boolean) {
        val img = findViewById<ImageView>(if (cibleEstAdv) R.id.advSprite else R.id.joueurSprite)
        img.alpha = 0.5f
        img.animate().alpha(1f).setDuration(220).start()
        ObjectAnimator.ofFloat(img, "translationX", 0f, 24f, -24f, 14f, -14f, 0f).apply { duration = 320; start() }
        texteFlottant(img, if (crit) "-$degats !" else "-$degats",
            if (crit) 0xFFFFD54F.toInt() else 0xFFFFFFFF.toInt(), if (crit) 30f else 24f)
        if (crit) ecranFlash()
    }

    private fun texteFlottant(cible: View, texte: String, couleur: Int, taille: Float) {
        val arene = findViewById<FrameLayout>(R.id.arene)
        val tv = TextView(this).apply {
            text = texte; setTextColor(couleur); textSize = taille
            typeface = Typeface.DEFAULT_BOLD; setShadowLayer(8f, 0f, 2f, 0xFF000000.toInt())
        }
        arene.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        tv.x = cible.x + cible.width / 2f - 24
        tv.y = cible.y + cible.height / 3f
        tv.animate().translationYBy(-140f).alpha(0f).setDuration(950).withEndAction { arene.removeView(tv) }.start()
    }

    private fun ecranFlash() {
        val f = findViewById<View>(R.id.flashEcran)
        f.setBackgroundColor(0xFFFFFFFF.toInt()); f.alpha = 0f
        f.animate().alpha(0.5f).setDuration(90).withEndAction { f.animate().alpha(0f).setDuration(190).start() }.start()
    }

    private fun animerKo(view: View) {
        view.animate().alpha(0f).translationYBy(70f).rotationBy(20f).setDuration(500).start()
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

    private fun objCombattant(c: Combattant): JSONObject = JSONObject().apply {
        put("id", c.id); put("pv", c.pv); put("max", c.pvMax); put("st", c.statut?.name ?: "")
    }

    private fun parseStatut(s: String?): Statut? =
        if (s.isNullOrBlank()) null else runCatching { Statut.valueOf(s) }.getOrNull()

    private fun ajouter(ligne: String) { log(ligne); tampon.add(ligne) }

    private fun log(message: String) {
        val txt = findViewById<TextView>(R.id.logText)
        txt.append(if (txt.text.isEmpty()) message else "\n$message")
        val scroll = findViewById<ScrollView>(R.id.logScroll)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    companion object {
        const val ROLE_HOST = "HOST"
        const val ROLE_GUEST = "GUEST"
        const val EXTRA_ROLE = "role"
        const val EXTRA_CODE = "code"
        const val EXTRA_MONID = "monid"
    }
}
