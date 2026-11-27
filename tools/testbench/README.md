# Banc de test desktop

De vrais clients contre de vrais hôtes, sans toucher au système : des LavApollo en mode « hôte de test »
(faux PC isolés) et le build de test de LavArtemis-Qt (`LavArtemisTest.exe`), piloté par son canal de commande.
Windows seulement. Spec : `docs/superpowers/specs/2026-11-24-testbench-design.md`.

## Lancer

```powershell
pip install -r tools\testbench\requirements.txt          # pytest, Pillow (une fois)
python tools\testbench\testbench.py run                    # toute la suite desktop
python tools\testbench\testbench.py run -k stream          # un scénario (nom ou mot du nom)
python tools\testbench\testbench.py run --rerun-failed-once  # porte de release : un échec est rejoué une fois
python -m pytest tools\testbench\tests -q                  # tests du banc lui-même
```

Il faut les dépôts `LavApollo` et `LavArtemis-Qt` à côté de celui-ci (ou `LAV_TESTBENCH_LAVAPOLLO` /
`LAV_TESTBENCH_QT`). Le banc reconstruit ce qui est périmé : `sunshine` du build `build/t` de LavApollo
(`ninja -j <LAV_TESTBENCH_JOBS, défaut 1>`) et le client (`scripts\build-test.bat`, `LAV_TEST_JOBS`, défaut 2).
Un build tué faute de mémoire n'est pas relancé : libérer de la mémoire et relancer.

## Porte de release

`scripts\local-release.ps1` lance `python tools\testbench\testbench.py run --suite desktop --rerun-failed-once` après
ses contrôles (commit épinglé, arbre propre) et **avant tout build** ; tout code non nul arrête la release (1 échec,
3 FLAKY, 4 écran éteint, autre = erreur du runner) et indique le dossier du rapport. `-SkipTests` la saute, avec un
avertissement. Le client de test est reconstruit depuis le clone LavArtemis-Qt épinglé s'il est périmé.

**Écran éteint = code 4.** Avant de lancer quoi que ce soit, `run` lit l'état de l'écran (`display.py` : le réglage
d'alimentation `GUID_CONSOLE_DISPLAY_STATE` n'est pas interrogeable, il est livré en `WM_POWERBROADCAST` à une
fenêtre inscrite par `RegisterPowerSettingNotification`, et Windows envoie la valeur courante dès l'inscription ;
une fenêtre invisible, 1,5 s d'attente). Éteint : message « écran éteint : le banc de test a besoin de l'écran allumé »
et sortie 4 ; allumé, tamisé ou état inconnu (pas de réponse) : on lance. Le banc ne rallume jamais l'écran.
**L'écran peut aussi s'éteindre en cours de route** (veille après inactivité) : après chaque scénario, `conftest.py`
relit l'état ; éteint, il écrit `display-off` dans le rapport et arrête la session, et `run` sort en **4** sans rejouer
ni juger (sinon tous les scénarios suivants échouaient pour rien, 52 échecs le 2026-11-27).
**Pendant `run`, l'affichage ne se met pas en veille** (autorisé par l'utilisateur le 2026-11-27, pour le banc seulement) :
`display.kept_awake()` appelle `SetThreadExecutionState(ES_CONTINUOUS | ES_DISPLAY_REQUIRED)` après le contrôle de départ
(un affichage déjà éteint n'est jamais rallumé) et le rend à la fin, quoi qu'il arrive. C'est l'état d'alimentation de
l'affichage de Windows qui compte, pas un écran physique : il s'éteint aussi avec un dongle HDMI.

**Vérifier la porte sans GUI** : `LAV_TESTBENCH_SELFTEST=pass|fail|flaky`, honoré par `run` seulement : il remplace
les scénarios par un scénario interne (`selftest/test_gate.py`, hors `scenarios/`), sans hôte ni client ni contrôle de
l'écran, et donne 0 / 1 / 3 (le rejeu fait passer `flaky`). Exemple :
`$env:LAV_TESTBENCH_SELFTEST='flaky'; scripts\local-release.ps1 -NotesFile n.md -NoPublish -SkipAndroid -SkipWindows; Remove-Item Env:LAV_TESTBENCH_SELFTEST`.
`local-release.ps1` **refuse** (sans `-NoPublish`) tant que la variable est posée, et l'annonce quand elle sert :
un reste de `pass` dans l'environnement ne doit jamais laisser passer une vraie release.

Limites de la lecture de l'écran : « tamisé » compte comme allumé, et l'écran peut s'éteindre juste après le
contrôle ; le run apparaît alors en échec ou FLAKY (à relancer écran allumé). État inconnu (Windows ne répond pas) :
une ligne sur la sortie d'erreur, et le run part.

## Rapport

`tools/testbench/testbench-reports/<date>_<heure>/` (hors git : captures et journaux peuvent porter des noms de
machine) : `report.md` / `report.json` (résultat de chaque scénario, durée, raison d'un échec attendu, commits de
LavArtemis, LavApollo et LavArtemis-Qt utilisés, modifications non commitées signalées), `captures/<scénario>/`
(PNG des fenêtres, celles du stream par PrintWindow), `logs/<scénario>/` (`sunshine.log` et `testhost/*.jsonl`
de chaque hôte, le journal de **chaque** lancement du client — `client-1-reset.log` pour celui qui efface
l'organisation, `client-N-args.log` pour un lancement avec un lien —, notes, chronologie du premier plan et
`visibility.txt`, les fenêtres du client vues par le contrôle de visibilité).
`--report-dir` choisit un autre dossier.

La colonne *Note* de `report.md` signale toute **reprise** (`RETRY` : une lecture d'image refaite, un clic qui attendait
la fermeture d'une popup, un PIN redonné à l'hôte qui ne l'attendait pas encore) et tout passage du premier plan à
**aucune fenêtre** ou à une **boîte de plantage** (WerFault). `--rerun-failed-once` rejoue une fois chaque scénario
en échec, dans le même rapport (tentative 2, `captures/` et `logs/<scénario>-rerun`) ; un scénario qui ne passe
qu'au rejeu est marqué **FLAKE**, jamais masqué.

**Code de sortie de `run`** (la porte de release s'arrête sur tout code non nul et dit lequel) :

| Code | Sens |
|---|---|
| 0 | chaque scénario a passé **du premier coup** (un `xfail` attendu compte comme passé) |
| 1 | un scénario échoue ou a été **ignoré** (skip : il n'a rien prouvé), après son rejeu s'il y en a eu un ; ou pytest a fini avec un code non nul sans qu'aucun scénario n'échoue (collecte, interruption, erreur d'usage) |
| 3 | **FLAKY** : plus rien n'échoue, mais un scénario n'a passé qu'au rejeu : une instabilité à regarder, pas à livrer |
| 4 | **écran éteint** : rien n'a tourné (voir ci-dessous) ; `run` refuse de démarrer, sans jamais allumer l'écran |

En fin de run, un bloc récapitulatif sur la sortie standard liste `FAIL: <scénario>` / `FLAKE: <scénario>` et
`RESULT: PASSED|FAILED|FLAKY (exit n)`, en plus de `report.md`.

Aucun bug produit connu n'est ouvert : chaque scénario doit passer. Un bug connu non corrigé passerait son scénario
en *expected failure* (`pytest.xfail`) sur sa **signature exacte** seulement, avec sa cause ; corrigé, le `xfail` part.

⚠️ **Écran allumé.** Quand Windows éteint l'écran (inactivité, 15 min ici), Qt Quick ne dessine plus la fenêtre du
client de test : plus de mise en page (`polish`) : les cartes de l'accueil restent toutes au même coin (vérifié pendant
plusieurs secondes), et les scénarios qui cliquent échouent (« covered », « no stream », ordre des
cartes) sans que l'appli y soit pour rien (vu le 2026-11-25 : la suite passe écran allumé ; écran éteint, la moitié des scénarios échouent ainsi).
Lancer la suite écran allumé ; une capture (`screenshot`) force un rendu, ce qui peut masquer le problème.

## Ce que le banc touche, et ce qu'il ne touche jamais

- **Écrit** : `%TEMP%\lavtestbench-run\<scénario>\` (un faux PC par dossier : sa config, ses applis, sa
  bibliothèque Playnite et ses jaquettes générées, ses journaux, son `ProgramData`), l'organisation Qt
  **« LavArtemis Test »** (registre `HKCU\Software\LavArtemis Test`, dossiers `LavArtemis Test`), effacée au début
  de chaque scénario, et le dossier du rapport.
- **Ports** : 127.0.0.1, 40000-44999. Le vrai LavApollo de ce PC (base 45989) n'est ni contacté, ni arrêté, ni
  configuré ; le client de test refuse toute autre adresse avant la moindre requête (scénario 8).
- **Jamais** : les réglages de « LavArtemis » / « LavArtemis Dev », le service `ApolloService` et sa config, un
  vrai hôte, une installation, un gestionnaire de liens `art://` (le lien passe en argument de l'exe), le vrai
  pointeur ou le vrai clavier (seules les entrées injectées par le canal atteignent l'hôte), le son, le vrai micro.
- **Processus** : seuls ceux que le banc a lancés sont arrêtés (chemin de l'exe vérifié), à la fin de chaque
  scénario, échec compris ; un run interrompu est nettoyé au suivant (fichiers de PID).
- **Fenêtres jamais vues** : la personne qui se sert du PC ne voit jamais une fenêtre du client de test et n'en est
  jamais gênée. Le client voile chacune de ses fenêtres (`DWMWA_CLOAK`) dès sa création, avant tout affichage :
  DWM n'en dessine rien et la souris passe au travers ; elles restent au bas de l'ordre Z, jamais topmost, jamais
  activées (LavArtemis-Qt, `testctl/hiddenwindows.h`, « Mode discret » de son CLAUDE.md). Pour Qt et SDL elles
  restent affichées à leur place : mise en page, animations, rendu du stream et captures (`grabWindow`,
  `PrintWindow`) marchent comme avant. Un second crochet `SetWinEventHook` (`VisibilityWatch` de `conftest.py`)
  regarde chaque fenêtre du client quand elle s'affiche, bouge ou change de place dans l'ordre Z, puis toutes une
  dernière fois avant l'arrêt du client : une fenêtre sur un écran et non voilée, ou topmost, fait échouer le
  scénario (`logs/<scénario>/visibility.txt`). **`LAV_TEST_VISIBLE=1`** dans l'environnement du banc (le client
  en hérite) remet les fenêtres à l'écran pour regarder un scénario à l'œil (toujours sans focus) ; ce contrôle ne
  vérifie alors rien. À ne pas laisser posé pendant que quelqu'un travaille sur le PC.
- **Premier plan** : les fenêtres ne prennent jamais le focus. Un crochet
  `SetWinEventHook(EVENT_SYSTEM_FOREGROUND)` (hors contexte, sur un thread avec sa boucle de messages) reçoit les
  changements de premier plan que Windows annonce (un passage à « aucune fenêtre » n'en produit pas toujours un : le
  premier plan est donc aussi relu à la fin du scénario) : un scénario échoue si l'un d'eux va à une fenêtre du client
  ou d'un hôte de test (reconnue au chemin de l'exe, quel que soit le lancement). On peut continuer à travailler
  pendant un run ; un changement vers une de ses propres fenêtres est noté dans `logs/<scénario>/foreground.txt`, pas
  compté contre le client ; aucune fenêtre au premier plan (pendant ou à la fin) ou WerFault est signalé dans
  `report.md`.
- **Restes** : à la fin de chaque scénario, tout `sunshine.exe` de test dont la ligne de commande vise le dossier du
  scénario et tout `LavArtemisTest.exe` de ce build encore lancés sont tués **et** font échouer le scénario (cause à
  chercher) ; un `LavArtemisTest.exe` d'un autre build ou illisible, ou un PID enregistré vivant mais méconnaissable,
  n'est jamais tué et fait aussi échouer. Le client meurt pendant le
  scénario : échec, avec son code de sortie. Deux clients à la fois : refusé au lancement.

## Captures hors écran (`shots`)

`python tools\testbench\testbench.py shots <dossier> [--screens home,home-join,home-session,options,details,library,segue,ingame,hosts,settings,bench,bench-results,mapper,update]
[--themes …] [--inputs mouse,keys] [--covers <dossier>] [--keep] [--no-build]` : le client de test sur la plateforme Qt
`offscreen` (rien à l'écran, marche écran éteint), PNG 1600 × 900 par écran × thème × clair / sombre × entrée, à comparer
aux rendus du prototype. **Jamais une porte de release.** Les effets (`MultiEffect` : flou, masques, lueurs) ne sont pas
dessinés par le rendu logiciel. `ingame` capture la fenêtre transparente du menu en jeu posée sur un fond (l'image du
faux PC est une mire), puis, à la souris, son dialogue « Plus » (`ingame/plus-<thème>-<mode>.png`). `segue` lance « Reprendre » (ou la première carte) une fois par thème × mode et capture l'écran de connexion avant que le stream s'ouvre. `bench-results` lance un benchmark Rapide raccourci (`LAV_TEST_BENCHMARK`) : il ferme ce qui tourne sur le faux PC, à placer après les écrans qui ont besoin de la session. `mapper` branche le joystick virtuel du client de test. `update` trouve une version (`LAV_TEST_UPDATE`) et garde « Mise à jour » dans l'en-tête : à placer en dernier.

`--covers <dossier>` : les faux PC prennent **les données du prototype** au lieu des fixtures (PC, jeux, heures, dernière
partie, installé, jeu masqué, session « depuis 42 min » de Playnite Fullscreen + un jeu), décrites par
`<dossier>/shots-data.json` (format : `covers_fixtures()` de `shots.py`), avec les images de ce dossier comme jaquettes,
fonds et images d'applis. Ce sont de vraies images de jeux : **le dossier et son fichier restent hors git** ; rien n'en est
écrit ailleurs que dans le dossier de travail des captures.

## Scénarios (`scenarios/`)

| # | Fichier | Vérifie |
|---|---|---|
| 1 | `test_01_pairing.py` | ajout par adresse + PIN lu à l'écran donné à l'hôte ; lien `art://…&passphrase=` (OTP) sans PIN ; aucun secret d'appairage (sel, hash OTP, défis, phrase de passe) dans le journal du client |
| 2 | `test_02_home.py` | (V8) cartes Bureau puis Playnite Fullscreen, jaquettes des « Récents » dans l'ordre de `library.json` (reconnues à leur couleur : plus de nom sous les jaquettes), « Masquer » par les options d'une jaquette, compteur de la bibliothèque (« Test-Atlas · 3 jeux » sur Tous et Récents avec un jeu masqué, « Test-Atlas · 1 jeu masqué » sur Masqués), retour par le filtre « Masqués · 1 » et « Ne plus masquer » |
| 3 | `test_03_hosts.py` | Mes PC : PC éteint, version de LavApollo trop ancienne (ouvrir et benchmark refusés), renommer, oublier |
| 4 | `test_04_settings.py` | chaque page des Réglages en normal et en expert, une écriture relue après redémarrage |
| 5 | `test_05_stream.py` | stream de Bureau : taille négociée (règle « résolution calée » appliquée à l'écran annoncé par le faux PC, `display` de `pc.json`, défaut 1920×1080 à 60 Hz), images décodées et numérotées, tuiles du menu en jeu, micro, quitter |
| 5b | `test_05b_stream_input.py` | entrées du canal pendant un stream : rien n'atteint l'hôte quand le canal se tait ; déplacement et clic de souris, manette de test (A), touche envoyée au stream : chacun dans `input.jsonl` de l'hôte |
| 6 | `test_06_playnite.py` | jeu Playnite pendant que Playnite tourne : `resume`, `playnite_launch`, « Quitter <jeu> » |
| 7 | `test_07_this_pc.py` | « Impossible de streamer … depuis lui-même », aucune session ; le benchmark de Mes PC refusé de même |
| 8 | `test_08_guards.py` | 192.0.2.10 (pour un PC du réseau local : adresse TEST-NET-1, qu'aucune machine n'a), 127.0.0.1:45989, 127.0.0.1:47989 refusés, aucune requête au journal |
| 9 | `test_09_lifecycle.py` | vie d'un PC : appairage retiré sur l'hôte (`hosts.unpair_all`, « Tout désappairer » de l'interface web) → en ligne, pas appairé, dans « Ajouter un PC », réappairé par son adresse ; 2ᵉ PC appairé affiché par l'accueil, oubli du PC affiché puis du dernier (« AUCUN PC APPAIRÉ ») ; PC éteint : « Test-Atlas — HORS LIGNE », cartes retirées, « Réveiller → » → « Le réveil … n'a pas pu être envoyé. » ; un dialogue « Oublier » garde son PC quand la liste bouge ; mauvais PIN, un seul appairage à la fois, hôte arrêté pendant l'appairage (message français qui nomme le PC) ; formes d'adresse ; PC oublié ici puis réappairé par un autre processus (canal `hoststore` : il remet l'entrée stockée du PC, comme la sauvegarde de l'autre processus) → repris à la sauvegarde suivante, encore là après redémarrage |
| 10 | `test_10_session.py` | session et menu en jeu (moteur), un test par point : S1 l'écran du PC change en plein stream (`hosts.set_display`) → une reconnexion par `resume`, « Reconnexion » affiché, l'appli jamais quittée même avec « Quitter l'appli… à la fin » ; S2 écran virtuel → au plus une reconnexion, pas de boucle ; S3 lancement refusé (`hosts.fail_next`) en français puis relancé, quit refusé dit (l'appli tourne encore : rejointe par « Reprendre »), quit en erreur (`cancel_error`) : « Impossible de quitter Bureau sur le PC (erreur 500). » ; S4 l'hôte finit le stream (`hosts.terminate`) menu ouvert : menu fermé avec la session, centré sur le stream (125 %), stream suivant intact ; S5 « Quitter la session » confirmé deux fois ; S6 presse-papiers (canal `clipboard`, `hosts.clipboard`) : envoyé au début, envoyé et récupéré à la demande (« Plus › Presse-papiers » → « Envoyer au PC ce que j’ai copié » / « Récupérer ce qui est copié sur le PC ») et sur « Déconnecter », notifié, récupération en échec (`fail_next clipboard`) : « … : erreur 500 », rien après le stream ; S7 commandes du PC (`server_cmd` du `pc.json`), Échap dans « Aucune commande de serveur » revient au menu ; S8 statistiques en français, micro coupé, zoom ; S9 zoom remis à 1× au stream suivant ; S10 écran tactile simulé : la tuile « Passer en trackpad » retenue, le `--bitrate` d'un stream lancé en ligne de commande jamais enregistré ; S11 Ctrl+Alt+Maj+Q = « Déconnecter » (appli gardée sur le PC), Ctrl+Alt+Maj+E = « Quitter la session » (appli quittée, client vivant, accueil) |
| 11 | `test_11_settings_logic.py` | Réglages, la logique : S1 profils (un nouveau profil = instantané du profil en cours ; changer, supprimer, redémarrer ; `setting raw`) ; S2 débit (suit les images/s tant qu'on ne l'a pas bougé, « Rétablir », retour de PyroWave à HEVC ramené à 800 Mbit/s) ; S3 résolution et images/s personnalisées ; S4 lignes grisées sans effet (frame pacing sans V-Sync, multi-écran, micro, presse-papiers) ; S5 réglages retirés forcés au démarrage ; S6 « Vérifier » : chaque check finit, échecs en français, release sans installeur → page (`LAV_TEST_UPDATE`) ; S7 clair/sombre gardé, toutes les langues listées ; S8 chaque interrupteur de chaque page (mode expert) basculé dans l'interface, relu après redémarrage |
| 12 | `test_12_benchmark.py` | Benchmark des réglages (Mes PC) : PC sans l'appli Benchmark (`"benchmark_app": false`) → raison en français, « Démarrer » grisé ; Rapide de bout en bout (le rapport JSON de `state.benchmark.report` : la référence enregistre ce qu'elle a streamé, `requested` = `effective`, chaque passage ok, notes en anglais ; page en français, mode et objectif traduits, historique) ; « Arrêter » pendant la fermeture de l'appli de l'hôte, avant tout passage (la page de départ revient, pas de page vide, aucun stream ensuite) ; « Arrêter » pendant le redémarrage de la scène entre un passage VRR et un autre (aucun lancement ensuite) ; « Arrêter » puis « Démarrer » aussitôt : le nouveau benchmark suit son plan, un stream par passage |
| 13 | `test_13_cli.py` | ligne de commande et liens : `stream <PC> Bureau`, `quit <PC>` (le processus finit seul, code 0 : `bench.expect_client_exit()`), appli introuvable (message en français) ; jeu Playnite pendant que Playnite tourne → lancé dans Playnite (`resume` + `playnite_launch`), sans question ; fichier `.art` valide → stream ; `.art` mal formé et lien sans appli → accueil + message en français |
| 14 | `test_14_gamepad.py` | manette inconnue (canal `joystick` : joystick HID générique hors stream) : « Disposition devinée » dans le mapper, encore après « Actualiser », avertissement à chaque lancement (`session.launchWarnings`) ; « Deviner la disposition » coupé → « Non reconnue » et « pas de disposition » au lancement, sans redémarrage ; remis → devinée de nouveau ; mapping enregistré dans le mapper avec la devinette coupée → « Reconnue » tout de suite, aucun avertissement |
| 15 | `test_15_host_data.py` | données de l'hôte pour le prototype (vague 5) : par jeu installé ou non, heures jouées, dernière activité, image de fond (canal `appasset` : 960 × 540 servie, 404 pour un jeu sans fond, jaquette intacte), ordre de la bibliothèque sur la dernière activité ; par PC type (`form_factor`) et liaison (`link`) de `pc.json`, latence mesurée (≥ 1 ms), début de la session et du jeu lancé dans Playnite (`state.json` écrit par le banc) ; **« Récents »** : un jeu joué pendant que Playnite Fullscreen tourne sur le PC (client déconnecté) passe en tête sans attendre la fin de la session |
| 16 | `test_16_port_v8_home.py` | (V8) accueil : grille de focus au clavier, barre d'aide, Menu / Entrée maintenue = options (même pressée aussitôt après l'Échap qui ferme les options), Bas puis Entrée dans les options = détails, Q / E = PC précédent / suivant, toast Multi-écran, Bureau repris après « Déconnecter » |
| 17 | `test_17_port_v8_more.py` | (V8) « Rejoindre » quand un autre appareil streame (`hosts.set_session`), menu en jeu de l'atelier du 2026-11-26 (ordre des tuiles, « Micro non envoyé », pas de « Quitter Bureau », entrées retirées absentes, ordre de Plus, Zoom 100 % ↔ 200 %, Comportement de la souris Bureau ↔ Jeu, Reprendre), carte de Mes PC « Tour · réseau filaire », Réglages : « N réglages avancés masqués », « ● », EXPERT |
| 18 | `test_18_port_v8_third.py` | (V8, troisième passe) toasts du stream « Connecté à <PC> » / « Session reprise » et « Déconnecté : la session continue sur le PC » au retour ; statistiques sur une ligne ; Mes PC : PC en ligne d'abord ; dialogue de mise à jour du thème (`LAV_TEST_UPDATE`) |
| 19 | `test_19_focus_rings.py` | un anneau de focus n'est jamais coupé (règle de l'utilisateur) : le clavier parcourt l'accueil, les options d'une carte, les détails, la bibliothèque, Mes PC et les options d'un PC, les Réglages, le menu en jeu et Plus, dans les trois thèmes (sombre), puis les dialogues d'options dans une fenêtre de 1024 × 576 : options d'un jeu et d'un PC (fenêtre principale réduite par la commande `resize` du canal), Plus sur deux colonnes, presse-papiers et 16 commandes du PC (la liste défile) dans la fenêtre du stream ; à chaque pas, la commande `focusrings` du canal ne doit trouver aucun anneau coupé par un ancêtre qui coupe (`clip`, `layer`) ni par la fenêtre |
| 20 | `test_20_gamepad_nav.py` | navigation à la manette et au clavier (audit du 2026-11-27) : la manette virtuelle du canal attachée **hors stream** pilote l'interface ; Réglages : Haut / Bas ne quittent jamais leur colonne (chaque page, une ligne dont un bouton a le focus, juste après LB / RB), LB / RB en boucle avec le focus sur la nouvelle page, B lignes → pages → accueil, Gauche / Droite sur une liste déroulante (Résolution), cartes du thème (Gauche / Droite déplacent, A applique), barre « A Ouvrir / Changer · LB RB Page · B Retour » ; répétition 380 / 110 ms de la croix, du stick (à 61 %, pas à 46 %, diagonale = verticale) et des flèches du clavier, une 2ᵉ manette au repos ne la relance pas ; accueil : premier appui après la souris sans bouger, changement de rangée vers le centre le plus proche, focus gardé sur Réglages au retour, Y sur Reprendre, anneaux des récents dans une fenêtre de 1000 × 600 ; Mes PC : focus sur le PC affiché, barre, une rangée, Menu sur « Ajouter un PC », Entrée maintenue = options ; listes à une colonne et dock d'une rangée, « Plus » sur deux colonnes bornée ; B / Échap / Start sur l'accueil = « Quitter LavArtemis ? » (dialogue du thème, focus sur « Quitter LavArtemis », B / Y / « Annuler » ferment, « Quitter LavArtemis » termine le client) ; `state.focus` (élément qui prend les touches, `objectName` de ses ancêtres), `state.pageState` (`focusId`, `currentPage`…), `state.menuFocusId` (menu en jeu) |

Les requêtes `launch` / `resume` / `cancel` reçues par un faux PC sont dans son `events.jsonl` (`request`, avec leurs
paramètres) ; `hosts.set_display`, `fail_next` (`launch`, `resume`, `cancel`, `cancel_error`, `clipboard`), `terminate`, `clipboard`,
`set_session` (autres appareils qui streament : `OtherClientsConnected` ; appli lancée « plus tôt » : `CurrentGameElapsed`)
pilotent ses routes `/api/test/*` (LavApollo, mode hôte de test, 127.0.0.1 seulement) ; `hosts.set_playnite_game` écrit le
`state.json` de son Playnite (un jeu en cours depuis N secondes).

`bench.start_client(env={...})` / `restart_client(env=...)` ajoutent des variables à l'environnement du client de test
(rien d'autre ne les lit) : **`LAV_TEST_TOUCHSCREEN=1`** = un écran tactile simulé (lignes tactiles des Réglages, entrée
« Passer en trackpad / multitouch » du menu en jeu ; aucun toucher n'est injectable) ; **`LAV_TEST_UPDATE`** = les
réponses aux « Vérifier » des Réglages, une par check (`fail:<NetworkError>[:<HTTP>]`, `available:<version>[:<URL>]`,
sinon à jour ; voir le CLAUDE.md de LavArtemis-Qt) ; **`LAV_TEST_BENCHMARK`** = `"warmup,measure,window[,quitDelayMs]"`
(secondes, millisecondes) : des passages de benchmark de quelques secondes, et chaque fermeture de l'appli de
l'hôte retenue `quitDelayMs` (une fenêtre pour cliquer « Arrêter »). Dans `pc.json`, **`"benchmark_app": false`**
retire l'appli Benchmark du faux PC (LavApollo `benchmark_app = disabled`) ; elle est là par défaut. **`form_factor`** (`tower`/`mini`/`laptop`) et **`link`** (`wired`/`wifi`) sont le type de PC et la liaison que `serverinfo` annonce (`test_host_form_factor` / `test_host_link`) ; les jeux de `library.json` ont `installed`, `playtime` (s) et, pour certains, `background` (une image 960 × 540 générée, comme les jaquettes). Les lignes des Réglages se trouvent par leur `objectName`
`set:<clé>` (champ `row` de `tree` : libellé, `checked`, `value`) ; après une molette, attendre que le `Flickable`
ne soit plus `moving` avant de cliquer (sinon le clic ne fait que l'arrêter).

Les streams restent légers (1280×720 à 30 i/s, H.264 logiciel côté hôte) et courts.
