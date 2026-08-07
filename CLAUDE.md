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
| versionName / versionCode | `20.2.8` / `59` |

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
./gradlew assembleTx15_gameDebug         # APK debug TX15 (RadioMaster TX15)
./gradlew assembleTx15_gameRelease       # APK release TX15
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
| `nonRoot_game` | `com.limelight` | — | Flavor **publié** par CI (LavArtemis, manettes standard) |
| `tx15_game` | `com.limelight.tx15` | — | Variante **LavArtemis-TX15** (RadioMaster TX15), `TX15_BUILD=true` |
| `root` | `com.limelight.root` | 25 | Binaire natif `evdev_reader`, `ROOT_BUILD=true`, **dev-only** |

Build types :

| Type | suffixe applicationId | label |
|---|---|---|
| `debug` | `.lavdebug` | LavArtemis (Debug) |
| `release` | `.lav` | LavArtemis |

→ appId release effectif (nonRoot) : **`com.limelight.lav`** ; TX15 : **`com.limelight.tx15.lav`** (co-installable côte à côte). ABI : **`arm64-v8a` uniquement** (les autres ABI ne sont ni buildés ni release).

> 🎮 **Variante TX15** (`tx15_game`, flag `BuildConfig.TX15_BUILD`) : remappe la RadioMaster TX15 dans `binding/input/ControllerHandler.java` — stick gauche X + Y(**inversé**), stick droit RY/RZ, pas de gâchettes, inter (axe Z)→**R1**, boutons 1/2→**Y/L1**. Gated par `BuildConfig.TX15_BUILD` (build dédié) via `applyTx15AxisMapping()` (surcharge des axes de `InputDeviceContext`), inversion Y dans `handleAxisSet`, synthèse Z→bouton dans `handleMotionEvent`, et remap boutons dans `handleRemapping`. La CI publie les **deux variants** à chaque release.

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
- `libmoonlight-core.so` est compilé depuis le **sous-module `moonlight-common-c`** (fork ClassicOldSong), à `app/src/main/jni/moonlight-core/moonlight-common-c/`.
- Pont JNI : **`simplejni.c`** (côté C, fonctions `Java_com_limelight_nvstream_jni_MoonBridge_*`) ↔ **`MoonBridge.java`** (côté Java, déclare aussi les constantes de stream : formats H264/H265/AV1, erreurs, ports).
- `callbacks.c` : callbacks C → Java.
- Bibliothèques statiques prébuildées **committées** : `libopus`, `openssl` (sous `app/src/main/jni/moonlight-core/{libopus,openssl}/<abi>/`).
- `evdev_reader/` = **exécutable natif** (pas une lib) construit **uniquement en flavor `root`** pour lire `/dev/input/event*`.
- `LuaScripts/` = dissecteurs Wireshark + `gridctl.lua` (outils de debug, **non embarqués** dans l'APK).

## 🖥️ Client desktop (`desktop/`)

LavArtemis existe aussi en **client desktop Qt**, dans le sous-module `desktop/` → [`Lavagnou/LavArtemis-Qt`](https://github.com/Lavagnou/LavArtemis-Qt) (C++/Qt 6.11, **qmake**, Windows x64/ARM64 + Linux).

**Lignage** : Moonlight-Qt → [wjbeckett/artemis](https://github.com/wjbeckett/artemis) (portage desktop des features Artemis) → LavArtemis-Qt. Remotes dans le sous-module : `upstream-qt` = moonlight-stream/moonlight-qt, `upstream-artemis` = wjbeckett/artemis.

Déjà présent côté desktop (hérité du portage wjbeckett) : clipboard sync, server commands, pairing OTP (`otpauth`), virtual display Apollo, refresh rate fractionnaire, resolution scaling, lancement par UUID d'app, affichage des permissions, quick menu in-stream.

### ⚠️ Invariant : un seul `moonlight-common-c` — **pas encore tenu**

Cible : les deux clients pointent le même commit de [`Lavagnou/moonlight-common-c`](https://github.com/Lavagnou/moonlight-common-c).

État réel :

| Client | Sous-module | Contenu |
|---|---|---|
| desktop | `Lavagnou/moonlight-common-c` branche `lavartemis` @ `ebb15ef` | `moonlight-stream/master` + cherry-pick `84af637` (`LiSendExecServerCmd`, extension Apollo) |
| Android | `ClassicOldSong/moonlight-common-c` | amont + `84af637` + `c999436` (`LiSendEmptyPayload`) |

> 🚧 **Blocage identifié — ne pas basculer l'Android en l'état.** La branche `lavartemis` écarte volontairement `c999436`, mais l'Android **appelle** `LiSendEmptyPayload` (`Game.java:336` → `simplejni.c:25`). Repointer le sous-module Android tel quel casse le build natif.
>
> Pour tenir l'invariant il faut d'abord cherry-picker `c999436` sur `lavartemis` (côté desktop c'est du code mort, ~20 lignes, inoffensif), rebumper le desktop, puis basculer l'Android. Tant que ce n'est pas fait, les deux sous-modules divergent.

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
| `binding/video/PacingStats.java` | `app/streaming/video/pacingstats.{h,cpp}` — même fenêtre 512, même filtre d'outliers, mêmes percentiles | ✅ porté |
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

- **`.github/workflows/release.yml`** : trigger sur **tag `v*`** (release) ou `workflow_dispatch` (prerelease `v<version>-ci.<run>`). **Une seule release contient Android + Windows.**
  - Job `version` : lit `versionName` dans `app/build.gradle`. Sur tag push, **échoue si le tag ≠ versionName** → `versionName` reste la source de vérité unique ; `desktop/app/version.txt` est écrit depuis le tag au build (jamais committé).
  - Job `build-android` : Ubuntu, JDK 17, NDK `27.0.12077973`, `assembleNonRoot_gameRelease assembleTx15_gameRelease`. N'initialise **que** le sous-module natif (pas `desktop/`).
  - Job `build-windows` : `windows-2025`, Qt 6.11, build x64 **et** arm64 dans le même job (l'installeur combiné a besoin des deux MSI), depuis le commit `desktop/` épinglé ici.
  - Job `publish` : agrège les artefacts et publie via `softprops/action-gh-release@v2`.
  - ⚠️ **Pas de job de test** dans CI (les tests ne tournent pas automatiquement).
  - Linux (AppImage/Flatpak) **pas encore câblé** : nécessite SDL3/libplacebo/FFmpeg buildés depuis les sources.
- **`LavArtemis-Qt/.github/workflows/build.yml`** : compile-check du client desktop (Windows x64/arm64 + Linux) à chaque push. Ne produit pas de release.

Artefacts publiés : `LavArtemis-<tag>-android-arm64-v8a.apk`, `LavArtemis-TX15-<tag>-android-arm64-v8a.apk`, `LavArtemis-<tag>-win-{x64,arm64}.zip`, `LavArtemis-<tag>-win-installer.exe`.

### Signature
- Aucun keystore committé (`key/` est gitignored).
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
- `README.md` ligne 1 : titre toujours **« Artemis Android »**.
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
| `.github/workflows/release.yml` | CI build & release **unifiée** (Android + Windows) |
| `android_test_setup.md` | Guide de tests JVM / Robolectric |
| `.gitmodules` | Sous-modules `moonlight-common-c` **et `desktop/`** |
| `desktop/` | Client Qt (sous-module `LavArtemis-Qt`) |
| `desktop/app/app.pro` | Build Qt : TARGET, icônes, métadonnées Windows |
| `desktop/app/settings/artemissettings.{h,cpp}` | Réglages Artemis/LavArtemis desktop (⚠️ `save()` explicite) |
| `desktop/app/gui/SettingsView.qml` | UI des réglages, groupe « LavArtemis Features » |
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
| TX15 debug/release | `./gradlew assembleTx15_gameDebug` / `assembleTx15_gameRelease` |
| Unit tests (nonRoot) | `./gradlew :app:testNonRoot_gameDebugUnitTest` |
| All unit tests | `./gradlew test` |

**applicationId** — `namespace` is `com.limelight` (unchanged); shipping release appIds are **`com.limelight.lav`** (LavArtemis) and **`com.limelight.tx15.lav`** (LavArtemis-TX15, the RadioMaster TX15 variant — `BuildConfig.TX15_BUILD` remaps its axes in `ControllerHandler.java`). Suffix `.lav`, do not remove. Debug = `.lavdebug`. Root flavor = `com.limelight.root` (dev-only, maxSdk 25).

**Tests** — Robolectric JVM only (no instrumentation). You **must shadow** JNI classes (`MoonBridge`, `GameManager`) or you get `UnsatisfiedLinkError`. See `android_test_setup.md`.

**Release** — push a `v*` tag to trigger `.github/workflows/release.yml`. It builds the 2 Android APKs (`nonRoot_game` + `tx15_game`, arm64-v8a) **and** the Windows x64/ARM64 portable zips + combined installer, then publishes them all under one release. The tag **must** match `versionName` in `app/build.gradle` or the run fails. Android signing via `CI_KEYSTORE_*` / `RELEASE_KEYSTORE_BASE64` (falls back to debug key); Windows Authenticode via `WINDOWS_CERT_BASE64` (skipped if absent). CI runs **no tests**.

**Top gotchas** — (1) init submodules; (2) never drop the `.lav` suffix; (3) shadow JNI in tests; (4) `applicationId` ≠ namespace; (5) R8 minify is on even in debug (`-dontobfuscate`); (6) the Artemis→LavArtemis rebrand is incomplete (see French section above); (7) the two clients still pin **different** `moonlight-common-c` forks, and the Android one cannot move until `LiSendEmptyPayload` lands on the `lavartemis` branch — see the desktop section.
