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
| versionName / versionCode | `20.3.0` / `60` |

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

→ appId release effectif (nonRoot) : **`com.limelight.lav`**. ABI : **`arm64-v8a` uniquement** (les autres ABI ne sont ni buildés ni release).

> 🎮 **Manettes exotiques : rien de spécifique côté Android.** `ControllerHandler.java` détecte les axes **par heuristique** à partir des `MotionRange` déclarés (stick gauche X/Y, stick droit RX/RY sinon Z/RZ, croix HAT_X/HAT_Y). C'est ce qui fait marcher des périphériques HID génériques — dont la RadioMaster TX15 — sans une ligne de code dédiée.
> Un variant `tx15_game` a existé (remap explicite des axes, `BuildConfig.TX15_BUILD`) : c'était un essai, **retiré** — le chemin standard fait déjà le travail. Ne pas le réintroduire sans preuve qu'un appareil résiste à l'heuristique.
> ⚠️ Côté **desktop**, SDL refuse au contraire de deviner : sans entrée dans `gamecontrollerdb.txt`, un périphérique reste un simple joystick et le client l'ignore (`SDL_IsGameController`). C'est la différence de fond entre les deux plateformes.

> ⚠️ **Ne jamais retirer le suffixe `.lav`.** Long commentaire dans `app/build.gradle` (release) : un APK release publié avec l'applicationId Moonlight officiel pollue la Play Console du projet amont — Google attribue les crashs par appId, indépendamment de la signature.

`bundle.language.enableSplit = false` (le sélecteur de langue in-app a besoin de toutes les locales).

## ⚡ Hot path streaming — valeur ajoutée LavArtemis

C'est ici que réside l'essentiel du travail spécifique au fork (performances / fluidité du stream) :

- **ADPF** — `binding/video/AdpfHelper.java` : `PerformanceHintManager` (API 31+) crée une hint session sur les threads decode/render et reporte la durée réelle des frames pour éviter le downclock CPU sur les scènes calmes.
- **Pacing & fluidité** — `binding/video/PacingStats.java` : fenêtre roulante de 512 frames, expose p50/p95/p99 des intervalles present-to-present. Deux modes dans `MediaCodecDecoderRenderer.java` : `checkbox_paced_ull` (aligne les frames sur le vsync en ULL) et `checkbox_predictive_pacing` (timestamp BALANCED 2 vsyncs à l'avance).
- **CSV perf logging** — `StreamSettings.java` + renderer écrivent un CSV par seconde (decode time, RTT, loss, percentiles) pour A/B offline.
- **Natif `-O3 -flto`** — `jni/moonlight-core/Android.mk` optimise les hot paths (FEC Reed-Solomon, dépacketisation) en release.
- **Boost priorité threads** — `jni/moonlight-core/callbacks.c` : `setpriority(-16)` sur le thread dépacketiseur vidéo, `-19` sur l'audio.
- **AV1 en mode Auto** — `checkbox_auto_av1` (défaut on) n'annonce l'AV1 à l'hôte que si un décodeur HW whitelisté **et** ≤15 Mbps ou 4K.
- **Fix audio + cap configurable** — `binding/audio/AndroidAudioRenderer.java` corrige un bug d'effets audio et rend le cap d'audio pending configurable (`seekbar_max_pending_audio_ms`, 10–100 ms).
- **Sustained performance + thermal** — `Game.java` : `setSustainedPerformanceMode(true)` (API 24+) et toast `thermal_throttling_warning` (API 29+).

Toutes ces options sont exposées dans **`app/src/main/res/xml/preferences.xml`** (Advanced Settings).

## 🔧 Code natif / JNI

- Build : **ndk-build** (`app/src/main/jni/Android.mk` aggregator ; `Application.mk` : `APP_PLATFORM android-21`, support des pages 16 KB).
- `libmoonlight-core.so` est compilé depuis le **sous-module `moonlight-common-c`** ([`Lavagnou/moonlight-common-c`](https://github.com/Lavagnou/moonlight-common-c) branche `lavartemis`, **partagé avec le client desktop**), à `app/src/main/jni/moonlight-core/moonlight-common-c/`. La FEC vient de **nanors** (sous-module imbriqué), pas de reedsolomon.
- Pont JNI : **`simplejni.c`** (côté C, fonctions `Java_com_limelight_nvstream_jni_MoonBridge_*`) ↔ **`MoonBridge.java`** (côté Java, déclare aussi les constantes de stream : formats H264/H265/AV1, erreurs, ports).
- `callbacks.c` : callbacks C → Java.
- Bibliothèques statiques prébuildées **committées** : `libopus`, `openssl` (sous `app/src/main/jni/moonlight-core/{libopus,openssl}/<abi>/`).
- `evdev_reader/` = **exécutable natif** (pas une lib) construit **uniquement en flavor `root`** pour lire `/dev/input/event*`.
- `LuaScripts/` = dissecteurs Wireshark + `gridctl.lua` (outils de debug, **non embarqués** dans l'APK).

## 🖥️ Client desktop (`desktop/`)

LavArtemis existe aussi en **client desktop Qt**, dans le sous-module `desktop/` → [`Lavagnou/LavArtemis-Qt`](https://github.com/Lavagnou/LavArtemis-Qt) (C++/Qt 6.11, **qmake**, Windows x64/ARM64 + Linux).

**Lignage** : Moonlight-Qt → [wjbeckett/artemis](https://github.com/wjbeckett/artemis) (portage desktop des features Artemis) → LavArtemis-Qt. Remotes dans le sous-module : `upstream-qt` = moonlight-stream/moonlight-qt, `upstream-artemis` = wjbeckett/artemis.

Déjà présent côté desktop (hérité du portage wjbeckett) : clipboard sync, server commands, pairing OTP (`otpauth`), virtual display Apollo, refresh rate fractionnaire, resolution scaling, lancement par UUID d'app, affichage des permissions, quick menu in-stream.

### Features Artemis portées côté LavArtemis-Qt

| Feature | Où | Notes |
|---|---|---|
| **Profils de réglages** | `settings/profilemanager.{h,cpp}`, groupe « Settings Profile » en haut de `gui/SettingsView.qml` | `profiles.json` dans `AppConfigLocation`. Un profil est un **snapshot complet**, pas un patch épars : `save()` écrit toutes les clés, donc un profil créé capture l'état courant puis vit sa vie. `language` et `defaultver` **bypassent** les profils (changer de profil ne doit pas changer la langue de l'app). |
| **Send keys + macros clavier** | `backend/keymacromanager.{h,cpp}`, sous-menu « Send Keys » de `gui/QuickMenu.qml` | 16 presets intégrés + macros utilisateur dans `keyboard-macros.json` (`AppConfigLocation`). **Même format JSON et mêmes noms `VK_` que l'Android** → un fichier marche sur les deux. La table des 174 noms est extraite de `utils/KeyMapper.java`, pas retapée. |
| **Liens `art://` + fichiers `.art`** | `cli/artlink.{h,cpp}`, enregistrement OS dans `wix/LavArtemis/Product.wxs` (HKCU) et `deploy/linux/com.lavartemis.LavArtemis.{desktop,xml}` | `ArtLink::rewriteArguments()` **traduit le lien en ligne de commande existante** (`stream <host> <app>` / `pair <host> --pin --passphrase`) au lieu d'ajouter un chemin de lancement parallèle → hérite du host seeking, du wake-on-LAN et de l'UI de segue. Formats identiques à l'Android. |

> ⚠️ **Le bug Android des profils n'est pas reproduit.** Côté Android, `OverlaySharedPreferences` lit à travers la map du profil mais `edit()` renvoie l'éditeur de base (`ProfilesManager.java:251`) : les écritures s'échappent du profil et atterrissent dans les prefs globales. Côté Qt, `StreamingPreferences::reload()` **et** `save()` passent tous les deux par `ProfileManager::value()`/`setValue()` — lecture et écriture ne peuvent pas diverger par construction.

Deux effets de bord utiles du portage `art://` : le lancement CLI accepte désormais un **UUID ou un id d'app** en plus du nom (`startstream.cpp::getAppIndex`), et `pair` accepte `--passphrase` (routé vers `pairHostWithOTP`, pairing OTP Apollo).

> ⚠️ **Pas d'instance unique.** Cliquer un lien `art://` alors que LavArtemis tourne déjà lance un **second processus**. Ce n'est pas une régression : c'est déjà le comportement de `lavartemis stream <host> <app>`, les commandes CLI amont étant conçues comme des processus indépendants. Une IPC `QLocalServer` serait nécessaire pour changer ça.

**Pan & zoom (D4)** est porté : `streaming/panzoom.{h,cpp}`, piloté par les combos Ctrl+Alt+Shift (`+`/`-`/`0`, flèches). Tous les renderers passaient déjà par un seul helper — la géométrie tient donc dans une fonction, `StreamUtils::scaleSourceToDestinationSurfaceWithPanZoom()`, et pas dans chaque backend. Le mapping des coordonnées souris passe par le **même** helper, donc le pointeur suit le zoom sans code supplémentaire.

> ⚠️ **Un seul appelant reste sur l'ancien helper** : `Session::getWindowDimensions()` (`session.cpp:1538`) réutilise l'ajustement d'aspect pour dimensionner la fenêtre. Y appliquer le zoom **redimensionnerait la fenêtre** au lieu d'agrandir la vidéo. C'est pour ça que c'est une seconde fonction et pas un flag dans la première.

**Export de fichiers `.art`** : `AppModel::exportArtFile()`, entrée « Export Shortcut File… » du menu contextuel d'`AppView.qml`. Même format `[clé] valeur` que `ShortcutHelper.java:243` → un fichier écrit ici s'ouvre sur Android et inversement.

Pas encore porté : **SBS 3D MiDaS** (hors périmètre v1).

### 🖥️ Multi-écran émulé (desktop uniquement, v20.7.0)

Quand le PC client a plusieurs moniteurs, l'hôte **LavApollo** en émule un écran virtuel chacun, aux
bonnes résolutions et à la bonne disposition, et le client réaffiche chaque écran hôte sur le
moniteur correspondant. Réglage « Virtual Display Multi-Screen », désactivé par défaut et grisé si l'écran
virtuel de base est désactivé. **Exclusivement desktop** — l'Android est hors périmètre.

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

Un bouton de bascule est aussi posé dans la barre d'outils du client, à côté des autres — c'est le
réglage qu'on change selon l'endroit où l'on est assis, pas selon ses goûts. Il suit jusqu'à la liste
des applications d'un hôte, l'écran d'où l'on lance réellement un jeu.

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
   - Réglage `genericGamepadFallback` (défaut **on**) dans *LavArtemis Features → Input*.
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
release. Deux canaux : stable (`/releases/latest`) ou CI (`/releases`, les builds CI étant publiés en
prerelease). Sous Windows, l'asset est **`-win-installer.exe` quelle que soit l'architecture** : le
bundle WiX embarque les MSI x64 et arm64 et choisit par `NativeMachine`, alors que les zips par arch
sont des builds portables sans installeur dedans.

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

**L'Android n'a pas d'updater in-app** : la distribution passe par Obtainium / l'APK de la release.

### ✅ Invariant : un seul `moonlight-common-c` — **tenu**

Les deux clients pointent le même commit de [`Lavagnou/moonlight-common-c`](https://github.com/Lavagnou/moonlight-common-c), branche `lavartemis` @ `0da9626` = `moonlight-stream/master` (juillet 2026) + `84af637` (`LiSendExecServerCmd`) + `c999436` (`LiSendEmptyPayload`).

`LiSendEmptyPayload` est du code mort côté desktop ; il est là pour que l'Android (`Game.java:336`) puisse partager la branche.

> ⚠️ **La bascule de l'Android a coûté deux ajustements**, à connaître avant de rebumper :
> - **FEC : reedsolomon → nanors.** Sous-module différent, avec ses propres sources `deps/obl/`. `Android.mk` liste `nanors/deps/obl/oblas_common.c`, `oblas_lite.c`, `nanors/rs.c` et les trois include paths correspondants (mêmes que le `.pro` desktop).
> - **Timestamps des decode units : ms → µs.** `receiveTimeMs` → `receiveTimeUs`. Le renommage est la partie visible, **le changement d'unité est la partie dangereuse** : le Java travaille toujours en ms (`MediaCodecDecoderRenderer` accumule `enqueue - receive` dans `totalTimeMs` et multiplie `enqueue` par 1000 pour le timestamp MediaCodec). Passer les µs telles quelles gonflerait les deux ×1000, silencieusement. La conversion se fait dans `callbacks.c` (`usToMs()`), le contrat Java est inchangé. Récupérer la précision supplémentaire est souhaitable mais mérite un commit isolé, bisectable.

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
| `checkbox_paced_ull` | ❌ **ne pas porter.** C'est un rattrapage Android : en ULL le renderer présente sans alignement vsync. Le desktop a déjà un vrai `IVsyncSource` (`DxVsyncSource` / Wayland presentation-time) derrière l'option **Frame pacing** de moonlight-qt — activer cette option *est* le mode pacé, en mieux (vsync réel, pas extrapolé). |
| `checkbox_predictive_pacing` | ❌ **ne pas porter.** L'équivalent supposé (`SetMaximumFrameLatency(1)`) est **délibérément évité** en amont : cf. le commentaire de `d3d11va.cpp` (~l. 551) — avec `SyncInterval 0`, le fixer à 1 fait bloquer `Present()` sur DWM et **augmente** la latence. Le `+2 vsync` Android est un détail SurfaceFlinger sans équivalent. |
| AV1 auto (≤15 Mbps ou 4K) | ❌ **ne pas porter tel quel.** Le mode Auto amont sonde déjà les décodeurs et *déprioritise* AV1 (sans le retirer) selon la dispo HEVC HW — cf. `session.cpp` ~l. 909-944. Appliquer en plus le seuil Android *retirerait* AV1 à >15 Mbps en <4K et ferait retomber en H.264 les GPU qui ne décodent qu'AV1 en HW : régression. |
| ADPF | ❌ pas d'équivalent Windows. La partie « inhibition veille » est déjà couverte par `SDL_DisableScreenSaver()` (`session.cpp:2137`) → `SetThreadExecutionState`. |
| Sustained performance mode, avertissement thermique | ❌ pas d'équivalent Windows — hors périmètre |

Les réglages desktop LavArtemis vivent dans `app/settings/artemissettings.{h,cpp}` (singleton QSettings, fichier `artemis-settings.ini` dans `AppConfigLocation`), exposés en QML via `qmlRegisterSingletonType` dans `main.cpp` et édités dans le groupe **LavArtemis Features → Streaming Performance** de `gui/SettingsView.qml`.

> ⚠️ Les setters d'`ArtemisSettings` **ne persistent pas** tout seuls : `SettingsView.qml` appelle `ArtemisSettings.save()` dans `StackView.onDeactivating` et `Component.onDestruction`, à côté de `StreamingPreferences.save()`. Tout nouveau point d'édition doit faire pareil.

## 🤖 CI/CD & release

- **`.github/workflows/release.yml`** : trigger sur **tag `v*`** (release) ou `workflow_dispatch` (prerelease `v<version>-ci.<run>`). **Une seule release contient Android + Windows + Linux.**
  - Job `version` : lit `versionName` dans `app/build.gradle`. Sur tag push, **échoue si le tag ≠ versionName** → `versionName` reste la source de vérité unique ; `desktop/app/version.txt` est écrit depuis le tag au build (jamais committé).
  - Job `build-android` : Ubuntu, JDK 17, NDK `27.0.12077973`, `assembleNonRoot_gameRelease`. N'initialise **que** le sous-module natif (pas `desktop/`).
  - Job `build-windows` : `windows-2025`, Qt 6.11, build x64 **et** arm64 dans le même job (l'installeur combiné a besoin des deux MSI), depuis le commit `desktop/` épinglé ici.
  - Job `build-linux` : `ubuntu-22.04`, AppImage x86_64. **Le plus long de loin** — aucun paquet prébuildé n'existe pour ce dont le client a besoin, donc SDL3, sdl2-compat, SDL_ttf, libva, libplacebo, dav1d et FFmpeg sont compilés depuis les sources avant même de configurer l'app. Les deps sont clonées sous `desktop/deps/` pour que les chemins relatifs des patchs du sous-module continuent de résoudre. Empaquetage par `desktop/scripts/build-appimage.sh` + linuxdeploy.
  - Job `publish` : agrège les artefacts et publie via `softprops/action-gh-release@v2`. **Bloquant sur les 3 jobs de build** : une plateforme qui casse fait échouer la release entière plutôt que de publier une release incomplète en silence.
  - Job `version` : contrôle aussi la santé du pointeur `desktop/`, en deux temps. **1)** Le commit épinglé a-t-il un compile-check vert *à lui* — c'est la question qui compte, puisque c'est ce commit-là qu'on publie. **2)** Seulement ensuite, est-il en retard sur le dernier commit vert, avec la liste des commits manqués. Il **signale sans corriger** — un tag doit reconstruire la même chose à chaque fois, donc avancer le pointeur en cours de release casserait la reproductibilité. En `continue-on-error` : un hoquet de l'API GitHub annote le run, il ne fait pas échouer la release.
  - ⚠️ **`compare/BASE...HEAD` décrit HEAD *par rapport à* BASE** : seul le statut `ahead` veut dire que le pointeur est réellement en retard. `behind` signifie que le dernier commit vert est un **ancêtre** de ce qu'on épingle — ce qui arrive dès que le commit épinglé n'a pas de build vert. Se contenter d'un `!=` faisait annoncer « 0 commits ahead » à chaque release, une alerte qui a crié dans le vide pendant 17 jours (v20.7.x → v20.8.2).
  - ⚠️ **Le compile-check et la release ne compilent pas le même code.** `build.yml` côté LavArtemis-Qt construit Linux **sans** `APP_IMAGE`, le job `build-linux` d'ici le construit **avec**. Les branches `#ifdef` diffèrent donc, et une erreur qui ne touche qu'un chemin peut laisser la release verte pendant que le compile-check est rouge — exactement ce qui est arrivé à `findAssetDownloadUrl()`. **Un compile-check rouge n'est pas cosmétique même quand la release passe.**
  - ⚠️ **Pas de job de test** dans CI (les tests ne tournent pas automatiquement). ⚠️ **5 tests JVM échouent déjà** sur `main` (`LayoutInflationTest`, `SimpleStartupTest`, `StartupTest`, `ProfilesNavigationTest` ×2) — problèmes de setup Robolectric préexistants, sans rapport avec le portage desktop.
- **`.github/workflows/update-desktop-pointer.yml`** : avance le sous-module `desktop/` vers le dernier commit **vert** de LavArtemis-Qt. **Manuel** (`workflow_dispatch`, avec SHA optionnel) — pas de cron, le pointeur ne comptant qu'au moment de couper un tag. Tourne **dans** LavArtemis et écrit **dans** LavArtemis, donc le `GITHUB_TOKEN` intégré suffit : aucun PAT à provisionner. Réécrit le gitlink via `git update-index --cacheinfo` plutôt que de checkouter le sous-module (économise un clone de ~250 Mo).
  - **Flatpak non câblé** : demande un manifeste (inexistant) et une soumission Flathub, chantier à part. Le Steam Deck tourne l'AppImage.
- **`LavArtemis-Qt/.github/workflows/build.yml`** : compile-check du client desktop (Windows x64/arm64 + Linux) à chaque push. Ne produit pas de release.
  - ⚠️ `build-appimage.yml` y est **orphelin** (`workflow_call` que personne n'appelle) : c'est la référence dont le job `build-linux` ci-dessus est dérivé. Toute correction doit aller dans les deux.

Artefacts publiés : `LavArtemis-<tag>-android-arm64-v8a.apk`, `LavArtemis-<tag>-win-{x64,arm64}.zip`, `LavArtemis-<tag>-win-installer.exe`, `LavArtemis-<tag>-linux-x86_64.AppImage`.

### Signature
- Aucun keystore committé (`key/` + `*.keystore`/`*.jks`/`*.b64` sont gitignored).
- **Mise en place initiale : voir `SIGNING.md`** (script `scripts/generate-release-keystore.ps1` + secrets GitHub à créer). Sans ça, chaque release CI est signée avec une debug key éphémère → les mises à jour sont refusées (« package en conflit »).
- Release Android signée via variables d'environnement : `CI_KEYSTORE_PATH`, `CI_KEYSTORE_PASSWORD`, `CI_KEY_ALIAS`, `CI_KEY_PASSWORD` (le secret GitHub `RELEASE_KEYSTORE_BASE64` est décodé vers `CI_KEYSTORE_PATH` dans le workflow).
- Sans secret : sur GitHub Actions → fallback **debug key** (artefacts installables mais signature distincte) ; en local → APK **non signé** (signer manuellement avec `apksigner`).
- Windows : Authenticode **optionnel** via `WINDOWS_CERT_BASE64` / `WINDOWS_CERT_PASSWORD`. Absents → binaires publiés non signés (le build ne casse pas).

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

## 🧹 Renommages incomplets / Dette technique (Artemis → LavArtemis)

Le rebrand vers LavArtemis est partiel. Restes connus à finaliser :
- ✅ ~~`README.md`~~ réécrit pour LavArtemis (deux clients, lignage, liens Obtainium corrigés). Le « Disclaimer » de ClassicOldSong, un témoignage personnel à la première personne, a été retiré — il n'avait pas à figurer sous le nom d'un autre mainteneur ; son travail est crédité dans « Lineage and credits ».
- Classe **`ArtemisApplication.java`** non renommée (référencée par `AndroidManifest.xml` et par `StartupTest`/`SimpleStartupTest`).
- Identifiant clipboard **`"ArtemisStreaming"`** dans `Game.java`.
- Email/branding dans `res/values/strings.xml` : `artemistics.logs@gmail.com`, « Artemistics - Performance Log », `https://tinyurl.com/artemis-performance`.
- Typo **« Nior »** (devrait être « Noir ») dans `summary_software_update` (`strings.xml`) + mention résiduelle de ClassicOldSong.
- **Icônes launcher et couleurs thème inchangées** (toujours celles d'Artemis : `#1A1A1A` / `#000000`).
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
| `.github/workflows/release.yml` | CI build & release **unifiée** (Android + Windows + Linux) |
| `.github/workflows/update-desktop-pointer.yml` | Avance le sous-module `desktop/` vers le dernier commit vert (manuel) |
| `android_test_setup.md` | Guide de tests JVM / Robolectric |
| `.gitmodules` | Sous-modules `moonlight-common-c` **et `desktop/`** |
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
| `desktop/app/gui/SettingsView.qml` | UI des réglages, groupes « Settings Profile » et « LavArtemis Features » |
| `desktop/app/backend/autoupdatechecker.{h,cpp}` | Vérification, téléchargement et lancement de l'installeur (⚠️ un signal terminal par check ; NAM recréé à chaque fois) |
| `desktop/app/gui/QuickMenu.qml` | Menu in-stream (server commands, send keys) |
| `desktop/app/streaming/video/pacingstats.{h,cpp}` | Métriques de fluidité — port de `PacingStats.java` |
| `desktop/app/streaming/video/ffmpeg.cpp` | Décodeur + `writePerfCsvRow()` (CSV perf) |
| `desktop/moonlight-common-c/moonlight-common-c.pro` | LTO du cœur natif (release) |
| `desktop/scripts/build-arch.bat` | Build Windows par architecture |
| `desktop/wix/LavArtemis{,Setup}/` | Installeur WiX (⚠️ `UpgradeCode` propre à LavArtemis) |

---

## ⚡ Quick reference (English)

**What:** LavArtemis is a GameStream client for **Apollo/Sunshine** hosts, shipped for **Android and desktop**:
- **Android** (this repo) — fork of Artemis (`ClassicOldSong/moonlight-android`), itself a fork of Moonlight. Java-only + C (NDK, ndk-build). Fork-specific work sits in the streaming hot path: ADPF, frame pacing, native `-O3`/LTO + thread-priority boosts, AV1-auto, an audio-renderer fix, sustained-performance/thermal handling.
- **Desktop** (`desktop/` submodule → `Lavagnou/LavArtemis-Qt`) — C++/Qt 6.11, qmake, Windows x64/ARM64. Fork of `wjbeckett/artemis` (the Artemis→Qt port) rebased onto current moonlight-qt.

One tag builds and releases both. See the "Client desktop" section above.

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

**Release** — push a `v*` tag to trigger `.github/workflows/release.yml`. It builds the Android APK (`nonRoot_game`, arm64-v8a), the Windows x64/ARM64 portable zips + combined installer, **and** the Linux x86_64 AppImage, then publishes them all under one release. The Linux job compiles SDL3/libplacebo/FFmpeg from source and is by far the slowest; `publish` blocks on all three so a broken platform fails the release instead of shipping it half-done. The tag **must** match `versionName` in `app/build.gradle` or the run fails. Android signing via `CI_KEYSTORE_*` / `RELEASE_KEYSTORE_BASE64` (falls back to debug key); Windows Authenticode via `WINDOWS_CERT_BASE64` (skipped if absent). CI runs **no tests**.

**Top gotchas** — (1) init submodules; (2) never drop the `.lav` suffix; (3) shadow JNI in tests; (4) `applicationId` ≠ namespace; (5) R8 minify is on even in debug (`-dontobfuscate`); (6) the Artemis→LavArtemis rebrand is incomplete (see French section above); (7) both clients now share **one** `moonlight-common-c` (`Lavagnou/moonlight-common-c`, branch `lavartemis`) — when rebumping it, remember FEC is nanors (not reedsolomon) and decode-unit timestamps are microseconds converted to milliseconds at the JNI boundary; see the desktop section.
