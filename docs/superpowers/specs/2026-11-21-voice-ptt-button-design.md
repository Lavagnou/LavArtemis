# Bouton vocal push-to-talk (Android) — design court

## Intention

Dicter sur le PC pendant un stream sans ouvrir le clavier Android. Sur le PC, un outil de
transcription écoute la touche **²** en push-to-talk : tant qu'elle est enfoncée, il transcrit et
écrit ce qui est dicté. LavArtemis affiche un bouton micro à l'écran qui tient cette touche enfoncée
tant que le doigt reste dessus.

## Objectifs

- Bouton micro pendant le stream, **en bas à droite par défaut**, semi-transparent (on voit le jeu
  derrière), assez grand pour être pressé sans viser.
- Appui = touche ² enfoncée sur l'hôte ; relâché = touche relâchée. **Aucun délai** au démarrage.
- Afficher / masquer : un réglage dans les options, **et** une entrée dans le menu en jeu.
- Déplacer le bouton via un **mode « Déplacer » du menu en jeu** (approche choisie, voir plus bas).
- La position est retenue d'un stream à l'autre.

## Non-objectifs

- Pas de reconnaissance vocale côté Android : c'est l'outil du PC qui transcrit.
- Pas de touche configurable dans cette version (² uniquement).
- Pas de bouton vocal côté desktop.

## Anti-objectifs (échec même si « ça marche »)

- Une touche ² qui **reste enfoncée** (doigt qui sort de l'écran, menu ouvert, app en arrière-plan,
  stream coupé) : la dictée tournerait sans fin.
- Un bouton déplacé par accident en dictant.
- Un toucher sur le bouton qui part aussi au stream (clic souris / toucher parasite dans le jeu).

## Approches considérées

1. **Glisser direct** (² envoyé après ~150 ms d'immobilité, sinon déplacement) — rapide, mais
   retarde la dictée et peut déplacer le bouton par erreur.
2. **Mode « Déplacer » via le menu en jeu** — ✅ **choisi par Lavagnou.** Aucun conflit avec le
   push-to-talk, aucun délai, jamais de déplacement accidentel ; coûte un passage par le menu.
3. **Poignée sur le bord du bouton** — pas de délai, mais petite cible.

## Design

**Touche envoyée.** Le client envoie des codes VK *normalisés clavier US* ; l'hôte les convertit en
scancode, et c'est la disposition du PC qui en fait un caractère. La touche à gauche du 1 est
`VK_OEM_3` (0xC0) → scancode 0x29 → **²** sur un clavier français. Envoyer `VK_OEM_7` (ce que
produit un ² tapé sur un clavier Android français) ne donnerait pas la bonne touche. À vérifier sur
l'hôte réel avant de conclure.

**Bouton.** Vue dédiée dans `activity_game.xml`, à côté du bouton menu flottant ; style rond
`floating_menu_button`. **L'icône doit dire « vocal » au premier coup d'œil** (demande de
Lavagnou) : un micro (vector drawable, type Material `mic`), lisible à 56 dp sur le fond
semi-transparent. Taille et opacité : voir « calls » ci-dessous.

**Appui.**
- `ACTION_DOWN` → `KEY_DOWN` ² ; bouton plus opaque (retour visuel « ça écoute »).
- `ACTION_UP` / `ACTION_CANCEL` → `KEY_UP` ².
- Le toucher est consommé, il n'atteint jamais le stream.
- Garde-fous : relâche forcée de ² quand le menu en jeu s'ouvre, en `onPause`/perte de focus, quand
  le bouton est masqué ou passe en mode déplacement, et à l'arrêt de la connexion.

**Afficher / masquer.**
- Réglage `checkbox_voice_ptt_button` (défaut **off**), catégorie *UI Settings*, à côté du bouton
  menu flottant.
- Menu en jeu : « Afficher / Masquer le bouton vocal » — **écrit le réglage** (l'état survit au
  stream suivant), dans le profil actif s'il y en a un (même règle que « Appliquer » du benchmark,
  à cause de `OverlaySharedPreferences`).

**Déplacer.**
- Menu en jeu : « Déplacer le bouton vocal » (visible seulement si le bouton est affiché).
- En mode déplacement, le bouton change d'aspect (contour / pulsation) et un court message dit
  « Faites glisser le bouton, touchez ailleurs pour valider ». Le bouton suit le doigt, borné à
  l'écran ; aucun envoi de touche dans ce mode.
- Toucher en dehors du bouton (ou la touche Retour) valide et sort du mode. Ce toucher de
  validation n'est pas envoyé au stream.
- Position enregistrée en **fraction de l'écran** (0–1 pour X et Y), pas en pixels, pour survivre
  à la rotation et aux changements de résolution. Absente = bas à droite avec une marge.

**Textes.** EN (`values/strings.xml`) + FR (`values-fr/strings.xml`).

## Calls faits par Claude (à corriger si besoin)

- Taille **56 dp**, opacité **50 %** au repos, **90 %** pendant l'appui.
- Réglage désactivé par défaut (fonction de niche, demande un outil côté PC).
- Le bouton du menu en jeu écrit le réglage (plutôt qu'un masquage valable pour le seul stream).
- Validation du déplacement par un toucher ailleurs, sans bouton « OK ».

## Laissé au builder

- Tracé exact de l'icône micro (dans la contrainte ci-dessus) et drawable de contour en mode
  déplacement.
- Où brancher précisément les garde-fous de relâche dans le cycle de vie de `Game`.
- Tests JVM : la conversion fraction ↔ pixels et la règle « position absente = bas à droite » sont
  testables sans Android ; le reste se teste à la main sur appareil.
