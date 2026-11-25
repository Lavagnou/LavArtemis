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
l'organisation, `client-N-args.log` pour un lancement avec un lien —, notes et chronologie du premier plan).
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
- **Premier plan** : les fenêtres sont visibles mais ne prennent jamais le focus. Un crochet
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

## Scénarios (`scenarios/`)

| # | Fichier | Vérifie |
|---|---|---|
| 1 | `test_01_pairing.py` | ajout par adresse + PIN lu à l'écran donné à l'hôte ; lien `art://…&passphrase=` (OTP) sans PIN |
| 2 | `test_02_home.py` | cartes Bureau puis Playnite Fullscreen, jaquettes, ordre de `library.json`, Masquer / réafficher |
| 3 | `test_03_hosts.py` | Mes PC : PC éteint, version de LavApollo trop ancienne (ouvrir et benchmark refusés), renommer, oublier |
| 4 | `test_04_settings.py` | chaque page des Réglages en normal et en expert, une écriture relue après redémarrage |
| 5 | `test_05_stream.py` | stream de Bureau : taille négociée (règle « résolution calée » appliquée à l'écran annoncé par le faux PC, `display` de `pc.json`, défaut 1920×1080 à 60 Hz), images décodées et numérotées, touches du menu en jeu, micro, quitter |
| 5b | `test_05b_stream_input.py` | entrées du canal pendant un stream : rien n'atteint l'hôte quand le canal se tait ; déplacement et clic de souris, manette de test (A), touche envoyée au stream : chacun dans `input.jsonl` de l'hôte |
| 6 | `test_06_playnite.py` | jeu Playnite pendant que Playnite tourne : `resume`, `playnite_launch`, « Quitter <jeu> » |
| 7 | `test_07_this_pc.py` | « Impossible de streamer … depuis lui-même », aucune session ; le benchmark de Mes PC refusé de même |
| 8 | `test_08_guards.py` | 192.0.2.10 (pour un PC du réseau local : adresse TEST-NET-1, qu'aucune machine n'a), 127.0.0.1:45989, 127.0.0.1:47989 refusés, aucune requête au journal |
| 9 | `test_09_lifecycle.py` | vie d'un PC : appairage retiré sur l'hôte (`hosts.unpair_all`, « Tout désappairer » de l'interface web) → en ligne, pas appairé, dans « Ajouter un PC », réappairé par son adresse ; 2ᵉ PC appairé affiché par l'accueil, oubli du PC affiché puis du dernier (« Aucun PC appairé ») ; PC éteint : cartes retirées, « Réveiller » → « Le réveil … n'a pas pu être envoyé. » ; un dialogue « Oublier » garde son PC quand la liste bouge ; mauvais PIN, un seul appairage à la fois, hôte arrêté pendant l'appairage (message français qui nomme le PC) ; formes d'adresse |

Les streams restent légers (1280×720 à 30 i/s, H.264 logiciel côté hôte) et courts.
