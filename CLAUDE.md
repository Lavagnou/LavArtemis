# CLAUDE.md

> **LavArtemis** — Client Android de *game streaming* (fork d'Artemis / Moonlight) pour les hôtes **Apollo** / **Sunshine** (protocole NVIDIA GameStream).
> *LavArtemis is an Android game-streaming client — a fork of Artemis / Moonlight — for Apollo / Sunshine hosts.*

---

## 📖 Vue d'ensemble

Lineage du fork :
**Moonlight Android** → **Artemis** (ClassicOldSong, anciennement « Moonlight Noir ») → **LavArtemis** (Lavagnou).

Git remotes :
- `origin` → https://github.com/Lavagnou/LavArtemis
- `upstream` → https://github.com/ClassicOldSong/moonlight-android (Artemis)

Le code original (Moonlight) est attribué à Cameron Gutman et al. (voir `README.md`). **Tout le travail LavArtemis-spécifique a été ajouté le 2026-06-12** (~7 commits, assistance Claude Code + Copilot), concentré dans le hot path du streaming vidéo.

LavArtemis hérite des fonctionnalités Artemis : manettes virtuelles personnalisables, modes souris multiples, profils, raccourcis `art://`, mode portrait, support des écrans externes (Apollo Virtual Display), et **SBS 3D pour écrans externes via MiDaS** (TFLite + OpenCV).

## 🧱 Stack technique

| Élément | Valeur |
|---|---|
| Langage app | **Java uniquement** (zéro Kotlin) |
| Langage natif | **C** via NDK (**ndk-build**, pas CMake) |
| Build | Gradle 8.13 (Groovy DSL, `apply plugin`), AGP 8.13.0 |
| compileSdk / minSdk / targetSdk | 36 / 21 / 34 |
| Java (source/cible) | 11 (builder avec JDK 17+) |
| NDK | `27.0.12077973` |
| versionName / versionCode | `lavVersionName` en tête d'`app/build.gradle` (`-dev.` = appli Dev, voir plus bas) |

Mono-module : `:app` (pas de version catalog, pas de `buildSrc`).

## 🗂️ Architecture

Package racine **`com.limelight`** (hérité Moonlight — inchangé). Sous-packages notables :
- `nvstream/` — cœur du client de stream (`NvConnection`, `StreamConfiguration`) + `jni/MoonBridge` (pont JNI), `http/` (pairing, `NvHTTP`), `mdns/`, `wol/`
- `binding/` — `video/MediaCodecDecoderRenderer` (décodeur HW), `audio/AndroidAudioRenderer`, `crypto/AndroidCryptoProvider` (BouncyCastle), `input/` (manettes, capture, virtual_controller, drivers USB, evdev en root-only)
- `computers/`, `discovery/` — gestion et découverte d'hôtes
- `preferences/`, `profiles/` — réglages et profils (feature Artemis)
- `grid/`, `ui/`, `utils/` — UI et helpers (dont `Stereo3DRenderer` pour le SBS 3D)

Entrées du manifest : `ArtemisApplication` (Application), `PcView` (launcher), `Game` (activité de stream), `GameMenu`, `ProfilesActivity`, `StreamSettings`, `ShortcutTrampoline` (deep links `art://`).

## 🚀 Build & commandes

**Première fois** (le cœur natif est un sous-module, obligatoire) :
```sh
git submodule update --init --recursive
```
Créer `local.properties` (gitignored) avec `ndk.dir=<chemin du NDK 27.0.12077973>` (ou laisser Android Studio gérer le SDK/NDK).

Commandes Gradle (depuis la racine) :
```sh
./gradlew assembleNonRoot_gameDebug      # APK debug (flavor publié)
./gradlew assembleNonRoot_gameRelease    # APK release
./gradlew assembleRootDebug              # flavor root (dev only, maxSdk 25)

./gradlew :app:testNonRoot_gameDebugUnitTest   # tests JVM, flavor nonRoot
./gradlew :app:testRootDebugUnitTest           # tests JVM, flavor root
./gradlew :app:testDebugUnitTest               # les deux flavors
./gradlew test                                  # agrégé (tâche racine)
```

> Sous Windows PowerShell, préfixer par `.\` : `.\gradlew.bat assembleNonRoot_gameDebug`.

## 🧪 Tests

- **JVM uniquement** via **Robolectric 4.16** (`@Config(sdk = {33})`), JUnit 4, Mockito. `testOptions.unitTests.includeAndroidResources = true` (layouts réels).
- **Aucune instrumentation** `androidTest` / Espresso.
- ⚠️ Les classes JNI natives (`MoonBridge`, `GameManager`, `BackdropFrameRenderer`) chargent des `.so` → il **faut les shadower** en test (shadows custom dans `app/src/test/java/com/limelight/shadows/`) sinon `UnsatisfiedLinkError`.
- Guide complet : **`android_test_setup.md`**.
- `BuildConfig.APPLICATION_ID` varie par flavor/build type → en test, utiliser `context.getPackageName()`.

## 🧩 Flavors, build types & applicationId

Dimension `root` :

| Flavor | applicationId | maxSdk | Détails |
|---|---|---|---|
| `nonRoot_game` | `com.limelight` | — | Flavor **publié** par CI, le seul |
| `root` | `com.limelight.root` | 25 | Binaire natif `evdev_reader`, `ROOT_BUILD=true`, **dev-only** |

Build types :

| Type | suffixe applicationId | label |
|---|---|---|
| `debug` | `.lavdebug` | LavArtemis (Debug) |
| `release` | `.lav` | LavArtemis |

→ appId release effectif (nonRoot) : **`com.limelight.lav`**. ABI buildés et publiés : **`arm64-v8a` et `armeabi-v7a`**, un APK par ABI (x86 / x86_64 ne sont ni buildés ni publiés).

> ⚠️ **Ne pas retirer `armeabi-v7a`.** Beaucoup d'appareils Android TV font tourner un userspace
> **32 bits sur du silicium 64 bits** : un Fire TV Stick 4K Max (`AFTKRT`, 2023) déclare
> `armeabi-v7a,armeabi` et rien d'autre, comme la famille Chromecast with Google TV. Une release
> arm64-only y échoue à l'installation avec `INSTALL_FAILED_NO_MATCHING_ABIS`, et **rien ne le
> signale au build** — les libs prébuildées (`libopus`, `openssl`) existent pour les 4 ABI, le
> natif compile, seul le `splits { abi { include ... } }` décide. Vérifier un appareil avec
> `adb shell getprop ro.product.cpu.abilist`.
>
> Le décodeur **PyroWave n'existe que dans l'APK arm64** (voir plus bas) : ce n'est pas une raison de
> retirer le 32 bits, qui reste l'unique APK installable sur ces appareils et streame en
> H.264/HEVC comme avant.

> 🎮 **Manettes exotiques : rien de spécifique côté Android.** `ControllerHandler.java` détecte les axes **par heuristique** à partir des `MotionRange` déclarés (stick gauche X/Y, stick droit RX/RY sinon Z/RZ, croix HAT_X/HAT_Y). C'est ce qui fait marcher des périphériques HID génériques — dont la RadioMaster TX15 — sans une ligne de code dédiée.
> Un variant `tx15_game` a existé (remap explicite des axes, `BuildConfig.TX15_BUILD`) : c'était un essai, **retiré** — le chemin standard fait déjà le travail. Ne pas le réintroduire sans preuve qu'un appareil résiste à l'heuristique.
> ⚠️ Côté **desktop**, SDL refuse au contraire de deviner : sans entrée dans `gamecontrollerdb.txt`, un périphérique reste un simple joystick et le client l'ignore (`SDL_IsGameController`). C'est la différence de fond entre les deux plateformes.

> ⚠️ **Ne jamais retirer le suffixe `.lav`.** Long commentaire dans `app/build.gradle` (release) : un APK release publié avec l'applicationId Moonlight officiel pollue la Play Console du projet amont — Google attribue les crashs par appId, indépendamment de la signature.

`bundle.language.enableSplit = false` (le sélecteur de langue in-app a besoin de toutes les locales).

## 🧪 Stable / Dev — deux applis côte à côte

**LavArtemis Dev** s'installe à côté de la stable (Android et Windows) et reçoit ses propres releases :
c'est là que partent les nouveautés (extension Playnite, refonte de l'interface) avant la stable.
Spec : `docs/superpowers/specs/2026-11-23-stable-dev-split-design.md`.

- **Branches** : `main` = stable, `dev` = Dev, dans LavArtemis **et** LavArtemis-Qt. Le `desktop/` de
  la branche `dev` pointe sur la branche `dev` de LavArtemis-Qt.
- **Le canal se lit dans la version, jamais dans la branche** : une version qui contient `-dev.`
  (`0.7.0-dev.3`) construit la Dev. `def lavVersionName` en tête d'`app/build.gradle` est la source
  (`scripts/local-release.ps1` et `release.yml` lisent cette ligne). Passer en stable = changer la version, donc le canal : une fusion
  `dev → main` ne peut pas l'emporter en douce.

| | Stable | Dev |
|---|---|---|
| applicationId release / debug | `com.limelight.lav` / `.lavdebug` | `com.limelight.lav.dev` / `.lavdebug.dev` |
| Nom, icône | LavArtemis, logo lune | LavArtemis Dev, plan d'atelier bleu + pastille DEV (`app/src/dev/res`, ajouté aux source sets des flavors) |
| Textes français | vouvoiement hérité | **tutoiement** : `app/src/dev/res/values-fr/strings_tu.xml` remplace, pour la Dev seule, les chaînes au « vous » qu'elle peut afficher (mise à jour, quitter, erreurs de stream, benchmark…). Une nouvelle chaîne affichée par la Dev dont le texte stable dit « vous » s'y ajoute |
| Qt : organisation (registre, AppData) | `LavArtemis` | `LavArtemis Dev` |
| Installeur Windows | dossier, raccourcis, UpgradeCodes d'origine ; fenêtre fond nuit | les siens ; **pas** d'`art://` ni de `.art` ; fenêtre « plan d'atelier » |
| Release (construite en local) | tag `vX.Y.Z`, Android + Windows x64 | tag `vX.Y.Z-dev.N`, **prerelease**, Android + Windows x64 |
| Updater in-app | `/releases/latest` | `/releases`, première release `-dev.` |

> ⚠️ **Valeurs stables intouchables.** Le suffixe `.lav`, l'organisation `LavArtemis`, les UpgradeCodes
> **MSI** et les clés `HKCU\Software\LavArtemis` doivent rester identiques au bit près : les
> changer couperait la mise à jour des installations existantes. Les UpgradeCodes du **bundle** ont
> changé une fois, avec la renumérotation (ci-dessous) ; les nouveaux sont à leur tour intouchables.

> ⚠️ **La Dev a sa propre organisation Qt, pas seulement un autre nom d'appli** : désinstaller la
> stable supprime `HKCU\Software\LavArtemis` et `%LOCALAPPDATA%\LavArtemis` en entier. **Une mise à
> jour ne doit donc jamais exiger de désinstaller.**

> ⚠️ **Version numérique côté Windows.** qmake et le MSI refusent `0.7.0-dev.3` : `app.pro` en fait
> `0.7.0.3` (`VERSION`), `VERSION_STR` garde la chaîne lisible. Le MSI ne compare que trois nombres :
> la Dev remplace donc aussi une version égale (ligne (a) ci-dessous).

### 🔢 Numérotation propre (depuis 0.7.0-dev.1)

Jusqu'à `21.8.0-dev.9` / `21.7.1`, le numéro venait de Moonlight. LavArtemis compte désormais seul :
la Dev repart à **`0.7.0-dev.N`** (première : `0.7.0-dev.1`, `versionCode` 98, qui continue de
croître), la stable passera à **`1.0.0`** à la toute fin. Le saut se fait **une fois, à la main** :
l'utilisateur réinstalle ses appareils lui-même (les updaters des 21.x voient 0.7 comme plus ancien et
ne le proposent pas ; c'est accepté).

> ⚠️ **INVARIANT : la nouvelle numérotation reste sous `20.0.0`, pour toujours.** Tout ce qui compare
> des versions prend un majeur ≥ 20 pour l'ancienne numérotation, plus vieille que toute nouvelle :
> `UpdateChecker.compareVersions()` (Android : updater et nettoyage des APK téléchargés),
> `AutoUpdateChecker::compareVersionStrings()` (desktop : updater et nettoyage des installeurs), et la
> table Upgrade du MSI. `app/build.gradle` et `desktop/scripts/build-arch.bat`
> refusent de construire une version ≥ 20.

**Installeur Windows** (`desktop/wix/LavArtemis/Product.wxs`, les deux canaux) : `MajorUpgrade` est
remplacé par une table Upgrade explicite sur le même UpgradeCode MSI :

| Ligne | Versions installées | Effet |
|---|---|---|
| (a) | `0.0.0` → cette version (incluse pour la Dev, exclue pour la stable, comme avant) | mise à niveau |
| (b) | au-dessus de cette version, sous `20.0.0` | détectée seulement ; la condition de lancement refuse : « Une version plus récente de [ProductName] est déjà installée. » — **la protection permanente contre le retour en arrière** |
| (c) | `20.0.0` et plus (les 21.x) | mise à niveau, quel que soit le numéro : le saut unique |

`RemoveExistingProducts` reste juste après `InstallInitialize`. L'ancien produit est retiré avec
`UPGRADINGPRODUCTCODE` posé, quelle que soit la ligne qui l'a trouvé : son `DeleteRegistryKey`
(`NOT UPGRADINGPRODUCTCODE`) ne part pas, les réglages et appairages restent, comme avec `MajorUpgrade`.

**Bundle Burn** (`Bundle.wxs`) : Burn refuse un bundle plus ancien qu'un bundle apparenté installé, et
0.7 est « plus ancien » que 21.8. Le bundle a donc **de nouveaux UpgradeCodes** (Dev
`72171b88-37c9-4543-9707-1cd668b848a4`, stable `51150c91-b139-4a69-aa95-e8e6df6efaec`) : le bundle
21.x n'est plus apparenté, la ligne (c) du MSI remplace son MSI, et le contrôle de version de Burn
protège à nouveau la nouvelle numérotation (il compare aussi le 4ᵉ nombre, que le MSI ignore).

> 📋 **Reste une fois** : l'entrée du bundle 21.x dans « Applications installées ». La désinstaller est
> sans risque (son MSI, identifié par son propre ProductCode, est déjà parti ; les VC++ sont
> `Permanent`). Ne **pas** la « réparer » : elle réinstallerait son MSI 21.x, dont la table voit 0.7
> comme plus ancien. Même limite pour un ancien installeur 21.x lancé à la main : ses règles sont
> figées, il redescendrait en 21.x.

- **Premier lancement de la Dev PC** : `ChannelMigration::importStableSettingsOnce()` copie une fois les
  réglages de la stable (registre + fichiers de `AppConfigLocation`) ; la stable n'est que lue. Android
  ne le permet pas : la Dev Android s'appaire à nouveau.
- Le réglage « inclure les pre-releases / builds CI » a disparu des deux clients : l'appli Dev le
  remplace, et un APK d'un autre applicationId serait de toute façon refusé à l'installation.
- **Release Dev** : `lavVersionName = "X.Y.Z-dev.N"` + `versionCode` +1 sur `dev`, pointeur `desktop/`
  sur le commit voulu de la branche `dev` de LavArtemis-Qt, puis `scripts/local-release.ps1` (voir
  « CI/CD & release ») qui pose le tag `vX.Y.Z-dev.N` et publie la prerelease.
- **Politique** (décision de l'utilisateur) : **tout le travail va dans la Dev**, sans report vers la
  stable. La stable sort en dernier, après des semaines d'essai de la Dev par l'utilisateur, qui est pour
  l'instant le seul utilisateur : pas de doc ni de parcours « pour les autres utilisateurs ».

## 🎮 Jeux Playnite (LavApollo Link, Dev)

Chaque jeu installé de Playnite devient une appli de l'hôte, lancée **dans** Playnite (extension
« LavApollo Link » et applis virtuelles côté LavApollo : `LavApollo/docs/superpowers/specs/
2026-11-23-playnite-integration-design.md`). Les clients Dev demandent `applist` avec
**`playnite=1`** : sans ce paramètre LavApollo ne liste pas ces jeux, donc la stable ne voit rien changer.

- Android : `NvApp.isPlayniteGame()/isPlayniteHost()` (`IsPlayniteGame`/`IsPlayniteHost` de
  `applist`), `ComputerDetails.playniteHostRunning`/`playniteGameName` (`serverinfo`), testés par
  `NvHTTPPlayniteTest`. Toucher un jeu Playnite pendant que Playnite tourne le lance directement
  (`HomeModel.decide()` → `INSIDE_PLAYNITE` en Dev, `AppView` en stable), et `NvConnection` envoie alors un **`resume`** avec ce jeu : LavApollo le démarre dans
  Playnite et la session continue, au lieu de quitter l'appli en cours.
- Desktop : même logique (`AppModel::startsInsidePlaynite()`, `Session`).
- **Données de l'hôte pour les écrans du prototype** (vague 5, 2026-11-26, données seulement, aucune UI ;
  protocole : `LavApollo/docs/lavartemis-host-data.md`). Par jeu : `PlayniteInstalled`, `PlaynitePlaytime` (s),
  `PlayniteLastActivity` (s Unix), `PlayniteBackground` + `/appasset?asset=background` (404 sans image) ; par PC :
  `CurrentGameElapsed`, `PlayniteGameElapsed` (des durées, pas des dates : les horloges diffèrent), `HostFormFactor`
  (`tower`/`mini`/`laptop`), `HostLink` (`wired`/`wifi`). Android : `NvApp.isPlayniteInstalled()`,
  `getPlayniteLastActivity()`, `getPlaynitePlaytime()`, `hasPlayniteBackground()`, `NvHTTP.getBackground()` ;
  `ComputerDetails.sessionStartedAt` / `playniteGameStartedAt` (ms, horloge de l'appareil, gardés sauf écart de plus
  de 5 s), `hostFormFactor`, `hostLink`, `latencyMs`. **La latence est mesurée par le client** :
  `computers/HostLatency` = un `connect` TCP sur l'adresse qui vient de répondre au poll (`tryPollIp`), au moins
  1 ms, -1 sinon ; le desktop fait de même. L'accueil prend le début de session de l'hôte quand il le donne
  (`HomeActivity`, `SessionClock` reste pour les autres hôtes). Tests : `NvHTTPPlayniteTest`, `HomeModelTest`,
  `HostLatencyTest` ; banc : scénario 15.
- **Jeux pas installés** : LavApollo Link 1.1.0 exporte aussi les jeux non installés (non masqués) ; les lancer
  passe par Playnite, qui **propose de les installer** (son propre dialogue sur le PC) ; la session attend alors le
  jeu 24 h au lieu de 120 s.
- ⚠️ **« Récents » figés (corrigé le 2026-11-26)** : LavApollo ne reconstruit sa liste d'applis que quand rien ne
  tourne, et l'utilisateur laisse Playnite Fullscreen lancé : l'ordre restait celui du démarrage de l'hôte.
  `/applist` prend maintenant l'ordre et l'état des jeux dans `library.json` tel qu'il est
  (`playnite::library_snapshot()`), et **les deux clients trient sur `PlayniteLastActivity`** (jamais joués à la
  fin, tri stable : un hôte qui ne l'envoie pas garde son ordre) — `HomeModel.sortRecentFirst()`,
  `NvComputer::sortApps()`.
- **« Quitter le jeu »** (menu en jeu, Android `GameMenu` et desktop `QuickMenu`) : `/playnite-quit-game`
  ferme seulement le jeu lancé dans Playnite, le stream continue et revient à Playnite.

## 🎨 Refonte de l'interface (Dev)

Spec `docs/superpowers/specs/2026-11-23-ui-redesign-design.md`, prototype validé de référence dans
`docs/ui-redesign/prototype/`, copie locale hors git car elle contient des noms de machines (`lavartemis-prototype.html#braise-pc`, `#affiche`, `#ludique-pc`…,
images absentes hors claude.ai). **Desktop terminé** (21.8.0-dev.7) : accueil, thèmes, Mes PC, Réglages v2
avec **mode expert**, menu en jeu, raccourcis ; l'ancienne interface Qt est supprimée et tout est en
français (`desktop/app/gui/v2/`, voir `desktop/CLAUDE.md`). **Android terminé** (21.8.0-dev.9) :
accueil, bibliothèque, Mes PC, Réglages v2 avec mode expert, menu en jeu et dialogues aux couleurs du
thème, en français au tutoiement (ci-dessous).

### 🤝 Parité PC / Android (règle de l'utilisateur)

« Il faut garder une constance le plus possible entre les deux versions. Les seules choses qui diffèrent
doivent être des features précises d'une version. Le reste doit être commun, au maximum. » Donc :
**même libellé, même description, mêmes options, même défaut, même place et même ordre** pour tout ce que
les deux clients ont ; une différence n'est acceptable que pour une feature propre à une plateforme
(multi-écran, raccourcis clavier, VRR Windows côté PC ; contrôles à l'écran, bouton vocal, rotation, ADPF
côté Android). Tout changement d'un texte, d'un menu ou d'un réglage commun se fait **des deux côtés dans
le même passage**, ou est noté comme écart. État et écarts restants :
`docs/superpowers/specs/2026-11-24-pc-android-parity.md` (le tenir à jour).

### 📱 Interface v2 Android (Dev, phases 1, 2a et 2b)

Design : `docs/superpowers/specs/2026-11-23-android-ui-v2-design.md` (+ `…-codemap.md`). Paquet
`com.limelight.v2`, vues **construites en code** (pas de XML de layout, pas de Compose/Kotlin).
`PcView.onCreate()` redirige vers `v2.HomeActivity` quand `BuildConfig.LAV_DEV`, **avant**
`setContentView` ; la stable garde l'ancienne interface, intacte.

> 🧭 **Portage exact du prototype V8 (branche `port-v8`, 2026-11-26, pas encore fusionné)** — notes, écarts et
> reste : `docs/superpowers/verif-2026-11-25/port-notes.md` (hors git). Fait : fondations, accueil, options, détails, bibliothèque, menu en jeu, Mes PC.
> - **Jetons du prototype** dans `V2Theme` : `warnBg/warnLine`, `popR/popRow/thumbR`, anneau par thème
>   (`focusGap/focusOuter/focusColor`, Affiche `focusFill` : remplissage jaune + encre ; le contour 4 px n'est que sur
>   les jaquettes, `V2Frame.setFocusFill(false)`), lueur par thème (`glowColor/glowPeriodMs…` : braise 2,6 s, prune
>   **violette** 2,4 s, Affiche barre `LiveStyle.BAR` / bloc décalé 1,6 s), couleurs littérales `V2Theme.Lit`.
>   Le desktop ne les a pas encore tous : liste dans port-notes.
> - **Entrée** (`V2Activity`) : A maintenu **500 ms** sur un élément à appui long (`isHoldable`), A **à l'appui**
>   ailleurs, Start = retour, D-pad sur la **grille du prototype** (`FocusGrid`, `focusRows()`). Toasts : `V2Toast`
>   (`V2Activity.toast()`), plus de `Toast` système dans les activités v2. Barre d'aide : `ui/HintsBar`.
> - **Accueil** : `HomeScreen` = une mise en page **par thème** (pièces dans `HomeParts`), tailles par
>   `HomeLayout` (tablette = scène du prototype ×k ; console / téléphone = planches de densité ; le texte rétrécit
>   moins que les blocs). Héros Braise = fond du jeu (`CoverSource.loadBackground`, LavApollo) sinon jaquette floutée
>   (`CoverView.setBlur`). ⚠️ Dans une colonne en `WRAP_CONTENT`, un intercalaire à poids doit être un `Space`, pas
>   une `View` (une `View` prend toute la hauteur offerte en `AT_MOST`). ⚠️ `HomeParts.cssLine()` reprend la hauteur
>   de ligne CSS d'un grand titre (Android lui donne ~1,25 em).
> - **Options** : `V2Sheet` (dialogue centré du prototype) ; listes de `ctxOpts` dans `V2HostActivity.showOptions`,
>   règle `SessionChoice` (rejoindre / dans Playnite / ferme quelque chose → les options s'ouvrent d'abord). Quitter
>   et Relancer passent par le même dialogue ; plus de « Oui / Non ».
> - **Menu en jeu** (Dev) : `V2GameMenu` (tuiles Braise, panneau Affiche, dock Ludique), Reprendre + 9 + « Plus »
>   construits par `GameMenu.showTileMenu` ; **pastille « Menu »** (`ui/MenuPill`) à la place du bouton flottant,
>   le bouton vocal reste. « Plus » liste les entrées du prototype (`StreamMenu.more`, plus de « Menu avancé » en Dev) ;
>   Touches / Commandes / Presse-papiers / Plus en `V2Sheet` (grille `V2Sheet.columns()` = ctxCols), et Annuler / B les
>   referme **sur leur tuile** (`GameMenu.sub()` / `showSubSheet`). Statistiques au style du thème (`ui/StatsStyle`).
>   ⚠️ La pastille se cache à la manette : `Game` **observe** `dispatchKeyEvent` / `dispatchTouchEvent` /
>   `dispatchGenericMotionEvent` pour `V2Input` (Dev, rien n'est consommé : tout part au stream comme avant), règle
>   `StreamMenu.pillVisible` ; un nouveau chemin qui montre la pastille passe par `applyMenuPill()`.
> - **Bibliothèque** : `LibraryScreen` = une mise en page par thème (Braise grille 6 + fiche, Affiche liste numérotée
>   avec **fenêtre de 8 lignes** à la manette, Ludique grille 8 + barre), RecyclerView (jaquettes chargées à
>   l'affichage), D-pad par `V2Activity.onV2Move` ; filtres et « joué hier » dans `LibraryModel` (= desktop), LB / RB
>   = filtre suivant.
> - **Mes PC** (V8-36 à 42, 65) : `HostsScreen` aux mesures du prototype (`HomeLayout`, carte 400, colonnes
>   min(5, max(3, PC + 1)) bornées par la largeur), icône par type (`hostFormFactor`, tour par défaut), ligne « Tour ·
>   réseau filaire · 4 ms » (`HostCards.meta`, les parties inconnues sont omises), « En cours : {jeu} dans Playnite »,
>   ⋯ toujours visible, barre d'aide A Ouvrir · Y Options · B Retour. Pas de « Réveiller » pour un PC sans adresse MAC
>   (carte ni menu). Dialogues options / ajout / adresse / appairage (PIN en grand) / renommer / oublier en `V2Sheet`,
>   qui gagne un **champ texte** (`Spec.inputLabel`, lu par `Spec.text()`) et un **code PIN** (`Spec.pin`).
>   ⚠️ Affiche : le remplissage jaune du focus repeint tous les textes en encre ; un enfant qui garde ses couleurs
>   (bouton noir, pastille noire) passe par `V2Frame.keepColors()`, et `setFocusFillStroke()` garde le cadre 3 px.
> - **Réglages** (V8-48 à 57) : mêmes `SettingsScreen` / `SettingRowView` / `SettingsCatalog`, aux mesures du
>   prototype (scène 1280 × 720 × `k`, texte × `kt` ≥ 0,9 ; l'étroit < 640 dp garde liste puis page) : Mode expert en
>   tête (carte 64), pages h40 (page courante = accent / onAccent, « EXPERT » pour une page toute experte), lignes en
>   cartes (libellé 15/700, **●** = `SettingsCatalog.followsProfile`, étiquette EXPERT pleine surface2, description 13),
>   interrupteur 46 × 26 (`V2Theme.switchKnob`), curseur 330 avec la valeur au-dessus (« 20 Mbit/s · auto » tant que le
>   débit suit), liste déroulante h40 240–330, action en pilule h38 fg / bg, choix du thème en cartes 108 × 74, en bas
>   **« N réglages avancés masqués · activer le mode expert »** (`SettingsCatalog.hiddenCount`, pluriel `v2_set_hidden_rows`).
>   En-tête « Thème {nom} · LavArtemis {version} », barre A Changer · LB RB Page · B Retour, **LB / RB = page
>   précédente / suivante** en boucle (`SettingsCatalog.stepPage`), toasts du mode expert ; le couper remet le codec
>   Auto s'il était PyroWave et une page qui se vide revient à Affichage. Tests : `SettingsV8Test` ; captures
>   `SettingsScreenshotTest.protoVideo` / `protoPages` (préfixes `settings-video-`, `settings-<page>-`).
> - **Écran de détails** (« Afficher les détails » des options, V8-68) : `DetailsActivity` + `DetailsScreen` (3 mises en
>   page), règles dans `LaunchRows` (note, verrou de l'Écran quand on rejoint, pas des rangées). Les rangées « Réglages
>   de ce lancement » changent les **vrais** réglages (profil actif, écran virtuel, codec, débit), comme le desktop.
>   Gauche / droite sur une rangée : `V2Activity.onV2Horizontal`.
> - **Décisions du 2026-11-26** : l'en-tête garde l'**icône de la Dev** (« plan d'atelier », `ui/AppIconDrawable` =
>   calques `ic_launcher_bg/fg` recadrés), plus de logo du thème ni de puce DEV ; temps de jeu sous une heure en
>   minutes (« 25 min jouées », `PlayTime`, testé), au-delà « N h jouées ».
> - **Le reste (étape 9)** : D-pad / stick à **380 ms puis 110 ms** dans les écrans v2 (`DirRepeat`, testé ; la
>   répétition du système est ignorée, `V2Activity` refait la sienne et sa propre recherche de focus si l'écran n'a pas
>   de grille ; arrêtée sur `onPause` / perte de focus de la fenêtre). Sélecteurs **thème** (appui long sur clair /
>   sombre) et **PC** (tap sur la pastille) en `V2Sheet` (« ✓ » + focus sur le courant, `Spec.focus`). « Oups »
>   (`V2Sheet.oops`), écran virtuel pas prêt (« Lancer quand même »), téléchargement de la mise à jour (`Spec.progressLabel`
>   / `setProgress`, « Annuler le téléchargement » arrête vraiment : `UpdateDownloader.download(…, cancelled)`),
>   « Quitter {X} ? » du stream puis **segue** « Fermeture sur {PC}… » (`ui/SegueView`, aussi sur l'accueil pendant
>   `doQuit`, 1,1 s au moins, puis « Session terminée »). `V2Dialogs.confirm` / `message` passent par `V2Sheet`.
> - Captures comparables : `V2_SHOT_COVERS` = dossier des images et de `shots-data.json` du prototype (jamais dans
>   git) → `ShotData` charge les mêmes jeux, heures, états et jaquettes ; sans la variable, jaquettes dessinées.
> - Captures : `HomeScreenshotTest` (1280 × 720 = prototype, 1280 × 800, 915 × 412, 960 × 540, 3 thèmes × clair /
>   sombre × tactile / manette, hors ligne, options, menu en jeu) ; tests `FocusGridTest`, `HomeLayoutTest`,
>   `SessionChoiceTest`.

- `V2Theme` : jetons **copiés de `desktop/app/gui/v2/Theme.qml`** (couleurs, rayons, bordures, poids,
  `dim()`) — les changer des deux côtés. Prefs par défaut `v2_theme` / `v2_theme_mode` (0 Système, qui
  suit le mode nuit à chaud via `onConfigurationChanged`, 1 Clair, 2 Sombre). Polices OFL variables dans
  `res/font/v2_*.ttf` ; **un XML `res/font/v2_<famille>_<poids>.xml` par graisse** fixe l'axe `wght`
  (`fontVariationSettings`, API 26+ ; en dessous : instance par défaut + faux gras). Licences dans
  `assets/licenses/fonts/`. Icônes `drawable/v2_ic_*` générées depuis les chemins de `LavIcon.qml`.
- `HomeModel` (Java pur, testé) porte les règles du desktop : `kindOf` (= `AppModel::kindOf`), cartes
  Bureau puis Playnite Fullscreen, Récents = jeux Playnite du plus récemment joué au plus ancien
  (`PlayniteLastActivity`, sinon l'ordre de l'hôte), 10 au plus, jamais les masqués ; `decide()` : START / RESUME / INSIDE_PLAYNITE / ASK_QUIT.
  Les autres applis de l'hôte ne sont pas montrées. Les cartes ignorent « Masquer » (le menu ne le propose
  que pour les jeux) : la bibliothèque est le seul endroit où l'on réaffiche les jeux masqués.
- `HomeScreen` / `LibraryScreen` se construisent depuis des **données simples** (`HomeScreen.State`,
  `CoverSource`) : aucune dépendance au service. Les activités (`V2HostActivity` : binding
  `ComputerManagerService`, poller d'applis du PC affiché, lancer/quitter/relancer, jeux masqués
  `HiddenApps` partagés avec `AppView`) les nourrissent ; `HomeScreenshotTest` aussi.
- **Captures** : `HomeScreenshotTest` (`@GraphicsMode(NATIVE)`) écrit des PNG dans `app/build/v2-shots/`
  (ou `$V2_SHOTS_DIR`) aux densités validées (915×412 et 960×540 dp, + portrait). Les relire avant toute
  livraison qui touche la v2.

- **Mes PC** (phase 2a, = `desktop/app/gui/v2/HostsView.qml`) : `HostsActivity` (actions, portées de
  `PcView` : `pair()` = `doPair`, `forget()` = `removeComputer` étape pour étape, ajout manuel =
  `AddComputerManually.doAddPc` dont `parseRawUserInputToUri` / `isWrongSubnetSiteLocalAddress` sont
  devenus `public static`), `HostsScreen` (vues, données simples, grille dont les colonnes suivent la
  largeur : 2 à 3 sur téléphone, 5 au plus), `HostCards` (Java pur, testé par `HostCardsTest` : textes
  d'une carte, tap, menu par état, colonnes, PIN espacé, lien d'appairage). Cartes **par nom** (elles ne
  bougent pas quand un PC s'allume), appairés seulement ; les PC non appairés qui répondent sont dans le
  menu « Ajouter un PC ». Un PC ajouté à la main et non appairé est appairé tout de suite. Succès de
  l'appairage → l'accueil montre ce PC (`PREF_LAST_HOST`, relu par `HomeActivity.onResume`).
- **Lien d'appairage** (`art://host?pin=…&passphrase=…`) : `AddComputerManually` ajoute le PC puis lance
  `PcView` avec `hostname/port/pin/passphrase`, que `PcView` passe en Dev à `HostsActivity`, qui appaire le
  PC dès qu'il répond à cette adresse (`HostCards.matchesLink`, port absent = 47989). Un code de lien expiré
  (3 min) ou déjà servi : LavApollo répond `503 « OTP auth not available. »`, `utils/PairingErrorText` en fait la
  phrase du desktop (« Le code du lien n'est plus valable sur %1… ») ; toute autre panne d'appairage finit par le
  motif court de `HostErrorText`, jamais par le texte anglais de l'exception (`PairingErrorTextTest`).
- **Renommer** : `ComputerManagerBinder.renameComputer(uuid, nom)`. Le schéma de `computers4.db` ne bouge
  pas : le nom choisi vit dans les prefs `HostNames` (uuid → nom) et **réappliqué après chaque poll**
  (`pollComputer` → `HostNames.apply`), sinon le `hostname` du `serverinfo` l'écraserait au poll suivant
  (c'est le `hasCustomName` du desktop) ; il est aussi écrit dans la base. Oublier le PC l'efface.
- `ComputerDetails.apolloVersion` (`ApolloVersion` du `serverinfo`) : « 192.168.1.24 · LavApollo 1.2.0 ».
- **Menu en jeu** : en Dev, `GameMenu.showMenuDialog` passe par `v2/ui/V2ListDialog` (même liste, même
  `run()` donc même `runWithGameFocus` ; « Annuler » devient un bouton ; deux colonnes sur un écran large
  mais bas) ; le message « Aucune commande de serveur » aussi. La stable garde l'`AlertDialog`. Libellés
  depuis `strings.xml` + `values-fr` (traduction de `game_menu_toggle_floating_button` ajoutée,
  « Envoyer le presse-papiers » au lieu de « Télécharger »). **Ordre et libellés du menu rapide du desktop
  pour les entrées communes** (Dev) : Quitter <jeu Playnite>, Déconnecter, Quitter la session, Commandes du
  PC hôte, Envoyer des touches, presse-papiers ×2, Statistiques de performance, micro (seulement s'il est
  envoyé), puis les entrées Android. « Envoyer des touches » = les 15 raccourcis du desktop (sans le HDR Win+Alt+B, retiré des deux)
  (`GameMenu.DEV_SEND_KEYS` = presets de `KeyMacroManager`, mêmes libellés `v2_keys_*`) ; « Menu avancé »
  ne garde que ce qui est propre à Android (le gestionnaire des tâches est Ctrl+Maj+Échap de la liste).
- **Réglages v2** (phase 2b, = `desktop/app/gui/v2/SettingsView2.qml`) : `v2.SettingsActivity` + `SettingsScreen`
  (liste des pages à gauche, « Mode expert » en tête, lignes à droite ; < 640 dp de large : la liste, puis une
  page seule, Retour revient à la liste). L'accueil y mène en Dev ; `StreamSettings` reste celui de la stable.
  **La table des lignes est `v2/settings/SettingsCatalog.java`** : clé de `preferences.xml`, type (interrupteur,
  choix, curseur, action), page, expert, `when()` (l'appareil la propose : mêmes règles que
  `StreamSettings.initializePreferences`, relues par `SettingsEnv.detect`), `dependsOn()` (grisée, comme
  `android:dependency`). Une page sans ligne visible disparaît. Pages : Profil, Affichage, Vidéo, Audio,
  Entrées, Manettes, Contrôles à l'écran, Apparence, PC (presse-papiers, comme le desktop), Avancé.
  - **Classement** : le tri de l'utilisateur (Normal / Expert / Supprimer) pour tout ce qu'il couvre ; pour
    les réglages Android seuls, ce qu'on change couramment est Normal (mode tactile, zoom au pincement, suivre
    la rotation, manette à l'écran, bouton vocal, vibrations…), le réglage fin et le diagnostic Expert. PyroWave
    n'est proposé dans le codec qu'en mode expert. `SettingsCatalogTest` vérifie que chaque clé existe dans
    `preferences.xml`, que **chaque préférence a une ligne ou une raison** (`REMOVED`, `NOT_SHOWN`), et que
    chaque défaut est celui de `preferences.xml` **et** de `PreferenceConfiguration` (et que la clé y est lue).
  - **Supprimés** (`SettingsCatalog.REMOVED`) : HDR, mise à l'échelle de la résolution, souris à la manette
    (Start maintenu). `PreferenceConfiguration.forceRemovedSettings()` les fige **en Dev seulement**
    (`BuildConfig.LAV_DEV`, comme `StreamingPreferences::reload` côté desktop). « Optimiser les réglages du jeu » (sops)
    a disparu des Réglages et reste toujours actif, comme sur le desktop. `NOT_SHOWN` : options de
    l'ancienne grille d'applis (petites icônes, reprise rapide), saisies passées par les listes, liens d'aide
    (wiki Artemis) et lien Obtainium.
  - **Corrigés au passage** : le correctif Joy-Con était écrit `checkbox_enable_joyconfix` par les écrans et
    lu `checkbox_joycon_fix` — il ne faisait rien ; `PreferenceConfiguration` lit maintenant la clé écrite
    (l'ancienne reste honorée) et la ligne est dans Manettes (expert). « Tight Vsync (Experimental) »
    (`checkbox_forceTightThresholds`), jamais lu, est **supprimé** (préférence, champ, chaînes).
  - **Écritures** : `SettingsStore` lit par `getOverlayingSharedPreferences` et écrit par
    `ProfileAwarePreferences.putBoolean/putString/putInt/putFloat/remove` (dans le profil actif s'il porte la
    clé, sinon les prefs de base). **Lignes `global()`** (base, jamais dans un profil) : thème et clair/sombre,
    et deux clés que leur lecteur prend dans les prefs de base (`checkbox_auto_update` par `UpdateChecker`,
    `keyboard_axi_list` par le chargeur des touches) — sinon l'écriture partirait dans le profil et ne serait
    jamais lue. Sont aussi `global()` toutes les clés de **`ProfilesManager.APP_WIDE_KEYS`** (micro, tampon audio,
    presse-papiers ×3, CSV des perfs : ce que le desktop garde dans `ArtemisSettings`, hors profils) ; une clé
    ajoutée à `APP_WIDE_KEYS` doit l'être aussi (`AppWideKeysTest`), et sa valeur est un booléen, une chaîne ou un
    entier (seuls types que `migrateAppWideKeys` recopie). Un nouveau profil est un **instantané complet** (toutes
    les lignes, défauts compris), langue, apparence et lignes `global()` exclues ; « Appliquer comme profil » du benchmark aussi (`BenchmarkSettings.applyAsProfile` part de
    `SettingsStore.snapshot()`), comme `ProfileManager::createProfile()` sur le desktop (`BenchmarkProfileTest`).
  - **Effets de bord repris** de `StreamSettings` : nouvelle résolution / fréquence → débit remis au défaut **tant
    qu'il est le débit recommandé** (`SettingsStore.bitrateIsRecommended()` : jamais écrit, ou égal au défaut ; bougé
    à la main, il reste ; « Revenir au débit recommandé » le fait suivre de nouveau — la règle du desktop,
    `autoAdjustBitrate`, et du texte choisi « Suit la résolution et les images/s tant que tu ne le changes pas ») ;
    quitter PyroWave ramène un débit au-delà de 800 Mbit/s au bout de la piste (comme le desktop) ;
    entrée native → avertissement ; « Personnalisée… » / « Autre… » (`edit_diy_w_h`, `custom_refresh_rate`,
    fréquence formatée en `Locale.US`, pas dans la langue de l'appareil) ; résolution ou fréquence que l'écran
    ne prend plus → réécrite à l'ouverture ; PyroWave absent → codec Auto ; micro → permission, refus = décoché ;
    couper l'enregistrement des perfs efface l'historique ; « Débloquer les FPS » relit la liste ; langue →
    redémarrage en quittant. Imports / exports / partage des logs / recherche de mise à jour :
    `preferences/SettingsFileActions`, partagé avec `StreamSettings`.
  - Clavier / manette : la ligne a le focus ; A / Entrée bascule ou ouvre la liste, Gauche / Droite font
    défiler un choix ou un curseur, Gauche en bout de course revient à la liste des pages ; B : lignes → pages →
    accueil. Le curseur du débit est **courbé** (`curve(3)` : 20 Mbit/s vers 29 % de la course, 800 au bout ; avec le codec
    PyroWave il va à 3 Gbit/s — `SettingDef.maxFrom()` / `maxFor()`, `SettingsCatalog.BITRATE_MAX_KBPS` /
    `BITRATE_MAX_PYROWAVE_KBPS`, mêmes valeurs que `bitrateMax` du desktop) et ses
    pas grandissent avec la valeur. Toute écriture est immédiate, pas de bouton « Enregistrer ».
  - Captures : `SettingsScreenshotTest` (préfixe `settings-`, dont `settings-full-<page>` : chaque page entière
    en mode expert) vérifie aussi qu'aucun texte anglais n'est à l'écran.
  - **Android 9** : la résolution native sans l'encoche se lit dans les insets de la fenêtre (`Display.getCutout()`
    est Android 10+). `V2Activity.onAttachedToWindow` → `SettingsEnv.rememberCutout()` (dernière valeur non nulle
    gardée, statique) ; Réglages relit la liste quand l'encoche devient connue (`onCutoutKnown`).
- **Plus aucun écran « Moonlight » en Dev** (`v2/ui/ThemedDialogs`, `V2Alert`, `V2Page`) :
  - `ThemedDialogs.enabled()` (= `LAV_DEV`) est demandé **par l'appelant**, qui garde sa branche `AlertDialog`
    intacte pour la stable. Passent par là : `utils/Dialog.displayDialog` (OK seul, non annulable,
    `closeDialogs()` le ferme toujours), `SpinnerDialog` (spinner, annulable seulement si `finish`, **jamais au
    toucher extérieur** : annuler termine l'activité, donc le stream qui démarre), `UiHelper.displayConfirmationDialog`
    (texte HTML, liens cliquables ; Retour ne lance ni oui ni non, comme l'AlertDialog), `Game.quit()` /
    `selectMouseMode()`, `UpdateDialogHelper` (3 boutons + téléchargement avec barre), dialogues des commandes à
    l'écran (`VirtualControllerElement`, `keyBoardVirtualControllerElement`, `KeyBoardController` : effacer tout,
    choix multiple des touches = `V2ListDialog.showMultiChoice`), lien `art://` d'`AddComputerManually` (question v2
    sur le fond v2, l'écran d'ajout hérité n'apparaît plus ; même ordre qu'avant : `finish()` puis ajout).
  - `V2Alert` : titre, texte, spinner ou barre, vue en plus, jusqu'à 3 boutons (le 1er en accent) ; un bouton
    ferme **puis** agit. ⚠️ `ButtonRow` passe en colonne quand les boutons ne tiennent pas, et pose `leftMargin`
    en plus de `setMarginStart` : la marge de début est déjà résolue avant son `onMeasure`, la changer seule
    n'avait aucun effet (boutons collés).
  - `V2Page` habille une activité plein écran **sans toucher à sa logique** : couleurs de fenêtre, en-tête retour +
    titre, colonne qui défile, `text/heading/button/buttonRow/choice`. L'activité relaie toucher / touches à
    `V2Input` (anneau de focus manette seulement). Utilisé par `BenchmarkActivity` (modes et objectif = choix v2 ;
    noms de mode/objectif traduits au lieu des `id` du JSON) et `DebugInfoActivity` (mêmes ids de boutons, donc
    même `onClick`).
  - `ShortcutTrampoline` : un raccourci de PC ouvre l'accueil v2 sur ce PC (plus `PcView` + `AppView`).
  - **Textes** : les messages anglais en dur de `NvConnection` (`text(id, anglais)`, repli anglais sans contexte),
    de `Game` (toasts HEVC/AV1/HDR, H.264 absent) et le nom des étapes de moonlight-common-c (`utils/StreamStages` :
    « Échec au démarrage : négociation RTSP (erreur -408) ») passent par `strings.xml` + `values-fr`. Le défaut
    anglais = l'ancien texte, la stable anglaise ne change pas. Les notes des runs de benchmark restent écrites
    en anglais dans le rapport JSON (format partagé avec le desktop) ; `benchmark/BenchmarkNotes` traduit les
    notes connues à l'affichage. Les toasts système ne sont pas thémés (mais sont en français).
- **Deux modes tactiles seulement en Dev : Multitouch (0) et Trackpad (3)**, les deux que l'utilisateur
  utilise, mêmes libellés et même description que la ligne « Écran tactile » du desktop. Trackpad = le mode
  « appui long pour faire glisser » (`RelativeTouchContext` : appui long 650 ms = clic gauche maintenu,
  appui à deux doigts = clic droit, deux doigts qui bougent = défilement ; le zoom au pincement y marche).
  ⚠️ **Une valeur stockée 1, 2, 4 ou 5 n'est jamais réécrite, elle est lue** comme le mode gardé le plus
  proche par `PreferenceConfiguration.devTouchMode()` (2 → 3 ; 1, 4, 5 → 0), appelé aux **trois** endroits
  qui lisent `mouse_mode_list` : `PreferenceConfiguration.readPreferences` (`LAV_DEV`), `Game.initMouseMode`
  et `SettingsStore.getString` (Réglages montre le mode réellement utilisé). Un nouveau lecteur doit passer
  par là. La stable lit toujours les six. Menu en jeu (Dev) : une seule entrée « Passer en trackpad » /
  « Passer en multitouch » (`Game.touchModeSwitchLabel()` / `switchTouchMode()`, toast, écrite par
  `ProfileAwarePreferences` si « Retenir le mode tactile choisi en jeu ») ; l'ancienne liste et son entrée
  « curseur local » ne sont plus proposées. Écran externe : Trackpad imposé, pas d'entrée ; appli
  « input only » : Trackpad (3) au lieu du 2.
- **Aucun bouton « Aide » en Dev** : `utils/Dialog` n'a plus que OK (l'Aide ouvrait le wiki de dépannage de
  Moonlight), et Réglages n'a plus les liens wiki d'Artemis (aide des commandes spéciales, tableau des
  performances : `NOT_SHOWN`). Restent des liens qui ne sont pas de l'aide : page des releases et la
  page de release ouverte par la mise à jour quand aucun APK ne convient. Même règle côté desktop.
  **Plus de lien Obtainium en Dev** (demande de l'utilisateur : la Dev se met à jour seule) :
  `option_follow_update` est dans `NOT_SHOWN` ; la stable le garde dans son écran de réglages.
- **Hôte non pris en charge** (`v2/HostSupport`, = `ServerSupportedRole` du desktop) : `appversion` du `serverinfo`
  (`ComputerDetails.serverAppVersion`) doit être un quadruplet de nombres (Sunshine finit par `-1`) de génération
  ≥ 3 (la plus ancienne de moonlight-common-c) ; inconnu ou PC hors ligne = accepté. Vérifié avant d'ouvrir un PC,
  de le benchmarker (Mes PC) et de lancer (accueil, bibliothèque) : message « Oups » au lieu d'un lancement qui
  échoue à mi-chemin. Le desktop compare la version de GeForce Experience à une liste en ligne ; les hôtes
  Apollo / Sunshine passent toujours ce test-là.
- **Polices sous Android 8** : l'axe `wght` des polices variables n'existe qu'à partir de l'API 26 ; en dessous
  l'instance par défaut s'affichait partout (Archivo 600, Bricolage 800, Space Grotesk 300) avec un faux gras.
  `V2Theme` charge alors des **instances statiques** de `assets/fonts/` (400, 700, + Archivo 900 et Bricolage 800 pour
  les titres d'Affiche / Ludique ; latin seulement, ~700 Ko), générées par `scripts/v2-static-fonts.py` (fontTools) :
  **le relancer quand une police de `res/font` change**. Plus de faux gras nulle part.
- Captures : `GapsScreenshotTest` (préfixe `gaps-`, en `fr`, refuse l'anglais à l'écran) ; logique : `GapsLogicTest`.

> ⚠️ **Le PcView sauté faisait deux choses au démarrage** que `HomeActivity` refait : la sonde du
> renderer GL (`GlPreferences`, lue par le réglage fin des décodeurs) et le check de mise à jour. PcView
> ne reste ouvert que sur `PcView.EXTRA_LEGACY_UI` (tests, dépannage) : la Dev n'a plus de chemin vers lui.

> ⚠️ **Appairage : pas de poll pendant `pm.pair()`** (`stopUpdates()` puis `waitForPollingStopped()` sur le
> thread, comme PcView), et au succès `pairState = PAIRED` sur l'objet vivant du service en plus du
> certificat : `HostList.isPaired` le lit, et sans ça l'accueil choisirait un autre PC jusqu'au poll suivant.

> ⚠️ **Touches des dialogues v2 (`DialogKeys`) : un relâché ne compte que si l'appui a eu lieu dans le
> dialogue.** A maintenu ouvre les options (`V2Activity`, délai d'appui long) et son relâché arrive dans le
> menu ; dans le stream, le relâché de la touche qui ouvre le menu y arrive aussi. Sans ce suivi, le menu
> validait sa première entrée ou se refermait aussitôt.

> ⚠️ **Une liste qui défile dans un panneau : le panneau ne doit pas être `Ui.noClip()`.** Une vue n'est
> coupée à ses bornes que par le `clipChildren` de son **parent** : sous un parent `noClip`, une
> `ScrollView` dessine ses lignes hors d'elle (par-dessus le pied du menu en jeu). Et dans une grille en
> `LinearLayout` : case vide = `Space` en `MATCH_PARENT` (une `View` simple prend toute la hauteur offerte,
> une hauteur fixe fait ignorer les cartes `MATCH_PARENT` de la ligne, qui tombe à 0).

> 📋 Jaquettes sans image : le nom tient en **3 lignes** et sa taille descend jusqu'à ce que son mot le plus
> long tienne dans la tuile (`CoverTile.fittedSize`) — un mot n'est jamais coupé en deux (« Redem/ption »).

> ⚠️ **Halo et anneau de focus sont dessinés hors des vues** (`V2Frame`) : tout parent doit avoir

> 📋 **Focus à la manette ou au clavier : les cartes et jaquettes grossissent à 1,04** (`V2Frame.setScaleOnFocus(true)`
> dans `CoverTile`, `AppCard`, `SeeAllTile` et les cartes de Mes PC ; pas les boutons ni les lignes de réglages), et la
> couleur revient sur une carte assombrie (`theme.dim(showFocus())`), comme sur le PC et dans le prototype. Le
> survol à la souris (le PC : ombre + liseré) n'a pas d'équivalent ici, le tactile n'a pas de survol.
> `Ui.noClip()` (clipChildren/clipToPadding faux), sinon ils sont coupés. L'assombrissement passe par
> un `saveLayer` avec `ColorMatrix` (gris puis luminosité **multipliée**, puis opacité), le halo par une
> image floutée rendue en logiciel (BlurMaskFilter n'est pas fiable en accéléré).

> ⚠️ **Textes : `setMaxLines(1)`, jamais `setSingleLine(true)`** (Ui.text/display). singleLine active le
> défilement horizontal : un texte centré (« A », « LB ») est posé au milieu d'une ligne immense, donc
> invisible ; et `setSingleLine(false)` efface le `setAllCaps` d'Affiche.

> 📋 Entrée (`V2Input`) : tactile / manette / clavier selon le dernier événement ; anneau de focus et
> indications A/Y/LB-RB seulement à la manette ou au clavier, « … » seulement au tactile. A = clic
> (Android n'en fait rien seul), B/Échap = retour, Y/Menu = options, LB/RB ou Q/E = PC suivant. Au
> premier appui directionnel après le tactile, le focus va sur Reprendre (sinon la première carte), pas sur
> l'en-tête. **A maintenu = options** (comme Y), le relâché ne clique pas. Densité (`Density`) : compacte
> sous 480 dp de petit côté ; cartes empilées sous 600 dp de large ; en portrait les Récents passent en
> grille.
>
> 📋 **Captures** : `HostsScreenshotTest` fait Mes PC (préfixe `hosts-`), le dialogue d'appairage (`pair-`)
> et le menu en jeu (`gamemenu-`, en `fr`). Ses dialogues sont les vrais (`HostsActivity` construite par
> Robolectric, PC posés à la main dans `computers`), dessinés par-dessus la page assombrie. Robolectric
> garde la fenêtre en mode tactile : pour montrer un focus, `setFocusableInTouchMode(true)` dans le test.

### Parité PC / Android (Dev)

Les deux clients Dev ont les mêmes textes, réglages et menus pour tout ce qu'ils partagent ; état et écarts
voulus : `docs/superpowers/specs/2026-11-24-pc-android-parity.md` (**à tenir à jour** à chaque changement
d'un texte ou d'un réglage commun). Repères Android :

- **« Quitter l'appli sur le PC distant à la fin »** (`checkbox_quit_app_after`, page PC, comme le
  `quitAppAfter` du desktop) : `utils/StreamEndPolicy` décide dans `Game.stopConnection()` — toujours avec
  « Quitter la session », sinon seulement si le stream a fini normalement (`unexpectedTermination` posé par
  `connectionTerminated` / `stageFailed`), jamais pendant un benchmark ni pour la reconnexion qui suit un
  changement d'écran du PC (`restartRequested`).
- **Presse-papiers** (`smartClipboardSync` / « Synchroniser le presse-papiers ») : envoyé au début du stream, récupéré
  sur « Déconnecter », à la demande dans le menu en jeu, mêmes messages ; le desktop envoie en plus une copie faite
  pendant le stream (détails : `desktop/CLAUDE.md`, « Session et menu en jeu »).
- **Erreurs de l'hôte en français** : `utils/HostErrorText` (= `ComputerManager::hostErrorDetail()` /
  `quitErrorText()` du desktop) donne le motif court (« erreur 500 », « connexion refusée »…) et la phrase d'un
  quitter refusé, « Impossible de quitter %1 sur le PC (%2). » (le 599 garde la sienne) : presse-papiers, accueil
  (`ServerHelper.doQuit`), fin de stream (`Game`). Jamais `e.getMessage()` à l'écran.
- **Micro hors profils** (voir « Passthrough du micro »). **« Envoyer des touches »** : 15 raccourcis, les
  mêmes que le desktop (sans le raccourci HDR Win+Alt+B).
- « Version du logiciel hôte non prise en charge » : règle `v2/HostSupport` reprise telle quelle par le
  desktop (`NvComputer::isSupportedAppVersion()`), même message avec la version, mêmes points de contrôle
  (ouvrir, lancer, benchmark).
- **Pas de stream vers son propre PC** (desktop seulement, depuis 0.7.0-dev.7) : un PC dont l'adresse ou la MAC
  est celle de cette machine (`NvComputer::isThisPc`) est refusé au lancement, à la reprise et au benchmark,
  avec « Impossible de streamer %1 depuis lui-même… ». Détails : `desktop/CLAUDE.md`.

## 🖼️ Icônes (LavArtemis + LavApollo)

Sources et générateur : **`docs/branding/`** (README : quel fichier va où). Le même écran pour les deux
applis : la **lune** pour le client (clin d'œil à Moonlight), le **soleil** pour l'hôte (Sunshine).
**Logo = écran en contour (5c)**, **« session en cours » = écran allumé (5d)**. Dev : fond « plan
d'atelier » `#1D4E89` quadrillé, dessin blanc, pastille verte `DEV`.

- **Android** : icône adaptative en vecteurs (`drawable/ic_launcher_bg|fg|mono.xml`), PNG `mipmap-*/ic_launcher.png`
  pour API < 26, icône de notification `drawable/app_icon.xml` (silhouette blanche), bannière TV
  `drawable-xhdpi/atv_banner.png`, logo de chaîne TV `drawable/ic_channel.xml` (les deux calques).
  **La Dev remplace `ic_launcher_bg`/`ic_launcher_fg`, les PNG legacy et la bannière dans `app/src/dev/res/`**,
  ajouté aux source sets des flavors seulement pour une version `-dev.` (voir « Stable / Dev »).
- ⚠️ `ic_launcher_bg.xml` a un dégradé (`aapt:attr`, API 24+) : ne le référencer que depuis l'icône adaptative
  (`mipmap-anydpi-v26`) ou du code API 26+ (`TvChannelHelper`), jamais depuis un écran affiché sous Android 7.
- ⚠️ **On modifie le générateur, jamais un rendu** : les vecteurs Android, les PNG, les `.ico` et les SVG
  des trois dépôts sortent des mêmes formes ; une retouche à la main les ferait diverger.
- « Lav » vient d'un nom de famille : **pas d'imagerie de lave**.
- **Fenêtre de l'installeur Windows** (LavArtemis-Qt, depuis 0.7.0-dev.7) : `docs/branding/generate-installer-fonts.py`
  (polices Inter / Space Grotesk du thème) puis `generate-installer.mjs` (`npm run installer`) en écrivent le
  thème, les images (textes des boutons compris) et la liste des payloads pour les deux canaux (direction « A »
  du prototype, page V7 : fenêtre sans barre Windows, fond du logo, logo à gauche, boutons pilule, sans
  licence) ; les textes des libellés sont dans `wix/LavArtemisSetup/theme/thm.wxl` de LavArtemis-Qt, et une DLL
  BAFunctions (`wix/LavArtemisSetup/bafunctions/`) fait ce que le thème ne sait pas faire. Règles :
  `desktop/CLAUDE.md`, « Fenêtre de l'installeur ».

## ⚡ Hot path streaming — valeur ajoutée LavArtemis

C'est ici que réside l'essentiel du travail spécifique au fork (performances / fluidité du stream) :

- **ADPF** — `binding/video/AdpfHelper.java` : `PerformanceHintManager` (API 31+) crée une hint session sur les threads decode/render et reporte, par frame, le **temps CPU réel de ces threads** : `submitDecodeUnit()` moins l'attente d'un buffer d'entrée, plus dequeue → release en sortie. ⚠️ Ne pas y remettre la latence du décodeur matériel (enqueue → dequeue) : les threads dorment pendant ce temps, et une valeur basse peut au contraire faire baisser les fréquences. API 34+ : `setThreads()` au lieu de recréer la session.
- **Pacing & fluidité** — `binding/video/PacingStats.java` : fenêtre roulante de 512 frames, expose p50/p95/p99 des intervalles present-to-present. Deux modes dans `MediaCodecDecoderRenderer.java` : `checkbox_paced_ull` (aligne les frames sur le vsync en ULL) et `checkbox_predictive_pacing` (timestamp BALANCED 2 vsyncs à l'avance).
- **CSV perf logging** — `StreamSettings.java` + renderer écrivent un CSV par seconde (decode time, RTT, loss, percentiles) pour A/B offline. ⚠️ La ligne est formatée sur le thread qui alimente MediaCodec mais **écrite sur un `HandlerThread` dédié** (`PerfCsv`) : aucune E/S fichier sur le hot path. 20 fichiers au plus.
- **Table PTS → instant d'enqueue** (`enqueueNsByPtsUs`) — écrite par le thread dépacketiseur, lue par le thread de rendu : **tout accès sous son moniteur**. Chaque lookup purge les PTS plus anciens (frames abandonnées par le drain « latest only »), sinon elle grossit toute la session.
  La table du pacing VRR (`rtpNsByPtsUs`, PTS → temps RTP) suit **les mêmes deux règles** : moniteur et purge des PTS ≤ celui qui sort, car le décodeur peut abandonner une image qui ne ressortira jamais. Et **tout dequeue de sortie passe par `dequeueOutputBufferTimed()`**, chemin VRR compris (`renderVrrPaced()`). Sinon l'ADPF mesure le travail de sortie depuis le dequeue précédent.
- **Natif `-O3 -flto`** — `jni/moonlight-core/Android.mk` optimise les hot paths (FEC Reed-Solomon, dépacketisation) en release.
- **Boost priorité threads** — `jni/moonlight-core/callbacks.c` : `setpriority(-16)` sur le thread dépacketiseur vidéo, `-19` sur l'audio.
- **AV1 en mode Auto** — `checkbox_auto_av1` (défaut on) n'annonce l'AV1 à l'hôte que si un décodeur HW whitelisté **et** ≤15 Mbps ou 4K.
- **Fix audio + cap configurable** — `binding/audio/AndroidAudioRenderer.java` corrige un bug d'effets audio et rend le cap d'audio pending configurable (`seekbar_max_pending_audio_ms`, 10–100 ms).
- **Sustained performance + thermal** — `Game.java` : `setSustainedPerformanceMode(true)` (API 24+) et toast `thermal_throttling_warning` (API 29+).

Toutes ces options sont déclarées dans **`app/src/main/res/xml/preferences.xml`** (Advanced Settings de la stable ; en Dev, lignes de `v2/settings/SettingsCatalog`, la plupart en mode expert).

## 🎙️ Bouton vocal push-to-talk (Android)

Un bouton micro semi-transparent pendant le stream (bas à droite par défaut) : **tant qu'on le tient,
la touche ² de l'hôte reste enfoncée**, pour un outil de dictée côté PC qui écoute ² en push-to-talk.
`binding/input/VoicePttButton.java` (machine à états, testée par `VoicePttButtonTest`),
`VoicePttButtonPosition.java` (maths de position), branchement dans `Game.initVoicePttButton()`.
Réglage `checkbox_voice_ptt_button` (*UI Settings* en stable, Réglages › Contrôles à l'écran en Dev ; défaut off) + entrées « Afficher / Masquer » et
« Déplacer » du menu en jeu.

> ⚠️ **La touche envoyée est `VK_OEM_3` (0xC0), pas `VK_OEM_7`.** Le client envoie des VK
> *normalisés clavier US* ; l'hôte en fait un scancode (0x29 ici), et c'est **la disposition du PC**
> qui le lit comme ². `VK_OEM_7` est ce que produit un ² tapé sur un clavier Android français : envoyé
> tel quel, il tombe sur une autre touche et la dictée ne réagit pas.

> ⚠️ **La seule panne grave est une touche laissée enfoncée** — la dictée tournerait sans fin.
> `releaseKey()` est idempotent et appelé partout où le doigt peut se perdre : `showGameMenu()`,
> `onWindowFocusChanged(false)`, `onPause()`, `stopConnection()` (**avant** l'arrêt de la connexion,
> comme le micro), masquage et passage en mode déplacement. Tout nouveau chemin de sortie doit faire
> pareil.
>
> ⚠️ **Appui et relâché ne sont pas gardés pareil.** `KeySender.keyDown()` refuse (et renvoie
> `false`) dès que `connected` est faux, et un appui refusé n'envoie **aucun** relâché. `keyUp()`, lui,
> ne teste que `conn != null` : `stopConnection()` remet `connected` à faux *avant* de relâcher la
> touche, et ce relâché-là doit partir.
>
> ⚠️ **PiP : `hideForOverlays()` / `restoreAfterOverlays()`, jamais un instantané dans `Game`.** Le
> changement de configuration du PiP peut arriver plusieurs fois ; un second instantané lisait le bouton
> déjà masqué et il ne revenait plus. L'appel est idempotent côté contrôleur. (Le bouton menu flottant
> hérité, `floatingButtonShown`, a toujours ce défaut.)

> 📋 **Déplacement par un mode du menu, pas par glisser direct** : l'appui long sert déjà à dicter.
> En mode déplacement, un « bouclier » plein écran (`voicePttMoveShield`) capte le toucher de
> validation pour qu'il n'atteigne pas le stream. La position est stockée en **fraction de l'écran**
> dans les préférences **de base** (propre à l'appareil, pas au profil) ; la visibilité passe par
> `ProfileAwarePreferences.putBoolean()`, qui écrit dans le profil actif s'il porte la clé — sinon
> `OverlaySharedPreferences.edit()` l'écrirait dans les prefs de base, masquées par le profil.

## 🔍 Zoom au pincement en mode trackpad (Android)

Comme Chrome Remote Desktop : en mode trackpad (souris 2 naturel / 3 gaming ; la Dev ne garde que le 3), **pincer zoome**
(1×–10×) et **la vue suit le curseur** — elle ne bouge que quand le curseur quitte la moitié centrale
de l'écran, juste assez pour l'y ramener. Réglage `checkbox_trackpad_pinch_zoom` (*Input Settings* en stable, Réglages › Entrées en Dev ;
défaut off). `binding/input/touch/TrackpadZoomController.java` orchestre ; `TrackedCursor`,
`PinchArbiter` et `utils/ZoomFollow` portent les calculs (tests JVM) ; `PanZoomHandler` implémente
`utils/ZoomView`.

> ⚠️ **L'appli tient le curseur et l'envoie en position absolue** (`sendMousePosition`) dès que
> l'option est active. Suivre un curseur dont on n'envoie que des déplacements relatifs est impossible
> à faire juste : l'accélération de Windows (« Améliorer la précision du pointeur », active par défaut)
> déforme les mouvements et toute estimation dérive vers un curseur fantôme. Prix assumé : plus
> d'accélération Windows, et les jeux à caméra souris réagissent mal (option désactivée par défaut).
> Les contextes trackpad passent par `MouseMover` ; l'option « souris absolue » du gaming est
> **supplantée** quand les deux sont actives (un seul propriétaire de la position).

> ⚠️ **Un pincement décidé possède tout le geste** : `PinchArbiter` tranche au début (l'écart entre les
> doigts change avant que le centre bouge), puis `cancelTouch()` sur les contextes — sinon le
> défilement ou le clic droit à deux doigts partiraient pendant le zoom. **Tant que ce n'est pas
> décidé, les mouvements à deux doigts sont retenus** (pas transmis aux contextes) : le contexte gaming
> confirme un défilement dès le premier mouvement si le doigt 0 bougeait déjà, et défilait donc avant
> que le pincement soit reconnu. Un défilement décidé ensuite rattrape son retard au mouvement suivant,
> les contextes calculant depuis leur dernière position. Et **`lastSpan` repart de zéro** à chaque
> doigt posé ou levé pendant un pincement, sinon reposer un doigt plus près fait s'effondrer le zoom.

> 📋 **Le suivi ne se déclenche que sur un mouvement du curseur**, jamais sur un pincement : on peut
> zoomer loin du curseur, la vue le rattrape au prochain mouvement. Il vaut pour **tout** zoom > 1×
> (pincement, mode zoom du menu, zoom retenu). En mode tactile, retour à 1× à l'entrée (zoom retenu
> compris, `Game` le refait après la restauration différée) ; un toucher absolu (`AbsoluteTouchContext`,
> modes 1/5, ou mode 0 sans support tactile côté hôte) resynchronise la position tenue. **Le mode 0 en
> toucher natif ne la resynchronise pas** : on ne sait pas si l'injection tactile de Windows déplace le
> curseur, et recaler sur une supposition ferait viser faux. Option off = aucun `MouseMover`, aucune interception : comportement d'avant.

> 📋 **Vitesse 1:1 à l'écran** : le doigt et le curseur parcourent la même distance à l'écran, à tout
> zoom. Le `MouseMover` reçoit le mouvement **en pixels écran, sans accélération** (pas de `cbrt` en
> naturel, pas d'échelle de référence 1280 en gaming, dont la sensibilité reste un multiplicateur,
> 100 % = 1:1) et le contrôleur le convertit une fois : `d × largeurStream / (largeurVue × zoom)`. La
> taille de la vue est relue **à chaque mouvement** : une rotation la change en plein stream.
>
> ⚠️ **Les trackpads lisent le doigt en coordonnées écran** (`Game.contextCoordinates()` /
> `utils/TouchSpace`) tant que le contrôleur tient le curseur. Le repère du conteneur zoomé
> (`(x − position) / zoom`, qu'il faut aux touchers absolus) **bouge quand la vue glisse** : un doigt
> immobile « bougeait », le curseur allait plus loin, la vue glissait encore — le curseur accélérait
> d'un coup au bord (21.6.2). Et la 21.6.1, qui lisait ce repère sans le savoir, redivisait par le
> zoom : curseur zoom fois trop lent. Une seule division, au contrôleur, sur des pixels écran.

> ⚠️ **On zoome le cadre du stream, pas l'image à l'intérieur.** `StreamContainer` est taillé au
> format du stream (c'est lui qui fait les bandes noires) ; zoomer la `SurfaceView` *dans* ce cadre la
> faisait découper par lui : l'image grossissait mais restait dans la bande, contrairement à Chrome
> Remote Desktop. `PanZoomHandler.forStreamContainer()` zoome le **conteneur** dans l'écran entier
> (bornes = son parent plein écran). C'était déjà l'hypothèse du reste du code :
> `getNormalizedCoordinates(streamContainer, …)` et l'aperçu PiP lisent la transformation **du
> conteneur** — les touchers absolus suivent donc enfin le zoom. Un zoom/pan « retenu » par une version
> antérieure était exprimé dans le repère du conteneur : il est recadré une fois au premier stream.

## 🔄 Rotation qui suit le téléphone (Android)

Réglage `checkbox_follow_device_rotation` (défaut off, à côté d'« Orientation automatique ») : l'image
tourne avec le téléphone pendant tout le stream, **l'écran du PC reste tel quel** (comme Chrome Remote
Desktop). Décisions dans `utils/RotationPolicy.java` (tests JVM), appliquées par `Game`.

> ⚠️ **La résolution demandée n'est jamais inversée quand on suit la rotation**, même avec « Inverser la
> résolution » cochée et un lancement en portrait : l'hôte ne peut pas changer de résolution en plein
> stream, donc l'inverser au lancement figerait un PC en portrait pour toute la session.

> ⚠️ **Portrait = toujours ajusté** (`effectiveScaleMode()` → FIT), même avec « Étirer » ou « Remplir » :
> une image paysage étirée ou rognée en portrait est inutilisable. `Game.applyStreamScaleMode()` est
> rappelé à chaque `onConfigurationChanged` ; en paysage le mode choisi revient (STRETCH =
> `setDesiredAspectRatio(0)`, le conteneur remplit l'écran comme le chemin d'origine).

> 📋 `SCREEN_ORIENTATION_FULL_USER` : le verrouillage de rotation d'Android est respecté. « Pivoter
> l'écran » disparaît du menu en jeu (c'est le téléphone qui décide). Pas sur écran externe (orientation
> forcée en paysage plus haut dans `onCreate`). Réglage off = chemin d'orientation et d'échelle d'origine.

## 📐 Résolution calée sur l'écran du PC (LavApollo + les deux clients)

Quand le client **ne crée pas l'écran** — il **rejoint une session en cours** (`/resume`) ou il **lance
sans écran virtuel** — il streame au **format de l'écran que le PC affiche**, à la plus grande taille qui
tient dans son réglage **et** dans cet écran (jamais agrandi), à `min(fréquence réglée, fréquence de
l'écran)`. Sinon le PC ajoutait des bandes noires *dans la vidéo* (bureau 16:9 dans un stream 20:9) et le
client se calait sur des images inexistantes. LavApollo annonce le mode dans `serverinfo`
(`CurrentDisplayWidth/Height/RefreshRate`) ; sans ces champs, rien ne change. Valeurs de référence
communes aux deux clients (`HostDisplayFitTest`, `tests/hostdisplay`).

- **Android** : `utils/HostDisplayFit` (règle) et `utils/HostDisplayWatch` (changement), appliqués par
  `NvConnection.applyHostDisplayMode()` juste avant `launchApp(resume|launch)` →
  `StreamConfiguration.applyHostDisplay()` (en mémoire) → `NvConnectionListener.streamModeDecided()` →
  `Game` met à jour la taille de la vue, le zoom et le curseur.
- **En cours de session** : `Game` relit `serverinfo` toutes les 2,5 s (`HostDisplayWatch`) et, sur un
  changement confirmé deux fois, **se reconnecte** : `finish()` puis relance du même intent depuis
  `onDestroy()` — la nouvelle instance prend le chemin de reprise et applique le nouveau mode. Le
  protocole fixe la taille à la connexion : il n'existe pas de changement à chaud.

> ⚠️ **Comparer la cible au stream en cours, jamais l'écran du PC au stream.** Un écran 2400×1080
> streamé en 1920×864 est l'état ajusté, pas un écart : comparer à l'écran ferait reconnecter en boucle.
> Et deux lectures identiques avant d'agir : Windows passe par des modes intermédiaires.

> ⚠️ **Android : la taille envoyée au PC est `context.negotiatedWidth/Height`, pas la `StreamConfiguration`.**
> L'URL de `launch`/`resume` et la négociation RTSP lisent les valeurs négociées, figées dans `startApp()`
> **avant** la décision de reprise. `NvConnection.applyHostDisplayMode()` doit les mettre à jour aussi : en
> 21.7.0 il ne changeait que la configuration, et un Pixel 7 rejoignant un écran 2560×1600 demandait encore
> 2400×1080 (la vue se calait sur 1728×1080, l'hôte envoyait autre chose). Le desktop n'a qu'un
> `m_StreamConfig`, ajusté avant tout usage : pas ce piège.

> 📋 Rejoindre applique la règle **même si l'écran virtuel est coché** : `/resume` ne recrée pas les
> écrans, le client streame forcément celui de la session. Pas de suivi pendant un run de benchmark.

## 🔧 Code natif / JNI

- Build : **ndk-build** (`app/src/main/jni/Android.mk` aggregator ; `Application.mk` : `APP_PLATFORM android-21`, support des pages 16 KB).
- `libmoonlight-core.so` est compilé depuis le **sous-module `moonlight-common-c`** ([`Lavagnou/moonlight-common-c`](https://github.com/Lavagnou/moonlight-common-c) branche `lavartemis`, **partagé avec le client desktop**), à `app/src/main/jni/moonlight-core/moonlight-common-c/`. La FEC vient de **nanors** (sous-module imbriqué), pas de reedsolomon.
- Pont JNI : **`simplejni.c`** (côté C, fonctions `Java_com_limelight_nvstream_jni_MoonBridge_*`) ↔ **`MoonBridge.java`** (côté Java, déclare aussi les constantes de stream : formats H264/H265/AV1, erreurs, ports).
- `callbacks.c` : callbacks C → Java.
- Bibliothèques statiques prébuildées **committées** : `libopus`, `openssl` (sous `app/src/main/jni/moonlight-core/{libopus,openssl}/<abi>/`).
- `evdev_reader/` = **exécutable natif** (pas une lib) construit **uniquement en flavor `root`** pour lire `/dev/input/event*`.
- `LuaScripts/` = dissecteurs Wireshark + `gridctl.lua` (outils de debug, **non embarqués** dans l'APK).

## 🖥️ Client desktop (`desktop/`)

LavArtemis existe aussi en **client desktop Qt**, dans le sous-module `desktop/` → [`Lavagnou/LavArtemis-Qt`](https://github.com/Lavagnou/LavArtemis-Qt) (C++/Qt 6.11, **qmake**, Windows x64/ARM64 + Linux ; les releases construites en local ne portent que Windows x64).

**Lignage** : Moonlight-Qt → [wjbeckett/artemis](https://github.com/wjbeckett/artemis) (portage desktop des features Artemis) → LavArtemis-Qt. Remotes dans le sous-module : `upstream-qt` = moonlight-stream/moonlight-qt, `upstream-artemis` = wjbeckett/artemis.

Déjà présent côté desktop (hérité du portage wjbeckett) : clipboard sync, server commands, pairing OTP (`otpauth`), virtual display Apollo, refresh rate fractionnaire, resolution scaling, lancement par UUID d'app, affichage des permissions, quick menu in-stream.

### Features Artemis portées côté LavArtemis-Qt

| Feature | Où | Notes |
|---|---|---|
| **Profils de réglages** | `settings/profilemanager.{h,cpp}`, page « Profil » de `gui/v2/SettingsView2.qml` | `profiles.json` dans `AppConfigLocation`. Un profil est un **snapshot complet**, pas un patch épars : `save()` écrit toutes les clés, donc un profil créé capture l'état courant puis vit sa vie. Ce qui tient à l'appareil **bypasse** les profils : `language`, `defaultver`, `k_AppWideKeys` (recherche des PC, détection des connexions bloquées, fenêtre au démarrage, trace VRR) et les réglages globaux d'`ArtemisSettings` (micro, tampon audio, presse-papiers, CSV des perfs…). Même principe côté Android (`ProfilesManager.APP_WIDE_KEYS`, les mêmes réglages depuis le 2026-11-26). |
| **Send keys + macros clavier** | `backend/keymacromanager.{h,cpp}`, sous-menu « Envoyer des touches » de `gui/QuickMenu.qml` | 15 presets intégrés (ceux de la Dev Android) + macros utilisateur dans `keyboard-macros.json` (`AppConfigLocation`). **Même format JSON et mêmes noms `VK_` que l'Android** → un fichier marche sur les deux. La table des 174 noms est extraite de `utils/KeyMapper.java`, pas retapée. |
| **Liens `art://` + fichiers `.art`** | `cli/artlink.{h,cpp}`, enregistrement OS dans `wix/LavArtemis/Product.wxs` (HKCU) et `deploy/linux/com.lavartemis.LavArtemis.{desktop,xml}` | `ArtLink::rewriteArguments()` **traduit le lien en ligne de commande existante** (`stream <host> <app>` / `pair <host> --pin --passphrase`) au lieu d'ajouter un chemin de lancement parallèle → hérite du host seeking, du wake-on-LAN et de l'UI de segue. Formats identiques à l'Android. |

> ⚠️ **Le bug Android des profils n'est pas reproduit.** Côté Android, `OverlaySharedPreferences` lit à travers la map du profil mais `edit()` renvoie l'éditeur de base (`ProfilesManager.java:251`) : les écritures s'échappent du profil et atterrissent dans les prefs globales. Côté Qt, `StreamingPreferences::reload()` **et** `save()` passent tous les deux par `ProfileManager::value()`/`setValue()` — lecture et écriture ne peuvent pas diverger par construction.

Deux effets de bord utiles du portage `art://` : le lancement CLI accepte désormais un **UUID ou un id d'app** en plus du nom (`startstream.cpp::getAppIndex`), et `pair` accepte `--passphrase` (routé vers `pairHostWithOTP`, pairing OTP Apollo).

> ⚠️ **Pas d'instance unique.** Cliquer un lien `art://` alors que LavArtemis tourne déjà lance un **second processus**. Ce n'est pas une régression : c'est déjà le comportement de `lavartemis stream <host> <app>`, les commandes CLI amont étant conçues comme des processus indépendants. Une IPC `QLocalServer` serait nécessaire pour changer ça.

**Pan & zoom (D4)** est porté : `streaming/panzoom.{h,cpp}`, piloté par les combos Ctrl+Alt+Shift (`+`/`-`/`0`, flèches). Tous les renderers passaient déjà par un seul helper — la géométrie tient donc dans une fonction, `StreamUtils::scaleSourceToDestinationSurfaceWithPanZoom()`, et pas dans chaque backend. Le mapping des coordonnées souris passe par le **même** helper, donc le pointeur suit le zoom sans code supplémentaire.

> ⚠️ **Un seul appelant reste sur l'ancien helper** : `Session::getWindowDimensions()` (`session.cpp:1538`) réutilise l'ajustement d'aspect pour dimensionner la fenêtre. Y appliquer le zoom **redimensionnerait la fenêtre** au lieu d'agrandir la vidéo. C'est pour ça que c'est une seconde fonction et pas un flag dans la première.

**Export de fichiers `.art`** : `AppModel::exportArtFile()`, entrée « Créer un raccourci .art » du menu d'une appli (`gui/v2/AppMenu.qml`, dialogue dans `HomeView.qml`). Même format `[clé] valeur` que `ShortcutHelper.java:243` → un fichier écrit ici s'ouvre sur Android et inversement.

Pas encore porté : **SBS 3D MiDaS** (hors périmètre v1).

### 🖥️ Multi-écran émulé (desktop uniquement, v20.7.0)

Quand le PC client a plusieurs moniteurs, l'hôte **LavApollo** en émule un écran virtuel chacun, aux
bonnes résolutions et à la bonne disposition, et le client réaffiche chaque écran hôte sur le
moniteur correspondant. Réglage « Un écran virtuel par moniteur » (Réglages › Affichage), désactivé par
défaut et grisé si l'écran virtuel de base est désactivé. **Exclusivement desktop** — l'Android est hors périmètre.

Le protocole ne transporte qu'**un seul flux vidéo** (`LiStartConnection` est un singleton global,
`cmd_announce` ne lit que `x-nv-video[0]`), donc le flux porte **une toile** unique = la bounding box
de la disposition, et l'hôte y compose ses écrans à leur position réelle.

> ⚠️ **L'invariant à ne jamais casser** : la toile doit rester géométriquement identique à la région
> de bureau qu'elle couvre. C'est ce qui fait que souris absolue, touch et stylet marchent **sans
> aucune modification du protocole** — `make_port()` (`LavApollo/src/video.cpp`) dérive le plan de
> coordonnées client de l'offset et de la taille du display capturé. Compacter la toile pour
> économiser des pixels casserait ça et imposerait un nouveau message de contrôle.

Une seule addition au protocole : `&displayLayout=<x>,<y>,<w>,<h>,<primary>;…` sur `launch`/`resume`,
plus `MultiDisplayCapable` dans `serverinfo`. Rétrocompatible : un hôte qui ignore le paramètre crée
un unique grand écran de la taille de la toile, et le client avertit au lieu de laisser deviner.

Une pastille « Multi-écran » est aussi posée dans l'en-tête de l'accueil (`HomeView.qml`), l'écran d'où
l'on lance réellement un jeu — c'est le réglage qu'on change selon l'endroit où l'on est assis, pas selon
ses goûts.

> ⚠️ **Deux pièges déjà payés, à ne pas réintroduire.** Une préférence QML écrite depuis
> `onCheckedChanged` s'efface toute seule (le signal suit le binding, pas le clic) — utiliser
> `onToggled`. Et côté hôte, Windows **ré-origine tout le bureau** quand l'écran principal change :
> les écrans émulés gardent leurs positions relatives, mais toutes les coordonnées absolues
> glissent, sans que rien ne le signale ; la capture composite le détecte elle-même et se
> reconstruit.

Détails côté client dans `desktop/CLAUDE.md`, côté hôte dans `LavApollo/CLAUDE.md`.

### 🎮 Manettes tierces — la divergence Android / desktop

Le point de friction le plus profond entre les deux clients, et il n'est pas dans le code de LavArtemis :

| | Android | Desktop (SDL) |
|---|---|---|
| Périphérique inconnu | **Deviné.** `ControllerHandler` attribue les rôles d'axes à partir des `MotionRange` déclarés : stick gauche X/Y, stick droit RX/RY sinon **Z/RZ**, croix HAT_X/HAT_Y | **Ignoré.** Sans entrée dans `gamecontrollerdb.txt`, le périphérique reste un joystick nu et tous les appels filtrent sur `SDL_IsGameController()` |

C'est pourquoi une RadioMaster TX15 (HID générique, 4 axes X/Y/Z/RZ + 8 boutons) marchait sur Android et était **muette** sur desktop.

Trois pièces côté Qt :

1. **Repli générique** — `MappingManager::applyFallbackMappings()` synthétise un mapping pour tout joystick ignoré, en reprenant le raisonnement Android (première paire d'axes = stick gauche ; la suivante = stick droit à 4 axes, gâchettes à 6+). Le **critère d'éligibilité est celui de l'amont** (`4-8 axes, ≥8 boutons, ≤1 hat`, cf. `getUnmappedGamepads()`) : l'appareil dont on avertit et celui qu'on répare sont le même par construction.
   - Appelé **à la fin d'`applyMappings()`**, donc une entrée réelle gagne toujours, et les trois appelants en héritent sans duplication.
   - Les suppositions **ne sont pas persistées** (recalculées à chaque lancement) et **s'annoncent** via un launch warning nommant l'appareil — sinon un succès silencieux serait indiscernable d'une disposition silencieusement fausse.
   - Réglage `genericGamepadFallback` (défaut **on**) : « Deviner la disposition des manettes inconnues », Réglages › Manettes.
2. **Mapper** — `gui/sdlgamepadmapper.{h,cpp}` + `gui/GamepadMapper.qml` + `gui/ControllerDiagram.qml` + `gui/GamepadControl.qml`. Le bouton existait en amont derrière `visible: false` et un TODO, pointant sur un stub de 5 lignes. **On clique le contrôle sur un schéma de manette, puis on l'actionne** : une seule cible à la fois, et une file (`selectElements()`) qui sert aux trois usages — un bouton, une paire d'axes de stick, ou les 21 éléments à la suite.
3. **Dialogue** des manettes non mappées : ouvre le mapper au lieu d'un lien wiki.

> ⚠️ **L'invariant qui a coûté le plus cher : toute entrée doit revenir au repos avant de compter à nouveau.** Les boutons avaient ce verrou, les axes et les hats **non** — et comme les quatre étapes d'axe se suivaient dans l'ancien assistant linéaire, avec un timer à 20 ms, une seule poussée du stick liait `leftx`, `lefty`, `rightx` **et** `righty` au même `a0`. La croix recevait `h0.1` sur ses quatre directions. `beginListening()` désarme désormais tout ce qui est actif quand l'écoute commence, sur les trois familles.

> ⚠️ **Les autres invariants du mapper**, dont dépend la justesse des captures :
> - **Écart au repos, pas valeur absolue.** Un interrupteur ou une gâchette peut reposer en butée. Le repos est relevé à l'ouverture, puis ré-échantillonné à chaque sélection **pour les axes calmes seulement** — un axe qu'on tient ne peut ni devenir le nouveau repos, ni déclencher.
> - **Le plus grand écart gagne**, pas le premier index : sinon un seul axe qui dérive rafle tous les éléments.
> - **Le sens de l'écart pilote l'inversion** (`~` de SDL). Les prompts demandent une direction précise pour que l'inversion soit *détectée*, pas codée en dur.
> - **Le repos décide de la syntaxe d'un axe**, pas la nature de l'élément — `MappingManager::axisSourceToken()`, partagé avec le repli deviné. Repos en butée ⇒ axe plein `a2` ; repos au centre ⇒ demi-axe `+a2`. À l'envers, une gâchette perd la moitié de sa course ou lit **50 % en permanence au repos**.
> - **Hat : cardinales uniquement.** SDL teste `(valeur & masque) == masque`, donc un `h0.3` capturé en diagonale ne se déclencherait qu'avec deux directions tenues.
>
> ⚠️ La navigation UI à la manette est **suspendue** pendant le mapping (`SdlGamepadKeyNavigation.disable()`) : elle lit les mêmes boutons. Et `Escape` doit être **consommé** tant qu'une capture attend, sinon le `StackView` dépile la page sous la capture.

> 📋 **Le retour visuel est un outil de diagnostic.** Le schéma réévalue les liaisons posées contre l'état brut à chaque tick : les sticks bougent, les gâchettes se remplissent, les boutons s'allument. Un axe inversé ou une gâchette à moitié enfoncée se voit **avant** la sauvegarde, au lieu de se découvrir en jeu.

> ⚠️ **La sauvegarde n'est pas destructive** : le schéma se préremplit depuis `SDL_GameControllerMappingForGUID()` et conserve verbatim les champs non modélisés (paddles, touchpad, sorties demi-axe, `crc:`). Sans ça, corriger un stick effacerait le reste d'une entrée de la base.

> 📋 **Choix assumé : pas de notification au démarrage pour un appareil deviné.** Il *fonctionne* ; un modal à chaque lancement serait du harcèlement. L'information est donnée là où elle est actionnable — au lancement d'un stream, et dans la liste du mapper (« Layout guessed — may be wrong »), badge lu par **GUID** et non par nom (deux manettes du même modèle portent le même nom).

### 🔄 Mise à jour in-app (desktop, Windows)

`desktop/app/backend/autoupdatechecker.{h,cpp}` interroge les releases GitHub de **LavArtemis** et,
sous Windows, télécharge et lance l'installeur lui-même — les autres plateformes ouvrent la page de
release. Le canal suit l'appli : stable → `/releases/latest`, Dev → première release `-dev.` de
`/releases` (le réglage « prereleases / CI » a disparu). « Ignorer cette version » au check du démarrage ;
le check manuel la propose toujours. Sous Windows, l'asset est **`-win-installer.exe` quelle que soit
l'architecture** (bundle x64 seul en release locale, `LAV_X64_ONLY=1` ; x64 + arm64 choisis par
`NativeMachine` quand la CI le construit), alors que les zips par arch sont des builds portables sans
installeur dedans.

> ⚠️ **Trois invariants payés en 20.8.1, détaillés dans `desktop/CLAUDE.md`.** Un `Dialog` Qt Quick se
> ferme dès qu'on presse un bouton standard, donc le téléchargement a son propre dialogue au lieu de
> dessiner sa progression sur une boîte déjà refermée. Tout check se termine par exactement un signal
> (`onUpdateAvailable`, `noUpdateAvailable`, `updateCheckFailed`), sinon « Checking… » tourne
> indéfiniment sur les deux issues les plus fréquentes. Et le `QNetworkAccessManager` est reconstruit
> à chaque check, sans quoi celui du démarrage consomme l'unique instance et tous les suivants
> repartent sans rien envoyer.

> 📋 **Une correction de l'updater ne se livre pas par l'updater** — la version cassée est celle qui
> télécharge. Le dire dans les notes de release et prévoir une installation manuelle.

> ⚠️ **L'installeur téléchargé est nettoyé au démarrage suivant**, pas après l'installation : `installAndRestart()`
> passe le fichier à l'installeur puis quitte, donc le processus qui pourrait le supprimer est celui
> qu'on remplace. Le tri se fait **par version, pas par date** — un installeur plus récent que la
> version qui tourne peut être un téléchargement en cours dans une autre instance, et il n'y a pas de
> verrou d'instance unique côté desktop.

### 🔄 Mise à jour in-app (Android)

`utils/UpdateChecker.java` + `UpdateDownloader.java` + `UpdateDialogHelper.java`. Le canal suit l'appli
(stable : `/releases/latest` ; Dev : première release `-dev.` de `/releases`). Obtainium n'a jamais servi :
la Dev n'en a plus le lien (`NOT_SHOWN`) ; seul l'ancien écran de réglages de la stable le montre encore.

> ⚠️ **L'APK se choisit par ABI** (`UpdateChecker.pickAsset()` sur `Build.SUPPORTED_ABIS`) : un Fire TV ou un Chromecast with Google TV ne peut pas installer l'APK arm64. Sans asset compatible, le dialogue ouvre la page de release.
>
> ⚠️ **L'APK téléchargé est vérifié avant l'installeur système** (`UpdateDownloader.verifyApk()`) : même package, `versionCode` supérieur, signé par la clé de l'app installée (rotation acceptée via l'historique). Un rejet supprime le fichier et ouvre la page de release. La règle elle-même est `checkUpdateCandidate()`, testée dans `UpdateVerificationTest`. ⚠️ Elle ne vaut que ce que vaut la clé : une app installée signée avec la clé de debug partagée accepte tout APK signé avec cette même clé, comme l'installeur système.

- Check automatique au plus toutes les 12 h (1 h après un échec), bouton « Ignorer cette version » (le check manuel des réglages la propose toujours).
- Une version de majeur ≥ 20 (ancienne numérotation, 21.x) compte comme plus ancienne que toute version de la nouvelle (0.7, 1.0) : voir « Numérotation propre ».
- Les APK de versions ≤ installée sont supprimés au démarrage ; un téléchargement qui finit après la destruction de l'activité donne une notification « mise à jour prête ».
- `res/xml/provider_file_paths.xml` n'expose que `updates/`, `export_settings/` et la racine du cache : tout nouveau `getUriForFile()` sur un autre dossier doit l'y ajouter.

### ✅ Invariant : un seul `moonlight-common-c` — **tenu**

Les deux clients pointent le même commit de [`Lavagnou/moonlight-common-c`](https://github.com/Lavagnou/moonlight-common-c), branche `lavartemis` @ `fc72797` = `moonlight-stream/master` (juillet 2026) + `ebb15ef` (`LiSendExecServerCmd`) + `0da9626` (`LiSendEmptyPayload`) + les 7 commits PyroWave de Nonary (négociation, frames partielles, délai de réassemblage) + l'ID de bitstream v1 (`63c75f1`) + `LiSendMicrophoneData` (`fc72797`, passthrough du micro).

> ⚠️ Les hashes `84af637` / `c999436` qu'on trouve ailleurs pour exec cmd et empty payload sont ceux de la branche `master` du fork, **pas** des ancêtres de `lavartemis` : chercher par message de commit, pas par hash.

`LiSendEmptyPayload` est du code mort côté desktop ; il est là pour que l'Android (`MoonBridge.sendEmptyPayload()` dans `Game.java`) puisse partager la branche.

> ⚠️ **La bascule de l'Android a coûté deux ajustements**, à connaître avant de rebumper :
> - **FEC : reedsolomon → nanors.** Sous-module différent, avec ses propres sources `deps/obl/`. `Android.mk` liste `nanors/deps/obl/oblas_common.c`, `oblas_lite.c`, `nanors/rs.c` et les trois include paths correspondants (mêmes que le `.pro` desktop).
> - **Timestamps des decode units : ms → µs.** `receiveTimeMs` → `receiveTimeUs`. Le renommage est la partie visible, **le changement d'unité est la partie dangereuse** : le Java travaille toujours en ms (`MediaCodecDecoderRenderer` accumule `enqueue - receive` dans `totalTimeMs` et multiplie `enqueue` par 1000 pour le timestamp MediaCodec). Passer les µs telles quelles gonflerait les deux ×1000, silencieusement. La conversion se fait dans `callbacks.c` (`usToMs()`), le contrat Java est inchangé. Récupérer la précision supplémentaire est souhaitable mais mérite un commit isolé, bisectable.

## 🌊 PyroWave (codec intra-only GPU)

[PyroWave](https://github.com/Themaister/pyrowave) (Themaister) compresse chaque image seule, en
ondelettes, sur GPU (Vulkan) : décodage sous la milliseconde, une perte ne coûte qu'un flou local sur
une image, mais il faut des **centaines de Mbit/s** — réseau filaire. Jamais choisi en Auto : codec
« PyroWave » explicite, repli sur les autres codecs si l'hôte ou l'appareil ne sait pas.
**Expérimental** : l'utilisateur ne s'en sert pas pour l'instant ; il reste parce qu'il est construit
(codec proposé en mode expert seulement, curseur de débit jusqu'à 3 Gbit/s avec lui, 800 Mbit/s sinon).

Le protocole est celui de [Vibepollo](https://github.com/Nonary/Vibepollo) / du fork moonlight-qt de
Nonary, **inchangé** : `docs/pyrowave-protocol.md` dans LavApollo et LavArtemis-Qt (identiques).
Hôte : LavApollo. Clients : desktop (Windows/Linux x64, porté depuis Nonary) et Android (ci-dessous).

> ⚠️ **Invariant : un seul PyroWave.** Le bitstream n'a pas de champ de version. LavApollo
> (`third-party/pyrowave`), LavArtemis-Qt (`pyrowave/pyrowave`) et l'Android
> (`app/src/main/jni/pyrowave`) épinglent **le même commit** de
> [`Lavagnou/pyrowave`](https://github.com/Lavagnou/pyrowave) (branche `lavartemis` = upstream v1 +
> Granite élagué + colle de build dans `lavartemis/`). On les avance ensemble. L'hôte annonce
> `502a3b52` (v1) ; les clients acceptent aussi `186f0393` (Vibepollo), au format réseau identique.

### Côté Android (arm64-v8a uniquement)

MediaCodec ne sait pas décoder PyroWave : un renderer natif Vulkan le fait
(`app/src/main/jni/pyrowave-renderer/`). Écrit pour LavArtemis — les forks Android existants n'étaient
pas assez éprouvés (et Moonlight V+ parle un autre protocole) ; seul le parseur de framing
(`pyrowaveframing.cpp`) est repris de Nonary, le même que le desktop.

- **Chemin d'une image** : `callbacks.c` (`BridgeDrSubmitDecodeUnit`) la passe directement au
  renderer **sans passer par Java** — copier des Mo par image dans un `byte[]` coûterait plus que le
  décodage. Un thread natif parse le framing, pousse les records, décode dans 3 plans R8, puis une
  passe de rendu convertit Y'CbCr→RGB dans la swapchain posée sur la Surface du stream.
- **Synchronisation GPU** : deux timeline semaphores. Le décodage attend la fin du rendu précédent
  (avant d'écraser les plans), le rendu attend le décodage. Le CPU n'attend jamais un décodage.
- **Un seul VkDevice partagé** : le renderer crée instance/device (avec le swapchain que PyroWave
  n'active pas) et les passe à `pyrowave_create_device()`. Vulkan est chargé par `dlopen` →
  minSdk 21 conservé.
- **Fragment path** de PyroWave (prévu pour les GPU mobiles) quand le device le préfère **ou** quand
  R8 n'accepte pas l'écriture compute.
- **SDR seulement**, 4:2:0 ou 4:4:4 (`checkbox_pyrowave_444`). Incompatible avec les modes 3D
  (`renderMode != 0`) : ils passent par un `SurfaceTexture` GLES.
- L'option n'apparaît que si `MoonBridge.pyroWaveIsSupported()` (sonde : Vulkan 1.3 + création d'un
  décodeur PyroWave) — toujours `false` sur l'APK 32 bits, qui n'embarque pas le renderer.

> ⚠️ **Les points d'entrée JNI PyroWave sont dans `simplejni.c`**, pas dans le renderer :
> `libmoonlight-core.so` est linkée avec `-Wl,--exclude-libs,ALL`, qui masque les symboles des libs
> statiques — un `Java_..._pyroWaveStart` défini dans `pyrowave-renderer` serait introuvable par la JVM.
> Sans `HAVE_PYROWAVE` (32 bits), `simplejni.c` fournit des stubs.

> 📋 `APP_STL := c++_static` (`Application.mk`) : le renderer et Granite sont en C++. Les shaders sont
> précompilés en SPIR-V (`pyrowave-renderer/shaders_spv.h`, régénéré par `shaders/build.sh` avec le
> `glslc` du NDK) — aucun compilateur de shaders au build.

## 🎞️ Pacing VRR (LavApollo + les deux clients)

Un client qui présente les images **à l'instant que vise leur timestamp RTP** (heure de capture de
l'hôte + un délai appris) reproduit la cadence du jeu, gigue réseau absorbée. Il le signale par
`&clientVrrRequested=1` sur `/launch`/`/resume` -- seul ajout au protocole ; un hôte qui l'ignore
streame comme avant. LavApollo crée alors ses écrans virtuels à **1000 Hz** (horodatage de capture au
ms près) et bride le jeu au rythme du stream.

- **Desktop** : port fidèle du pacer VRR de Nonary (Windows ; Linux retombe sur le pacing fixe) --
  détails dans `desktop/CLAUDE.md`.
- **Android** : écrit pour LavArtemis, `binding/video/VrrPacing.java` (Java pur, testé en JVM :
  `VrrPacingTest`). Décalage d'horloge = minimum glissant (2 s) de arrivée − temps RTP ; délai =
  percentile du retard de décodage (p90 / p97 / p99,5 selon le profil), plafonné à 1 / 2 / 4 images
  (les allocations de Nonary). `MediaCodecDecoderRenderer.renderVrrPaced()` rend chaque image avec
  `releaseOutputBuffer(index, cible)` et en saute une en retard de plus d'une image, **jamais deux de
  suite**. Réglages `checkbox_vrr_pacing` (défaut off, prend le pas sur ULL / prédictif / équilibré) et
  `list_vrr_pacing_mode`.
  - ⚠️ **Le timestamp RTP traverse le JNI** : `bridgeDrSubmitDecodeUnit` a un `int rtpTimestamp` de plus
    (signature `([BIIIICJJI)I` dans `callbacks.c`), non signé côté C, 32 bits qui bouclent : `VrrPacing`
    le déroule en 64 bits.
  - ⚠️ L'arrivée mesurée est l'instant où la trame entière est soumise (`System.nanoTime()` dans
    `submitDecodeUnit`), pas le premier paquet ; le minimum glissant absorbe ce décalage constant.
  - Android n'a pas de G-Sync : SurfaceFlinger affiche à la vsync qui suit la cible ; le gain vient
    d'un écran à fréquence adaptative (LTPO / ARR) qui suit la cadence du contenu.
  - **Pas avec PyroWave** : son renderer Vulkan natif présente seul (FIFO) ; `VK_GOOGLE_display_timing`
    n'est pas branché.

## 📊 Benchmark des réglages (LavApollo + les deux clients)

Choisir codec, débit et pacing « au ressenti » est remplacé par une mesure : menu d'un PC dans **Mes PC** →
« Benchmark des réglages » (Dev, les deux clients : le benchmark vit avec le PC, pas dans les réglages) ;
clic droit (desktop) ou appui long (Android) sur un hôte dans la stable. Le client streame plusieurs fois de suite la **même scène
animée**, servie par l'app cachée « Benchmark » de LavApollo (`tools/benchmark_scene.cpp`, fonction du seul
numéro d'image), une combinaison par run, et note chaque run sur 100 en **latence** et en **fluidité** face
aux réglages actuels, mesurés en premier et en dernier. Modes Rapide / Complet / Adaptatif, bouton
« Appliquer », historique. **Spécification commune : `LavApollo/docs/benchmark.md`** (planification,
mesures, formules, JSON) : les deux clients l'implémentent à l'identique et leurs tests vérifient les mêmes
valeurs de référence.

- **Protocole** : `serverinfo` donne `BenchmarkAppId` / `BenchmarkAppUuid` ; l'app n'apparaît pas dans
  `/applist` sauf pendant qu'elle tourne. Aucun changement dans `moonlight-common-c`.
- **Desktop** : `backend/benchmark/` + `gui/BenchmarkView.qml`, détails dans `desktop/CLAUDE.md`.
- **Android** : `com.limelight.benchmark` (`BenchmarkPlanner`, `BenchmarkScoring`, `BenchmarkReport` : ports
  ligne à ligne du C++, testés par `BenchmarkTest` / `BenchmarkControllerTest`), `BenchmarkActivity` (habillée par `V2Page`
  en Dev), ouverte par le menu d'une carte de `HostsActivity` (Dev) ou `BENCHMARK_ID` de `PcView` (stable).
  Les notes des runs sont écrites en anglais et traduites à l'affichage (`BenchmarkNotes`).

> ⚠️ **Android : chaque run est une activité `Game` normale**, lancée avec l'extra `BenchmarkRun` (JSON) que
> `Game` applique à `prefConfig` **en mémoire**, juste après `readPreferences()`. Le run se mesure lui-même
> (`BenchmarkGameRun`, calendrier sur `timerHandler`) et écrit son résultat dans
> `files/benchmarks/runs/<id>.json` avant `finish()`. **Pas de `setResult()`** : `Game` est
> `singleTask`/`noHistory`, ce qui rend `startActivityForResult()` peu fiable, et ses stats sont lues dans
> `onStop`, après `finish()`. Pas de fichier pour un run = l'utilisateur l'a quitté = le benchmark s'arrête.

> ⚠️ **Les stats Android du benchmark ont leurs propres compteurs** dans `MediaCodecDecoderRenderer`
> (`bench*`, sous `benchLock`), publiés à chaque fenêtre d'1 s (`publishBenchmarkStats()`) : le décodage en µs
> (les `VideoStats` sont en ms tronquées), et l'attente en file = du dequeue (noté par
> `dequeueOutputBufferTimed()`) au **moment visé** par `releaseOutputBuffer` (`benchmarkPresented()`, à chaque
> site de présentation). Tout nouveau chemin de présentation doit appeler `benchmarkPresented()`.

- **La référence enregistre ce qu'elle a streamé** (`requested` = `effective` pour `baseline` et `baseline-repeat` :
  `BenchmarkController.complete()`, `measure()` du desktop) : en Auto il n'y a pas de codec demandé, et le mode
  adaptatif montait le débit puis confirmait sur la supposition (HEVC). `BenchmarkControllerTest`.
- **Notes en anglais dans le JSON, traduites à l'affichage**, mêmes phrases des deux côtés (`BenchmarkNotes.java`,
  `Benchmark::Notes` du desktop ; liste dans `LavApollo/docs/benchmark.md`, « Notes ») : chacun traduit aussi les
  formes de l'autre, un rapport s'ouvre des deux côtés. Les identifiants qu'une note contient (codec `hevc`, pacing
  `vrr-balanced`, ceux des deux clients) s'affichent par leur nom (`BenchmarkNotes.codecLabel` / `pacingLabel`, les
  mêmes que les libellés de `BenchmarkActivity`), comme `Benchmark::Notes::localized`.
- Android n'a pas d'étage « rendu » mesurable (SurfaceFlinger) et ne mesure pas PyroWave (décodé en natif,
  hors des stats Java) : ses scores ne se comparent pas un pour un à ceux du desktop. Chaque rapport compare
  ses propres runs.
- « Appliquer » écrit dans le **profil actif** s'il y en a un, sinon dans les préférences de base : écrire les
  préférences de base sous un profil serait masqué par lui (`OverlaySharedPreferences`).

## 🎤 Passthrough du micro (LavApollo + les deux clients)

Le micro du client est joué par l'hôte dans un **micro virtuel** (Steam Streaming Microphone par
défaut), pour le chat vocal et les jeux à chat de proximité. **Désactivé par défaut côté client.**
Protocole complet : `LavApollo/docs/mic-passthrough.md`.

- **Transport : le flux de contrôle chiffré, pas un port UDP.** `LiSendMicrophoneData()`
  (`Lavagnou/moonlight-common-c`) envoie une trame Opus (48 kHz mono, 20 ms) dans le message `0x3080`,
  canal ENet 9, *unsequenced*. Refus d'envoyer si le flux de contrôle n'est pas chiffré ou si l'hôte
  n'est pas de la famille Sunshine : la voix ne passe jamais en clair.
- **Négociation** : `serverinfo` donne `MicPassthroughState` (`ready` / `disabled` / `no_permission` /
  `no_device`, absent sur un autre hôte). Le client n'envoie `&clientMicRequested=1` que sur `ready`,
  sinon il affiche la raison au lancement, et le stream continue sans micro.
- **32 kbit/s, FEC Opus et DTX** : les trames de silence (2 octets) partent quand même, pour que les
  numéros de séquence restent contigus. Un trou côté hôte signifie toujours une perte.
- ⚠️ **`LiSendMicrophoneData()` : un seul thread appelant, et plus aucun appel après le début de
  `LiStopConnection()`.** Les deux clients arrêtent la capture **avant** l'arrêt de la connexion.
- **Couper le micro** : Ctrl+Alt+Shift+**U** sur les deux clients (M est déjà le mode souris du desktop).
  Sur desktop, menu rapide + « Microphone muted » affiché tant qu'il est coupé ; sur Android, entrée
  du menu en jeu (visible seulement si le micro est actif).

| | Android | Desktop |
|---|---|---|
| Capture | `binding/audio/MicrophoneStreamer.java` : `AudioRecord` en `VOICE_COMMUNICATION` (annulation d'écho et réduction de bruit de l'appareil) | `streaming/audio/micstreamer.{h,cpp}` : `SDL_OpenAudioDevice(name, 1, …)`, encodage et envoi dans le callback SDL |
| Encodage | natif, `simplejni.c` (`micStart` / `micSendPcm` / `micStop`) : les points d'entrée JNI vivent là à cause de `--exclude-libs` | `opus_encoder_create` direct |
| Réglages | `checkbox_mic_passthrough`, `list_mic_device` (clé `type:nom`, pas l'id : il change à chaque reconnexion). **Globaux en Dev**, comme le desktop : `ProfilesManager.APP_WIDE_KEYS` (l'overlay et `ProfileAwarePreferences` passent par les préférences de base ; `load()` les sort des profils enregistrés en gardant la valeur du profil actif, `AppWideKeysTest`) | `ArtemisSettings::micPassthroughEnabled` / `micDeviceName`, **globaux** (le micro dépend de la machine, pas du profil) |
| Permission | `RECORD_AUDIO`, demandée quand on coche la case ; refusée → la case se décoche | — |
| Écho | traité par l'appareil | **aucun** : SDL n'annule pas l'écho, d'où le conseil du casque |

> ⚠️ **Desktop : le sous-système audio SDL est partagé** entre le rendu (`SdlAudioRenderer`) et le
> micro. Les deux `SDL_assert(!SDL_WasInit(SDL_INIT_AUDIO))` de `sdlaud.cpp` supposaient un seul
> utilisateur ; ils ont été retirés. `SDL_InitSubSystem` / `SDL_QuitSubSystem` comptent les références.

### Build desktop

```sh
git submodule update --init --recursive desktop
cd desktop && powershell ./setup-deps.ps1        # Windows : libs prébuildées
scripts\build-arch.bat Release x64               # puis arm64
scripts\generate-bundle.bat Release              # installeur WiX combiné
```
Requiert **Qt 6.11 + MSVC (VS2026)** et **WiX 7**. Sur Linux : `qmake6 lavartemis.pro && make release`.

### Mapping hot path Android ↔ Qt

| LavArtemis Android | Équivalent desktop | État |
|---|---|---|
| `binding/video/PacingStats.java` | `app/streaming/video/pacingstats.{h,cpp}` — même fenêtre 512, même filtre d'outliers, mêmes percentiles | ✅ porté **et amélioré** |
| *(pas d'équivalent Android)* | **Scanout réel sur D3D11** : `IFFmpegRenderer::getLastPresentTimeUs()` (virtuelle, défaut 0) surchargée dans `d3d11va.cpp` via `GetFrameStatistics()`/`SyncQPCTime` — le vblank où la frame est *réellement* sortie, pas l'instant du hand-off. L'Android mesure le hand-off faute de mieux (SurfaceFlinger), ce qui mélange le jitter de soumission du client aux percentiles censés décrire l'écran. ⚠️ **Époques différentes** : `LiGetMicroseconds()` compte depuis le démarrage du cœur, `SyncQPCTime` est du QPC brut → basculer entre les deux sources produit un intervalle aberrant, que `PacingStats` rejette déjà dans les deux sens (test d'ordre / plafond 1 s). Coût : 1 échantillon sur 512. | ✅ desktop only |
| CSV perf (11 colonnes) | `FFmpegVideoDecoder::writePerfCsvRow()` dans `streaming/video/ffmpeg.cpp` → `stream-perf-<epoch>.csv` dans `AppDataLocation`. **Mêmes colonnes** que l'Android, les runs se comparent directement. | ✅ porté |
| `setpriority(-16)` thread dépacketiseur (`callbacks.c`) | `SDL_SetThreadPriority(HIGH)` one-shot dans `Session::drSubmitDecodeUnit` | ✅ porté |
| `setpriority(-19)` thread audio | déjà fait en amont dans `Session::arDecodeAndPlaySample` | ✅ amont |
| cap audio pending (`seekbar_max_pending_audio_ms`) | `ArtemisSettings::maxPendingAudioMs` (10–100 ms, défaut 30), lu par `sdlaud.cpp` **à la construction** → s'applique au stream suivant | ✅ porté |
| `-O3 -flto` (`Android.mk`) | `moonlight-common-c/moonlight-common-c.pro` : `-GL` (MSVC, `*-msvc` pour couvrir arm64) / `-O3 -flto` (GCC/Clang) | ✅ porté |
| Benchmark (`com.limelight.benchmark`, `BenchmarkActivity`) | `backend/benchmark/`, `gui/BenchmarkView.qml` | ✅ même spécification (`LavApollo/docs/benchmark.md`), mêmes valeurs de référence testées des deux côtés ; un run Android est une activité `Game`, un run desktop une `Session` détachée |
| `checkbox_vrr_pacing` (`VrrPacing.java`) | Pacer VRR de Nonary (`pacer/vrrpacingworker.*`, `pacer/vrr/*`), réglage « VRR » à côté de la V-Sync | ✅ même principe ; le desktop est le port fidèle (présentation adaptative D3D11 avec tearing), l'Android une version compacte |
| `checkbox_paced_ull` | ❌ **ne pas porter.** C'est un rattrapage Android : en ULL le renderer présente sans alignement vsync. Le desktop a déjà un vrai `IVsyncSource` (`DxVsyncSource` / Wayland presentation-time) derrière l'option **Frame pacing** de moonlight-qt — activer cette option *est* le mode pacé, en mieux (vsync réel, pas extrapolé). |
| `checkbox_predictive_pacing` | ❌ **ne pas porter.** L'équivalent supposé (`SetMaximumFrameLatency(1)`) est **délibérément évité** en amont : cf. le commentaire de `d3d11va.cpp` (~l. 551) — avec `SyncInterval 0`, le fixer à 1 fait bloquer `Present()` sur DWM et **augmente** la latence. Le `+2 vsync` Android est un détail SurfaceFlinger sans équivalent. |
| AV1 auto (≤15 Mbps ou 4K) | ❌ **ne pas porter tel quel.** Le mode Auto amont sonde déjà les décodeurs et *déprioritise* AV1 (sans le retirer) selon la dispo HEVC HW — cf. `session.cpp` ~l. 909-944. Appliquer en plus le seuil Android *retirerait* AV1 à >15 Mbps en <4K et ferait retomber en H.264 les GPU qui ne décodent qu'AV1 en HW : régression. |
| ADPF | ❌ pas d'équivalent Windows. La partie « inhibition veille » est déjà couverte par `SDL_DisableScreenSaver()` (`session.cpp:2137`) → `SetThreadExecutionState`. |
| Sustained performance mode, avertissement thermique | ❌ pas d'équivalent Windows — hors périmètre |

Les réglages desktop LavArtemis vivent dans `app/settings/artemissettings.{h,cpp}` (singleton QSettings, fichier `artemis-settings.ini` dans `AppConfigLocation`), exposés en QML via `qmlRegisterSingletonType` dans `main.cpp` et édités dans les pages de `gui/v2/SettingsView2.qml`.

> ⚠️ Les setters d'`ArtemisSettings` **ne persistent pas** tout seuls : `SettingsView2.qml` appelle `ArtemisSettings.save()` (avec `StreamingPreferences.save()`) dans `saveNow()`, déclenché par `persist()` après chaque modification. Tout nouveau point d'édition doit faire pareil.

## 🧪 Banc de test (desktop)

De vrais clients contre de vrais hôtes, sans toucher au système : des **LavApollo en mode « hôte de test »**
(faux PC isolés, sur loopback) et le **build de test de LavArtemis-Qt** (`LavArtemisTest.exe`, piloté par un canal de
commande), le tout lancé par un runner pytest. Windows seulement, desktop seulement (l'Android n'a pas de banc).
Mode d'emploi complet, scénarios et codes de sortie : `tools/testbench/README.md`.

- **Où** : `tools/testbench/` ici (runner, scénarios `scenarios/`, rapports `testbench-reports/`, hors git) ;
  côté hôte, le mode `test_host` de LavApollo (faux PC : config, applis, bibliothèque Playnite, jaquettes
  générées, journaux d'entrées) ; côté client, `CONFIG+=lav_test` de LavArtemis-Qt (`scripts\build-test.bat`) et son
  canal de commande (état, arbre des vues, clics, captures, entrées injectées). Le banc reconstruit ce qui est périmé.
- **Lancer** : `python tools\testbench\testbench.py run` (suite complète), `-k <mot>` pour un scénario,
  `--rerun-failed-once` (rejoue une fois chaque échec ; un scénario qui ne passe qu'au rejeu est un **FLAKE**).
  Rapport : `tools/testbench/testbench-reports/<date>_<heure>/report.md` (+ captures et journaux).
  **Codes de sortie** : `0` tout passe du premier coup · `1` un scénario échoue ou a été ignoré, ou pytest a échoué
  sans résultat explicable · `3` FLAKY · `4` écran éteint (rien n'a tourné).
- **Garanties** : ports 127.0.0.1 **40000-44999** seulement (le vrai LavApollo, base 45989, n'est jamais contacté, arrêté
  ni configuré ; le client de test refuse toute autre adresse), organisation Qt **« LavArtemis Test »** (jamais les
  réglages de LavArtemis / Dev), fenêtres **sans prise de focus**, **entrées injectées seulement** (ni vrai pointeur,
  ni vrai clavier, ni son, ni micro), ni installation ni gestionnaire `art://` ; seuls les processus lancés par le banc
  sont arrêtés.
- **Écran du faux PC fixe** : l'hôte de test annonce dans `serverinfo` le mode de `display` (`pc.json`, défaut
  1920×1080 à 60 Hz, clé `test_host_display`), jamais le moniteur de ce PC. Un scénario déduit la taille négociée de
  ce mode et du réglage du client par la règle « résolution calée » (`helpers.host_display_fit`), sans la coder en dur.
- ⚠️ **Écran allumé obligatoire.** Écran éteint, Qt Quick ne met plus les fenêtres en page et la moitié des scénarios
  échouent sans que l'appli y soit pour rien. `run` le détecte (`display.py` : `GUID_CONSOLE_DISPLAY_STATE`, lu sans
  jamais allumer l'écran) et sort en **code 4** avant de lancer quoi que ce soit.
- **Porte de release** : `scripts/local-release.ps1` lance `testbench.py run --suite desktop --rerun-failed-once` **avant
  tout build** et s'arrête sur tout code non nul (1, 3 FLAKY, 4 écran éteint, autre = erreur du runner), en pointant le
  dossier du rapport. `-SkipTests` la saute avec un avertissement visible. **Un FLAKE bloque aussi la release** : il se
  regarde, il ne se livre pas. Vérifier la porte sans GUI : `LAV_TESTBENCH_SELFTEST=pass|fail|flaky` (scénario interne,
  honoré par `run` seulement ; ne contrôle pas l'écran).
- **Règle** : toute nouvelle fonctionnalité desktop (ou tout bug corrigé) vient avec un **scénario** ; un bug produit
  connu non corrigé passe son scénario en `xfail` sur sa signature exacte, avec la cause, jamais en skip (un scénario
  ignoré fait échouer la porte).

## 🤖 CI/CD & release

- **Les releases se construisent sur le PC du mainteneur** (depuis le 2026-11-23, quota Actions trop juste) :
  `scripts/local-release.ps1 -NotesFile <notes.md>` (`-SkipAndroid` / `-SkipWindows` réutilisent un build,
  `-NoPublish` s'arrête avant le tag, `-SkipTests` saute le banc de test) lance d'abord **le banc de test desktop**
  (étape « Tests », avant tout build : `testbench.py run --suite desktop --rerun-failed-once` ; tout code non nul
  arrête la release : 1 échec, 3 FLAKY, 4 écran éteint — voir « Banc de test »), puis construit les APK signés (`key/release.keystore`, mot de passe dans
  `key/keystore.properties`, hors git — ne jamais l'ouvrir ni l'afficher), le client Windows **x64
  seulement** (pas de kit Qt arm64 ici : `LAV_X64_ONLY=1` donne un installeur sans ARM64 ; `CI_VERSION` et un
  `version.txt` réécrit le temps du build donnent la version), le zip portable, puis tag `v<lavVersionName>`,
  push et `gh release create` (prerelease pour une `-dev.`). Pas d'AppImage Linux ni d'ARM64 en local.
  ⚠️ Le clone `LavArtemis-Qt` (à côté de ce dépôt) doit être sur le commit épinglé par `desktop/` (le script
  le vérifie) et LavArtemis ne doit pas avoir de modification non commitée.
- **Le pointeur `desktop/` s'avance à la main** (commit du gitlink, « Point desktop/ at … »), sur le commit
  de LavArtemis-Qt qui vient d'être construit et essayé en local.
- Artefacts : `LavArtemis-<tag>-android-arm64-v8a.apk`, `LavArtemis-<tag>-android-armeabi-v7a.apk`,
  `LavArtemis-<tag>-win-x64.zip`, `LavArtemis-<tag>-win-installer.exe` (+ `-win-arm64.zip` et
  `-linux-x86_64.AppImage` quand `release.yml` construit la release).
- **Tous les workflows GitHub sont manuels ou liés aux PR** — aucun ne part sur un push ou un tag :
  - **`.github/workflows/release.yml`** : `workflow_dispatch` seulement ; reste le secours pour une release
    avec Linux et ARM64 (Android + Windows x64/arm64 + Linux dans une seule release). Lancé sur un tag
    `v<lavVersionName>` déjà poussé (« Use workflow from » → le tag), il publie cette release (et refuse un tag
    ≠ version) ; lancé sur une branche, une prerelease `v<version>-ci.<run>` — ⚠️ sur `dev`, ce tag contient
    `-dev.` et l'updater de la Dev le prendrait. Job `version` : lit
    `lavVersionName`, écrit `desktop/app/version.txt` au build (sa valeur committée est périmée et ignorée),
    contrôle la santé du pointeur `desktop/` (signale sans corriger, `continue-on-error`). `build-android`
    (Ubuntu, JDK 17, NDK `27.0.12077973`, n'initialise **que** le sous-module natif), `build-windows`
    (`windows-2025`, Qt 6.11, x64 **et** arm64 pour l'installeur combiné), `build-linux` (`ubuntu-22.04`,
    AppImage x86_64, **de loin le plus long** : SDL3, sdl2-compat, SDL_ttf, libva, libplacebo, dav1d et
    FFmpeg compilés depuis les sources sous `desktop/deps/`, empaquetage par `desktop/scripts/build-appimage.sh`
    + linuxdeploy), `publish` (`softprops/action-gh-release@v2`, **bloquant sur les 3 builds** ; une version
    `-dev.` saute Linux et part en prerelease).
  - ⚠️ **`compare/BASE...HEAD` décrit HEAD *par rapport à* BASE** (contrôle du pointeur) : seul `ahead` veut
    dire que le pointeur est en retard ; `behind` = le dernier commit vert est un ancêtre de l'épinglé. Un
    simple `!=` a fait crier une alerte dans le vide pendant 17 jours (v20.7.x → v20.8.2).
  - **`unit-tests.yml`** : PR et lancement manuel, `continue-on-error`. ⚠️ **5 tests JVM échouent déjà**
    (`LayoutInflationTest`, `SimpleStartupTest`, `StartupTest`, `ProfilesNavigationTest` ×2) — setup
    Robolectric préexistant. Les lancer en local avant chaque push.
  - **`update-desktop-pointer.yml`** : manuel ; cherche le dernier build **vert déclenché par un push** sur
    `main` de LavArtemis-Qt et écrit dans `main` d'ici. Le compile-check n'étant plus lancé par les push, il
    ne trouve plus de nouveau commit : **obsolète** tant que le pointeur s'avance à la main (et il ignore la
    branche `dev`).
  - **`claude.yml`** / **`claude-code-review.yml`** : Claude sur les issues / PR. Ne buildent rien.
  - **`LavArtemis-Qt/.github/workflows/build.yml`** : compile-check (Windows x64/arm64 + Linux), **manuel
    seulement** ; le client est construit en local avant chaque push. ⚠️ Il construit Linux **sans**
    `APP_IMAGE`, `build-linux` d'ici **avec** : les branches `#ifdef` diffèrent (cas de
    `findAssetDownloadUrl()`) — un compile-check rouge n'est pas cosmétique même quand la release passe.
    `build-appimage.yml` y est **orphelin** (`workflow_call` que personne n'appelle) : c'est la référence dont
    `build-linux` est dérivé, toute correction va dans les deux.
- **Flatpak non câblé** : demande un manifeste (inexistant) et une soumission Flathub.

### Signature
- Aucun keystore committé (`key/` + `*.keystore`/`*.jks`/`*.b64` sont gitignored).
- **Mise en place : voir `SIGNING.md`** (`scripts/generate-release-keystore.ps1`).
- `app/build.gradle` signe une release, dans cet ordre : `signingConfigs.ci` si `CI_KEYSTORE_PATH` est posé
  (workflow : secret `RELEASE_KEYSTORE_BASE64` décodé) ; sinon `signingConfigs.local` si
  `key/keystore.properties` et `key/release.keystore` existent (**le cas normal, release locale**) ; sinon,
  sur GitHub Actions seulement, la **debug key** (installable, mais les mises à jour sont refusées
  « package en conflit ») ; ailleurs, APK non signé.
- Windows : Authenticode **optionnel** via `WINDOWS_CERT_BASE64` / `WINDOWS_CERT_PASSWORD` (workflow
  seulement). Absents → binaires publiés non signés (le build ne casse pas).

## 🛡️ ProGuard / R8 & lint

- `minifyEnabled true` sur **debug ET release**, mais **`-dontobfuscate`** (`app/proguard-rules.pro`) : R8 shrink sans renommage → builds release lisibles.
- Keep rules : `binding.input.evdev.*`, `KeyMapper`/`KeyConfigHelper` (Gson), TFLite GPU delegate + OpenCV (pour le SBS 3D), `ProfilesManager$ProfilesData`/`SettingsProfile`, `nvstream.jni.*`, BouncyCastle, MPAndroidChart.
- `app/lint.xml` minimal (`MissingThemeAttr` en error, ignore `bcpkix`). `MissingTranslation` **désactivé** (traductions partielles). Pas de baseline.
- Historique : le commit `2d996e52` a retiré des strings orphelins `values-ru` (perf charts) qui faisaient échouer `lintVitalRelease` (`ExtraTranslation`).

## 📝 Conventions & contribution

- **Java uniquement**, UTF-8, package racine `com.limelight`. Suivre le style existant.
- Voir **`.github/CONTRIBUTING.md`** : commits atomiques, branches `feature/...`.
- **Politique code IA** : permis mais **revue ligne par ligne** ; le repo stipule explicitement *« Don't trust AI generated tests. Test each modification manually. »*
- `AGENTS.md` est gitignored ; ce fichier **`CLAUDE.md` ne l'est pas** et peut être commité.

## 🌍 i18n

- ~30 locales via qualificateurs resources Android standards (traductions communautaires héritées de Weblate).
- `android:localeConfig="@xml/locales_config"` (Android 13+) → `bundle.language.enableSplit = false`.

## ⚠️ Pièges / Gotchas

1. **Sous-module natif obligatoire** — sans `git submodule update --init --recursive`, le build natif échoue.
2. **Suffixe `.lav`** — ne pas le retirer (voir ci-dessus).
3. **Shadower le JNI en test** — sinon `UnsatisfiedLinkError`.
4. **Dossier `app/src/root/java/com.limelight/`** — nom littéral **à points** (un seul dossier, pas `com/limelight/`), spécifique à la flavor root.
5. **`applicationId` ≠ namespace** — le namespace Java reste `com.limelight` ; seul l'appId varie.
6. **`minifyEnabled` actif même en debug** (avec `-dontobfuscate`) — R8 tourne toujours.
7. **ABI : arm64-v8a *et* armeabi-v7a** — le 32 bits n'est pas du legacy ici, c'est ce que parlent les Fire TV / Chromecast with Google TV (voir ci-dessus).

## 🧹 Renommages incomplets / Dette technique (Artemis → LavArtemis)

Le rebrand vers LavArtemis est partiel. Restes connus à finaliser :
- ✅ ~~`README.md`~~ réécrit pour LavArtemis (deux clients, stable / Dev, lignage, sans Obtainium). Le « Disclaimer » de ClassicOldSong, un témoignage personnel à la première personne, a été retiré — il n'avait pas à figurer sous le nom d'un autre mainteneur ; son travail est crédité dans « Lineage and credits ».
- Classe **`ArtemisApplication.java`** non renommée (référencée par `AndroidManifest.xml` et par `StartupTest`/`SimpleStartupTest`).
- Identifiant clipboard **`"ArtemisStreaming"`** dans `Game.java`.
- ✅ ~~Email/branding du partage des logs de perf~~ : plus de destinataire imposé (c'était la boîte de collecte de l'amont), sujet « LavArtemis - Performance Log ».
- Typo **« Nior »** (devrait être « Noir ») dans `summary_software_update` des traductions `values-ru` et `values-zh-rTW` (l'anglais et le français sont corrigés).
- ✅ ~~Icônes launcher~~ : icônes LavArtemis propres depuis 21.8.0-dev.5 (voir « Icônes »). Les couleurs du
  thème Android classique (stable) restent celles d'Artemis (`#1A1A1A` / `#000000`) ; la Dev a les thèmes v2.
- `fastlane/metadata/android/en-US/title.txt` = « Moonlight Game Streaming ».

## 🔑 Fichiers clés

| Fichier | Rôle |
|---|---|
| `app/build.gradle` | Flavors, build types, suffixes appId, signing, ABI splits |
| `app/proguard-rules.pro` | R8 (minify sans obfuscation) |
| `app/src/main/jni/moonlight-core/simplejni.c` | Pont JNI (côté C) |
| `app/src/main/java/com/limelight/nvstream/jni/MoonBridge.java` | Pont JNI (côté Java) + constantes de stream |
| `app/src/main/java/com/limelight/Game.java` | Activité de stream |
| `.../binding/video/MediaCodecDecoderRenderer.java` | Décodeur vidéo HW (hot path) |
| `.../binding/video/AdpfHelper.java` | ADPF (perf hints) — LavArtemis |
| `.../binding/video/PacingStats.java` | Métriques de fluidité — LavArtemis |
| `.../binding/audio/AndroidAudioRenderer.java` | Rendu audio — LavArtemis (fix + cap) |
| `.../preferences/StreamSettings.java` | Réglages + CSV perf log |
| `app/src/main/res/xml/preferences.xml` | Toutes les options (dont LavArtemis) |
| `scripts/local-release.ps1` | **La** release : APK signés + Windows x64, tag, `gh release create` (sur le PC du mainteneur) |
| `.github/workflows/release.yml` | Release Android + Windows x64/arm64 + Linux, **manuelle seulement** (secours) |
| `app/src/main/java/com/limelight/v2/` | Interface v2 de la Dev (accueil, bibliothèque, Mes PC, Réglages v2, briques `ui/`, table `settings/SettingsCatalog`) |
| `docs/superpowers/specs/2026-11-24-pc-android-parity.md` | Parité PC / Android : commun, écarts voulus, reste à faire (à tenir à jour) |
| `android_test_setup.md` | Guide de tests JVM / Robolectric |
| `app/src/main/jni/pyrowave-renderer/` | Renderer PyroWave Android (Vulkan, arm64) |
| `app/src/main/jni/pyrowave/` | Sous-module `Lavagnou/pyrowave` (⚠️ même commit que LavApollo et le desktop) |
| `.gitmodules` | Sous-modules `moonlight-common-c`, `pyrowave` **et `desktop/`** |
| `desktop/` | Client Qt (sous-module `LavArtemis-Qt`) |
| `desktop/app/app.pro` | Build Qt : TARGET, icônes, métadonnées Windows |
| `desktop/app/settings/artemissettings.{h,cpp}` | Réglages Artemis/LavArtemis desktop (⚠️ `save()` explicite) |
| `desktop/app/settings/profilemanager.{h,cpp}` | Profils de réglages + routage lecture/écriture des prefs |
| `desktop/app/backend/keymacromanager.{h,cpp}` | Send keys + macros clavier (table `VK_`) |
| `desktop/app/cli/artlink.{h,cpp}` | Parsing `art://` + `.art` → ligne de commande |
| `desktop/app/streaming/panzoom.{h,cpp}` | Zoom & pan client (D4) — ⚠️ ne pas l'appliquer au dimensionnement de fenêtre |
| `desktop/app/settings/mappingmanager.{h,cpp}` | Mappings manettes + **repli générique** pour les périphériques ignorés par SDL |
| `desktop/app/gui/sdlgamepadmapper.{h,cpp}` | Capture d'un mapping (API joystick brute) — ⚠️ écart au repos, pas valeur absolue ; désarmement jusqu'au retour au repos |
| `desktop/app/gui/GamepadMapper.qml` | Page du mapper — ⚠️ suspend `SdlGamepadKeyNavigation`, consomme `Escape` pendant une capture |
| `desktop/app/gui/ControllerDiagram.qml` | Schéma cliquable de la manette (repère 1000×620, `res/gamepad_body.svg` en fond) |
| `desktop/app/gui/GamepadControl.qml` | Un contrôle du schéma : cliquable, coloré par état, animé par l'entrée live |
| `desktop/app/gui/v2/` | Interface v2 desktop (`HomeView`, `HostsView`, `SettingsView2`, `Theme`…) ; l'ancienne interface est supprimée |
| `desktop/app/backend/autoupdatechecker.{h,cpp}` | Vérification, téléchargement et lancement de l'installeur (⚠️ un signal terminal par check ; NAM recréé à chaque fois) |
| `desktop/app/gui/QuickMenu.qml` | Menu en jeu (commandes du PC hôte, envoyer des touches, presse-papiers, micro, mode tactile) |
| `desktop/app/streaming/video/pacingstats.{h,cpp}` | Métriques de fluidité — port de `PacingStats.java` |
| `desktop/app/streaming/video/ffmpeg.cpp` | Décodeur + `writePerfCsvRow()` (CSV perf) |
| `desktop/moonlight-common-c/moonlight-common-c.pro` | LTO du cœur natif (release) |
| `desktop/scripts/build-arch.bat` | Build Windows par architecture |
| `desktop/wix/LavArtemis{,Setup}/` | Installeur WiX (⚠️ `UpgradeCode` propre à LavArtemis ; table Upgrade explicite et versions < 20.0.0, voir « Numérotation propre ») |
| `tools/testbench/` | Banc de test desktop : runner (`testbench.py`), faux PC (`hosts.py`, `fixtures/`), client de test (`client.py`), scénarios (`scenarios/`), `README.md` (voir « Banc de test ») |
| `LavApollo/src/test_host.{h,cpp}` (dépôt LavApollo) | Mode « hôte de test » : faux PC isolé, journaux `testhost/*.jsonl`, `skip()` des chemins qui touchent le système (voir « Hôte de test » de `LavApollo/CLAUDE.md`) |
| `desktop/app/testctl/` | Build de test (`CONFIG+=lav_test`) : canal de commande du client de test (`testcontrol`, `testprotocol`, `uiprobe`) |
| `desktop/tests/testguard/` | Tests du garde-fou d'adresse du client de test (127.0.0.1, ports 40000-44999) et du protocole du canal |

---

## ⚡ Quick reference (English)

**What:** LavArtemis is a GameStream client for **LavApollo** (and Apollo/Sunshine) hosts, shipped for **Android and desktop**, as two side-by-side apps: the stable LavArtemis (`main`) and **LavArtemis Dev** (`dev`, a version containing `-dev.`), where all new work goes (redesigned French interface, Playnite library) — no backports to stable:
- **Android** (this repo) — fork of Artemis (`ClassicOldSong/moonlight-android`), itself a fork of Moonlight. Java-only + C (NDK, ndk-build). Fork-specific work sits in the streaming hot path: ADPF, frame pacing, native `-O3`/LTO + thread-priority boosts, AV1-auto, an audio-renderer fix, sustained-performance/thermal handling.
- **Desktop** (`desktop/` submodule → `Lavagnou/LavArtemis-Qt`) — C++/Qt 6.11, qmake, Windows x64/ARM64 (+ Linux). Fork of `wjbeckett/artemis` (the Artemis→Qt port) rebased onto current moonlight-qt.

One version builds and releases both. See the "Client desktop" section above.

**First-time setup**
```sh
git submodule update --init --recursive app/src/main/jni/moonlight-core/moonlight-common-c   # Android only
git submodule update --init --recursive desktop                                              # desktop client (large)
# add ndk.dir=<NDK 27.0.12077973 path> to local.properties
```

**Commands**
| Action | Command |
|---|---|
| Debug build | `./gradlew assembleNonRoot_gameDebug` |
| Release build | `./gradlew assembleNonRoot_gameRelease` |
| Unit tests (nonRoot) | `./gradlew :app:testNonRoot_gameDebugUnitTest` |
| All unit tests | `./gradlew test` |

**applicationId** — `namespace` is `com.limelight` (unchanged); the shipping release appId is **`com.limelight.lav`**. Suffix `.lav`, do not remove. Debug = `.lavdebug`. Root flavor = `com.limelight.root` (dev-only, maxSdk 25).

**Tests** — Robolectric JVM only (no instrumentation). You **must shadow** JNI classes (`MoonBridge`, `GameManager`) or you get `UnsatisfiedLinkError`. See `android_test_setup.md`.

**Release** — built **locally** to spare the Actions quota: `scripts/local-release.ps1 -NotesFile <notes.md>` builds the signed APKs (one per ABI: arm64-v8a and armeabi-v7a; key in `key/release.keystore`, password in `key/keystore.properties` — never commit or print them) and the Windows **x64-only** installer + portable zip (`LAV_X64_ONLY=1`), then tags `v<lavVersionName>`, pushes and runs `gh release create` (a `-dev.` version is a prerelease). No Linux AppImage or ARM64 locally. `.github/workflows/release.yml` (Android + Windows x64/ARM64 + Linux) and the Qt compile check are **manual-only** (`workflow_dispatch`). Nothing runs tests on push: run the JVM tests locally (`unit-tests.yml` only runs on PRs or by hand; 5 tests are known red).

**Version numbering** — up to 21.x the number came from Moonlight; LavArtemis now has its own: Dev `0.7.0-dev.N` (first `0.7.0-dev.1`, versionCode 98 and still increasing), stable `1.0.0` later. **Versions must stay below 20.0.0 forever**: both updaters and the Windows MSI treat a 20+ major as the old numbering, older than any new version (Gradle and `build-arch.bat` refuse to build 20+). The MSI's explicit Upgrade table upgrades 0.0.0→this version and 20.0.0+ (the one-time jump), and refuses to install over a newer version below 20.0.0 (downgrade protection); the Burn bundle got new UpgradeCodes, so the old 21.x bundle entry stays once in "Installed apps" (safe to uninstall, never repair). See "Numérotation propre".

**Top gotchas** — (1) init submodules; (2) never drop the `.lav` suffix; (3) shadow JNI in tests; (4) `applicationId` ≠ namespace; (5) R8 minify is on even in debug (`-dontobfuscate`); (6) the Artemis→LavArtemis rebrand is incomplete (see French section above); (6b) ship **both** ABIs — Android TV sticks (Fire TV, Chromecast with Google TV) are 32-bit, and an arm64-only release only fails at install time with `INSTALL_FAILED_NO_MATCHING_ABIS`; (7) both clients now share **one** `moonlight-common-c` (`Lavagnou/moonlight-common-c`, branch `lavartemis`) — when rebumping it, remember FEC is nanors (not reedsolomon) and decode-unit timestamps are microseconds converted to milliseconds at the JNI boundary; see the desktop section.
