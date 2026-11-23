# Interface v2 sur Android — design technique

*2026-11-23. Le design (écrans, thèmes, comportements) est celui déjà validé : prototype
`docs/ui-redesign/prototype/` (copie locale hors git) et canvas claude.ai, plateforme `android`, plus les
trois planches de densité (console Odin 2 Portal, tablette, téléphone Pixel 7). La version PC
(`desktop/app/gui/v2/`) est la référence de comportement : même accueil, mêmes menus, mêmes textes.
L'utilisateur a laissé la partie technique à l'implémenteur.*

## Périmètre

- **LavArtemis Dev seulement** (`BuildConfig.LAV_DEV`). La stable garde son interface.
- Écrans : accueil (+ bibliothèque complète), Mes PC, Réglages v2 (mode expert), menu en jeu aux
  couleurs du thème. Le stream lui-même (`Game`) ne change pas.
- Thèmes Braise / Affiche / Ludique, chacun clair et sombre, Système / Clair / Sombre.
- Tout fonctionne **au tactile** (appui = lancer, appui long ou « … » = options, listes au doigt), à la
  **manette** et au **clavier** (focus visible, A/B/Y, LB/RB pour changer de PC).
- Textes **en français**, comme la v2 PC. Anglicismes courants gardés (stream, benchmark…).

## Approches

1. **Vues Android natives en Java, construites en code** *(retenue)* : le projet est Java seulement
   (pas de Kotlin, donc pas de Compose) ; `recyclerview`, `material` et `appcompat` sont déjà là. Les
   jetons de thème deviennent des couleurs, rayons et polices appliqués par une petite boîte à outils.
2. WebView qui reprendrait le HTML du prototype : rendu fidèle tout de suite, mais navigation manette,
   performances et pont Java/JS fragiles ; le prototype est une démo à état fictif.
3. Restyler `PcView`/`AppView` : garde la structure Moonlight (grilles), ce que l'utilisateur veut quitter.

## Architecture

- Paquet **`com.limelight.v2`**. `PcView.onCreate()` redirige vers `v2.HomeActivity` quand
  `BuildConfig.LAV_DEV` (avant `setContentView`) : le manifest, les raccourcis et `art://` restent tels quels.
- **`V2Theme`** : jetons copiés de `desktop/app/gui/v2/Theme.qml` (mêmes couleurs, rayons, bordures,
  `dim()`), polices OFL embarquées dans `res/font` (Space Grotesk, Inter, Archivo, Bricolage Grotesque,
  copiées de `desktop/app/res/fonts`). Choix stockés dans les préférences par défaut : `v2_theme`
  (`braise` par défaut), `v2_theme_mode` (0 Système, 1 Clair, 2 Sombre), `v2_expert`.
- **Briques** (`v2/ui/`) : fond de carte (GradientDrawable aux rayons du thème), bouton pilule, bouton
  rond à icône (vecteurs `res/drawable/v2_ic_*`), tuile de jaquette, carte d'appli, dialogue et menu
  d'options du thème, anneau de focus, halo animé de l'élément en cours, assombrissement (gris +
  luminosité multipliée + opacité, valeurs de `Theme.dim()`).
- **Entrée active** (`V2Input`) : dernier périphérique (tactile, manette, clavier) ; indications A/B/Y
  et anneau de focus seulement à la manette ou au clavier.
- **Densité** : tailles en dp ; mise en page « compacte » quand la hauteur utile < 480 dp (téléphone
  en paysage), « console » sinon ; cibles tactiles ≥ 48 dp.
- **Données** : `ComputerManagerService` (binder, `startPolling`, `createAppListPoller`), `NvHTTP`,
  `CachedAppAssetLoader` (jaquettes), `ServerHelper.doStart/doQuit`, `PairingManager`, `WakeOnLanSender`,
  jeux masqués dans les préférences `HiddenApps` — exactement comme `PcView`/`AppView` aujourd'hui.

## Écrans

- **Accueil** : en-tête (PC courant + changement de PC, Mes PC, Réglages, clair/sombre), Scène de la
  session en cours (titre, durée, Reprendre, Quitter), cartes Bureau et Playnite Fullscreen, rangée
  « Récents » (bibliothèque Playnite, la plus récente d'abord, ordre de l'hôte) et « Tout voir » vers la
  bibliothèque complète. Projecteur (le reste assombri) et halo sur l'élément en cours. Menu d'options :
  Lancer / Reprendre, Quitter, Relancer la session, Masquer / Ne plus masquer. Jeu Playnite pendant que
  Playnite tourne : lancé dedans, sans quitter. Pas de section « Applis ».
- **Mes PC** : une carte par PC appairé (état, adresse, ce qui tourne), « Ajouter un PC » (trouvés sur
  le réseau, ou par adresse), appairage par PIN, menu Ouvrir / Benchmark / Réveiller / Renommer /
  Oublier.
- **Réglages v2** : pages et lignes (interrupteurs, choix, curseurs, actions) sur les clés existantes de
  `preferences.xml`, réglages « expert » masqués hors mode expert, écriture compatible avec les profils.
- **Menu en jeu** : la même liste d'options qu'aujourd'hui, dans un dialogue aux couleurs du thème.

## Vérification

Tests JVM (Robolectric) de la logique pure (tri, choix de section, valeurs d'assombrissement, lignes
de réglages) et **captures Robolectric** (`@GraphicsMode(NATIVE)`) des écrans avec des données factices,
relues avant chaque livraison. Pas d'essai sur les appareils de l'utilisateur sans nécessité.
