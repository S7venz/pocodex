# PoCodex

**Pokédex connecté + vrai jeu de combat de Pokémon**, application Android native (Kotlin + XML), 100 % en français et **100 % hors-ligne**.

> Les 151 Pokémon de la 1ʳᵉ génération, leurs infos et leurs artworks sont **embarqués dans l'app** : tout reste visible même sans connexion.

---

## ✨ Fonctionnalités

| | |
|---|---|
| 📖 **Codex** | Grille des 151 Pokémon, cartes « holo » teintées par type, recherche instantanée |
| 🔍 **Fiche détail** | Artwork HD, types, nature, taille/poids, description, 6 statistiques |
| ⭐ **Favoris & Équipe** | Sauvegardés en local (Room / SQLite), équipe de 6 max |
| ⚔️ **Combat** | Tour par tour : 18 types, 25 natures, vraies attaques, statuts, objets, dresseurs adverses, animations (« game feel ») |
| 🌐 **Combat en ligne** | Lobby créer/rejoindre par code (via ntfy.sh) — *expérimental* |

## 🎨 Direction artistique — « BESTIA »

Identité visuelle « carte holo de collection » : rouge emblématique enrichi, dégradés signature par type, boutons en relief 3D, jauges LCD, filigranes et reflets foil.
Typographie : **Bungee** (titres) · **Baloo 2** (UI) · **Press Start 2P** (accents pixel) · **Outfit** (corps).

## 🛠️ Stack technique

- **Kotlin** + **XML View layouts** (pas de Compose)
- **Room** (favoris + équipe), **Coil** (images depuis les assets), **Gson** (lecture du pokédex local)
- **OkHttp** (uniquement pour le combat en ligne)
- minSdk 26 · targetSdk 35 · AGP 8.7.3 · Kotlin 2.0.21

## 📦 Hors-ligne d'abord

- `app/src/main/assets/pokedex.json` — données des 151 Pokémon (noms FR, types, stats…)
- `app/src/main/assets/artwork/NNN.png` — les 151 artworks (320 px)

Aucun appel réseau pour afficher le Pokédex : la liste et les images sont lues localement.

## 🚀 Build

```bash
./gradlew :app:assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

## 🙏 Crédits

- Données Pokémon : [Purukitto/pokemon-data.json](https://github.com/Purukitto/pokemon-data.json)
- Polices : Bungee, Baloo 2, Press Start 2P, Outfit (SIL Open Font License)
- Pokémon™ est une marque de Nintendo / Game Freak / The Pokémon Company. Projet personnel non commercial.
