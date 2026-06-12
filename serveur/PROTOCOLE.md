# Protocole du serveur PoCodex

Contrat d'échange entre l'app Android et le serveur auto-hébergé (`:serveur`).
Ce serveur remplace **ntfy.sh** pour le combat en ligne : il gère les **comptes**
(REST) et les **salles de combat** (WebSocket, relai *host-authoritative* + ELO).

- Tout est en **français** (messages d'erreur compris).
- Configuration par variables d'environnement : `PORT` (défaut `8080`),
  `BDD` (chemin SQLite, défaut `pocodex-serveur.db`).
- Base : SQLite (tables `comptes`, `sessions`, `parties`).

---

## 1. REST — Comptes

Toutes les réponses sont en JSON. Les erreurs ont la forme `{"erreur":"<message FR>"}`.

### `GET /api/sante`
Sonde de santé. Toujours `200`.
```json
{ "ok": true, "version": "1.0" }
```

### `POST /api/inscription`
Crée un compte et ouvre une session.

Corps :
```json
{ "pseudo": "Sacha", "mdp": "pikachu" }
```
Règles : `pseudo` 3–16 caractères `[A-Za-z0-9_]` ; `mdp` ≥ 6 caractères.
Le mot de passe est haché en **bcrypt (coût 12)**. Le pseudo est **unique**
(insensible à la casse).

Réponses :
- `200` :
  ```json
  { "jeton": "<64 hex>", "pseudo": "Sacha", "elo": 1000, "victoires": 0, "defaites": 0 }
  ```
- `400` : `{ "erreur": "Pseudo invalide (3 à 16 caractères : lettres, chiffres ou _)" }`
  ou `{ "erreur": "Mot de passe trop court (au moins 6 caractères)" }`
- `409` : `{ "erreur": "Pseudo déjà pris" }`

Le **jeton** fait 64 caractères hexadécimaux (32 octets `SecureRandom`) et
**expire au bout de 30 jours**.

### `POST /api/connexion`
Même corps que l'inscription. Vérifie le mot de passe (bcrypt).

Réponses :
- `200` : identique à l'inscription (nouveau jeton à chaque connexion).
- `401` : `{ "erreur": "Pseudo ou mot de passe incorrect" }`

### `GET /api/moi`
Profil du joueur connecté. En-tête requis :
```
Authorization: Bearer <jeton>
```
Réponses :
- `200` : `{ "pseudo": "Sacha", "elo": 1000, "victoires": 0, "defaites": 0 }`
- `401` : `{ "erreur": "Jeton invalide ou expiré" }`

---

## 2. WebSocket — Salles de combat

### Connexion
```
ws://<hote>:<port>/ws?jeton=<jeton>
```
Le jeton est **vérifié avant** d'accepter durablement la connexion. S'il est
invalide ou expiré, le serveur ferme avec le code **4401**.

Tous les messages (dans les deux sens) sont du **JSON texte** avec un champ
discriminant **`k`**. Les messages inconnus (`k` non reconnu) sont **ignorés**.

Modèle **host-authoritative** : l'**hôte** (créateur de la salle) fait tourner le
moteur de combat et **diffuse l'état** (`etat`) ; l'**invité** envoie son coup
(`act`). Le serveur **relaie** `etat` et `act` **tels quels** (il n'en lit pas le
contenu), gère l'appariement, et clôture la partie (ELO + persistance).

> Un même jeton/compte ne peut tenir **qu'une seule salle active** à la fois.

### 2.1 Client → Serveur

| `k`          | Émetteur | Charge utile                                  | Effet |
|--------------|----------|-----------------------------------------------|-------|
| `creer`      | hôte     | —                                             | Crée une salle, INSERT dans `parties`. |
| `rejoindre`  | invité   | `code` (5 car.), `pokemon` (id du Pokémon)    | Rattache l'invité à la salle. |
| `etat`       | hôte     | *(champs libres du moteur, ex. `seq`, `tour`)* | Relayé tel quel à l'invité. |
| `act`        | invité   | *(champs libres, ex. `seq`, `move`)*           | Relayé tel quel à l'hôte. |
| `fin`        | hôte     | `vainqueurEstHote` (booléen)                  | Clôt la partie, calcule l'ELO. |

Exemples :
```json
{ "k": "creer" }
{ "k": "rejoindre", "code": "ABC12", "pokemon": 25 }
{ "k": "etat", "seq": 7, "tour": "guest", "pvHote": 80, "pvInvite": 100 }
{ "k": "act", "seq": 7, "move": 0 }
{ "k": "fin", "vainqueurEstHote": true }
```

### 2.2 Serveur → Client

| `k`           | Destinataire | Charge utile | Quand |
|---------------|--------------|--------------|-------|
| `salle`       | hôte         | `code` | Après `creer`. |
| `join`        | hôte         | `id` (Pokémon de l'invité), `pseudo`, `elo` | À l'arrivée de l'invité. |
| `infos`       | invité       | `adversaire` : `{ pseudo, elo }` | À l'arrivée de l'invité. |
| `classement`  | les deux     | `elo` (nouveau), `delta` (±N), `vainqueur` (`"hote"`/`"invite"`) | Sur `fin` ou forfait. |
| `deco`        | l'autre joueur | — | À la déconnexion d'un joueur. |
| `erreur`      | l'émetteur fautif | `message` (FR) | Action invalide. |

Exemples :
```json
{ "k": "salle", "code": "ABC12" }
{ "k": "join", "id": 25, "pseudo": "Régis", "elo": 1000 }
{ "k": "infos", "adversaire": { "pseudo": "Sacha", "elo": 1000 } }
{ "k": "classement", "elo": 1016, "delta": 16, "vainqueur": "hote" }
{ "k": "deco" }
{ "k": "erreur", "message": "Salle introuvable" }
```

Messages d'erreur possibles (`{"k":"erreur","message":...}`) :
- `Vous avez déjà une salle active`
- `Salle introuvable`
- `Salle déjà pleine`

### 2.3 Clôture & ELO

Sur `fin` (hôte) ou **forfait** (déconnexion d'un joueur alors que la partie a
commencé), le serveur :

1. Calcule le nouvel ELO des deux joueurs.
   - Attendu de A face à B : `Ea = 1 / (1 + 10^((eloB − eloA) / 400))`.
   - Nouvel ELO : `elo' = round(elo + K · (score − Ea))`, avec **K = 32** et
     `score` = `1` (victoire) ou `0` (défaite).
   - ELO égaux (1000 vs 1000) → vainqueur **+16**, perdant **−16**.
2. Met à jour `comptes` (ELO + `victoires`/`defaites`) et `parties`
   (`vainqueur_id`, `fin`).
3. Envoie `{"k":"classement",...}` aux **deux** joueurs.
4. Ferme la salle (registre mémoire).

**Forfait sur déconnexion :** si l'hôte se déconnecte, l'invité gagne ; si
l'invité se déconnecte, l'hôte gagne. L'autre joueur reçoit `{"k":"deco"}` puis
`{"k":"classement",...}`. Si **personne** n'avait rejoint, la salle est
simplement retirée (sans ELO).

---

## 3. Outil de test (simulation d'invité)

`com.s7venz.pocodex.outils.OutilInvite` se connecte au WebSocket en tant
qu'invité, rejoint une salle, affiche tout ce qu'il reçoit, et joue
automatiquement `{"k":"act","seq":<seq>,"move":0}` 2 s après chaque `etat` où
`tour == "guest"`.

```bash
./gradlew :serveur:simulerInvite \
  --args="ws://localhost:8080 <jeton> <code> <idPokemon>"
```
