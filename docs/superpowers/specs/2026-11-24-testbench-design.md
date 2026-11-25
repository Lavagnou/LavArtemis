# Banc de test : la vraie appli contre un faux PC hôte

Date : 2026-11-24. Dépôts : LavApollo, LavArtemis-Qt, LavArtemis (Android + outillage).

## Pourquoi

L'utilisateur teste aujourd'hui chaque version à la main, sur ses vrais appareils et ses vrais PC. Il veut que
Claude puisse lancer **la vraie appli**, la piloter (cliquer, lire l'écran, faire des captures) et vérifier
**tous les niveaux** — interface, appairage, bibliothèque, réglages, lancement, stream, menu en jeu, entrées —
contre un **faux PC hôte** aux données inventées, **sans jamais toucher à ses appareils**. Usages : vérifier
chaque nouveauté, débuguer, automatiser les vérifications avant chaque release, remplacer ses tests manuels.

## Objectifs

- Un **hôte de test** : LavApollo lui-même, lancé dans un mode « hôte de test », qui répond, appaire, liste
  des applis et une bibliothèque Playnite inventées, lance et **streame** une vidéo et un son synthétiques, et
  **enregistre** ce qu'un vrai hôte exécuterait (entrées, commandes, presse-papiers, micro).
- Plusieurs hôtes de test en même temps, pour simuler plusieurs PC (en ligne, éteint, non appairé, version non
  prise en charge, appli en cours, Playnite lancé).
- Un **client de test** desktop et Android : la vraie appli, compilée depuis le même code avec une option, qui
  ne parle qu'aux hôtes de test et que Claude pilote par un canal de commande.
- Un **orchestrateur** qui monte et démonte le tout, et une **suite de scénarios** rejouable à chaque version.
- **Suit le développement** : ce sont des modes du code existant, pas des copies ; ils se construisent avec
  les mêmes sources à chaque build.

## Non-objectifs

- Mesurer de vraies performances (la vidéo est synthétique, le réseau est local).
- HDR, multi-écran émulé, PyroWave dans la première version.
- Tester l'installeur Windows ou la mise à jour in-app (plus tard, dans une machine virtuelle ou une sandbox).
- Remplacer les tests unitaires existants (JVM, Qt, LavApollo) : le banc les complète.

## Anti-objectifs (ce qui en ferait un échec même s'il « marche »)

- Toucher au vrai système : capturer l'écran réel, créer un écran virtuel, changer la sortie audio ou les modes
  d'affichage, injecter des entrées dans Windows, lancer une vraie appli ou une commande, modifier les réglages
  NVIDIA, écrire dans la config du vrai LavApollo (service `ApolloService`, qui continue de tourner) ou dans les
  réglages de la Dev / de la stable.
- Parler à un vrai hôte (y compris ce PC, ATLAS-PC, qui est un hôte) ou à un vrai appareil Android (Pixel 7,
  Fire TV) : seul l'émulateur.
- Livrer le canal de commande ou le mode hôte de test dans une release.
- Dériver du vrai code : un faux serveur écrit à part, que chaque évolution de LavApollo laisserait derrière.

## Approches envisagées

| | Ce que c'est | Pour | Contre |
|---|---|---|---|
| A. Interface seule | Faux serveur des requêtes HTTP (serverinfo, applist, pair, launch) | Rapide | Rien après le lancement (menu en jeu, stats, entrées, benchmark) ; serveur écrit à part, qui dérive |
| **B. LavApollo en hôte de test** (choisie par l'utilisateur) | Le vrai LavApollo, isolé, avec une source vidéo/audio synthétique et des effets de bord journalisés | Couvre tout, jusque dans le stream ; même code, suit les mises à jour | Le plus de travail ; demande des points de coupure dans LavApollo |

## 1. Hôte de test (LavApollo)

Un mode du même exécutable (`sunshine.exe` construit dans `build/t`), activé par la clé de config
`test_host = enabled`. L'orchestrateur écrit une config par faux PC et lance l'exécutable avec ce fichier en
argument (`config.cpp:1526`, un argument sans `=` est le fichier de config).

### Isolation des fichiers

- `appdata()` (aujourd'hui le dossier de l'exe + `\config`, `windows/misc.cpp:131`) lit la variable
  d'environnement **`LAVAPOLLO_APPDATA`** quand elle est posée : tout ce qui en dérive (credentials, état,
  `apps.json`, `covers/`, `display_device.state`, `updates/`, `lavapollo_auth.json`, `lavapollo_history.db`,
  journaux) va dans le dossier du faux PC.
- L'orchestrateur lance chaque instance avec **`ProgramData`** pointé sur un dossier du faux PC : la
  bibliothèque Playnite (`playnite.cpp:71`), `nvprefs_undo.json`, `nvcp_overrides.json`, `rtss_overrides.json`
  y sont alors propres à l'instance.
- Répertoire de travail = `build/t` (les `assets` y sont relatifs, `common.cmake:34`).
- Ports : base `port` différente par instance (40989, 41989…), loin de 47989 et **sous 49152** (au-dessus,
  Windows réserve des plages à chaque démarrage, `config.cpp:1400`) ; tous les autres en dérivent
  (`network.cpp:190`).
- **Écoute sur 127.0.0.1 seulement** (nouvelle clé `bind_address`, forcée en mode test) : le client desktop
  est local, l'émulateur Android joint `127.0.0.1` par `10.0.2.2`. Pas d'alerte du pare-feu Windows (qui
  bloquerait un run sans surveillance), aucun faux PC visible sur le réseau. *(Choix de Claude.)*
- Aussi coupé : la reprise après plantage de `display_device::init` (`main.cpp:211`).

### Ce que le mode coupe ou remplace (`test_host = enabled`)

| Effet réel | Point de coupure | En mode test |
|---|---|---|
| Profil NVIDIA, NVCP, RTSS au démarrage | `main.cpp:219-233`, `stream.cpp:2554` | Sautés |
| Pilote d'écran virtuel (SudoVDA), écrans virtuels, changements de mode, sonde d'encodeur avec écran temporaire | `process.cpp:2142`, `:342-677`, `:1164`, `main.cpp:376` | Sautés, toujours |
| Capture d'écran | `platf::display()` (`display_base.cpp:996`) | **Source synthétique** (ci-dessous) |
| Audio : sortie par défaut changée, pilotes Steam installés | `audio.cpp:188`, `windows/audio.cpp:1404` | **Source synthétique** (tonalité), aucun changement de sortie, aucune installation |
| Entrées (clavier, souris, manette, tactile, stylet) | appels `platf::*` de `src/input.cpp` | **Journal** `input.jsonl` (un événement par ligne), rien envoyé à Windows, pas de ViGEm |
| Applis : commandes, prep, detached, undo | `platf::run_command` (`process.cpp:779-1132`) | **Journal** `commands.jsonl` ; l'appli « tourne » jusqu'à ce qu'on la quitte |
| Playnite : `playnite://…` | `playnite.cpp:214` | Journal ; l'hôte de test écrit lui-même le `state.json` « jeu lancé » |
| Presse-papiers | `misc.cpp:1993-2074` | Journal + mémoire de l'instance (le client relit ce qu'il a envoyé) |
| Micro reçu | `mic_passthrough.cpp` | Compté et journalisé, aucun périphérique installé |
| Commandes serveur (exec) | `stream.cpp:1110` | Journal, jamais exécutées |
| `streaming_will_start` (minuterie, MMCSS, Wi-Fi, Mouse Keys), garder l'écran allumé | `misc.cpp:1194`, `display_base.cpp:224` | Sautés |
| mDNS, UPnP, icône, vérification de mise à jour | config | Forcés à off |

- `serverinfo` annonce **`<LavTestHost>1</LavTestHost>`** : c'est à ça que le client de test reconnaît un hôte
  de test (et le seul hôte qu'il accepte).
- Le nom du PC (`hostname`), `appversion`, `uniqueid`, la MAC et l'état « Playnite lancé » viennent de la
  config du faux PC : on peut simuler une version non prise en charge, un PC sans Playnite, etc.
- Le **mode d'écran annoncé** (`CurrentDisplayWidth/Height/RefreshRate`, que le client suit avec la « résolution
  calée ») est **fixe** : `test_host_display` (`display` de `pc.json`), défaut 1920×1080 à 60 Hz, jamais l'écran
  réel du PC des tests — sinon la taille négociée dépendait du moniteur, et un changement de mode de celui-ci faisait
  reconnecter le client de test en pleine session.

### Source vidéo synthétique

- **Encodage logiciel par défaut** (`encoder = software`, libx264) : l'hôte de test ne touche pas à
  l'encodeur de la carte graphique, ne prend aucune session NVENC/AMF au vrai LavApollo (un stream réel de
  l'utilisateur pendant un test n'est pas perturbé) et marche sans GPU (machine virtuelle). Une option
  `test_host_hw_encoder` garde le chemin matériel pour les tests propres à un codec. *(Choix de Claude.)*
- Une classe `display_synthetic_t` (capture `"synthetic"`) **en mémoire système** (images `img_t` en RAM, le
  format que l'encodeur logiciel consomme) ; une variante D3D11 (`display_vram_t`, device sans sortie DXGI)
  seulement pour l'option matérielle. SDR, à la taille et à la fréquence demandées par le client.
- Chaque image est **la scène du benchmark** (`tools/benchmark_scene.cpp:31-131`, fonction du seul numéro
  d'image) : sa formule passe dans un en-tête partagé par l'outil (shader) et la source (rendu CPU, plus
  simple ; l'image peut être plus pauvre que la scène GPU tant qu'elle bouge et porte le numéro). Le numéro
  d'image est inscrit en bits en bas à gauche (`:128`) : un test vérifie que les images arrivent, dans
  l'ordre, et compte les pertes.
- Cadence de `display_base_t::capture` ; `frame_timestamp` posé à chaque image. Choisie d'office en mode test,
  quel que soit `capture`.

### Données d'un faux PC (fixtures)

Dossier `tools/testbench/fixtures/<nom>/` dans LavArtemis : `pc.json` (nom, version, MAC, ports, Playnite
lancé ou non, appli en cours, mode d'écran annoncé `display` = `"LxHxF"`, facultatif), `apps.json`, `playnite/library.json` + `covers/`. Les jaquettes sont
**générées** (titre sur un fond de couleur), jamais des images de vrais jeux.

## 2. Client de test desktop (LavArtemis-Qt)

- Option de build **`CONFIG+=lav_test`** → `LAV_TEST_BUILD` (en plus du canal Dev), dans un dossier de build à
  part (`build/test-x64`), compilé incrémentalement depuis les mêmes sources. Jamais construit par
  `local-release.ps1` ni par la CI.
- **Isolation** : organisation Qt « LavArtemis Test » (registre, AppData à part) ; découverte mDNS coupée ;
  vérification de mise à jour coupée. **Deux garde-fous** : avant toute requête, seule une adresse de bouclage
  dans la plage des hôtes de test (40000-44999, liste blanche : le vrai LavApollo de ce PC écoute sur la base 45989) est acceptée — le client de test ne parle jamais au vrai hôte
  de ce PC, même pour un `serverinfo` ; puis tout serveur qui n'annonce pas `LavTestHost` est refusé. Le refus « ce PC »
  (`isThisPc`) ne s'applique pas aux hôtes de test, dans ce build seulement.
- **Canal de commande** : un `QLocalServer` (nom `lavartemis-test`), une commande JSON par ligne :
  - `tree` : les éléments QML visibles (type, `objectName`, texte, rectangle, focus), pour trouver quoi viser ;
  - `click` (par texte, `objectName` ou coordonnées), `key`, `text`, `wheel`, `gamepad` (boutons, axes) ;
  - `screenshot` (fenêtre Qt, ou fenêtre du stream SDL) vers un PNG ;
  - `state` (PC connus, applis, page affichée, session en cours, stats du stream) ;
  - `setting` (lire, écrire, remettre à zéro), `reset` (tout effacer), `quit`.
  
  Pendant un stream, les entrées passent par `SDL_PushEvent` (et une manette virtuelle SDL) : elles suivent le
  même chemin qu'une vraie touche, jusqu'au journal de l'hôte.
- Aucun mouvement de la vraie souris, aucune touche système : l'utilisateur peut se servir du PC pendant un
  test.
- **Visible mais discret** (décision de l'utilisateur) : les fenêtres de test s'affichent, mais le build de test
  force le mode fenêtré (jamais plein écran), ne prend ni le focus ni la souris (pas de capture du pointeur, pas
  de `SetForegroundWindow`), coupe le son du stream et remplace le micro par une source factice (aucun
  enregistrement du vrai micro). Les captures se font par `PrintWindow` / le rendu, pas par l'écran.

## 3. Client de test Android (LavArtemis)

- Un type de build **`uitest`** (débogable, `applicationIdSuffix ".lavtest"`, `LAV_DEV = true`,
  `LAV_TEST = true`) : données à part, même garde-fou `LavTestHost`, découverte coupée.
- Lancé dans l'**émulateur** (AVD `Pixel_7`, et `Fire_TV` pour la télécommande) ; l'hôte de test est joint par
  `10.0.2.2:<port>`. `adb` vise toujours le numéro de série de l'émulateur, jamais un appareil réel.
  Émulateur sans son (`-no-audio`) et sans micro de l'hôte : même règle « visible mais discret » que le
  desktop.
- Piloté par adb + uiautomator (outils mobile-mcp) : arbre d'écran, appuis, texte, captures ; un récepteur de
  diffusion de débogage (`uitest` seulement) pour `state`, `setting`, `reset`.
- Décodage dans l'émulateur : H.264 logiciel (le banc force H.264 côté Android ; le client accepte déjà le
  décodeur logiciel de l'émulateur, `SHOULD_BYPASS_SOFTWARE_BLOCK` sur `ranchu`).
- Manette et télécommande sans root : `adb shell input gamepad keyevent …` et `input dpad keyevent …`.
- **Mêmes scénarios que le desktop** (parité : 1 à 6), plus le bouton vocal (8) ; le 7 (« ce PC ») n'existe
  pas sur Android. Le `Fire_TV` a sa série : navigation à la télécommande dans l'accueil, la bibliothèque,
  Mes PC, les réglages et le menu en jeu. *(Choix de Claude, au nom de la parité.)*

## 4. Orchestrateur et scénarios (LavArtemis `tools/testbench/`)

- Python, une commande : `testbench up [fixtures…]` (construit ce qui manque : `sunshine` dans `build/t`, le
  client de test desktop, l'APK `uitest` ; monte un dossier par faux PC ; lance les instances et le client),
  `testbench down`, `testbench client <commande>` (relaie au canal), `testbench shot`, `testbench logs <pc>`.
- Le dossier de travail est temporaire et effacé par `down` : rien ne persiste entre deux runs.
- **Suite de scénarios** (pytest), chacun repart d'un état propre. Première série :
  1. Premier lancement, ajout d'un PC par adresse, appairage par PIN (le PIN est envoyé à l'hôte de test par
     son API web, comme on le taperait) et par lien `art://` (OTP).
  2. Accueil : Bureau, Playnite Fullscreen, Récents dans l'ordre de l'hôte ; bibliothèque, jaquettes, masquer.
  3. Mes PC : en ligne, éteint, non appairé, version non prise en charge (message), renommer, oublier.
  4. Réglages v2 : chaque page en mode normal et expert, écriture persistée, profils.
  5. Lancer le Bureau : des images arrivent (numéros croissants), menu en jeu, « Envoyer des touches » (les
     touches attendues sont dans `input.jsonl`), statistiques, quitter / déconnecter / quitter la session.
  6. Jeu Playnite pendant que Playnite tourne (reprise `resume`), « Quitter le jeu ».
  7. Refus « ce PC » : un hôte de test lancé avec `test_host_counts_as_this_pc` pour lever l'exemption.
  8. Bouton vocal (Android) : ² tenue puis relâchée dans le journal.
- Chaque run écrit un rapport (succès / échecs, captures, journaux des hôtes) dans un dossier daté ; Claude
  relit les captures.
- **Avant chaque release Dev** : `local-release.ps1` lance la suite **desktop** (cible : moins de 10 min) et
  s'arrête sur un échec (`-SkipTests` pour passer outre). L'hôte de test est construit depuis le LavApollo
  voisin tel qu'il est (branche `dev`, modifications locales comprises) : c'est ce que l'utilisateur fait
  tourner. Le rapport note le commit de LavApollo utilisé. La suite Android tourne à la demande, et avant la
  release stable. La stable n'a pas de suite propre : elle sortira de la Dev (même interface v2) après des
  semaines d'essai. *(Choix de Claude.)*

## 5. Étapes

1. **Hôte de test** (LavApollo) : isolation des fichiers et ports, coupures, journaux, `LavTestHost`, fausses
   applis et Playnite ; puis la source vidéo et audio synthétique. Tests unitaires LavApollo du mode.
2. **Client de test desktop** + canal de commande + orchestrateur + scénarios 1 à 7.
3. **Client de test Android** + émulateur + scénarios Android (dont 8).
4. Plus tard : pertes réseau simulées, installeur et mise à jour dans une sandbox, multi-écran.

## Ce qui est laissé au constructeur

Noms internes, format exact des journaux, découpage des fichiers, choix des bibliothèques Python, détail du
protocole du canal (tant qu'il couvre les commandes listées).
