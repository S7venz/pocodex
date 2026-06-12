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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import com.s7venz.pocodex.combat.Combattant
import com.s7venz.pocodex.combat.MoteurCombat
import com.s7venz.pocodex.combat.Statut
import com.s7venz.pocodex.data.PokedexRepository
import com.s7venz.pocodex.online.ClientWs
import com.s7venz.pocodex.ui.TypeColors
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Combat en ligne branché sur NOTRE serveur PoCodex (WebSocket, host-authoritative).
 *
 * Le transport ([ClientWs]) remplace l'ancien relai ntfy.sh : le serveur gère
 * l'appariement (création / arrivée de l'invité), relaie l'état (`etat`) et le coup
 * (`act`) tels quels, et clôture la partie (ELO). Plus aucune retransmission ni
 * déduplication côté client : le contrat est dans `serveur/PROTOCOLE.md`.
 *
 * - HÔTE (autoritaire) : envoie `creer`, reçoit `salle` (code) puis `join` (l'invité
 *   arrive), fait tourner le moteur et diffuse l'`etat`. À la fin, envoie `fin`.
 * - INVITÉ : envoie `rejoindre` (code + son Pokémon), reçoit `infos` (l'adversaire),
 *   applique chaque `etat` reçu et renvoie son `act`.
 * - Les deux : `classement` met à jour le compte (ELO, V/D) et le dialogue de fin ;
 *   `deco`/`erreur` sont journalisés ; une fermeture inattendue ramène à l'accueil.
 */
class OnlineCombatActivity : AppCompatActivity() {

    private lateinit var role: String
    private lateinit var code: String
    private var monId = 25

    private lateinit var moi: Combattant
    private var adv: Combattant? = null

    private var tour = "host"
    private var seq = 0
    private var fini = false
    private var enCours = false
    private var enJeu = false

    private var seqVu = -1                    // dernier seq d'état appliqué (invité) — idempotence
    private var demarrageHote = false         // démarrage hôte en cours (anti double-join)
    private var finAffichee = false           // dialogue de fin déjà affiché (anti double-dialogue)
    private val tampon = mutableListOf<String>()

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
        code = intent.getStringExtra(EXTRA_CODE) ?: ""
        monId = intent.getIntExtra(EXTRA_MONID, 25)
        android.util.Log.i("PokeOnline", "role=$role code=$code monId=$monId")

        findViewById<ProgressBar>(R.id.combatProgress).isVisible = true
        findViewById<TextView>(R.id.advNom).text = "…"

        lifecycleScope.launch {
            moi = MoteurCombat.depuisPokemon(PokedexRepository.parId(monId) ?: PokedexRepository.tous().first())
            bindMoi()
            findViewById<ProgressBar>(R.id.combatProgress).isVisible = false
            if (estHote) {
                log("Connexion au serveur…")
                afficherAttente("Connexion…")
            } else {
                log("Connexion à la partie $code …")
                afficherAttente("Connexion…")
            }
            connecter()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        ClientWs.fermer()
    }

    /** Ouvre la connexion WebSocket et envoie le 1ᵉʳ message (creer / rejoindre). */
    private fun connecter() {
        ClientWs.connecter(
            serveur = Compte.serveur,
            jeton = Compte.jeton,
            surMessage = { o -> traiter(o) },
            surFermeture = { raison -> surFermeture(raison) },
        )
        // OkHttp met en file les messages tant que le socket n'est pas ouvert : on peut
        // envoyer le 1ᵉʳ message tout de suite, il partira dès l'ouverture.
        if (estHote) {
            ClientWs.envoyer(JSONObject().put("k", "creer"))
        } else {
            ClientWs.envoyer(JSONObject().put("k", "rejoindre").put("code", code).put("pokemon", monId))
        }
    }

    /** Une fermeture inattendue (avant la fin de partie) ramène à l'accueil. */
    private fun surFermeture(raison: String) {
        android.util.Log.w("PokeOnline", "WS fermé : $raison")
        if (isFinishing || fini || finAffichee) return
        finAffichee = true
        AlertDialog.Builder(this)
            .setTitle("Déconnexion")
            .setMessage("Connexion au serveur perdue.")
            .setCancelable(false)
            .setPositiveButton("Retour") { _, _ -> finish() }
            .show()
    }

    /** Routeur des messages reçus du serveur (déjà sur le thread principal). */
    private fun traiter(o: JSONObject) {
        when (o.optString("k")) {
            // --- Hôte ---
            "salle" -> {
                code = o.optString("code")
                log("Partie créée ! Code : $code")
                log("Partage ce code et attends ton adversaire…")
                afficherAttente("Code : $code — en attente…")
            }
            "join" -> if (estHote && adv == null && !demarrageHote) {
                val pseudo = o.optString("pseudo").ifBlank { "Adversaire" }
                val elo = o.optInt("elo", 1000)
                lifecycleScope.launch {
                    demarrerHote(o.optInt("id"))
                    log("$pseudo (ELO $elo) a rejoint le combat !")
                }
            }
            "act" -> if (estHote && enJeu && tour == "guest" && !enCours && o.optInt("seq") == seq) {
                recevoirActionInvite(o.optInt("move"))
            }
            // --- Invité ---
            "infos" -> if (!estHote) {
                val a = o.optJSONObject("adversaire")
                val pseudo = a?.optString("pseudo")?.ifBlank { "Adversaire" } ?: "Adversaire"
                val elo = a?.optInt("elo", 1000) ?: 1000
                log("Adversaire : $pseudo (ELO $elo)")
            }
            "etat" -> if (!estHote) lifecycleScope.launch { appliquerEtat(o) }
            // --- Les deux ---
            "classement" -> appliquerClassement(o)
            "deco" -> log("L'adversaire s'est déconnecté.")
            "erreur" -> {
                val m = o.optString("message").ifBlank { "Erreur du serveur." }
                Toast.makeText(this, m, Toast.LENGTH_LONG).show()
                log("⚠ $m")
            }
        }
    }

    /** Met à jour le compte local + le dialogue de fin avec le résultat classé. */
    private fun appliquerClassement(o: JSONObject) {
        val nouvelElo = o.optInt("elo", Compte.elo)
        val delta = o.optInt("delta", 0)
        val vainqueur = o.optString("vainqueur") // "hote" / "invite"
        val jaiGagne = (estHote && vainqueur == "hote") || (!estHote && vainqueur == "invite")

        Compte.elo = nouvelElo
        if (jaiGagne) Compte.victoires += 1 else Compte.defaites += 1

        val signe = if (delta >= 0) "+$delta" else "$delta"
        finPartie(if (jaiGagne) "win" else "lose", classement = "$signe ELO ($nouvelElo)")
    }

    // ---------- Côté HÔTE (autoritaire) ----------

    private suspend fun demarrerHote(advId: Int) {
        demarrageHote = true  // verrou avant toute suspension : empêche un 2ᵉ join de relancer
        adv = MoteurCombat.depuisPokemon(PokedexRepository.parId(advId) ?: PokedexRepository.tous().first())
        bindAdv()
        enJeu = true
        tour = if (moi.vitesse >= adv!!.vitesse) "host" else "guest"
        seq = 1
        publierEtat("Le combat commence !")
        majTour()
    }

    private fun recevoirActionInvite(moveIdx: Int) {
        enCours = true
        val a = adv ?: return
        val atk = a.attaques.getOrElse(moveIdx) { a.attaques.first() }
        resoudre(a, moi, atk, attaquantEstMoi = false)
        if (!moi.enVie) { finirHote(vainqueurEstHote = false); return }
        tour = "host"; seq++
        publierEtat(tampon.joinToString("\n")); tampon.clear()
        enCours = false
        majTour()
    }

    private fun jouerCoupHote(moveIdx: Int) {
        enCours = true
        afficherAttente("…")
        val a = adv ?: return
        resoudre(moi, a, moi.attaques[moveIdx], attaquantEstMoi = true)
        if (!a.enVie) { finirHote(vainqueurEstHote = true); return }
        tour = "guest"; seq++
        publierEtat(tampon.joinToString("\n")); tampon.clear()
        enCours = false
        majTour()
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

    /** Fin de partie côté hôte : diffuse l'état final puis notifie le serveur (ELO). */
    private fun finirHote(vainqueurEstHote: Boolean) {
        fini = true
        tour = "host"
        publierEtat(tampon.joinToString("\n")); tampon.clear()
        ClientWs.envoyer(JSONObject().put("k", "fin").put("vainqueurEstHote", vainqueurEstHote))
        // L'affichage du résultat (Victoire/Défaite + ELO) attend le `classement` du serveur.
    }

    private fun publierEtat(msg: String) {
        val a = adv ?: return
        val o = JSONObject()
        o.put("k", "etat"); o.put("seq", seq); o.put("tour", tour)
        o.put("fini", fini); o.put("vainqueur", if (fini) (if (!moi.enVie) "guest" else "host") else "")
        o.put("h", objCombattant(moi))  // l'hôte est "h"
        o.put("g", objCombattant(a))
        o.put("msg", msg)
        ClientWs.envoyer(o)
    }

    // ---------- Côté INVITÉ (affichage) ----------

    private suspend fun appliquerEtat(o: JSONObject) {
        // Idempotence : un état déjà appliqué (ré-émission) est ignoré.
        val s = o.optInt("seq")
        if (s <= seqVu) return
        seqVu = s

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
        if (o.optBoolean("fini")) {
            fini = true
            findViewById<LinearLayout>(R.id.movesContainer).removeAllViews()
            // L'animation de KO + le dialogue final (avec ELO) viennent du `classement`.
            val perdant = o.optString("vainqueur")
            if (perdant == monCamp) animerKo(findViewById(R.id.advSprite)) else animerKo(findViewById(R.id.joueurSprite))
        } else {
            enCours = false; majTour()
        }
    }

    private fun jouerCoupInvite(moveIdx: Int) {
        enCours = true
        log("Tu choisis ${moi.attaques[moveIdx].nom}…")
        afficherAttente("En attente de l'hôte…")
        ClientWs.envoyer(JSONObject().put("k", "act").put("seq", seq).put("move", moveIdx))
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

    /**
     * Affiche le résultat de la partie. [issue] = "win"/"lose" ; [classement] =
     * libellé ELO enrichi (« +16 ELO (1016) ») reçu du serveur, ou null.
     */
    private fun finPartie(issue: String, classement: String?) {
        if (finAffichee) return  // anti double-dialogue (état fini + classement)
        finAffichee = true
        fini = true
        findViewById<LinearLayout>(R.id.movesContainer).removeAllViews()
        val jaiGagne = issue == "win"
        // Au cas où l'animation de KO n'a pas déjà été déclenchée.
        if (jaiGagne) animerKo(findViewById(R.id.advSprite)) else animerKo(findViewById(R.id.joueurSprite))
        val titre = if (jaiGagne) "Victoire !" else "Défaite…"
        val base = if (jaiGagne) "Tu as gagné le combat en ligne ! 🏆" else "Tu as perdu… 💀"
        val message = if (classement != null) "$base\n\n$classement" else base
        AlertDialog.Builder(this)
            .setTitle(titre)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Retour") { _, _ -> finish() }
            .show()
    }

    // ---------- Effets ----------

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
