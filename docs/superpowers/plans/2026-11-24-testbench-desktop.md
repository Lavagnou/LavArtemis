# Banc de test (étapes 1 et 2 : hôte de test + client desktop) — plan de construction

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Claude lance la vraie appli desktop (build de test) contre un ou plusieurs LavApollo en mode « hôte de test », la pilote par un canal de commande et rejoue une suite de scénarios, sans toucher au vrai système ni aux vrais appareils.

**Architecture:** Un mode `test_host` dans LavApollo (même exécutable `build/t/sunshine.exe`) isole fichiers, ports et effets de bord, et remplace capture/audio/entrées/commandes par du synthétique ou du journal. Un build `lav_test` de LavArtemis-Qt n'accepte que ces hôtes et ouvre un canal `QLocalServer`. Un orchestrateur Python (`LavArtemis/tools/testbench`) monte les faux PC depuis des fixtures, lance le client, relaie les commandes et exécute les scénarios pytest.

**Tech Stack:** C++20 (LavApollo, Boost, FFmpeg/libx264), Qt 6.11 QML + SDL (LavArtemis-Qt, qmake, QtTest pour les événements), Python 3.13 (pytest, Pillow, ctypes).

**Spec:** `docs/superpowers/specs/2026-11-24-testbench-design.md` (sections 1, 2, 4). Android (section 3) a son propre plan, plus tard.

> 📋 **Le plan a été dépassé en cours d'exécution** : les décisions du journal d'exécution (« rulings ») l'emportent
> sur les étapes d'origine quand elles divergent. Les principales : le client de test n'accepte que **127.0.0.1 et les
> ports 40000-44999** (liste blanche ; le vrai LavApollo de ce PC écoute sur la base **45989**, pas 47989) ; le banc
> **refuse de tourner écran éteint** (code 4) ; **un scénario qui ne passe qu'au rejeu fait échouer la porte** (code 3,
> FLAKY) ; le remplaçant d'une appli en mode test est un `ping` **durable pour la commande principale** seulement ;
> l'hôte de test annonce un **mode d'écran fixe** (`test_host_display`, défaut 1920×1080 à 60 Hz) ; et les **bugs
> produit trouvés** par les scénarios (stream v2 qui ne démarrait pas, appairage, ordre de la bibliothèque Playnite,
> « Quitter la session », plantage ncrypt, PIN incorrect…) ont été **corrigés**, pas laissés en `xfail`. Le README du
> banc (`tools/testbench/README.md`) et les `CLAUDE.md` des trois dépôts décrivent l'état réel.

## Global Constraints

- Branches : `dev` dans LavApollo et LavArtemis-Qt ; LavArtemis `dev`. Commits atomiques, messages en anglais, docs en français.
- Rien du mode test n'est actif sans `test_host = enabled` (LavApollo) ni hors du build `CONFIG+=lav_test` (client) : une release ne change pas.
- L'hôte de test ne capture jamais l'écran réel, ne crée aucun écran virtuel, ne change ni sortie audio ni mode d'affichage, n'injecte rien dans Windows, n'exécute aucune commande, ne touche ni aux réglages NVIDIA/NVCP/RTSS ni à la config du vrai LavApollo (service `ApolloService`, qui reste lancé).
- Ports des hôtes de test : bases 40989, 41989, 42989… (sous 49152, loin de 47984-48010). Écoute sur 127.0.0.1 seulement.
- Encodage logiciel (H.264) par défaut dans l'hôte de test ; l'hôte n'annonce que H.264.
- Client de test : organisation Qt « LavArtemis Test » ; avant toute requête, adresse de bouclage seulement et port dans la plage des hôtes de test, **40000-44999** (liste blanche : le vrai LavApollo de ce PC écoute sur la base 45989, pas 47989, constaté en A9) ; puis refus de tout serveur sans `LavTestHost`.
- Fenêtres « visibles mais discrètes » : fenêtré, pas de focus volé, pas de capture du pointeur, son coupé, micro factice.
- Jaquettes des fixtures générées (titre sur couleur), jamais des images de vrais jeux.
- Build LavApollo : depuis PowerShell/cmd (pas MSYS bash), `C:\msys64\ucrt64\bin;C:\msys64\usr\bin` en tête du PATH, `ninja -C build/t -j 3 <cible>`. 31 tests rouges connus (CLAUDE.md de LavApollo).
- Build client : qmake + jom sous vcvars64 et Qt 6.11.1 msvc2022_64 (comme `build/pw-x64/build.bat`), dans `build/test-x64`.

## Review Focus

1. **Le vrai LavApollo tourne pendant les tests** : un hôte de test ne doit ni prendre ses ports ni écrire dans ses fichiers. → Tâche C1 : test qui empreinte (hash) `C:\Program Files\LavApollo\config` (ou le dossier du service) avant/après `up`+`down`, et vérifie que le service répond toujours sur 45989 (sa base sur ce PC).
2. **Le client de test pointé vers une vraie adresse** (une adresse du réseau local — `192.0.2.10`, TEST-NET-1, dans le scénario, pour ne viser aucune vraie machine —, 127.0.0.1:45989 — le vrai hôte de ce PC —, 127.0.0.1:47989) : refusé avant toute requête. → Tâche B2 : test unitaire du garde-fou d'adresse ; scénario C3 « ajout d'une vraie adresse refusé » qui vérifie qu'aucune connexion n'est ouverte (journal du client).
3. **Processus orphelins après un plantage** (hôtes de test encore lancés, ports pris). → Tâche C1 : `up` tue les instances d'un run précédent (fichiers PID) et vérifie que les ports sont libres ; test qui tue l'orchestrateur en plein run puis relance `up`.
4. **Codec non disponible en logiciel** (client qui demande HEVC/AV1). → Tâche A7 : l'hôte de test n'annonce que H.264 (`ServerCodecModeSupport`) ; test unitaire sur la valeur annoncée.
5. **Focus volé pendant que l'utilisateur travaille.** → Tâche C3 : le scénario de stream relève la fenêtre au premier plan avant et après, et échoue si elle a changé.

---

## Partie A — LavApollo (dépôt `LavApollo`, branche `dev`)

### Task A1: Module `test_host`, clés de config, dossier d'appdata redirigé

**Files:**
- Create: `src/test_host.h`, `src/test_host.cpp`
- Modify: `src/config.h` (struct `sunshine_t`), `src/config.cpp` (parse), `src/platform/windows/misc.cpp:131` (`appdata()`), `cmake/compile_definitions/common.cmake` (sources)
- Test: `tests/unit/test_test_host.cpp`

**Interfaces:**
- Produces:
  - config `sunshine_t` : `bool test_host` (clé `test_host`, valeurs `enabled`/`disabled`), `std::string test_host_mac` (`test_host_mac`, vide = vraie MAC), `std::string test_host_appversion` (vide = la vraie), `bool test_host_this_pc` (`test_host_this_pc`).
  - `namespace test_host { bool enabled(); std::filesystem::path dir(); void log(std::string_view stream, const nlohmann::json &event); }` — `dir()` = `appdata()/testhost`, `log("input"|"commands"|"events", e)` ajoute une ligne JSON (avec `"t"` en ms depuis le démarrage) à `dir()/<stream>.jsonl`, sous mutex.
  - `appdata()` renvoie `LAVAPOLLO_APPDATA` s'il est posé.
- [ ] Test : `TEST(TestHost, LogsOneJsonLinePerEvent)` (deux `log("input", …)` → deux lignes valides, champs conservés) ; `TEST(TestHost, DisabledByDefault)` ; `TEST(TestHost, AppdataFollowsEnvironment)` (`_putenv_s("LAVAPOLLO_APPDATA", tmp)` → `platf::appdata() == tmp`).
- [ ] Lancer : `ninja -C build/t -j 3 test_sunshine` puis `build\t\tests\test_sunshine.exe --gtest_filter=TestHost.*` → échec (symboles absents).
- [ ] Implémenter ; mode test forcé dès `test_host` : `enable_discovery=false`, `system_tray=false`, UPnP off, `update_auto_check=false`, `address_family=ipv4`, `mic_set_default=false`, `frame_limiter_enable=false`, `server_cmd` conservé (journalisé en A4), `encoder=software`, `capture=synthetic`.
- [ ] Relancer → PASS. Commit `Test host mode: config keys, appdata override, JSONL logs`.

### Task A2: Écoute sur 127.0.0.1 en mode test

**Files:**
- Modify: `src/network.cpp` / `network.h` (nouvelle `bind_address_string(af_e)`), `src/nvhttp.cpp:2170,2185`, `src/confighttp.cpp:2897`, `src/network.cpp:165` (ENet), `src/rtsp.cpp:422`, `src/stream.cpp:2134,2156`
- Test: `tests/unit/test_network.cpp`

**Interfaces:**
- Produces: `std::string_view net::bind_address_string(af_e af)` → `"127.0.0.1"` si `test_host::enabled()`, sinon `af_to_any_address_string(af)`. Tous les binds (HTTP/HTTPS/web/ENet/RTSP/UDP vidéo et audio) passent par elle (UDP/RTSP : `ip::make_address(bind_address_string(af))` au lieu de l'adresse « any » du protocole).
- [ ] Test : `TEST(Network, BindsLoopbackInTestHostMode)` / `…AnyAddressOtherwise`.
- [ ] Échec, implémenter, PASS. Commit `Test host mode: listen on 127.0.0.1 only`.

### Task A3: Couper les effets de bord système

**Files:**
- Modify: `src/main.cpp:211-233` (display_device init + reprise, nvprefs, NVCP/RTSS), `src/main.cpp:376-410` (sonde avec écran virtuel temporaire), `src/process.cpp:2142-2150` (`initVDisplayDriver`), `src/process.cpp:112-170,342-677,1164-1190` (écrans virtuels, Remote Monitor, changements de mode, retour), `src/video.cpp:51-75` (écran virtuel « aucun écran actif »), `src/platform/windows/misc.cpp:1194-1300` (`streaming_will_start`), `src/platform/windows/display_base.cpp:224,534` (`SetThreadExecutionState`), `src/stream.cpp:2554` (limiteur)
- Test: `tests/unit/test_test_host.cpp`

**Interfaces:**
- Consumes: `test_host::enabled()`.
- Produces: chaque site est gardé par `if (test_host::enabled()) { BOOST_LOG(info) << "test host: skipping …"; … }` ; `proc_t::execute` n'appelle jamais `createVirtualDisplay`/`changeDisplaySettings`/`configure_display` en mode test.
- [ ] Test : `TEST(TestHost, ForcedConfigValues)` (après `config::parse` d'une config avec `test_host=enabled` : discovery/tray/upnp/update/mic/limiteur off, `encoder == "software"`, `capture == "synthetic"`, `address_family == "ipv4"`).
- [ ] Échec, implémenter, PASS. Vérification manuelle : lancer l'instance de A9 et lire son journal (une ligne « test host: skipping » par site, aucune ligne SudoVDA). Commit `Test host mode: no virtual display, NVIDIA profile or system tweaks`.

### Task A4: Commandes, Playnite et presse-papiers journalisés

**Files:**
- Modify: `src/platform/windows/misc.cpp:1041` (`run_command`), `src/platform/windows/misc.cpp:1993-2074` (presse-papiers), `src/playnite.cpp:214-245` (lancer, arrêter)
- Test: `tests/unit/test_test_host.cpp`, `tests/unit/test_playnite.cpp`

**Interfaces:**
- Produces:
  - `run_command` en mode test : `test_host::log("commands", {cmd, elevated, interactive, working_dir})` puis, pour la **commande principale d'une appli** (appel avec groupe de processus), lance un remplaçant durable `ping` (ni élevé ni interactif) que la fin du groupe arrête : l'appli reste « en cours » sans dépendre du détachement automatique ; préparation, annulation, détachées, d'état et de serveur reçoivent un `exit 0` immédiat (elles sont attendues).
  - Presse-papiers : écriture → `log("events", {"clipboard_set": texte})` + mémoire de l'instance ; lecture → la mémoire.
  - Playnite : lancer un jeu → `log("events", {"playnite_launch": id})` et écriture du `state.json` « jeu lancé » de la bibliothèque factice ; demande d'arrêt → `state.json` « aucun jeu ».
- [ ] Tests : `TEST(TestHost, RunCommandIsLoggedNotRun)` (commande `cmd /c echo x > fichier` → fichier absent, ligne dans `commands.jsonl`) ; `TEST(Playnite, TestHostLaunchWritesRunningState)`.
- [ ] Échec, implémenter, PASS. Commit `Test host mode: commands, clipboard and Playnite are logged, not run`.

### Task A5: Entrées journalisées

**Files:**
- Modify: `src/platform/windows/input.cpp:537-1784` (`input()`, `abs_mouse`, `move_mouse`, `button_mouse`, `scroll`, `hscroll`, `keyboard_update`, `touch_update`, `pen_update`, `unicode`, `alloc_gamepad`, `free_gamepad`, `gamepad_update`, `gamepad_touch`, `gamepad_motion`, `gamepad_battery`)
- Test: `tests/unit/test_test_host.cpp`

**Interfaces:**
- Produces: en mode test, `input()` ne sonde ni VHF ni ViGEm ; chaque entrée écrit `log("input", {"type": "<fonction>", …paramètres})` et retourne sans `SendInput`. Exemple attendu pour une touche : `{"type":"key","vk":192,"down":true}` (VK_OEM_3, le ² du bouton vocal).
- [ ] Test : `TEST(TestHost, KeyboardIsLoggedNotInjected)` (`platf::keyboard_update(input, 0xC0, false, 0)` → ligne `{"type":"key","vk":192,"down":true}`).
- [ ] Échec, implémenter, PASS. Commit `Test host mode: input is logged, never sent to Windows`.

### Task A6: Audio synthétique

**Files:**
- Create: `src/platform/windows/audio_synthetic.cpp`
- Modify: `src/platform/windows/audio.cpp:1394-1411` (fabrique)
- Test: `tests/unit/test_audio.cpp`

**Interfaces:**
- Produces: en mode test, `platf::audio_control()` renvoie un `audio_control_t` dont `set_sink` ne fait rien, `is_sink_available` vrai, `sink_info()` un puits factice, et `microphone(...)` un `mic_t` dont `sample(std::vector<float>&)` remplit une sinusoïde 440 Hz (amplitude 0,1) au rythme du temps réel ; aucune installation de pilote.
  - Micro reçu (`mic_passthrough.cpp`) : `MicPassthroughState` vaut `ready`, mais les trames décodées ne vont dans aucun périphérique (pas d'installation, pas de défaut changé) : `log("events", {"mic_frames": n})` toutes les secondes.
- [ ] Tests : `TEST(Audio, TestHostToneHasExpectedFrequency)` (deux tampons consécutifs : continuité de phase, passage par zéro ≈ 440 Hz) ; `TEST(MicPassthrough, TestHostCountsFramesWithoutDevice)`.
- [ ] Échec, implémenter, PASS. Commit `Test host mode: synthetic audio, no sink change`.

### Task A7: Source vidéo synthétique et codecs annoncés

**Files:**
- Create: `src/benchmark_scene_formula.h` (formule CPU de la scène + écriture des bits du numéro d'image), `src/platform/windows/display_synthetic.cpp`
- Modify: `src/platform/windows/display_base.cpp:996-1068` (`display()`, `display_names()`), `tools/benchmark_scene.cpp` (le marquage du numéro d'image cite le même schéma de bits), `src/nvhttp.cpp` (`ServerCodecModeSupport` en mode test)
- Test: `tests/unit/test_video.cpp`

**Interfaces:**
- Produces:
  - `void benchmark_scene::render_bgra(std::uint8_t *dst, int width, int height, int row_pitch, std::uint64_t frame)` : image qui bouge avec `frame`, et `frame` écrit en 32 cases noires/blanches de 8×8 px en bas à gauche ; `std::uint64_t benchmark_scene::read_frame_number(const std::uint8_t *bgra, int width, int height, int row_pitch)` l'inverse (utilisé par le test et réimplémenté en Python en C2).
  - `class display_synthetic_t : public platf::display_t` (mémoire système, `mem_type_e::system`) : `capture()` cadencé sur `config.framerate`, `alloc_img()` BGRA `width*height*4`, `dummy_img()` noir, `frame_timestamp` posé ; modèle des images système : `display_ram_t` (`display_ram.cpp`).
  - `platf::display(...)` renvoie `display_synthetic_t` quand `config::video.capture == "synthetic"` ; `display_names()` renvoie `{"synthetic"}`.
  - `ServerCodecModeSupport` en mode test : H.264 seulement.
- [ ] Tests : `TEST(Video, SyntheticFrameNumberRoundTrips)` (`render_bgra` puis `read_frame_number` pour 0, 1, 255, 1 000 000) ; `TEST(Video, SyntheticFramesDifferOverTime)` ; `TEST(Video, TestHostAdvertisesH264Only)`.
- [ ] Échec, implémenter, PASS. Commit `Test host mode: synthetic video source, H.264 only`.

### Task A8: `serverinfo` et appairage en mode test

**Files:**
- Modify: `src/nvhttp.cpp` (réponse `serverinfo`), `src/confighttp.cpp` (route `POST /api/test/pin`)
- Test: `tests/unit/test_http_pairing.cpp`

**Interfaces:**
- Produces:
  - `serverinfo` en mode test : `<LavTestHost>1</LavTestHost>`, `<LavTestHostThisPc>1</LavTestHostThisPc>` si `test_host_this_pc`, `mac` = `test_host_mac` s'il est posé, `appversion` = `test_host_appversion` s'il est posé. Le nom vient de la clé existante `sunshine_name`.
  - `POST /api/test/pin` `{"pin","name"}` → `nvhttp::pin(pin, name)` ; n'existe qu'en mode test, sans authentification, depuis 127.0.0.1 seulement.
- [ ] Tests : `TEST(HttpPairing, TestHostServerinfoMarks)` ; `TEST(HttpPairing, TestPinRouteOnlyInTestHostMode)`.
- [ ] Échec, implémenter, PASS. Commit `Test host mode: serverinfo marks and a PIN route for the testbench`.

### Task A9: Lancement manuel et documentation

**Files:**
- Modify: `CLAUDE.md` de LavApollo (section « Hôte de test » : clés, ce qui est coupé, journaux, ports, `LAVAPOLLO_APPDATA`, `ProgramData`, comment le lancer à la main)

- [ ] Lancer une instance à la main depuis PowerShell : `$env:LAVAPOLLO_APPDATA=<tmp>\pc1; $env:ProgramData=<tmp>\pc1\pd; cd build\t; .\sunshine.exe <tmp>\pc1\sunshine.conf` (config : `test_host=enabled`, `port=40989`, `sunshine_name=Test-PC-1`) ; vérifier `curl http://127.0.0.1:40989/serverinfo` (contient `LavTestHost`), `netstat -ano` (écoute sur 127.0.0.1 seulement), journal (aucun SudoVDA), et que `ApolloService` répond toujours sur 45989.
- [ ] Écrire la section, commit `Docs: test host mode`.

---

## Partie B — client desktop (dépôt `LavArtemis-Qt`, branche `dev`)

### Task B1: Build `lav_test`

**Files:**
- Modify: `app/app.pro` (bloc `lav_test` : `DEFINES += LAV_TEST_BUILD`, `QT += testlib`, sources de `app/testctl/`, `TARGET = LavArtemisTest`), `app/main.cpp:460-466` (organisation « LavArtemis Test »), `app/backend/computermanager.cpp:371-390` (pas de mDNS), `app/backend/autoupdatechecker.cpp` (pas de vérification)
- Create: `scripts/build-test.bat` (vcvars64 + Qt + `qmake CONFIG+=lav_test` + `jom release` dans `build/test-x64`, puis `windeployqt` vers `build/test-x64/deploy`)

**Interfaces:**
- Produces: `build/test-x64/deploy/LavArtemisTest.exe` ; `LAV_TEST_BUILD` implique le canal Dev (interface v2).
- [ ] `scripts\build-test.bat` → l'exe existe ; le lancer : registre `HKCU\Software\LavArtemis Test` créé, rien d'écrit sous `LavArtemis Dev`.
- [ ] Commit `Test build (CONFIG+=lav_test): own settings, no discovery, no updater`.

### Task B2: Garde-fous et reconnaissance des hôtes de test

**Files:**
- Modify: `app/backend/nvhttp.cpp` (avant toute requête), `app/backend/nvcomputer.{h,cpp}` (`isTestHost`, `testHostThisPc`, exemption de `isThisPc`), `app/backend/computermanager.cpp` (ajout manuel refusé avec message)
- Test: `tests/testguard/` (nouvelle suite opt-in, comme `tests/*`)

**Interfaces:**
- Produces:
  - `bool NvHTTP::isAllowedTestAddress(const QString& host, uint16_t port)` (statique, build test seulement) : vrai si bouclage (`127.0.0.1`, `::1`, `localhost`) et port dans 40000-44999 (liste blanche des hôtes de test ; jamais un port déduit du vrai hôte).
  - `NvComputer::isTestHost` (`LavTestHost`), et dans le build test `isThisPc = testHostThisPc` (balise `LavTestHostThisPc`) au lieu de `isThisMachine()`.
  - Un serveur sans `LavTestHost` est mis hors ligne avec la raison « Pas un hôte de test » dans le journal du client.
- [ ] Test : `isAllowedTestAddress("127.0.0.1", 40989)` vrai ; `("127.0.0.1", 47989)` faux ; `("127.0.0.1", 45989)` faux (vrai hôte de ce PC) ; `("192.168.1.231", 40989)` faux ; `("localhost", 41984)` vrai ; `("127.0.0.1", 39999)` et `("127.0.0.1", 45000)` faux.
- [ ] Échec, implémenter, PASS. Commit `Test build: talk to loopback test hosts only`.

### Task B3: Mode discret

**Files:**
- Modify: `app/streaming/session.cpp` (mode fenêtré forcé, pas de `SDL_RaiseWindow`/grab/relative mouse), `app/main.cpp` (`SDL_AUDIODRIVER=dummy` avant l'init SDL), `app/streaming/audio/micstreamer.cpp` (tonalité au lieu du vrai micro), `app/gui/main.qml` (pas de `raise()`/`requestActivate()` au démarrage)

**Interfaces:**
- Produces: dans `LAV_TEST_BUILD`, un stream s'ouvre en fenêtre, sans focus ni capture du pointeur, sans son, et le micro envoie une tonalité.
- [ ] Vérification en C3 (scénario 5 : fenêtre au premier plan inchangée, aucun appareil audio ouvert). Commit `Test build: windowed, no focus or mouse capture, muted, fake microphone`.

### Task B4: Canal de commande

**Files:**
- Create: `app/testctl/testcontrol.h`, `app/testctl/testcontrol.cpp`
- Modify: `app/main.cpp` (démarrage du serveur dans `LAV_TEST_BUILD`)
- Test: `tests/testguard/` (protocole : parse d'une commande, `tree` sur une petite scène QML)

**Interfaces:**
- Produces: `QLocalServer` `lavartemis-test` ; une requête JSON par ligne `{"id":n,"cmd":…}` → une réponse `{"id":n,"ok":bool,…}`. Commandes :
  - `tree` → `[{type, objectName, text, x, y, w, h, focus, enabled}]` des `QQuickItem` visibles (opacité > 0) de toutes les fenêtres Qt ;
  - `click {text|objectName|x,y}` (premier élément visible correspondant, clic au centre par `QTest::mouseClick` sur la `QQuickWindow`), `key {key, modifiers}`, `text {s}`, `wheel {dx,dy}` ;
  - pendant un stream : `key`/`text`/`mouse` passent par `SDL_PushEvent` vers la fenêtre SDL, `gamepad {button|axis, value}` par une manette virtuelle SDL créée à la demande ;
  - `screenshot {path}` (fenêtre Qt : `QQuickWindow::grabWindow`) ; `windows` → liste `{title, hwnd, kind: "qt"|"stream"}` (l'orchestrateur capture la fenêtre du stream par `PrintWindow`) ;
  - `state` → `{page, computers:[{name, online, paired, testHost, thisPc, apps}], session:{active, frames, decoded, dropped}}` ;
  - `setting {get|set|reset, key, value}` (StreamingPreferences + ArtemisSettings), `reset` (efface l'organisation « LavArtemis Test »), `quit`.
- [ ] Test : `tree` sur une scène avec un `Text { text: "Bonjour" }` renvoie un élément `text == "Bonjour"` ; `click {"text":"Bonjour"}` déclenche le `MouseArea` parent.
- [ ] Échec, implémenter, PASS. Commit `Test build: local command channel for the testbench`.

### Task B5: Documentation

**Files:**
- Modify: `CLAUDE.md` de LavArtemis-Qt (section « Build de test » : option, garde-fous, mode discret, canal et ses commandes, `scripts/build-test.bat`)
- [ ] Commit `Docs: test build and command channel`.

---

## Partie C — orchestrateur et scénarios (dépôt `LavArtemis`, branche `dev`)

### Task C1: Fixtures et hôtes de test

**Files:**
- Create: `tools/testbench/testbench.py` (CLI), `tools/testbench/hosts.py`, `tools/testbench/fixtures/` (`atlas-test/`, `cynisca-test/`, `offline-test/`, `old-version-test/`, `this-pc-test/` : `pc.json`, `apps.json`, `playnite/library.json`), `tools/testbench/covers.py` (jaquettes générées, Pillow), `tools/testbench/requirements.txt`
- Test: `tools/testbench/tests/test_hosts.py`

**Interfaces:**
- Produces:
  - `pc.json` : `{name, port, mac?, appversion?, playnite_running, running_app?, this_pc?}` ; `offline-test` n'a pas d'instance (port fermé).
  - `hosts.up(fixtures, workdir) -> list[Host]` (`Host`: `name, port, pid, dir`) : construit `sunshine` si l'exe est plus vieux que les sources (`ninja -C build/t -j 3 sunshine`), écrit `sunshine.conf` + `apps.json` + `ProgramData/LavApollo/playnite/` + jaquettes, lance chaque instance (`LAVAPOLLO_APPDATA`, `ProgramData`, cwd `build/t`), attend `/serverinfo`, écrit les PID ; `hosts.down(workdir)` ; au début de `up`, tue les instances d'un run précédent et vérifie les ports libres.
  - `hosts.pin(host, pin, name)` → `POST https://127.0.0.1:<base+1>/api/test/pin` (certificat non vérifié ; jamais `localhost`/`::1`, refusés).
  - CLI : `testbench up [fixtures…]`, `testbench down`, `testbench logs <pc> [input|commands|events]`.
- [ ] Tests : `up(["atlas-test"])` → `serverinfo` contient `LavTestHost` et `Test-Atlas` ; empreinte du dossier de config du vrai LavApollo identique avant/après ; `ApolloService` répond sur 45989 ; un `up` après un run tué net réussit.
- [ ] Échec, implémenter, PASS. Commit `Testbench: fixtures and sandboxed test hosts`.

### Task C2: Client, canal et captures

**Files:**
- Create: `tools/testbench/client.py`, `tools/testbench/frames.py`
- Test: `tools/testbench/tests/test_client.py`, `tools/testbench/tests/test_frames.py`

**Interfaces:**
- Produces:
  - `Client.start(workdir)` (construit par `LavArtemis-Qt\scripts\build-test.bat` si besoin, lance l'exe, se connecte au canal), `client.cmd(name, **args) -> dict`, `client.click(text)`, `client.wait_for(predicate_on_state, timeout)`, `client.screenshot(path)` (fenêtre Qt par le canal, fenêtre du stream par `PrintWindow` via ctypes), `client.stop()`.
  - `frames.read_frame_number(png_path) -> int` (même schéma de bits que A7).
  - CLI : `testbench client <cmd> [json]`, `testbench shot <path>`.
- [ ] Tests : `read_frame_number` sur des PNG produits par `render_bgra` (exportés par un petit exécutable de test LavApollo, ou générés en Python avec le même schéma) ; `Client.start` puis `state` → `page` non vide.
- [ ] Échec, implémenter, PASS. Commit `Testbench: drive the test client and capture its windows`.

### Task C3: Scénarios desktop et rapport

**Files:**
- Create: `tools/testbench/conftest.py` (fixtures pytest : `workdir` temporaire, `hosts`, `client`, rapport), `tools/testbench/scenarios/test_01_pairing.py` … `test_07_this_pc.py`, `tools/testbench/scenarios/test_08_guards.py`, `tools/testbench/report.py`

**Interfaces:**
- Consumes: C1, C2.
- Produces: `testbench run [--suite desktop] [-k …]` → pytest ; rapport `testbench-reports/<date>/` (résultat par scénario, captures, journaux des hôtes, commit de LavApollo et de LavArtemis-Qt utilisés).
- Scénarios (assertions principales) :
  1. Ajout de `127.0.0.1:40989`, appairage : le PIN lu dans l'arbre de l'écran d'appairage est envoyé par `hosts.pin` → `state.computers[0].paired`. Lien `art://…&passphrase=` : appairé sans PIN.
  2. Accueil : cartes « Bureau » et « Playnite Fullscreen », Récents dans l'ordre de `library.json` ; bibliothèque : jaquettes, « Masquer » puis bibliothèque qui le réaffiche.
  3. Mes PC : `offline-test` « éteint », `old-version-test` → message « La version du logiciel hôte… (x.y.z.w) » au clic, renommer, oublier.
  4. Réglages : chaque page ouverte en normal et en mode expert (captures), une écriture relue après redémarrage du client.
  5. Lancer « Bureau » : `session.decoded` croît ; deux captures du stream → numéros d'image croissants ; menu en jeu → « Envoyer des touches » → la combinaison attendue dans `input.jsonl` ; micro activé dans les réglages → `mic_frames` croît dans `events.jsonl` ; quitter la session → `commands.jsonl`/état de l'hôte sans appli ; premier plan inchangé avant/après.
  6. Jeu Playnite pendant que Playnite tourne : `resume`, `events.jsonl` contient `playnite_launch` ; « Quitter le jeu ».
  7. `this-pc-test` (`test_host_this_pc`) : lancer → message « Impossible de streamer … depuis lui-même », aucune session.
  8. Garde-fous : ajout de `192.0.2.10` (TEST-NET-1, pour un PC du réseau local), `127.0.0.1:45989` et `127.0.0.1:47989` refusés, aucune connexion dans le journal du client.
- [ ] Écrire les scénarios un par un, chacun lancé et vert avant le suivant (`testbench run -k <nom>`), en relisant les captures. Commit par scénario ou par paire.

### Task C4: Porte de release et documentation

**Files:**
- Modify: `scripts/local-release.ps1` (étape « Tests » avant le build : `python tools\testbench\testbench.py run --suite desktop`, arrêt sur échec, `-SkipTests`), `CLAUDE.md` de LavArtemis (section « Banc de test »), `tools/testbench/README.md`
- [ ] `scripts\local-release.ps1 -NoPublish -SkipAndroid -SkipWindows` lance la suite et s'arrête si un scénario échoue (vérifié en cassant volontairement une assertion, puis en la rétablissant).
- [ ] Commit `Release: run the desktop testbench before building`.
