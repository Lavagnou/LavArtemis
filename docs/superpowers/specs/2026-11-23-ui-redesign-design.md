# Refonte de l'interface — design

*2026-11-23 — validé d'avance par l'utilisateur. Version Dev uniquement. **Livré** : desktop en
21.8.0-dev.7 (l'ancienne interface Qt est supprimée), Android en 21.8.0-dev.9.
Écarts voulus entre les deux : `2026-11-24-pc-android-parity.md`. Décisions détaillées :
mémoire `ui-redesign-decisions`, prototypes dans `docs/ui-redesign/prototype/` (copie locale hors git ; référence visuelle
et comportementale : `lavartemis-prototype.html#braise-pc`, `#affiche-pc`, `#ludique-pc`).*

## Pourquoi

L'interface héritée de Moonlight (grille d'hôtes, grille d'applis, 12 groupes de réglages, ~130 textes
non traduits) ne ressemble pas à un frontal de console. L'utilisateur veut l'esprit de Playnite
Fullscreen : de grandes jaquettes, la session en cours mise en scène, des thèmes intégrés, une
interface qui s'adapte à l'entrée utilisée (manette, clavier, souris, tactile).

## Objectifs

1. Un **accueil** par hôte : bloc « Scène » de la session en cours (titre, durée, Reprendre, Quitter),
   Bureau et Playnite Fullscreen en deux demi-cartes, puis la bibliothèque (jeux Playnite, la plus
   récemment jouée d'abord, dans l'ordre que donne LavApollo ≥ 2.4.93 ; sans jaquette, le nom du jeu).
2. La session en cours se voit sur les trois thèmes : Scène + « Projecteur » (le reste assombri et
   désaturé) + halo animé sur l'élément en cours.
3. **Menu contextuel** : popover collé au pointeur (souris), dialogue centré (manette/clavier). Entrées :
   Lancer/Reprendre, Quitter, Relancer la session, Créer un raccourci .art (PC), Masquer (jeux Playnite
   seulement ; les jeux masqués se réaffichent depuis l'accueil).
4. **Mes PC** : liste des hôtes (état, ajout, appairage par PIN, renommer, oublier, réveil, benchmark —
   le benchmark vit avec le PC, pas dans les réglages).
5. **Thèmes Braise, Affiche, Ludique**, chacun en clair et sombre, avec Système / Clair / Sombre
   (bouton soleil/lune dans l'en-tête + réglage), « Système » suivant l'OS en direct.
6. **Entrée active** : indications manette seulement avec une vraie manette, clavier avec le clavier,
   aucune à la souris ; anneau de focus sauf à la souris (soulèvement au survol), « ⋯ » au survol.
7. **Réglages** : nouvelle organisation avec **mode expert** (désactivé par défaut), suppressions du tri.
8. **Touches PC** : pastille Multi-écran dans l'en-tête, page Raccourcis clavier, menu en jeu
   (envoyer des touches, presse-papiers, commandes du PC).

## Non-objectifs

- Pas de nouveaux réglages de stream ; les écrans de stream eux-mêmes ne changent pas.
- La stable garde l'interface actuelle (tout vit sur la branche `dev`).

## Anti-objectifs

- Le « truc générique fait avec l'IA » : barre plate, onglets, listes denses, tiroir latéral.
  L'utilisateur a rejeté une version PC de ce style : le PC reprend le design Android/console.
- Afficher l'image d'un jeu sur la carte d'une appli (Bureau, Playnite) : leurs propres jaquettes.
- Casser un parcours existant : appairage, réveil, lancement, quitter, benchmark, mapper de manettes.

## Approches

1. **Nouveau frontal QML à côté de l'ancien** *(retenue)* : `app/gui/v2/`, branché par `main.qml`.
   Réutilise `ComputerModel`, `AppModel`, `StreamSegue`, les dialogues d'appairage et de fermeture.
   Le réglage « ancienne interface » de secours a servi pendant la transition ; l'ancienne interface
   (`PcView`, `AppView`, `SettingsView`, barre d'outils) est maintenant supprimée et l'appli démarre
   toujours sur l'accueil v2.
2. Restyler les vues existantes : garde la structure Moonlight (grilles, barre), ce que l'utilisateur
   veut quitter.
3. Réécrire en C++ Widgets : abandonne QML et tout l'existant (navigation manette).

## Décisions

### Thèmes

- `gui/v2/Theme.qml` (singleton QML) : jetons du prototype (`THEMES` de `logic.js`), un jeu par thème et
  par variante (`bg, surface, surface2, fg, muted, accent, onAccent, line, radius r/rs/pill, police
  display/texte, graisse, majuscules, interlettrage`).
- Variante choisie par `ArtemisSettings.uiThemeMode` : 0 Système, 1 Clair, 2 Sombre ; thème par
  `ArtemisSettings.uiTheme` (`braise` par défaut). Système suit `Qt.styleHints.colorScheme`.
- Polices du prototype embarquées si leur licence le permet (OFL : Space Grotesk, Inter, Archivo,
  Bricolage Grotesque), sinon repli système.

### Accueil (`HomeView.qml`)

- En-tête : nom de l'hôte + sélecteur ‹ › (Q/E, LB/RB), pastilles Multi-écran, soleil/lune, Mes PC,
  Réglages (icône engrenage).
- Bureau : appli dont le nom est Desktop/Bureau ; Playnite : appli `IsPlayniteHost` (ou nommée
  Playnite). Puis la bibliothèque des jeux Playnite (`IsPlayniteGame`). Les autres applis de l'hôte ne
  sont pas montrées (pas de section « Applis »).
- Jeu lancé pendant Playnite : démarre dedans (déjà côté hôte, `startsInsidePlaynite`), la Scène
  affiche « Playnite — en jeu : X ».
- Clic/A : lance ou reprend ; appui long/clic droit/Y/« ⋯ » : menu contextuel.

### Réglages

- Pages : Profil, Affichage, Vidéo, Audio, Entrées, Manettes, Apparence, PC, Avancé ; en plus
  Raccourcis clavier (PC) et Contrôles à l'écran (Android).
  Le mode expert affiche les réglages « Expert » du tri ; ceux « à retirer » disparaissent et sont figés à
  leur défaut (HDR, mise à l'échelle de la résolution, souris à la manette… ; « Optimiser les réglages du
  jeu » toujours actif).
- Réglages propres à l'appareil, hors profils : langue, apparence, micro, mises à jour (+ côté PC
  recherche des PC, tampon audio, presse-papiers…).

## Livraison par étapes (chacune publiée en release Dev, toutes livrées)

1. Thèmes + accueil + menu contextuel + Mes PC + page Apparence (desktop).
2. Réglages v2 avec mode expert et suppressions.
3. Menu en jeu v2, page Raccourcis.
4. Android (mêmes écrans, tactile complet, tailles par densité).

## Laissé au constructeur

- Animations exactes, tailles fines, ordre des entrées secondaires du menu.
